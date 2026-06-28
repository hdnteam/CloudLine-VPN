package com.hdnteam.cloudlinevpn.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.util.AppLogger
import com.hdnteam.cloudlinevpn.util.LogLevel

@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var copyTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) snackbar.showSnackbar("لاگ کپی شد ✓")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF050A12))))
                .padding(pad)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = CloudAccent)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("لاگ برنامه", style = MaterialTheme.typography.titleLarge,
                            color = CloudWhite, fontWeight = FontWeight.Bold)
                        Text("${logs.size} خط", style = MaterialTheme.typography.bodySmall, color = CloudGray)
                    }
                    // Copy all
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("CloudLine Log", AppLogger.getAll()))
                        copyTrigger++
                    }) {
                        Icon(Icons.Default.ContentCopy, "کپی", tint = CloudAccent)
                    }
                    // Clear
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Default.Delete, "پاک کردن", tint = CloudRed)
                    }
                }

                HorizontalDivider(color = CloudSurface)

                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Code, null, tint = CloudGray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("هنوز لاگی نیست", color = CloudGray)
                            Text("دکمه Connect را بزنید", color = CloudGray.copy(0.6f),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when (entry.level) {
                                LogLevel.ERROR -> CloudRed
                                LogLevel.WARN  -> CloudOrange
                                LogLevel.INFO  -> CloudGreen
                                LogLevel.DEBUG -> CloudGray
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = color.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(
                                        "[${entry.time}] ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloudGray.copy(0.6f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "${entry.level.label}/${entry.tag}: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        entry.msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloudWhite.copy(0.85f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}
