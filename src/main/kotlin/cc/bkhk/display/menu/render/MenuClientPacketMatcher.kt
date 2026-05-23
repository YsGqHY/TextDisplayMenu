package cc.bkhk.display.menu.render

import taboolib.module.nms.Packet

object MenuClientPacketMatcher {

    private val lookPacketNames = setOf(
        "PacketPlayInLook",
        "PacketPlayInPositionLook",
        "ServerboundMovePlayerPacket.Rot",
        "ServerboundMovePlayerPacket.PosRot",
    )

    fun isLookPacket(packet: Packet): Boolean {
        return packet.names().any { name ->
            val normalized = name.substringAfterLast('/').substringAfterLast('.').normalizeNestedClass()
            normalized in lookPacketNames || lookPacketNames.any { normalized.endsWith(it) }
        }
    }

    private fun Packet.names(): List<String> {
        return listOfNotNull(name, nameInSpigot, nameInMojang, fullyName)
            .map { it.replace('/', '.') }
            .filter { it.isNotBlank() }
    }

    private fun String.normalizeNestedClass(): String {
        return replace('$', '.')
    }
}
