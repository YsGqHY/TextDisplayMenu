package cc.bkhk.display.menu.interaction.shortcut

import cc.bkhk.display.menu.config.DebugRuntime
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.config.ShortcutAction
import cc.bkhk.display.menu.kether.MenuActionContext
import cc.bkhk.display.menu.kether.MenuActionExecutor
import cc.bkhk.display.menu.render.MenuRenderService
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import taboolib.common.platform.function.info
import taboolib.platform.util.sendLang
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MenuShortcutService {

    private const val COOLDOWN_MESSAGE_KEY = "shortcut-cooldown"
    private const val BLOCKED_MESSAGE_KEY = "shortcut-blocked"

    private val lastShortcutAt = ConcurrentHashMap<UUID, Long>()
    private val lastSneakingAt = ConcurrentHashMap<UUID, Long>()
    private val lastLeftAirInteractAt = ConcurrentHashMap<UUID, Long>()

    fun initialize() {
        cleanupOffline()
    }

    fun shutdown() {
        lastShortcutAt.clear()
        lastSneakingAt.clear()
        lastLeftAirInteractAt.clear()
    }

    fun clear(playerId: UUID) {
        lastShortcutAt.remove(playerId)
        lastSneakingAt.remove(playerId)
        lastLeftAirInteractAt.remove(playerId)
    }

    fun markSneaking(player: Player, sneaking: Boolean) {
        if (sneaking) {
            lastSneakingAt[player.uniqueId] = System.currentTimeMillis()
        } else if (PluginConfig.snapshot().shortcut.sneakingValidMs <= 0L) {
            lastSneakingAt.remove(player.uniqueId)
        }
    }

    fun isSneakingCombo(player: Player): Boolean {
        if (player.isSneaking) {
            return true
        }
        val lastAt = lastSneakingAt[player.uniqueId] ?: return false
        val validMs = PluginConfig.snapshot().shortcut.sneakingValidMs.coerceAtLeast(0L)
        if (validMs <= 0L) {
            lastSneakingAt.remove(player.uniqueId)
            return false
        }
        val valid = System.currentTimeMillis() - lastAt <= validMs
        if (!valid) {
            lastSneakingAt.remove(player.uniqueId)
        }
        return valid
    }

    fun chooseKey(player: Player, normal: MenuShortcutKey, sneaking: MenuShortcutKey? = null): MenuShortcutKey {
        if (sneaking == null || !isSneakingCombo(player)) {
            return normal
        }
        val config = PluginConfig.snapshot().shortcut
        return if (config.actions.containsKey(sneaking.configKey)) sneaking else normal
    }

    fun handle(trigger: MenuShortcutTrigger): Boolean {
        if (trigger.key == MenuShortcutKey.LEFT_CLICK_AIR) {
            lastLeftAirInteractAt[trigger.player.uniqueId] = System.currentTimeMillis()
        }
        return handleInternal(trigger)
    }

    fun handleAnimation(trigger: MenuShortcutTrigger): Boolean {
        val lastInteractAt = lastLeftAirInteractAt[trigger.player.uniqueId]
        if (lastInteractAt != null && System.currentTimeMillis() - lastInteractAt <= 80L) {
            return false
        }
        return handleInternal(trigger)
    }

    private fun handleInternal(trigger: MenuShortcutTrigger): Boolean {
        cleanupOffline()
        val player = trigger.player
        val config = PluginConfig.snapshot().shortcut
        if (!config.enabled) {
            return false
        }
        val action = config.actions[trigger.key.configKey]
        val hasConfiguredWork = action?.hasWork() == true
        val canCloseDefaultMenu = isDefaultMenuToggle(player, trigger)
        if (!hasConfiguredWork && !canCloseDefaultMenu) {
            return false
        }
        if (!passesConditions(player)) {
            player.sendLang(BLOCKED_MESSAGE_KEY)
            return true
        }
        val now = System.currentTimeMillis()
        val cooldownLeft = cooldownLeft(player, now)
        if (cooldownLeft > 0L) {
            player.sendLang(COOLDOWN_MESSAGE_KEY, cooldownLeft)
            return true
        }
        lastShortcutAt[player.uniqueId] = now
        if (action != null && hasConfiguredWork) {
            execute(player, trigger, action)
            logShortcut("shortcut player=${player.name} key=${trigger.key.configKey} target=${trigger.targetName ?: "-"}")
            return true
        }
        if (canCloseDefaultMenu) {
            MenuRenderService.close(player)
            logShortcut("shortcut-close player=${player.name} key=${trigger.key.configKey}")
            return true
        }
        return false
    }

    private fun execute(player: Player, trigger: MenuShortcutTrigger, action: ShortcutAction) {
        val snapshot = MenuSessionRegistry.current(player)
        val context = MenuActionContext(
            player = player,
            menuId = snapshot?.menuId.orEmpty(),
            pageId = snapshot?.pageId.orEmpty(),
            source = "shortcut",
            shortcutKey = trigger.key.configKey,
            targetName = trigger.targetName,
            targetUuid = trigger.targetUuid,
        )
        MenuActionExecutor.executeShortcut(context, action)
    }

    private fun isDefaultMenuToggle(player: Player, trigger: MenuShortcutTrigger): Boolean {
        val config = PluginConfig.snapshot().shortcut
        val current = MenuSessionRegistry.current(player) ?: return false
        if (!current.menuId.equals(config.defaultMenu, ignoreCase = true)) {
            return false
        }
        return trigger.key == MenuShortcutKey.SNEAKING_OFFHAND || trigger.key == MenuShortcutKey.SNEAKING_RIGHT_CLICK_AIR
    }

    private fun passesConditions(player: Player): Boolean {
        val conditions = PluginConfig.snapshot().shortcut.conditions
        if (conditions.ignoreWhenInventoryOpen && player.openInventory.type != InventoryType.CRAFTING) {
            return false
        }
        if (conditions.blockSpectator && player.gameMode == GameMode.SPECTATOR) {
            return false
        }
        if (conditions.blockInsideVehicle && player.isInsideVehicle) {
            return false
        }
        if (conditions.requireEmptyHand && !player.inventory.itemInMainHand.type.isAir) {
            return false
        }
        if (conditions.blockedWorlds.any { it.equals(player.world.name, ignoreCase = true) }) {
            return false
        }
        return true
    }

    private fun cooldownLeft(player: Player, now: Long): Long {
        val cooldown = PluginConfig.snapshot().shortcut.cooldownMs.coerceAtLeast(0L)
        if (cooldown <= 0L) {
            return 0L
        }
        val lastAt = lastShortcutAt[player.uniqueId] ?: return 0L
        val left = cooldown - (now - lastAt)
        return left.coerceAtLeast(0L)
    }

    private fun ShortcutAction.hasWork(): Boolean {
        return scripts.isNotEmpty() || !condition.isNullOrBlank() || execute.isNotEmpty() || deny.isNotEmpty() || branches.any { it.hasWork() }
    }

    private fun cleanupOffline() {
        lastShortcutAt.keys.removeIf { org.bukkit.Bukkit.getPlayer(it) == null }
        lastSneakingAt.keys.removeIf { org.bukkit.Bukkit.getPlayer(it) == null }
        lastLeftAirInteractAt.keys.removeIf { org.bukkit.Bukkit.getPlayer(it) == null }
    }

    private fun logShortcut(message: String) {
        if (DebugRuntime.enabled() && DebugRuntime.interactionLogging()) {
            info("[TextDisplayMenu Debug] $message")
        }
    }
}
