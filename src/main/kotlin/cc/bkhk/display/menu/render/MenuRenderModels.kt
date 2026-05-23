package cc.bkhk.display.menu.render

import cc.bkhk.display.menu.menu.model.MenuElementDefinition
import cc.bkhk.display.menu.menu.model.MenuHitbox
import cc.bkhk.display.menu.menu.model.MenuPosition
import cc.bkhk.display.menu.nms.DisplayEntityType
import cc.bkhk.display.menu.nms.DisplayPacketOperation
import cc.bkhk.display.menu.nms.DisplayTransform
import org.bukkit.Location
import org.bukkit.util.Vector

/**
 * 菜单平面方向，供渲染跟随与视线命中共用。
 */
data class MenuRenderBasis(
    val forward: Vector,
    val right: Vector,
    val up: Vector,
)

/**
 * 菜单锚点状态。
 *
 * anchorOffset 使用世界坐标保存打开瞬间菜单相对玩家基准点的偏移，后续玩家转头不会重新旋转该偏移。
 */
data class MenuRenderAnchorState(
    val playerBase: Location,
    val anchorOffset: Vector,
    val basis: MenuRenderBasis,
    val yaw: Float,
    val pitch: Float,
)

/**
 * 单个菜单元素对应的一帧 Display 渲染数据。
 */
data class MenuRenderElementFrame(
    val key: String,
    val elementId: String,
    val element: MenuElementDefinition,
    val type: DisplayEntityType,
    val location: Location,
    val logicalPosition: MenuPosition,
    val hitbox: MenuHitbox,
    val interactive: Boolean,
    val transform: DisplayTransform,
    val spawnOperation: DisplayPacketOperation,
)

/**
 * 当前页面完整 Display 渲染帧。
 */
data class MenuRenderFrame(
    val menuId: String,
    val pageId: String,
    val anchor: Location,
    val basis: MenuRenderBasis,
    val anchorState: MenuRenderAnchorState,
    val elements: List<MenuRenderElementFrame>,
) {

    fun layoutSignature(): List<Pair<String, DisplayEntityType>> {
        return elements.map { it.key to it.type }
    }

    fun element(key: String?): MenuRenderElementFrame? {
        return key?.let { target -> elements.firstOrNull { it.key == target } }
    }
}
