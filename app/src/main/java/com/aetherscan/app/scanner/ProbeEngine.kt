package com.aetherscan.app.scanner

import com.aetherscan.app.data.ScanConfig
import com.aetherscan.app.data.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class ProbeEngine {

    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private fun createSslContext(): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
    }

    private fun clientFor(timeoutSec: Double): OkHttpClient {
        val ms = (timeoutSec * 1000).toLong().coerceAtLeast(1000)
        return OkHttpClient.Builder()
            .connectTimeout(ms, TimeUnit.MILLISECONDS)
            .readTimeout(ms, TimeUnit.MILLISECONDS)
            .writeTimeout(ms, TimeUnit.MILLISECONDS)
            .hostnameVerifier { _, _ -> true }
            .sslSocketFactory(createSslContext().socketFactory, trustAll[0] as X509TrustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun probe(
        ip: String,
        config: ScanConfig
    ): ScanResult? = withContext(Dispatchers.IO) {
        val runs = mutableListOf<ScanResult>()
        repeat(config.tries) {
            if (!coroutineContext.isActive) return@withContext null
            val one = probeOnce(ip, config)
            if (one != null) runs.add(one)
        }
        if (runs.isEmpty()) return@withContext null

        val last = runs.last()
        if (config.expectProvider.isNotBlank() &&
            runs.any { !it.provider.contains(config.expectProvider, ignoreCase = true) }
        ) return@withContext null

        val totals = runs.map { it.totalMs }
        val avg = totals.average()
        last.copy(
            tcpMs = runs.map { it.tcpMs }.average(),
            tlsMs = runs.map { it.tlsMs }.average(),
            ttfbMs = runs.map { it.ttfbMs }.average(),
            totalMs = avg,
            success = true
        )
    }

    private fun probeOnce(ip: String, config: ScanConfig): ScanResult? {
        val t0 = System.nanoTime()
        var socket: Socket? = null
        var ssl: SSLSocket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, config.port), (config.timeoutSec * 1000).toInt())
            val tcpMs = (System.nanoTime() - t0) / 1_000_000.0

            val ctx = createSslContext()
            ssl = ctx.socketFactory.createSocket(socket, config.sni, config.port, true) as SSLSocket
            ssl.soTimeout = (config.timeoutSec * 1000).toInt()
            ssl.startHandshake()
            val tlsMs = (System.nanoTime() - t0) / 1_000_000.0 - tcpMs

            val req = buildString {
                append("${config.method} ${config.path} HTTP/1.1\r\n")
                append("Host: ${config.host}\r\n")
                append("User-Agent: AetherScan/1.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            val t1 = System.nanoTime()
            ssl.outputStream.write(req.toByteArray())
            ssl.outputStream.flush()

            val buf = ByteArray(8192)
            var totalRead = 0
            var ttfb = 0.0
            val data = StringBuilder()
            while (totalRead < 16384) {
                val n = ssl.inputStream.read(buf)
                if (n <= 0) break
                if (ttfb == 0.0) ttfb = (System.nanoTime() - t1) / 1_000_000.0
                data.append(String(buf, 0, n, Charsets.ISO_8859_1))
                totalRead += n
                if (data.contains("\r\n\r\n") && totalRead > 512) break
            }

            val raw = data.toString()
            val headEnd = raw.indexOf("\r\n\r\n")
            val head = if (headEnd >= 0) raw.substring(0, headEnd) else raw
            val body = if (headEnd >= 0) raw.substring(headEnd + 4) else ""

            val lines = head.split("\r\n")
            val status = lines.firstOrNull()?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] =
                        line.substring(idx + 1).trim()
                }
            }

            val (prov, colo) = detect(headers, body)
            val total = tcpMs + tlsMs + ttfb

            ScanResult(
                ip = ip,
                tcpMs = tcpMs,
                tlsMs = tlsMs,
                ttfbMs = ttfb,
                totalMs = total,
                status = status,
                provider = prov,
                colo = colo,
                server = headers["server"] ?: "",
                success = status in 200..499
            )
        } catch (_: Exception) {
            null
        } finally {
            try { ssl?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun detect(headers: Map<String, String>, body: String): Pair<String, String> {
        val server = headers["server"] ?: ""
        val via = headers["via"] ?: ""
        val xCache = headers["x-cache"] ?: ""
        val xServed = headers["x-served-by"] ?: ""

        val prov = when {
            headers.containsKey("cf-ray") || server.equals("cloudflare", true) -> "cloudflare"
            headers.containsKey("x-fastly-request-id") ||
                    via.contains("fastly", true) ||
                    xServed.contains("cache-", true) -> "fastly"
            server.contains("akamai", true) ||
                    via.contains("akamai", true) ||
                    headers.keys.any { it.startsWith("x-akamai") || it.startsWith("akamai") } ||
                    xCache.contains("akamai", true) -> "akamai"
            else -> server.take(20).ifBlank { "unknown" }
        }

        var colo = ""
        val coloMatch = Regex("colo=(\\w+)").find(body)
        if (coloMatch != null) colo = coloMatch.groupValues[1]
        else if (headers.containsKey("cf-ray")) {
            colo = headers["cf-ray"]!!.substringAfterLast('-')
        } else if (xServed.isNotBlank()) {
            colo = xServed.split(",").last().trim().substringAfterLast('-')
        }
        return prov to colo
    }

    suspend fun speedTest(ip: String, config: ScanConfig, seconds: Int = 4): Double =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            var ssl: SSLSocket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, config.port), (config.timeoutSec * 1000).toInt())
                val ctx = createSslContext()
                ssl = ctx.socketFactory.createSocket(socket, config.sni, config.port, true) as SSLSocket
                ssl.soTimeout = (config.timeoutSec * 1000).toInt()
                ssl.startHandshake()

                val req = "GET ${config.path} HTTP/1.1\r\nHost: ${config.host}\r\nUser-Agent: AetherScan/1.0\r\nConnection: close\r\n\r\n"
                ssl.outputStream.write(req.toByteArray())
                ssl.outputStream.flush()

                val buf = ByteArray(65536)
                var got = 0L
                var headerDone = false
                val t0 = System.nanoTime()
                val limitNs = seconds * 1_000_000_000L
                while (System.nanoTime() - t0 < limitNs) {
                    val n = ssl.inputStream.read(buf)
                    if (n <= 0) break
                    if (!headerDone) {
                        // rough: after first chunk assume header passed
                        headerDone = true
                        got = 0
                    } else {
                        got += n
                    }
                }
                val elapsed = (System.nanoTime() - t0) / 1e9
                if (elapsed > 0.3 && got > 0) {
                    ((got * 8.0) / 1_000_000.0 / elapsed * 10).roundToInt() / 10.0
                } else 0.0
            } catch (_: Exception) {
                0.0
            } finally {
                try { ssl?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }

    suspend fun runBatch(
        ips: List<String>,
        config: ScanConfig,
        onProgress: (done: Int, total: Int, found: Int) -> Unit,
        onResult: (ScanResult) -> Unit,
        isStopped: () -> Boolean
    ): List<ScanResult> = coroutineScope {
        val results = mutableListOf<ScanResult>()
        val done = AtomicInteger(0)
        val found = AtomicInteger(0)
        val sem = Semaphore(config.threads.coerceIn(4, 80))

        val jobs = ips.map { ip ->
            async(Dispatchers.IO) {
                if (isStopped()) return@async
                sem.withPermit {
                    if (isStopped()) return@withPermit
                    val r = probe(ip, config)
                    val d = done.incrementAndGet()
                    if (r != null && r.success) {
                        found.incrementAndGet()
                        synchronized(results) { results.add(r) }
                        onResult(r)
                    }
                    onProgress(d, ips.size, found.get())
                }
            }
        }
        jobs.awaitAll()
        results.sortedBy { it.totalMs }
    }
}
