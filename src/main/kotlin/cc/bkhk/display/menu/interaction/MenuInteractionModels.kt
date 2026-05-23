package cc.bkhk.display.menu.interaction

import cc.bkhk.display.menu.render.MenuRenderElementFrame

/**
 * 菜单点击类型，对应菜单配置中的动作节点。
 */
enum class MenuClickType(val configKey: String) {
    INTERACT("interact_click"),
    LEFT("left_click"),
    RIGHT("right_click"),
    SHIFT_LEFT("shift_left_click"),
    SHIFT_RIGHT("shift_right_click"),
}

/**
 * 玩家视线命中的菜单元素。
 */
data class MenuHitResult(
    val element: MenuRenderElementFrame,
    val localX: Double,
    val localY: Double,
    val distance: Double,
)
