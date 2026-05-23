package cc.bkhk.display.menu

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.interaction.MenuInteractionService
import cc.bkhk.display.menu.interaction.shortcut.MenuShortcutService
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.render.MenuRenderService
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object TextDisplayMenu : Plugin() {

    override fun onEnable() {
        PluginConfig.initialize()
        val menuCount = MenuRegistry.initialize()
        MenuRenderService.initialize()
        MenuInteractionService.initialize()
        MenuShortcutService.initialize()
        info("TextDisplayMenu 已加载，菜单数量: $menuCount")
    }

    override fun onDisable() {
        MenuShortcutService.shutdown()
        MenuInteractionService.shutdown()
        MenuRenderService.shutdown()
    }
}
