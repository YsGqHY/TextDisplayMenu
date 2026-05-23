package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuElementActions
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.menu.model.MenuPosition
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.platform.util.sendLang

object TextDisplayMenuCommandSupport {

    fun findMenu(sender: CommandSender, menuId: String): MenuDefinition? {
        val menu = MenuRegistry.get(menuId)
        if (menu == null) {
            sender.sendLang("command-open-not-found", menuId)
        }
        return menu
    }

    fun findOnlinePlayer(sender: CommandSender, playerName: String): Player? {
        val target = Bukkit.getPlayerExact(playerName)
        if (target == null) {
            sender.sendLang("command-player-not-found", playerName)
        }
        return target
    }

    fun menuTargetSuggestions(): List<String> {
        return MenuRegistry.all().flatMap { menu ->
            listOf(menu.id) + menu.pages.map { page -> "${menu.id}:${page.id}" }
        }.sorted()
    }

    fun menuPageSuggestions(menuId: String?): List<String> {
        val menu = menuId?.let { MenuRegistry.get(it) } ?: return emptyList()
        return menu.pages.map { it.id }
    }

    fun menuElementSuggestions(menuId: String?, pageId: String?): List<String> {
        val menu = menuId?.let { MenuRegistry.get(it) } ?: return emptyList()
        val page = pageId?.let { menu.page(it) } ?: return emptyList()
        return page.elements.map { it.id }
    }

    fun parseArguments(input: String): List<String> {
        return input.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun formatState(value: Boolean): String {
        return if (value) "on" else "off"
    }

    fun bytesToMiB(bytes: Long): Long {
        return bytes / 1024L / 1024L
    }

    fun MenuDefinition.page(pageId: String): MenuPageDefinition? {
        val index = pageId.toIntOrNull()
        if (index != null && index in 1..pages.size) {
            return pages[index - 1]
        }
        return pages.firstOrNull { it.id.equals(pageId, ignoreCase = true) }
    }

    fun MenuElementActions.allScripts(): List<String> {
        return interactClick + leftClick + rightClick + shiftLeftClick + shiftRightClick
    }

    fun MenuPosition.format(): String {
        return "$x,$y,$z"
    }
}
