package com.hdnteam.cloudlinevpn.ui.screen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hdnteam.cloudlinevpn.ui.theme.*

@Composable
fun PurchaseScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Text("خرید / تمدید اشتراک",
                style = MaterialTheme.typography.headlineMedium,
                color = CloudWhite, fontWeight = FontWeight.Bold)
            Text("CloudLine VPN", style = MaterialTheme.typography.bodySmall, color = CloudAccent)

            Spacer(Modifier.height(36.dp))

            // Logo
            Surface(shape = RoundedCornerShape(32.dp), color = CloudCard, modifier = Modifier.size(110.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Cloud, null, tint = CloudAccent, modifier = Modifier.size(72.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("اشتراک CloudLine VPN",
                style = MaterialTheme.typography.headlineMedium,
                color = CloudWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            // Feature list
            listOf(
                Triple(Icons.Default.Speed,        "سرعت بالا",        "مناسب برای اینترنت ملی"),
                Triple(Icons.Default.Security,     "امنیت کامل",       "رمزنگاری پیشرفته"),
                Triple(Icons.Default.Language,     "آی‌پی ثابت",       "جهت امنیت شما تمامی سرویس‌ها آی‌پی ثابت می‌باشد"),
                Triple(Icons.Default.HeadsetMic,   "پشتیبانی ۲۴/۷",   "همیشه در دسترس")
            ).forEach { (icon, title, desc) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CloudCard,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = CloudAccent.copy(0.15f), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = CloudAccent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.bodyMedium,
                                color = CloudWhite, fontWeight = FontWeight.SemiBold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = CloudGray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            // Telegram CTA
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vpnxubot"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9))
            ) {
                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(22.dp), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("خرید از طریق ربات تلگرام",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Share button
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "🔒 VPN سریع و امن CloudLine\n\nخرید اشتراک:\nhttps://t.me/vpnxubot")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری"))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CloudAccent.copy(0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CloudAccent)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("اشتراک‌گذاری با دوستان",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
            Text("@vpnxubot", style = MaterialTheme.typography.bodySmall, color = CloudGray.copy(0.6f))

            Spacer(Modifier.height(36.dp))
            Text("POWERED BY HDNTEAM", style = MaterialTheme.typography.labelSmall,
                color = CloudGray.copy(0.4f), letterSpacing = 2.sp)
            Spacer(Modifier.height(80.dp))
        }
    }
}
