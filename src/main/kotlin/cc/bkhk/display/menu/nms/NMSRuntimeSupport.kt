package cc.bkhk.display.menu.nms

import org.joml.Quaternionf
import org.joml.Vector3f
import taboolib.module.nms.MinecraftVersion

/**
 * 当前服务器 NMS 运行时画像，用于选择发包实现和输出诊断信息。
 */
data class NMSRuntimeProfile(
    val runningVersion: String,
    val versionId: Int,
    val kind: NMSRuntimeKind,
    val supportedByTabooLib: Boolean,
    val skippedByTabooLib: Boolean,
    val unobfuscated: Boolean,
    val universalCraftBukkit: Boolean,
    val supportsDisplayPackets: Boolean,
    val reason: String,
) {

    fun summary(): String {
        return "version=$runningVersion, version_id=$versionId, kind=${kind.name}, taboolib_supported=$supportedByTabooLib, skipped=$skippedByTabooLib, unobfuscated=$unobfuscated, universal_cb=$universalCraftBukkit, display_packets=$supportsDisplayPackets, reason=$reason"
    }
}

enum class NMSRuntimeKind {
    V11904_NATIVE,
    V12000_LEGACY_MAPPED,
    V12005_COMPONENTS,
    V12100_MODERN,
    V260100_UNOBFUSCATED,
    UNSUPPORTED_OR_SKIPPED,
}

object NMSRuntimeSupport {

    val current: NMSRuntimeProfile by lazy { detect() }

    fun detect(): NMSRuntimeProfile {
        val runningVersion = runCatching { MinecraftVersion.runningVersion }.getOrElse { "UNKNOWN" }
        val versionId = runCatching { MinecraftVersion.versionId }.getOrDefault(0)
        val supported = runCatching { MinecraftVersion.isSupported }.getOrDefault(false)
        val skipped = runCatching { MinecraftVersion.isSkipped }.getOrDefault(false)
        val unobfuscated = runCatching { MinecraftVersion.isUnobfuscated }.getOrDefault(false)
        val universalCraftBukkit = runCatching { MinecraftVersion.isUniversalCraftBukkit }.getOrDefault(false)
        val jomlAvailable = isJomlAvailable()
        val kind = when {
            skipped || !supported -> NMSRuntimeKind.UNSUPPORTED_OR_SKIPPED
            versionId == 11904 -> NMSRuntimeKind.V11904_NATIVE
            versionId in 12000..12004 -> NMSRuntimeKind.V12000_LEGACY_MAPPED
            versionId in 12005..12099 -> NMSRuntimeKind.V12005_COMPONENTS
            versionId in 12100..259999 -> NMSRuntimeKind.V12100_MODERN
            versionId >= 260100 || unobfuscated -> NMSRuntimeKind.V260100_UNOBFUSCATED
            else -> NMSRuntimeKind.UNSUPPORTED_OR_SKIPPED
        }
        val supportsDisplayPackets = kind != NMSRuntimeKind.UNSUPPORTED_OR_SKIPPED && jomlAvailable
        val reason = when {
            !jomlAvailable -> "当前服务端运行时缺少 org.joml，Display Transformation 无法构造，Display 发包不会启用。"
            kind == NMSRuntimeKind.V11904_NATIVE -> "已启用 1.19.4 Display 启发式发包实现。"
            kind == NMSRuntimeKind.V12000_LEGACY_MAPPED -> "已启用 1.20-1.20.4 Display 启发式发包实现。"
            kind == NMSRuntimeKind.V12005_COMPONENTS -> "已启用 1.20.5+ Data Components 线 Display 启发式发包实现。"
            kind == NMSRuntimeKind.V12100_MODERN -> "已启用 1.21+ Display 启发式发包实现。"
            kind == NMSRuntimeKind.V260100_UNOBFUSCATED -> "已启用 26.1+ 非混淆 Display 启发式发包实现。"
            else -> "当前版本在 TabooLib 中未支持或被跳过，Display 发包不会启用。"
        }
        return NMSRuntimeProfile(
            runningVersion = runningVersion,
            versionId = versionId,
            kind = kind,
            supportedByTabooLib = supported,
            skippedByTabooLib = skipped,
            unobfuscated = unobfuscated,
            universalCraftBukkit = universalCraftBukkit,
            supportsDisplayPackets = supportsDisplayPackets,
            reason = reason,
        )
    }

    private fun isJomlAvailable(): Boolean {
        return runCatching {
            Vector3f(0.0f, 0.0f, 0.0f)
            Quaternionf()
        }.isSuccess
    }
}

class UnsupportedNMSDisplayPacketException(message: String) : RuntimeException(message)
