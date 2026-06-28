package com.hdnteam.cloudlinevpn.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import androidx.core.app.NotificationCompat
import com.hdnteam.cloudlinevpn.R
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.data.parser.XrayConfigBuilder
import com.hdnteam.cloudlinevpn.ui.MainActivity
import com.hdnteam.cloudlinevpn.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class CloudLineVpnService : VpnService() {

    companion object {
        const val ACTION_START    = "com.hdnteam.cloudlinevpn.START"
        const val ACTION_STOP     = "com.hdnteam.cloudlinevpn.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID      = "cloudline_vpn"
        const val SOCKS_PORT      = 10808
        const val HTTP_PORT       = 8080
        private const val TAG     = "CloudLineVPN"

        // Process-level state — shared across activity restarts
        private val _state   = MutableStateFlow(VpnConnectionState.DISCONNECTED)
        val connectionState: StateFlow<VpnConnectionState> = _state

        private val _traffic = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _traffic

        private val _server  = MutableStateFlow<ServerConfig?>(null)
        val connectedServer: StateFlow<ServerConfig?> = _server

        private val _proxyShareTraffic = MutableStateFlow(0L to 0L)
        val proxyShareTraffic: StateFlow<Pair<Long, Long>> = _proxyShareTraffic

        fun resetState() {
            _state.value   = VpnConnectionState.DISCONNECTED
            _server.value  = null
            _traffic.value = TrafficStats()
        }

        /** Static hot-switch — triggers the running service to swap Xray core */
        private var runningInstance: CloudLineVpnService? = null

        fun hotSwitch(ctx: android.content.Context, server: ServerConfig) {
            val instance = runningInstance
            if (instance != null && _state.value == VpnConnectionState.CONNECTED) {
                VpnStateManager.pendingServer = server
                instance.hotSwitchServer(server)
            } else {
                // Fallback: full start
                VpnStateManager.pendingServer = server
                ctx.startForegroundService(
                    Intent(ctx, CloudLineVpnService::class.java).apply { action = ACTION_START }
                )
            }
        }
    }

    private val binder = LocalBinder()
    private var xrayCtrl  : CoreController? = null
    private var vpnFd     : ParcelFileDescriptor? = null
    private var trafficJob: Job? = null
    private var scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var xrayReady = false

    inner class LocalBinder : Binder() {
        fun getService() = this@CloudLineVpnService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        createNotificationChannel()

        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        startForeground(NOTIFICATION_ID, buildNotification("CloudLine VPN", "آماده اتصال"))

        // Register running instance for hot-switch
        runningInstance = this

        // Init Xray env in background — required before startLoop
        scope.launch { initXray() }

        AppLogger.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val server = VpnStateManager.pendingServer
                if (server != null) {
                    scope.launch { startVpn(server) }
                } else {
                    AppLogger.e(TAG, "No pending server — stopping service")
                    safeCleanup()
                    stopSelf()
                }
            }
            ACTION_STOP -> scope.launch { stopVpn() }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        AppLogger.w(TAG, "VPN permission revoked by system")
        scope.launch { stopVpn() }
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "Service onDestroy")
        runningInstance = null
        safeCleanup()
        scope.cancel()
        super.onDestroy()
    }

    // ── Xray init ─────────────────────────────────────────────────────────────

    private suspend fun initXray() {
        try {
            copyGeoFiles()

            // go.Seq requires application context — run on main thread
            withContext(Dispatchers.Main) {
                try { go.Seq.setContext(applicationContext) }
                catch (e: Throwable) { AppLogger.w(TAG, "go.Seq.setContext: ${e.message}") }
            }

            // Empty key = skip xudp BaseKey (avoids the 32-byte crash)
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
            xrayReady = true
            AppLogger.i(TAG, "Xray ready: ${Libv2ray.checkVersionX()}")
        } catch (e: Throwable) {
            AppLogger.e(TAG, "initXray FAILED: ${e.message}", e)
            xrayReady = false
        }
    }

    private fun copyGeoFiles() {
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val dest = File(filesDir, name)
            if (dest.exists() && dest.length() > 10_000) {
                AppLogger.d(TAG, "geo $name ok (${dest.length()} bytes)")
                return@forEach
            }
            try {
                assets.open(name).use { src -> dest.outputStream().use { src.copyTo(it) } }
                AppLogger.i(TAG, "Copied $name (${dest.length()} bytes)")
            } catch (e: Throwable) {
                AppLogger.w(TAG, "Cannot copy $name from assets: ${e.message}")
            }
        }
    }

    // ── Start VPN ─────────────────────────────────────────────────────────────

    private suspend fun startVpn(server: ServerConfig) {
        try {
            _state.value  = VpnConnectionState.CONNECTING
            _server.value = server
            AppLogger.i(TAG, "=== startVpn: [${server.name}] ${server.protocol} ${server.address}:${server.port} ===")

            // Wait for Xray init (max 5s)
            if (!xrayReady) {
                AppLogger.i(TAG, "Waiting for Xray init…")
                repeat(25) {
                    if (!xrayReady) delay(200)
                }
                if (!xrayReady) {
                    // One more direct attempt
                    initXray()
                }
                if (!xrayReady) {
                    AppLogger.e(TAG, "Xray still not ready — aborting")
                    _state.value  = VpnConnectionState.ERROR
                    _server.value = null
                    return
                }
            }

            // Tear down any previous Xray instance
            stopXraySafe()
            delay(200)

            // Build TUN — must run on main thread
            AppLogger.i(TAG, "Building TUN…")
            val tun = withContext(Dispatchers.Main) { buildTun(server) }
            if (tun == null) {
                AppLogger.e(TAG, "TUN establish() returned null — no VPN permission?")
                _state.value  = VpnConnectionState.ERROR
                _server.value = null
                return
            }
            vpnFd = tun
            AppLogger.i(TAG, "TUN fd=${tun.fd}")

            // Build Xray JSON config
            val proxyShare = VpnStateManager.proxyShareEnabled
            val cfg = XrayConfigBuilder.build(server, SOCKS_PORT, HTTP_PORT, proxyShare)
            AppLogger.d(TAG, "Config built (${cfg.length} chars)")

            // Start Xray — pass tunFd so Xray drives the TUN directly
            AppLogger.i(TAG, "Starting Xray core (tunFd=${tun.fd})…")
            val ctrl = startXrayCore(cfg, tun.fd)
            if (ctrl == null) {
                AppLogger.e(TAG, "startXrayCore returned null")
                tun.close(); vpnFd = null
                _state.value  = VpnConnectionState.ERROR
                _server.value = null
                return
            }
            xrayCtrl = ctrl

            // Give Xray time to stabilise — minimal wait
            delay(300)

            if (!ctrl.isRunning) {
                AppLogger.e(TAG, "Xray isRunning=false after startup — aborting")
                safeCleanup()
                _state.value = VpnConnectionState.ERROR
                return
            }

            _state.value = VpnConnectionState.CONNECTED
            updateNotification(server)
            startTrafficMonitor(ctrl)
            AppLogger.i(TAG, "=== VPN CONNECTED ✓ ===")

        } catch (e: Throwable) {
            AppLogger.e(TAG, "startVpn ERROR: ${e.javaClass.simpleName}: ${e.message}", e)
            _state.value  = VpnConnectionState.ERROR
            _server.value = null
            safeCleanup()
        }
    }

    private fun startXrayCore(cfg: String, tunFd: Int): CoreController? {
        return try {
            val cb = object : CoreCallbackHandler {
                // gomobile maps Go int → Kotlin Long (64-bit)
                override fun startup(): Long {
                    AppLogger.i(TAG, "Xray startup callback")
                    return 0L
                }
                override fun shutdown(): Long {
                    AppLogger.i(TAG, "Xray shutdown callback")
                    if (_state.value == VpnConnectionState.CONNECTED) {
                        _state.value = VpnConnectionState.DISCONNECTED
                    }
                    _server.value = null
                    return 0L
                }
                override fun onEmitStatus(code: Long, msg: String?): Long {
                    if (!msg.isNullOrBlank()) AppLogger.d(TAG, "xray: $msg")
                    return 0L
                }
            }
            val ctrl = Libv2ray.newCoreController(cb)
            // StartLoop(config, tunFd int32) — Go int32 → Kotlin Int
            ctrl.startLoop(cfg, tunFd)
            AppLogger.i(TAG, "startLoop done, isRunning=${ctrl.isRunning}")
            ctrl
        } catch (e: Throwable) {
            AppLogger.e(TAG, "startXrayCore exception: ${e.message}", e)
            null
        }
    }

    private fun buildTun(server: ServerConfig): ParcelFileDescriptor? {
        return try {
            val b = Builder()
                .setSession("CloudLine VPN")
                .addAddress("172.19.0.1", 30)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setMtu(8500)
                .allowBypass()

            // Route all traffic through TUN
            b.addRoute("0.0.0.0", 0)
            b.addRoute("::", 0)

            // Per-app routing
            try {
                val bypass = VpnStateManager.bypassApps
                val proxy  = VpnStateManager.proxyApps
                when {
                    proxy.isNotEmpty() -> {
                        // Allowed-apps mode — only these apps use VPN
                        // Cannot mix addAllowedApplication with addDisallowedApplication
                        proxy.forEach {
                            if (it != packageName) {
                                try { b.addAllowedApplication(it) } catch (_: Exception) {}
                            }
                        }
                    }
                    else -> {
                        // Default: exclude self + bypass apps
                        try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
                        bypass.forEach {
                            try { b.addDisallowedApplication(it) } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { b.setMetered(false) } catch (_: Exception) {}
            }

            b.establish()
        } catch (e: Throwable) {
            AppLogger.e(TAG, "buildTun: ${e.message}", e)
            null
        }
    }

    // ── Hot-Switch Server (without disconnecting TUN) ────────────────────────

    fun hotSwitchServer(server: ServerConfig) {
        scope.launch {
            try {
                AppLogger.i(TAG, "=== Hot-switch to [${server.name}] ${server.protocol} ${server.address}:${server.port} ===")
                _state.value = VpnConnectionState.CONNECTING

                // Stop old Xray core (TUN stays open)
                stopXraySafe()
                delay(200)

                // Build new config
                val proxyShare = VpnStateManager.proxyShareEnabled
                val cfg = XrayConfigBuilder.build(server, SOCKS_PORT, HTTP_PORT, proxyShare)

                // Re-use existing TUN fd
                val fd = vpnFd?.fd
                if (fd == null || fd < 0) {
                    AppLogger.e(TAG, "Hot-switch: TUN fd invalid, falling back to full restart")
                    startVpn(server)
                    return@launch
                }

                // Start Xray with same TUN fd
                val ctrl = startXrayCore(cfg, fd)
                if (ctrl == null) {
                    AppLogger.e(TAG, "Hot-switch: startXrayCore failed")
                    _state.value = VpnConnectionState.ERROR
                    return@launch
                }
                xrayCtrl = ctrl
                delay(300)

                if (!ctrl.isRunning) {
                    AppLogger.e(TAG, "Hot-switch: Xray not running after start")
                    _state.value = VpnConnectionState.ERROR
                    return@launch
                }

                _server.value = server
                _state.value = VpnConnectionState.CONNECTED
                _traffic.value = TrafficStats() // reset traffic counters
                updateNotification(server)
                startTrafficMonitor(ctrl)
                AppLogger.i(TAG, "=== Hot-switch DONE ✓ ===")
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Hot-switch ERROR: ${e.message}", e)
                _state.value = VpnConnectionState.ERROR
            }
        }
    }

    // ── Stop VPN ──────────────────────────────────────────────────────────────

    private suspend fun stopVpn() {
        AppLogger.i(TAG, "Stopping VPN…")
        safeCleanupSuspend()
        try { withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE) } }
        catch (_: Throwable) {}
        stopSelf()
    }

    private suspend fun stopXraySafe() {
        try { withTimeoutOrNull(3000) { xrayCtrl?.stopLoop() } } catch (_: Throwable) {}
        xrayCtrl = null
    }

    private suspend fun safeCleanupSuspend() {
        try { trafficJob?.cancel(); trafficJob = null } catch (_: Throwable) {}
        try { withTimeoutOrNull(3000) { xrayCtrl?.stopLoop() }; xrayCtrl = null }
        catch (_: Throwable) { xrayCtrl = null }
        delay(100)
        try { vpnFd?.close(); vpnFd = null } catch (_: Throwable) { vpnFd = null }
        _state.value   = VpnConnectionState.DISCONNECTED
        _server.value  = null
        _traffic.value = TrafficStats()
        VpnStateManager.pendingServer = null
        AppLogger.i(TAG, "Cleanup done")
    }

    private fun safeCleanup() {
        try { trafficJob?.cancel(); trafficJob = null } catch (_: Throwable) {}
        try { xrayCtrl?.stopLoop(); xrayCtrl = null } catch (_: Throwable) { xrayCtrl = null }
        try { vpnFd?.close(); vpnFd = null } catch (_: Throwable) { vpnFd = null }
        _state.value   = VpnConnectionState.DISCONNECTED
        _server.value  = null
        _traffic.value = TrafficStats()
        VpnStateManager.pendingServer = null
    }

    // ── Traffic monitor ───────────────────────────────────────────────────────

    private fun startTrafficMonitor(ctrl: CoreController) {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            var totalDown = 0L
            var totalUp   = 0L
            var proxyShareDown = 0L
            var proxyShareUp   = 0L
            while (isActive && _state.value == VpnConnectionState.CONNECTED) {
                delay(1000)
                try {
                    if (!ctrl.isRunning) {
                        AppLogger.e(TAG, "Xray stopped unexpectedly")
                        _state.value = VpnConnectionState.ERROR
                        break
                    }
                    val down = ctrl.queryStats("proxy", "downlink").coerceAtLeast(0)
                    val up   = ctrl.queryStats("proxy", "uplink").coerceAtLeast(0)
                    totalDown += down; totalUp += up
                    _traffic.value = TrafficStats(down, up, totalDown, totalUp)

                    // Track proxy share (http-in inbound) traffic
                    val psDown = ctrl.queryStats("http-in", "downlink").coerceAtLeast(0)
                    val psUp   = ctrl.queryStats("http-in", "uplink").coerceAtLeast(0)
                    proxyShareDown += psDown; proxyShareUp += psUp
                    _proxyShareTraffic.value = proxyShareDown to proxyShareUp
                } catch (_: Throwable) {}
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(server: ServerConfig) {
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, CloudLineVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CloudLine VPN – متصل")
                .setContentText(server.name.ifBlank { server.address })
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .addAction(R.drawable.ic_disconnect, "قطع اتصال", stopPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "CloudLine VPN",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "وضعیت اتصال VPN" }
            )
        }
    }
}

// ── Types ─────────────────────────────────────────────────────────────────────

enum class VpnConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class TrafficStats(
    val downloadSpeed: Long = 0,
    val uploadSpeed:   Long = 0,
    val totalDownload: Long = 0,
    val totalUpload:   Long = 0
)

object VpnStateManager {
    @Volatile var pendingServer: ServerConfig? = null
    @Volatile var bypassApps: Set<String> = emptySet()
    @Volatile var proxyApps:  Set<String> = emptySet()
    @Volatile var proxyShareEnabled: Boolean = false
}
