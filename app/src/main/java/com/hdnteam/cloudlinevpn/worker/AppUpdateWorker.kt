package com.hdnteam.cloudlinevpn.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hdnteam.cloudlinevpn.BuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Daily auto-update worker.
 *
 * Every 24h downloads fresh versions of:
 *  1. geoip.dat + geosite.dat  (from Loyalsoldier)
 *  2. hev-socks5-tunnel binary  (from heiher/hev-socks5-tunnel)
 *  3. libv2ray.aar (Xray core)  (from 2dust/v2rayNG)
 *  4. CloudLine app APK update  (from hdnteam/cloudline-vpn — if available)
 */
@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "daily_asset_update"
        private const val TAG = "AppUpdateWorker"

        const val V2RAYNG_API = "https://api.github.com/repos/2dust/v2rayNG/releases/latest"
        const val HEV_API     = "https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/latest"
        const val GEOIP_URL   = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
        const val GEOSITE_URL = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"

        fun schedule(workManager: WorkManager) {
            val req = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun scheduleNow(workManager: WorkManager): WorkRequest =
            OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build().also { workManager.enqueue(it) }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily asset update...")
        var ok = true
        if (!updateGeoFiles())   ok = false
        if (!updateHevTunnel())  ok = false
        if (!updateXrayCore())   ok = false
        checkAppUpdate()
        Log.i(TAG, "Daily update complete. ok=$ok")
        return if (ok) Result.success() else Result.retry()
    }

    // ── 1. Geo files ──────────────────────────────────────────────────────────
    private fun updateGeoFiles(): Boolean {
        return try {
            Log.d(TAG, "Updating geo files...")
            downloadFile(GEOIP_URL,   File(applicationContext.filesDir, "geoip.dat"))
            downloadFile(GEOSITE_URL, File(applicationContext.filesDir, "geosite.dat"))
            Log.i(TAG, "Geo files updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Geo files: ${e.message}"); false
        }
    }

    // ── 2. hev-socks5-tunnel ──────────────────────────────────────────────────
    private fun updateHevTunnel(): Boolean {
        return try {
            val release = fetchRelease(HEV_API) ?: return false
            val tagName = release.get("tag_name").asString
            val saved   = readVersion("hev_version.txt")
            if (saved == tagName) { Log.d(TAG, "hev already at $tagName"); return true }

            Log.i(TAG, "hev update $saved → $tagName")
            val abi = getDeviceAbi()
            val assetName = when (abi) {
                "arm64-v8a"   -> "hev-socks5-tunnel-linux-arm64"
                "armeabi-v7a" -> "hev-socks5-tunnel-linux-arm32v7"
                "x86_64"      -> "hev-socks5-tunnel-linux-x86_64"
                "x86"         -> "hev-socks5-tunnel-linux-i686"
                else          -> "hev-socks5-tunnel-linux-arm64"
            }

            val url = findAssetUrl(release, assetName) ?: return false
            downloadFile(url, File(applicationContext.filesDir, "libhev-socks5-tunnel_new.so"))
            saveVersion("hev_version.txt", tagName)
            Log.i(TAG, "hev updated to $tagName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "hev update: ${e.message}"); false
        }
    }

    // ── 3. Xray core (libv2ray.aar from v2rayNG) ──────────────────────────────
    private fun updateXrayCore(): Boolean {
        return try {
            val release = fetchRelease(V2RAYNG_API) ?: return false
            val tagName = release.get("tag_name").asString
            val saved   = readVersion("xray_version.txt")
            if (saved == tagName) { Log.d(TAG, "Xray already at $tagName"); return true }

            Log.i(TAG, "Xray update $saved → $tagName")
            val url = findAssetUrlByExtension(release, ".aar")
            if (url != null) {
                val dest = File(applicationContext.cacheDir, "libv2ray_update.aar")
                downloadFile(url, dest)
                saveVersion("xray_version.txt", tagName)
                saveVersion("v2rayng_version.txt", tagName.trimStart('v'))
                saveVersion("xray_update_pending.txt", dest.absolutePath)
                Log.i(TAG, "Xray core downloaded (${dest.length() / 1024 / 1024} MB)")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Xray update: ${e.message}"); false
        }
    }

    // ── 4. App self-update ────────────────────────────────────────────────────
    private fun checkAppUpdate() {
        val repoApi = "https://api.github.com/repos/hdnteam/cloudline-vpn/releases/latest"
        try {
            val release = fetchRelease(repoApi) ?: return
            val tagName = release.get("tag_name").asString
            val latestCode = tagName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: return
            if (latestCode <= BuildConfig.VERSION_CODE) return

            val url = findAssetUrlByExtension(release, ".apk") ?: return
            val apk = File(applicationContext.cacheDir, "cloudline_update.apk")
            downloadFile(url, apk)
            promptInstall(apk)
        } catch (e: Exception) {
            Log.d(TAG, "App update check: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun fetchRelease(url: String): JsonObject? {
        val resp = client.newCall(
            Request.Builder().url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
        ).execute()
        if (!resp.isSuccessful) return null
        val body = resp.body?.string() ?: return null
        return JsonParser.parseString(body).asJsonObject
    }

    private fun findAssetUrl(release: JsonObject, name: String): String? {
        val assets = release.getAsJsonArray("assets")
        for (el in assets) {
            val obj = el.asJsonObject
            if (obj.get("name").asString == name) {
                return obj.get("browser_download_url").asString
            }
        }
        return null
    }

    private fun findAssetUrlByExtension(release: JsonObject, ext: String): String? {
        val assets = release.getAsJsonArray("assets")
        for (el in assets) {
            val obj = el.asJsonObject
            if (obj.get("name").asString.endsWith(ext)) {
                return obj.get("browser_download_url").asString
            }
        }
        return null
    }

    private fun downloadFile(url: String, dest: File) {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        resp.body?.byteStream()?.use { inp ->
            FileOutputStream(dest).use { out -> inp.copyTo(out) }
        }
        Log.d(TAG, "Downloaded ${dest.name} (${dest.length() / 1024} KB)")
    }

    private fun promptInstall(apk: File) {
        val uri = FileProvider.getUriForFile(
            applicationContext, "${applicationContext.packageName}.fileprovider", apk
        )
        applicationContext.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        )
    }

    private fun getDeviceAbi() = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a"   -> "arm64-v8a"
        "armeabi-v7a" -> "armeabi-v7a"
        "x86_64"      -> "x86_64"
        "x86"         -> "x86"
        else          -> "arm64-v8a"
    }

    private fun readVersion(file: String): String =
        try { File(applicationContext.filesDir, file).readText().trim() }
        catch (_: Exception) { "" }

    private fun saveVersion(file: String, v: String) =
        File(applicationContext.filesDir, file).writeText(v)
}
