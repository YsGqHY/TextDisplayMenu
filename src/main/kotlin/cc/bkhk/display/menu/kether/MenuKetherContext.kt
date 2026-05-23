package cc.bkhk.display.menu.kether

import cc.bkhk.display.menu.interaction.MenuClickType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyPlayer
import taboolib.module.kether.ScriptFrame
import taboolib.module.kether.script
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MenuKetherContext {

    private val contextByPlayer = ConcurrentHashMap<UUID, MenuActionContext>()

    fun current(): MenuActionContext? {
        return contextByPlayer.values.firstOrNull()
    }

    fun current(frame: ScriptFrame): MenuActionContext? {
        val sender = frame.script().sender as? ProxyPlayer ?: return null
        return contextByPlayer[sender.uniqueId]?.takeIf { it.player.isOnline }
    }

    fun current(player: Player): MenuActionContext? {
        return contextByPlayer[player.uniqueId]
    }

    fun enter(value: MenuActionContext): MenuActionContext? {
        return contextByPlayer.put(value.player.uniqueId, value)
    }

    fun restore(current: MenuActionContext, previous: MenuActionContext?) {
        if (previous == null) {
            contextByPlayer.remove(current.player.uniqueId, current)
        } else {
            contextByPlayer[previous.player.uniqueId] = previous
        }
        cleanupOffline()
    }

    fun restore(previous: MenuActionContext?) {
        if (previous != null) {
            contextByPlayer[previous.player.uniqueId] = previous
        }
        cleanupOffline()
    }

    fun clear(player: Player) {
        contextByPlayer.remove(player.uniqueId)
    }

    fun cleanupOffline() {
        contextByPlayer.keys.removeIf { Bukkit.getPlayer(it) == null }
    }
}

data class MenuActionContext(
    val player: Player,
    val menuId: String = "",
    val pageId: String = "",
    val elementKey: String? = null,
    val elementId: String? = null,
    val clickType: MenuClickType? = null,
    val source: String = "menu",
    val shortcutKey: String? = null,
    val targetName: String? = null,
    val targetUuid: UUID? = null,
)
