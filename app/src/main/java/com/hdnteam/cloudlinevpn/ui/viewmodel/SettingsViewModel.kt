package com.hdnteam.cloudlinevpn.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.gson.JsonParser
import com.hdnteam.cloudlinevpn.BuildConfig
import com.hdnteam.cloudlinevpn.data.model.Subscription
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import com.hdnteam.cloudlinevpn.util.AppLogger
import com.hdnteam.cloudlinevpn.worker.AppUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val subscriptionRepository: SubscriptionRepository,
    private val workManager: WorkManager
) : AndroidViewModel(application) {

    companion object {
        val KEY_PROXY_SHARE = booleanPreferencesKey("proxy_share")
        val KEY_ASSET_VERSION = stringPreferencesKey("asset_version")
    }

    private val dataStore = application.dataStore
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "CloudLine-VPN/1.0 Android")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            chain.proceed(request)
        }
        .build()

    // Client that routes through Xray HTTP proxy when VPN is active
    private fun getProxiedClient(): OkHttpClient {
        return try {
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress("127.0.0.1", 8080)
            )
            OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .addHeader("User-Agent", "CloudLine-VPN/1.0 Android")
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .build())
                }
                .build()
        } catch (_: Exception) { client }
    }

    val subscriptions: StateFlow<List<Subscription>> = subscriptionRepository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxyShareEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[KEY_PROXY_SHARE] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    private val _updateProgress = MutableStateFlow("")
    val updateProgress: StateFlow<String> = _updateProgress

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    // App version — mirrors v2rayNG version
    // Initialized lazily to avoid crash during Hilt injection
    private val _appVersion = MutableStateFlow("2.2.5")
    val appVersion: StateFlow<String> = _appVersion

    // Proxy share traffic (download, upload) from Xray http-in inbound
    private val _proxyShareTraffic = MutableStateFlow(0L to 0L)
    val proxyShareTraffic: StateFlow<Pair<Long, Long>> = _proxyShareTraffic

    init {
        // Load saved version after ViewModel is fully initialized
        viewModelScope.launch(Dispatchers.IO) {
            val saved = readVersion("v2rayng_version.txt")
            if (saved.isNotEmpty() && saved != "نامشخص") {
                _appVersion.value = saved
            }
        }
        // Load proxy share state
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val enabled = prefs[KEY_PROXY_SHARE] ?: false
                com.hdnteam.cloudlinevpn.vpn.VpnStateManager.proxyShareEnabled = enabled
            }
        }
        // Monitor proxy share traffic via StateFlow (no polling)
        viewModelScope.launch {
            com.hdnteam.cloudlinevpn.vpn.CloudLineVpnService.proxyShareTraffic.collect { traffic ->
                _proxyShareTraffic.value = traffic
            }
        }
    }

    val appVersionCode: Int = BuildConfig.VERSION_CODE

    // Asset version (Xray core + hev version from last update)
    val assetVersion: StateFlow<String> = dataStore.data
        .map { it[KEY_ASSET_VERSION] ?: "نامشخص" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "نامشخص")
    // ── Subscription ──────────────────────────────────────────────────────────
    fun addSubscription(url: String, alias: String = "") {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = subscriptionRepository.addSubscription(url.trim(), alias.trim())
            _isRefreshing.value = false
            _message.value = if (result.isSuccess) "اشتراک با موفقیت اضافه شد"
            else "خطا: ${result.exceptionOrNull()?.message ?: "دوباره تلاش کنید"}"
        }
    }

    fun refreshSubscriptions() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val subs = subscriptionRepository.subscriptions.first()
            if (subs.isEmpty()) {
                _message.value = "هیچ اشتراکی وجود ندارد"
                _isRefreshing.value = false
                return@launch
            }
            var failed = 0
            subs.forEach { sub ->
                val result = subscriptionRepository.fetchAndUpdate(sub)
                if (result.isFailure) failed++
            }
            _isRefreshing.value = false
            _message.value = if (failed == 0) "سرورها بروزرسانی شدند ✓" else "$failed اشتراک ناموفق"
        }
    }

    fun deleteSubscription(sub: Subscription) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(sub)
            _message.value = "اشتراک حذف شد"
        }
    }

    // ── Proxy Share ───────────────────────────────────────────────────────────
    fun setProxyShare(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_PROXY_SHARE] = enabled }
            com.hdnteam.cloudlinevpn.vpn.VpnStateManager.proxyShareEnabled = enabled
            _message.value = if (enabled)
                "پروکسی فعال شد — بعد از اتصال مجدد اعمال میشه"
            else "پروکسی غیرفعال شد"
        }
    }

    // ── App Update ────────────────────────────────────────────────────────────
    fun checkForUpdate() {
        viewModelScope.launch {
            _isUpdating.value = true
            _updateProgress.value = "در حال بررسی بروزرسانی…"
            AppLogger.i("Update", "=== بررسی بروزرسانی شروع شد ===")

            // Check if VPN is connected — if not, show message that VPN is needed
            val vpnState = com.hdnteam.cloudlinevpn.vpn.CloudLineVpnService.connectionState.value
            if (vpnState != com.hdnteam.cloudlinevpn.vpn.VpnConnectionState.CONNECTED) {
                _updateProgress.value = "⚠ وی‌پی‌ان متصل نیست\nبرای بروزرسانی ابتدا وی‌پی‌ان را روشن کنید"
                AppLogger.e("Update", "VPN not connected")
                delay(4000)
                _isUpdating.value = false
                _updateProgress.value = ""
                return@launch
            }

            try {
                val (xrayTag, hevTag) = withContext(Dispatchers.IO) {
                    _updateProgress.value = "بررسی نسخه Xray…"
                    val xray = fetchLatestTag("https://api.github.com/repos/2dust/v2rayNG/releases/latest")
                    _updateProgress.value = "بررسی نسخه hev-tunnel…"
                    val hev = fetchLatestTag("https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/latest")
                    xray to hev
                }

                val savedXray = readVersion("xray_version.txt")
                val savedHev  = readVersion("hev_version.txt")
                AppLogger.i("Update", "فعلی: Xray=$savedXray | hev=$savedHev")
                AppLogger.i("Update", "آخرین: Xray=${xrayTag ?: "N/A"} | hev=${hevTag ?: "N/A"}")

                val xrayNew = xrayTag != null && xrayTag != savedXray
                val hevNew  = hevTag  != null && hevTag  != savedHev

                val displayVer = "Xray: ${xrayTag ?: savedXray.ifEmpty { "نامشخص" }} | hev: ${hevTag ?: savedHev.ifEmpty { "نامشخص" }}"
                saveAssetVersionToPrefs(displayVer)
                if (xrayTag != null) {
                    saveVersion("v2rayng_version.txt", xrayTag.trimStart('v'))
                    _appVersion.value = xrayTag.trimStart('v')
                }

                when {
                    xrayTag == null && hevTag == null -> {
                        _updateProgress.value = "⚠ نتوانست به گیت‌هاب متصل شود\nوی‌پی‌ان متصل است اما GitHub در دسترس نیست"
                        AppLogger.e("Update", "GitHub unreachable even with VPN")
                    }
                    !xrayNew && !hevNew -> {
                        val msg = "✓ برنامه بروز است\n$displayVer"
                        _updateProgress.value = msg
                        AppLogger.i("Update", msg)
                    }
                    else -> {
                        val parts = mutableListOf<String>()
                        if (xrayNew) { parts.add("Xray: $savedXray → $xrayTag"); AppLogger.i("Update", "Xray جدید: $xrayTag") }
                        if (hevNew)  { parts.add("hev:  $savedHev → $hevTag");   AppLogger.i("Update", "hev جدید: $hevTag") }
                        _updateProgress.value = "⬇ بروزرسانی:\n${parts.joinToString("\n")}"
                        AppUpdateWorker.scheduleNow(workManager)
                        delay(500)
                        _updateProgress.value = "⬇ دانلود شروع شد\n$displayVer"
                        AppLogger.i("Update", "Worker در صف")
                    }
                }
            } catch (e: Exception) {
                val msg = "خطا: ${e.message}"
                _updateProgress.value = msg
                AppLogger.e("Update", msg, e)
            } finally {
                AppLogger.i("Update", "=== تمام شد ===")
                delay(4000)
                _isUpdating.value = false
                _updateProgress.value = ""
            }
        }
    }

    fun getLastCrash(): String? =
        com.hdnteam.cloudlinevpn.util.CrashHandler.getLastCrash(getApplication())

    fun clearMessage() { _message.value = "" }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun fetchLatestTag(url: String): String? {
        // Use HTTP proxy through Xray (127.0.0.1:8080) — works when VPN is connected
        val httpClient = getProxiedClient()
        return try {
            val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
            AppLogger.d("Update", "HTTP ${resp.code} for $url")
            if (!resp.isSuccessful) {
                AppLogger.e("Update", "HTTP ${resp.code} — ${resp.message}")
                return null
            }
            val body = resp.body?.string() ?: return null
            val tag = JsonParser.parseString(body).asJsonObject.get("tag_name")?.asString
            AppLogger.i("Update", "Tag from $url = $tag")
            tag
        } catch (e: Exception) {
            AppLogger.e("Update", "fetchLatestTag error: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun readVersion(file: String): String =
        try { File(getApplication<Application>().filesDir, file).readText().trim() }
        catch (_: Exception) { "نامشخص" }

    private fun saveVersion(file: String, version: String) {
        try { File(getApplication<Application>().filesDir, file).writeText(version) }
        catch (_: Exception) {}
    }

    private suspend fun saveAssetVersionToPrefs(version: String) {
        dataStore.edit { it[KEY_ASSET_VERSION] = version }
    }
}
