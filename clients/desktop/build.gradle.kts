import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

val commitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
val romsafeVersion = rootProject.extra["romsafeVersion"] as String

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.cmp.material.icons.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.java)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    // DPAPI, to keep the device token encrypted at rest on Windows.
    implementation(libs.jna.platform)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "app.romsafe.desktop.MainKt"
        nativeDistributions {
            // AppImage here is jpackage's app folder; packaging/linux/build-appimage.sh wraps it into
            // the single .AppImage file (a .deb wouldn't install on SteamOS's read-only system).
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.AppImage)
            packageName = "RomSafe"
            description = "Save sync and ROM library client for a RomSafe server"
            vendor = "RomSafe"
            // MSI versions are MAJOR.MINOR.BUILD with BUILD at most 65535, and Windows Installer
            // ignores anything after that. The release's major.minor plus the commit count keeps
            // every build an upgrade of the one before. (The app itself shows the release version.)
            packageVersion = romsafeVersion.split('.').take(2).joinToString(".") + ".${commitCount.get()}"
            modules("java.instrument", "java.management", "java.net.http", "jdk.unsupported")
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                // Never change this: it is how Windows Installer recognises an upgrade.
                upgradeUuid = "6f1c2b1e-5a8f-4b52-9d1e-3f0c2a7d9b41"
                iconFile.set(project.file("icons/romsafe.ico"))
            }
            macOS {
                bundleID = "app.romsafe.desktop"
                appCategory = "public.app-category.utilities"
                // macOS rejects a bundle version starting with 0, so 0.x releases are 1.x here
                // (only Finder's Get Info shows it). Like the MSI, the last number is the commit count.
                val (major, minor) = romsafeVersion.split('.').map { it.takeWhile(Char::isDigit).toInt() }
                packageVersion = "${maxOf(major, 1)}.$minor.${commitCount.get()}"
                iconFile.set(project.file("icons/romsafe.icns"))
                infoPlist {
                    // Shown in macOS's Local Network prompt; the server is nearly always on the LAN.
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>RomSafe connects to your RomSafe server on your network to sync saves and download games.</string>
                    """.trimIndent()
                }
            }
            linux {
                packageVersion = romsafeVersion
                iconFile.set(project.file("icons/romsafe.png"))
            }
        }
    }
}

// jpackage copies one prebuilt launcher into every app, so every jpackage app's main executable
// has the same Mach-O UUID. macOS's Local Network privacy identifies apps partly by that UUID,
// and with a shared one (and only an ad hoc signature) it neither prompts nor allows: the app
// silently can't reach a server on the LAN. Give the launcher a UUID of its own (one per build,
// as Apple asks), then sign the bundle again, ad hoc, since the patch breaks the old signature.
val launcherUuid = UUID.nameUUIDFromBytes("app.romsafe.desktop $romsafeVersion ${commitCount.get()}".toByteArray())
val macApp = layout.buildDirectory.dir("compose/binaries/main/app/RomSafe.app")
tasks.matching { it.name == "createDistributable" }.configureEach {
    val onMac = System.getProperty("os.name").lowercase().contains("mac")
    // Locals, so the action doesn't capture the script (the configuration cache can't store it).
    val uuid = launcherUuid
    val appDir = macApp
    inputs.property("launcherUuid", uuid.toString())
    doLast {
        if (!onMac) return@doLast
        val app = appDir.get().asFile
        val launcher = app.resolve("Contents/MacOS/RomSafe")
        val bytes = launcher.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buf.getInt(0) == 0xfeedfacf.toInt()) { "$launcher isn't a thin 64-bit Mach-O" }
        val commands = buf.getInt(16)
        var offset = 32 // after mach_header_64
        var patched = false
        repeat(commands) {
            val cmd = buf.getInt(offset)
            if (cmd == 0x1b) { // LC_UUID: cmd, cmdsize, then the 16 UUID bytes
                ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
                    .copyInto(bytes, offset + 8)
                patched = true
            }
            offset += buf.getInt(offset + 4)
        }
        check(patched) { "$launcher has no LC_UUID" }
        launcher.writeBytes(bytes)
        val sign = ProcessBuilder(
            "codesign", "--force", "--sign", "-", "--preserve-metadata=identifier,entitlements,flags", app.path,
        ).inheritIO().start()
        check(sign.waitFor() == 0) { "codesign failed for $app" }
    }
}
