import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    id("io.izzel.taboolib") version "2.0.37"
    kotlin("jvm") version "2.3.0"
}

taboolib {
    env {
        // 基础平台与 Bukkit 工具
        install(Basic, Bukkit, BukkitHook, BukkitUtil)

        // Display 实体发包与 NMS 工具
        install(BukkitNMS, BukkitNMSUtil)

        // Minecraft 功能模块
        install(MinecraftChat)      // 富文本、颜色和交互文本
        install(MinecraftEffect)    // 粒子与几何特效
        install(I18n)               // 国际化语言文件

        // 脚本与表达式引擎
        install(Kether)             // 菜单交互动作用脚本
        install(JavaScript)         // JavaScript 脚本环境
        install(Jexl)               // JEXL 表达式环境

        // 功能支持模块
        install(BukkitUI)           // UI 界面工具
        install(Database)           // 数据库
        install(CommandHelper)      // 命令系统
    }
    version {
        taboolib = "6.3.0-5880b4c"
        coroutines = "1.8.1"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 物品源适配由项目内部实现，不额外引入外部物品框架

    compileOnly("com.google.code.gson:gson:2.8.8")
    compileOnly("com.mojang:brigadier:1.0.18")
    // Minecraft 1.19.4+ 服务端运行时已提供 JOML；这里只用于编译 Display Transformation，禁止打包或 relocate，避免与服务端 NMS 类型不一致。
    compileOnly("org.joml:joml:1.10.5")
    // Minecraft 服务端运行时已提供 fastutil；这里只用于编译移除实体包的 IntList 构造回退。
    compileOnly("it.unimi.dsi:fastutil:8.5.12")
    // Paper 等服务端运行时可提供 Adventure MiniMessage；这里只用于编译可选解析入口，不打包到插件。
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.17.0")

    compileOnly("ink.ptms.core:v11904:11904:universal")

    // 已查询到的后续 NMS 坐标：
    // 1.20/1.20.4: v12000 / v12004，1.20.5+: v12005，1.21+: v12100 / v12101 / v12110 / v12111，26.1+: v260100。
    // 当前源码只实现 v11904 Display 发包；添加新坐标前必须先拆分对应 NMSDisplayPacketImpl 版本实现。

    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}