package com.hdnteam.cloudlinevpn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_configs")
data class ServerConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriptionId: Long = 0,
    val name: String = "",
    val protocol: String = "",   // vmess, vless, trojan, ss, etc.
    val address: String = "",
    val port: Int = 443,
    val uuid: String = "",
    val alterId: Int = 0,
    val security: String = "auto",
    val network: String = "tcp",  // tcp, ws, grpc, xhttp
    val headerType: String = "",
    val requestHost: String = "",
    val path: String = "",
    val tlsSecurity: String = "",  // tls, reality, none
    val sni: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",     // Reality public key
    val shortId: String = "",       // Reality short id
    val spiderX: String = "",       // Reality spiderX
    val flow: String = "",          // XTLS flow
    val password: String = "",      // Trojan/SS password
    val method: String = "",        // SS encryption method
    val rawConfig: String = "",     // original uri/json
    val latency: Long = -1,         // ms, -1 = not tested
    val isSelected: Boolean = false,
    val sortOrder: Int = 0
)
