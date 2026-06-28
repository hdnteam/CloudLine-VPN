package com.hdnteam.cloudlinevpn.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: Drawable?
)

@Composable
fun PerAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val KEY_BYPASS_APPS = stringSetPreferencesKey("bypass_apps")

    // Load installed apps with icons — use getInstalledPackages for full list on Android 13+
    val apps = remember {
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (_: Exception) {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
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

    var bypassApps by remember { mutableStateOf(VpnStateManager.bypassApps) }
    var showSystem by remember { mutableStateOf(true) }  // Show ALL apps by default
    var search     by remember { mutableStateOf("") }

    // Load saved preferences
    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[KEY_BYPASS_APPS] ?: emptySet() }.collect {
            bypassApps = it
            VpnStateManager.bypassApps = it
        }
    }

    // Filter — show all by default, toggle hides system apps
    val filtered = remember(search, showSystem, apps) {
        apps.filter { app ->
            (showSystem || !app.isSystem) &&
            (search.isBlank() ||
             app.appName.contains(search, ignoreCase = true) ||
             app.packageName.contains(search, ignoreCase = true))
        }
    }

    fun toggleApp(pkg: String) {
        val newSet = if (bypassApps.contains(pkg)) bypassApps - pkg else bypassApps + pkg
        bypassApps = newSet
        VpnStateManager.bypassApps = newSet
        scope.launch { context.dataStore.edit { it[KEY_BYPASS_APPS] = newSet } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = CloudAccent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("تنظیمات برنامه‌ها", style = MaterialTheme.typography.titleLarge,
                        color = CloudWhite, fontWeight = FontWeight.Bold)
                    Text("${bypassApps.size} برنامه bypass شده · ${filtered.size} نمایش",
                        style = MaterialTheme.typography.bodySmall, color = CloudGray)
                }
            }

            // Info banner
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp),
                color = CloudAccent.copy(0.1f)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = CloudAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "برنامه‌های انتخاب‌شده از VPN رد نمیشن (مستقیم وصل میشن)",
                        style = MaterialTheme.typography.bodySmall, color = CloudGray
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search + toggle system apps
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
                        focusedBorderColor = CloudAccent,
                        unfocusedBorderColor = CloudGray.copy(0.3f),
                        focusedTextColor = CloudWhite,
                        unfocusedTextColor = CloudWhite,
                        cursorColor = CloudAccent
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
                        selectedContainerColor = CloudAccent.copy(0.2f),
                        selectedLabelColor = CloudAccent
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Select all / Deselect all / Reset buttons
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val allPkgs = filtered.map { it.packageName }.toSet()
                        bypassApps = bypassApps + allPkgs
                        VpnStateManager.bypassApps = bypassApps
                        scope.launch { context.dataStore.edit { it[KEY_BYPASS_APPS] = bypassApps } }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CloudAccent.copy(0.15f))
                ) {
                    Icon(Icons.Default.SelectAll, null, tint = CloudAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("انتخاب همه", style = MaterialTheme.typography.labelSmall, color = CloudAccent)
                }
                FilledTonalButton(
                    onClick = {
                        bypassApps = emptySet()
                        VpnStateManager.bypassApps = emptySet()
                        scope.launch { context.dataStore.edit { it[KEY_BYPASS_APPS] = emptySet() } }
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

            // App list
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
                        val isBypassed = bypassApps.contains(app.packageName)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBypassed) CloudRed.copy(0.08f) else CloudCard.copy(0.6f),
                            border = if (isBypassed) BorderStroke(1.dp, CloudRed.copy(0.3f)) else null,
                            modifier = Modifier.fillMaxWidth().clickable { toggleApp(app.packageName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // App icon (real icon from system)
                                val icon = app.icon
                                if (icon != null) {
                                    Image(
                                        bitmap = icon.toBitmap(width = 40, height = 40).asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(38.dp)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = CloudAccent.copy(0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                app.appName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CloudAccent,
                                                fontWeight = FontWeight.Bold
                                            )
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
                                if (isBypassed) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = CloudRed.copy(0.15f)) {
                                        Text("bypass", style = MaterialTheme.typography.labelSmall,
                                            color = CloudRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
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
