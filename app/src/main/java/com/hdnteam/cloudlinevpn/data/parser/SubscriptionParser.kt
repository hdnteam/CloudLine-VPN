package com.hdnteam.cloudlinevpn.data.parser

import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Universal subscription/config parser.
 * Supports: VMess, VLESS, Trojan, Shadowsocks, Hysteria2
 * Handles all transport types, TLS/Reality, and edge cases.
 */
object SubscriptionParser {

    private const val TAG = "SubscriptionParser"

    // ── Public entry point ────────────────────────────────────────────────────

    fun parse(content: String, subscriptionId: Long): List<ServerConfig> {
        if (content.isBlank()) return emptyList()
        return try {
            val trimmed = content.trim()
            val lines = tryBase64Decode(trimmed)?.lines() ?: trimmed.lines()
            lines.mapIndexedNotNull { index, line ->
                val uri = line.trim()
                if (uri.isBlank()) null
                else try { parseUri(uri, subscriptionId, index) }
                catch (e: Exception) { Log.w(TAG, "Skip[$index]: ${e.message}"); null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse error: ${e.message}", e)
            emptyList()
        }
    }

    fun parseUri(uri: String, subscriptionId: Long = 0, sortOrder: Int = 0): ServerConfig? {
        return when {
            uri.startsWith("vmess://")      -> parseVmess(uri, subscriptionId, sortOrder)
            uri.startsWith("vless://")      -> parseVless(uri, subscriptionId, sortOrder)
            uri.startsWith("trojan://")     -> parseTrojan(uri, subscriptionId, sortOrder)
            uri.startsWith("ss://")         -> parseShadowsocks(uri, subscriptionId, sortOrder)
            uri.startsWith("hysteria2://")  -> parseHysteria2(uri, subscriptionId, sortOrder)
            uri.startsWith("hy2://")        -> parseHysteria2(uri, subscriptionId, sortOrder)
            else -> null
        }
    }

    // ── VMess ─────────────────────────────────────────────────────────────────

    private fun parseVmess(uri: String, subId: Long, order: Int): ServerConfig? {
        return try {
            val encoded = uri.removePrefix("vmess://").trim()
            val decoded = decodeBase64Safe(encoded) ?: return null
            val obj = JsonParser.parseString(decoded).asJsonObject

            val net = obj.get("net")?.asString ?: "tcp"
            val tls = obj.get("tls")?.asString ?: ""
            val sni = obj.get("sni")?.asString ?: ""
            val host = obj.get("host")?.asString ?: ""
            val fp = obj.get("fp")?.asString ?: ""
            val alpn = obj.get("alpn")?.asString ?: ""

            ServerConfig(
                subscriptionId = subId,
                name           = obj.get("ps")?.asString?.trim() ?: "VMess",
                protocol       = "vmess",
                address        = obj.get("add")?.asString ?: "",
                port           = obj.get("port")?.asString?.toIntOrNull()
                    ?: obj.get("port")?.asInt ?: 443,
                uuid           = obj.get("id")?.asString ?: "",
                alterId        = obj.get("aid")?.asString?.toIntOrNull()
                    ?: obj.get("aid")?.asInt ?: 0,
                security       = obj.get("scy")?.asString?.ifBlank { "auto" } ?: "auto",
                network        = net,
                headerType     = obj.get("type")?.asString ?: "",
                requestHost    = host,
                path           = obj.get("path")?.asString ?: "",
                tlsSecurity    = when (tls) { "tls" -> "tls"; "reality" -> "reality"; else -> "none" },
                sni            = sni,
                fingerprint    = fp,
                flow           = obj.get("flow")?.asString ?: "",
                rawConfig      = uri,
                sortOrder      = order
            )
        } catch (e: Exception) {
            Log.e(TAG, "VMess parse error", e); null
        }
    }

    // ── VLESS ─────────────────────────────────────────────────────────────────

    private fun parseVless(uri: String, subId: Long, order: Int): ServerConfig? {
        return try {
            val noScheme = uri.removePrefix("vless://")
            val atIdx    = noScheme.indexOf('@')
            if (atIdx < 0) return null

            val uuid     = urlDecode(noScheme.substring(0, atIdx))
            val rest     = noScheme.substring(atIdx + 1)
            val hashIdx  = rest.lastIndexOf('#')
            val name     = if (hashIdx >= 0) urlDecode(rest.substring(hashIdx + 1)) else "VLESS"
            val hostPart = if (hashIdx >= 0) rest.substring(0, hashIdx) else rest
            val qIdx     = hostPart.indexOf('?')
            val params   = if (qIdx >= 0) parseQuery(hostPart.substring(qIdx + 1)) else emptyMap()
            val hostPort = if (qIdx >= 0) hostPart.substring(0, qIdx) else hostPart
            val (host, port) = splitHostPort(hostPort)
            val security = params["security"] ?: "none"

            ServerConfig(
                subscriptionId = subId,
                name           = name,
                protocol       = "vless",
                address        = host,
                port           = port,
                uuid           = uuid,
                network        = params["type"] ?: "tcp",
                headerType     = params["headerType"] ?: "",
                requestHost    = params["host"] ?: "",
                path           = urlDecode(params["path"] ?: ""),
                tlsSecurity    = security,
                sni            = params["sni"] ?: "",
                fingerprint    = params["fp"] ?: "",
                publicKey      = params["pbk"] ?: "",
                shortId        = params["sid"] ?: "",
                spiderX        = urlDecode(params["spx"] ?: ""),
                flow           = params["flow"] ?: "",
                rawConfig      = uri,
                sortOrder      = order
            )
        } catch (e: Exception) {
            Log.e(TAG, "VLESS parse error", e); null
        }
    }

    // ── Trojan ────────────────────────────────────────────────────────────────

    private fun parseTrojan(uri: String, subId: Long, order: Int): ServerConfig? {
        return try {
            val noScheme = uri.removePrefix("trojan://")
            val atIdx    = noScheme.indexOf('@')
            if (atIdx < 0) return null

            val password = urlDecode(noScheme.substring(0, atIdx))
            val rest     = noScheme.substring(atIdx + 1)
            val hashIdx  = rest.lastIndexOf('#')
            val name     = if (hashIdx >= 0) urlDecode(rest.substring(hashIdx + 1)) else "Trojan"
            val hostPart = if (hashIdx >= 0) rest.substring(0, hashIdx) else rest
            val qIdx     = hostPart.indexOf('?')
            val params   = if (qIdx >= 0) parseQuery(hostPart.substring(qIdx + 1)) else emptyMap()
            val hostPort = if (qIdx >= 0) hostPart.substring(0, qIdx) else hostPart
            val (host, port) = splitHostPort(hostPort)

            ServerConfig(
                subscriptionId = subId,
                name           = name,
                protocol       = "trojan",
                address        = host,
                port           = port,
                password       = password,
                network        = params["type"] ?: "tcp",
                headerType     = params["headerType"] ?: "",
                requestHost    = params["host"] ?: "",
                path           = urlDecode(params["path"] ?: ""),
                tlsSecurity    = params["security"] ?: "tls",
                sni            = params["sni"] ?: "",
                fingerprint    = params["fp"] ?: "",
                flow           = params["flow"] ?: "",
                publicKey      = params["pbk"] ?: "",
                shortId        = params["sid"] ?: "",
                spiderX        = urlDecode(params["spx"] ?: ""),
                rawConfig      = uri,
                sortOrder      = order
            )
        } catch (e: Exception) {
            Log.e(TAG, "Trojan parse error", e); null
        }
    }

    // ── Shadowsocks ───────────────────────────────────────────────────────────

    private fun parseShadowsocks(uri: String, subId: Long, order: Int): ServerConfig? {
        return try {
            val noScheme = uri.removePrefix("ss://")
            val hashIdx  = noScheme.lastIndexOf('#')
            val name     = if (hashIdx >= 0) urlDecode(noScheme.substring(hashIdx + 1)) else "SS"
            val main     = if (hashIdx >= 0) noScheme.substring(0, hashIdx) else noScheme

            // Remove query params for host:port parsing
            val qIdx = main.indexOf('?')
            val mainNoQuery = if (qIdx >= 0) main.substring(0, qIdx) else main
            val params = if (qIdx >= 0) parseQuery(main.substring(qIdx + 1)) else emptyMap()

            val method: String
            val password: String
            val host: String
            val port: Int

            val atIdx = mainNoQuery.lastIndexOf('@')
            if (atIdx >= 0) {
                // SIP002: base64(method:password)@host:port or method:password@host:port
                val credRaw = mainNoQuery.substring(0, atIdx)
                val hostPortStr = mainNoQuery.substring(atIdx + 1)
                val creds = decodeBase64Safe(credRaw) ?: credRaw
                val ci = creds.indexOf(':')
                if (ci < 0) return null
                method = creds.substring(0, ci)
                password = creds.substring(ci + 1)
                val hp = splitHostPort(hostPortStr)
                host = hp.first; port = hp.second
            } else {
                // Legacy: all base64
                val decoded = decodeBase64Safe(mainNoQuery) ?: return null
                val ai = decoded.lastIndexOf('@')
                if (ai < 0) return null
                val creds = decoded.substring(0, ai)
                val ci = creds.indexOf(':')
                if (ci < 0) return null
                method = creds.substring(0, ci)
                password = creds.substring(ci + 1)
                val hp = splitHostPort(decoded.substring(ai + 1))
                host = hp.first; port = hp.second
            }

            // SS can optionally have plugin params (e.g., obfs)
            val plugin = params["plugin"] ?: ""

            ServerConfig(
                subscriptionId = subId,
                name           = name,
                protocol       = "shadowsocks",
                address        = host,
                port           = port,
                method         = method,
                password       = password,
                network        = if (plugin.contains("v2ray-plugin")) "ws" else "tcp",
                path           = params["path"] ?: "",
                requestHost    = params["host"] ?: "",
                tlsSecurity    = if (plugin.contains("tls")) "tls" else "none",
                rawConfig      = uri,
                sortOrder      = order
            )
        } catch (e: Exception) {
            Log.e(TAG, "SS parse error", e); null
        }
    }

    // ── Hysteria2 ─────────────────────────────────────────────────────────────

    private fun parseHysteria2(uri: String, subId: Long, order: Int): ServerConfig? {
        return try {
            val noScheme = uri.removePrefix("hysteria2://").removePrefix("hy2://")
            val atIdx = noScheme.indexOf('@')
            if (atIdx < 0) return null

            val password = urlDecode(noScheme.substring(0, atIdx))
            val rest     = noScheme.substring(atIdx + 1)
            val hashIdx  = rest.lastIndexOf('#')
            val name     = if (hashIdx >= 0) urlDecode(rest.substring(hashIdx + 1)) else "Hysteria2"
            val hostPart = if (hashIdx >= 0) rest.substring(0, hashIdx) else rest
            val qIdx     = hostPart.indexOf('?')
            val params   = if (qIdx >= 0) parseQuery(hostPart.substring(qIdx + 1)) else emptyMap()
            val hostPort = if (qIdx >= 0) hostPart.substring(0, qIdx) else hostPart
            val (host, port) = splitHostPort(hostPort)

            ServerConfig(
                subscriptionId = subId,
                name           = name,
                protocol       = "hysteria2",
                address        = host,
                port           = port,
                password       = password,
                sni            = params["sni"] ?: "",
                fingerprint    = params["fp"] ?: "",
                tlsSecurity    = "tls",
                path           = params["obfs-password"] ?: "",
                headerType     = params["obfs"] ?: "",
                rawConfig      = uri,
                sortOrder      = order
            )
        } catch (e: Exception) {
            Log.e(TAG, "Hysteria2 parse error", e); null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun tryBase64Decode(content: String): String? {
        // Only attempt if content looks like base64 (no line-breaks that are URIs)
        if (content.contains("://")) return null
        return try {
            val decoded = String(Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8)
            if (decoded.contains("vmess://") || decoded.contains("vless://") ||
                decoded.contains("trojan://") || decoded.contains("ss://") ||
                decoded.contains("hysteria2://") || decoded.contains("hy2://")) decoded
            else null
        } catch (_: Exception) { null }
    }

    private fun decodeBase64Safe(s: String): String? {
        return try {
            val padded = when (s.length % 4) {
                2 -> "$s=="
                3 -> "$s="
                else -> s
            }
            String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                .also { if (it.isBlank()) return null }
        } catch (_: Exception) {
            try {
                String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8)
                    .also { if (it.isBlank()) return null }
            } catch (_: Exception) { null }
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null
            else it.substring(0, eq).trim() to urlDecode(it.substring(eq + 1))
        }.toMap()

    private fun urlDecode(s: String): String =
        try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun splitHostPort(s: String): Pair<String, Int> {
        // Handle IPv6 [::1]:port
        if (s.startsWith("[")) {
            val bracket = s.indexOf(']')
            if (bracket >= 0 && bracket + 1 < s.length && s[bracket + 1] == ':') {
                val host = s.substring(1, bracket)
                val port = s.substring(bracket + 2).toIntOrNull() ?: 443
                return host to port
            }
            // [::1] without port
            if (bracket >= 0) return s.substring(1, bracket) to 443
        }
        val lastColon = s.lastIndexOf(':')
        return if (lastColon >= 0) {
            s.substring(0, lastColon) to (s.substring(lastColon + 1).toIntOrNull() ?: 443)
        } else s to 443
    }
}
