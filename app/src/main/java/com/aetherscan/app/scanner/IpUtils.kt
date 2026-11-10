package com.aetherscan.app.scanner

import java.net.InetAddress
import java.util.concurrent.ThreadLocalRandom

object IpUtils {

    data class Range(val start: Long, val end: Long)

    fun parseCidr(cidr: String): Range? {
        return try {
            val parts = cidr.trim().split("/")
            if (parts.size != 2) return null
            val ip = parts[0]
            val prefix = parts[1].toInt()
            if (prefix < 0 || prefix > 32) return null
            val addr = ipToLong(ip) ?: return null
            val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix))
            val network = addr and mask
            val broadcast = network or mask.inv() and 0xFFFFFFFFL
            Range(network, broadcast)
        } catch (_: Exception) {
            null
        }
    }

    fun parseRange(text: String): List<Range> {
        val out = mutableListOf<Range>()
        for (tok in text.split(Regex("[\\s,;\\n]+"))) {
            val t = tok.trim()
            if (t.isEmpty()) continue
            when {
                "/" in t -> parseCidr(t)?.let { out.add(it) }
                "-" in t -> {
                    val (a, b) = t.split("-", limit = 2)
                    val start = ipToLong(a.trim())
                    val end = if ("." in b) ipToLong(b.trim()) else {
                        val base = start?.and(0xFFFFFF00L) ?: return@let
                        base or (b.trim().toLongOrNull() ?: return@let)
                    }
                    if (start != null && end != null && end >= start) {
                        out.add(Range(start, end))
                    }
                }
                else -> ipToLong(t)?.let { out.add(Range(it, it)) }
            }
        }
        return out
    }

    fun ipToLong(ip: String): Long? {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return null
            var v = 0L
            for (p in parts) {
                val n = p.toInt()
                if (n !in 0..255) return null
                v = (v shl 8) or n.toLong()
            }
            v
        } catch (_: Exception) {
            null
        }
    }

    fun longToIp(v: Long): String {
        return "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
    }

    fun sampleIps(ranges: List<Range>, limit: Int): List<String> {
        if (ranges.isEmpty() || limit <= 0) return emptyList()
        val total = ranges.sumOf { it.end - it.start + 1 }
        if (total <= 0) return emptyList()
        val result = LinkedHashSet<String>()
        val random = ThreadLocalRandom.current()
        val attempts = limit * 8
        var tries = 0
        while (result.size < limit && tries < attempts) {
            tries++
            val r = ranges[random.nextInt(ranges.size)]
            val size = r.end - r.start + 1
            if (size <= 0) continue
            val offset = if (size > Int.MAX_VALUE) {
                random.nextLong(size)
            } else {
                random.nextInt(size.toInt()).toLong()
            }
            val ip = longToIp(r.start + offset)
            // skip private / reserved roughly
            if (isPublic(ip)) result.add(ip)
        }
        return result.toList()
    }

    private fun isPublic(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val a = parts[0]
        val b = parts[1]
        return when {
            a == 10 -> false
            a == 127 -> false
            a == 0 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 168 -> false
            a == 169 && b == 254 -> false
            a >= 224 -> false
            else -> true
        }
    }

    fun resolveHost(host: String): String? {
        return try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
