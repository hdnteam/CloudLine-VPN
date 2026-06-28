package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.ConnectStep
import com.hdnteam.cloudlinevpn.ui.viewmodel.HomeViewModel
import com.hdnteam.cloudlinevpn.vpn.VpnConnectionState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestVpnPermission: (android.content.Intent) -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trafficStats    by viewModel.trafficStats.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val connectStep     by viewModel.connectStep.collectAsState()
    val statusMessage   by viewModel.statusMessage.collectAsState()
    val servers         by viewModel.servers.collectAsState()

    // Connection mode: auto or manual
    var isAutoMode by remember { mutableStateOf(true) }
    // Selected server for manual mode
    var manualServerId by remember { mutableStateOf<Long?>(null) }
    val manualServer = servers.find { it.id == manualServerId } ?: servers.firstOrNull()

    val isConnected = connectionState == VpnConnectionState.CONNECTED
    val isBusy      = isLoading || connectionState == VpnConnectionState.CONNECTING

    fun doConnect() {
        val permIntent = viewModel.requestVpnPermission()
        if (permIntent != null) {
            onRequestVpnPermission(permIntent)
        } else if (isAutoMode) {
            viewModel.connectAutomatic()
        } else {
            val server = manualServer
            if (server != null) viewModel.connectToServer(server)
            else viewModel.connectAutomatic()
        }
    }

    // Pulse animation when connected
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35), CloudDarkBlue)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Brand ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, tint = CloudAccent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("CloudLine", style = MaterialTheme.typography.headlineMedium, color = CloudAccent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("VPN", style = MaterialTheme.typography.headlineMedium, color = CloudWhite, fontWeight = FontWeight.Light)
            }

            Spacer(Modifier.height(6.dp))

            // ── Status pill ────────────────────────────────────────────────
            AnimatedContent(
                targetState = connectionState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "statusPill"
            ) { state ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when (state) {
                        VpnConnectionState.CONNECTED  -> CloudGreen.copy(.15f)
                        VpnConnectionState.CONNECTING -> CloudOrange.copy(.15f)
                        VpnConnectionState.ERROR      -> CloudRed.copy(.15f)
                        else -> CloudGray.copy(.1f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                color = when (state) {
                                    VpnConnectionState.CONNECTED  -> CloudGreen
                                    VpnConnectionState.CONNECTING -> CloudOrange
                                    VpnConnectionState.ERROR      -> CloudRed
                                    else -> CloudGray
                                },
                                shape = CircleShape
                            )
                        )
                        Text(
                            text = when (state) {
                                VpnConnectionState.CONNECTED  -> "Connected"
                                VpnConnectionState.CONNECTING -> "Connecting..."
                                VpnConnectionState.ERROR      -> "Connection Error"
                                else -> "Not Connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state) {
                                VpnConnectionState.CONNECTED  -> CloudGreen
                                VpnConnectionState.CONNECTING -> CloudOrange
                                VpnConnectionState.ERROR      -> CloudRed
                                else -> CloudGray
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Mode toggle: Auto / Manual ─────────────────────────────────
            AnimatedVisibility(visible = !isConnected && !isBusy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(50), color = CloudCard) {
                        Row(Modifier.padding(3.dp)) {
                            listOf(true to ("خودکار" to Icons.Default.Bolt),
                                   false to ("دستی" to Icons.Default.Tune)).forEach { (auto, pair) ->
                                val (label, icon) = pair
                                val sel = isAutoMode == auto
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (sel) CloudAccent else Color.Transparent,
                                    modifier = Modifier.clickable { isAutoMode = auto }
                                ) {
                                    Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, tint = if (sel) Color.White else CloudGray,
                                            modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(label, style = MaterialTheme.typography.bodySmall,
                                            color = if (sel) Color.White else CloudGray,
                                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }

                    // Manual mode server picker
                    AnimatedVisibility(visible = !isAutoMode && servers.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(10.dp))
                            Text("انتخاب سرور:",
                                style = MaterialTheme.typography.bodySmall, color = CloudGray,
                                modifier = Modifier.padding(start = 20.dp))
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(servers) { server ->
                                    val isSel = server.id == (manualServerId ?: servers.firstOrNull()?.id)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) CloudAccent.copy(0.12f) else CloudCard,
                                        border = BorderStroke(
                                            if (isSel) 1.5.dp else 0.5.dp,
                                            if (isSel) CloudAccent else CloudGray.copy(0.2f)
                                        ),
                                        modifier = Modifier.clickable { manualServerId = server.id }.width(130.dp)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(server.name.ifBlank { server.address },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSel) CloudAccent else CloudWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
                                            Text(server.protocol.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSel) CloudAccent.copy(0.7f) else CloudGray,
                                                fontSize = 9.sp)
                                            if (server.latency > 0) {
                                                val lc = when { server.latency < 100 -> CloudGreen; server.latency < 300 -> CloudOrange; else -> CloudRed }
                                                Text("${server.latency}ms", style = MaterialTheme.typography.labelSmall, color = lc, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Main power button ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(if (isConnected) pulseScale else 1f),
                contentAlignment = Alignment.Center
            ) {
                // Glow
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            Brush.radialGradient(
                                if (isConnected) listOf(CloudGreen.copy(.2f), Color.Transparent)
                                else listOf(CloudAccent.copy(.08f), Color.Transparent)
                            ),
                            RoundedCornerShape(40.dp)
                        )
                )
                // Button
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = CloudCard,
                    border = BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            when {
                                isConnected -> listOf(CloudGreen, CloudGreen.copy(.4f))
                                isBusy      -> listOf(CloudOrange, CloudOrange.copy(.4f))
                                else        -> listOf(CloudAccent, CloudAccent.copy(.2f))
                            }
                        )
                    ),
                    modifier = Modifier.size(184.dp).clickable(enabled = true) {
                        if (isConnected) {
                            viewModel.disconnect()
                        } else if (isBusy) {
                            viewModel.cancelConnect()
                        } else {
                            doConnect()
                        }
                    }                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isBusy -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(52.dp),
                                        color = CloudOrange, strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = when (connectStep) {
                                            ConnectStep.UPDATING_SUB    -> "بروزرسانی\nسرورها…"
                                            ConnectStep.TESTING_LATENCY -> "تست\nپینگ…"
                                            ConnectStep.CONNECTING      -> "در حال اتصال…"
                                            else -> "لطفاً صبر کنید…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CloudOrange,
                                        textAlign = TextAlign.Center,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            isConnected -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.VpnLock, null, tint = CloudGreen, modifier = Modifier.size(68.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("قطع\nاتصال", style = MaterialTheme.typography.labelSmall,
                                        color = CloudGreen, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center, letterSpacing = 1.sp)
                                }
                            }
                            else -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.VpnKey, null, tint = CloudAccent, modifier = Modifier.size(68.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("اتصال", style = MaterialTheme.typography.labelSmall,
                                        color = CloudAccent, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("انتخاب خودکار بهترین سرور", style = MaterialTheme.typography.labelSmall,
                                        color = CloudGray.copy(.7f), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Step indicator ─────────────────────────────────────────────
            AnimatedVisibility(visible = isBusy, enter = fadeIn(), exit = fadeOut()) {
                ConnectStepIndicator(connectStep)
            }

            // ── Status message ─────────────────────────────────────────────
            AnimatedVisibility(visible = statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CloudGray, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Connected server card ──────────────────────────────────────
            AnimatedVisibility(
                visible = isConnected && connectedServer != null,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                connectedServer?.let { server ->
                    Surface(shape = RoundedCornerShape(16.dp), color = CloudCard, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(10.dp), color = CloudAccent.copy(.15f), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Storage, null, tint = CloudAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.name.ifBlank { server.address },
                                    style = MaterialTheme.typography.bodyMedium, color = CloudWhite,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${server.protocol.uppercase()} · ${server.address}",
                                    style = MaterialTheme.typography.bodySmall, color = CloudGray, maxLines = 1)
                            }
                            if (server.latency > 0) LatencyBadge(server.latency)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Traffic cards ──────────────────────────────────────────────
            AnimatedVisibility(visible = isConnected, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrafficCard(Modifier.weight(1f), "↓  Download", trafficStats.downloadSpeed, trafficStats.totalDownload, CloudGreen)
                        TrafficCard(Modifier.weight(1f), "↑  Upload",   trafficStats.uploadSpeed,   trafficStats.totalUpload,   CloudAccent)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Session usage card — professional design
                    val sessionUsage by viewModel.sessionUsage.collectAsState()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, CloudOrange.copy(0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CloudOrange.copy(0.08f), CloudCard, CloudAccent.copy(0.06f))
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon with glow background
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CloudOrange.copy(0.15f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.BarChart, null,
                                            tint = CloudOrange, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("کل مصرف این اتصال",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloudGray, letterSpacing = 0.5.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatBytes(sessionUsage),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = CloudOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // Upload/Download mini indicators
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDownward, null,
                                            tint = CloudGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(formatBytes(trafficStats.totalDownload),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloudGreen, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowUpward, null,
                                            tint = CloudAccent, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(formatBytes(trafficStats.totalUpload),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloudAccent, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Support box ───────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CloudCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/hdnteam"))
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        viewModel.getApplication<android.app.Application>().startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF229ED9).copy(0.15f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.HeadsetMic, null, tint = Color(0xFF229ED9), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ارتباط با پشتیبانی", style = MaterialTheme.typography.bodyMedium,
                            color = CloudWhite, fontWeight = FontWeight.SemiBold)
                        Text("@hdnteam", style = MaterialTheme.typography.bodySmall, color = CloudGray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = CloudGray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Channel box ──────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CloudCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/vpnxub"))
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        viewModel.getApplication<android.app.Application>().startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = CloudAccent.copy(0.15f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Campaign, null, tint = CloudAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("کانال اطلاع‌رسانی وضعیت کانفیگ‌ها", style = MaterialTheme.typography.bodyMedium,
                            color = CloudWhite, fontWeight = FontWeight.SemiBold)
                        Text("@vpnxub", style = MaterialTheme.typography.bodySmall, color = CloudGray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = CloudGray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("POWERED BY HDNTEAM", style = MaterialTheme.typography.labelSmall,
                color = CloudGray.copy(.4f), letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Step indicator ─────────────────────────────────────────────────────────────

@Composable
fun ConnectStepIndicator(step: ConnectStep) {
    val steps = listOf(
        ConnectStep.UPDATING_SUB    to "بروزرسانی",
        ConnectStep.TESTING_LATENCY to "تست پینگ",
        ConnectStep.CONNECTING      to "اتصال"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (s, label) ->
            val isActive = s == step
            val isDone = steps.indexOfFirst { it.first == step } > index

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isActive -> CloudOrange
                        isDone   -> CloudGreen
                        else     -> CloudGray.copy(.2f)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDone) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Color.White else CloudGray)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) CloudOrange else if (isDone) CloudGreen else CloudGray.copy(.5f),
                    fontSize = 9.sp)
            }

            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(0.5f).height(1.dp).background(
                    if (isDone) CloudGreen else CloudGray.copy(.2f)
                ))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ── Reusable components ────────────────────────────────────────────────────────

@Composable
fun TrafficCard(modifier: Modifier = Modifier, label: String, speed: Long, total: Long, color: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = CloudCard) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = CloudGray, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(formatSpeed(speed), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text("Total: ${formatBytes(total)}", style = MaterialTheme.typography.bodySmall, color = CloudGray.copy(.7f), fontSize = 10.sp)
        }
    }
}

@Composable
fun LatencyBadge(latency: Long) {
    val color = when {
        latency < 100 -> CloudGreen
        latency < 300 -> CloudOrange
        else          -> CloudRed
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(.15f)) {
        Text("${latency}ms", style = MaterialTheme.typography.bodySmall, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000     -> "%.0f KB/s".format(bytesPerSec / 1_000.0)
    else                     -> "$bytesPerSec B/s"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L     -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L         -> "%.0f KB".format(bytes / 1_000.0)
    else                    -> "$bytes B"
}
