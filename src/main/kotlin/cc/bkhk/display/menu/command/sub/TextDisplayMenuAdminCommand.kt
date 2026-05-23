package cc.bkhk.display.menu.command.sub

import org.bukkit.command.CommandSender
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggest
import taboolib.common.platform.command.suggestUncheck
import taboolib.platform.util.sendLang

object TextDisplayMenuAdminCommand {

    private val unsupportedCommandSuggestions = listOf("action", "template", "item", "sounds", "migrate")

    val command = subCommand {
        dynamic("command") {
            suggest { unsupportedCommandSuggestions }
            execute<CommandSender> { sender, _, command -> sendUnsupportedCommand(sender, command) }
            dynamic("arguments", optional = true) {
                suggestUncheck { emptyList() }
                execute<CommandSender> { sender, context, _ -> sendUnsupportedCommand(sender, context["command"]) }
            }
        }
        execute<CommandSender> { sender, _, _ -> sendUnsupportedCommand(sender, "admin") }
    }

    private fun sendUnsupportedCommand(sender: CommandSender, command: String) {
        sender.sendLang("command-unsupported", command)
    }
}
