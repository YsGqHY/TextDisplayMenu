package cc.bkhk.display.menu.text

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.module.chat.Components
import taboolib.module.chat.colored
import taboolib.platform.compat.replacePlaceholder

/**
 * 菜单显示文本统一处理入口。
 *
 * 解析顺序固定为 papi -> components -> MiniMessage。TextDisplay 使用 JSON 结果以保留富文本；
 * Bukkit 字符串接口使用 legacy 文本，无法使用的格式会安全回退到颜色码文本。
 */
object MenuTextProcessor {

    fun format(input: String, context: MenuTextContext = MenuTextContext()): String {
        return formatRich(input, context).legacyText
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

    fun formatComponent(input: String, context: MenuTextContext = MenuTextContext()): MenuFormattedText {
        return formatRich(input, context)
    }

    private fun formatRich(input: String, context: MenuTextContext): MenuFormattedText {
        if (input.isBlank()) {
            return MenuFormattedText.empty()
        }
        val papi = applyPapi(input, context)
        parseComponents(papi)?.let { return it }
        parseMiniMessage(papi)?.let { return it }
        return MenuFormattedText(legacyText = papi.colored())
    }

    private fun applyPapi(input: String, context: MenuTextContext): String {
        val player = context.player ?: return input.replacePlaceholder()
        return input.replacePlaceholder(player)
    }

    private fun parseComponents(input: String): MenuFormattedText? {
        if (!input.mayContainComponentSyntax()) {
            return null
        }
        return runCatching {
            val component = Components.parseSimple(input).buildColored()
            val raw = component.toRawMessage()
            MenuFormattedText(
                legacyText = component.toLegacyText(),
                plainText = component.toPlainText(),
                jsonText = raw,
                textIsJson = true,
            )
        }.getOrNull()
    }

    private fun parseMiniMessage(input: String): MenuFormattedText? {
        if (!input.mayContainMiniMessageSyntax() || !MiniMessageBridge.available) {
            return null
        }
        return MiniMessageBridge.parse(input)
    }

    private fun String.mayContainComponentSyntax(): Boolean {
        return contains('[') && contains("](")
    }

    private fun String.mayContainMiniMessageSyntax(): Boolean {
        return contains('<') && contains('>')
    }
}

private object MiniMessageBridge {

    val available: Boolean by lazy {
        runCatching {
            net.kyori.adventure.text.minimessage.MiniMessage::class.java.name
            GsonComponentSerializer::class.java.name
            LegacyComponentSerializer::class.java.name
            PlainTextComponentSerializer::class.java.name
        }.isSuccess
    }

    fun parse(input: String): MenuFormattedText? {
        return runCatching {
            val component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(input)
            MenuFormattedText(
                legacyText = LegacyComponentSerializer.legacySection().serialize(component),
                plainText = PlainTextComponentSerializer.plainText().serialize(component),
                jsonText = GsonComponentSerializer.gson().serialize(component),
                textIsJson = true,
            )
        }.getOrNull()
    }
}

data class MenuFormattedText(
    val legacyText: String,
    val plainText: String = legacyText,
    val jsonText: String = "",
    val textIsJson: Boolean = false,
) {

    val displayText: String
        get() = jsonText.takeIf { textIsJson && it.isNotBlank() } ?: legacyText

    companion object {

        fun empty(): MenuFormattedText {
            return MenuFormattedText(legacyText = "")
        }
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
