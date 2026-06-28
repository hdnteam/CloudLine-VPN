package com.hdnteam.cloudlinevpn.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import com.hdnteam.cloudlinevpn.vpn.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val subscriptionRepository: SubscriptionRepository
) : AndroidViewModel(application) {

    // State flows — declare BEFORE init block
    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _connectStep   = MutableStateFlow(ConnectStep.IDLE)
    val connectStep: StateFlow<ConnectStep> = _connectStep

    // Session usage — resets on each connect
    private val _sessionUsage = MutableStateFlow(0L)
    val sessionUsage: StateFlow<Long> = _sessionUsage

    // VPN service state
    val connectionState: StateFlow<VpnConnectionState> = CloudLineVpnService.connectionState
    val trafficStats:    StateFlow<TrafficStats>        = CloudLineVpnService.trafficStats
    val connectedServer: StateFlow<ServerConfig?>       = CloudLineVpnService.connectedServer

    val servers: StateFlow<List<ServerConfig>> = subscriptionRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Connect job — can be cancelled
    private var connectJob: Job? = null

    init {
        // Watch connection state
        viewModelScope.launch {
            CloudLineVpnService.connectionState.collect { state ->
                when (state) {
                    VpnConnectionState.ERROR -> {
                        _statusMessage.value = "خطا در اتصال — دوباره تلاش کنید"
                        _isLoading.value     = false
                        _connectStep.value   = ConnectStep.IDLE
                    }
                    VpnConnectionState.DISCONNECTED -> {
                        _isLoading.value   = false
                        _connectStep.value = ConnectStep.IDLE
                    }
                    VpnConnectionState.CONNECTED -> {
                        _isLoading.value   = false
                        _connectStep.value = ConnectStep.IDLE
                        _statusMessage.value = ""
                        _sessionUsage.value = 0L  // reset session counter
                    }
                    else -> {}
                }
            }
        }

        // Track session usage (total download + upload)
        viewModelScope.launch {
            CloudLineVpnService.trafficStats.collect { stats ->
                if (CloudLineVpnService.connectionState.value == VpnConnectionState.CONNECTED) {
                    _sessionUsage.value = stats.totalDownload + stats.totalUpload
                }
            }
        }

        // On fresh start, verify VPN state consistency
        viewModelScope.launch {
            delay(500)
            if (CloudLineVpnService.connectionState.value == VpnConnectionState.CONNECTED &&
                CloudLineVpnService.connectedServer.value == null) {
                CloudLineVpnService.resetState()
            }
        }

        // Auto-refresh subscriptions on app start (silent, no UI blocking)
        viewModelScope.launch {
            delay(1000)
            try { refreshSubscriptionsSilent() } catch (_: Throwable) {}
        }
    }

    fun requestVpnPermission(): Intent? = VpnService.prepare(getApplication())

    // ── Auto-Connect: update sub → test latency → best server ─────────────────
    fun connectAutomatic() {
        if (_isLoading.value) {
            // Second tap = cancel
            cancelConnect()
            return
        }
        connectJob = viewModelScope.launch {
            try {
                _isLoading.value = true

                // Step 1: Update subscription (skip if fails)
                _connectStep.value = ConnectStep.UPDATING_SUB
                _statusMessage.value = "بروزرسانی سرورها…"
                try { refreshSubscriptions() } catch (_: Throwable) {
                    // Sub update failed — continue anyway with existing servers
                    _statusMessage.value = "بروزرسانی ناموفق — ادامه با سرورهای موجود…"
                }

                // Check cancellation
                ensureActive()

                // Step 2: Get servers
                val allServers = subscriptionRepository.getAllServersDirect()
                if (allServers.isEmpty()) {
                    _statusMessage.value = "سروری یافت نشد. لینک اشتراک را بررسی کنید."
                    reset()
                    return@launch
                }

                // Step 3: Test latency (max 10)
                _connectStep.value = ConnectStep.TESTING_LATENCY
                val toTest = allServers.take(10)
                _statusMessage.value = "تست پینگ ${toTest.size} سرور…"

                val results = toTest.map { server ->
                    async {
                        ensureActive()
                        val latency = LatencyTester.testServer(server.address, server.port)
                        subscriptionRepository.updateLatency(server.id, latency)
                        server to latency
                    }
                }.awaitAll()

                ensureActive()

                // Step 4: Best server
                val best = results.filter { it.second > 0 }.minByOrNull { it.second }
                if (best == null) {
                    _statusMessage.value = "همه سرورها در دسترس نیستند."
                    reset()
                    return@launch
                }

                _connectStep.value = ConnectStep.CONNECTING
                _statusMessage.value = "اتصال به ${best.first.name.ifBlank { best.first.address }} · ${best.second}ms"
                launchVpn(best.first)

            } catch (e: CancellationException) {
                _statusMessage.value = "لغو شد"
                reset()
            } catch (e: Exception) {
                _statusMessage.value = "خطا: ${e.message}"
                reset()
            }
        }
    }

    // ── Hot-Switch: change server without disconnecting VPN ──────────────────
    fun switchServer(server: ServerConfig) {
        // If VPN is already connected — hot-switch the Xray core without tearing down TUN
        if (connectionState.value == VpnConnectionState.CONNECTED) {
            viewModelScope.launch {
                _statusMessage.value = "سوئیچ به ${server.name.ifBlank { server.address }}…"
                subscriptionRepository.selectServer(server.id)
                val ctx = getApplication<Application>()
                // Trigger hot-switch via a static method — no new service start needed
                CloudLineVpnService.hotSwitch(ctx, server)
            }
            return
        }
        // Not connected — do a normal connect
        connectToServer(server)
    }

    // ── Manual-Connect: update sub → connect to chosen server ──────────────────
    fun connectToServer(server: ServerConfig) {
        if (_isLoading.value) {
            cancelConnect()
            return
        }
        connectJob = viewModelScope.launch {
            try {
                _isLoading.value = true

                _connectStep.value = ConnectStep.UPDATING_SUB
                _statusMessage.value = "بروزرسانی…"
                try { refreshSubscriptions() } catch (_: Throwable) {}

                ensureActive()

                val fresh = subscriptionRepository.getServerById(server.id) ?: server

                _connectStep.value = ConnectStep.CONNECTING
                _statusMessage.value = "اتصال به ${fresh.name.ifBlank { fresh.address }}…"
                launchVpn(fresh)

            } catch (e: CancellationException) {
                _statusMessage.value = "لغو شد"
                reset()
            } catch (e: Exception) {
                _statusMessage.value = "خطا: ${e.message}"
                reset()
            }
        }
    }

    // ── Cancel ongoing connect ────────────────────────────────────────────────
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        _statusMessage.value = "لغو شد"
        reset()
    }

    // ── Disconnect ────────────────────────────────────────────────────────────
    fun disconnect() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, CloudLineVpnService::class.java).apply {
                action = CloudLineVpnService.ACTION_STOP
            }
        )
        _connectStep.value   = ConnectStep.IDLE
        _statusMessage.value = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun refreshSubscriptions() {
        val subs = subscriptionRepository.subscriptions.first()
        subs.forEach { subscriptionRepository.fetchAndUpdate(it) }
    }

    /** Silent refresh — no exceptions thrown, for background auto-refresh */
    private suspend fun refreshSubscriptionsSilent() {
        try {
            val subs = subscriptionRepository.subscriptions.first()
            subs.forEach {
                try { subscriptionRepository.fetchAndUpdate(it) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun launchVpn(server: ServerConfig) {
        val ctx = getApplication<Application>()
        VpnStateManager.pendingServer = server

        viewModelScope.launch {
            subscriptionRepository.selectServer(server.id)
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                ctx.startForegroundService(
                    Intent(ctx, CloudLineVpnService::class.java).apply {
                        action = CloudLineVpnService.ACTION_START
                    }
                )
            } catch (e: Exception) {
                _statusMessage.value = "خطا در شروع سرویس: ${e.message}"
                reset()
            }
        }
    }

    private fun reset() {
        _isLoading.value   = false
        _connectStep.value = ConnectStep.IDLE
    }

    fun clearStatusMessage() { _statusMessage.value = "" }
}

enum class ConnectStep { IDLE, UPDATING_SUB, TESTING_LATENCY, CONNECTING }
