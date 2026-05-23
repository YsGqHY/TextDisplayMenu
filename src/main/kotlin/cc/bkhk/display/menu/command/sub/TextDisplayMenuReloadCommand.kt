package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.render.MenuRenderService
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggest
import taboolib.module.lang.Language
import taboolib.platform.util.sendLang

object TextDisplayMenuReloadCommand {

    private val reloadSuggestions = listOf("all", "config", "menu", "menus", "lang", "language")

    val command = subCommand {
        dynamic("part", optional = true) {
            suggest { reloadSuggestions }
            execute<CommandSender> { sender, _, part -> reloadPart(sender, part) }
        }
        execute<CommandSender> { sender, _, _ -> reloadPart(sender, "all") }
    }

    private fun reloadPart(sender: CommandSender, input: String) {
        val part = input.lowercase()
        runCatching {
            when (part) {
                "all", "*" -> {
                    PluginConfig.config.reload()
                    PluginConfig.reload()
                    MenuRenderService.restartTasks()
                    val menus = MenuRegistry.reload()
                    Language.reload()
                    val refreshed = MenuRenderService.refreshAll()
                    sender.sendLang("command-reload-success-detail", "all", menus)
                    sender.sendLang("command-reload-render-refreshed", refreshed)
                }
                "config" -> {
                    PluginConfig.config.reload()
                    PluginConfig.reload()
                    MenuRenderService.restartTasks()
                    sender.sendLang("command-reload-config-success")
                }
                "menu", "menus" -> {
                    val menus = MenuRegistry.reload()
                    val refreshed = MenuRenderService.refreshAll()
                    sender.sendLang("command-reload-menu-success", menus)
                    sender.sendLang("command-reload-render-refreshed", refreshed)
                }
                "lang", "language" -> {
                    Language.reload()
                    sender.sendLang("command-reload-lang-success")
                }
                else -> sender.sendLang("command-reload-unknown", input)
            }
        }.onFailure { throwable ->
            sender.sendLang("command-reload-failure", throwable.message ?: throwable.javaClass.simpleName)
        }
    }
}
