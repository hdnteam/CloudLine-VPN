package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hdnteam.cloudlinevpn.ui.theme.*

@Composable
fun OnboardingScreen(
    isLoading: Boolean,
    errorMessage: String,
    onSubscribe: (url: String, alias: String) -> Unit,
    onSkip: () -> Unit
) {
    var url   by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35), CloudDarkBlue)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // Logo area
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = CloudCard,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = CloudAccent,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "CloudLine VPN",
                style = MaterialTheme.typography.headlineLarge,
                color = CloudAccent,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "برای شروع، لینک اشتراک خود را وارد کنید",
                style = MaterialTheme.typography.bodyMedium,
                color = CloudGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Subscription URL input
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = {
                    Text(
                        "لینک سابسکرایب CloudLine VPN",
                        color = CloudGray
                    )
                },
                placeholder = {
                    Text("https://...", color = CloudGray.copy(0.4f))
                },
                leadingIcon = {
                    Icon(Icons.Default.Link, null, tint = CloudAccent)
                },
                trailingIcon = {
                    if (url.isNotBlank()) {
                        IconButton(onClick = { url = "" }) {
                            Icon(Icons.Default.Clear, null, tint = CloudGray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CloudAccent,
                    unfocusedBorderColor = CloudGray.copy(0.3f),
                    focusedTextColor = CloudWhite,
                    unfocusedTextColor = CloudWhite,
                    cursorColor = CloudAccent,
                    focusedLabelColor = CloudAccent
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Alias field
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("نام اشتراک (اختیاری)", color = CloudGray) },
                placeholder = { Text("مثلاً: اشتراک اصلی", color = CloudGray.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Label, null, tint = CloudAccent) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CloudAccent,
                    unfocusedBorderColor = CloudGray.copy(0.3f),
                    focusedTextColor = CloudWhite,
                    unfocusedTextColor = CloudWhite,
                    cursorColor = CloudAccent,
                    focusedLabelColor = CloudAccent
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (url.isNotBlank()) onSubscribe(url.trim(), alias.trim())
                }),
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudRed.copy(0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(Icons.Default.Error, null, tint = CloudRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CloudRed)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (url.isNotBlank()) onSubscribe(url.trim(), alias.trim())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = url.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CloudAccent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("در حال دریافت سرورها…", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Done, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("شروع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button
            TextButton(onClick = onSkip) {
                Text("بعداً اضافه می‌کنم", color = CloudGray, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Info cards
            listOf(
                Icons.Default.Security to "اتصال امن و رمزنگاری‌شده",
                Icons.Default.Speed    to "سرعت بالا با هسته Xray",
                Icons.Default.Wifi     to "اتصال خودکار به بهترین سرور"
            ).forEach { (icon, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CloudAccent.copy(0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = CloudAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = CloudGray)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                "POWERED BY HDNTEAM",
                style = MaterialTheme.typography.labelSmall,
                color = CloudGray.copy(0.4f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
