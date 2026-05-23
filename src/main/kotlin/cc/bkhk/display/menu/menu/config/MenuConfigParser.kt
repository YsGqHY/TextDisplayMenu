package cc.bkhk.display.menu.menu.config

import cc.bkhk.display.menu.menu.model.MenuBossBarDefinition
import cc.bkhk.display.menu.menu.model.MenuControlsDefinition
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuDisplayPopupDefinition
import cc.bkhk.display.menu.menu.model.MenuElementActions
import cc.bkhk.display.menu.menu.model.MenuElementDefinition
import cc.bkhk.display.menu.menu.model.MenuElementType
import cc.bkhk.display.menu.menu.model.MenuHitbox
import cc.bkhk.display.menu.menu.model.MenuHoverDefinition
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.menu.model.MenuPosition
import cc.bkhk.display.menu.menu.model.MenuScale
import cc.bkhk.display.menu.menu.model.MenuTitleTimesDefinition
import cc.bkhk.display.menu.menu.model.MenuViewDefinition
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File

object MenuConfigParser {

    fun parse(file: File): MenuDefinition {
        val config = Configuration.loadFromFile(file)
        val id = file.nameWithoutExtension.lowercase()
        return parse(id, file.name, config)
    }

    fun parse(id: String, sourceFileName: String, section: ConfigurationSection): MenuDefinition {
        val pages = parsePages(section)
        require(pages.isNotEmpty()) { "菜单 $id 缺少 pages 页面列表。" }
        return MenuDefinition(
            id = id,
            displayName = section.getString("display_name", id) ?: id,
            description = section.getString("description", "") ?: "",
            view = parseView(section.getConfigurationSection("view")),
            controls = parseControls(section.getConfigurationSection("controls")),
            pages = pages,
            sourceFileName = sourceFileName,
        )
    }

    private fun parseView(section: ConfigurationSection?): MenuViewDefinition {
        if (section == null) {
            return MenuViewDefinition()
        }
        return MenuViewDefinition(
            distance = section.getOptionalDouble("distance"),
            horizontalOffset = section.getOptionalDouble("horizontal_offset"),
            verticalOffset = section.getOptionalDouble("vertical_offset"),
            width = section.getOptionalDouble("width"),
            height = section.getOptionalDouble("height"),
            followView = section.getOptionalBoolean("follow_view"),
            followPosition = section.getOptionalBoolean("follow_position"),
            lockView = section.getOptionalBoolean("lock.view"),
            lockPosition = section.getOptionalBoolean("lock.position"),
        )
    }

    private fun parseControls(section: ConfigurationSection?): MenuControlsDefinition {
        if (section == null) {
            return MenuControlsDefinition()
        }
        return MenuControlsDefinition(
            backClosesWhenNoHistory = section.getBoolean("back_closes_when_no_history", true),
            closeClicks = section.getStringList("close_clicks"),
            backClicks = section.getStringList("back_clicks"),
            backMenu = section.getString("back_menu", "") ?: "",
        )
    }

    private fun parsePages(section: ConfigurationSection): List<MenuPageDefinition> {
        return section.getMapList("pages").mapIndexed { index, map ->
            val pageSection = map.asSimpleSection()
            val id = pageSection.getString("id", "page_$index") ?: "page_$index"
            MenuPageDefinition(
                id = id,
                default = pageSection.getBoolean("default", false),
                title = pageSection.getString("title", "") ?: "",
                openMessage = pageSection.getString("open_message", "") ?: "",
                elements = parseElements(pageSection),
            )
        }
    }

    private fun parseElements(pageSection: SimpleSection): List<MenuElementDefinition> {
        return pageSection.getMapList("elements").mapIndexed { index, map ->
            val section = map.asSimpleSection()
            val id = section.getString("id", "element_$index") ?: "element_$index"
            MenuElementDefinition(
                id = id,
                type = MenuElementType.of(section.getString("type", "text") ?: "text"),
                position = parsePosition(section["position"]),
                hitbox = parseHitbox(section["hitbox"]),
                text = section.getString("text", "") ?: "",
                focusedText = section.getString("focused_text", "") ?: "",
                disabledText = section.getString("disabled_text", "") ?: "",
                hover = parseHover(section["hover"]),
                material = section.getString("material", "") ?: "",
                transform = section.getString("transform", "") ?: "",
                block = section.getString("block", "") ?: "",
                interactive = section.getOptionalBoolean("interactive"),
                scale = parseScale(section["scale"]),
                actions = MenuElementActions(
                    interactClick = section.getStringList("interact_click"),
                    leftClick = section.getStringList("left_click"),
                    rightClick = section.getStringList("right_click"),
                    shiftLeftClick = section.getStringList("shift_left_click"),
                    shiftRightClick = section.getStringList("shift_right_click"),
                ),
            )
        }
    }

    private fun parseHover(value: Any?): MenuHoverDefinition {
        val section = value.asSimpleSectionOrNull() ?: return MenuHoverDefinition()
        return MenuHoverDefinition(
            displayPopup = parseDisplayPopup(section["display_popup"]),
            actionbar = section.getString("actionbar", "") ?: "",
            title = section.getString("title", "") ?: "",
            subtitle = section.getString("subtitle", "") ?: "",
            titleTimes = parseTitleTimes(section["title_times"]),
            bossbar = parseBossBar(section["bossbar"]),
        )
    }

    private fun parseDisplayPopup(value: Any?): MenuDisplayPopupDefinition? {
        val section = value.asSimpleSectionOrNull() ?: return null
        return MenuDisplayPopupDefinition(
            text = section.getString("text", "") ?: "",
            offset = parsePosition(section["offset"]),
        )
    }

    private fun parseTitleTimes(value: Any?): MenuTitleTimesDefinition {
        val section = value.asSimpleSectionOrNull() ?: return MenuTitleTimesDefinition()
        return MenuTitleTimesDefinition(
            fadeIn = section.getInt("fade_in", 5),
            stay = section.getInt("stay", 20),
            fadeOut = section.getInt("fade_out", 5),
        )
    }

    private fun parseBossBar(value: Any?): MenuBossBarDefinition? {
        val section = value.asSimpleSectionOrNull() ?: return null
        return MenuBossBarDefinition(
            title = section.getString("title", "") ?: "",
            color = section.getString("color", "WHITE") ?: "WHITE",
            style = section.getString("style", "SOLID") ?: "SOLID",
            progress = section.getDouble("progress", 1.0).coerceIn(0.0, 1.0),
        )
    }

    private fun parsePosition(value: Any?): MenuPosition {
        val section = value.asSimpleSectionOrNull() ?: return MenuPosition()
        return MenuPosition(
            x = section.getDouble("x", 0.0),
            y = section.getDouble("y", 0.0),
            z = section.getDouble("z", 0.0),
        )
    }

    private fun parseScale(value: Any?): MenuScale? {
        return when (value) {
            is Number -> MenuScale(value.toDouble(), value.toDouble(), value.toDouble())
            is String -> value.toDoubleOrNull()?.let { MenuScale(it, it, it) }
            else -> {
                val section = value.asSimpleSectionOrNull() ?: return null
                MenuScale(
                    x = section.getDouble("x", 1.0),
                    y = section.getDouble("y", 1.0),
                    z = section.getDouble("z", 1.0),
                )
            }
        }
    }

    private fun parseHitbox(value: Any?): MenuHitbox? {
        val section = value.asSimpleSectionOrNull() ?: return null
        return MenuHitbox(
            width = section.getDouble("width", 0.0),
            height = section.getDouble("height", 0.0),
        )
    }

    private fun Map<*, *>.asSimpleSection(): SimpleSection {
        return SimpleSection(entries.associate { it.key.toString() to it.value })
    }

    private fun Any?.asSimpleSectionOrNull(): SimpleSection? {
        return when (this) {
            is SimpleSection -> this
            is Map<*, *> -> this.asSimpleSection()
            is ConfigurationSection -> SimpleSection(getValues(false))
            else -> null
        }
    }

    private class SimpleSection(private val values: Map<String, Any?>) {

        operator fun get(path: String): Any? {
            val split = path.split('.')
            var value: Any? = values
            split.forEach { key ->
                value = when (val current = value) {
                    is Map<*, *> -> current[key]
                    is ConfigurationSection -> current[key]
                    else -> return null
                }
            }
            return value
        }

        fun getString(path: String, default: String? = null): String? {
            val value = get(path) ?: return default
            return if (value is List<*>) value.joinToString("\n") else value.toString()
        }

        fun getBoolean(path: String, default: Boolean): Boolean {
            return get(path)?.toString()?.toBooleanStrictOrNull() ?: default
        }

        fun getOptionalBoolean(path: String): Boolean? {
            return get(path)?.toString()?.toBooleanStrictOrNull()
        }

        fun getInt(path: String, default: Int): Int {
            return get(path)?.toString()?.toIntOrNull() ?: default
        }

        fun getDouble(path: String, default: Double): Double {
            return get(path)?.toString()?.toDoubleOrNull() ?: default
        }

        fun getStringList(path: String): List<String> {
            return when (val value = get(path)) {
                is String -> listOf(value)
                is List<*> -> value.mapNotNull { it?.toString() }
                else -> emptyList()
            }
        }

        fun getMapList(path: String): List<Map<*, *>> {
            return (get(path) as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
        }
    }

    private fun ConfigurationSection.getOptionalBoolean(path: String): Boolean? {
        return get(path)?.toString()?.toBooleanStrictOrNull()
    }

    private fun ConfigurationSection.getOptionalDouble(path: String): Double? {
        return get(path)?.toString()?.toDoubleOrNull()
    }
}
