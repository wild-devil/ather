package com.aetherscan.app.data

data class ScanResult(
    val ip: String,
    val tcpMs: Double = 0.0,
    val tlsMs: Double = 0.0,
    val ttfbMs: Double = 0.0,
    val totalMs: Double = 0.0,
    val status: Int = 0,
    val provider: String = "",
    val colo: String = "",
    val server: String = "",
    val speedMbps: Double = 0.0,
    val success: Boolean = false
)

data class ScanConfig(
    val provider: String = "cloudflare",
    val customRanges: String = "",
    val host: String = "www.cloudflare.com",
    val sni: String = "www.cloudflare.com",
    val path: String = "/cdn-cgi/trace",
    val method: String = "GET",
    val port: Int = 443,
    val maxIps: Int = 200,
    val threads: Int = 40,
    val tries: Int = 2,
    val timeoutSec: Double = 3.0,
    val expectProvider: String = "",
    val ipVersion: String = "4" // 4, 6, both
)

enum class ScanState {
    IDLE, RUNNING, FINISHED, STOPPED, ERROR
}
