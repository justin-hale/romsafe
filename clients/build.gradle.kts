plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}

// One version per release: the root package.json's, which the server reports too.
// The apps' version names and the shared engine's BuildInfo.VERSION come from it.
val romsafeVersion: String = providers.fileContents(layout.projectDirectory.file("../package.json")).asText.get()
    .let { Regex(""""version"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
    ?: error("No version in ../package.json")
allprojects { extra["romsafeVersion"] = romsafeVersion }
