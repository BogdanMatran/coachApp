import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Supabase connection is read from local.properties (SUPABASE_URL / SUPABASE_ANON_KEY)
// and falls back to the defaults of a local `supabase start` stack.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val supabaseUrl = localProps.getProperty("SUPABASE_URL") ?: "http://127.0.0.1:54321"
val supabaseAnonKey = localProps.getProperty("SUPABASE_ANON_KEY")
    ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

// Host a physical iPhone uses instead of 127.0.0.1 to reach the Mac (Bonjour name, e.g. "my-mac.local").
val devHost = localProps.getProperty("DEV_HOST")
    ?: runCatching {
        providers.exec { commandLine("scutil", "--get", "LocalHostName") }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { "$it.local" }
    ?: ""

val generateAppConfig = tasks.register("generateAppConfig") {
    val outDir = layout.buildDirectory.dir("generated/appconfig/kotlin")
    val url = supabaseUrl
    val key = supabaseAnonKey
    val host = devHost
    inputs.property("url", url)
    inputs.property("key", key)
    inputs.property("devHost", host)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("ch/coachapp/core/config/AppConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package ch.coachapp.core.config
            |
            |object AppConfig {
            |    const val SUPABASE_URL: String = "$url"
            |    const val SUPABASE_ANON_KEY: String = "$key"
            |    const val DEV_HOST: String = "$host"
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    android {
        namespace = "ch.coachapp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
        withHostTest { }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }

    // iOS and wasmJs both render through Skia, and Coil ships no animated decoder for either, so
    // they share one Skiko-based GIF player (core/ui/AnimatedImage.skiko.kt).
    applyDefaultHierarchyTemplate()

    sourceSets {
        val skikoMain by creating { dependsOn(commonMain.get()) }
        iosMain.get().dependsOn(skikoMain)
        wasmJsMain.get().dependsOn(skikoMain)

        commonMain {
            kotlin.srcDir(generateAppConfig)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.androidx.navigationevent.compose)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.storage)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.filekit.dialogs.compose)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.coil.gif)
            // Video playback; iOS uses AVPlayer directly instead (see VideoPlayer.ios.kt).
            implementation(libs.composemediaplayer)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.composemediaplayer)
        }
    }
}
