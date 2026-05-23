package cc.bkhk.display.menu.config

import java.util.concurrent.atomic.AtomicBoolean

object DebugRuntime {

    private val enabled = AtomicBoolean(false)
    private val packetLogging = AtomicBoolean(false)
    private val interactionLogging = AtomicBoolean(false)

    fun syncFromConfig(snapshot: GlobalConfigSnapshot = PluginConfig.snapshot()) {
        enabled.set(snapshot.debug.enabled)
        packetLogging.set(snapshot.debug.logPackets)
        interactionLogging.set(snapshot.debug.logInteractions)
    }

    fun enabled(): Boolean {
        return enabled.get()
    }

    fun packetLogging(): Boolean {
        return packetLogging.get()
    }

    fun interactionLogging(): Boolean {
        return interactionLogging.get()
    }

    fun setEnabled(value: Boolean): Boolean {
        enabled.set(value)
        return value
    }

    fun setPacketLogging(value: Boolean): Boolean {
        packetLogging.set(value)
        return value
    }

    fun setInteractionLogging(value: Boolean): Boolean {
        interactionLogging.set(value)
        return value
    }

    fun toggleEnabled(): Boolean {
        return toggle(enabled)
    }

    fun togglePacketLogging(): Boolean {
        return toggle(packetLogging)
    }

    fun toggleInteractionLogging(): Boolean {
        return toggle(interactionLogging)
    }

    private fun toggle(target: AtomicBoolean): Boolean {
        while (true) {
            val current = target.get()
            val next = !current
            if (target.compareAndSet(current, next)) {
                return next
            }
        }
    }
}
