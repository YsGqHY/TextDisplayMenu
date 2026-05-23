package cc.bkhk.display.menu.config

import cc.bkhk.display.menu.nms.DisplayBillboard
import cc.bkhk.display.menu.nms.DisplayItemTransform
import cc.bkhk.display.menu.nms.DisplayTextAlignment

data class GlobalConfigSnapshot(
    val shortcut: ShortcutConfig = ShortcutConfig(),
    val render: RenderConfig = RenderConfig(),
    val interaction: InteractionConfig = InteractionConfig(),
    val session: SessionConfig = SessionConfig(),
    val confirmation: ConfirmationConfig = ConfirmationConfig(),
    val debug: DebugConfig = DebugConfig(),
)

data class ShortcutConfig(
    val enabled: Boolean = true,
    val cooldownMs: Long = 800L,
    val sneakingValidMs: Long = 1500L,
    val defaultMenu: String = "default",
    val actions: Map<String, ShortcutAction> = emptyMap(),
    val conditions: ShortcutConditions = ShortcutConditions(),
)

data class ShortcutAction(
    val scripts: List<String> = emptyList(),
    val condition: String? = null,
    val execute: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
    val branches: List<ShortcutAction> = emptyList(),
)

data class ShortcutConditions(
    val cancelEvent: Boolean = true,
    val ignoreWhenInventoryOpen: Boolean = true,
    val blockSpectator: Boolean = true,
    val blockInsideVehicle: Boolean = false,
    val requireEmptyHand: Boolean = false,
    val blockedWorlds: List<String> = emptyList(),
)

data class RenderConfig(
    val anchor: String = "player_eye",
    val distance: Double = 2.4,
    val horizontalOffset: Double = 0.0,
    val verticalOffset: Double = -0.15,
    val followView: Boolean = true,
    val followPosition: Boolean = true,
    val followSmoothTicks: Int = 2,
    val followPositionSmoothTicks: Int = 2,
    val viewPacketThrottleMs: Long = 50L,
    val updateIntervalTicks: Long = 1L,
    val maxSessionDistance: Double = 12.0,
    val displayDefaults: DisplayDefaultsConfig = DisplayDefaultsConfig(),
)

data class DisplayDefaultsConfig(
    val common: CommonDisplayDefaults = CommonDisplayDefaults(),
    val text: TextDisplayDefaults = TextDisplayDefaults(),
    val item: ItemDisplayDefaults = ItemDisplayDefaults(),
    val block: BlockDisplayDefaults = BlockDisplayDefaults(),
)

data class CommonDisplayDefaults(
    val billboard: DisplayBillboard = DisplayBillboard.CENTER,
    val brightness: Int = -1,
    val viewRange: Float = 1.0f,
    val shadowRadius: Float = 0.0f,
    val shadowStrength: Float = 0.0f,
    val interpolationDelayTicks: Int = 0,
    val interpolationDurationTicks: Int = 2,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val scaleZ: Float = 1.0f,
)

data class TextDisplayDefaults(
    val shadowed: Boolean = true,
    val seeThrough: Boolean = false,
    val backgroundColor: Int = 0x66000000,
    val textOpacity: Int = 255,
    val lineWidth: Int = 220,
    val alignment: DisplayTextAlignment = DisplayTextAlignment.CENTER,
)

data class ItemDisplayDefaults(
    val transform: DisplayItemTransform = DisplayItemTransform.GUI,
    val material: String = "PAPER",
    val interactive: Boolean = true,
    val scaleX: Float = 0.55f,
    val scaleY: Float = 0.55f,
    val scaleZ: Float = 0.55f,
)

data class BlockDisplayDefaults(
    val block: String = "STONE",
    val interactive: Boolean = true,
    val scaleX: Float = 0.45f,
    val scaleY: Float = 0.45f,
    val scaleZ: Float = 0.45f,
)

data class InteractionConfig(
    val detectionIntervalTicks: Long = 1L,
    val maxDistance: Double = 4.0,
    val itemDisplayClickable: Boolean = true,
    val blockDisplayClickable: Boolean = true,
    val hitPriority: List<String> = listOf("button", "item", "block", "text"),
    val rayStep: Double = 0.05,
    val focusStayTicks: Int = 2,
    val defaultHitbox: DefaultHitboxConfig = DefaultHitboxConfig(),
    val clickCooldownMs: Long = 150L,
    val clickMapping: ClickMappingConfig = ClickMappingConfig(),
    val input: InteractionInputConfig = InteractionInputConfig(),
    val focusFeedback: FocusFeedbackConfig = FocusFeedbackConfig(),
)

data class DefaultHitboxConfig(
    val textWidth: Double = 90.0,
    val textHeight: Double = 24.0,
    val itemWidth: Double = 32.0,
    val itemHeight: Double = 32.0,
    val blockWidth: Double = 36.0,
    val blockHeight: Double = 36.0,
)

data class ClickMappingConfig(
    val interactClick: String = "primary",
    val leftClick: String = "primary",
    val rightClick: String = "secondary",
    val shiftLeftClick: String = "quick",
    val shiftRightClick: String = "back",
)

data class InteractionInputConfig(
    val interactClick: Boolean = true,
    val leftClick: Boolean = true,
    val rightClick: Boolean = true,
    val shiftClick: Boolean = true,
    val interactEvents: List<String> = listOf("right_click_air", "right_click_block"),
)

data class FocusFeedbackConfig(
    val enabled: Boolean = true,
    val playSound: Boolean = true,
    val sound: String = "UI_BUTTON_CLICK",
    val volume: Float = 0.35f,
    val pitch: Float = 1.6f,
    val displayPopupEnabled: Boolean = true,
    val displayPopupLifetimeTicks: Long = 8L,
    val displayPopupOffset: OffsetConfig = OffsetConfig(0.0, -32.0, 0.04),
    val actionbarEnabled: Boolean = true,
    val actionbarIntervalTicks: Long = 20L,
    val titleEnabled: Boolean = true,
    val titleIntervalTicks: Long = 40L,
    val bossbarEnabled: Boolean = true,
    val bossbarIntervalTicks: Long = 20L,
)

data class OffsetConfig(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

data class SessionConfig(
    val idleTimeoutSeconds: Long = 120L,
    val keepStateSeconds: Long = 30L,
    val closeOnQuit: Boolean = true,
    val closeOnWorldChange: Boolean = true,
    val closeOnTeleport: Boolean = true,
    val closeOnDeath: Boolean = true,
    val closeOnSpectator: Boolean = true,
    val destroyDelayTicks: Long = 0L,
    val cleanupIntervalTicks: Long = 100L,
)

data class ConfirmationConfig(
    val defaultRequired: Boolean = true,
    val expireSeconds: Long = 8L,
    val acceptClick: String = "left_click",
    val cancelClick: String = "right_click",
    val promptMessageKey: String = "confirmation-prompt",
    val expiredMessageKey: String = "confirmation-expired",
    val cancelledMessageKey: String = "confirmation-cancelled",
)

data class DebugConfig(
    val enabled: Boolean = false,
    val logPackets: Boolean = false,
    val logInteractions: Boolean = false,
    val messageKey: String = "debug-line",
)
