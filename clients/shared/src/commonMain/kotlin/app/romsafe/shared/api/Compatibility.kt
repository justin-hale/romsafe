package app.romsafe.shared.api

import app.romsafe.shared.BuildInfo
import kotlinx.serialization.Serializable

/** What the server says about itself, in `/api/health` and `/api/device/config`. */
@Serializable
data class ServerVersion(
    val version: String,
    /** The device API generation it speaks. */
    val protocol: Int,
    /** The oldest app release it still works with. */
    val minClientVersion: String,
)

@Serializable
internal data class Health(
    val name: String? = null,
    val version: String? = null,
    val protocol: Int? = null,
    val minClientVersion: String? = null,
)

/**
 * The app and the server can't work together; [update] says which one is behind.
 * A [RomSafeApiException] (status 426), so code that shows API errors shows this message too.
 */
class IncompatibleVersionException(val update: Side, message: String) : RomSafeApiException(426, message) {
    enum class Side { APP, SERVER }

    val problem: VersionProblem get() = VersionProblem(update, message ?: "")
}

/** A version mismatch as the apps keep it in their UI state. */
data class VersionProblem(val update: IncompatibleVersionException.Side, val message: String)

/** The version handshake in docs/sync-protocol.md ("Versions"). */
object Compatibility {
    /** The device API generation this build needs. Servers older than this are turned down. */
    const val PROTOCOL = 1

    /** The header the app sends its version in; the server answers 426 when it's too old. */
    const val HEADER = "X-RomSafe-Client-Version"

    /** Why this app can't use [server], or null when it can. A null [server] is one from before the handshake. */
    fun problem(server: ServerVersion?, appVersion: String = BuildInfo.VERSION): IncompatibleVersionException? {
        if (server == null || server.protocol < PROTOCOL) {
            val which = server?.version?.let { "This RomSafe server ($it)" } ?: "This RomSafe server"
            return IncompatibleVersionException(
                IncompatibleVersionException.Side.SERVER,
                "$which is too old for this app ($appVersion). Update the server to $appVersion or newer.",
            )
        }
        val cmp = compareVersions(appVersion, server.minClientVersion)
        if (cmp != null && cmp < 0) {
            return IncompatibleVersionException(
                IncompatibleVersionException.Side.APP,
                "This app ($appVersion) is too old for this RomSafe server (${server.version}). " +
                    "Update the app to ${server.minClientVersion} or newer.",
            )
        }
        return null
    }

    internal fun fromHealth(health: Health): ServerVersion? {
        val version = health.version ?: return null
        val protocol = health.protocol ?: return null
        return ServerVersion(version, protocol, health.minClientVersion ?: "0.0.0")
    }

    /**
     * Compares `major.minor.patch` versions, ignoring any `-prerelease` or `+build`
     * suffix, like the server does. Null when either side doesn't parse.
     */
    fun compareVersions(a: String, b: String): Int? {
        fun parse(v: String): List<Int>? {
            val m = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""").find(v.trim()) ?: return null
            return listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt())
        }
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        for (i in 0..2) if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        return 0
    }
}
