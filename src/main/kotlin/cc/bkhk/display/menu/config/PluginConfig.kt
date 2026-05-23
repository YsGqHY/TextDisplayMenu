package cc.bkhk.display.menu.config

import cc.bkhk.display.menu.nms.DisplayBillboard
import cc.bkhk.display.menu.nms.DisplayItemTransform
import cc.bkhk.display.menu.nms.DisplayTextAlignment
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.concurrent.atomic.AtomicReference

object PluginConfig {

    @Config("config.yml", autoReload = true)
    lateinit var config: Configuration

    private val snapshotRef = AtomicReference(GlobalConfigSnapshot())

    fun initialize() {
        reload()
        config.onReload { reload() }
    }

    fun reload(): GlobalConfigSnapshot {
        val snapshot = parse(config)
        snapshotRef.set(snapshot)
        DebugRuntime.syncFromConfig(snapshot)
        return snapshot
    }

    fun snapshot(): GlobalConfigSnapshot {
        return snapshotRef.get()
    }

    private fun parse(section: ConfigurationSection): GlobalConfigSnapshot {
        return GlobalConfigSnapshot(
            shortcut = parseShortcut(section),
            render = parseRender(section),
            interaction = parseInteraction(section),
            session = parseSession(section),
            confirmation = parseConfirmation(section),
            debug = parseDebug(section),
        )
    }

    private fun parseShortcut(section: ConfigurationSection): ShortcutConfig {
        return ShortcutConfig(
            enabled = section.getBoolean("shortcut.enabled", true),
            cooldownMs = section.getLongSafe("shortcut.cooldown_ms", 800L),
            sneakingValidMs = section.getLongSafe("shortcut.sneaking_valid_ms", 1500L),
            defaultMenu = section.getString("shortcut.default_menu", "default") ?: "default",
            actions = parseShortcutActions(section.getConfigurationSection("shortcut.actions")),
            conditions = ShortcutConditions(
                cancelEvent = section.getBoolean("shortcut.conditions.cancel_event", true),
                ignoreWhenInventoryOpen = section.getBoolean("shortcut.conditions.ignore_when_inventory_open", true),
                blockSpectator = section.getBoolean("shortcut.conditions.block_spectator", true),
                blockInsideVehicle = section.getBoolean("shortcut.conditions.block_inside_vehicle", false),
                requireEmptyHand = section.getBoolean("shortcut.conditions.require_empty_hand", false),
                blockedWorlds = section.getStringList("shortcut.conditions.blocked_worlds"),
            ),
        )
    }

    private fun parseShortcutActions(section: ConfigurationSection?): Map<String, ShortcutAction> {
        if (section == null) {
            return emptyMap()
        }
        return section.getKeys(false).associateWith { key ->
            parseShortcutAction(section[key])
        }
    }

    private fun parseShortcutAction(value: Any?): ShortcutAction {
        return when (value) {
            is String -> ShortcutAction(scripts = listOf(value))
            is List<*> -> {
                val scripts = value.filterIsInstance<String>()
                val branches = value.mapNotNull { parseShortcutActionBranch(it) }
                ShortcutAction(
                    scripts = scripts,
                    branches = branches,
                )
            }
            is Map<*, *> -> parseShortcutActionBranch(value) ?: ShortcutAction()
            else -> ShortcutAction()
        }
    }

    private fun parseShortcutActionBranch(value: Any?): ShortcutAction? {
        val map = value as? Map<*, *> ?: return null
        return ShortcutAction(
            condition = map["condition"]?.toString(),
            execute = parseStringList(map["execute"]),
            deny = parseStringList(map["deny"]),
        )
    }

    private fun parseRender(section: ConfigurationSection): RenderConfig {
        return RenderConfig(
            anchor = section.getString("render.anchor", "player_eye") ?: "player_eye",
            distance = section.getDouble("render.distance", 2.4),
            horizontalOffset = section.getDouble("render.horizontal_offset", 0.0),
            verticalOffset = section.getDouble("render.vertical_offset", -0.15),
            followView = section.getBoolean("render.follow_view", true),
            followPosition = section.getBoolean("render.follow_position", true),
            followSmoothTicks = section.getInt("render.follow_smooth_ticks", 2),
            followPositionSmoothTicks = section.getInt("render.follow_position_smooth_ticks", 2),
            viewPacketThrottleMs = section.getLongSafe("render.view_packet_throttle_ms", 50L),
            updateIntervalTicks = section.getLongSafe("render.update_interval_ticks", 1L),
            maxSessionDistance = section.getDouble("render.max_session_distance", 12.0),
            displayDefaults = parseDisplayDefaults(section),
        )
    }

    private fun parseDisplayDefaults(section: ConfigurationSection): DisplayDefaultsConfig {
        return DisplayDefaultsConfig(
            common = CommonDisplayDefaults(
                billboard = parseBillboard(section.getString("render.display_defaults.common.billboard", "center")),
                brightness = section.getInt("render.display_defaults.common.brightness", -1),
                viewRange = section.getFloatSafe("render.display_defaults.common.view_range", 1.0f),
                shadowRadius = section.getFloatSafe("render.display_defaults.common.shadow_radius", 0.0f),
                shadowStrength = section.getFloatSafe("render.display_defaults.common.shadow_strength", 0.0f),
                interpolationDelayTicks = section.getInt("render.display_defaults.common.interpolation_delay_ticks", 0),
                interpolationDurationTicks = section.getInt("render.display_defaults.common.interpolation_duration_ticks", 2),
                scaleX = section.getFloatSafe("render.display_defaults.common.scale_x", 1.0f),
                scaleY = section.getFloatSafe("render.display_defaults.common.scale_y", 1.0f),
                scaleZ = section.getFloatSafe("render.display_defaults.common.scale_z", 1.0f),
            ),
            text = TextDisplayDefaults(
                shadowed = section.getBoolean("render.display_defaults.text.shadowed", true),
                seeThrough = section.getBoolean("render.display_defaults.text.see_through", false),
                backgroundColor = parseColor(section.getString("render.display_defaults.text.background_color"), 0x66000000),
                textOpacity = section.getInt("render.display_defaults.text.text_opacity", 255),
                lineWidth = section.getInt("render.display_defaults.text.line_width", 220),
                alignment = parseAlignment(section.getString("render.display_defaults.text.alignment", "center")),
            ),
            item = ItemDisplayDefaults(
                transform = parseItemTransform(section.getString("render.display_defaults.item.transform", "gui")),
                material = section.getString("render.display_defaults.item.material", "PAPER") ?: "PAPER",
                interactive = section.getBoolean("render.display_defaults.item.interactive", true),
                scaleX = section.getFloatSafe("render.display_defaults.item.scale_x", 0.55f),
                scaleY = section.getFloatSafe("render.display_defaults.item.scale_y", 0.55f),
                scaleZ = section.getFloatSafe("render.display_defaults.item.scale_z", 0.55f),
            ),
            block = BlockDisplayDefaults(
                block = section.getString("render.display_defaults.block.block", "STONE") ?: "STONE",
                interactive = section.getBoolean("render.display_defaults.block.interactive", true),
                scaleX = section.getFloatSafe("render.display_defaults.block.scale_x", 0.45f),
                scaleY = section.getFloatSafe("render.display_defaults.block.scale_y", 0.45f),
                scaleZ = section.getFloatSafe("render.display_defaults.block.scale_z", 0.45f),
            ),
        )
    }

    private fun parseInteraction(section: ConfigurationSection): InteractionConfig {
        return InteractionConfig(
            detectionIntervalTicks = section.getLongSafe("interaction.detection_interval_ticks", 1L),
            maxDistance = section.getDouble("interaction.max_distance", 4.0),
            itemDisplayClickable = section.getBoolean("interaction.item_display_clickable", true),
            blockDisplayClickable = section.getBoolean("interaction.block_display_clickable", true),
            hitPriority = section.getStringList("interaction.hit_priority").ifEmpty { listOf("button", "item", "block", "text") },
            rayStep = section.getDouble("interaction.ray_step", 0.05),
            focusStayTicks = section.getInt("interaction.focus_stay_ticks", 2),
            defaultHitbox = DefaultHitboxConfig(
                textWidth = section.getDouble("interaction.default_hitbox.text_width", 90.0),
                textHeight = section.getDouble("interaction.default_hitbox.text_height", 24.0),
                itemWidth = section.getDouble("interaction.default_hitbox.item_width", 32.0),
                itemHeight = section.getDouble("interaction.default_hitbox.item_height", 32.0),
                blockWidth = section.getDouble("interaction.default_hitbox.block_width", 36.0),
                blockHeight = section.getDouble("interaction.default_hitbox.block_height", 36.0),
            ),
            clickCooldownMs = section.getLongSafe("interaction.click_cooldown_ms", 150L),
            clickMapping = ClickMappingConfig(
                interactClick = section.getString("interaction.click_mapping.interact_click", "primary") ?: "primary",
                leftClick = section.getString("interaction.click_mapping.left_click", "primary") ?: "primary",
                rightClick = section.getString("interaction.click_mapping.right_click", "secondary") ?: "secondary",
                shiftLeftClick = section.getString("interaction.click_mapping.shift_left_click", "quick") ?: "quick",
                shiftRightClick = section.getString("interaction.click_mapping.shift_right_click", "back") ?: "back",
            ),
            input = InteractionInputConfig(
                interactClick = section.getBoolean("interaction.input.interact_click", true),
                leftClick = section.getBoolean("interaction.input.left_click", true),
                rightClick = section.getBoolean("interaction.input.right_click", true),
                shiftClick = section.getBoolean("interaction.input.shift_click", true),
                interactEvents = section.getStringList("interaction.input.interact_events").ifEmpty { listOf("right_click_air", "right_click_block") },
            ),
            focusFeedback = FocusFeedbackConfig(
                enabled = section.getBoolean("interaction.focus_feedback.enabled", true),
                playSound = section.getBoolean("interaction.focus_feedback.play_sound", true),
                sound = section.getString("interaction.focus_feedback.sound", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK",
                volume = section.getFloatSafe("interaction.focus_feedback.volume", 0.35f),
                pitch = section.getFloatSafe("interaction.focus_feedback.pitch", 1.6f),
                displayPopupEnabled = section.getBoolean("interaction.focus_feedback.display_popup_enabled", true),
                displayPopupLifetimeTicks = section.getLongSafe("interaction.focus_feedback.display_popup_lifetime_ticks", 8L),
                displayPopupOffset = OffsetConfig(
                    x = section.getDouble("interaction.focus_feedback.display_popup_offset.x", 0.0),
                    y = section.getDouble("interaction.focus_feedback.display_popup_offset.y", -32.0),
                    z = section.getDouble("interaction.focus_feedback.display_popup_offset.z", 0.04),
                ),
                actionbarEnabled = section.getBoolean("interaction.focus_feedback.actionbar_enabled", true),
                actionbarIntervalTicks = section.getLongSafe("interaction.focus_feedback.actionbar_interval_ticks", 20L),
                titleEnabled = section.getBoolean("interaction.focus_feedback.title_enabled", true),
                titleIntervalTicks = section.getLongSafe("interaction.focus_feedback.title_interval_ticks", 40L),
                bossbarEnabled = section.getBoolean("interaction.focus_feedback.bossbar_enabled", true),
                bossbarIntervalTicks = section.getLongSafe("interaction.focus_feedback.bossbar_interval_ticks", 20L),
            ),
        )
    }

    private fun parseSession(section: ConfigurationSection): SessionConfig {
        return SessionConfig(
            idleTimeoutSeconds = section.getLongSafe("session.idle_timeout_seconds", 120L),
            keepStateSeconds = section.getLongSafe("session.keep_state_seconds", 30L),
            closeOnQuit = section.getBoolean("session.close_on_quit", true),
            closeOnWorldChange = section.getBoolean("session.close_on_world_change", true),
            closeOnTeleport = section.getBoolean("session.close_on_teleport", true),
            closeOnDeath = section.getBoolean("session.close_on_death", true),
            closeOnSpectator = section.getBoolean("session.close_on_spectator", true),
            destroyDelayTicks = section.getLongSafe("session.destroy_delay_ticks", 0L),
            cleanupIntervalTicks = section.getLongSafe("session.cleanup_interval_ticks", 100L),
        )
    }

    private fun parseConfirmation(section: ConfigurationSection): ConfirmationConfig {
        return ConfirmationConfig(
            defaultRequired = section.getBoolean("confirmation.default_required", true),
            expireSeconds = section.getLongSafe("confirmation.expire_seconds", 8L),
            acceptClick = section.getString("confirmation.accept_click", "left_click") ?: "left_click",
            cancelClick = section.getString("confirmation.cancel_click", "right_click") ?: "right_click",
            promptMessageKey = section.getString("confirmation.prompt_message_key", "confirmation-prompt") ?: "confirmation-prompt",
            expiredMessageKey = section.getString("confirmation.expired_message_key", "confirmation-expired") ?: "confirmation-expired",
            cancelledMessageKey = section.getString("confirmation.cancelled_message_key", "confirmation-cancelled") ?: "confirmation-cancelled",
        )
    }

    private fun parseDebug(section: ConfigurationSection): DebugConfig {
        return DebugConfig(
            enabled = section.getBoolean("debug.enabled", false),
            logPackets = section.getBoolean("debug.log_packets", false),
            logInteractions = section.getBoolean("debug.log_interactions", false),
            messageKey = section.getString("debug.message_key", "debug-line") ?: "debug-line",
        )
    }

    private fun parseStringList(value: Any?): List<String> {
        return when (value) {
            is String -> listOf(value)
            is List<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }

    private fun parseBillboard(value: String?): DisplayBillboard {
        return when (value?.lowercase()) {
            "fixed" -> DisplayBillboard.FIXED
            "vertical" -> DisplayBillboard.VERTICAL
            "horizontal" -> DisplayBillboard.HORIZONTAL
            else -> DisplayBillboard.CENTER
        }
    }

    private fun parseAlignment(value: String?): DisplayTextAlignment {
        return when (value?.lowercase()) {
            "left" -> DisplayTextAlignment.LEFT
            "right" -> DisplayTextAlignment.RIGHT
            else -> DisplayTextAlignment.CENTER
        }
    }

    private fun parseItemTransform(value: String?): DisplayItemTransform {
        return DisplayItemTransform.entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: DisplayItemTransform.GUI
    }

    private fun parseColor(value: String?, default: Int): Int {
        val raw = value?.trim()?.removePrefix("#") ?: return default
        return raw.toLongOrNull(16)?.toInt() ?: default
    }

    private fun ConfigurationSection.getLongSafe(path: String, default: Long): Long {
        return get(path)?.toString()?.toLongOrNull() ?: default
    }

    private fun ConfigurationSection.getFloatSafe(path: String, default: Float): Float {
        return get(path)?.toString()?.toFloatOrNull() ?: default
    }
}
