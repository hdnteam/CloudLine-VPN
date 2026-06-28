package com.hdnteam.cloudlinevpn.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Stub TProxyService — hev-socks5-tunnel removed due to JNI compatibility issues.
 * Traffic routing is handled by Xray's native TUN support via startLoop(cfg, tunFd).
 */
class TProxyService(
    private val context: Context,
    private val vpnInterface: ParcelFileDescriptor,
    private val socksPort: Int = 10808,
    private val vpnIpv4: String = "172.19.0.1",
    private val mtu: Int = 8500
) {
    companion object {
        private const val TAG = "TProxyService"
    }

    fun startTun2Socks() {
        // Xray handles TUN directly via startLoop(cfg, tunFd)
        Log.i(TAG, "TUN routing via Xray native (fd=${vpnInterface.fd})")
    }

    fun stopTun2Socks() {
        Log.i(TAG, "TUN stopped")
    }

    fun getStats(): Pair<Long, Long> = 0L to 0L
}
