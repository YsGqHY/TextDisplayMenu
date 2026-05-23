package cc.bkhk.display.menu.menu.config

import cc.bkhk.display.menu.menu.model.MenuDefinition
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.warning
import taboolib.common5.FileWatcher
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MenuRegistry {

    private val menusRef = ConcurrentHashMap<String, MenuDefinition>()
    private val menuFolder: File
        get() = File(getDataFolder(), "menu")

    fun initialize(): Int {
        releaseDefaultMenu()
        registerWatcher()
        return reload()
    }

    fun reload(): Int {
        val folder = menuFolder
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val loaded = linkedMapOf<String, MenuDefinition>()
        folder.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }?.sortedBy { it.name }?.forEach { file ->
            runCatching { MenuConfigParser.parse(file) }
                .onSuccess { menu -> loaded[menu.id] = menu }
                .onFailure { throwable -> warning("菜单配置 ${file.name} 加载失败: ${throwable.message}") }
        }
        menusRef.clear()
        menusRef.putAll(loaded)
        return loaded.size
    }

    fun get(id: String): MenuDefinition? {
        return menusRef[id.lowercase()]
    }

    fun contains(id: String): Boolean {
        return get(id) != null
    }

    fun ids(): List<String> {
        return menusRef.keys.sorted()
    }

    fun all(): List<MenuDefinition> {
        return menusRef.values.toList()
    }

    fun size(): Int {
        return menusRef.size
    }

    private fun releaseDefaultMenu() {
        releaseResourceFile("menu/default.yml")
    }

    private fun registerWatcher() {
        val folder = menuFolder
        if (!folder.exists()) {
            folder.mkdirs()
        }
        FileWatcher.INSTANCE.removeListener(folder)
        FileWatcher.INSTANCE.addSimpleListener(folder) {
            reload()
        }
    }
}
