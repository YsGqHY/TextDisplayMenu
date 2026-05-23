package cc.bkhk.display.menu.interaction.shortcut

import cc.bkhk.display.menu.config.PluginConfig
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object MenuShortcutListener {

    @SubscribeEvent(ignoreCancelled = true)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        MenuShortcutService.markSneaking(event.player, event.isSneaking)
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        MenuShortcutService.clear(event.player.uniqueId)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val key = MenuShortcutService.chooseKey(event.player, MenuShortcutKey.OFFHAND, MenuShortcutKey.SNEAKING_OFFHAND)
        if (MenuShortcutService.handle(MenuShortcutTrigger(event.player, key))) {
            cancel(event)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != null && event.hand != EquipmentSlot.HAND) {
            return
        }
        val key = when (event.action) {
            Action.LEFT_CLICK_AIR -> MenuShortcutKey.LEFT_CLICK_AIR
            Action.RIGHT_CLICK_AIR -> MenuShortcutService.chooseKey(event.player, MenuShortcutKey.RIGHT_CLICK_AIR, MenuShortcutKey.SNEAKING_RIGHT_CLICK_AIR)
            else -> return
        }
        if (MenuShortcutService.handle(MenuShortcutTrigger(event.player, key))) {
            cancel(event)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        val target = event.rightClicked as? Player ?: return
        val key = MenuShortcutService.chooseKey(event.player, MenuShortcutKey.RIGHT_CLICK_PLAYER, MenuShortcutKey.SNEAKING_RIGHT_CLICK_PLAYER)
        if (MenuShortcutService.handle(MenuShortcutTrigger(event.player, key, target.name, target.uniqueId))) {
            cancel(event)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAnimation(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) {
            return
        }
        if (MenuShortcutService.handleAnimation(MenuShortcutTrigger(event.player, MenuShortcutKey.LEFT_CLICK_AIR))) {
            cancel(event)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        if (MenuShortcutService.handle(MenuShortcutTrigger(event.player, MenuShortcutKey.DROP_ITEM))) {
            cancel(event)
        }
    }

    private fun cancel(event: org.bukkit.event.Cancellable) {
        if (PluginConfig.snapshot().shortcut.conditions.cancelEvent) {
            event.isCancelled = true
        }
    }
}
