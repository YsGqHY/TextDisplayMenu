package cc.bkhk.display.menu.nms

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * 未实现版本的安全占位发包实现，避免错误加载具体 NMS 类导致插件崩溃。
 */
class UnsupportedNMSDisplayPacket(private val profile: NMSRuntimeProfile = NMSRuntimeSupport.current) : NMSDisplayPacket() {

    override fun spawnTextDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: TextDisplayOptions,
    ): DisplayEntityHandle {
        unsupported("spawnTextDisplay")
    }

    override fun spawnItemDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: ItemDisplayOptions,
    ): DisplayEntityHandle {
        unsupported("spawnItemDisplay")
    }

    override fun spawnBlockDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: BlockDisplayOptions,
    ): DisplayEntityHandle {
        unsupported("spawnBlockDisplay")
    }

    override fun teleportDisplay(player: Player, handle: DisplayEntityHandle, location: Location, transform: DisplayTransform) {
        unsupported("teleportDisplay")
    }

    override fun updateTextDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: TextDisplayOptions) {
        unsupported("updateTextDisplay")
    }

    override fun updateItemDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: ItemDisplayOptions) {
        unsupported("updateItemDisplay")
    }

    override fun updateBlockDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: BlockDisplayOptions) {
        unsupported("updateBlockDisplay")
    }

    override fun destroyDisplay(player: Player, handle: DisplayEntityHandle) {
        // unsupported 分支不会生成本插件的 Display 句柄，销毁可安全忽略。
    }

    override fun destroyDisplays(player: Player, handles: Collection<DisplayEntityHandle>) {
        // unsupported 分支不会生成本插件的 Display 句柄，销毁可安全忽略。
    }

    override fun destroyAllDisplays(player: Player) {
        // unsupported 分支不会生成本插件的 Display 句柄，销毁可安全忽略。
    }

    override fun sendDisplayBatch(player: Player, operations: List<DisplayPacketOperation>): List<DisplayEntityHandle> {
        if (operations.all { it is DisplayPacketOperation.Destroy || it is DisplayPacketOperation.DestroyMany }) {
            return emptyList()
        }
        unsupported("sendDisplayBatch")
    }

    private fun unsupported(operation: String): Nothing {
        throw UnsupportedNMSDisplayPacketException("NMS Display 发包未支持当前运行版本，operation=$operation，${profile.summary()}")
    }
}
