package cc.bkhk.display.menu.nms

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import taboolib.library.reflex.Reflex.Companion.invokeConstructor
import taboolib.library.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.nmsClass
import taboolib.module.nms.obcClass
import taboolib.module.nms.sendBundlePacketBlocking
import taboolib.module.nms.sendPacketBlocking
import java.util.Collections
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Minecraft 1.19.4+ Display 发包实现。
 *
 * 这里仅构造未加入世界的 NMS Display 实体并向指定玩家发包，不生成 Bukkit 真实实体。
 * 运行时通过启发式候选探测生成调用计划，后续发包复用已缓存的可用路径。
 */
class NMSDisplayPacketImpl : NMSDisplayPacket() {

    private val runtime = NMSHeuristicRuntime(NMSRuntimeSupport.current)
    private val entityCache = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, CachedDisplayEntity>>()

    override fun spawnTextDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: TextDisplayOptions,
    ): DisplayEntityHandle {
        val (handle, packets) = createTextPackets(player, location, transform, options)
        player.sendBundlePacketBlocking(packets)
        return handle
    }

    override fun spawnItemDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: ItemDisplayOptions,
    ): DisplayEntityHandle {
        val (handle, packets) = createItemPackets(player, location, transform, options)
        player.sendBundlePacketBlocking(packets)
        return handle
    }

    override fun spawnBlockDisplay(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: BlockDisplayOptions,
    ): DisplayEntityHandle {
        val (handle, packets) = createBlockPackets(player, location, transform, options)
        player.sendBundlePacketBlocking(packets)
        return handle
    }

    override fun teleportDisplay(player: Player, handle: DisplayEntityHandle, location: Location, transform: DisplayTransform) {
        val packets = buildTeleportPackets(player, handle, location, transform)
        if (packets.isNotEmpty()) {
            player.sendBundlePacketBlocking(packets)
        }
    }

    override fun updateTextDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: TextDisplayOptions) {
        val packets = buildUpdatePackets(player, handle, transform) { entity ->
            applyTextOptions(entity, options)
            true
        }
        if (packets.isNotEmpty()) {
            player.sendBundlePacketBlocking(packets)
        }
    }

    override fun updateItemDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: ItemDisplayOptions) {
        val packets = buildUpdatePackets(player, handle, transform) { entity ->
            applyItemOptions(entity, options)
            true
        }
        if (packets.isNotEmpty()) {
            player.sendBundlePacketBlocking(packets)
        }
    }

    override fun updateBlockDisplay(player: Player, handle: DisplayEntityHandle, transform: DisplayTransform, options: BlockDisplayOptions) {
        val packets = buildUpdatePackets(player, handle, transform) { entity ->
            applyBlockOptions(entity, options)
            true
        }
        if (packets.isNotEmpty()) {
            player.sendBundlePacketBlocking(packets)
        }
    }

    override fun destroyDisplay(player: Player, handle: DisplayEntityHandle) {
        destroyDisplays(player, listOf(handle))
    }

    override fun destroyDisplays(player: Player, handles: Collection<DisplayEntityHandle>) {
        val targets = cachedDestroyTargets(player, handles)
        val ids = targets.map { it.entityId }
        if (ids.isNotEmpty()) {
            player.sendPacketBlocking(runtime.destroyPacket(ids))
            targets.forEach { removeCachedEntity(player, it) }
        }
    }

    override fun destroyAllDisplays(player: Player) {
        val map = entityCache[player.uniqueId] ?: return
        val removed = map.keys.toList()
        if (removed.isNotEmpty()) {
            player.sendPacketBlocking(runtime.destroyPacket(removed))
            entityCache.remove(player.uniqueId, map)
        }
    }

    override fun sendDisplayBatch(player: Player, operations: List<DisplayPacketOperation>): List<DisplayEntityHandle> {
        val packets = ArrayList<Any>()
        val created = ArrayList<DisplayEntityHandle>()
        val destroyed = ArrayList<DisplayEntityHandle>()
        operations.forEach { operation ->
            when (operation) {
                is DisplayPacketOperation.SpawnText -> {
                    val (handle, packetList) = createTextPackets(player, operation.location, operation.transform, operation.options)
                    created += handle
                    packets.addAll(packetList)
                }
                is DisplayPacketOperation.SpawnItem -> {
                    val (handle, packetList) = createItemPackets(player, operation.location, operation.transform, operation.options)
                    created += handle
                    packets.addAll(packetList)
                }
                is DisplayPacketOperation.SpawnBlock -> {
                    val (handle, packetList) = createBlockPackets(player, operation.location, operation.transform, operation.options)
                    created += handle
                    packets.addAll(packetList)
                }
                is DisplayPacketOperation.Teleport -> packets.addAll(buildTeleportPackets(player, operation.handle, operation.location, operation.transform))
                is DisplayPacketOperation.UpdateText -> {
                    packets.addAll(buildUpdatePackets(player, operation.handle, operation.transform) { entity ->
                        applyTextOptions(entity, operation.options)
                        true
                    })
                }
                is DisplayPacketOperation.UpdateItem -> {
                    packets.addAll(buildUpdatePackets(player, operation.handle, operation.transform) { entity ->
                        applyItemOptions(entity, operation.options)
                        true
                    })
                }
                is DisplayPacketOperation.UpdateBlock -> {
                    packets.addAll(buildUpdatePackets(player, operation.handle, operation.transform) { entity ->
                        applyBlockOptions(entity, operation.options)
                        true
                    })
                }
                is DisplayPacketOperation.Destroy -> packets.addAll(buildDestroyPacket(player, listOf(operation.handle), destroyed))
                is DisplayPacketOperation.DestroyMany -> packets.addAll(buildDestroyPacket(player, operation.handles, destroyed))
            }
        }
        if (packets.isNotEmpty()) {
            player.sendBundlePacketBlocking(packets)
            destroyed.forEach { removeCachedEntity(player, it) }
        }
        return created
    }

    private fun createTextPackets(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: TextDisplayOptions,
    ): CreatedPackets {
        val entity = runtime.newTextDisplay(location)
        applyCommonTransform(entity, transform)
        applyTextOptions(entity, options)
        return cacheCreatedEntity(player, DisplayEntityType.TEXT, entity, runtime.textEntityType, location)
    }

    private fun createItemPackets(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: ItemDisplayOptions,
    ): CreatedPackets {
        val entity = runtime.newItemDisplay(location)
        applyCommonTransform(entity, transform)
        applyItemOptions(entity, options)
        return cacheCreatedEntity(player, DisplayEntityType.ITEM, entity, runtime.itemEntityType, location)
    }

    private fun createBlockPackets(
        player: Player,
        location: Location,
        transform: DisplayTransform,
        options: BlockDisplayOptions,
    ): CreatedPackets {
        val entity = runtime.newBlockDisplay(location)
        applyCommonTransform(entity, transform)
        applyBlockOptions(entity, options)
        return cacheCreatedEntity(player, DisplayEntityType.BLOCK, entity, runtime.blockEntityType, location)
    }

    private fun cacheCreatedEntity(
        player: Player,
        type: DisplayEntityType,
        entity: Any,
        entityType: Any,
        location: Location,
    ): CreatedPackets {
        val handle = DisplayEntityHandle(runtime.entityId(entity), UUID.randomUUID(), type)
        runtime.setEntityUuid(entity, handle.uuid)
        entityCache.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[handle.entityId] = CachedDisplayEntity(handle, entity)
        val metadata = runtime.fullMetadata(entity)
        val packets = ArrayList<Any>()
        packets += runtime.spawnPacket(handle, location, entityType)
        if (metadata.isNotEmpty()) {
            packets += runtime.metadataPacket(handle.entityId, metadata)
        }
        return CreatedPackets(handle, packets)
    }

    private fun buildTeleportPackets(
        player: Player,
        handle: DisplayEntityHandle,
        location: Location,
        transform: DisplayTransform,
    ): List<Any> {
        val cached = getCachedEntity(player, handle) ?: return emptyList()
        val entity = cached.entity
        applyCommonTransform(entity, transform)
        val packets = ArrayList<Any>()
        packets += runtime.teleportPacket(handle, entity, location)
        val metadata = runtime.dirtyMetadata(entity)
        if (metadata.isNotEmpty()) {
            packets += runtime.metadataPacket(handle.entityId, metadata)
        }
        return packets
    }

    private fun buildUpdatePackets(
        player: Player,
        handle: DisplayEntityHandle,
        transform: DisplayTransform,
        applySpecificOptions: (Any) -> Boolean,
    ): List<Any> {
        val cached = getCachedEntity(player, handle) ?: return emptyList()
        val entity = cached.entity
        applyCommonTransform(entity, transform)
        if (!applySpecificOptions(entity)) {
            return emptyList()
        }
        val metadata = runtime.dirtyMetadata(entity)
        return if (metadata.isEmpty()) emptyList() else listOf(runtime.metadataPacket(handle.entityId, metadata))
    }

    private fun buildDestroyPacket(player: Player, handles: Collection<DisplayEntityHandle>, destroyed: MutableCollection<DisplayEntityHandle>): List<Any> {
        val targets = cachedDestroyTargets(player, handles)
        val ids = targets.map { it.entityId }
        if (ids.isEmpty()) {
            return emptyList()
        }
        destroyed += targets
        return listOf(runtime.destroyPacket(ids))
    }

    private fun cachedDestroyTargets(player: Player, handles: Collection<DisplayEntityHandle>): List<DisplayEntityHandle> {
        return handles.filter { handle -> getCachedEntity(player, handle) != null }
    }

    private fun getCachedEntity(player: Player, handle: DisplayEntityHandle): CachedDisplayEntity? {
        return entityCache[player.uniqueId]?.get(handle.entityId)?.takeIf { cached ->
            cached.handle.uuid == handle.uuid && cached.handle.type == handle.type
        }
    }

    private fun removeCachedEntity(player: Player, handle: DisplayEntityHandle): CachedDisplayEntity? {
        val map = entityCache[player.uniqueId] ?: return null
        val cached = map[handle.entityId]?.takeIf { cached ->
            cached.handle.uuid == handle.uuid && cached.handle.type == handle.type
        } ?: return null
        map.remove(handle.entityId, cached)
        if (map.isEmpty()) {
            entityCache.remove(player.uniqueId, map)
        }
        return cached
    }

    private fun applyCommonTransform(entity: Any, transform: DisplayTransform) {
        val transformation = runtime.newTransformation(transform)
        runtime.applyCommonTransform(entity, transformation, transform)
    }

    private fun applyTextOptions(entity: Any, options: TextDisplayOptions) {
        runtime.applyTextOptions(
            entity = entity,
            component = runtime.toComponent(options.text, options.textIsJson),
            lineWidth = options.lineWidth,
            backgroundColor = options.backgroundColor,
            textOpacity = options.textOpacity.coerceIn(0, 255).toByte(),
            flags = textFlags(options),
        )
    }

    private fun applyItemOptions(entity: Any, options: ItemDisplayOptions) {
        runtime.applyItemOptions(entity, runtime.toNmsItemStack(options.itemStack), options.transform)
    }

    private fun applyBlockOptions(entity: Any, options: BlockDisplayOptions) {
        runtime.applyBlockOptions(entity, runtime.toNmsBlockData(options.blockData))
    }

    private fun textFlags(options: TextDisplayOptions): Byte {
        var flags = 0
        if (options.shadowed) {
            flags = flags or 0x01
        }
        if (options.seeThrough) {
            flags = flags or 0x02
        }
        if (options.useDefaultBackground) {
            flags = flags or 0x04
        }
        flags = flags or when (options.alignment) {
            DisplayTextAlignment.LEFT -> 0x08
            DisplayTextAlignment.RIGHT -> 0x10
            DisplayTextAlignment.CENTER -> 0x00
        }
        return flags.toByte()
    }

    private class NMSHeuristicRuntime(private val profile: NMSRuntimeProfile) {

        private val invoker = HeuristicInvoker()
        private val plan = DisplayCallPlan()

        private val entityTypeClass: Class<*> by lazy { firstNmsClass("world.entity.EntityTypes", "world.entity.EntityType") }
        private val textDisplayClass: Class<*> by lazy { nmsClass("world.entity.Display\$TextDisplay") }
        private val itemDisplayClass: Class<*> by lazy { nmsClass("world.entity.Display\$ItemDisplay") }
        private val blockDisplayClass: Class<*> by lazy { nmsClass("world.entity.Display\$BlockDisplay") }
        private val billboardClass: Class<*> by lazy { nmsClass("world.entity.Display\$BillboardConstraints") }
        private val itemDisplayContextClass: Class<*> by lazy { nmsClass("world.item.ItemDisplayContext") }
        private val vec3Class: Class<*> by lazy { firstNmsClass("world.phys.Vec3D", "world.phys.Vec3") }
        private val brightnessClass: Class<*> by lazy { nmsClass("util.Brightness") }
        private val transformationClass: Class<*> by lazy { com.mojang.math.Transformation::class.java }
        private val spawnPacketClass: Class<*> by lazy { firstNmsClass("network.protocol.game.PacketPlayOutSpawnEntity", "network.protocol.game.ClientboundAddEntityPacket") }
        private val metadataPacketClass: Class<*> by lazy { firstNmsClass("network.protocol.game.PacketPlayOutEntityMetadata", "network.protocol.game.ClientboundSetEntityDataPacket") }
        private val teleportPacketClass: Class<*> by lazy { firstNmsClass("network.protocol.game.PacketPlayOutEntityTeleport", "network.protocol.game.ClientboundTeleportEntityPacket") }
        private val destroyPacketClass: Class<*> by lazy { firstNmsClass("network.protocol.game.PacketPlayOutEntityDestroy", "network.protocol.game.ClientboundRemoveEntitiesPacket") }
        private val positionMoveRotationClass: Class<*> by lazy { nmsClass("world.entity.PositionMoveRotation") }
        private val craftItemStackClass: Class<*> by lazy { obcClass("inventory.CraftItemStack") }
        private val craftChatMessageClass: Class<*> by lazy { obcClass("util.CraftChatMessage") }

        val textEntityType: Any by lazy { entityType("minecraft:text_display") }
        val itemEntityType: Any by lazy { entityType("minecraft:item_display") }
        val blockEntityType: Any by lazy { entityType("minecraft:block_display") }

        fun newTextDisplay(location: Location): Any {
            return textDisplayClass.asAnyClass().invokeConstructor(textEntityType, nmsWorld(location))
        }

        fun newItemDisplay(location: Location): Any {
            return itemDisplayClass.asAnyClass().invokeConstructor(itemEntityType, nmsWorld(location))
        }

        fun newBlockDisplay(location: Location): Any {
            return blockDisplayClass.asAnyClass().invokeConstructor(blockEntityType, nmsWorld(location))
        }

        fun entityId(entity: Any): Int {
            return invoker.call(entity, plan.entityId)
                ?: error("Cannot read Display entity id for ${entity::class.java.name}.")
        }

        fun setEntityUuid(entity: Any, uuid: UUID) {
            invoker.callVoid(entity, plan.setUuid, uuid)
        }

        fun spawnPacket(handle: DisplayEntityHandle, location: Location, entityType: Any): Any {
            return spawnPacketClass.asAnyClass().invokeConstructor(
                handle.entityId,
                handle.uuid,
                location.x,
                location.y,
                location.z,
                location.pitch,
                location.yaw,
                entityType,
                0,
                newVec3(0.0, 0.0, 0.0),
                location.yaw.toDouble(),
            )
        }

        fun metadataPacket(entityId: Int, metadata: List<Any>): Any {
            return metadataPacketClass.asAnyClass().invokeConstructor(entityId, metadata)
        }

        fun teleportPacket(handle: DisplayEntityHandle, entity: Any, location: Location): Any {
            return when (plan.teleportMode) {
                TeleportMode.MODERN -> modernTeleportPacket(handle, location)
                TeleportMode.LEGACY -> legacyTeleportPacket(entity, location)
                TeleportMode.UNKNOWN -> detectTeleportPacket(handle, entity, location)
            }
        }

        fun destroyPacket(ids: Collection<Int>): Any {
            val idArray = ids.toIntArray()
            return runCatching { destroyPacketClass.asAnyClass().invokeConstructor(idArray as Any) }.getOrElse {
                destroyPacketClass.asAnyClass().invokeConstructor(IntArrayList.wrap(idArray))
            }
        }

        fun applyLocation(entity: Any, location: Location) {
            invoker.callVoid(
                entity,
                plan.location,
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch,
            ) || error("Cannot set Display location for ${entity::class.java.name}.")
        }

        fun newTransformation(transform: DisplayTransform): Any {
            return transformationClass.asAnyClass().invokeConstructor(
                Vector3f(transform.translationX, transform.translationY, transform.translationZ),
                Quaternionf(),
                Vector3f(transform.scaleX, transform.scaleY, transform.scaleZ),
                Quaternionf(),
            )
        }

        fun applyCommonTransform(entity: Any, transformation: Any, transform: DisplayTransform) {
            invoker.callVoid(entity, plan.transformation, transformation)
            invoker.callVoid(entity, plan.interpolationDuration, transform.interpolationDurationTicks)
            invoker.callVoid(entity, plan.interpolationDelay, transform.interpolationDelayTicks)
            invoker.callVoid(entity, plan.billboard, billboard(transform.billboard))
            invoker.callVoid(entity, plan.brightness, transform.brightness?.let { brightness(it) })
            invoker.callVoid(entity, plan.viewRange, transform.viewRange)
            invoker.callVoid(entity, plan.shadowRadius, transform.shadowRadius)
            invoker.callVoid(entity, plan.shadowStrength, transform.shadowStrength)
            invoker.callVoid(entity, plan.width, transform.width)
            invoker.callVoid(entity, plan.height, transform.height)
            invoker.callVoid(entity, plan.glowColor, transform.glowColorOverride)
        }

        fun applyTextOptions(entity: Any, component: Any, lineWidth: Int, backgroundColor: Int, textOpacity: Byte, flags: Byte) {
            invoker.callVoid(entity, plan.text, component)
            invoker.callVoid(entity, plan.lineWidth, lineWidth)
            invoker.callVoid(entity, plan.textOpacity, textOpacity)
            invoker.callVoid(entity, plan.backgroundColor, backgroundColor)
            invoker.callVoid(entity, plan.flags, flags)
        }

        fun applyItemOptions(entity: Any, nmsItemStack: Any, transform: DisplayItemTransform) {
            invoker.callVoid(entity, plan.itemStack, nmsItemStack)
            invoker.callVoid(entity, plan.itemTransform, itemDisplayContext(transform))
        }

        fun applyBlockOptions(entity: Any, blockData: Any) {
            invoker.callVoid(entity, plan.blockState, blockData)
        }

        fun fullMetadata(entity: Any): List<Any> {
            return metadataValues(entityData(entity), plan.fullMetadata)
        }

        fun dirtyMetadata(entity: Any): List<Any> {
            return metadataValues(entityData(entity), plan.dirtyMetadata)
        }

        fun toComponent(text: String, json: Boolean): Any {
            val rawJson = if (json) text else "{\"text\":\"${escapeJson(text)}\"}"
            val parsed = runCatching { parseComponent(rawJson) }.getOrNull()
            if (parsed != null) {
                return parsed
            }
            return parseComponent("{\"text\":\"${escapeJson(text)}\"}")
        }

        fun toNmsItemStack(itemStack: ItemStack): Any {
            return invoker.staticCall(craftItemStackClass, plan.asNmsCopy, itemStack)
                ?: error("Cannot convert Bukkit ItemStack to NMS ItemStack.")
        }

        fun toNmsBlockData(blockData: BlockData): Any {
            val craftBlockData = blockData.takeIf { it::class.java.name.contains("CraftBlockData") }
                ?: Material.STONE.createBlockData()
            return invoker.call(craftBlockData, plan.blockDataState) ?: error("Cannot convert Bukkit BlockData to NMS block state.")
        }

        fun profileSummary(): String {
            return "${profile.summary()}, teleport=${plan.teleportMode.name}, selected=${plan.selectedSummary()}"
        }

        @Suppress("UNCHECKED_CAST")
        private fun Class<*>.asAnyClass(): Class<Any> {
            return this as Class<Any>
        }

        private fun firstNmsClass(vararg names: String): Class<*> {
            names.forEach { name ->
                val nms = runCatching { nmsClass(name) }.getOrNull()
                if (nms != null) {
                    return nms
                }
            }
            error("Cannot resolve NMS class: ${names.joinToString()}.")
        }

        private fun entityType(key: String): Any {
            val keys = listOf(key, key.substringAfter(':'))
            plan.entityType.selectedName?.let { selected ->
                keys.forEach { candidate ->
                    val entityType = unwrapOptional(runCatching { entityTypeClass.invokeMethod<Any>(selected, candidate, isStatic = true) }.getOrNull())
                    if (entityType != null) {
                        return entityType
                    }
                }
            }
            plan.entityType.names.forEach { method ->
                keys.forEach { candidate ->
                    val entityType = unwrapOptional(runCatching { entityTypeClass.invokeMethod<Any>(method, candidate, isStatic = true) }.getOrNull())
                    if (entityType != null) {
                        plan.entityType.selectedName = method
                        return entityType
                    }
                }
            }
            error("Cannot resolve Display entity type: $key.")
        }

        private fun nmsWorld(location: Location): Any {
            val world = location.world ?: error("Cannot create Display entity without Bukkit world.")
            return invoker.call(world, plan.worldHandle) ?: error("Cannot find CraftWorld.getHandle.")
        }

        private fun entityData(entity: Any): Any {
            return invoker.call(entity, plan.entityData)
                ?: error("Cannot find entity data accessor for ${entity::class.java.name}.")
        }

        private fun metadataValues(data: Any, metadataPlan: MethodPlan): List<Any> {
            val result = invoker.call<Collection<*>>(data, metadataPlan)
            return result?.filterNotNull().orEmpty()
        }

        private fun parseComponent(json: String): Any {
            return invoker.staticCall(craftChatMessageClass, plan.fromJson, json)
                ?: error("Cannot parse chat component json.")
        }

        private fun newVec3(x: Double, y: Double, z: Double): Any {
            return vec3Class.asAnyClass().invokeConstructor(x, y, z)
        }

        private fun billboard(billboard: DisplayBillboard): Any {
            return enumConstant(billboardClass, billboard.metadataId.toInt().coerceIn(0, 3))
        }

        private fun brightness(brightness: DisplayBrightness): Any {
            return invoker.staticCall(brightnessClass, plan.brightnessUnpack, brightness.packed)
                ?: brightnessClass.asAnyClass().invokeConstructor(brightness.blockLight.coerceIn(0, 15), brightness.skyLight.coerceIn(0, 15))
        }

        private fun itemDisplayContext(transform: DisplayItemTransform): Any {
            return enumConstant(itemDisplayContextClass, transform.ordinal)
        }

        private fun enumConstant(type: Class<*>, ordinal: Int): Any {
            val values = invoker.staticCall<Any>(type, plan.enumValues)
            val arrayValues = values as? Array<*>
            if (arrayValues != null) {
                return arrayValues[ordinal.coerceIn(arrayValues.indices)] ?: error("Enum value is null: ${type.name}[$ordinal].")
            }
            val listValues = values as? List<*>
            if (!listValues.isNullOrEmpty()) {
                return listValues[ordinal.coerceIn(listValues.indices)] ?: error("Enum value is null: ${type.name}[$ordinal].")
            }
            error("Cannot read enum values for ${type.name}.")
        }

        private fun detectTeleportPacket(handle: DisplayEntityHandle, entity: Any, location: Location): Any {
            val modern = runCatching { modernTeleportPacket(handle, location) }.getOrNull()
            if (modern != null) {
                plan.teleportMode = TeleportMode.MODERN
                return modern
            }
            val legacy = runCatching { legacyTeleportPacket(entity, location) }.getOrNull()
            if (legacy != null) {
                plan.teleportMode = TeleportMode.LEGACY
                return legacy
            }
            error("Cannot create teleport packet for runtime: ${profile.summary()}.")
        }

        private fun modernTeleportPacket(handle: DisplayEntityHandle, location: Location): Any {
            val change = positionMoveRotationClass.asAnyClass().invokeConstructor(
                newVec3(location.x, location.y, location.z),
                newVec3(0.0, 0.0, 0.0),
                location.yaw,
                location.pitch,
            )
            return teleportPacketClass.asAnyClass().invokeConstructor(handle.entityId, change, Collections.emptySet<Any>(), false)
        }

        private fun legacyTeleportPacket(entity: Any, location: Location): Any {
            applyLocation(entity, location)
            return teleportPacketClass.asAnyClass().invokeConstructor(entity)
        }

        private fun unwrapOptional(value: Any?): Any? {
            return when (value) {
                is Optional<*> -> value.orElse(null)
                else -> value
            }
        }
    }

    private class HeuristicInvoker {

        fun <T> call(target: Any, plan: MethodPlan, vararg args: Any?): T? {
            plan.selectedName?.let { selected ->
                val selectedResult = runCatching { target.invokeMethod<T>(selected, *args) }
                if (selectedResult.isSuccess) {
                    return selectedResult.getOrNull()
                }
                plan.selectedName = null
            }
            plan.names.forEach { name ->
                val result = runCatching { target.invokeMethod<T>(name, *args) }
                if (result.isSuccess) {
                    plan.selectedName = name
                    return result.getOrNull()
                }
            }
            return null
        }

        fun callVoid(target: Any, plan: MethodPlan, vararg args: Any?): Boolean {
            plan.selectedName?.let { selected ->
                val selectedResult = runCatching { target.invokeMethod<Any?>(selected, *args) }
                if (selectedResult.isSuccess) {
                    return true
                }
                plan.selectedName = null
            }
            plan.names.forEach { name ->
                val result = runCatching { target.invokeMethod<Any?>(name, *args) }
                if (result.isSuccess) {
                    plan.selectedName = name
                    return true
                }
            }
            return false
        }

        fun <T> staticCall(target: Any, plan: MethodPlan, vararg args: Any?): T? {
            plan.selectedName?.let { selected ->
                val selectedResult = runCatching { target.invokeMethod<T>(selected, *args, isStatic = true) }
                if (selectedResult.isSuccess) {
                    return selectedResult.getOrNull()
                }
                plan.selectedName = null
            }
            plan.names.forEach { name ->
                val result = runCatching { target.invokeMethod<T>(name, *args, isStatic = true) }
                if (result.isSuccess) {
                    plan.selectedName = name
                    return result.getOrNull()
                }
            }
            return null
        }
    }

    private data class MethodPlan(
        val id: String,
        val names: List<String>,
    ) {

        @Volatile
        var selectedName: String? = null
    }

    private class DisplayCallPlan {

        val entityId = MethodPlan("entity_id", listOf("getId", "af", "aj", "al", "ar"))
        val setUuid = MethodPlan("set_uuid", listOf("setUUID", "a_"))
        val worldHandle = MethodPlan("world_handle", listOf("getHandle"))
        val entityData = MethodPlan("entity_data", listOf("getEntityData", "aj", "an", "ap", "au"))
        val fullMetadata = MethodPlan("full_metadata", listOf("getNonDefaultValues", "c", "packDirty", "b"))
        val dirtyMetadata = MethodPlan("dirty_metadata", listOf("packDirty", "b", "getNonDefaultValues", "c"))

        val location = MethodPlan("location", listOf("absMoveTo", "moveTo", "a", "b"))
        val transformation = MethodPlan("transformation", listOf("setTransformation", "a"))
        val interpolationDuration = MethodPlan("interpolation_duration", listOf("setTransformationInterpolationDuration", "b"))
        val interpolationDelay = MethodPlan("interpolation_delay", listOf("setTransformationInterpolationDelay", "c"))
        val billboard = MethodPlan("billboard", listOf("setBillboardConstraints", "a"))
        val brightness = MethodPlan("brightness", listOf("setBrightnessOverride", "a"))
        val viewRange = MethodPlan("view_range", listOf("setViewRange", "g", "b"))
        val shadowRadius = MethodPlan("shadow_radius", listOf("setShadowRadius", "h", "c"))
        val shadowStrength = MethodPlan("shadow_strength", listOf("setShadowStrength", "w", "u", "t", "x"))
        val width = MethodPlan("width", listOf("setWidth", "x", "v", "u", "y"))
        val height = MethodPlan("height", listOf("setHeight", "y", "w", "v", "z"))
        val glowColor = MethodPlan("glow_color", listOf("setGlowColorOverride", "d", "m", "n", "l"))

        val text = MethodPlan("text", listOf("setText", "a", "c"))
        val lineWidth = MethodPlan("line_width", listOf("setLineWidth", "b"))
        val textOpacity = MethodPlan("text_opacity", listOf("setTextOpacity", "c"))
        val backgroundColor = MethodPlan("background_color", listOf("setBackgroundColor", "c"))
        val flags = MethodPlan("flags", listOf("setFlags", "d"))

        val itemStack = MethodPlan("item_stack", listOf("setItemStack", "a"))
        val itemTransform = MethodPlan("item_transform", listOf("setItemTransform", "a"))
        val blockState = MethodPlan("block_state", listOf("setBlockState", "c", "b"))

        val entityType = MethodPlan("entity_type", listOf("byString", "a"))
        val enumValues = MethodPlan("enum_values", listOf("values"))
        val brightnessUnpack = MethodPlan("brightness_unpack", listOf("unpack", "a"))
        val fromJson = MethodPlan("chat_from_json", listOf("fromJSON"))
        val asNmsCopy = MethodPlan("as_nms_copy", listOf("asNMSCopy"))
        val blockDataState = MethodPlan("block_data_state", listOf("getState"))

        @Volatile
        var teleportMode: TeleportMode = TeleportMode.UNKNOWN

        fun selectedSummary(): String {
            return allPlans().mapNotNull { plan ->
                plan.selectedName?.let { selected -> "${plan.id}=$selected" }
            }.joinToString(",")
        }

        private fun allPlans(): List<MethodPlan> {
            return listOf(
                entityId,
                setUuid,
                worldHandle,
                entityData,
                fullMetadata,
                dirtyMetadata,
                location,
                transformation,
                interpolationDuration,
                interpolationDelay,
                billboard,
                brightness,
                viewRange,
                shadowRadius,
                shadowStrength,
                width,
                height,
                glowColor,
                text,
                lineWidth,
                textOpacity,
                backgroundColor,
                flags,
                itemStack,
                itemTransform,
                blockState,
                entityType,
                enumValues,
                brightnessUnpack,
                fromJson,
                asNmsCopy,
                blockDataState,
            )
        }
    }

    private enum class TeleportMode {
        UNKNOWN,
        MODERN,
        LEGACY,
    }

    private data class CachedDisplayEntity(
        val handle: DisplayEntityHandle,
        val entity: Any,
    )

    private data class CreatedPackets(
        val handle: DisplayEntityHandle,
        val packets: List<Any>,
    )

    private companion object {

        private fun escapeJson(text: String): String {
            val builder = StringBuilder(text.length + 16)
            text.forEach { char ->
                when (char) {
                    '\\' -> builder.append("\\\\")
                    '"' -> builder.append("\\\"")
                    '\b' -> builder.append("\\b")
                    '\u000C' -> builder.append("\\f")
                    '\n' -> builder.append("\\n")
                    '\r' -> builder.append("\\r")
                    '\t' -> builder.append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            builder.append("\\u")
                            builder.append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            builder.append(char)
                        }
                    }
                }
            }
            return builder.toString()
        }
    }
}
