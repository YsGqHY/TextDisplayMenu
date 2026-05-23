package cc.bkhk.display.menu.nms

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info
import taboolib.common.util.unsafeLazy
import taboolib.module.nms.nmsProxy
import java.util.UUID

/**
 * Display 展示实体类型。
 */
enum class DisplayEntityType {
    TEXT,
    ITEM,
    BLOCK
}

/**
 * 客户端展示实体句柄，业务层只需要记录该对象，不直接接触 NMS 实例。
 */
data class DisplayEntityHandle(
    val entityId: Int,
    val uuid: UUID,
    val type: DisplayEntityType,
)

/**
 * Display billboard 约束。
 */
enum class DisplayBillboard(val metadataId: Byte) {
    FIXED(0),
    VERTICAL(1),
    HORIZONTAL(2),
    CENTER(3),
}

/**
 * 物品展示方式，对应原版 ItemDisplayContext。
 */
enum class DisplayItemTransform(val key: String) {
    NONE("none"),
    THIRD_PERSON_LEFT_HAND("thirdperson_lefthand"),
    THIRD_PERSON_RIGHT_HAND("thirdperson_righthand"),
    FIRST_PERSON_LEFT_HAND("firstperson_lefthand"),
    FIRST_PERSON_RIGHT_HAND("firstperson_righthand"),
    HEAD("head"),
    GUI("gui"),
    GROUND("ground"),
    FIXED("fixed"),
}

/**
 * 文本对齐方式。
 */
enum class DisplayTextAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * Display 亮度覆盖。原版范围通常为 0..15，传入 null 表示不覆盖。
 */
data class DisplayBrightness(
    val blockLight: Int,
    val skyLight: Int,
) {

    val packed: Int
        get() {
            val block = blockLight.coerceIn(0, 15)
            val sky = skyLight.coerceIn(0, 15)
            return block shl 4 or (sky shl 20)
        }
}

/**
 * Display 通用变换和渲染元数据。
 */
data class DisplayTransform(
    val translationX: Float = 0.0f,
    val translationY: Float = 0.0f,
    val translationZ: Float = 0.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val scaleZ: Float = 1.0f,
    val billboard: DisplayBillboard = DisplayBillboard.CENTER,
    val brightness: DisplayBrightness? = null,
    val viewRange: Float = 1.0f,
    val shadowRadius: Float = 0.0f,
    val shadowStrength: Float = 0.0f,
    val width: Float = 0.0f,
    val height: Float = 0.0f,
    val glowColorOverride: Int = -1,
    val interpolationDelayTicks: Int = 0,
    val interpolationDurationTicks: Int = 2,
)

/**
 * TextDisplay 文本元数据。
 */
data class TextDisplayOptions(
    val text: String,
    val textIsJson: Boolean = false,
    val lineWidth: Int = 220,
    val backgroundColor: Int = 0x66000000,
    val textOpacity: Int = 255,
    val shadowed: Boolean = true,
    val seeThrough: Boolean = false,
    val useDefaultBackground: Boolean = false,
    val alignment: DisplayTextAlignment = DisplayTextAlignment.CENTER,
)

/**
 * ItemDisplay 物品元数据。
 */
data class ItemDisplayOptions(
    val itemStack: ItemStack = ItemStack(Material.PAPER),
    val transform: DisplayItemTransform = DisplayItemTransform.GUI,
)

/**
 * BlockDisplay 方块元数据。
 */
data class BlockDisplayOptions(
    val blockData: BlockData = Material.STONE.createBlockData(),
)

/**
 * 批量发包操作。按列表顺序构建数据包，最后统一发送。
 */
sealed class DisplayPacketOperation {

    data class SpawnText(
        val location: Location,
        val transform: DisplayTransform,
        val options: TextDisplayOptions,
    ) : DisplayPacketOperation()

    data class SpawnItem(
        val location: Location,
        val transform: DisplayTransform,
        val options: ItemDisplayOptions,
    ) : DisplayPacketOperation()

    data class SpawnBlock(
        val location: Location,
        val transform: DisplayTransform,
        val options: BlockDisplayOptions,
    ) : DisplayPacketOperation()

    data class Teleport(
        val handle: DisplayEntityHandle,
        val location: Location,
        val transform: DisplayTransform,
    ) : DisplayPacketOperation()

    data class UpdateText(
        val handle: DisplayEntityHandle,
        val transform: DisplayTransform,
        val options: TextDisplayOptions,
    ) : DisplayPacketOperation()

    data class UpdateItem(
        val handle: DisplayEntityHandle,
        val transform: DisplayTransform,
        val options: ItemDisplayOptions,
    ) : DisplayPacketOperation()

    data class UpdateBlock(
        val handle: DisplayEntityHandle,
        val transform: DisplayTransform,
        val options: BlockDisplayOptions,
    ) : DisplayPacketOperation()

    data class Destroy(
        val handle: DisplayEntityHandle,
    ) : DisplayPacketOperation()

    data class DestroyMany(
        val handles: Collection<DisplayEntityHandle>,
    ) : DisplayPacketOperation()
}

/**
 * Display 展示实体发包代理。所有 Display 实体创建、更新、移动和销毁都应通过这里进入 NMS 层。
 */
abstract class NMSDisplayPacket {

    abstract fun spawnTextDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: TextDisplayOptions,
    ): DisplayEntityHandle

    abstract fun spawnItemDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: ItemDisplayOptions,
    ): DisplayEntityHandle

    abstract fun spawnBlockDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: BlockDisplayOptions,
    ): DisplayEntityHandle

    abstract fun teleportDisplay(player: Player, handle: DisplayEntityHandle, location: Location, transform: DisplayTransform)

    abstract fun updateTextDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: TextDisplayOptions)

    abstract fun updateItemDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: ItemDisplayOptions)

    abstract fun updateBlockDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: BlockDisplayOptions)

    abstract fun destroyDisplay(player: Player, handle: DisplayEntityHandle)

    abstract fun destroyDisplays(player: Player, handles: Collection<DisplayEntityHandle>)

    abstract fun destroyAllDisplays(player: Player)

    abstract fun sendDisplayBatch(player: Player, operations: List<DisplayPacketOperation>): List<DisplayEntityHandle>

    companion object {

        val instance: NMSDisplayPacket by unsafeLazy {
            val profile = NMSRuntimeSupport.current
            info("TextDisplayMenu NMS 运行时：${profile.summary()}")
            if (profile.supportsDisplayPackets) {
                nmsProxy<NMSDisplayPacket>()
            } else {
                UnsupportedNMSDisplayPacket(profile)
            }
        }
    }
}
