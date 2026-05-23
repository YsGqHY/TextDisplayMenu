package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.text.MenuTextContext
import cc.bkhk.display.menu.text.MenuTextProcessor
import cc.bkhk.display.menu.text.MenuTextUse
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggestUncheck
import taboolib.platform.util.sendLang

object TextDisplayMenuListCommand {

    val command = subCommand {
        dynamic("filter", optional = true) {
            suggestUncheck { MenuRegistry.ids() }
            execute<CommandSender> { sender, _, filter ->
                listMenus(sender, filter)
            }
        }
        execute<CommandSender> { sender, _, _ ->
            listMenus(sender, "")
        }
    }

    private fun listMenus(sender: CommandSender, filter: String) {
        val menus = MenuRegistry.all().filter { menu ->
            filter.isBlank() || menu.id.contains(filter, ignoreCase = true) || menu.displayName.contains(filter, ignoreCase = true)
        }
        sender.sendLang("command-list-header", menus.size, MenuRegistry.size())
        if (menus.isEmpty()) {
            sender.sendLang("command-list-empty")
            return
        }
        menus.forEach { menu ->
            val defaultPage = menu.defaultPage?.id ?: "-"
            sender.sendLang("command-list-entry", menu.id, menu.pages.size, defaultPage, menu.sourceFileName, MenuTextProcessor.formatFor(sender, menu.displayName, MenuTextContext(menuId = menu.id, use = MenuTextUse.MENU_DISPLAY_NAME)))
        }
    }
}
