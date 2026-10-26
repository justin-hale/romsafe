package app.romsafe.shared.api

import app.romsafe.shared.BuildInfo
import app.romsafe.shared.api.IncompatibleVersionException.Side
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun server(version: String = "0.1.0", protocol: Int = Compatibility.PROTOCOL, minClient: String = "0.1.0") =
        ServerVersion(version, protocol, minClient)

    @Test
    fun matchingVersionsWork() {
        assertNull(Compatibility.problem(server(), appVersion = "0.1.0"))
        assertNull(Compatibility.problem(server(version = "0.4.0"), appVersion = "0.2.0"))
        // Dev builds and unparseable versions aren't turned away.
        assertNull(Compatibility.problem(server(minClient = "0.1.0"), appVersion = "0.1.0-dev.3"))
        assertNull(Compatibility.problem(server(minClient = "0.1.0"), appVersion = "local"))
    }

    @Test
    fun anOldAppIsToldToUpdateItself() {
        val p = Compatibility.problem(server(version = "0.3.0", minClient = "0.2.0"), appVersion = "0.1.4")!!
        assertEquals(Side.APP, p.update)
        assertEquals(
            "This app (0.1.4) is too old for this RomSafe server (0.3.0). Update the app to 0.2.0 or newer.",
            p.message,
        )
    }

    @Test
    fun anOldServerIsToldToUpdate() {
        val older = Compatibility.problem(server(version = "0.0.5", protocol = Compatibility.PROTOCOL - 1), appVersion = "0.2.0")!!
        assertEquals(Side.SERVER, older.update)
        assertEquals("This RomSafe server (0.0.5) is too old for this app (0.2.0). Update the server to 0.2.0 or newer.", older.message)
        // A server from before the handshake doesn't report a version at all.
        assertEquals(Side.SERVER, Compatibility.problem(null)!!.update)
    }

    @Test
    fun comparesLikeTheServer() {
        assertEquals(0, Compatibility.compareVersions("0.1.0", "v0.1"))
        assertTrue(Compatibility.compareVersions("0.10.0", "0.9.9")!! > 0)
        assertTrue(Compatibility.compareVersions("1.0.0-rc.1", "1.0.1")!! < 0)
        assertNull(Compatibility.compareVersions("x", "0.1.0"))
    }

    @Test
    fun sendsItsVersionAndRejectsAServerWithoutOne() = runTest {
        val engine = MockEngine { req ->
            assertEquals(BuildInfo.VERSION, req.headers[Compatibility.HEADER])
            respond(
                """{"device":{"id":3,"name":"Thor","kind":"android"},"platforms":[],"profiles":[],"mappings":[]}""",
                headers = jsonHeaders,
            )
        }
        val e = assertFailsWith<IncompatibleVersionException> { RomSafeApi("http://nas", "t", engine).config() }
        assertEquals(Side.SERVER, e.update)
    }

    @Test
    fun aServerThatTurnsTheAppAwayMeansUpdateTheApp() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":"This app (0.0.9) is too old for this RomSafe server (0.3.0). Update the app to 0.2.0 or newer."}""",
                HttpStatusCode.UpgradeRequired,
                jsonHeaders,
            )
        }
        val e = assertFailsWith<IncompatibleVersionException> { RomSafeApi("http://nas", "t", engine).config() }
        assertEquals(Side.APP, e.update)
        assertEquals(426, e.status)
        assertTrue(e.message!!.startsWith("This app (0.0.9)"))
    }

    @Test
    fun pairingChecksTheServerFirst() = runTest {
        var paired = false
        val engine = MockEngine { req ->
            when (req.url.encodedPath) {
                // An old server: its health has no protocol.
                "/api/health" -> respond("""{"ok":true,"name":"romsafe","version":"0.1.0"}""", headers = jsonHeaders)
                else -> {
                    paired = true
                    respond("""{"deviceId":1,"token":"t"}""", headers = jsonHeaders)
                }
            }
        }
        val e = assertFailsWith<IncompatibleVersionException> { RomSafeApi("http://nas", null, engine).pair("CODE", "Thor", "android") }
        assertEquals(Side.SERVER, e.update)
        assertEquals(false, paired, "the pairing code must not be used up")
    }

    @Test
    fun pairingSomethingElseSaysSo() = runTest {
        val engine = MockEngine { respond("""{"status":"ok"}""", headers = jsonHeaders) }
        val e = assertFailsWith<RomSafeApiException> { RomSafeApi("http://nas", null, engine).pair("CODE", "Thor", "android") }
        assertEquals("That address doesn't look like a RomSafe server.", e.message)
    }
}
