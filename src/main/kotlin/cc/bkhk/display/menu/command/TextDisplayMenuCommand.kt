package cc.bkhk.display.menu.command

import cc.bkhk.display.menu.command.sub.TextDisplayMenuAdminCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuCloseCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuDebugCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuHelpCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuListCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuOpenCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuReloadCommand
import cc.bkhk.display.menu.command.sub.TextDisplayMenuStatusCommand
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.expansion.createHelper

@CommandHeader(
    name = "textdisplaymenu",
    aliases = ["tdm"],
    description = "TextDisplayMenu command.",
    permission = "textdisplaymenu.command",
)
object TextDisplayMenuCommand {

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    @CommandBody(aliases = ["?"], description = "Show command helper.")
    val help = TextDisplayMenuHelpCommand.command

    @CommandBody(permission = "textdisplaymenu.command.open", description = "Open a menu for yourself or another player.")
    val open = TextDisplayMenuOpenCommand.command

    @CommandBody(permission = "textdisplaymenu.command.close", description = "Close the current menu.")
    val close = TextDisplayMenuCloseCommand.command

    @CommandBody(permission = "textdisplaymenu.command.reload", description = "Reload config, menus or language files.")
    val reload = TextDisplayMenuReloadCommand.command

    @CommandBody(permission = "textdisplaymenu.command.list", description = "List loaded menus.")
    val list = TextDisplayMenuListCommand.command

    @CommandBody(permission = "textdisplaymenu.command.status", description = "Show runtime status.")
    val status = TextDisplayMenuStatusCommand.command

    @CommandBody(permission = "textdisplaymenu.command.debug", description = "Show or toggle debug options.")
    val debug = TextDisplayMenuDebugCommand.command

    @CommandBody(permission = "textdisplaymenu.command.admin", hidden = true, description = "TrMenu-compatible reserved command group.")
    val admin = TextDisplayMenuAdminCommand.command
}
