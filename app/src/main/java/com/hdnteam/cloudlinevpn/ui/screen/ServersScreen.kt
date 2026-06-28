package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.ServersViewModel
import com.hdnteam.cloudlinevpn.vpn.CloudLineVpnService
import com.hdnteam.cloudlinevpn.vpn.VpnConnectionState

@Composable
fun ServersScreen(
    viewModel: ServersViewModel = hiltViewModel(),
    onConnectServer: (ServerConfig) -> Unit,
    onEditServer: (ServerConfig) -> Unit = {}
) {
    val servers       by viewModel.servers.collectAsState()
    val groupedServers by viewModel.groupedServers.collectAsState()
    val subscriptions  by viewModel.subscriptions.collectAsState()
    val testingIds    by viewModel.testingIds.collectAsState()
    val isTestingAll  by viewModel.isTestingAll.collectAsState()
    val isRefreshing  by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    // Currently connected server from VPN service
    val connectedServer by CloudLineVpnService.connectedServer.collectAsState()
    val connectionState by CloudLineVpnService.connectionState.collectAsState()
    val isConnected = connectionState == VpnConnectionState.CONNECTED

    // Local selected server state (highlighted but not yet connected)
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selectedServer = servers.find { it.id == selectedId }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Professional Header ─────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "سرورها",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CloudWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isConnected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(CloudGreen, CircleShape)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        if (isConnected) "متصل · ${servers.size} سرور"
                                        else "${servers.size} سرور موجود",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isConnected) CloudGreen else CloudGray
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Action buttons row - professional chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Refresh subscription button
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = CloudCard,
                                border = BorderStroke(1.dp, CloudAccent.copy(0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable(enabled = !isRefreshing) { viewModel.refreshServers() }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isRefreshing)
                                        CircularProgressIndicator(Modifier.size(14.dp), color = CloudAccent, strokeWidth = 2.dp)
                                    else
                                        Icon(Icons.Default.Sync, null, Modifier.size(16.dp), tint = CloudAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isRefreshing) "بروزرسانی…" else "بروزرسانی",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CloudWhite
                                    )
                                }
                            }

                            // Test all latency button
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = CloudCard,
                                border = BorderStroke(1.dp, CloudGreen.copy(0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable(enabled = !isTestingAll && !isRefreshing && servers.isNotEmpty()) {
                                            viewModel.testAllLatency()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isTestingAll)
                                        CircularProgressIndicator(Modifier.size(14.dp), color = CloudGreen, strokeWidth = 2.dp)
                                    else
                                        Icon(Icons.Default.Speed, null, Modifier.size(16.dp), tint = CloudGreen)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isTestingAll) "تست پینگ…" else "تست پینگ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CloudWhite
                                    )
                                }
                            }
                        }

                        // Currently connected server banner
                        if (isConnected && connectedServer != null) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CloudGreen.copy(0.08f),
                                border = BorderStroke(1.dp, CloudGreen.copy(0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(CloudGreen, CircleShape)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "سرور فعال",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloudGreen,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            connectedServer?.name?.ifBlank { connectedServer?.address } ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloudWhite,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CloudGreen.copy(0.15f)
                                    ) {
                                        Text(
                                            connectedServer?.protocol?.uppercase() ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloudGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CloudGray.copy(0.1f))

                // ── Server list ──────────────────────────────────────────
                if (servers.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, null, tint = CloudGray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("سروری موجود نیست", style = MaterialTheme.typography.titleMedium, color = CloudGray)
                            Text("در حساب کاربری لینک اشتراک اضافه کنید",
                                style = MaterialTheme.typography.bodySmall, color = CloudGray.copy(.6f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Group by subscription
                        groupedServers.forEach { (subId, subServers) ->
                            val sub = subscriptions.find { it.id == subId }
                            val subName = sub?.alias?.ifBlank { sub.url.take(30) } ?: "اشتراک $subId"

                            // Subscription header
                            item(key = "header_$subId") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = CloudGray.copy(0.15f))
                                    Spacer(Modifier.width(10.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = CloudAccent.copy(0.08f)) {
                                        Text(
                                            "$subName (${subServers.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloudAccent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = CloudGray.copy(0.15f))
                                }
                            }

                            // Servers under this subscription
                            items(subServers, key = { it.id }) { server ->
                                val isHighlighted = server.id == selectedId
                                val isActive = isConnected && connectedServer?.id == server.id
                                ServerItem(
                                    server        = server,
                                    isHighlighted = isHighlighted,
                                    isActive      = isActive,
                                    isTesting     = server.id in testingIds,
                                    onTap = {
                                        selectedId = if (selectedId == server.id) null else server.id
                                        viewModel.selectServer(server)
                                    },
                                    onTestLatency = { viewModel.testLatency(server) },
                                    onEdit        = { onEditServer(server) },
                                    onConnectDirect = {
                                        selectedId = server.id
                                        viewModel.selectServer(server)
                                        onConnectServer(server)
                                    }
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(if (selectedServer != null) 120.dp else 32.dp))
                            Text(
                                "POWERED BY HDNTEAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = CloudGray.copy(.3f),
                                letterSpacing = 2.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            // ── Floating Connect Button ──────────────────────────────────
            AnimatedVisibility(
                visible = selectedServer != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                selectedServer?.let { server ->
                    val isActiveServer = isConnected && connectedServer?.id == server.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloudCard,
                        border = BorderStroke(1.dp, if (isActiveServer) CloudGreen else CloudAccent.copy(0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Server info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    server.name.ifBlank { server.address },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CloudWhite, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(server.protocol.uppercase(),
                                        style = MaterialTheme.typography.bodySmall, color = CloudAccent)
                                    if (server.latency > 0) LatencyBadge(server.latency)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            // Cancel selection
                            IconButton(onClick = { selectedId = null }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Close, "لغو", tint = CloudGray, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            // CONNECT / SWITCH button
                            Button(
                                onClick = { onConnectServer(server) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActiveServer) CloudGreen.copy(0.3f) else CloudGreen
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    if (isActiveServer) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                    null, tint = if (isActiveServer) CloudGreen else Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isActiveServer) "متصل" else if (isConnected) "سوئیچ" else "اتصال",
                                    color = if (isActiveServer) CloudGreen else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: ServerConfig,
    isHighlighted: Boolean,
    isActive: Boolean,
    isTesting: Boolean,
    onTap: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit = {},
    onConnectDirect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isActive -> CloudGreen.copy(0.06f)
            isHighlighted -> CloudAccent.copy(0.06f)
            else -> CloudCard.copy(.7f)
        },
        border = when {
            isActive -> BorderStroke(1.5.dp, CloudGreen.copy(0.7f))
            isHighlighted -> BorderStroke(1.5.dp, CloudAccent.copy(0.6f))
            else -> BorderStroke(1.dp, CloudGray.copy(0.08f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // Protocol badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    isActive -> CloudGreen.copy(.15f)
                    isHighlighted -> CloudAccent.copy(.15f)
                    else -> CloudGray.copy(.08f)
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        server.protocol.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isActive -> CloudGreen
                            isHighlighted -> CloudAccent
                            else -> CloudGray
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Server info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(CloudGreen, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        server.name.ifBlank { server.address },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isActive -> CloudGreen
                            isHighlighted -> CloudWhite
                            else -> CloudWhite.copy(.85f)
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isActive || isHighlighted) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${server.address}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloudGray.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· فعال",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloudGreen,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Latency
            if (isTesting) {
                CircularProgressIndicator(Modifier.size(20.dp), color = CloudAccent, strokeWidth = 2.dp)
            } else {
                Box(Modifier.clickable(onClick = onTestLatency), contentAlignment = Alignment.Center) {
                    if (server.latency > 0) LatencyBadge(server.latency)
                    else if (server.latency < 0) {
                        Surface(shape = RoundedCornerShape(6.dp), color = CloudRed.copy(.1f)) {
                            Text(
                                "✕",
                                color = CloudRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(6.dp), color = CloudGray.copy(.08f)) {
                            Icon(Icons.Default.Speed, "پینگ", tint = CloudGray.copy(0.5f),
                                modifier = Modifier.padding(5.dp).size(14.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Direct connect (play)
            FilledIconButton(
                onClick = onConnectDirect,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = when {
                        isActive -> CloudGreen.copy(0.2f)
                        else -> CloudAccent.copy(.1f)
                    }
                )
            ) {
                Icon(
                    if (isActive) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = "اتصال",
                    tint = if (isActive) CloudGreen else CloudAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
