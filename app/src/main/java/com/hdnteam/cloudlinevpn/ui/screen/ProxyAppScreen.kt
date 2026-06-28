package com.hdnteam.cloudlinevpn.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.dataStore
import com.hdnteam.cloudlinevpn.vpn.VpnStateManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Proxy-only app screen — only selected apps will use VPN.
 * All other apps connect directly without VPN.
 */
@Composable
fun ProxyAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val KEY_PROXY_APPS = stringSetPreferencesKey("proxy_apps")

    val apps = remember {
        val pm = context.packageManager
        val packages = try { pm.getInstalledPackages(0) }
        catch (_: Exception) { pm.getInstalledPackages(PackageManager.GET_META_DATA) }
        packages
            .filter { it.packageName != context.packageName }
            .mapNotNull { pkgInfo ->
                try {
                    val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                    AppInfo(
                        packageName = pkgInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                    )
                } catch (_: Exception) { null }
            }
            .sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))
    }

    var proxyApps by remember { mutableStateOf(VpnStateManager.proxyApps) }
    var showSystem by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[KEY_PROXY_APPS] ?: emptySet() }.collect {
            proxyApps = it
            VpnStateManager.proxyApps = it
        }
    }

    val filtered = remember(search, showSystem, apps) {
        apps.filter { app ->
            (showSystem || !app.isSystem) &&
            (search.isBlank() ||
             app.appName.contains(search, ignoreCase = true) ||
             app.packageName.contains(search, ignoreCase = true))
        }
    }

    fun toggleApp(pkg: String) {
        val newSet = if (proxyApps.contains(pkg)) proxyApps - pkg else proxyApps + pkg
        proxyApps = newSet
        VpnStateManager.proxyApps = newSet
        scope.launch { context.dataStore.edit { it[KEY_PROXY_APPS] = newSet } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = CloudGreen)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("برنامه‌های فقط VPN", style = MaterialTheme.typography.titleLarge,
                        color = CloudWhite, fontWeight = FontWeight.Bold)
                    Text("${proxyApps.size} برنامه انتخاب شده · ${filtered.size} نمایش",
                        style = MaterialTheme.typography.bodySmall, color = CloudGray)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp),
                color = CloudGreen.copy(0.1f)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = CloudGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "فقط برنامه‌های انتخاب‌شده از VPN استفاده می‌کنن. بقیه مستقیم وصل میشن.",
                        style = MaterialTheme.typography.bodySmall, color = CloudGray
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("جستجوی برنامه…", color = CloudGray.copy(0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = CloudGray) },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, null, tint = CloudGray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CloudGreen,
                        unfocusedBorderColor = CloudGray.copy(0.3f),
                        focusedTextColor = CloudWhite, unfocusedTextColor = CloudWhite,
                        cursorColor = CloudGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text(if (showSystem) "همه" else "کاربری", style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CloudGreen.copy(0.2f),
                        selectedLabelColor = CloudGreen
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Select all / Deselect all buttons
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val allPkgs = filtered.map { it.packageName }.toSet()
                        proxyApps = proxyApps + allPkgs
                        VpnStateManager.proxyApps = proxyApps
                        scope.launch { context.dataStore.edit { it[KEY_PROXY_APPS] = proxyApps } }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CloudGreen.copy(0.15f))
                ) {
                    Icon(Icons.Default.SelectAll, null, tint = CloudGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("انتخاب همه", style = MaterialTheme.typography.labelSmall, color = CloudGreen)
                }
                FilledTonalButton(
                    onClick = {
                        proxyApps = emptySet()
                        VpnStateManager.proxyApps = emptySet()
                        scope.launch { context.dataStore.edit { it[KEY_PROXY_APPS] = emptySet() } }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CloudRed.copy(0.15f))
                ) {
                    Icon(Icons.Default.Deselect, null, tint = CloudRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف همه", style = MaterialTheme.typography.labelSmall, color = CloudRed)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, tint = CloudGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("برنامه‌ای پیدا نشد", color = CloudGray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isProxied = proxyApps.contains(app.packageName)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isProxied) CloudGreen.copy(0.08f) else CloudCard.copy(0.6f),
                            border = if (isProxied) BorderStroke(1.dp, CloudGreen.copy(0.4f)) else null,
                            modifier = Modifier.fillMaxWidth().clickable { toggleApp(app.packageName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = app.icon
                                if (icon != null) {
                                    Image(
                                        bitmap = icon.toBitmap(width = 40, height = 40).asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(38.dp)
                                    )
                                } else {
                                    Surface(shape = CircleShape, color = CloudGreen.copy(0.15f),
                                        modifier = Modifier.size(38.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(app.appName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CloudGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, style = MaterialTheme.typography.bodyMedium,
                                        color = CloudWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = CloudGray.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp)
                                }
                                if (isProxied) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = CloudGreen.copy(0.15f)) {
                                        Text("VPN ✓", style = MaterialTheme.typography.labelSmall,
                                            color = CloudGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
