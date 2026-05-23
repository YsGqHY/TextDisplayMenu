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
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuElementDefinition
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.nms.DisplayEntityHandle
import cc.bkhk.display.menu.nms.DisplayEntityType
import cc.bkhk.display.menu.nms.NMSRuntimeSupport
import cc.bkhk.display.menu.session.MenuPendingConfirmation
import cc.bkhk.display.menu.session.MenuSessionRegistry
import cc.bkhk.display.menu.session.MenuSessionSnapshot
import cc.bkhk.display.menu.text.MenuTextContext
import cc.bkhk.display.menu.text.MenuTextProcessor
import cc.bkhk.display.menu.text.MenuTextUse

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggest
import taboolib.common.platform.command.suggestUncheck
import taboolib.platform.util.sendLang
import java.lang.management.ManagementFactory

object TextDisplayMenuDebugCommand {

    private val stateSuggestions = listOf("on", "off", "toggle")
    private val debugPartSuggestions = listOf(
        "info",
        "summary",
        "menus",
        "menu",
        "players",
        "player",
        "session",
        "sessions",
        "config",
        "nms",
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
        literal("menus") {
            execute<CommandSender> { sender, _, _ -> sendMenusDebug(sender) }
        }
        literal("menu") {
            execute<CommandSender> { sender, _, _ -> sendMenusDebug(sender) }
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
        literal("players") {
            execute<CommandSender> { sender, _, _ -> sendPlayersDebug(sender) }
        }
        literal("player") {
            execute<CommandSender> { sender, _, _ -> sendPlayersDebug(sender) }
            player {
                execute<CommandSender> { sender, context, _ -> sendPlayerDebug(sender, context["player"]) }
            }
        }
        literal("session") {
            execute<CommandSender> { sender, _, _ -> sendSessionsDebug(sender) }
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
        literal("nms") {
            execute<CommandSender> { sender, _, _ -> sendNMSDebug(sender) }
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
                execute<CommandSender> { sender, _, state -> setPacketLogging(sender, state) }
            }
            execute<CommandSender> { sender, _, _ -> setPacketLogging(sender, null) }
        }
        literal("interactions") {
            dynamic("state", optional = true) {
                suggest { stateSuggestions }
                execute<CommandSender> { sender, _, state -> setInteractionLogging(sender, state) }
            }
            execute<CommandSender> { sender, _, _ -> setInteractionLogging(sender, null) }
        }
        literal("on") {
            execute<CommandSender> { sender, _, _ -> setDebugEnabled(sender, true) }
        }
        literal("off") {
            execute<CommandSender> { sender, _, _ -> setDebugEnabled(sender, false) }
        }
        dynamic("part", optional = true) {
            suggest { debugPartSuggestions }
            execute<CommandSender> { sender, _, part -> sendDebugPart(sender, part) }
        }
        execute<CommandSender> { sender, _, _ -> sendDebugInfo(sender) }
    }

    private fun sendDebugInfo(sender: CommandSender) {
        val snapshot = PluginConfig.snapshot()
        sender.sendLang("command-debug-info", formatState(DebugRuntime.enabled()), formatState(DebugRuntime.packetLogging()), formatState(DebugRuntime.interactionLogging()))
        sender.sendLang("command-debug-info-config", formatState(snapshot.debug.enabled), formatState(snapshot.debug.logPackets), formatState(snapshot.debug.logInteractions), snapshot.debug.messageKey)
        sender.sendLang("command-debug-info-counts", MenuRegistry.size(), MenuSessionRegistry.size(), MenuSessionRegistry.retainedSize(), Bukkit.getOnlinePlayers().size)
        sendDebugEffective(sender)
        sendNMSDebug(sender)
        sender.sendLang("command-debug-info-help")
    }

    private fun sendDebugSummary(sender: CommandSender) {
        TextDisplayMenuStatusCommand.sendStatus(sender)
        sendDebugEffective(sender)
        sendMemoryDebug(sender)
        sendThreadDebug(sender)
        sender.sendLang("command-debug-summary", MenuRegistry.size(), MenuSessionRegistry.size(), MenuSessionRegistry.retainedSize(), NMSRuntimeSupport.current.supportsDisplayPackets)
    }

    private fun sendMenusDebug(sender: CommandSender) {
        val menus = MenuRegistry.all().sortedBy { it.id }
        sender.sendLang("command-debug-menus-header", menus.size)
        if (menus.isEmpty()) {
            sender.sendLang("command-list-empty")
            return
        }
        menus.forEach { menu ->
            sender.sendLang(
                "command-debug-menus-entry",
                menu.id,
                menu.sourceFileName.ifBlank { "-" },
                menu.pages.size,
                menu.defaultPage?.id ?: "-",
                menu.elementCount(),
                menu.actionCount(),
                menu.hoverCount(),
                menu.typeCounts(),
            )
        }
    }

    private fun sendMenuDebug(sender: CommandSender, menuId: String) {
        val menu = findMenu(sender, menuId) ?: return
        sender.sendLang(
            "command-debug-menu",
            menu.id,
            menu.sourceFileName.ifBlank { "-" },
            menu.pages.size,
            menu.defaultPage?.id ?: "-",
            menu.elementCount(),
            menu.actionCount(),
            menu.hoverCount(),
            MenuTextProcessor.formatFor(sender, menu.displayName, MenuTextContext(menuId = menu.id, use = MenuTextUse.MENU_DISPLAY_NAME)),
        )
        sender.sendLang(
            "command-debug-menu-view",
            formatNullable(menu.view.distance),
            formatNullable(menu.view.horizontalOffset),
            formatNullable(menu.view.verticalOffset),
            formatNullable(menu.view.width),
            formatNullable(menu.view.height),
            formatNullable(menu.view.followView),
            formatNullable(menu.view.followPosition),
            formatNullable(menu.view.lockView),
            formatNullable(menu.view.lockPosition),
        )
        sender.sendLang("command-debug-menu-controls", menu.controls.backClosesWhenNoHistory, listSummary(menu.controls.closeClicks), listSummary(menu.controls.backClicks), menu.controls.backMenu.ifBlank { "-" })
        menu.pages.forEach { page ->
            sender.sendLang(
                "command-debug-menu-page",
                page.id,
                page.elements.size,
                page.default,
                page.actionCount(),
                page.interactiveCount(),
                page.hoverCount(),
                page.typeCounts(),
                MenuTextProcessor.formatFor(sender, page.title, MenuTextContext(menuId = menu.id, pageId = page.id, use = MenuTextUse.PAGE_TITLE)),
            )
        }
    }

    private fun sendMenuPageDebug(sender: CommandSender, menuId: String, pageId: String) {
        val menu = findMenu(sender, menuId) ?: return
        val page = menu.page(pageId)
        if (page == null) {
            sender.sendLang("command-debug-page-not-found", pageId, menu.id)
            return
        }
        sender.sendLang(
            "command-debug-page",
            menu.id,
            page.id,
            page.elements.size,
            page.default,
            page.interactiveCount(),
            page.actionCount(),
            page.hoverCount(),
            MenuTextProcessor.formatFor(sender, page.title, MenuTextContext(menuId = menu.id, pageId = page.id, use = MenuTextUse.PAGE_TITLE)),
        )
        sender.sendLang("command-debug-page-meta", page.typeCounts(), page.renderTypeCounts(), textLength(page.openMessage))
        page.elements.forEach { element ->
            sender.sendLang(
                "command-debug-page-element",
                element.id,
                element.type.name.lowercase(),
                element.prepared.displayType.name.lowercase(),
                element.position.format(),
                formatHitbox(element),
                formatScale(element),
                formatInteractive(element),
                element.hoverFlags(),
                element.actions.allScripts().size,
            )
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
        sender.sendLang(
            "command-debug-element",
            element.id,
            menu.id,
            page.id,
            element.type.name.lowercase(),
            element.prepared.displayType.name.lowercase(),
            element.position.format(),
            formatHitbox(element),
            formatScale(element),
            formatInteractive(element),
        )
        sender.sendLang(
            "command-debug-element-source",
            element.material.ifBlank { "-" },
            element.prepared.material?.name ?: "-",
            element.transform.ifBlank { "-" },
            element.prepared.itemTransform?.key ?: "-",
            element.block.ifBlank { "-" },
            element.prepared.blockData?.asString ?: "-",
        )
        sender.sendLang("command-debug-element-text", textLength(element.text), textLength(element.focusedText), textLength(element.disabledText))
        sender.sendLang("command-debug-element-hover", element.hoverFlags(), popupSummary(element), titleSummary(element), bossBarSummary(element))
        sendElementActions(sender, element)
    }

    private fun sendPlayersDebug(sender: CommandSender) {
        val players = Bukkit.getOnlinePlayers().sortedBy { it.name }
        sender.sendLang("command-debug-players-header", players.size, MenuSessionRegistry.size())
        if (players.isEmpty()) {
            return
        }
        players.forEach { player ->
            val session = MenuSessionRegistry.current(player)
            sender.sendLang(
                "command-debug-players-entry",
                player.name,
                player.uniqueId.toString(),
                player.world.name,
                session?.menuId ?: "-",
                session?.pageId ?: "-",
                session?.entityHandles?.size ?: 0,
                session?.focusedElementKey ?: "-",
            )
        }
    }

    private fun sendPlayerDebug(sender: CommandSender, playerName: String) {
        val target = findOnlinePlayer(sender, playerName) ?: return
        val session = MenuSessionRegistry.current(target)
        if (session == null) {
            sender.sendLang("command-debug-player-none", target.name)
            return
        }
        sendSessionDetails(sender, target.name, session)
    }

    private fun sendSessionsDebug(sender: CommandSender) {
        val sessions = MenuSessionRegistry.all().sortedBy { it.openedAt }
        sender.sendLang("command-debug-sessions-header", sessions.size, MenuSessionRegistry.retainedSize())
        if (sessions.isEmpty()) {
            sender.sendLang("command-debug-sessions-empty")
            return
        }
        sessions.forEach { session ->
            val name = Bukkit.getPlayer(session.playerId)?.name ?: session.playerId.toString()
            val openedMs = System.currentTimeMillis() - session.openedAt
            val idleMs = System.currentTimeMillis() - session.lastActiveAt
            sender.sendLang("command-debug-sessions-entry", name, session.menuId, session.pageId, openedMs, idleMs, session.entityHandles.size, session.focusedElementKey ?: "-")
        }
    }

    private fun sendConfigDebug(sender: CommandSender) {
        val snapshot = PluginConfig.snapshot()
        sender.sendLang("command-debug-config-shortcut", snapshot.shortcut.enabled, snapshot.shortcut.defaultMenu, snapshot.shortcut.actions.size, snapshot.shortcut.cooldownMs, snapshot.shortcut.sneakingValidMs)
        sender.sendLang(
            "command-debug-config-shortcut-conditions",
            snapshot.shortcut.conditions.cancelEvent,
            snapshot.shortcut.conditions.ignoreWhenInventoryOpen,
            snapshot.shortcut.conditions.blockSpectator,
            snapshot.shortcut.conditions.blockInsideVehicle,
            snapshot.shortcut.conditions.requireEmptyHand,
            listSummary(snapshot.shortcut.conditions.blockedWorlds),
        )
        sender.sendLang(
            "command-debug-config-render",
            snapshot.render.anchor,
            snapshot.render.distance,
            snapshot.render.horizontalOffset,
            snapshot.render.verticalOffset,
            snapshot.render.followView,
            snapshot.render.followPosition,
            snapshot.render.updateIntervalTicks,
            snapshot.render.maxSessionDistance,
        )
        sender.sendLang("command-debug-config-render-follow", snapshot.render.followSmoothTicks, snapshot.render.followPositionSmoothTicks, snapshot.render.viewPacketThrottleMs)
        sender.sendLang(
            "command-debug-config-display-common",
            snapshot.render.displayDefaults.common.billboard.name.lowercase(),
            snapshot.render.displayDefaults.common.brightness,
            snapshot.render.displayDefaults.common.viewRange,
            snapshot.render.displayDefaults.common.interpolationDelayTicks,
            snapshot.render.displayDefaults.common.interpolationDurationTicks,
            "${snapshot.render.displayDefaults.common.scaleX},${snapshot.render.displayDefaults.common.scaleY},${snapshot.render.displayDefaults.common.scaleZ}",
        )
        sender.sendLang(
            "command-debug-config-display-text",
            snapshot.render.displayDefaults.text.shadowed,
            snapshot.render.displayDefaults.text.seeThrough,
            snapshot.render.displayDefaults.text.lineWidth,
            snapshot.render.displayDefaults.text.textOpacity,
            snapshot.render.displayDefaults.text.alignment.name.lowercase(),
            "0x${snapshot.render.displayDefaults.text.backgroundColor.toUInt().toString(16)}",
        )
        sender.sendLang(
            "command-debug-config-display-item",
            snapshot.render.displayDefaults.item.material,
            snapshot.render.displayDefaults.item.transform.key,
            snapshot.render.displayDefaults.item.interactive,
            "${snapshot.render.displayDefaults.item.scaleX},${snapshot.render.displayDefaults.item.scaleY},${snapshot.render.displayDefaults.item.scaleZ}",
        )
        sender.sendLang(
            "command-debug-config-display-block",
            snapshot.render.displayDefaults.block.block,
            snapshot.render.displayDefaults.block.interactive,
            "${snapshot.render.displayDefaults.block.scaleX},${snapshot.render.displayDefaults.block.scaleY},${snapshot.render.displayDefaults.block.scaleZ}",
        )
        sender.sendLang("command-debug-config-nms", NMSRuntimeSupport.current.summary())
        sender.sendLang(
            "command-debug-config-interaction",
            snapshot.interaction.detectionIntervalTicks,
            snapshot.interaction.maxDistance,
            snapshot.interaction.rayStep,
            snapshot.interaction.focusStayTicks,
            snapshot.interaction.clickCooldownMs,
            snapshot.interaction.hitPriority.joinToString(","),
        )
        sender.sendLang(
            "command-debug-config-interaction-clickable",
            snapshot.interaction.itemDisplayClickable,
            snapshot.interaction.blockDisplayClickable,
            snapshot.interaction.input.interactClick,
            snapshot.interaction.input.leftClick,
            snapshot.interaction.input.rightClick,
            snapshot.interaction.input.shiftClick,
            listSummary(snapshot.interaction.input.interactEvents),
        )
        sender.sendLang(
            "command-debug-config-interaction-mapping",
            snapshot.interaction.clickMapping.interactClick,
            snapshot.interaction.clickMapping.leftClick,
            snapshot.interaction.clickMapping.rightClick,
            snapshot.interaction.clickMapping.shiftLeftClick,
            snapshot.interaction.clickMapping.shiftRightClick,
        )
        sender.sendLang(
            "command-debug-config-interaction-hitbox",
            "${snapshot.interaction.defaultHitbox.textWidth}x${snapshot.interaction.defaultHitbox.textHeight}",
            "${snapshot.interaction.defaultHitbox.itemWidth}x${snapshot.interaction.defaultHitbox.itemHeight}",
            "${snapshot.interaction.defaultHitbox.blockWidth}x${snapshot.interaction.defaultHitbox.blockHeight}",
        )
        sender.sendLang(
            "command-debug-config-feedback",
            snapshot.interaction.focusFeedback.enabled,
            snapshot.interaction.focusFeedback.playSound,
            snapshot.interaction.focusFeedback.sound,
            snapshot.interaction.focusFeedback.displayPopupEnabled,
            snapshot.interaction.focusFeedback.actionbarEnabled,
            snapshot.interaction.focusFeedback.titleEnabled,
            snapshot.interaction.focusFeedback.bossbarEnabled,
        )
        sender.sendLang(
            "command-debug-config-feedback-timing",
            snapshot.interaction.focusFeedback.displayPopupLifetimeTicks,
            snapshot.interaction.focusFeedback.actionbarIntervalTicks,
            snapshot.interaction.focusFeedback.titleIntervalTicks,
            snapshot.interaction.focusFeedback.bossbarIntervalTicks,
            "${snapshot.interaction.focusFeedback.displayPopupOffset.x},${snapshot.interaction.focusFeedback.displayPopupOffset.y},${snapshot.interaction.focusFeedback.displayPopupOffset.z}",
        )
        sender.sendLang("command-debug-config-session", snapshot.session.idleTimeoutSeconds, snapshot.session.keepStateSeconds, snapshot.session.cleanupIntervalTicks, snapshot.session.destroyDelayTicks)
        sender.sendLang(
            "command-debug-config-session-close",
            snapshot.session.closeOnQuit,
            snapshot.session.closeOnWorldChange,
            snapshot.session.closeOnTeleport,
            snapshot.session.closeOnDeath,
            snapshot.session.closeOnSpectator,
        )
        sender.sendLang(
            "command-debug-config-confirmation",
            snapshot.confirmation.defaultRequired,
            snapshot.confirmation.expireSeconds,
            snapshot.confirmation.acceptClick,
            snapshot.confirmation.cancelClick,
            snapshot.confirmation.promptMessageKey,
            snapshot.confirmation.expiredMessageKey,
            snapshot.confirmation.cancelledMessageKey,
        )
        sender.sendLang("command-debug-config-debug", snapshot.debug.enabled, snapshot.debug.logPackets, snapshot.debug.logInteractions, snapshot.debug.messageKey)
        sendDebugEffective(sender)
    }

    private fun sendNMSDebug(sender: CommandSender) {
        val profile = NMSRuntimeSupport.current
        sender.sendLang(
            "command-debug-nms",
            profile.runningVersion,
            profile.versionId,
            profile.kind.name,
            profile.supportsDisplayPackets,
            profile.supportedByTabooLib,
            profile.skippedByTabooLib,
            profile.unobfuscated,
            profile.universalCraftBukkit,
        )
        sender.sendLang("command-debug-nms-reason", profile.reason)
    }

    private fun sendThreadDebug(sender: CommandSender) {
        val threadBean = ManagementFactory.getThreadMXBean()
        val deadlocked = threadBean.findDeadlockedThreads()?.size ?: 0
        sender.sendLang("command-debug-threads", threadBean.threadCount, threadBean.daemonThreadCount, threadBean.peakThreadCount, threadBean.totalStartedThreadCount, deadlocked)
    }

    private fun sendMemoryDebug(sender: CommandSender) {
        val runtime = Runtime.getRuntime()
        val max = bytesToMiB(runtime.maxMemory())
        val total = bytesToMiB(runtime.totalMemory())
        val free = bytesToMiB(runtime.freeMemory())
        val used = total - free
        val usedPercent = if (max > 0L) "${used * 100L / max}%" else "-"
        sender.sendLang("command-debug-memory", used, total, max, free, usedPercent)
        sender.sendLang("command-debug-memory-runtime", runtime.availableProcessors(), ManagementFactory.getRuntimeMXBean().uptime)
    }

    private fun sendDebugDump(sender: CommandSender) {
        TextDisplayMenuStatusCommand.sendStatus(sender)
        sendDebugInfo(sender)
        sendConfigDebug(sender)
        sendMemoryDebug(sender)
        sendThreadDebug(sender)
        sender.sendLang("command-debug-dump-menus", MenuRegistry.ids().sorted().joinToString(", ").ifBlank { "-" })
        val sessions = MenuSessionRegistry.all().joinToString(", ") { session ->
            val name = Bukkit.getPlayer(session.playerId)?.name ?: session.playerId.toString()
            "$name:${session.menuId}/${session.pageId},entities=${session.entityHandles.size},focus=${session.focusedElementKey ?: "-"}"
        }
        sender.sendLang("command-debug-dump-sessions", sessions.ifBlank { "-" })
    }

    private fun resetDebug(sender: CommandSender) {
        DebugRuntime.syncFromConfig()
        sender.sendLang("command-debug-reset", formatState(DebugRuntime.enabled()), formatState(DebugRuntime.packetLogging()), formatState(DebugRuntime.interactionLogging()))
        sendDebugEffective(sender)
    }

    private fun sendDebugPart(sender: CommandSender, part: String) {
        when (part.lowercase()) {
            "info" -> sendDebugInfo(sender)
            "summary" -> sendDebugSummary(sender)
            "menus", "menu" -> sendMenusDebug(sender)
            "players", "player" -> sendPlayersDebug(sender)
            "config" -> sendConfigDebug(sender)
            "nms" -> sendNMSDebug(sender)
            "threads" -> sendThreadDebug(sender)
            "memory" -> sendMemoryDebug(sender)
            "sessions", "session" -> sendSessionsDebug(sender)
            "dump" -> sendDebugDump(sender)
            "reset" -> resetDebug(sender)
            "on" -> setDebugEnabled(sender, true)
            "off" -> setDebugEnabled(sender, false)
            "packets" -> setPacketLogging(sender, null)
            "interactions" -> setInteractionLogging(sender, null)
            else -> sender.sendLang("command-debug-unknown", part)
        }
    }

    private fun setDebugEnabled(sender: CommandSender, value: Boolean) {
        sender.sendLang("command-debug-enabled", formatState(DebugRuntime.setEnabled(value)))
        sendDebugEffective(sender)
    }

    private fun setPacketLogging(sender: CommandSender, state: String?) {
        val enabled = state?.let { applyDebugState(it, DebugRuntime.packetLogging(), DebugRuntime::setPacketLogging, DebugRuntime::togglePacketLogging) } ?: DebugRuntime.togglePacketLogging()
        sender.sendLang("command-debug-packets", formatState(enabled))
        sendDebugEffective(sender)
    }

    private fun setInteractionLogging(sender: CommandSender, state: String?) {
        val enabled = state?.let { applyDebugState(it, DebugRuntime.interactionLogging(), DebugRuntime::setInteractionLogging, DebugRuntime::toggleInteractionLogging) } ?: DebugRuntime.toggleInteractionLogging()
        sender.sendLang("command-debug-interactions", formatState(enabled))
        sendDebugEffective(sender)
    }

    private fun sendDebugEffective(sender: CommandSender) {
        sender.sendLang(
            "command-debug-effective",
            formatState(DebugRuntime.enabled()),
            formatState(DebugRuntime.enabled() && DebugRuntime.packetLogging()),
            formatState(DebugRuntime.enabled() && DebugRuntime.interactionLogging()),
        )
    }

    private fun sendSessionDetails(sender: CommandSender, name: String, session: MenuSessionSnapshot) {
        val now = System.currentTimeMillis()
        val openedMs = now - session.openedAt
        val idleMs = now - session.lastActiveAt
        val arguments = session.arguments.joinToString(" ").ifBlank { "-" }
        sender.sendLang("command-debug-player", name, session.menuId, session.pageId, openedMs, idleMs, session.entityHandles.size, session.focusedElementKey ?: "-", arguments)
        sender.sendLang("command-debug-player-render", signatureSummary(session.renderSignature), handleSummary(session.entityHandles))
        sender.sendLang("command-debug-player-focus", session.focusedElementKey ?: "-", session.focusCandidateKey ?: "-", session.focusCandidateSinceTick, clickAge(session.lastClickAt))
        sender.sendLang("command-debug-player-feedback", handleSummary(session.hoverPopupHandle), session.hoverPopupElementKey ?: "-", session.hoverPopupExpiresAtTick, session.bossBarElementKey ?: "-", session.actionbarLastSentTick, session.titleLastSentTick)
        sender.sendLang("command-debug-player-history", listSummary(session.pageHistory))
        sendPendingConfirmation(sender, session.pendingConfirmation)
    }

    private fun sendPendingConfirmation(sender: CommandSender, confirmation: MenuPendingConfirmation?) {
        if (confirmation == null) {
            sender.sendLang("command-debug-player-confirmation-empty")
            return
        }
        val expiresIn = (confirmation.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        sender.sendLang(
            "command-debug-player-confirmation",
            confirmation.elementKey,
            confirmation.acceptClick,
            confirmation.cancelClick,
            expiresIn,
            confirmation.thenScripts.size,
            confirmation.elseScripts.size,
        )
    }

    private fun sendElementActions(sender: CommandSender, element: MenuElementDefinition) {
        val actions = listOf(
            "interact" to element.actions.interactClick,
            "left" to element.actions.leftClick,
            "right" to element.actions.rightClick,
            "shift_left" to element.actions.shiftLeftClick,
            "shift_right" to element.actions.shiftRightClick,
        )
        val activeActions = actions.filter { it.second.isNotEmpty() }
        if (activeActions.isEmpty()) {
            sender.sendLang("command-debug-element-actions-empty")
            return
        }
        activeActions.forEach { (name, scripts) ->
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

    private fun MenuDefinition.elementCount(): Int {
        return pages.sumOf { it.elements.size }
    }

    private fun MenuDefinition.actionCount(): Int {
        return pages.sumOf { it.actionCount() }
    }

    private fun MenuDefinition.hoverCount(): Int {
        return pages.sumOf { it.hoverCount() }
    }

    private fun MenuDefinition.typeCounts(): String {
        return pages.flatMap { it.elements }.typeCounts()
    }

    private fun MenuPageDefinition.actionCount(): Int {
        return elements.sumOf { it.actions.allScripts().size }
    }

    private fun MenuPageDefinition.interactiveCount(): Int {
        return elements.count { it.interactive != false }
    }

    private fun MenuPageDefinition.hoverCount(): Int {
        return elements.count { it.hasHover() }
    }

    private fun MenuPageDefinition.typeCounts(): String {
        return elements.typeCounts()
    }

    private fun MenuPageDefinition.renderTypeCounts(): String {
        return elements.groupingBy { it.prepared.displayType.name.lowercase() }.eachCount().formatCounts()
    }

    private fun List<MenuElementDefinition>.typeCounts(): String {
        return groupingBy { it.type.name.lowercase() }.eachCount().formatCounts()
    }

    private fun Map<String, Int>.formatCounts(): String {
        return entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }.ifBlank { "-" }
    }

    private fun MenuElementDefinition.hasHover(): Boolean {
        return hover.displayPopup != null || hover.actionbar.isNotBlank() || hover.title.isNotBlank() || hover.subtitle.isNotBlank() || hover.bossbar != null
    }

    private fun MenuElementDefinition.hoverFlags(): String {
        val flags = buildList {
            if (hover.displayPopup != null) add("popup")
            if (hover.actionbar.isNotBlank()) add("actionbar")
            if (hover.title.isNotBlank()) add("title")
            if (hover.subtitle.isNotBlank()) add("subtitle")
            if (hover.bossbar != null) add("bossbar")
        }
        return listSummary(flags)
    }

    private fun popupSummary(element: MenuElementDefinition): String {
        val popup = element.hover.displayPopup ?: return "-"
        return "text=${textLength(popup.text)},offset=${popup.offset.format()}"
    }

    private fun titleSummary(element: MenuElementDefinition): String {
        val title = textLength(element.hover.title)
        val subtitle = textLength(element.hover.subtitle)
        val times = element.hover.titleTimes
        return "title=$title,subtitle=$subtitle,times=${times.fadeIn}/${times.stay}/${times.fadeOut}"
    }

    private fun bossBarSummary(element: MenuElementDefinition): String {
        val bossbar = element.hover.bossbar ?: return "-"
        return "title=${textLength(bossbar.title)},color=${bossbar.color},style=${bossbar.style},progress=${bossbar.progress},prepared=${element.prepared.hover.bossbarColor.name}/${element.prepared.hover.bossbarStyle.name}"
    }

    private fun formatHitbox(element: MenuElementDefinition): String {
        return element.hitbox?.let { "${it.width}x${it.height}" } ?: "-"
    }

    private fun formatScale(element: MenuElementDefinition): String {
        return element.scale?.let { "${it.x},${it.y},${it.z}" } ?: "-"
    }

    private fun formatInteractive(element: MenuElementDefinition): String {
        return element.interactive?.toString() ?: "default"
    }

    private fun formatNullable(value: Double?): String {
        return value?.toString() ?: "default"
    }

    private fun formatNullable(value: Boolean?): String {
        return value?.toString() ?: "default"
    }

    private fun textLength(value: String): String {
        return if (value.isBlank()) "-" else "len=${value.length}"
    }

    private fun listSummary(values: Collection<String>, limit: Int = 8): String {
        if (values.isEmpty()) {
            return "-"
        }
        val visible = values.take(limit).joinToString(",")
        val remaining = values.size - limit
        return if (remaining > 0) "$visible,+$remaining" else visible
    }

    private fun signatureSummary(signature: List<Pair<String, DisplayEntityType>>): String {
        return listSummary(signature.map { "${it.first}:${it.second.name.lowercase()}" }, limit = 12)
    }

    private fun handleSummary(handles: Map<String, DisplayEntityHandle>): String {
        return listSummary(handles.entries.map { "${it.key}:${it.value.entityId}/${it.value.type.name.lowercase()}" }, limit = 12)
    }

    private fun handleSummary(handle: DisplayEntityHandle?): String {
        return handle?.let { "${it.entityId}/${it.type.name.lowercase()}" } ?: "-"
    }

    private fun clickAge(lastClickAt: Long): String {
        return if (lastClickAt <= 0L) "-" else "${System.currentTimeMillis() - lastClickAt}ms"
    }
}
