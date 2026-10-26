plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// BuildInfo.VERSION: the release version, which the apps send to the server and check it against.
val generateBuildInfo by tasks.registering {
    val version = rootProject.extra["romsafeVersion"] as String
    val out = layout.buildDirectory.dir("generated/buildInfo/kotlin")
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("app/romsafe/shared/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package app.romsafe.shared
            |
            |// Generated from the root package.json by :shared:generateBuildInfo.
            |object BuildInfo {
            |    const val VERSION: String = "$version"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    android {
        namespace = "app.romsafe.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
    }
    // The Windows app (Compose Desktop) will build on this target.
    jvm("desktop")

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        named("desktopMain").dependencies {
            implementation(libs.ktor.client.java)
        }
    }
}
