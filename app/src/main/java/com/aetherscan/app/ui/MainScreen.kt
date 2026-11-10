package com.aetherscan.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aetherscan.app.data.Providers
import com.aetherscan.app.data.ScanResult
import com.aetherscan.app.data.ScanState
import com.aetherscan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ScanViewModel = viewModel()) {
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070B18),
                        Color(0xFF0C1228),
                        Color(0xFF070B18)
                    )
                )
            )
    ) {
        // subtle grid glow
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x227DD3FC), Color.Transparent),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "AetherScan",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "CDN IP Scanner · Precision Probe",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(
                        if (showSettings) Icons.Default.ExpandLess else Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = NeonCyan
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnimatedVisibility(visible = showSettings) {
                        SettingsPanel(vm)
                    }
                }

                item {
                    ActionBar(vm, ctx)
                }

                item {
                    ProgressCard(vm)
                }

                if (vm.results.isNotEmpty()) {
                    item {
                        ResultsHeader(vm, ctx)
                    }
                    items(vm.results, key = { it.ip }) { r ->
                        ResultRow(r)
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(vm: ScanViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NeonLabel("Provider")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Providers.names.forEach { name ->
                    val selected = vm.selectedProvider == name
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) NeonCyan.copy(alpha = 0.18f)
                                else Color(0x22FFFFFF)
                            )
                            .border(
                                1.dp,
                                if (selected) NeonCyan.copy(alpha = 0.6f) else Color(0x22FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { vm.setProvider(name) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            color = if (selected) NeonCyan else TextDim,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            NeonLabel("Host / SNI")
            OutlinedTextField(
                value = vm.config.host,
                onValueChange = {
                    vm.updateConfig { c -> c.copy(host = it, sni = it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    NeonLabel("Max IPs")
                    OutlinedTextField(
                        value = vm.config.maxIps.toString(),
                        onValueChange = {
                            val v = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 200
                            vm.updateConfig { c -> c.copy(maxIps = v.coerceIn(10, 5000)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    NeonLabel("Threads")
                    OutlinedTextField(
                        value = vm.config.threads.toString(),
                        onValueChange = {
                            val v = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 40
                            vm.updateConfig { c -> c.copy(threads = v.coerceIn(4, 80)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (vm.selectedProvider == "Custom") {
                NeonLabel("Custom Ranges (CIDR or IP)")
                OutlinedTextField(
                    value = vm.config.customRanges,
                    onValueChange = { vm.updateConfig { c -> c.copy(customRanges = it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("1.2.3.0/24\n5.6.7.8", color = TextDim) }
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan.copy(alpha = 0.6f),
    unfocusedBorderColor = BorderGlow,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = NeonCyan,
    focusedContainerColor = Color(0x22101831),
    unfocusedContainerColor = Color(0x15101831)
)

@Composable
private fun ActionBar(vm: ScanViewModel, ctx: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val running = vm.state == ScanState.RUNNING
        Button(
            onClick = {
                if (running) vm.stopScan() else vm.startScan()
            },
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) NeonPink.copy(alpha = 0.85f) else NeonCyan.copy(alpha = 0.9f),
                contentColor = BgDeep
            )
        ) {
            Icon(
                if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (running) "Stop Scan" else "Start Scan",
                fontWeight = FontWeight.Bold
            )
        }

        if (vm.results.isNotEmpty()) {
            IconButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("ips", vm.exportIpsOnly()))
                    Toast.makeText(ctx, "IPs copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33101831))
                    .border(1.dp, BorderGlow, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Outlined.ContentCopy, null, tint = NeonGreen)
            }
        }
    }
}

@Composable
private fun ProgressCard(vm: ScanViewModel) {
    if (vm.state == ScanState.IDLE && vm.results.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonLabel(
                    when (vm.state) {
                        ScanState.RUNNING -> "Scanning…"
                        ScanState.FINISHED -> "Finished"
                        ScanState.STOPPED -> "Stopped"
                        ScanState.ERROR -> "Error"
                        else -> "Ready"
                    },
                    color = when (vm.state) {
                        ScanState.RUNNING -> NeonCyan
                        ScanState.FINISHED -> NeonGreen
                        ScanState.STOPPED -> NeonPink
                        ScanState.ERROR -> NeonPink
                        else -> TextDim
                    }
                )
                MonoText(
                    "${vm.foundCount} found · ${vm.progressDone}/${vm.progressTotal}",
                    color = TextDim,
                    size = 12
                )
            }
            if (vm.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { vm.progressDone.toFloat() / vm.progressTotal },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = NeonCyan,
                    trackColor = Color(0x33FFFFFF)
                )
            }
            vm.errorMessage?.let {
                Text(it, color = NeonPink, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ResultsHeader(vm: ScanViewModel, ctx: Context) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle("Results (${vm.results.size})")
        TextButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("results", vm.exportText()))
            Toast.makeText(ctx, "Full results copied", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Outlined.Download, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Export", color = NeonCyan, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ResultRow(r: ScanResult) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoText(r.ip, color = NeonCyan, size = 15, weight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    r.totalMs < 80 -> NeonGreen
                                    r.totalMs < 180 -> NeonCyan
                                    else -> NeonPink
                                }
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    MonoText("%.0f ms".format(r.totalMs), size = 13)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${r.provider}  ·  ${r.colo.ifBlank { "—" }}  ·  HTTP ${r.status}",
                    color = TextDim,
                    fontSize = 12.sp
                )
                Text(
                    "TCP ${"%.0f".format(r.tcpMs)} · TLS ${"%.0f".format(r.tlsMs)}",
                    color = TextDim,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
