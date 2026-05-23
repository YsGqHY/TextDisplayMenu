package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.findOnlinePlayer
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.menuTargetSuggestions
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.page
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.parseArguments
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.render.MenuRenderService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggest
import taboolib.common.platform.command.suggestUncheck
import taboolib.module.chat.colored
import taboolib.platform.util.sendLang

object TextDisplayMenuOpenCommand {

    val command = subCommand {
        dynamic("menu") {
            suggest { menuTargetSuggestions() }
            execute<CommandSender> { sender, _, menuInput ->
                val player = sender as? Player
                if (player == null) {
                    sender.sendLang("command-player-only")
                    return@execute
                }
                openMenu(sender, player, menuInput)
            }
            player(optional = true, permission = "textdisplaymenu.command.open.other") {
                execute<CommandSender> { sender, context, _ ->
                    val target = findOnlinePlayer(sender, context["player"]) ?: return@execute
                    openMenu(sender, target, context["menu"])
                }
                dynamic("arguments", optional = true) {
                    suggestUncheck { listOf("{}") }
                    execute<CommandSender> { sender, context, argument ->
                        val target = findOnlinePlayer(sender, context["player"]) ?: return@execute
                        openMenu(sender, target, context["menu"], parseArguments(argument))
                    }
                }
            }
        }
        execute<CommandSender> { sender, _, _ ->
            sender.sendLang("command-open-usage")
        }
    }

    private fun openMenu(sender: CommandSender, target: Player, rawMenu: String, arguments: List<String> = emptyList()) {
        val targetMenu = parseMenuTarget(rawMenu)
        val menu = MenuRegistry.get(targetMenu.menuId)
        if (menu == null) {
            sender.sendLang("command-open-not-found", targetMenu.menuId)
            return
        }
        val snapshot = runCatching { MenuRenderService.open(target, menu, targetMenu.pageId, arguments) }
            .onFailure { throwable -> sender.sendLang("command-open-render-failure", throwable.message ?: throwable.javaClass.simpleName) }
            .getOrNull()
        if (snapshot == null) {
            sender.sendLang("command-open-page-not-found", targetMenu.pageId ?: "-", menu.id)
            return
        }
        val page = menu.page(snapshot.pageId)
        val openMessage = page?.openMessage.orEmpty()
        if (openMessage.isNotBlank()) {
            target.sendMessage(openMessage.colored())
        }
        if (sender == target) {
            sender.sendLang("command-open-success", menu.id, snapshot.pageId)
        } else {
            sender.sendLang("command-open-success-target", menu.id, target.name, snapshot.pageId)
        }
    }

    private fun parseMenuTarget(input: String): MenuTarget {
        val trimmed = input.trim()
        val separator = listOf(':', '@').map { trimmed.indexOf(it) }.filter { it > 0 }.minOrNull()
        if (separator == null) {
            return MenuTarget(trimmed.lowercase(), null)
        }
        val menuId = trimmed.substring(0, separator).lowercase()
        val pageId = trimmed.substring(separator + 1).ifBlank { null }
        return MenuTarget(menuId, pageId)
    }

    private data class MenuTarget(val menuId: String, val pageId: String?)
}
