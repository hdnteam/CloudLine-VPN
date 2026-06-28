package com.hdnteam.cloudlinevpn.data.repository

import android.util.Log
import com.hdnteam.cloudlinevpn.data.db.ServerConfigDao
import com.hdnteam.cloudlinevpn.data.db.SubscriptionDao
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.data.model.Subscription
import com.hdnteam.cloudlinevpn.data.parser.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val serverConfigDao: ServerConfigDao
) {
    private val TAG = "SubscriptionRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val subscriptions: Flow<List<Subscription>> = subscriptionDao.getAll()
    val servers: Flow<List<ServerConfig>> = serverConfigDao.getAll()

    fun getServersBySubscription(subId: Long): Flow<List<ServerConfig>> =
        serverConfigDao.getBySubscription(subId)

    suspend fun addSubscription(url: String, alias: String = ""): Result<Subscription> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if it's a single config (vmess://, vless://, trojan://, ss://)
                val trimmed = url.trim()
                if (trimmed.startsWith("vmess://") || trimmed.startsWith("vless://") ||
                    trimmed.startsWith("trojan://") || trimmed.startsWith("ss://")) {
                    // Single config — create a dummy subscription and parse the config
                    val sub = Subscription(url = trimmed, alias = alias.ifBlank { "تک کانفیگ" })
                    val id = subscriptionDao.insert(sub)
                    val inserted = sub.copy(id = id)
                    val config = SubscriptionParser.parseUri(trimmed, id)
                    if (config != null) {
                        serverConfigDao.insertAll(listOf(config))
                        val updated = inserted.copy(serverCount = 1, lastUpdated = System.currentTimeMillis())
                        subscriptionDao.update(updated)
                        return@withContext Result.success(updated)
                    } else {
                        subscriptionDao.delete(inserted)
                        return@withContext Result.failure(Exception("کانفیگ نامعتبر"))
                    }
                }

                // Otherwise treat as subscription URL
                val sub = Subscription(url = url, alias = alias.ifBlank { url })
                val id = subscriptionDao.insert(sub)
                val inserted = sub.copy(id = id)
                fetchAndUpdate(inserted)
            } catch (e: Exception) {
                Log.e(TAG, "addSubscription error", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAndUpdate(subscription: Subscription): Result<Subscription> {
        return withContext(Dispatchers.IO) {
            try {
                // Skip single config URIs — they don't need fetching
                val url = subscription.url.trim()
                if (url.startsWith("vmess://") || url.startsWith("vless://") ||
                    url.startsWith("trojan://") || url.startsWith("ss://")) {
                    return@withContext Result.success(subscription)
                }

                val request = Request.Builder()
                    .url(subscription.url)
                    .addHeader("User-Agent", "CloudLine-VPN/1.0 (Android)")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(
                    Exception("Empty response")
                )

                // Parse subscription-userinfo header
                val userInfo = response.header("subscription-userinfo")
                val (upload, download, total, expire) = parseUserInfo(userInfo)

                // Parse servers
                val configs = SubscriptionParser.parse(body, subscription.id)

                // Update DB
                serverConfigDao.deleteBySubscription(subscription.id)
                serverConfigDao.insertAll(configs)

                val updated = subscription.copy(
                    uploadBytes = upload,
                    downloadBytes = download,
                    totalBytes = total,
                    expireTimestamp = expire,
                    lastUpdated = System.currentTimeMillis(),
                    serverCount = configs.size
                )
                subscriptionDao.update(updated)
                Result.success(updated)
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndUpdate error", e)
                Result.failure(e)
            }
        }
    }

    suspend fun refreshAllSubscriptions(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val subs = subscriptionDao.getActive().first()
                var errorCount = 0
                subs.forEach { sub ->
                    val result = fetchAndUpdate(sub)
                    if (result.isFailure) errorCount++
                }
                if (errorCount == 0) Result.success(Unit)
                else Result.failure(Exception("$errorCount subscriptions failed to update"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        serverConfigDao.deleteBySubscription(subscription.id)
        subscriptionDao.delete(subscription)
    }

    suspend fun selectServer(id: Long) {
        serverConfigDao.clearSelection()
        serverConfigDao.select(id)
    }

    suspend fun getBestServer(): ServerConfig?    = serverConfigDao.getBestLatency()
    suspend fun getSelectedServer(): ServerConfig? = serverConfigDao.getSelected()
    suspend fun getServerById(id: Long): ServerConfig? = serverConfigDao.getById(id)
    suspend fun updateServer(server: ServerConfig) {
        serverConfigDao.update(server)
    }

    suspend fun getAllServersDirect(): List<ServerConfig> = serverConfigDao.getAllDirect()

    suspend fun updateLatency(id: Long, latency: Long) {
        serverConfigDao.updateLatency(id, latency)
    }

    private fun parseUserInfo(header: String?): List<Long> {
        if (header == null) return listOf(0, 0, 0, 0)
        val map = header.split(";").mapNotNull {
            val parts = it.trim().split("=")
            if (parts.size == 2) parts[0].trim() to parts[1].trim().toLongOrNull()
            else null
        }.toMap()
        return listOf(
            map["upload"] ?: 0L,
            map["download"] ?: 0L,
            map["total"] ?: 0L,
            map["expire"] ?: 0L
        )
    }
}
