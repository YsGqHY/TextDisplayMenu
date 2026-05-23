package cc.bkhk.display.menu.session

import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.nms.DisplayEntityHandle
import cc.bkhk.display.menu.nms.DisplayEntityType
import cc.bkhk.display.menu.render.MenuRenderFrame
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import taboolib.common.function.debounce
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 等待玩家二次确认的菜单动作。
 */
data class MenuPendingConfirmation(
    val elementKey: String,
    val acceptClick: String,
    val cancelClick: String,
    val thenScripts: List<String>,
    val elseScripts: List<String>,
    val expiresAt: Long,
)

/**
 * 菜单关闭后的轻量状态缓存，只保留可恢复的页面状态，不保留实体和临时反馈。
 */
data class MenuRetainedSessionState(
    val playerId: UUID,
    val menuId: String,
    val pageId: String,
    val arguments: List<String>,
    val pageHistory: List<String>,
    val retainedAt: Long = System.currentTimeMillis(),
)

private data class MenuRetainedSessionKey(
    val playerId: UUID,
    val menuId: String,
)

/**
 * 对外只读会话视图。命令、反馈和调试代码只依赖该快照，避免直接修改运行态。
 */
data class MenuSessionSnapshot(
    val playerId: UUID,
    val menuId: String,
    val pageId: String,
    val openedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = openedAt,
    val arguments: List<String> = emptyList(),
    val entityHandles: Map<String, DisplayEntityHandle> = emptyMap(),
    val renderSignature: List<Pair<String, DisplayEntityType>> = emptyList(),
    val pageHistory: List<String> = emptyList(),
    val focusedElementKey: String? = null,
    val focusCandidateKey: String? = null,
    val focusCandidateSinceTick: Long = 0L,
    val lastClickAt: Long = 0L,
    val hoverPopupHandle: DisplayEntityHandle? = null,
    val hoverPopupElementKey: String? = null,
    val hoverPopupExpiresAtTick: Long = 0L,
    val bossBar: BossBar? = null,
    val bossBarElementKey: String? = null,
    val bossBarLastSentTick: Long = 0L,
    val actionbarLastSentTick: Long = 0L,
    val titleLastSentTick: Long = 0L,
    val pendingConfirmation: MenuPendingConfirmation? = null,
)

/**
 * 单个玩家的菜单运行态。参考 TrMenu 的 MenuSession：会话自己持有任务、帧和临时状态。
 */
class MenuSession(val playerId: UUID) {

    var menuId: String? = null
        private set
    var pageId: String? = null
        private set
    var openedAt: Long = 0L
        private set
    var lastActiveAt: Long = 0L
        private set
    var arguments: List<String> = emptyList()
    var entityHandles: Map<String, DisplayEntityHandle> = emptyMap()
        private set
    var renderSignature: List<Pair<String, DisplayEntityType>> = emptyList()
        private set
    var pageHistory: List<String> = emptyList()
        private set
    var focusedElementKey: String? = null
        private set
    var focusCandidateKey: String? = null
        private set
    var focusCandidateSinceTick: Long = 0L
        private set
    var lastClickAt: Long = 0L
        private set
    var hoverPopupHandle: DisplayEntityHandle? = null
        private set
    var hoverPopupElementKey: String? = null
        private set
    var hoverPopupExpiresAtTick: Long = 0L
        private set
    var bossBar: BossBar? = null
        private set
    var bossBarElementKey: String? = null
        private set
    var bossBarLastSentTick: Long = 0L
        private set
    var actionbarLastSentTick: Long = 0L
        private set
    var titleLastSentTick: Long = 0L
        private set
    var pendingConfirmation: MenuPendingConfirmation? = null
        private set
    var currentFrame: MenuRenderFrame? = null
        private set
    var interactionTick: Long = 0L
        private set

    private val tasking = mutableSetOf<PlatformExecutor.PlatformTask>()
    private val temporaries = mutableSetOf<PlatformExecutor.PlatformTask>()

    fun open(menu: MenuDefinition, page: MenuPageDefinition, arguments: List<String>, retainedState: MenuRetainedSessionState? = null) {
        shut()
        val now = System.currentTimeMillis()
        menuId = menu.id
        pageId = page.id
        openedAt = now
        lastActiveAt = now
        this.arguments = arguments
        pageHistory = retainedState?.pageHistory.orEmpty()
    }

    fun switchPage(page: MenuPageDefinition, pushHistory: Boolean = true) {
        val previousPage = pageId
        pageId = page.id
        lastActiveAt = System.currentTimeMillis()
        if (pushHistory && previousPage != null && previousPage != page.id) {
            pageHistory = pageHistory + previousPage
        }
        clearPageRuntime()
    }

    fun popHistory(): PagePopResult? {
        val snapshot = snapshot() ?: return null
        val targetPage = pageHistory.lastOrNull()
        if (targetPage == null) {
            return PagePopResult(snapshot, null)
        }
        pageId = targetPage
        lastActiveAt = System.currentTimeMillis()
        pageHistory = pageHistory.dropLast(1)
        clearPageRuntime()
        return snapshot()?.let { PagePopResult(it, targetPage) }
    }

    fun updateRenderState(handles: Map<String, DisplayEntityHandle>, signature: List<Pair<String, DisplayEntityType>>, frame: MenuRenderFrame?) {
        lastActiveAt = System.currentTimeMillis()
        entityHandles = handles
        renderSignature = signature
        currentFrame = frame
    }

    fun updateCurrentFrame(frame: MenuRenderFrame?) {
        currentFrame = frame
    }

    fun updateFocusCandidate(candidateKey: String?, currentTick: Long) {
        if (focusCandidateKey == candidateKey) {
            return
        }
        focusCandidateKey = candidateKey
        focusCandidateSinceTick = currentTick
    }

    fun setFocusedElement(elementKey: String?) {
        focusedElementKey = elementKey
        lastActiveAt = System.currentTimeMillis()
    }

    fun updateClick(clickAt: Long = System.currentTimeMillis()) {
        lastClickAt = clickAt
        lastActiveAt = clickAt
    }

    fun setHoverPopup(handle: DisplayEntityHandle?, elementKey: String?, expiresAtTick: Long) {
        hoverPopupHandle = handle
        hoverPopupElementKey = elementKey
        hoverPopupExpiresAtTick = expiresAtTick
    }

    fun setBossBar(bossBar: BossBar?, elementKey: String?, sentTick: Long) {
        this.bossBar = bossBar
        bossBarElementKey = elementKey
        bossBarLastSentTick = sentTick
    }

    fun markActionbarSent(tick: Long) {
        actionbarLastSentTick = tick
    }

    fun markTitleSent(tick: Long) {
        titleLastSentTick = tick
    }

    fun setPendingConfirmation(confirmation: MenuPendingConfirmation?) {
        pendingConfirmation = confirmation
        lastActiveAt = System.currentTimeMillis()
    }

    fun touch() {
        lastActiveAt = System.currentTimeMillis()
    }

    fun nextInteractionTick(step: Long): Long {
        interactionTick += step
        return interactionTick
    }

    fun arrange(task: PlatformExecutor.PlatformTask, temporary: Boolean = false) {
        tasking.add(task)
        if (temporary) {
            temporaries.add(task)
        }
    }

    fun cancelTasks() {
        tasking.removeIf { task ->
            task.cancel()
            true
        }
        temporaries.clear()
    }

    fun shutTemps() {
        tasking.removeIf { task ->
            if (temporaries.remove(task)) {
                task.cancel()
                true
            } else {
                false
            }
        }
        clearFocusRuntime()
    }

    fun shut() {
        cancelTasks()
        menuId = null
        pageId = null
        openedAt = 0L
        lastActiveAt = 0L
        arguments = emptyList()
        pageHistory = emptyList()
        clearPageRuntime()
    }

    fun isViewing(): Boolean {
        return menuId != null && pageId != null
    }

    fun snapshot(): MenuSessionSnapshot? {
        val menu = menuId ?: return null
        val page = pageId ?: return null
        return MenuSessionSnapshot(
            playerId = playerId,
            menuId = menu,
            pageId = page,
            openedAt = openedAt,
            lastActiveAt = lastActiveAt,
            arguments = arguments,
            entityHandles = entityHandles,
            renderSignature = renderSignature,
            pageHistory = pageHistory,
            focusedElementKey = focusedElementKey,
            focusCandidateKey = focusCandidateKey,
            focusCandidateSinceTick = focusCandidateSinceTick,
            lastClickAt = lastClickAt,
            hoverPopupHandle = hoverPopupHandle,
            hoverPopupElementKey = hoverPopupElementKey,
            hoverPopupExpiresAtTick = hoverPopupExpiresAtTick,
            bossBar = bossBar,
            bossBarElementKey = bossBarElementKey,
            bossBarLastSentTick = bossBarLastSentTick,
            actionbarLastSentTick = actionbarLastSentTick,
            titleLastSentTick = titleLastSentTick,
            pendingConfirmation = pendingConfirmation,
        )
    }

    private fun clearPageRuntime() {
        entityHandles = emptyMap()
        renderSignature = emptyList()
        currentFrame = null
        clearFocusRuntime()
        hoverPopupHandle = null
        hoverPopupElementKey = null
        hoverPopupExpiresAtTick = 0L
        bossBar = null
        bossBarElementKey = null
        bossBarLastSentTick = 0L
        pendingConfirmation = null
    }

    private fun clearFocusRuntime() {
        focusedElementKey = null
        focusCandidateKey = null
        focusCandidateSinceTick = 0L
        interactionTick = 0L
        actionbarLastSentTick = 0L
        titleLastSentTick = 0L
    }
}

object MenuSessionRegistry {

    private val sessions = ConcurrentHashMap<UUID, MenuSession>()
    private val retainedStates = ConcurrentHashMap<MenuRetainedSessionKey, MenuRetainedSessionState>()
    private val cleanupRetainedState = debounce<MenuRetainedSessionKey>(0L) { key ->
        retainedStates.remove(key)
    }

    fun session(player: Player): MenuSession? {
        return session(player.uniqueId)
    }

    fun session(playerId: UUID): MenuSession? {
        return sessions[playerId]?.takeIf { it.isViewing() }
    }

    fun getSession(player: Player): MenuSession {
        return sessions.computeIfAbsent(player.uniqueId) { MenuSession(player.uniqueId) }
    }

    fun open(player: Player, menu: MenuDefinition, pageId: String? = null, arguments: List<String> = emptyList()): MenuSessionSnapshot? {
        val key = MenuRetainedSessionKey(player.uniqueId, menu.id)
        val retainedState = retainedStateForOpen(key, pageId, arguments)
        val targetPageId = pageId ?: retainedState?.pageId
        val restoredPage = resolvePage(menu, targetPageId)
        val page = restoredPage ?: if (pageId.isNullOrBlank() && retainedState != null) resolvePage(menu, null) else null
        page ?: return null
        val stateToRestore = retainedState?.takeIf { restoredPage != null && restoredPage.id == it.pageId }
        retainedStates.remove(key)
        cleanupRetainedState.removeKey(key)
        val session = getSession(player)
        session.open(menu, page, arguments.ifEmpty { stateToRestore?.arguments.orEmpty() }, stateToRestore)
        return session.snapshot()
    }

    fun switchPage(player: Player, menu: MenuDefinition, pageId: String, pushHistory: Boolean = true): MenuSessionSnapshot? {
        val page = resolvePage(menu, pageId) ?: return null
        val session = session(player) ?: return null
        session.switchPage(page, pushHistory)
        return session.snapshot()
    }

    fun popHistory(player: Player): PagePopResult? {
        return session(player)?.popHistory()
    }

    fun updateRenderState(
        player: Player,
        handles: Map<String, DisplayEntityHandle>,
        signature: List<Pair<String, DisplayEntityType>>,
        frame: MenuRenderFrame? = null,
    ): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.updateRenderState(handles, signature, frame)
        return session.snapshot()
    }

    fun updateCurrentFrame(player: Player, frame: MenuRenderFrame?): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.updateCurrentFrame(frame)
        return session.snapshot()
    }

    fun updateFocusCandidate(player: Player, candidateKey: String?, currentTick: Long): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.updateFocusCandidate(candidateKey, currentTick)
        return session.snapshot()
    }

    fun setFocusedElement(player: Player, elementKey: String?): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.setFocusedElement(elementKey)
        return session.snapshot()
    }

    fun updateClick(player: Player, clickAt: Long = System.currentTimeMillis()): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.updateClick(clickAt)
        return session.snapshot()
    }

    fun setHoverPopup(player: Player, handle: DisplayEntityHandle?, elementKey: String?, expiresAtTick: Long): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.setHoverPopup(handle, elementKey, expiresAtTick)
        return session.snapshot()
    }

    fun setBossBar(player: Player, bossBar: BossBar?, elementKey: String?, sentTick: Long): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.setBossBar(bossBar, elementKey, sentTick)
        return session.snapshot()
    }

    fun markActionbarSent(player: Player, tick: Long): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.markActionbarSent(tick)
        return session.snapshot()
    }

    fun markTitleSent(player: Player, tick: Long): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.markTitleSent(tick)
        return session.snapshot()
    }

    fun setPendingConfirmation(player: Player, confirmation: MenuPendingConfirmation?): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.setPendingConfirmation(confirmation)
        return session.snapshot()
    }

    fun touch(player: Player): MenuSessionSnapshot? {
        val session = session(player) ?: return null
        session.touch()
        return session.snapshot()
    }

    fun close(player: Player): Boolean {
        return close(player.uniqueId)
    }

    fun close(playerId: UUID): Boolean {
        val removed = sessions.remove(playerId) ?: return false
        retainState(removed.snapshot())
        removed.shut()
        return true
    }

    fun closeAll(): Int {
        val activeSnapshots = all()
        activeSnapshots.forEach { retainState(it) }
        sessions.values.forEach { it.shut() }
        sessions.clear()
        return activeSnapshots.size
    }

    fun current(player: Player): MenuSessionSnapshot? {
        return current(player.uniqueId)
    }

    fun current(playerId: UUID): MenuSessionSnapshot? {
        return sessions[playerId]?.snapshot()
    }

    fun size(): Int {
        return sessions.values.count { it.isViewing() }
    }

    fun all(): List<MenuSessionSnapshot> {
        return sessions.values.mapNotNull { it.snapshot() }
    }

    fun retainedSize(): Int {
        return retainedStates.size
    }

    fun clearRetainedStates() {
        cleanupRetainedState.clearAll()
        retainedStates.clear()
    }

    fun clear() {
        sessions.values.forEach { it.shut() }
        sessions.clear()
        clearRetainedStates()
    }

    private fun retainedStateForOpen(key: MenuRetainedSessionKey, pageId: String?, arguments: List<String>): MenuRetainedSessionState? {
        if (!pageId.isNullOrBlank() || arguments.isNotEmpty()) {
            return null
        }
        return retainedStates[key]?.takeIf { state ->
            val keepMillis = PluginConfig.snapshot().session.keepStateSeconds.coerceAtLeast(0L) * 1000L
            keepMillis > 0L && System.currentTimeMillis() - state.retainedAt <= keepMillis
        }
    }

    private fun retainState(snapshot: MenuSessionSnapshot?) {
        val keepMillis = PluginConfig.snapshot().session.keepStateSeconds.coerceAtLeast(0L) * 1000L
        if (snapshot == null || keepMillis <= 0L) {
            return
        }
        val key = MenuRetainedSessionKey(snapshot.playerId, snapshot.menuId)
        retainedStates[key] = MenuRetainedSessionState(
            playerId = snapshot.playerId,
            menuId = snapshot.menuId,
            pageId = snapshot.pageId,
            arguments = snapshot.arguments,
            pageHistory = snapshot.pageHistory,
        )
        cleanupRetainedState(key, keepMillis)
    }

    private fun resolvePage(menu: MenuDefinition, pageId: String?): MenuPageDefinition? {
        if (pageId.isNullOrBlank()) {
            return menu.defaultPage
        }
        val index = pageId.toIntOrNull()
        if (index != null && index in 1..menu.pages.size) {
            return menu.pages[index - 1]
        }
        return menu.pages.firstOrNull { it.id.equals(pageId, ignoreCase = true) }
    }
}

data class PagePopResult(
    val snapshot: MenuSessionSnapshot,
    val targetPageId: String?,
)
