package com.programmersbox.claudemanager.data.cdm

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** A device from Claude Device Manager (`cdm devices`). */
data class CdmDevice(
    val serial: String,
    val model: String,
    val platform: String,
    val real: Boolean,
    val category: String,
    /** Claude session id holding the device, or null when free. */
    val holderSessionId: String?,
)

/**
 * Optional, read-only integration with Claude Device Manager. Talks to its local daemon
 * (`GET /cdm/list` on the port in `~/.claude-device-manager/port`); the `cdm` CLI itself refuses
 * to run outside Claude Code's Bash tool. Results are cached for [CACHE_MS].
 */
class CdmClient {
    private val portFile: Path = System.getenv("CDM_PORT_FILE")?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".claude-device-manager", "port")
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private var cached: List<CdmDevice> = emptyList()
    private var cachedAt = 0L

    /** True when the Device Manager has been installed on this machine (its port file exists). */
    val isAvailable: Boolean get() = Files.exists(portFile)

    fun devices(): List<CdmDevice> {
        if (!isAvailable) return emptyList()
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_MS) return cached
        cachedAt = now
        cached = runCatching {
            val port = Files.readString(portFile).trim()
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/cdm/list"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) parse(response.body()) else emptyList()
        }.getOrDefault(emptyList()) // Daemon not running: no devices.
        return cached
    }

    companion object {
        private const val CACHE_MS = 5_000L

        // MBX6P240401889  S6003L · Android real · phone · in use by be0ee6eb-...:main
        private val LINE = Regex("""^(\S+)\s+(.+?) · (\S+) (real|virtual) · (\S+) · (free|in use by (\S+))\s*$""")

        fun parse(output: String): List<CdmDevice> = output.lineSequence().mapNotNull { line ->
            val m = LINE.find(line.trim()) ?: return@mapNotNull null
            val (serial, model, platform, kind, category, _, holder) = m.destructured
            CdmDevice(
                serial = serial,
                model = model,
                platform = platform,
                real = kind == "real",
                category = category,
                holderSessionId = holder.takeIf { it.isNotEmpty() }?.substringBefore(':'),
            )
        }.toList()
    }
}
