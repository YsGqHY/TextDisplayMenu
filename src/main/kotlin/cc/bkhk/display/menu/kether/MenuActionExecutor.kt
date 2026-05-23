package cc.bkhk.display.menu.kether

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.config.ShortcutAction
import cc.bkhk.display.menu.interaction.MenuClickType
import cc.bkhk.display.menu.session.MenuPendingConfirmation
import cc.bkhk.display.menu.session.MenuSessionRegistry
import org.bukkit.entity.Player
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.warning
import taboolib.common5.Coerce
import taboolib.module.chat.colored
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions
import java.util.concurrent.CompletableFuture
import taboolib.platform.util.sendLang

object MenuActionExecutor {

    fun execute(player: Player, elementKey: String?, elementId: String?, clickType: MenuClickType?, scripts: List<String>) {
        if (scripts.isEmpty()) {
            return
        }
        val snapshot = MenuSessionRegistry.current(player) ?: return
        val context = MenuActionContext(player, snapshot.menuId, snapshot.pageId, elementKey, elementId, clickType)
        executeDirect(context, scripts)
    }

    fun executeDirect(context: MenuActionContext, scripts: List<String>) {
        executeDirectFuture(context, scripts)
    }

    fun executeShortcut(context: MenuActionContext, action: ShortcutAction) {
        executeShortcutFuture(context, action)
    }

    private fun executeShortcutFuture(context: MenuActionContext, action: ShortcutAction): CompletableFuture<Any?> {
        if (action.branches.isEmpty()) {
            return executeShortcutBranch(context, action)
        }
        val directFuture = executeDirectFuture(context, action.scripts)
        return action.branches.fold(directFuture) { future, branch ->
            future.thenCompose { executeShortcutBranch(context, branch) }
        }
    }

    private fun executeShortcutBranch(context: MenuActionContext, action: ShortcutAction): CompletableFuture<Any?> {
        if (action.condition.isNullOrBlank()) {
            return executeDirectFuture(context, action.scripts.ifEmpty { action.execute })
        }
        return eval(context, action.condition).handle { result, throwable ->
            if (throwable != null) {
                warning("快捷入口条件执行失败: ${throwable.message ?: throwable.javaClass.simpleName}")
            }
            if (throwable == null && Coerce.toBoolean(result)) action.execute else action.deny
        }.thenCompose { scripts ->
            executeDirectFuture(context, scripts)
        }
    }

    private fun executeDirectFuture(context: MenuActionContext, scripts: List<String>): CompletableFuture<Any?> {
        return scripts.fold(CompletableFuture.completedFuture<Any?>(null)) { future, script ->
            future.thenCompose { executeOne(context, script) }
        }
    }

    private fun executeOne(context: MenuActionContext, script: String): CompletableFuture<Any?> {
        val trimmed = script.trim()
        if (trimmed.isBlank()) {
            return CompletableFuture.completedFuture(null)
        }
        if (trimmed.startsWith("tdmenu confirm")) {
            handleConfirm(context, trimmed)
            return CompletableFuture.completedFuture(null)
        }
        return eval(context, trimmed)
    }

    private fun eval(context: MenuActionContext, script: String): CompletableFuture<Any?> {
        val previous = MenuKetherContext.enter(context)
        return KetherShell.eval(
            script,
            ScriptOptions.new {
                sender(adaptPlayer(context.player))
                namespace(listOf("kether"))
                sandbox(true)
                vars(vars(context))
            },
        ).whenComplete { _, throwable ->
            MenuKetherContext.restore(context, previous)
            if (throwable != null) {
                warning("菜单动作执行失败: ${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }
    }

    private fun vars(context: MenuActionContext): Map<String, Any?> {
        return mapOf(
            "menu_id" to context.menuId,
            "page_id" to context.pageId,
            "element_key" to (context.elementKey ?: ""),
            "element_id" to (context.elementId ?: ""),
            "click_type" to (context.clickType?.configKey ?: ""),
            "source" to context.source,
            "shortcut_key" to (context.shortcutKey ?: ""),
            "target_name" to (context.targetName ?: ""),
            "target_uuid" to (context.targetUuid?.toString() ?: ""),
        )
    }

    fun handlePendingConfirmation(player: Player, elementKey: String, clickType: MenuClickType): Boolean {
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val pending = snapshot.pendingConfirmation ?: return false
        if (System.currentTimeMillis() > pending.expiresAt) {
            MenuSessionRegistry.setPendingConfirmation(player, null)
            player.sendLang(PluginConfig.snapshot().confirmation.expiredMessageKey)
            return true
        }
        if (pending.elementKey != elementKey) {
            return false
        }
        val context = MenuActionContext(player, snapshot.menuId, snapshot.pageId, elementKey, null, clickType)
        return when (clickType.configKey) {
            pending.acceptClick -> {
                MenuSessionRegistry.setPendingConfirmation(player, null)
                executeDirect(context, pending.thenScripts)
                true
            }
            pending.cancelClick -> {
                MenuSessionRegistry.setPendingConfirmation(player, null)
                executeDirect(context, pending.elseScripts)
                true
            }
            else -> false
        }
    }

    private fun handleConfirm(context: MenuActionContext, script: String) {
        val prompt = extractQuoted(script)
        val thenScripts = splitScripts(extractBlock(script, "then"))
        val elseScripts = splitScripts(extractBlock(script, "else"))
        startConfirmation(context, prompt, thenScripts, elseScripts)
    }

    fun startConfirmation(context: MenuActionContext, promptInput: String, thenScripts: List<String>, elseScripts: List<String>): Boolean {
        val player = context.player
        val elementKey = context.elementKey ?: return false
        val prompt = promptInput.ifBlank { player.asConfirmationPrompt() }
        val config = PluginConfig.snapshot().confirmation
        val confirmation = MenuPendingConfirmation(
            elementKey = elementKey,
            acceptClick = config.acceptClick,
            cancelClick = config.cancelClick,
            thenScripts = thenScripts,
            elseScripts = elseScripts,
            expiresAt = System.currentTimeMillis() + config.expireSeconds * 1000L,
        )
        MenuSessionRegistry.setPendingConfirmation(player, confirmation)
        player.sendMessage(prompt.colored())
        return true
    }

    private fun Player.asConfirmationPrompt(): String {
        return "&e请再次左键确认，或右键取消。"
    }

    private fun extractQuoted(script: String): String {
        val start = script.indexOf('"')
        if (start < 0) {
            return ""
        }
        val builder = StringBuilder()
        var escaped = false
        for (index in start + 1 until script.length) {
            val char = script[index]
            when {
                escaped -> {
                    builder.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '"' -> return builder.toString()
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun extractBlock(script: String, keyword: String): String {
        val keywordIndex = script.indexOf(keyword)
        if (keywordIndex < 0) {
            return ""
        }
        val start = script.indexOf('{', keywordIndex)
        if (start < 0) {
            return ""
        }
        var depth = 0
        for (index in start until script.length) {
            when (script[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return script.substring(start + 1, index).trim()
                    }
                }
            }
        }
        return ""
    }

    private fun splitScripts(block: String): List<String> {
        if (block.isBlank()) {
            return emptyList()
        }
        return block.split('\n')
            .flatMap { line -> line.split("; ") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

private fun Player.sendLang(key: String) {
    sendLang(key, PluginConfig.snapshot().confirmation.expireSeconds)
}
