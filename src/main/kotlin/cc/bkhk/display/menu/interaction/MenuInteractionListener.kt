package cc.bkhk.display.menu.interaction

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.module.nms.PacketReceiveEvent

object MenuInteractionListener {

    @SubscribeEvent(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (MenuSessionRegistry.current(event.player) == null) {
            return
        }
        val clickType = when (event.action) {
            Action.LEFT_CLICK_AIR,
            Action.LEFT_CLICK_BLOCK -> if (event.player.isSneaking) MenuClickType.SHIFT_LEFT else MenuClickType.LEFT
            Action.RIGHT_CLICK_AIR,
            Action.RIGHT_CLICK_BLOCK -> {
                if (!event.player.isSneaking && isInteractEvent(event.action) && MenuInteractionService.handleClick(event.player, MenuClickType.INTERACT)) {
                    event.isCancelled = true
                    return
                }
                if (event.player.isSneaking) MenuClickType.SHIFT_RIGHT else MenuClickType.RIGHT
            }
            else -> return
        }
        val handled = MenuInteractionService.handleClick(event.player, clickType)
        if (handled) {
            event.isCancelled = true
        }
    }

    private fun isInteractEvent(action: Action): Boolean {
        val key = when (action) {
            Action.RIGHT_CLICK_AIR -> "right_click_air"
            Action.RIGHT_CLICK_BLOCK -> "right_click_block"
            Action.LEFT_CLICK_AIR -> "left_click_air"
            Action.LEFT_CLICK_BLOCK -> "left_click_block"
            else -> ""
        }
        return PluginConfig.snapshot().interaction.input.interactEvents.any { it.equals(key, ignoreCase = true) }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (MenuSessionRegistry.current(event.player) == null) {
            return
        }
        val allowed = PluginConfig.snapshot().interaction.input.interactEvents.any { it.equals("swap_hand", ignoreCase = true) }
        if (!allowed) {
            return
        }
        val handled = MenuInteractionService.handleClick(event.player, MenuClickType.INTERACT)
        if (handled) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceiveEvent) {
        val snapshot = MenuSessionRegistry.current(event.player) ?: return
        val entityId = MenuInteractionPacketMatcher.readInteractEntityId(event.packet) ?: return
        if (snapshot.entityHandles.values.none { handle -> handle.entityId == entityId }) {
            return
        }
        event.isCancelled = true
        submit {
            MenuInteractionService.handleEntityPacketClick(event.player, entityId)
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onAnimation(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) {
            return
        }
        if (MenuSessionRegistry.current(event.player) == null) {
            return
        }
        val clickType = if (event.player.isSneaking) MenuClickType.SHIFT_LEFT else MenuClickType.LEFT
        if (MenuInteractionService.handleClick(event.player, clickType)) {
            event.isCancelled = true
        }
    }
}
