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
import cc.bkhk.display.menu.session.MenuSessionRegistry
import cc.bkhk.display.menu.text.MenuTextContext
import cc.bkhk.display.menu.text.MenuTextProcessor
import cc.bkhk.display.menu.text.MenuTextUse
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.Locale

object MenuRenderFrameBuilder {

    const val PIXELS_PER_BLOCK = 100.0

    fun build(
        player: Player,
        menu: MenuDefinition,
        page: MenuPageDefinition,
        snapshot: GlobalConfigSnapshot = PluginConfig.snapshot(),
        previousAnchorState: MenuRenderAnchorState? = null,
    ): MenuRenderFrame {
        val playerBase = createPlayerBase(player)
        val anchorState = previousAnchorState?.takeIf { it.playerBase.world == playerBase.world }?.copy(
            playerBase = playerBase,
        ) ?: createAnchorState(player, menu, snapshot, playerBase)
        val anchor = createAnchor(anchorState)
        val elements = page.elements.mapIndexed { index, element ->
            buildElement(index, player, menu, page, element, anchor, anchorState.basis, snapshot)
        }
        return MenuRenderFrame(menu.id, page.id, anchor, anchorState.basis, anchorState, elements)
    }

    fun createBasis(location: Location): MenuRenderBasis {
        val forward = location.direction.normalize()
        val worldUp = Vector(0.0, 1.0, 0.0)
        val right = forward.clone().crossProduct(worldUp).normalize()
        val up = right.clone().crossProduct(forward).normalize()
        return MenuRenderBasis(forward, right, up)
    }

    fun createTextOptions(
        player: Player,
        menuId: String,
        pageId: String,
        element: MenuElementDefinition,
        snapshot: GlobalConfigSnapshot = PluginConfig.snapshot(),
        focused: Boolean = false,
        textUse: MenuTextUse? = null,
    ): TextDisplayOptions {
        val use = textUse ?: if (focused && element.focusedText.isNotBlank()) MenuTextUse.ELEMENT_FOCUSED_TEXT else MenuTextUse.ELEMENT_TEXT
        val rawText = when {
            focused && element.focusedText.isNotBlank() -> element.focusedText
            element.text.isNotBlank() -> element.text
            element.focusedText.isNotBlank() -> element.focusedText
            else -> element.id
        }
        val text = MenuTextProcessor.format(rawText, textContext(player, menuId, pageId, element.id, use))
        val defaults = snapshot.render.displayDefaults.text
        return TextDisplayOptions(
            text = text,
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
        player: Player,
        menu: MenuDefinition,
        page: MenuPageDefinition,
        element: MenuElementDefinition,
        anchor: Location,
        basis: MenuRenderBasis,
        snapshot: GlobalConfigSnapshot,
    ): MenuRenderElementFrame {
        val location = anchor.clone().add(
            basis.right.clone().multiply(element.position.x / PIXELS_PER_BLOCK),
        ).add(
            basis.up.clone().multiply(element.position.y / PIXELS_PER_BLOCK),
        ).add(
            basis.forward.clone().multiply(element.position.z),
        )
        location.yaw = anchor.yaw
        location.pitch = anchor.pitch

        val displayType = element.prepared.displayType
        val transform = createTransform(element, displayType, snapshot)
        val operation = when (displayType) {
            DisplayEntityType.TEXT -> DisplayPacketOperation.SpawnText(location, transform, createTextOptions(player, menu.id, page.id, element, snapshot))
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

    private fun createPlayerBase(player: Player): Location {
        return player.eyeLocation
    }

    private fun createAnchorState(player: Player, menu: MenuDefinition, snapshot: GlobalConfigSnapshot, playerBase: Location): MenuRenderAnchorState {
        val render = snapshot.render
        val view = menu.view
        val distance = view.distance ?: render.distance
        val horizontalOffset = view.horizontalOffset ?: render.horizontalOffset
        val verticalOffset = view.verticalOffset ?: render.verticalOffset
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val basis = createBasis(eye)
        val anchor = eye.clone()
            .add(direction.multiply(distance))
            .add(basis.right.clone().multiply(horizontalOffset))
            .add(0.0, verticalOffset, 0.0)
        return MenuRenderAnchorState(
            playerBase = playerBase,
            anchorOffset = anchor.toVector().subtract(playerBase.toVector()),
            basis = basis,
            yaw = eye.yaw,
            pitch = eye.pitch,
        )
    }

    private fun createAnchor(anchorState: MenuRenderAnchorState): Location {
        return anchorState.playerBase.clone().add(anchorState.anchorOffset).also {
            it.yaw = anchorState.yaw
            it.pitch = anchorState.pitch
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
        val material = element.prepared.material ?: parseMaterial(defaults.material, Material.PAPER)
        val transform = element.prepared.itemTransform ?: defaults.transform
        return ItemDisplayOptions(ItemStack(material), transform)
    }

    private fun createBlockOptions(element: MenuElementDefinition, snapshot: GlobalConfigSnapshot): BlockDisplayOptions {
        val defaults = snapshot.render.displayDefaults.block
        return BlockDisplayOptions(element.prepared.blockData ?: parseBlockData(defaults.block))
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

    private fun textContext(player: Player, menuId: String, pageId: String, elementId: String, use: MenuTextUse): MenuTextContext {
        val arguments = MenuSessionRegistry.current(player)?.arguments.orEmpty()
        return MenuTextContext(
            player = player,
            menuId = menuId,
            pageId = pageId,
            elementId = elementId,
            use = use,
            arguments = arguments,
        )
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
