package cc.bkhk.display.menu.interaction.shortcut

import org.bukkit.entity.Player
import java.util.UUID

/**
 * 全局快捷入口事件键，对应 config.yml 中 shortcut.actions 的键名。
 */
enum class MenuShortcutKey(val configKey: String) {
    OFFHAND("offhand"),
    SNEAKING_OFFHAND("sneaking_offhand"),
    RIGHT_CLICK_PLAYER("right_click_player"),
    SNEAKING_RIGHT_CLICK_PLAYER("sneaking_right_click_player"),
    LEFT_CLICK_AIR("left_click_air"),
    RIGHT_CLICK_AIR("right_click_air"),
    SNEAKING_RIGHT_CLICK_AIR("sneaking_right_click_air"),
    DROP_ITEM("drop_item"),
}

data class MenuShortcutTrigger(
    val player: Player,
    val key: MenuShortcutKey,
    val targetName: String? = null,
    val targetUuid: UUID? = null,
)
