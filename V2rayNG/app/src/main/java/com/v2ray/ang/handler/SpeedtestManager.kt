package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

object SpeedtestManager {

    private val tcpTestingSockets = ArrayList<Socket?>()

    /**
     * Measures the TCP connection time to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    /**
     * Direct HTTP latency probe (no proxy) to a captive-portal-style generate_204 endpoint.
     * Measures time-to-first-byte of the response headers.
     *
     * @param url the test URL (expects HTTP 204/200).
     * @param timeoutMs per-probe timeout.
     * @return latency in ms, or -1 on failure / unexpected status / redirect.
     */
    fun httpPing(url: String, timeoutMs: Int = 5000): Long {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .build()
        val req = okhttp3.Request.Builder()
            .url(url)
            .head()
            .header("Connection", "close")
            .build()
        val start = System.nanoTime()
        return try {
            client.newCall(req).execute().use { r ->
                val ms = (System.nanoTime() - start) / 1_000_000
                if (r.code == 204 || r.code == 200) ms else -1L
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "httpPing failed: ${e.message}")
            -1L
        }
    }

    /**
     * ICMP echo probe via the system `ping` binary (works without root on modern Android).
     * Note: many nodes/CDNs drop ICMP, so -1 here often means "filtered", not "node down".
     *
     * @param host node host or IP.
     * @param timeoutSec per-probe timeout in seconds.
     * @return round-trip time in ms, or -1 on failure / no reply.
     */
    fun icmpPing(host: String, timeoutSec: Int = 2): Long {
        val ip = HttpUtil.resolveHostToIP(host)?.firstOrNull() ?: host
        var process: Process? = null
        return try {
            process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", ip)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) {
                Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloat()?.toLong() ?: -1L
            } else {
                -1L
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "icmpPing failed: ${e.message}")
            -1L
        } finally {
            process?.destroy()
        }
    }

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
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int): Long {
        try {
            val socket = Socket()
            synchronized(this) {
                tcpTestingSockets.add(socket)
            }
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(url, port), 3000)
            val time = System.currentTimeMillis() - start
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            socket.close()
            return time
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
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
