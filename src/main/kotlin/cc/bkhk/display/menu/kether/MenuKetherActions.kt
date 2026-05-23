package cc.bkhk.display.menu.kether

import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.render.MenuRenderService
import org.bukkit.Bukkit
import org.bukkit.Material
import taboolib.common.Inject
import taboolib.module.kether.KetherParser
import taboolib.module.kether.combinationParser
import taboolib.platform.util.sendLang

@Inject
object MenuKetherActions {

    @KetherParser(["tdmenu"])
    fun tdmenu() = combinationParser {
        it.group(text(), text().option()).apply(it) { action, value ->
            now {
                val context = MenuKetherContext.current(this) ?: return@now false
                when (action.lowercase()) {
                    "open" -> openMenu(context, value ?: return@now false)
                    "page" -> MenuRenderService.pageTo(context.player, value ?: return@now false)
                    "back" -> backMenu(context, value)
                    "close" -> closeMenu(context, value)
                    "refresh" -> refreshMenu(context, value)
                    "confirm" -> MenuActionExecutor.startConfirmation(context, value.orEmpty(), emptyList(), emptyList())
                    "tell-lang", "lang" -> tellLang(context, value ?: return@now false)
                    "hand-empty", "empty-hand" -> isHandEmpty(context)
                    else -> false
                }
            }
        }
    }

    private fun openMenu(context: MenuActionContext, menuId: String): Boolean {
        val menu = MenuRegistry.get(menuId) ?: return false
        return MenuRenderService.open(context.player, menu) != null
    }

    private fun backMenu(context: MenuActionContext, fallbackPage: String?): Boolean {
        return if (MenuRenderService.back(context.player)) {
            true
        } else if (!fallbackPage.isNullOrBlank()) {
            MenuRenderService.pageTo(context.player, fallbackPage, pushHistory = false)
        } else {
            false
        }
    }

    private fun closeMenu(context: MenuActionContext, playerName: String?): Boolean {
        val target = playerName?.takeIf { name -> name.isNotBlank() }?.let { name -> Bukkit.getPlayerExact(name) } ?: context.player
        return MenuRenderService.close(target)
    }

    private fun refreshMenu(context: MenuActionContext, playerName: String?): Boolean {
        val target = playerName?.takeIf { name -> name.isNotBlank() }?.let { name -> Bukkit.getPlayerExact(name) } ?: context.player
        return MenuRenderService.refresh(target)
    }

    private fun tellLang(context: MenuActionContext, key: String): Boolean {
        context.player.sendLang(key)
        return true
    }

    private fun isHandEmpty(context: MenuActionContext): Boolean {
        val type = context.player.inventory.itemInMainHand.type
        return type == Material.AIR || type.name.endsWith("_AIR")
    }
}
