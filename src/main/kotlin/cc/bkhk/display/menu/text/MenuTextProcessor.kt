package cc.bkhk.display.menu.text

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.module.chat.colored

/**
 * 菜单显示文本统一处理入口。
 *
 * 后续接入 PlaceholderAPI、MiniMessage 或自定义变量时，只需要扩展这里，
 * Display 文本、hover 反馈和页面提示等显示出口不再分散处理格式。
 */
object MenuTextProcessor {

    fun format(input: String, context: MenuTextContext = MenuTextContext()): String {
        if (input.isBlank()) {
            return ""
        }
        return applyColor(applyPlaceholders(input, context))
    }

    fun formatFor(sender: CommandSender, input: String, context: MenuTextContext = MenuTextContext()): String {
        return format(
            input,
            context.copy(
                sender = context.sender ?: sender,
                player = context.player ?: sender as? Player,
            ),
        )
    }

    private fun applyPlaceholders(input: String, context: MenuTextContext): String {
        // PAPI 等玩家相关解析需要运行期上下文，后续统一在这里接入。
        context.player ?: return input
        return input
    }

    private fun applyColor(input: String): String {
        return input.colored()
    }
}

data class MenuTextContext(
    val player: Player? = null,
    val sender: CommandSender? = player,
    val menuId: String = "",
    val pageId: String = "",
    val elementId: String = "",
    val use: MenuTextUse = MenuTextUse.GENERAL,
    val arguments: List<String> = emptyList(),
)

enum class MenuTextUse {
    GENERAL,
    MENU_DISPLAY_NAME,
    MENU_DESCRIPTION,
    PAGE_TITLE,
    PAGE_OPEN_MESSAGE,
    ELEMENT_TEXT,
    ELEMENT_FOCUSED_TEXT,
    ELEMENT_DISABLED_TEXT,
    HOVER_POPUP,
    HOVER_ACTIONBAR,
    HOVER_TITLE,
    HOVER_SUBTITLE,
    HOVER_BOSSBAR,
    CONFIRMATION_PROMPT,
    COMMAND_TEXT,
}
