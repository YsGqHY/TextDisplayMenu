package cc.bkhk.display.menu.render

import cc.bkhk.display.menu.nms.DisplayEntityHandle
import cc.bkhk.display.menu.nms.NMSDisplayPacket
import cc.bkhk.display.menu.nms.UnsupportedNMSDisplayPacketException
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 追踪所有已经向玩家客户端发送过的 Display 实体句柄。
 *
 * Display 是纯发包实体，服务端没有真实实体兜底；创建后必须登记，任何关闭、重载、失控路径都通过这里销毁。
 */
object MenuDisplayEntityTracker {

    private val tracked = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, DisplayEntityHandle>>()

    fun register(player: Player, handles: Collection<DisplayEntityHandle>, reason: String = "register") {
        if (handles.isEmpty()) {
            return
        }
        val map = tracked.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        handles.forEach { handle -> map[handle.entityId] = handle }
        logStateIssue(player.uniqueId, reason, null)
    }

    fun register(player: Player, handle: DisplayEntityHandle?, reason: String = "register") {
        if (handle != null) {
            register(player, listOf(handle), reason)
        }
    }

    fun destroy(player: Player, handles: Collection<DisplayEntityHandle>, reason: String = "destroy"): Int {
        val targets = trackedTargets(player.uniqueId, handles)
        if (targets.isEmpty()) {
            return 0
        }
        return if (sendDestroy(player, targets, reason)) {
            forget(player.uniqueId, targets)
            targets.size
        } else {
            0
        }
    }

    fun destroy(player: Player, handle: DisplayEntityHandle?, reason: String = "destroy"): Int {
        return if (handle == null) 0 else destroy(player, listOf(handle), reason)
    }

    fun destroyAll(player: Player, reason: String = "destroy_all"): Int {
        val playerId = player.uniqueId
        val targets = tracked[playerId]?.values.orEmpty().distinctBy { it.entityId }
        var destroyed = 0
        if (targets.isNotEmpty() && sendDestroy(player, targets, reason)) {
            forget(playerId, targets)
            destroyed += targets.size
        }
        if (sendDestroyAll(player, reason)) {
            tracked.remove(playerId)
        }
        return destroyed
    }

    fun forget(playerId: UUID, handles: Collection<DisplayEntityHandle>) {
        val map = tracked[playerId] ?: return
        handles.forEach { handle -> map.remove(handle.entityId, handle) }
        if (map.isEmpty()) {
            tracked.remove(playerId, map)
        }
    }

    fun forgetAll(playerId: UUID) {
        tracked.remove(playerId)
    }

    fun destroyAllOnline(reason: String = "destroy_all_online"): Int {
        return Bukkit.getOnlinePlayers().sumOf { player -> destroyAll(player, reason) }
    }

    fun count(playerId: UUID): Int {
        return tracked[playerId]?.size ?: 0
    }

    fun totalCount(): Int {
        return tracked.values.sumOf { it.size }
    }

    private fun trackedTargets(playerId: UUID, handles: Collection<DisplayEntityHandle>): List<DisplayEntityHandle> {
        val map = tracked[playerId] ?: return emptyList()
        return handles.asSequence()
            .filter { handle -> map[handle.entityId] == handle }
            .distinctBy { it.entityId }
            .toList()
    }

    private fun sendDestroy(player: Player, handles: Collection<DisplayEntityHandle>, reason: String): Boolean {
        return runCatching {
            NMSDisplayPacket.instance.destroyDisplays(player, handles)
        }.onFailure { throwable ->
            logStateIssue(player.uniqueId, reason, throwable)
        }.isSuccess
    }

    private fun sendDestroyAll(player: Player, reason: String): Boolean {
        return runCatching {
            NMSDisplayPacket.instance.destroyAllDisplays(player)
        }.onFailure { throwable ->
            logStateIssue(player.uniqueId, "$reason:fallback", throwable)
        }.isSuccess
    }

    private fun logStateIssue(playerId: UUID, reason: String, throwable: Throwable?) {
        if (throwable == null) {
            return
        }
        if (throwable is UnsupportedNMSDisplayPacketException) {
            return
        }
        warning("TextDisplayMenu Display 实体清理失败: player=$playerId, reason=$reason, error=${throwable.message ?: throwable.javaClass.simpleName}")
    }
}
