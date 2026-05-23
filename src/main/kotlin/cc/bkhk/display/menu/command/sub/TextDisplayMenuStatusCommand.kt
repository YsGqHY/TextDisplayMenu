package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.config.DebugRuntime
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.nms.NMSRuntimeSupport
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.subCommand
import taboolib.platform.util.sendLang

object TextDisplayMenuStatusCommand {

    val command = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sendStatus(sender)
        }
    }

    fun sendStatus(sender: CommandSender) {
        val snapshot = PluginConfig.snapshot()
        sender.sendLang("command-status-line", MenuRegistry.size(), MenuSessionRegistry.size(), DebugRuntime.enabled())
        sender.sendLang("command-status-session-cache", MenuSessionRegistry.retainedSize())
        sender.sendLang("command-status-render", snapshot.render.distance, snapshot.render.followView, snapshot.render.followPosition)
        sender.sendLang("command-status-interaction", snapshot.interaction.maxDistance, snapshot.interaction.clickCooldownMs)
        sender.sendLang("command-status-shortcut", snapshot.shortcut.enabled, snapshot.shortcut.defaultMenu, snapshot.shortcut.actions.size)
        sender.sendLang("command-status-nms", NMSRuntimeSupport.current.summary())
    }
}
