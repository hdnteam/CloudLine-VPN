package com.hdnteam.cloudlinevpn.data.parser

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hdnteam.cloudlinevpn.data.model.ServerConfig

/**
 * Universal Xray JSON configuration builder.
 *
 * Supported protocols: VMess, VLESS, Trojan, Shadowsocks, Hysteria2
 * Supported transports: TCP, WS, gRPC, H2, xHTTP/splitHTTP, mKCP, QUIC
 * Supported security: TLS, Reality, None
 *
 * All parameters are parsed from rawConfig URL when available to ensure
 * no information is lost during parsing→build pipeline.
 */
object XrayConfigBuilder {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun build(
        server: ServerConfig,
        socksPort: Int = 10808,
        httpPort: Int = 8080,
        proxyShareEnabled: Boolean = false
    ): String {
        val root = JsonObject()

        // ── Log ──────────────────────────────────────────────────────────────
        root.add("log", JsonObject().apply {
            addProperty("loglevel", "error")
        })

        // ── Stats + Policy ───────────────────────────────────────────────────
        root.add("stats", JsonObject())
        root.add("policy", JsonObject().apply {
            add("levels", JsonObject().apply {
                add("8", JsonObject().apply {
                    addProperty("handshake", 4)
                    addProperty("connIdle", 600)      // 10 min — keeps long-lived connections (WhatsApp, etc.)
                    addProperty("uplinkOnly", 2)
                    addProperty("downlinkOnly", 5)    // wait longer before closing download-only connections
                    addProperty("bufferSize", 4)
                })
            })
            add("system", JsonObject().apply {
                addProperty("statsOutboundUplink", true)
                addProperty("statsOutboundDownlink", true)
                addProperty("statsInboundUplink", true)
                addProperty("statsInboundDownlink", true)
            })
        })

        // ── DNS ──────────────────────────────────────────────────────────────
        root.add("dns", JsonObject().apply {
            addProperty("queryStrategy", "UseIP")
            addProperty("disableCache", false)
            addProperty("disableFallback", false)
            addProperty("tag", "dns-out")
            val servers = JsonArray()
            // Primary: Cloudflare (fast, reliable)
            servers.add(JsonObject().apply {
                addProperty("address", "1.1.1.1")
                addProperty("skipFallback", false)
            })
            // Fallback DNS servers
            servers.add("8.8.8.8")
            servers.add("8.8.4.4")
            servers.add("1.0.0.1")
            add("servers", servers)
        })

        // ── Inbounds ─────────────────────────────────────────────────────────
        val inbounds = JsonArray()

        // HTTP proxy inbound — always present for proxy share & stats
        inbounds.add(JsonObject().apply {
            addProperty("tag", "http-in")
            addProperty("port", httpPort)
            addProperty("listen", if (proxyShareEnabled) "0.0.0.0" else "127.0.0.1")
            addProperty("protocol", "http")
            add("settings", JsonObject().apply {
                addProperty("allowTransparent", true)
                addProperty("userLevel", 8)
            })
            add("sniffing", JsonObject().apply {
                addProperty("enabled", true)
                val dest = JsonArray()
                dest.add("http"); dest.add("tls"); dest.add("quic")
                add("destOverride", dest)
            })
        })

        // TUN inbound — driven by startLoop(config, tunFd)
        inbounds.add(JsonObject().apply {
            addProperty("tag", "tun-in")
            addProperty("protocol", "tun")
            addProperty("port", 0)
            add("settings", JsonObject().apply {
                addProperty("mtu", 8500)
                addProperty("userLevel", 8)
            })
            add("sniffing", JsonObject().apply {
                addProperty("enabled", true)
                val dest = JsonArray()
                dest.add("http"); dest.add("tls"); dest.add("quic")
                add("destOverride", dest)
                addProperty("routeOnly", true)      // Only use sniffed domain for routing, don't override dest
                addProperty("metadataOnly", false)
            })
        })

        root.add("inbounds", inbounds)

        // ── Outbounds ────────────────────────────────────────────────────────
        val outbounds = JsonArray()
        outbounds.add(buildProxyOutbound(server))
        outbounds.add(JsonObject().apply {
            addProperty("tag", "direct")
            addProperty("protocol", "freedom")
            add("settings", JsonObject().apply {
                addProperty("domainStrategy", "AsIs")
            })
        })
        outbounds.add(JsonObject().apply {
            addProperty("tag", "block")
            addProperty("protocol", "blackhole")
        })
        // DNS outbound — handles intercepted DNS queries
        outbounds.add(JsonObject().apply {
            addProperty("tag", "dns-out")
            addProperty("protocol", "dns")
        })
        root.add("outbounds", outbounds)

        // ── Routing ──────────────────────────────────────────────────────────
        root.add("routing", JsonObject().apply {
            addProperty("domainStrategy", "IPIfNonMatch")
            val rules = JsonArray()

            // DNS hijack — route all DNS queries to Xray's internal DNS
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                addProperty("outboundTag", "dns-out")
                val inTag = JsonArray()
                inTag.add("tun-in")
                add("inboundTag", inTag)
                addProperty("port", "53")
            })

            // Private IPs direct
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                addProperty("outboundTag", "direct")
                val ip = JsonArray()
                ip.add("10.0.0.0/8")
                ip.add("172.16.0.0/12")
                ip.add("192.168.0.0/16")
                ip.add("127.0.0.0/8")
                ip.add("169.254.0.0/16")
                ip.add("fc00::/7")
                add("ip", ip)
            })

            // Server address direct — prevents routing loop
            if (server.address.isNotBlank()) {
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    addProperty("outboundTag", "direct")
                    val arr = JsonArray()
                    arr.add(server.address)
                    if (server.address.matches(Regex("[\\d.:]+(/\\d+)?"))) {
                        add("ip", arr)
                    } else {
                        add("domain", arr)
                    }
                })
            }

            add("rules", rules)
        })

        return gson.toJson(root)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OUTBOUND BUILDER
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildProxyOutbound(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("tag", "proxy")

        when (s.protocol.lowercase()) {
            "vmess"             -> buildVmess(this, s)
            "vless"             -> buildVless(this, s)
            "trojan"            -> buildTrojan(this, s)
            "shadowsocks", "ss" -> buildShadowsocks(this, s)
            "hysteria2", "hy2"  -> buildHysteria2(this, s)
            else                -> buildVless(this, s) // fallback
        }

        // Stream settings (not needed for hysteria2)
        if (s.protocol.lowercase() !in listOf("hysteria2", "hy2")) {
            add("streamSettings", buildStreamSettings(s))
        }

        // Mux — disable for XTLS flow and hysteria2
        val hasXtls = s.flow.isNotBlank() && s.flow.contains("xtls", true)
        if (s.protocol.lowercase() !in listOf("hysteria2", "hy2") && !hasXtls) {
            add("mux", JsonObject().apply {
                addProperty("enabled", false)
                addProperty("concurrency", -1)
            })
        }
    }

    // ── VMess ─────────────────────────────────────────────────────────────────

    private fun buildVmess(obj: JsonObject, s: ServerConfig) {
        obj.addProperty("protocol", "vmess")
        obj.add("settings", JsonObject().apply {
            val vnext = JsonArray()
            vnext.add(JsonObject().apply {
                addProperty("address", s.address)
                addProperty("port", s.port)
                val users = JsonArray()
                users.add(JsonObject().apply {
                    addProperty("id", s.uuid)
                    addProperty("alterId", s.alterId)
                    addProperty("security", s.security.ifBlank { "auto" })
                    addProperty("level", 8)
                })
                add("users", users)
            })
            add("vnext", vnext)
        })
    }

    // ── VLESS ─────────────────────────────────────────────────────────────────

    private fun buildVless(obj: JsonObject, s: ServerConfig) {
        obj.addProperty("protocol", "vless")
        obj.add("settings", JsonObject().apply {
            val vnext = JsonArray()
            vnext.add(JsonObject().apply {
                addProperty("address", s.address)
                addProperty("port", s.port)
                val users = JsonArray()
                users.add(JsonObject().apply {
                    addProperty("id", s.uuid)
                    addProperty("encryption", "none")
                    addProperty("level", 8)
                    if (s.flow.isNotBlank()) addProperty("flow", s.flow)
                })
                add("users", users)
            })
            add("vnext", vnext)
        })
    }

    // ── Trojan ────────────────────────────────────────────────────────────────

    private fun buildTrojan(obj: JsonObject, s: ServerConfig) {
        obj.addProperty("protocol", "trojan")
        obj.add("settings", JsonObject().apply {
            val servers = JsonArray()
            servers.add(JsonObject().apply {
                addProperty("address", s.address)
                addProperty("port", s.port)
                addProperty("password", s.password)
                addProperty("level", 8)
                if (s.flow.isNotBlank()) addProperty("flow", s.flow)
            })
            add("servers", servers)
        })
    }

    // ── Shadowsocks ───────────────────────────────────────────────────────────

    private fun buildShadowsocks(obj: JsonObject, s: ServerConfig) {
        obj.addProperty("protocol", "shadowsocks")
        obj.add("settings", JsonObject().apply {
            val servers = JsonArray()
            servers.add(JsonObject().apply {
                addProperty("address", s.address)
                addProperty("port", s.port)
                addProperty("method", s.method.ifBlank { "aes-128-gcm" })
                addProperty("password", s.password)
                addProperty("level", 8)
            })
            add("servers", servers)
        })
    }

    // ── Hysteria2 ─────────────────────────────────────────────────────────────

    private fun buildHysteria2(obj: JsonObject, s: ServerConfig) {
        obj.addProperty("protocol", "hysteria2")
        obj.add("settings", JsonObject().apply {
            val servers = JsonArray()
            servers.add(JsonObject().apply {
                addProperty("address", s.address)
                addProperty("port", s.port)
                addProperty("password", s.password)
            })
            add("servers", servers)
        })
        // Hysteria2 TLS settings
        obj.add("streamSettings", JsonObject().apply {
            addProperty("network", "hysteria2")
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                addProperty("allowInsecure", getAllowInsecure(s))
                val sni = resolveSni(s)
                if (sni.isNotBlank()) addProperty("serverName", sni)
                val fp = s.fingerprint.ifBlank { "chrome" }
                addProperty("fingerprint", fp)
                val alpn = JsonArray()
                alpn.add("h3")
                add("alpn", alpn)
            })
        })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STREAM SETTINGS BUILDER
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildStreamSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        val net = s.network.lowercase().ifBlank { "tcp" }
        val tls = s.tlsSecurity.lowercase()

        addProperty("network", net)
        addProperty("security", when (tls) {
            "tls" -> "tls"
            "reality" -> "reality"
            else -> "none"
        })

        // ── TLS Settings ─────────────────────────────────────────────────
        when (tls) {
            "tls" -> add("tlsSettings", buildTlsSettings(s))
            "reality" -> add("realitySettings", buildRealitySettings(s))
        }

        // ── Transport Settings ───────────────────────────────────────────
        when (net) {
            "ws" -> add("wsSettings", buildWsSettings(s))
            "grpc" -> add("grpcSettings", buildGrpcSettings(s))
            "h2", "http" -> add("httpSettings", buildH2Settings(s))
            "kcp", "mkcp" -> add("kcpSettings", buildKcpSettings(s))
            "quic" -> add("quicSettings", buildQuicSettings(s))
            "xhttp", "splithttp" -> add("xhttpSettings", buildXhttpSettings(s))
            "tcp" -> {
                val tcpSettings = buildTcpSettings(s)
                if (tcpSettings != null) add("tcpSettings", tcpSettings)
            }
        }
    }

    // ── TLS ───────────────────────────────────────────────────────────────────

    private fun buildTlsSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("allowInsecure", getAllowInsecure(s))

        val sni = resolveSni(s)
        if (sni.isNotBlank()) addProperty("serverName", sni)

        // Fingerprint — default to chrome for better compatibility
        val fp = s.fingerprint.ifBlank { "chrome" }
        addProperty("fingerprint", fp)

        // ALPN — parse from rawConfig URL params for accuracy
        val alpn = resolveAlpn(s)
        add("alpn", alpn)
    }

    // ── Reality ───────────────────────────────────────────────────────────────

    private fun buildRealitySettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("show", false)

        val fp = s.fingerprint.ifBlank { "chrome" }
        addProperty("fingerprint", fp)

        val sni = resolveSni(s)
        if (sni.isNotBlank()) addProperty("serverName", sni)
        if (s.publicKey.isNotBlank()) addProperty("publicKey", s.publicKey)
        if (s.shortId.isNotBlank()) addProperty("shortId", s.shortId)
        if (s.spiderX.isNotBlank()) addProperty("spiderX", s.spiderX)
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private fun buildWsSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("path", s.path.ifBlank { "/" })
        add("headers", JsonObject().apply {
            val host = s.requestHost.ifBlank { s.sni.ifBlank { s.address } }
            addProperty("Host", host)
        })
    }

    // ── gRPC ──────────────────────────────────────────────────────────────────

    private fun buildGrpcSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("serviceName", s.path.ifBlank { "" })
        addProperty("multiMode", s.headerType.equals("multi", true))
        addProperty("idle_timeout", 60)
        addProperty("health_check_timeout", 20)
    }

    // ── HTTP/2 ────────────────────────────────────────────────────────────────

    private fun buildH2Settings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("path", s.path.ifBlank { "/" })
        val hosts = JsonArray()
        val host = s.requestHost.ifBlank { s.sni.ifBlank { s.address } }
        host.split(",").forEach { hosts.add(it.trim()) }
        add("host", hosts)
    }

    // ── mKCP ──────────────────────────────────────────────────────────────────

    private fun buildKcpSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("mtu", 1350)
        addProperty("tti", 50)
        addProperty("uplinkCapacity", 12)
        addProperty("downlinkCapacity", 100)
        addProperty("congestion", false)
        addProperty("readBufferSize", 2)
        addProperty("writeBufferSize", 2)
        add("header", JsonObject().apply {
            addProperty("type", s.headerType.ifBlank { "none" })
        })
        if (s.path.isNotBlank()) addProperty("seed", s.path)
    }

    // ── QUIC ──────────────────────────────────────────────────────────────────

    private fun buildQuicSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("security", s.requestHost.ifBlank { "none" })
        addProperty("key", s.path)
        add("header", JsonObject().apply {
            addProperty("type", s.headerType.ifBlank { "none" })
        })
    }

    // ── xHTTP / splitHTTP ─────────────────────────────────────────────────────

    private fun buildXhttpSettings(s: ServerConfig): JsonObject = JsonObject().apply {
        addProperty("path", s.path.ifBlank { "/" })
        val host = s.requestHost.ifBlank { s.sni.ifBlank { s.address } }
        addProperty("host", host)
        addProperty("mode", "auto")
    }

    // ── TCP ───────────────────────────────────────────────────────────────────

    private fun buildTcpSettings(s: ServerConfig): JsonObject? {
        if (!s.headerType.equals("http", true)) return null
        return JsonObject().apply {
            add("header", JsonObject().apply {
                addProperty("type", "http")
                add("request", JsonObject().apply {
                    addProperty("method", "GET")
                    val paths = JsonArray()
                    s.path.ifBlank { "/" }.split(",").forEach { paths.add(it.trim()) }
                    add("path", paths)
                    add("headers", JsonObject().apply {
                        val hosts = JsonArray()
                        val host = s.requestHost.ifBlank { s.address }
                        host.split(",").forEach { hosts.add(it.trim()) }
                        add("Host", hosts)
                        // User-Agent for HTTP camouflage
                        val ua = JsonArray()
                        ua.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        add("User-Agent", ua)
                        val accept = JsonArray()
                        accept.add("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        add("Accept", accept)
                    })
                })
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Parse allowInsecure from rawConfig URL params.
     * Checks: allowInsecure=1, insecure=1
     */
    private fun getAllowInsecure(s: ServerConfig): Boolean {
        val raw = s.rawConfig
        if (raw.isBlank()) return false
        return try {
            val params = parseRawParams(raw)
            params["allowInsecure"] == "1" || params["insecure"] == "1"
        } catch (_: Exception) { false }
    }

    /**
     * Resolve SNI with proper fallback chain:
     * sni param > requestHost > address
     */
    private fun resolveSni(s: ServerConfig): String {
        return s.sni.ifBlank { s.requestHost.ifBlank { s.address } }
    }

    /**
     * Parse ALPN from rawConfig URL parameters.
     * Falls back to sensible defaults per protocol/network.
     */
    private fun resolveAlpn(s: ServerConfig): JsonArray {
        val alpn = JsonArray()
        val alpnStr = try {
            val params = parseRawParams(s.rawConfig)
            params["alpn"] ?: ""
        } catch (_: Exception) { "" }

        if (alpnStr.isNotBlank()) {
            // Use exactly what the config specifies
            alpnStr.split(",").forEach { alpn.add(it.trim()) }
        } else {
            // Sensible defaults based on network type
            when (s.network.lowercase()) {
                "h2", "http" -> { alpn.add("h2") }
                "grpc" -> { alpn.add("h2") }
                else -> { alpn.add("h2"); alpn.add("http/1.1") }
            }
        }
        return alpn
    }

    /**
     * Parse query parameters from raw config URI.
     */
    private fun parseRawParams(raw: String): Map<String, String> {
        val qIdx = raw.indexOf('?')
        if (qIdx < 0) return emptyMap()
        val query = raw.substring(qIdx + 1).substringBefore('#')
        return query.split('&').mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null
            else it.substring(0, eq) to try {
                java.net.URLDecoder.decode(it.substring(eq + 1), "UTF-8")
            } catch (_: Exception) { it.substring(eq + 1) }
        }.toMap()
    }
}
