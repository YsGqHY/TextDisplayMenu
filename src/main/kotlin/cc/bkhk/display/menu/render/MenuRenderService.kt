package cc.bkhk.display.menu.render

import cc.bkhk.display.menu.config.DebugRuntime
import cc.bkhk.display.menu.config.PluginConfig
import cc.bkhk.display.menu.feedback.MenuFeedbackService
import cc.bkhk.display.menu.interaction.MenuInteractionService
import cc.bkhk.display.menu.kether.MenuKetherContext
import cc.bkhk.display.menu.menu.config.MenuRegistry
import cc.bkhk.display.menu.menu.model.MenuDefinition
import cc.bkhk.display.menu.menu.model.MenuPageDefinition
import cc.bkhk.display.menu.nms.DisplayEntityHandle
import cc.bkhk.display.menu.nms.DisplayPacketOperation
import cc.bkhk.display.menu.nms.NMSDisplayPacket
import cc.bkhk.display.menu.nms.UnsupportedNMSDisplayPacketException
import cc.bkhk.display.menu.session.MenuSession
import cc.bkhk.display.menu.session.MenuSessionRegistry
import cc.bkhk.display.menu.session.MenuSessionSnapshot
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object MenuRenderService {

    private val started = AtomicBoolean(false)

    fun initialize() {
        started.set(true)
    }

    fun shutdown() {
        started.set(false)
        closeAll(preserveState = false)
        MenuSessionRegistry.clearRetainedStates()
        MenuDisplayEntityTracker.destroyAllOnline("shutdown_fallback")
    }

    fun restartTasks() {
        MenuSessionRegistry.all().forEach { snapshot ->
            val player = Bukkit.getPlayer(snapshot.playerId)
            if (player == null) {
                forgetDetachedSession(snapshot.playerId)
                return@forEach
            }
            val session = MenuSessionRegistry.session(player) ?: return@forEach
            session.cancelTasks()
            startSessionTasks(player, session)
        }
    }

    fun currentFrame(player: Player): MenuRenderFrame? {
        return MenuSessionRegistry.session(player)?.currentFrame
    }

    fun shouldFollowView(player: Player): Boolean {
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        return menu.view.followView ?: PluginConfig.snapshot().render.followView
    }

    fun shouldFollowPosition(player: Player): Boolean {
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        return menu.view.followPosition ?: PluginConfig.snapshot().render.followPosition
    }

    fun shouldLockView(player: Player): Boolean {
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        return menu.view.lockView == true
    }

    fun shouldLockPosition(player: Player): Boolean {
        val snapshot = MenuSessionRegistry.current(player) ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        return menu.view.lockPosition == true
    }

    fun updateForPlayerMotion(player: Player, reason: String = "motion"): Boolean {
        val session = MenuSessionRegistry.session(player) ?: return false
        val snapshot = session.snapshot() ?: return false
        if (!player.isOnline) {
            close(player)
            return false
        }
        if (isIdleExpired(snapshot)) {
            close(player)
            return false
        }
        updateFrame(player, session, snapshot)
        logPacket("motion player=${player.name} reason=$reason menu=${snapshot.menuId} page=${snapshot.pageId}")
        return true
    }

    fun open(player: Player, menu: MenuDefinition, pageId: String? = null, arguments: List<String> = emptyList()): MenuSessionSnapshot? {
        val page = resolvePage(menu, pageId) ?: return null
        close(player)
        val snapshot = MenuSessionRegistry.open(player, menu, page.id, arguments) ?: return null
        val session = MenuSessionRegistry.session(player) ?: return null
        if (!spawnFrame(player, session, menu, page)) {
            cleanupFailedSession(player, session)
            return null
        }
        startSessionTasks(player, session)
        logPacket("open player=${player.name} menu=${menu.id} page=${page.id} elements=${page.elements.size}")
        return MenuSessionRegistry.current(player) ?: snapshot
    }

    fun pageTo(player: Player, pageId: String, pushHistory: Boolean = true): Boolean {
        val session = MenuSessionRegistry.session(player) ?: return false
        val snapshot = session.snapshot() ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        val page = resolvePage(menu, pageId) ?: return false
        clearTemporaryRuntime(player, session, snapshot, immediateDestroy = false)
        MenuSessionRegistry.switchPage(player, menu, page.id, pushHistory) ?: return false
        val spawned = spawnFrame(player, session, menu, page)
        if (spawned) {
            startSessionTasks(player, session)
        } else {
            cleanupFailedSession(player, session)
        }
        return spawned
    }

    fun back(player: Player): Boolean {
        val session = MenuSessionRegistry.session(player) ?: return false
        val snapshot = session.snapshot() ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: return false
        val targetPage = snapshot.pageHistory.lastOrNull()
        if (targetPage == null) {
            return if (menu.controls.backClosesWhenNoHistory) close(player) else false
        }
        val page = resolvePage(menu, targetPage) ?: return false
        clearTemporaryRuntime(player, session, snapshot, immediateDestroy = false)
        MenuSessionRegistry.popHistory(player) ?: return false
        val spawned = spawnFrame(player, session, menu, page)
        if (spawned) {
            startSessionTasks(player, session)
        } else {
            cleanupFailedSession(player, session)
        }
        return spawned
    }

    fun close(player: Player, preserveState: Boolean = true): Boolean {
        val session = MenuSessionRegistry.session(player) ?: return false
        val snapshot = session.snapshot() ?: return false
        session.cancelTasks()
        MenuFeedbackService.clear(player)
        MenuKetherContext.clear(player)
        MenuRenderListener.clearViewThrottle(player.uniqueId)
        destroyHandles(player, snapshot.entityHandles.values, immediate = true, session = null)
        MenuDisplayEntityTracker.destroyAll(player, "close_fallback")
        MenuSessionRegistry.close(player, preserveState)
        logPacket("close player=${player.name} menu=${snapshot.menuId} page=${snapshot.pageId} handles=${snapshot.entityHandles.size}")
        return true
    }

    fun close(playerId: UUID, preserveState: Boolean = true): Boolean {
        val player = Bukkit.getPlayer(playerId)
        if (player != null) {
            return close(player, preserveState)
        }
        return forgetDetachedSession(playerId, preserveState)
    }

    fun closeAll(preserveState: Boolean = true): Int {
        val sessions = MenuSessionRegistry.all()
        sessions.forEach { snapshot -> close(snapshot.playerId, preserveState) }
        return sessions.size
    }

    fun refresh(player: Player): Boolean {
        val session = MenuSessionRegistry.session(player) ?: return false
        val snapshot = session.snapshot() ?: return false
        val menu = MenuRegistry.get(snapshot.menuId) ?: run {
            close(player)
            return false
        }
        val page = resolvePage(menu, snapshot.pageId) ?: run {
            close(player)
            return false
        }
        clearTemporaryRuntime(player, session, snapshot, immediateDestroy = true)
        val refreshed = spawnFrame(player, session, menu, page)
        if (refreshed) {
            startSessionTasks(player, session)
        } else {
            cleanupFailedSession(player, session)
        }
        return refreshed
    }

    fun refreshAll(menuId: String? = null): Int {
        var refreshed = 0
        MenuSessionRegistry.all().forEach { snapshot ->
            if (menuId != null && !snapshot.menuId.equals(menuId, ignoreCase = true)) {
                return@forEach
            }
            val player = Bukkit.getPlayer(snapshot.playerId) ?: run {
                forgetDetachedSession(snapshot.playerId)
                return@forEach
            }
            if (refresh(player)) {
                refreshed++
            }
        }
        return refreshed
    }

    internal fun tickSession(playerId: UUID) {
        val session = MenuSessionRegistry.session(playerId) ?: return
        val snapshot = session.snapshot() ?: return
        val player = Bukkit.getPlayer(playerId) ?: run {
            forgetDetachedSession(playerId)
            return
        }
        if (!player.isOnline) {
            close(player)
            return
        }
        if (isIdleExpired(snapshot)) {
            close(player)
            return
        }
        updateFrame(player, session, snapshot)
    }

    private fun startSessionTasks(player: Player, session: MenuSession) {
        if (!started.get()) {
            return
        }
        val interval = PluginConfig.snapshot().render.updateIntervalTicks.coerceAtLeast(1L)
        session.arrange(
            submit(delay = interval, period = interval) {
                tickSession(player.uniqueId)
            },
            temporary = true,
        )
        MenuInteractionService.startSessionTask(player, session)
    }

    private fun updateFrame(player: Player, session: MenuSession, snapshot: MenuSessionSnapshot) {
        val menu = MenuRegistry.get(snapshot.menuId) ?: run {
            close(player)
            return
        }
        val page = resolvePage(menu, snapshot.pageId) ?: run {
            close(player)
            return
        }
        val frame = MenuRenderFrameBuilder.build(player, menu, page, previousAnchorState = session.renderAnchorState)
        if (frame.layoutSignature() != snapshot.renderSignature || snapshot.entityHandles.size != frame.elements.size) {
            refresh(player)
            return
        }
        session.updateCurrentFrame(frame)
        val operations = frame.elements.mapNotNull { element ->
            val handle = snapshot.entityHandles[element.key] ?: return@mapNotNull null
            DisplayPacketOperation.Teleport(handle, element.location, element.transform)
        }
        if (operations.isNotEmpty()) {
            runNms("updateFrame", player) {
                NMSDisplayPacket.instance.sendDisplayBatch(player, operations)
            }
        }
    }

    private fun spawnFrame(player: Player, session: MenuSession, menu: MenuDefinition, page: MenuPageDefinition): Boolean {
        val frame = MenuRenderFrameBuilder.build(player, menu, page)
        if (frame.elements.isEmpty()) {
            session.updateRenderState(emptyMap(), emptyList(), frame)
            return true
        }
        val handles = runNms("spawnFrame", player) {
            NMSDisplayPacket.instance.sendDisplayBatch(player, frame.elements.map { it.spawnOperation })
        } ?: return false
        MenuDisplayEntityTracker.register(player, handles, "spawn_frame")
        if (handles.size != frame.elements.size) {
            destroyHandles(player, handles, immediate = true, session = null)
            return false
        }
        val handleMap = frame.elements.zip(handles).associate { (element, handle) -> element.key to handle }
        session.updateRenderState(handleMap, frame.layoutSignature(), frame)
        return true
    }

    private fun clearTemporaryRuntime(player: Player, session: MenuSession, snapshot: MenuSessionSnapshot, immediateDestroy: Boolean) {
        session.shutTemps()
        MenuFeedbackService.clear(player)
        destroyHandles(player, snapshot.entityHandles.values, immediate = immediateDestroy, session = session)
    }

    private fun destroyHandles(
        player: Player,
        handles: Collection<DisplayEntityHandle>,
        immediate: Boolean,
        session: MenuSession?,
    ) {
        if (handles.isEmpty()) {
            return
        }
        val delay = PluginConfig.snapshot().session.destroyDelayTicks.coerceAtLeast(0L)
        if (immediate || delay == 0L || session == null) {
            MenuDisplayEntityTracker.destroy(player, handles, "destroy_displays")
            return
        }
        val captured = handles.toList()
        submit(delay = delay) {
            if (player.isOnline) {
                MenuDisplayEntityTracker.destroy(player, captured, "destroy_displays_delayed")
            }
        }
    }

    private fun cleanupFailedSession(player: Player, session: MenuSession) {
        session.cancelTasks()
        MenuFeedbackService.clear(player)
        MenuKetherContext.clear(player)
        MenuRenderListener.clearViewThrottle(player.uniqueId)
        MenuDisplayEntityTracker.destroyAll(player, "cleanup_failed_session")
        MenuSessionRegistry.close(player)
    }

    private fun forgetDetachedSession(playerId: UUID, preserveState: Boolean = true): Boolean {
        MenuDisplayEntityTracker.forgetAll(playerId)
        return MenuSessionRegistry.close(playerId, preserveState)
    }

    private fun <T> runNms(operation: String, player: Player, block: () -> T): T? {
        return try {
            block()
        } catch (ex: UnsupportedNMSDisplayPacketException) {
            warning("TextDisplayMenu NMS 发包不可用: player=${player.name}, operation=$operation, reason=${ex.message}")
            null
        } catch (ex: Throwable) {
            warning("TextDisplayMenu NMS 发包失败: player=${player.name}, operation=$operation, reason=${ex.message ?: ex.javaClass.simpleName}")
            null
        }
    }

    private fun isIdleExpired(snapshot: MenuSessionSnapshot): Boolean {
        val timeoutMillis = PluginConfig.snapshot().session.idleTimeoutSeconds * 1000L
        if (timeoutMillis <= 0L) {
            return false
        }
        return System.currentTimeMillis() - snapshot.lastActiveAt > timeoutMillis
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

    private fun logPacket(message: String) {
        if (DebugRuntime.enabled() && DebugRuntime.packetLogging()) {
            info("[TextDisplayMenu Debug] $message")
        }
    }
}
