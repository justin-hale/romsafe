plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val commitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
val romsafeVersion = rootProject.extra["romsafeVersion"] as String

// Release signing, from environment variables (CI) or Gradle properties (~/.gradle/gradle.properties
// on a release machine). Without them the release build comes out unsigned; debug builds always
// use the SDK's debug key. The keystore itself never lives in the repository.
fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(prop)).orNull?.takeIf { it.isNotBlank() }
val releaseKeystore = signingValue("ROMSAFE_KEYSTORE_FILE", "romsafe.keystore.file")

android {
    namespace = "app.romsafe.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.romsafe"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        // A new version code for every commit, so launchers and package
        // managers notice updates (Cocoon, for one, caches icons per version).
        versionCode = commitCount.get()
        versionName = romsafeVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("ROMSAFE_KEYSTORE_PASSWORD", "romsafe.keystore.password")
                keyAlias = signingValue("ROMSAFE_KEY_ALIAS", "romsafe.key.alias")
                keyPassword = signingValue("ROMSAFE_KEY_PASSWORD", "romsafe.key.password")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
