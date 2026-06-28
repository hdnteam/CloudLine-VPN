package com.hdnteam.cloudlinevpn.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Latency tester — measures TCP handshake time to the actual server address:port.
 *
 * This is the most accurate pre-connection test because:
 * - It tests the actual server reachability (not a 3rd party like Cloudflare)
 * - If TCP connect fails/timeouts → server is dead → returns -1 (shown as ✕)
 * - If TCP connect succeeds → server port is open → returns latency in ms
 *
 * This matches how v2rayNG "Real delay" works before connection.
 */
object LatencyTester {
    private const val CONNECT_TIMEOUT_MS = 4000

    /** Not used for test logic anymore, kept for settings compatibility */
    const val DEFAULT_TEST_URL = "direct"

    @Volatile
    var testTarget: String = DEFAULT_TEST_URL

    /**
     * Test latency by TCP connecting directly to the server's address:port.
     * This checks if the VPN server itself is reachable and responsive.
     *
     * @param address Server address (IP or domain)
     * @param port Server port
     * @return Latency in ms, or -1 if unreachable/timeout
     */
    suspend fun testServer(address: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS.toLong() + 1000) {
                testTcpConnect(address, port)
            } ?: -1L
        }
    }

    /**
     * TCP handshake test to server.
     * Measures the time to establish a TCP connection.
     * If the server port is closed or filtered → timeout → returns -1.
     */
    private fun testTcpConnect(address: String, port: Int): Long {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }
}
