package cc.bkhk.display.menu.interaction

import cc.bkhk.display.menu.config.DebugRuntime
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.feedback.MenuFeedbackService
import cc.bkhk.display.menu.kether.MenuActionContext
import cc.bkhk.display.menu.kether.MenuActionExecutor
import cc.bkhk.display.menu.kether.MenuKetherContext
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.menu.model.MenuControlsDefinition
import cc.bkhk.display.menu.menu.model.MenuElementActions
import cc.bkhk.display.menu.render.MenuRenderService
import cc.bkhk.display.menu.session.MenuSession
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import java.util.concurrent.atomic.AtomicBoolean

object MenuInteractionService {

    private val started = AtomicBoolean(false)

    fun initialize() {
        started.set(true)
    }

    fun shutdown() {
        Bukkit.getOnlinePlayers().forEach { MenuFeedbackService.clear(it) }
        started.set(false)
    }

    fun restartTask() {
        MenuSessionRegistry.all().forEach { snapshot ->
            val player = Bukkit.getPlayer(snapshot.playerId) ?: return@forEach
            val session = MenuSessionRegistry.session(player) ?: return@forEach
            startSessionTask(player, session)
        }
    }

    fun startSessionTask(player: Player, session: MenuSession) {
        if (!started.get()) {
            return
        }
        val interval = PluginConfig.snapshot().interaction.detectionIntervalTicks.coerceAtLeast(1L)
        session.arrange(
            submit(delay = interval, period = interval) {
                tickFocus(player.uniqueId)
            },
            temporary = true,
        )
    }

    fun handleClick(player: Player, clickType: MenuClickType): Boolean {
        val config = PluginConfig.snapshot().interaction
        if (!isClickEnabled(clickType)) {
            return false
        }
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        val now = System.currentTimeMillis()
        if (now - snapshot.lastClickAt < config.clickCooldownMs) {
            return true
        }
        val frame = MenuRenderService.currentFrame(player) ?: return false
        val focusedKey = snapshot.focusedElementKey
        val element = focusedKey?.let { frame.element(it) }
        if (focusedKey != null && MenuActionExecutor.handlePendingConfirmation(player, focusedKey, clickType)) {
            MenuSessionRegistry.updateClick(player, now)
            return true
        }
        val scripts = element?.let { actionsFor(it.element.actions, clickType) }.orEmpty()
        if (scripts.isNotEmpty() && element != null) {
            MenuSessionRegistry.updateClick(player, now)
            MenuActionExecutor.execute(player, element.key, element.elementId, clickType, scripts)
            logInteraction("click player=${player.name} type=${clickType.configKey} element=${element.elementId}")
            return true
        }
        if (handleMenuControls(player, menu.controls, clickType, snapshot.menuId, snapshot.pageId, now)) {
            return true
        }
        return false
    }

    fun handleEntityPacketClick(player: Player, entityId: Int): Boolean {
        if (!isClickEnabled(MenuClickType.INTERACT)) {
            return false
        }
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val elementKey = snapshot.entityHandles.entries.firstOrNull { (_, handle) -> handle.entityId == entityId }?.key ?: return false
        val frame = MenuRenderService.currentFrame(player) ?: return false
        val element = frame.element(elementKey)?.takeIf { it.interactive } ?: return false
        MenuSessionRegistry.setFocusedElement(player, element.key)
        return handleClick(player, MenuClickType.INTERACT)
    }

    private fun tickFocus(playerId: java.util.UUID) {
        MenuKetherContext.cleanupOffline()
        val player = Bukkit.getPlayer(playerId) ?: return
        val session = MenuSessionRegistry.session(player) ?: return
        val frame = session.currentFrame ?: return
        val interval = PluginConfig.snapshot().interaction.detectionIntervalTicks.coerceAtLeast(1L)
        val tick = session.nextInteractionTick(interval)
        MenuFeedbackService.tickPopupLifetime(player, tick)
        val snapshot = session.snapshot() ?: return
        val hit = MenuHitDetector.detect(player, frame)
        val candidateKey = hit?.element?.key
        MenuSessionRegistry.updateFocusCandidate(player, candidateKey, tick)
        val updated = session.snapshot() ?: snapshot
        val focusedKey = if (candidateKey != null && updated.focusCandidateKey == candidateKey) {
            val stayed = tick - updated.focusCandidateSinceTick
            if (stayed >= PluginConfig.snapshot().interaction.focusStayTicks) candidateKey else updated.focusedElementKey
        } else {
            null
        }
        if (focusedKey != updated.focusedElementKey) {
            MenuSessionRegistry.setFocusedElement(player, focusedKey)
            MenuFeedbackService.onFocusChange(player, updated.focusedElementKey, focusedKey, frame, tick)
            logInteraction("focus player=${player.name} element=${focusedKey ?: "-"}")
        } else {
            MenuFeedbackService.onFocusChange(player, focusedKey, focusedKey, frame, tick)
        }
    }

    private fun handleMenuControls(
        player: Player,
        controls: MenuControlsDefinition,
        clickType: MenuClickType,
        menuId: String,
        pageId: String,
        now: Long,
    ): Boolean {
        val clickKey = clickType.configKey
        val scripts = when {
            controls.closeClicks.any { it.equals(clickKey, ignoreCase = true) } -> listOf("tdmenu close")
            controls.backClicks.any { it.equals(clickKey, ignoreCase = true) } -> {
                val backMenu = controls.backMenu.trim()
                if (backMenu.isNotEmpty()) listOf("tdmenu open \"$backMenu\"") else listOf("tdmenu back")
            }
            else -> emptyList()
        }
        if (scripts.isEmpty()) {
            return false
        }
        MenuSessionRegistry.updateClick(player, now)
        MenuActionExecutor.executeDirect(
            MenuActionContext(
                player = player,
                menuId = menuId,
                pageId = pageId,
                clickType = clickType,
                source = "controls",
            ),
            scripts,
        )
        logInteraction("control player=${player.name} type=$clickKey scripts=${scripts.size}")
        return true
    }

    private fun actionsFor(actions: MenuElementActions, clickType: MenuClickType): List<String> {
        return when (clickType) {
            MenuClickType.INTERACT -> actions.interactClick.ifEmpty { actions.leftClick }
            MenuClickType.LEFT -> actions.leftClick
            MenuClickType.RIGHT -> actions.rightClick
            MenuClickType.SHIFT_LEFT -> actions.shiftLeftClick
            MenuClickType.SHIFT_RIGHT -> actions.shiftRightClick
        }
    }

    private fun isClickEnabled(clickType: MenuClickType): Boolean {
        val input = PluginConfig.snapshot().interaction.input
        return when (clickType) {
            MenuClickType.INTERACT -> input.interactClick
            MenuClickType.LEFT -> input.leftClick
            MenuClickType.RIGHT -> input.rightClick
            MenuClickType.SHIFT_LEFT,
            MenuClickType.SHIFT_RIGHT -> input.shiftClick
        }
    }

    private fun logInteraction(message: String) {
        if (DebugRuntime.enabled() && DebugRuntime.interactionLogging()) {
            info("[TextDisplayMenu Debug] $message")
        }
    }
}
