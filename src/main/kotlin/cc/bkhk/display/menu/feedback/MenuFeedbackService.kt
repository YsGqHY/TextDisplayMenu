package cc.bkhk.display.menu.feedback

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.model.MenuBossBarDefinition
import cc.bkhk.display.menu.nms.DisplayPacketOperation
import cc.bkhk.display.menu.nms.NMSDisplayPacket
import cc.bkhk.display.menu.render.MenuRenderElementFrame
import cc.bkhk.display.menu.render.MenuRenderFrame
import cc.bkhk.display.menu.render.MenuRenderFrameBuilder
import cc.bkhk.display.menu.session.MenuSessionRegistry
import cc.bkhk.display.menu.session.MenuSessionSnapshot
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import taboolib.module.chat.colored
import taboolib.platform.util.sendActionBar
import taboolib.platform.util.title

object MenuFeedbackService {

    fun onFocusChange(player: Player, oldKey: String?, newKey: String?, frame: MenuRenderFrame, tick: Long) {
        val snapshot = MenuSessionRegistry.current(player) ?: return
        if (oldKey == newKey) {
            newKey?.let { sendRepeatingFeedback(player, snapshot, frame.element(it), frame, tick) }
            return
        }
        oldKey?.let { restoreElementText(player, snapshot, frame.element(it)) }
        clearPopup(player, snapshot)
        clearBossBar(player, snapshot)
        val focused = newKey?.let { frame.element(it) }
        if (focused != null) {
            applyFocusedText(player, snapshot, focused)
            sendFocusFeedback(player, focused, frame, tick, focusChanged = true)
        }
    }

    fun sendRepeatingFeedback(player: Player, snapshot: MenuSessionSnapshot, element: MenuRenderElementFrame?, frame: MenuRenderFrame, tick: Long) {
        if (element == null) {
            return
        }
        sendFocusFeedback(player, element, frame, tick, focusChanged = false, snapshot = snapshot)
    }

    fun clear(player: Player) {
        val snapshot = MenuSessionRegistry.current(player) ?: return
        clearPopup(player, snapshot)
        clearBossBar(player, snapshot)
    }

    private fun applyFocusedText(player: Player, snapshot: MenuSessionSnapshot, element: MenuRenderElementFrame) {
        if (element.type != cc.bkhk.display.menu.nms.DisplayEntityType.TEXT || element.element.focusedText.isBlank()) {
            return
        }
        val handle = snapshot.entityHandles[element.key] ?: return
        NMSDisplayPacket.instance.updateTextDisplay(player, handle, element.transform, MenuRenderFrameBuilder.createTextOptions(element.element, focused = true))
    }

    private fun restoreElementText(player: Player, snapshot: MenuSessionSnapshot, element: MenuRenderElementFrame?) {
        if (element == null || element.type != cc.bkhk.display.menu.nms.DisplayEntityType.TEXT) {
            return
        }
        val handle = snapshot.entityHandles[element.key] ?: return
        NMSDisplayPacket.instance.updateTextDisplay(player, handle, element.transform, MenuRenderFrameBuilder.createTextOptions(element.element, focused = false))
    }

    private fun sendFocusFeedback(
        player: Player,
        element: MenuRenderElementFrame,
        frame: MenuRenderFrame,
        tick: Long,
        focusChanged: Boolean,
        snapshot: MenuSessionSnapshot? = MenuSessionRegistry.current(player),
    ) {
        snapshot ?: return
        val config = PluginConfig.snapshot()
        val feedback = config.interaction.focusFeedback
        if (!feedback.enabled) {
            return
        }
        if (focusChanged && feedback.playSound) {
            runCatching { Sound.valueOf(feedback.sound.uppercase()) }
                .onSuccess { player.playSound(player.location, it, feedback.volume, feedback.pitch) }
        }
        val hover = element.element.hover
        if (feedback.displayPopupEnabled && hover.displayPopup != null && hover.displayPopup.text.isNotBlank()) {
            showPopup(player, snapshot, element, frame, tick)
        }
        if (feedback.actionbarEnabled && hover.actionbar.isNotBlank() && tick - snapshot.actionbarLastSentTick >= feedback.actionbarIntervalTicks) {
            player.sendActionBar(hover.actionbar.colored())
            MenuSessionRegistry.markActionbarSent(player, tick)
        }
        if (feedback.titleEnabled && (hover.title.isNotBlank() || hover.subtitle.isNotBlank()) && tick - snapshot.titleLastSentTick >= feedback.titleIntervalTicks) {
            val times = hover.titleTimes
            player.title(hover.title.colored().ifBlank { null }, hover.subtitle.colored().ifBlank { null }, times.fadeIn, times.stay, times.fadeOut)
            MenuSessionRegistry.markTitleSent(player, tick)
        }
        if (feedback.bossbarEnabled && hover.bossbar != null && hover.bossbar.title.isNotBlank()) {
            showBossBar(player, snapshot, element, hover.bossbar, tick)
        }
    }

    private fun showPopup(player: Player, snapshot: MenuSessionSnapshot, element: MenuRenderElementFrame, frame: MenuRenderFrame, tick: Long) {
        val feedback = PluginConfig.snapshot().interaction.focusFeedback
        val popup = element.element.hover.displayPopup ?: return
        val expiresAt = tick + feedback.displayPopupLifetimeTicks
        if (snapshot.hoverPopupElementKey == element.key && snapshot.hoverPopupHandle != null) {
            MenuSessionRegistry.setHoverPopup(player, snapshot.hoverPopupHandle, element.key, expiresAt)
            return
        }
        clearPopup(player, snapshot)
        val offset = popup.offset
        val location = element.location.clone()
            .add(frame.basis.right.clone().multiply(offset.x / MenuRenderFrameBuilder.PIXELS_PER_BLOCK))
            .add(frame.basis.up.clone().multiply(-offset.y / MenuRenderFrameBuilder.PIXELS_PER_BLOCK))
            .add(frame.basis.forward.clone().multiply(offset.z))
        val transform = element.transform.copy(scaleX = element.transform.scaleX * 0.85f, scaleY = element.transform.scaleY * 0.85f, scaleZ = element.transform.scaleZ * 0.85f)
        val handle = NMSDisplayPacket.instance.sendDisplayBatch(
            player,
            listOf(DisplayPacketOperation.SpawnText(location, transform, MenuRenderFrameBuilder.createTextOptions(element.element.copy(text = popup.text), focused = false))),
        ).firstOrNull()
        MenuSessionRegistry.setHoverPopup(player, handle, element.key, expiresAt)
    }

    fun tickPopupLifetime(player: Player, tick: Long) {
        val snapshot = MenuSessionRegistry.current(player) ?: return
        if (snapshot.hoverPopupHandle != null && snapshot.hoverPopupExpiresAtTick in 1..tick) {
            clearPopup(player, snapshot)
        }
    }

    private fun clearPopup(player: Player, snapshot: MenuSessionSnapshot) {
        val handle = snapshot.hoverPopupHandle ?: return
        NMSDisplayPacket.instance.destroyDisplay(player, handle)
        MenuSessionRegistry.setHoverPopup(player, null, null, 0L)
    }

    private fun showBossBar(player: Player, snapshot: MenuSessionSnapshot, element: MenuRenderElementFrame, config: MenuBossBarDefinition, tick: Long) {
        val interval = PluginConfig.snapshot().interaction.focusFeedback.bossbarIntervalTicks
        if (snapshot.bossBarElementKey == element.key && snapshot.bossBar != null) {
            if (tick - snapshot.bossBarLastSentTick >= interval) {
                snapshot.bossBar.setTitle(config.title.colored())
                snapshot.bossBar.progress = config.progress.coerceIn(0.0, 1.0)
                MenuSessionRegistry.setBossBar(player, snapshot.bossBar, element.key, tick)
            }
            return
        }
        clearBossBar(player, snapshot)
        val bossBar = Bukkit.createBossBar(config.title.colored(), parseColor(config.color), parseStyle(config.style))
        bossBar.progress = config.progress.coerceIn(0.0, 1.0)
        bossBar.addPlayer(player)
        MenuSessionRegistry.setBossBar(player, bossBar, element.key, tick)
    }

    private fun clearBossBar(player: Player, snapshot: MenuSessionSnapshot) {
        val bossBar = snapshot.bossBar ?: return
        bossBar.removePlayer(player)
        bossBar.removeAll()
        MenuSessionRegistry.setBossBar(player, null, null, 0L)
    }

    private fun parseColor(input: String): BarColor {
        return runCatching { BarColor.valueOf(input.uppercase()) }.getOrDefault(BarColor.WHITE)
    }

    private fun parseStyle(input: String): BarStyle {
        return runCatching { BarStyle.valueOf(input.uppercase()) }.getOrDefault(BarStyle.SOLID)
    }
}
