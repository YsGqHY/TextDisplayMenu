package cc.bkhk.display.menu.command.sub

import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.allScripts
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.bytesToMiB
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.findMenu
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.findOnlinePlayer
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.format
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.formatState
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.menuElementSuggestions
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.menuPageSuggestions
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCommandSupport.page
import cc.bkhk.display.menu.config.DebugRuntime
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.menu.model.MenuElementDefinition
import cc.bkhk.display.menu.nms.NMSRuntimeSupport
import cc.bkhk.display.menu.session.MenuSessionRegistry

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggest
import taboolib.common.platform.command.suggestUncheck
import taboolib.module.chat.colored
import taboolib.platform.util.sendLang
import java.lang.management.ManagementFactory

object TextDisplayMenuDebugCommand {

    private val stateSuggestions = listOf("on", "off", "toggle")
    private val debugPartSuggestions = listOf(
        "info",
        "summary",
        "menu",
        "player",
        "session",
        "sessions",
        "config",
        "threads",
        "memory",
        "dump",
        "reset",
        "on",
        "off",
        "packets",
        "interactions",
    )

    val command = subCommand {
        literal("info") {
            execute<CommandSender> { sender, _, _ -> sendDebugInfo(sender) }
        }
        literal("summary") {
            execute<CommandSender> { sender, _, _ -> sendDebugSummary(sender) }
        }
        literal("menu") {
            dynamic("menu") {
                suggest { MenuRegistry.ids() }
                execute<CommandSender> { sender, _, menuId -> sendMenuDebug(sender, menuId) }
                dynamic("page", optional = true) {
                    suggestUncheck { menuPageSuggestions(ctx.getOrNull("menu")) }
                    execute<CommandSender> { sender, context, pageId -> sendMenuPageDebug(sender, context["menu"], pageId) }
                    dynamic("element", optional = true) {
                        suggestUncheck { menuElementSuggestions(ctx.getOrNull("menu"), ctx.getOrNull("page")) }
                        execute<CommandSender> { sender, context, elementId -> sendMenuElementDebug(sender, context["menu"], context["page"], elementId) }
                    }
                }
            }
        }
        literal("player") {
            player {
                execute<CommandSender> { sender, context, _ -> sendPlayerDebug(sender, context["player"]) }
            }
        }
        literal("session") {
            player {
                execute<CommandSender> { sender, context, _ -> sendPlayerDebug(sender, context["player"]) }
            }
        }
        literal("sessions") {
            execute<CommandSender> { sender, _, _ -> sendSessionsDebug(sender) }
        }
        literal("config") {
            execute<CommandSender> { sender, _, _ -> sendConfigDebug(sender) }
        }
        literal("threads") {
            execute<CommandSender> { sender, _, _ -> sendThreadDebug(sender) }
        }
        literal("memory") {
            execute<CommandSender> { sender, _, _ -> sendMemoryDebug(sender) }
        }
        literal("dump") {
            execute<CommandSender> { sender, _, _ -> sendDebugDump(sender) }
        }
        literal("reset") {
            execute<CommandSender> { sender, _, _ -> resetDebug(sender) }
        }
        literal("packets") {
            dynamic("state", optional = true) {
                suggest { stateSuggestions }
                execute<CommandSender> { sender, _, state ->
                    val enabled = applyDebugState(state, DebugRuntime.packetLogging(), DebugRuntime::setPacketLogging, DebugRuntime::togglePacketLogging)
                    sender.sendLang("command-debug-packets", formatState(enabled))
                }
            }
            execute<CommandSender> { sender, _, _ ->
                val enabled = DebugRuntime.togglePacketLogging()
                sender.sendLang("command-debug-packets", formatState(enabled))
            }
        }
        literal("interactions") {
            dynamic("state", optional = true) {
                suggest { stateSuggestions }
                execute<CommandSender> { sender, _, state ->
                    val enabled = applyDebugState(state, DebugRuntime.interactionLogging(), DebugRuntime::setInteractionLogging, DebugRuntime::toggleInteractionLogging)
                    sender.sendLang("command-debug-interactions", formatState(enabled))
                }
            }
            execute<CommandSender> { sender, _, _ ->
                val enabled = DebugRuntime.toggleInteractionLogging()
                sender.sendLang("command-debug-interactions", formatState(enabled))
            }
        }
        literal("on") {
            execute<CommandSender> { sender, _, _ ->
                sender.sendLang("command-debug-enabled", formatState(DebugRuntime.setEnabled(true)))
            }
        }
        literal("off") {
            execute<CommandSender> { sender, _, _ ->
                sender.sendLang("command-debug-enabled", formatState(DebugRuntime.setEnabled(false)))
            }
        }
        dynamic("part", optional = true) {
            suggest { debugPartSuggestions }
            execute<CommandSender> { sender, _, part -> sendDebugPart(sender, part) }
        }
        execute<CommandSender> { sender, _, _ -> sendDebugInfo(sender) }
    }

    private fun sendDebugInfo(sender: CommandSender) {
        sender.sendLang("command-debug-info", formatState(DebugRuntime.enabled()), formatState(DebugRuntime.packetLogging()), formatState(DebugRuntime.interactionLogging()))
        sender.sendLang("command-debug-info-counts", MenuRegistry.size(), MenuSessionRegistry.size(), Bukkit.getOnlinePlayers().size)
        sender.sendLang("command-status-session-cache", MenuSessionRegistry.retainedSize())
        sender.sendLang("command-debug-info-help")
    }

    private fun sendDebugSummary(sender: CommandSender) {
        TextDisplayMenuStatusCommand.sendStatus(sender)
        sendMemoryDebug(sender)
        sendThreadDebug(sender)
    }

    private fun sendMenuDebug(sender: CommandSender, menuId: String) {
        val menu = findMenu(sender, menuId) ?: return
        sender.sendLang("command-debug-menu", menu.id, menu.sourceFileName, menu.pages.size, menu.defaultPage?.id ?: "-", menu.displayName.colored())
        menu.pages.forEach { page -> sender.sendLang("command-debug-menu-page", page.id, page.elements.size, page.default, page.title.colored()) }
    }

    private fun sendMenuPageDebug(sender: CommandSender, menuId: String, pageId: String) {
        val menu = findMenu(sender, menuId) ?: return
        val page = menu.page(pageId)
        if (page == null) {
            sender.sendLang("command-debug-page-not-found", pageId, menu.id)
            return
        }
        sender.sendLang("command-debug-page", menu.id, page.id, page.elements.size, page.default, page.title.colored())
        page.elements.forEach { element ->
            val actionCount = element.actions.allScripts().size
            val hitbox = element.hitbox?.let { "${it.width}x${it.height}" } ?: "-"
            sender.sendLang("command-debug-page-element", element.id, element.type.name.lowercase(), hitbox, actionCount)
        }
    }

    private fun sendMenuElementDebug(sender: CommandSender, menuId: String, pageId: String, elementId: String) {
        val menu = findMenu(sender, menuId) ?: return
        val page = menu.page(pageId)
        if (page == null) {
            sender.sendLang("command-debug-page-not-found", pageId, menu.id)
            return
        }
        val element = page.elements.firstOrNull { it.id.equals(elementId, ignoreCase = true) }
        if (element == null) {
            sender.sendLang("command-debug-element-not-found", elementId, page.id, menu.id)
            return
        }
        val hitbox = element.hitbox?.let { "${it.width}x${it.height}" } ?: "-"
        val scale = element.scale?.let { "${it.x},${it.y},${it.z}" } ?: "-"
        sender.sendLang("command-debug-element", element.id, element.type.name.lowercase(), element.position.format(), hitbox, scale, element.interactive ?: "default")
        sendElementActions(sender, element)
    }

    private fun sendPlayerDebug(sender: CommandSender, playerName: String) {
        val target = findOnlinePlayer(sender, playerName) ?: return
        val session = MenuSessionRegistry.current(target)
        if (session == null) {
            sender.sendLang("command-debug-player-none", target.name)
        } else {
            val openedMs = System.currentTimeMillis() - session.openedAt
            val arguments = session.arguments.joinToString(" ").ifBlank { "-" }
            sender.sendLang("command-debug-player", target.name, session.menuId, session.pageId, openedMs, arguments)
        }
    }

    private fun sendSessionsDebug(sender: CommandSender) {
        val sessions = MenuSessionRegistry.all().sortedBy { it.openedAt }
        sender.sendLang("command-debug-sessions-header", sessions.size)
        if (sessions.isEmpty()) {
            sender.sendLang("command-debug-sessions-empty")
            return
        }
        sessions.forEach { session ->
            val name = Bukkit.getPlayer(session.playerId)?.name ?: session.playerId.toString()
            val openedMs = System.currentTimeMillis() - session.openedAt
            sender.sendLang("command-debug-sessions-entry", name, session.menuId, session.pageId, openedMs)
        }
    }

    private fun sendConfigDebug(sender: CommandSender) {
        val snapshot = PluginConfig.snapshot()
        sender.sendLang("command-debug-config-shortcut", snapshot.shortcut.enabled, snapshot.shortcut.defaultMenu, snapshot.shortcut.actions.size)
        sender.sendLang("command-debug-config-shortcut-conditions", snapshot.shortcut.conditions.cancelEvent, snapshot.shortcut.conditions.ignoreWhenInventoryOpen, snapshot.shortcut.conditions.blockSpectator, snapshot.shortcut.conditions.requireEmptyHand)
        sender.sendLang("command-debug-config-render", snapshot.render.anchor, snapshot.render.updateIntervalTicks, snapshot.render.maxSessionDistance)
        sender.sendLang("command-debug-config-nms", NMSRuntimeSupport.current.summary())
        sender.sendLang("command-debug-config-interaction", snapshot.interaction.detectionIntervalTicks, snapshot.interaction.rayStep, snapshot.interaction.focusStayTicks, snapshot.interaction.hitPriority.joinToString(","))
        sender.sendLang("command-debug-config-session", snapshot.session.idleTimeoutSeconds, snapshot.session.keepStateSeconds, snapshot.session.cleanupIntervalTicks)
        sender.sendLang("command-debug-config-debug", snapshot.debug.enabled, snapshot.debug.logPackets, snapshot.debug.logInteractions)
    }

    private fun sendThreadDebug(sender: CommandSender) {
        val threadBean = ManagementFactory.getThreadMXBean()
        sender.sendLang("command-debug-threads", threadBean.threadCount, threadBean.daemonThreadCount, threadBean.peakThreadCount)
    }

    private fun sendMemoryDebug(sender: CommandSender) {
        val runtime = Runtime.getRuntime()
        val max = bytesToMiB(runtime.maxMemory())
        val total = bytesToMiB(runtime.totalMemory())
        val free = bytesToMiB(runtime.freeMemory())
        val used = total - free
        sender.sendLang("command-debug-memory", used, total, max, free)
    }

    private fun sendDebugDump(sender: CommandSender) {
        TextDisplayMenuStatusCommand.sendStatus(sender)
        sendDebugInfo(sender)
        sendConfigDebug(sender)
        sender.sendLang("command-debug-dump-menus", MenuRegistry.ids().joinToString(", ").ifBlank { "-" })
        val sessions = MenuSessionRegistry.all().joinToString(", ") { "${it.playerId}:${it.menuId}/${it.pageId}" }
        sender.sendLang("command-debug-dump-sessions", sessions.ifBlank { "-" })
    }

    private fun resetDebug(sender: CommandSender) {
        DebugRuntime.syncFromConfig()
        sender.sendLang("command-debug-reset", formatState(DebugRuntime.enabled()), formatState(DebugRuntime.packetLogging()), formatState(DebugRuntime.interactionLogging()))
    }

    private fun sendDebugPart(sender: CommandSender, part: String) {
        when (part.lowercase()) {
            "info" -> sendDebugInfo(sender)
            "summary" -> sendDebugSummary(sender)
            "config" -> sendConfigDebug(sender)
            "threads" -> sendThreadDebug(sender)
            "memory" -> sendMemoryDebug(sender)
            "sessions", "session" -> sendSessionsDebug(sender)
            "dump" -> sendDebugDump(sender)
            "reset" -> resetDebug(sender)
            "on" -> sender.sendLang("command-debug-enabled", formatState(DebugRuntime.setEnabled(true)))
            "off" -> sender.sendLang("command-debug-enabled", formatState(DebugRuntime.setEnabled(false)))
            "packets" -> sender.sendLang("command-debug-packets", formatState(DebugRuntime.togglePacketLogging()))
            "interactions" -> sender.sendLang("command-debug-interactions", formatState(DebugRuntime.toggleInteractionLogging()))
            else -> sender.sendLang("command-debug-unknown", part)
        }
    }

    private fun sendElementActions(sender: CommandSender, element: MenuElementDefinition) {
        val actions = listOf(
            "interact" to element.actions.interactClick,
            "left" to element.actions.leftClick,
            "right" to element.actions.rightClick,
            "shift_left" to element.actions.shiftLeftClick,
            "shift_right" to element.actions.shiftRightClick,
        )
        actions.filter { it.second.isNotEmpty() }.forEach { (name, scripts) ->
            sender.sendLang("command-debug-element-action", name, scripts.joinToString(" | "))
        }
    }

    private fun applyDebugState(
        input: String,
        current: Boolean,
        set: (Boolean) -> Boolean,
        toggle: () -> Boolean,
    ): Boolean {
        return when (input.lowercase()) {
            "on", "true", "enable", "enabled", "1" -> set(true)
            "off", "false", "disable", "disabled", "0" -> set(false)
            "toggle" -> toggle()
            else -> current
        }
    }
}
