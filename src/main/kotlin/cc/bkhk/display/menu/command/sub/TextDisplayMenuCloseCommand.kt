package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.findOnlinePlayer
import cc.bkhk.display.menu.render.MenuRenderService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.platform.util.sendLang

object TextDisplayMenuCloseCommand {

    val command = subCommand {
        literal("all", permission = "textdisplaymenu.command.close.all") {
            execute<CommandSender> { sender, _, _ ->
                val closed = MenuRenderService.closeAll()
                sender.sendLang("command-close-all-success", closed)
            }
        }
        player(optional = true, permission = "textdisplaymenu.command.close.other") {
            execute<CommandSender> { sender, context, _ ->
                val target = findOnlinePlayer(sender, context["player"]) ?: return@execute
                closeMenu(sender, target)
            }
        }
        execute<CommandSender> { sender, _, _ ->
            val player = sender as? Player
            if (player == null) {
                sender.sendLang("command-player-only")
                return@execute
            }
            closeMenu(sender, player)
        }
    }

    private fun closeMenu(sender: CommandSender, target: Player) {
        if (MenuRenderService.close(target)) {
            if (sender == target) {
                sender.sendLang("command-close-success")
            } else {
                sender.sendLang("command-close-success-target", target.name)
            }
        } else {
            if (sender == target) {
                sender.sendLang("command-close-none")
            } else {
                sender.sendLang("command-close-none-target", target.name)
            }
        }
    }
}
