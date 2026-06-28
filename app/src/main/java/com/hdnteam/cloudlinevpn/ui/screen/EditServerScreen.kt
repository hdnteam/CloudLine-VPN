package com.hdnteam.cloudlinevpn.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.ui.theme.*

@Composable
fun EditServerScreen(
    server: ServerConfig,
    onSave: (ServerConfig) -> Unit,
    onBack: () -> Unit
) {
    var name     by remember { mutableStateOf(server.name) }
    var address  by remember { mutableStateOf(server.address) }
    var port     by remember { mutableStateOf(server.port.toString()) }
    var uuid     by remember { mutableStateOf(server.uuid) }
    var sni      by remember { mutableStateOf(server.sni) }
    var path     by remember { mutableStateOf(server.path) }
    var host     by remember { mutableStateOf(server.requestHost) }
    var security by remember { mutableStateOf(server.tlsSecurity) }
    var network  by remember { mutableStateOf(server.network) }
    var flow     by remember { mutableStateOf(server.flow) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CloudDarkBlue, Color(0xFF0D1E35))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = CloudAccent)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("ویرایش کانفیگ", style = MaterialTheme.typography.headlineMedium,
                        color = CloudWhite, fontWeight = FontWeight.Bold)
                    Text(server.protocol.uppercase(), style = MaterialTheme.typography.bodySmall, color = CloudAccent)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Fields
            EditField("نام", name, Icons.Default.Label) { name = it }
            Spacer(Modifier.height(10.dp))
            EditField("آدرس سرور", address, Icons.Default.Public) { address = it }
            Spacer(Modifier.height(10.dp))
            EditField("پورت", port, Icons.Default.Tag, KeyboardType.Number) { port = it }
            Spacer(Modifier.height(10.dp))

            if (server.protocol != "shadowsocks") {
                EditField("UUID / رمز", uuid, Icons.Default.Key) { uuid = it }
                Spacer(Modifier.height(10.dp))
            }

            EditField("SNI / هاست", sni, Icons.Default.Domain) { sni = it }
            Spacer(Modifier.height(10.dp))
            EditField("Path", path, Icons.Default.Link) { path = it }
            Spacer(Modifier.height(10.dp))
            EditField("Host Header", host, Icons.Default.Language) { host = it }
            Spacer(Modifier.height(10.dp))
            EditField("Security (tls/reality/none)", security, Icons.Default.Security) { security = it }
            Spacer(Modifier.height(10.dp))
            EditField("Network (tcp/ws/grpc/xhttp)", network, Icons.Default.Share) { network = it }
            Spacer(Modifier.height(10.dp))

            if (server.protocol == "vless" || server.protocol == "trojan") {
                EditField("Flow (xtls-rprx-vision / خالی)", flow, Icons.Default.Speed) { flow = it }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val updated = server.copy(
                        name = name.trim(),
                        address = address.trim(),
                        port = port.toIntOrNull() ?: server.port,
                        uuid = uuid.trim(),
                        sni = sni.trim(),
                        path = path.trim(),
                        requestHost = host.trim(),
                        tlsSecurity = security.trim().lowercase(),
                        network = network.trim().lowercase(),
                        flow = flow.trim()
                    )
                    onSave(updated)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CloudAccent)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ذخیره تغییرات", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CloudGray) },
        leadingIcon = { Icon(icon, null, tint = CloudAccent, modifier = Modifier.size(18.dp)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CloudAccent,
            unfocusedBorderColor = CloudGray.copy(0.3f),
            focusedTextColor = CloudWhite,
            unfocusedTextColor = CloudWhite,
            cursorColor = CloudAccent
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
