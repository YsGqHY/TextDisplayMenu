package cc.bkhk.display.menu.render

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.function.throttle
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.module.nms.PacketReceiveEvent
import java.util.UUID

object MenuRenderListener {

    private val viewUpdateThrottle = throttle<UUID>(50L) { playerId ->
        val player = Bukkit.getPlayer(playerId) ?: return@throttle
        submit {
            MenuRenderService.updateForPlayerMotion(player, "look_packet")
        }
    }

    fun clearViewThrottle(playerId: UUID) {
        viewUpdateThrottle.removeKey(playerId)
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        clearViewThrottle(event.player.uniqueId)
        MenuRenderService.close(event.player)
    }

    @SubscribeEvent
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (PluginConfig.snapshot().session.closeOnWorldChange) {
            MenuRenderService.close(event.player)
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (PluginConfig.snapshot().session.closeOnTeleport) {
            MenuRenderService.close(event.player)
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (MenuSessionRegistry.current(event.player) == null) {
            return
        }
        val to = event.to ?: return
        if (MenuRenderService.shouldLockPosition(event.player) && hasPositionChanged(event.from, to)) {
            event.setTo(event.from.clone().also {
                it.yaw = to.yaw
                it.pitch = to.pitch
            })
            MenuRenderService.updateForPlayerMotion(event.player, "lock_position")
            return
        }
        if (!hasPositionChanged(event.from, to)) {
            return
        }
        if (!MenuRenderService.shouldFollowPosition(event.player)) {
            return
        }
        MenuRenderService.updateForPlayerMotion(event.player, "move")
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceiveEvent) {
        if (MenuSessionRegistry.current(event.player) == null) {
            return
        }
        if (!MenuClientPacketMatcher.isLookPacket(event.packet)) {
            return
        }
        if (MenuRenderService.shouldLockView(event.player)) {
            event.isCancelled = true
            MenuRenderService.updateForPlayerMotion(event.player, "lock_view")
            return
        }
        if (!MenuRenderService.shouldFollowView(event.player)) {
            return
        }
        val delay = PluginConfig.snapshot().render.viewPacketThrottleMs.coerceAtLeast(0L)
        viewUpdateThrottle(event.player.uniqueId, delay)
    }

    @SubscribeEvent
    fun onDeath(event: PlayerDeathEvent) {
        if (PluginConfig.snapshot().session.closeOnDeath) {
            MenuRenderService.close(event.entity)
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode == GameMode.SPECTATOR && PluginConfig.snapshot().session.closeOnSpectator) {
            MenuRenderService.close(event.player)
        }
    }

    private fun hasPositionChanged(from: Location, to: Location): Boolean {
        if (from.world != to.world) {
            return true
        }
        return from.x != to.x || from.y != to.y || from.z != to.z
    }
}
