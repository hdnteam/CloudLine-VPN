package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.hdnteam.cloudlinevpn.data.model.Subscription
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.AccountViewModel

@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val subscriptions by viewModel.subscriptions.collectAsState()
    val message       by viewModel.message.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var urlInput      by remember { mutableStateOf("") }
    var aliasInput    by remember { mutableStateOf("") }

    // QR Code scanner
    val qrScanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data?.getStringExtra("SCAN_RESULT")
        if (!data.isNullOrBlank()) {
            viewModel.addSubscription(data.trim(), "")
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent
    ) { pad ->
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

                // ── Header ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("حساب کاربری",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CloudWhite, fontWeight = FontWeight.Bold)
                        Text("CloudLine VPN",
                            style = MaterialTheme.typography.bodySmall, color = CloudAccent)
                    }
                    FilledTonalButton(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CloudCard)
                    ) {
                        Icon(Icons.Default.Sync, null, tint = CloudAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("بروزرسانی اشتراک", style = MaterialTheme.typography.bodySmall, color = CloudAccent)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Add subscription / QR buttons ────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CloudAccent.copy(0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CloudAccent)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("افزودن", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = com.journeyapps.barcodescanner.ScanContract().createIntent(
                                context,
                                com.journeyapps.barcodescanner.ScanOptions().apply {
                                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                    setPrompt("QR Code را اسکن کنید\nبرای لغو دکمه بازگشت را بزنید")
                                    setCameraId(0)
                                    setBeepEnabled(false)
                                    setOrientationLocked(true)
                                    setBarcodeImageEnabled(false)
                                }
                            )
                            qrScanLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CloudGreen.copy(0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CloudGreen)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("اسکن", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                viewModel.addSubscription(clip.trim(), "")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CloudOrange.copy(0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CloudOrange)
                    ) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("کلیپبورد", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Subscription cards ─────────────────────────────────────
                if (subscriptions.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountCircle, null,
                                tint = CloudGray, modifier = Modifier.size(72.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("هیچ اشتراکی ثبت نشده",
                                style = MaterialTheme.typography.titleMedium, color = CloudGray)
                            Spacer(Modifier.height(8.dp))
                            Text("روی دکمه بالا کلیک کنید",
                                style = MaterialTheme.typography.bodySmall,
                                color = CloudGray.copy(0.6f))
                        }
                    }
                } else {
                    subscriptions.forEachIndexed { index, sub ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        SubscriptionCard(
                            sub = sub,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteSubscription(sub) }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("POWERED BY HDNTEAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloudGray.copy(0.4f), letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ── Add subscription dialog ──────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; urlInput = ""; aliasInput = "" },
            containerColor = CloudCard,
            title = {
                Text("افزودن اشتراک یا کانفیگ",
                    style = MaterialTheme.typography.titleLarge, color = CloudWhite)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("لینک اشتراک یا تک کانفیگ (vmess://, vless://, trojan://, ss://) وارد کنید",
                        style = MaterialTheme.typography.bodySmall, color = CloudGray)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("لینک اشتراک یا کانفیگ", color = CloudGray) },
                        placeholder = { Text("https://… یا vless://…", color = CloudGray.copy(0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Link, null, tint = CloudAccent) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CloudAccent,
                            unfocusedBorderColor = CloudGray.copy(0.3f),
                            focusedTextColor = CloudWhite,
                            unfocusedTextColor = CloudWhite,
                            cursorColor = CloudAccent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("نام مستعار (اختیاری)", color = CloudGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CloudAccent,
                            unfocusedBorderColor = CloudGray.copy(0.3f),
                            focusedTextColor = CloudWhite,
                            unfocusedTextColor = CloudWhite,
                            cursorColor = CloudAccent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.addSubscription(urlInput.trim(), aliasInput.trim())
                            urlInput = ""; aliasInput = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CloudAccent),
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("افزودن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false; urlInput = ""; aliasInput = ""
                }) {
                    Text("لغو", color = CloudGray)
                }
            }
        )
    }
}

// ── SubscriptionCard ──────────────────────────────────────────────────────────

@Composable
fun SubscriptionCard(
    sub: Subscription,
    viewModel: AccountViewModel,
    onDelete: () -> Unit = {}
) {
    val days         = viewModel.getDaysRemaining(sub)
    val usedBytes    = viewModel.getUsedBytes(sub)
    val usagePercent = viewModel.getUsagePercent(sub)
    val dayColor = when {
        days < 0  -> CloudWhite
        days <= 3 -> CloudRed
        days <= 7 -> CloudOrange
        else      -> CloudGreen
    }
    val usageColor = when {
        usagePercent > 0.9f -> CloudRed
        usagePercent > 0.7f -> CloudOrange
        else                -> CloudAccent
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CloudCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {

            // ── Header ───────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = CloudAccent.copy(0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null,
                            tint = CloudAccent, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.username.ifBlank { "CloudLine User" },
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite, fontWeight = FontWeight.Bold)
                    Text(sub.alias.ifBlank { sub.url.take(35) + "…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = CloudGray, maxLines = 1)
                }
                // Days badge
                Surface(shape = RoundedCornerShape(8.dp), color = dayColor.copy(0.15f)) {
                    Text(
                        if (days < 0) "∞" else "$days روز",
                        style = MaterialTheme.typography.labelSmall,
                        color = dayColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Delete button
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.DeleteOutline, "حذف",
                        tint = CloudRed.copy(0.7f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = CloudSurface)
            Spacer(Modifier.height(14.dp))

            // ── Data usage ────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DataUsage, null,
                    tint = CloudAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حجم مصرفی",
                            style = MaterialTheme.typography.bodySmall, color = CloudGray)
                        Text(
                            "${viewModel.formatBytes(usedBytes)} / ${viewModel.formatBytes(sub.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloudWhite, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { usagePercent },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = usageColor,
                        trackColor = CloudSurface,
                        strokeCap = StrokeCap.Round
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${(usagePercent * 100).toInt()}٪",
                    style = MaterialTheme.typography.bodySmall,
                    color = usageColor, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            // ── Download / Upload detail ──────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, null,
                        tint = CloudGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("دریافت: ${viewModel.formatBytes(sub.downloadBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloudGray, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, null,
                        tint = CloudAccent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("ارسال: ${viewModel.formatBytes(sub.uploadBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloudGray, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Server count ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null,
                    tint = CloudGray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${sub.serverCount} سرور",
                    style = MaterialTheme.typography.bodySmall, color = CloudGray)
            }
        }
    }
}
