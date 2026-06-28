package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPerApp: () -> Unit = {},
    onNavigateToProxyApp: () -> Unit = {},
    onNavigateToLog: () -> Unit = {}
) {
    val proxyShare     by viewModel.proxyShareEnabled.collectAsState()
    val isUpdating     by viewModel.isUpdating.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val message        by viewModel.message.collectAsState()
    val assetVersion   by viewModel.assetVersion.collectAsState(initial = "نامشخص")
    val appVersion     by viewModel.appVersion.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message.isNotBlank()) { snackbar.showSnackbar(message); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
                .padding(pad)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // ── Header ───────────────────────────────────────────────
                Text(
                    "تنظیمات",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CloudWhite,
                    fontWeight = FontWeight.Bold
                )
                Text("CloudLine VPN", style = MaterialTheme.typography.bodySmall, color = CloudAccent)

                Spacer(Modifier.height(28.dp))

                // ── Per-App Routing Section ───────────────────────────────
                SettingsSectionHeader(icon = Icons.Default.Route, title = "مسیریابی برنامه‌ها", color = CloudAccent)
                Spacer(Modifier.height(10.dp))

                SettingsCard(
                    icon = Icons.Default.VpnLock,
                    iconColor = CloudRed,
                    title = "برنامه‌هایی که از وی‌پی‌ان رد نشن",
                    subtitle = "با انتخاب این برنامه‌ها، هنگام روشن بودن وی‌پی‌ان از آن استفاده نمی‌کنند",
                    onClick = onNavigateToPerApp
                )

                Spacer(Modifier.height(8.dp))

                SettingsCard(
                    icon = Icons.Default.VpnLock,
                    iconColor = CloudGreen,
                    title = "برنامه‌هایی که از وی‌پی‌ان رد بشن",
                    subtitle = "با انتخاب این برنامه‌ها، فقط همین برنامه‌ها از وی‌پی‌ان استفاده می‌کنند",
                    onClick = onNavigateToProxyApp
                )

                Spacer(Modifier.height(24.dp))

                // ── Proxy Share Section ──────────────────────────────────
                SettingsSectionHeader(icon = Icons.Default.Wifi, title = "اشتراک‌گذاری پروکسی", color = CloudAccent)
                Spacer(Modifier.height(10.dp))

                Surface(shape = RoundedCornerShape(16.dp), color = CloudCard, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("HTTP Proxy Share", style = MaterialTheme.typography.bodyMedium,
                                    color = CloudWhite, fontWeight = FontWeight.SemiBold)
                                Text("اینترنت وی‌پی‌ان را با دستگاه‌های دیگر به اشتراک بگذارید",
                                    style = MaterialTheme.typography.bodySmall, color = CloudGray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = proxyShare,
                                onCheckedChange = { viewModel.setProxyShare(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CloudAccent,
                                    uncheckedThumbColor = CloudGray,
                                    uncheckedTrackColor = CloudSurface
                                )
                            )
                        }
                        if (proxyShare) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = CloudGray.copy(0.1f))
                            Spacer(Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CloudDarkBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Host:", style = MaterialTheme.typography.bodySmall,
                                            color = CloudGray, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(12.dp))
                                        Text("10.223.64.115", style = MaterialTheme.typography.bodyMedium,
                                            color = CloudAccent, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Port:", style = MaterialTheme.typography.bodySmall,
                                            color = CloudGray, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(14.dp))
                                        Text("8080", style = MaterialTheme.typography.bodyMedium,
                                            color = CloudAccent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CloudOrange.copy(0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, null, tint = CloudOrange, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "بعد از فعال/غیرفعال کردن، وی‌پی‌ان را قطع و وصل کنید\nWiFi → تنظیمات پیشرفته → پروکسی → دستی",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloudGray, fontSize = 10.sp, lineHeight = 16.sp
                                    )
                                }
                            }

                            // Traffic indicator for proxy share
                            val proxyTraffic by viewModel.proxyShareTraffic.collectAsState()
                            if (proxyTraffic.first > 0 || proxyTraffic.second > 0) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = CloudGray.copy(0.1f))
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDownward, null, tint = CloudGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("↓ ${formatProxyBytes(proxyTraffic.first)}",
                                            style = MaterialTheme.typography.bodySmall, color = CloudGreen)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowUpward, null, tint = CloudAccent, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("↑ ${formatProxyBytes(proxyTraffic.second)}",
                                            style = MaterialTheme.typography.bodySmall, color = CloudAccent)
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = CloudGreen.copy(0.15f)) {
                                        Text(" فعال ", style = MaterialTheme.typography.labelSmall,
                                            color = CloudGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── App Update Section ───────────────────────────────────
                SettingsSectionHeader(icon = Icons.Default.SystemUpdate, title = "بروزرسانی برنامه", color = CloudAccent)
                Spacer(Modifier.height(10.dp))

                Surface(shape = RoundedCornerShape(16.dp), color = CloudCard, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("نسخه برنامه", style = MaterialTheme.typography.bodySmall, color = CloudGray)
                                Text(
                                    "v$appVersion",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CloudAccent, fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("هسته Xray", style = MaterialTheme.typography.bodySmall, color = CloudGray)
                                Text(assetVersion, style = MaterialTheme.typography.bodySmall,
                                    color = CloudGray, fontSize = 10.sp, maxLines = 2)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = CloudGray.copy(0.1f))
                        Spacer(Modifier.height(10.dp))

                        Text("بروزرسانی خودکار روزانه (هسته Xray، geo files)",
                            style = MaterialTheme.typography.bodySmall, color = CloudGray)

                        if (updateProgress.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = CloudAccent.copy(0.08f),
                                modifier = Modifier.fillMaxWidth()) {
                                Text(updateProgress, style = MaterialTheme.typography.bodySmall,
                                    color = CloudAccent, modifier = Modifier.padding(10.dp), fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.checkForUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isUpdating,
                            colors = ButtonDefaults.buttonColors(containerColor = CloudAccent)
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("در حال بررسی…", color = Color.White)
                            } else {
                                Icon(Icons.Default.SystemUpdate, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("بررسی و بروزرسانی", color = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Developer Tools ──────────────────────────────────────
                SettingsSectionHeader(icon = Icons.Default.DeveloperMode, title = "ابزارها", color = CloudOrange)
                Spacer(Modifier.height(10.dp))

                SettingsCard(
                    icon = Icons.Default.BugReport,
                    iconColor = CloudOrange,
                    title = "لاگ برنامه",
                    subtitle = "مشاهده و کپی لاگ‌های اتصال و خطاها",
                    onClick = onNavigateToLog
                )

                Spacer(Modifier.height(24.dp))

                // ── Debug (crash log) ─────────────────────────────────────
                val lastCrash = viewModel.getLastCrash()
                if (lastCrash != null) {
                    SettingsSectionHeader(icon = Icons.Default.Warning, title = "آخرین خطا", color = CloudRed)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CloudRed.copy(0.06f),
                        border = BorderStroke(1.dp, CloudRed.copy(0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastCrash.take(800),
                            style = MaterialTheme.typography.bodySmall,
                            color = CloudRed.copy(0.9f),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text("POWERED BY HDNTEAM", style = MaterialTheme.typography.labelSmall,
                    color = CloudGray.copy(0.3f), letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ── Reusable Components ──────────────────────────────────────────────────────

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CloudCard,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    color = CloudWhite, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = CloudGray, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = CloudGray.copy(0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = CloudAccent,
        letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
}

private fun formatProxyBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L     -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L         -> "%.0f KB".format(bytes / 1_000.0)
    else                    -> "$bytes B"
}
