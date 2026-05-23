package cc.bkhk.display.menu.interaction

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.model.MenuElementType
import cc.bkhk.display.menu.render.MenuRenderFrame
import cc.bkhk.display.menu.render.MenuRenderFrameBuilder
import org.bukkit.entity.Player
import kotlin.math.abs

object MenuHitDetector {

    fun detect(player: Player, frame: MenuRenderFrame): MenuHitResult? {
        val eye = player.eyeLocation
        if (eye.world != frame.anchor.world) {
            return null
        }
        val direction = eye.direction.normalize()
        val normal = frame.basis.forward.clone().normalize()
        val denominator = direction.dot(normal)
        if (abs(denominator) < 1.0E-5) {
            return null
        }
        val t = frame.anchor.toVector().subtract(eye.toVector()).dot(normal) / denominator
        if (t <= 0.0 || t > PluginConfig.snapshot().interaction.maxDistance) {
            return null
        }
        val hitPoint = eye.toVector().add(direction.multiply(t))
        val relative = hitPoint.subtract(frame.anchor.toVector())
        val x = relative.dot(frame.basis.right) * MenuRenderFrameBuilder.PIXELS_PER_BLOCK
        val y = -relative.dot(frame.basis.up) * MenuRenderFrameBuilder.PIXELS_PER_BLOCK
        val candidates = frame.elements.asSequence()
            .filter { it.interactive }
            .filter { element ->
                val halfWidth = element.hitbox.width / 2.0
                val halfHeight = element.hitbox.height / 2.0
                abs(x - element.logicalPosition.x) <= halfWidth && abs(y - element.logicalPosition.y) <= halfHeight
            }
            .map { element -> MenuHitResult(element, x - element.logicalPosition.x, y - element.logicalPosition.y, t) }
            .toList()
        if (candidates.isEmpty()) {
            return null
        }
        val priority = PluginConfig.snapshot().interaction.hitPriority.mapIndexed { index, key -> key.lowercase() to index }.toMap()
        return candidates.minWith(
            compareBy<MenuHitResult> { priority[it.element.element.type.priorityKey()] ?: Int.MAX_VALUE }
                .thenByDescending { it.element.logicalPosition.z }
                .thenBy { frame.elements.indexOf(it.element) }
        )
    }

    private fun MenuElementType.priorityKey(): String {
        return when (this) {
            MenuElementType.BUTTON -> "button"
            MenuElementType.ITEM -> "item"
            MenuElementType.BLOCK -> "block"
            MenuElementType.TEXT -> "text"
        }
    }
}
