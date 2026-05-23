package cc.bkhk.display.menu.menu.model

import cc.bkhk.display.menu.nms.DisplayEntityType
import cc.bkhk.display.menu.nms.DisplayItemTransform
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle

data class MenuDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val view: MenuViewDefinition = MenuViewDefinition(),
    val controls: MenuControlsDefinition = MenuControlsDefinition(),
    val pages: List<MenuPageDefinition> = emptyList(),
    val sourceFileName: String = "",
) {

    val defaultPage: MenuPageDefinition?
        get() = pages.firstOrNull { it.default } ?: pages.firstOrNull()
}

data class MenuViewDefinition(
    val distance: Double? = null,
    val horizontalOffset: Double? = null,
    val verticalOffset: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val followView: Boolean? = null,
    val followPosition: Boolean? = null,
    val lockView: Boolean? = null,
    val lockPosition: Boolean? = null,
)

data class MenuControlsDefinition(
    val backClosesWhenNoHistory: Boolean = true,
    val closeClicks: List<String> = emptyList(),
    val backClicks: List<String> = emptyList(),
    val backMenu: String = "",
)

data class MenuPageDefinition(
    val id: String,
    val default: Boolean = false,
    val title: String = "",
    val openMessage: String = "",
    val elements: List<MenuElementDefinition> = emptyList(),
)

data class MenuElementDefinition(
    val id: String,
    val type: MenuElementType,
    val position: MenuPosition = MenuPosition(),
    val hitbox: MenuHitbox? = null,
    val text: String = "",
    val focusedText: String = "",
    val disabledText: String = "",
    val hover: MenuHoverDefinition = MenuHoverDefinition(),
    val material: String = "",
    val transform: String = "",
    val block: String = "",
    val interactive: Boolean? = null,
    val scale: MenuScale? = null,
    val actions: MenuElementActions = MenuElementActions(),
    val prepared: PreparedMenuElement = PreparedMenuElement(),
)

/**
 * 菜单元素的静态预处理结果，在菜单配置加载时构建，避免打开菜单时重复解析。
 */
data class PreparedMenuElement(
    val displayType: DisplayEntityType = DisplayEntityType.TEXT,
    val material: Material? = null,
    val itemTransform: DisplayItemTransform? = null,
    val blockData: BlockData? = null,
    val hover: PreparedMenuHover = PreparedMenuHover(),
    val actions: PreparedMenuActions = PreparedMenuActions(),
)

data class PreparedMenuHover(
    val bossbarColor: BarColor = BarColor.WHITE,
    val bossbarStyle: BarStyle = BarStyle.SOLID,
)

data class PreparedMenuActions(
    val interactClick: List<String> = emptyList(),
    val leftClick: List<String> = emptyList(),
    val rightClick: List<String> = emptyList(),
    val shiftLeftClick: List<String> = emptyList(),
    val shiftRightClick: List<String> = emptyList(),
)

enum class MenuElementType {
    TEXT,
    BUTTON,
    ITEM,
    BLOCK;

    companion object {

        fun of(value: String): MenuElementType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TEXT
        }
    }
}

data class MenuPosition(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

data class MenuScale(
    val x: Double = 1.0,
    val y: Double = 1.0,
    val z: Double = 1.0,
)

data class MenuHitbox(
    val width: Double = 0.0,
    val height: Double = 0.0,
)

data class MenuElementActions(
    val interactClick: List<String> = emptyList(),
    val leftClick: List<String> = emptyList(),
    val rightClick: List<String> = emptyList(),
    val shiftLeftClick: List<String> = emptyList(),
    val shiftRightClick: List<String> = emptyList(),
)

data class MenuHoverDefinition(
    val displayPopup: MenuDisplayPopupDefinition? = null,
    val actionbar: String = "",
    val title: String = "",
    val subtitle: String = "",
    val titleTimes: MenuTitleTimesDefinition = MenuTitleTimesDefinition(),
    val bossbar: MenuBossBarDefinition? = null,
)

data class MenuDisplayPopupDefinition(
    val text: String = "",
    val offset: MenuPosition = MenuPosition(),
)

data class MenuTitleTimesDefinition(
    val fadeIn: Int = 5,
    val stay: Int = 20,
    val fadeOut: Int = 5,
)

data class MenuBossBarDefinition(
    val title: String = "",
    val color: String = "WHITE",
    val style: String = "SOLID",
    val progress: Double = 1.0,
)
