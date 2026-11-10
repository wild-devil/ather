package com.aetherscan.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherscan.app.data.Providers
import com.aetherscan.app.data.ScanConfig
import com.aetherscan.app.data.ScanResult
import com.aetherscan.app.data.ScanState
import com.aetherscan.app.scanner.IpUtils
import com.aetherscan.app.scanner.ProbeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {

    private val engine = ProbeEngine()
    private var scanJob: Job? = null
    private var stopFlag = false

    var config by mutableStateOf(ScanConfig())
        private set

    var state by mutableStateOf(ScanState.IDLE)
        private set

    var progressDone by mutableStateOf(0)
        private set
    var progressTotal by mutableStateOf(0)
        private set
    var foundCount by mutableStateOf(0)
        private set

    val results = mutableStateListOf<ScanResult>()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedProvider by mutableStateOf("Cloudflare")
        private set

    fun updateConfig(transform: (ScanConfig) -> ScanConfig) {
        config = transform(config)
    }

    fun setProvider(name: String) {
        selectedProvider = name
        val key = name.lowercase()
        config = config.copy(
            provider = key,
            host = when (key) {
                "cloudflare" -> "www.cloudflare.com"
                "fastly" -> "www.fastly.com"
                "akamai" -> "www.akamai.com"
                else -> config.host
            },
            sni = when (key) {
                "cloudflare" -> "www.cloudflare.com"
                "fastly" -> "www.fastly.com"
                "akamai" -> "www.akamai.com"
                else -> config.sni
            },
            path = if (key == "cloudflare") "/cdn-cgi/trace" else "/"
        )
    }

    fun startScan() {
        if (state == ScanState.RUNNING) return
        stopFlag = false
        results.clear()
        progressDone = 0
        progressTotal = 0
        foundCount = 0
        errorMessage = null
        state = ScanState.RUNNING

        scanJob = viewModelScope.launch {
            try {
                val rangesText = buildString {
                    if (selectedProvider != "Custom") {
                        append(Providers.rangesFor(selectedProvider.lowercase()).joinToString("\n"))
                        append("\n")
                    }
                    append(config.customRanges)
                }
                val ranges = IpUtils.parseRange(rangesText)
                if (ranges.isEmpty()) {
                    errorMessage = "No valid IP ranges found"
                    state = ScanState.ERROR
                    return@launch
                }
                val ips = IpUtils.sampleIps(ranges, config.maxIps)
                if (ips.isEmpty()) {
                    errorMessage = "Could not sample any public IPs"
                    state = ScanState.ERROR
                    return@launch
                }
                progressTotal = ips.size

                val found = engine.runBatch(
                    ips = ips,
                    config = config,
                    onProgress = { d, t, f ->
                        progressDone = d
                        progressTotal = t
                        foundCount = f
                    },
                    onResult = { r ->
                        results.add(r)
                        // keep sorted by latency
                        results.sortBy { it.totalMs }
                    },
                    isStopped = { stopFlag }
                )

                state = if (stopFlag) ScanState.STOPPED else ScanState.FINISHED
            } catch (e: Exception) {
                errorMessage = e.message ?: "Scan failed"
                state = ScanState.ERROR
            }
        }
    }

    fun stopScan() {
        stopFlag = true
        scanJob?.cancel()
        state = ScanState.STOPPED
    }

    fun clearResults() {
        results.clear()
        progressDone = 0
        progressTotal = 0
        foundCount = 0
        state = ScanState.IDLE
        errorMessage = null
    }

    fun exportText(): String {
        return buildString {
            appendLine("# AetherScan results")
            appendLine("# IP, Total(ms), TCP, TLS, TTFB, Status, Provider, Colo")
            results.forEach { r ->
                appendLine("${r.ip},${"%.1f".format(r.totalMs)},${"%.1f".format(r.tcpMs)},${"%.1f".format(r.tlsMs)},${"%.1f".format(r.ttfbMs)},${r.status},${r.provider},${r.colo}")
            }
        }
    }

    fun exportIpsOnly(): String = results.joinToString("\n") { it.ip }
}
