package cc.bkhk.display.menu.render

import cc.bkhk.display.menu.config.DefaultHitboxConfig
import cc.bkhk.display.menu.config.GlobalConfigSnapshot
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuElementDefinition
import cc.bkhk.display.menu.menu.model.MenuElementType
import cc.bkhk.display.menu.menu.model.MenuHitbox
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.menu.model.MenuScale
import cc.bkhk.display.menu.nms.BlockDisplayOptions
import cc.bkhk.display.menu.nms.DisplayBrightness
import cc.bkhk.display.menu.nms.DisplayEntityType
import cc.bkhk.display.menu.nms.DisplayItemTransform
import cc.bkhk.display.menu.nms.DisplayPacketOperation
import cc.bkhk.display.menu.nms.DisplayTransform
import cc.bkhk.display.menu.nms.ItemDisplayOptions
import cc.bkhk.display.menu.nms.TextDisplayOptions
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import taboolib.module.chat.colored
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object MenuRenderFrameBuilder {

    const val PIXELS_PER_BLOCK = 100.0

    fun build(player: Player, menu: MenuDefinition, page: MenuPageDefinition, snapshot: GlobalConfigSnapshot = PluginConfig.snapshot()): MenuRenderFrame {
        val anchor = createAnchor(player, menu, snapshot)
        val basis = createBasis(anchor)
        val elements = page.elements.mapIndexed { index, element ->
            buildElement(index, element, anchor, basis, snapshot)
        }
        return MenuRenderFrame(menu.id, page.id, anchor, basis, elements)
    }

    fun createBasis(location: Location): MenuRenderBasis {
        val yawRadians = Math.toRadians(location.yaw.toDouble())
        val forward = location.direction.normalize()
        val right = Vector(cos(yawRadians), 0.0, sin(yawRadians)).normalize()
        val up = right.clone().crossProduct(forward).normalize()
        return MenuRenderBasis(forward, right, up)
    }

    fun createTextOptions(element: MenuElementDefinition, snapshot: GlobalConfigSnapshot = PluginConfig.snapshot(), focused: Boolean = false): TextDisplayOptions {
        val text = when {
            focused && element.focusedText.isNotBlank() -> element.focusedText
            element.text.isNotBlank() -> element.text
            element.focusedText.isNotBlank() -> element.focusedText
            else -> element.id
        }
        val defaults = snapshot.render.displayDefaults.text
        return TextDisplayOptions(
            text = text.colored(),
            lineWidth = defaults.lineWidth,
            backgroundColor = defaults.backgroundColor,
            textOpacity = defaults.textOpacity,
            shadowed = defaults.shadowed,
            seeThrough = defaults.seeThrough,
            alignment = defaults.alignment,
        )
    }

    private fun buildElement(
        index: Int,
        element: MenuElementDefinition,
        anchor: Location,
        basis: MenuRenderBasis,
        snapshot: GlobalConfigSnapshot,
    ): MenuRenderElementFrame {
        val location = anchor.clone().add(
            basis.right.clone().multiply(element.position.x / PIXELS_PER_BLOCK),
        ).add(
            basis.up.clone().multiply(-element.position.y / PIXELS_PER_BLOCK),
        ).add(
            basis.forward.clone().multiply(element.position.z),
        )
        location.yaw = anchor.yaw
        location.pitch = anchor.pitch

        val displayType = element.displayType()
        val transform = createTransform(element, displayType, snapshot)
        val operation = when (displayType) {
            DisplayEntityType.TEXT -> DisplayPacketOperation.SpawnText(location, transform, createTextOptions(element, snapshot))
            DisplayEntityType.ITEM -> DisplayPacketOperation.SpawnItem(location, transform, createItemOptions(element, snapshot))
            DisplayEntityType.BLOCK -> DisplayPacketOperation.SpawnBlock(location, transform, createBlockOptions(element, snapshot))
        }
        return MenuRenderElementFrame(
            key = "${index}:${element.id}",
            elementId = element.id,
            element = element,
            type = displayType,
            location = location,
            logicalPosition = element.position,
            hitbox = element.hitbox ?: defaultHitbox(element.type, snapshot.interaction.defaultHitbox),
            interactive = isInteractive(element, snapshot),
            transform = transform,
            spawnOperation = operation,
        )
    }

    private fun createAnchor(player: Player, menu: MenuDefinition, snapshot: GlobalConfigSnapshot): Location {
        val render = snapshot.render
        val view = menu.view
        val distance = view.distance ?: render.distance
        val horizontalOffset = view.horizontalOffset ?: render.horizontalOffset
        val verticalOffset = view.verticalOffset ?: render.verticalOffset
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val basis = createBasis(eye)
        return eye.clone()
            .add(direction.multiply(distance))
            .add(basis.right.multiply(horizontalOffset))
            .add(0.0, verticalOffset, 0.0)
            .also {
                it.yaw = eye.yaw
                it.pitch = eye.pitch
            }
    }

    private fun createTransform(element: MenuElementDefinition, type: DisplayEntityType, snapshot: GlobalConfigSnapshot): DisplayTransform {
        val common = snapshot.render.displayDefaults.common
        val scale = element.scale ?: defaultScale(type, snapshot)
        return DisplayTransform(
            scaleX = (common.scaleX.toDouble() * scale.x).toFloat(),
            scaleY = (common.scaleY.toDouble() * scale.y).toFloat(),
            scaleZ = (common.scaleZ.toDouble() * scale.z).toFloat(),
            billboard = common.billboard,
            brightness = common.brightness.takeIf { it >= 0 }?.let { DisplayBrightness(it, it) },
            viewRange = common.viewRange,
            shadowRadius = common.shadowRadius,
            shadowStrength = common.shadowStrength,
            interpolationDelayTicks = common.interpolationDelayTicks,
            interpolationDurationTicks = common.interpolationDurationTicks,
        )
    }

    private fun defaultScale(type: DisplayEntityType, snapshot: GlobalConfigSnapshot): MenuScale {
        return when (type) {
            DisplayEntityType.TEXT -> MenuScale(1.0, 1.0, 1.0)
            DisplayEntityType.ITEM -> {
                val item = snapshot.render.displayDefaults.item
                MenuScale(item.scaleX.toDouble(), item.scaleY.toDouble(), item.scaleZ.toDouble())
            }
            DisplayEntityType.BLOCK -> {
                val block = snapshot.render.displayDefaults.block
                MenuScale(block.scaleX.toDouble(), block.scaleY.toDouble(), block.scaleZ.toDouble())
            }
        }
    }

    private fun createItemOptions(element: MenuElementDefinition, snapshot: GlobalConfigSnapshot): ItemDisplayOptions {
        val defaults = snapshot.render.displayDefaults.item
        val material = parseMaterial(element.material.ifBlank { defaults.material }, Material.PAPER)
        val transform = DisplayItemTransform.entries.firstOrNull { it.key.equals(element.transform, ignoreCase = true) } ?: defaults.transform
        return ItemDisplayOptions(ItemStack(material), transform)
    }

    private fun createBlockOptions(element: MenuElementDefinition, snapshot: GlobalConfigSnapshot): BlockDisplayOptions {
        val defaults = snapshot.render.displayDefaults.block
        return BlockDisplayOptions(parseBlockData(element.block.ifBlank { defaults.block }))
    }

    private fun MenuElementDefinition.displayType(): DisplayEntityType {
        return when (type) {
            MenuElementType.ITEM -> DisplayEntityType.ITEM
            MenuElementType.BLOCK -> DisplayEntityType.BLOCK
            MenuElementType.TEXT,
            MenuElementType.BUTTON -> DisplayEntityType.TEXT
        }
    }

    private fun defaultHitbox(type: MenuElementType, defaults: DefaultHitboxConfig): MenuHitbox {
        return when (type) {
            MenuElementType.ITEM -> MenuHitbox(defaults.itemWidth, defaults.itemHeight)
            MenuElementType.BLOCK -> MenuHitbox(defaults.blockWidth, defaults.blockHeight)
            MenuElementType.TEXT,
            MenuElementType.BUTTON -> MenuHitbox(defaults.textWidth, defaults.textHeight)
        }
    }

    private fun isInteractive(element: MenuElementDefinition, snapshot: GlobalConfigSnapshot): Boolean {
        element.interactive?.let { return it }
        return when (element.type) {
            MenuElementType.ITEM -> snapshot.interaction.itemDisplayClickable
            MenuElementType.BLOCK -> snapshot.interaction.blockDisplayClickable
            MenuElementType.TEXT,
            MenuElementType.BUTTON -> true
        }
    }

    private fun parseMaterial(input: String, default: Material): Material {
        return Material.matchMaterial(input.trim().uppercase(Locale.ROOT)) ?: default
    }

    private fun parseBlockData(input: String): BlockData {
        val raw = input.trim()
        if (raw.isBlank()) {
            return Material.STONE.createBlockData()
        }
        return runCatching { org.bukkit.Bukkit.createBlockData(raw) }.getOrElse {
            parseMaterial(raw, Material.STONE).createBlockData()
        }
    }
}
