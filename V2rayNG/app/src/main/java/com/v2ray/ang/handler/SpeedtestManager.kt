package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

object SpeedtestManager {

    private val tcpTestingSockets = ArrayList<Socket?>()

    /** Connect timeout for the TCP rung of [httpPing]; the node's port already refused HTTP(S). */
    private const val TCP_FALLBACK_TIMEOUT_MS = 2000

    private const val PING = "/system/bin/ping"
    private const val PING6 = "/system/bin/ping6"

    // One OkHttpClient shared across direct HTTP probes for connection/thread pool reuse.
    private val httpPingClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout(5000, TimeUnit.MILLISECONDS)
            .callTimeout(5000, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .build()
    }

    /**
     * Direct HTTP latency probe (no proxy). Sends a HEAD and measures time-to-first-byte of the
     * response headers (the method is HEAD, not GET — earlier KDoc and the picker label said GET).
     *
     * Per-node mode ([expectAny] = true) is a two-rung ladder on purpose. Rung 1 is HTTP(S) to the
     * node's own host:port. Most nodes do not speak HTTPS on their transport port (plain
     * vmess/vless/ss, ws without TLS), so the TLS handshake fails and rung 1 on its own reported
     * -1 for a node that is perfectly up — the "https://host:port misclassifies non-TLS nodes"
     * defect. Rung 2 is one TCP connect to the same host:port: if the port completes a handshake
     * the node *is* reachable and that handshake time is a real measurement of that node, so it is
     * reported. A node that answers neither rung gets no number at all — -1, painted red as a
     * failure — because a plausible-looking latency for an unreachable node is worse than no
     * latency. The two rungs measure different layers (HTTP TTFB includes TLS, a TCP handshake is
     * one RTT), so this column ranks nodes only roughly, exactly as ping-methods-design.md says of
     * the direct methods; the proxied real delay stays the only ranker fast-connect trusts.
     *
     * @param url the target URL.
     * @param expectAny when true this is a **per-node** reachability probe: any HTTP response
     *        counts, and a transport failure falls back to a TCP connect. When false this is the
     *        captive-portal generate_204 connectivity check and only 204/200 counts — a redirect
     *        or a login page there is a hijack, i.e. a real failure, and no TCP rung runs.
     * @return latency in ms, or -1 on failure. Never 0: 0 is the "not tested" sentinel that
     *         [com.v2ray.ang.dto.entities.ServerAffiliationInfo] renders as a blank cell.
     */
    fun httpPing(url: String, expectAny: Boolean = false): Long {
        val req = okhttp3.Request.Builder()
            .url(url)
            .head()
            .header("Connection", "close")
            .build()
        val start = System.nanoTime()
        try {
            httpPingClient.newCall(req).execute().use { r ->
                val answered =
                    if (expectAny) r.code in 100..599 else r.code == 204 || r.code == 200
                if (answered) return elapsedMs(start)
            }
            if (!expectAny) return -1L
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "httpPing failed: ${e.message}")
            if (!expectAny) return -1L
        }
        // Rung 2, per-node mode only.
        return socketConnectTime(req.url.host, req.url.port, TCP_FALLBACK_TIMEOUT_MS)
    }

    /**
     * ICMP echo probe via the system `ping` binary (works without root on modern Android).
     * Note: many nodes/CDNs drop ICMP, so -1 here often means "filtered", not "node down".
     *
     * The address family has to be chosen explicitly: `/system/bin/ping` is IPv4-only, so an IPv6
     * literal — or a host with only AAAA records — used to fail on the command line and read as
     * "filtered" when nothing had been measured at all. IPv6 targets go to `ping6`, falling back
     * to `ping -6` on builds that ship no `ping6`, and again to the next candidate when a binary
     * answers with a usage error instead of a probe.
     *
     * @param host node host or IP; a bracketed IPv6 literal is accepted.
     * @param timeoutSec per-probe timeout in seconds.
     * @return round-trip time in ms, or -1 on failure / no reply. Never 0 (see [httpPing]).
     */
    fun icmpPing(host: String, timeoutSec: Int = 2): Long {
        val target = normalizeIcmpTarget(pickIcmpAddress(host))
        if (target.isEmpty()) return -1L
        for (cmd in pingCommands(target, timeoutSec)) {
            // null = that binary could not probe at all, so try the next candidate; -1 = it ran
            // and got no reply, which is an answer (filtered or down) and ends the ladder.
            val rtt = runPing(cmd, timeoutSec)
            if (rtt != null) return rtt
        }
        return -1L
    }

    /** Runs one `ping` invocation. Returns ms, -1 for "ran, no reply", or null for "unusable". */
    private fun runPing(cmd: List<String>, timeoutSec: Int): Long? {
        var process: Process? = null
        return try {
            process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            // Read before waitFor: draining the pipe first is what keeps this from deadlocking.
            val out = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(timeoutSec + 2L, TimeUnit.SECONDS)
            when {
                exited && process.exitValue() == 0 -> parseIcmpRtt(out) ?: -1L
                looksLikeUsageError(out) -> null
                else -> -1L
            }
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "icmpPing: ${cmd.first()} unusable: ${e.message}")
            null
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "icmpPing failed: ${e.message}")
            -1L
        } finally {
            process?.destroy()
        }
    }

    /**
     * IPv4 first when the host has both, because that is the family `ping` handles unaided; an
     * AAAA-only host still returns IPv6 and is then pinged as IPv6 rather than mis-read as down.
     * A literal resolves to null here (see `HttpUtil.resolveHostToIP`) and is used as given.
     */
    private fun pickIcmpAddress(host: String): String {
        val h = host.trim()
        val resolved = HttpUtil.resolveHostToIP(h)?.filter { it.isNotBlank() } ?: return h
        return resolved.firstOrNull { !isIpv6Literal(it) } ?: resolved.firstOrNull() ?: h
    }

    /** `ping` wants a bare literal: no brackets, and an IPv4-mapped address pinged as IPv4. */
    private fun normalizeIcmpTarget(address: String): String {
        var a = address.trim()
        if (a.startsWith("[") && a.endsWith("]")) a = a.substring(1, a.length - 1)
        if (a.startsWith("::ffff:", ignoreCase = true) && a.contains('.')) a = a.substring(7)
        return a
    }

    /** A hostname can never contain ':', so a colon is a reliable IPv6-literal marker. */
    private fun isIpv6Literal(address: String) = address.contains(':')

    private fun pingCommands(target: String, timeoutSec: Int): List<List<String>> {
        val tail = listOf("-c", "1", "-W", timeoutSec.toString(), target)
        if (!isIpv6Literal(target)) return listOf(listOf(PING) + tail)
        val cmds = ArrayList<List<String>>(2)
        if (File(PING6).canExecute()) cmds.add(listOf(PING6) + tail)
        cmds.add(listOf(PING, "-6") + tail)
        return cmds
    }

    /** "64 bytes from …: icmp_seq=1 ttl=52 time=23.4 ms" → 23. Some builds print a comma. */
    private fun parseIcmpRtt(out: String): Long? {
        val raw = ICMP_TIME.find(out)?.groupValues?.get(1) ?: return null
        val ms = raw.replace(',', '.').toFloatOrNull() ?: return null
        return ms.roundToLong().coerceAtLeast(1L)
    }

    /** Tells "this binary/flag does not exist here" from "the node did not reply". */
    private fun looksLikeUsageError(out: String): Boolean {
        val l = out.lowercase()
        return "usage" in l || "unknown option" in l || "invalid option" in l ||
                "unrecognized option" in l || "bad option" in l || "illegal option" in l
    }

    private val ICMP_TIME = Regex("""time[=<]\s*([0-9]+(?:[.,][0-9]+)?)""")

    /** Elapsed ms, floored at 1 so a genuine sub-millisecond probe is not stored as "not tested". */
    private fun elapsedMs(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)

    /**
     * TCP connect latency to `url:port`, best of two tries, cancellable between tries.
     *
     * @return the faster handshake in ms, or -1 when neither try connected.
     */
    suspend fun tcping(url: String, port: Int): Long {
        var time = -1L
        for (k in 0 until 2) {
            val one = socketConnectTime(url, port)
            if (!currentCoroutineContext().isActive) {
                break
            }
            if (one != -1L && (time == -1L || one < time)) {
                time = one
            }
        }
        return time
    }

    /**
     * Measures the time taken to establish a TCP connection to a given host and port.
     *
     * Timed with [System.nanoTime]: a wall-clock delta can be shifted by an NTP correction
     * mid-handshake, which would print a latency nobody measured. The socket is registered for
     * [closeAllTcpSockets] and de-registered/closed in `finally`, so a failed probe cannot leave a
     * dead socket in that list for a later cancel to trip over.
     *
     * @param url The host to connect to.
     * @param port The port to connect to.
     * @param timeoutMs Connect timeout.
     * @return The connection time in milliseconds (never 0, see [httpPing]), or -1 on failure.
     */
    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 3000): Long {
        val socket = Socket()
        synchronized(this) {
            tcpTestingSockets.add(socket)
        }
        try {
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(url, port), timeoutMs)
            return elapsedMs(start)
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            try {
                socket.close()
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "socketConnectTime close failed: ${e.message}")
            }
        }
        return -1
    }

    /**
     * Closes all TCP sockets that are currently being tested.
     */
    fun closeAllTcpSockets() {
        synchronized(this) {
            tcpTestingSockets.forEach {
                it?.close()
            }
            tcpTestingSockets.clear()
        }
    }

    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return "(${country ?: "unknown"}) ${ip ?: "unknown"}"
    }
}
