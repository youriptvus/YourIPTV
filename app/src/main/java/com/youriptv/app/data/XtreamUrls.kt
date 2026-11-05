package com.youriptv.app.data

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** URL construction for the Xtream Codes API (pure, unit-testable). */
object XtreamUrls {

    fun normalizeBase(server: String): String {
        var s = server.trim()
        if (s.isEmpty()) return s
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        for (suffix in listOf("/player_api.php", "/get.php", "/xmltv.php")) {
            val i = s.indexOf(suffix, ignoreCase = true)
            if (i > 0) s = s.substring(0, i)
        }
        return s.trimEnd('/')
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    fun api(base: String, user: String, pass: String, action: String? = null, extra: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder(normalizeBase(base))
            .append("/player_api.php?username=").append(enc(user))
            .append("&password=").append(enc(pass))
        if (action != null) sb.append("&action=").append(enc(action))
        for ((k, v) in extra) sb.append('&').append(enc(k)).append('=').append(enc(v))
        return sb.toString()
    }

    fun xmltv(base: String, user: String, pass: String) =
        "${normalizeBase(base)}/xmltv.php?username=${enc(user)}&password=${enc(pass)}"

    fun live(base: String, user: String, pass: String, streamId: String, ext: String = "ts") =
        "${normalizeBase(base)}/live/${enc(user)}/${enc(pass)}/$streamId.$ext"

    fun movie(base: String, user: String, pass: String, streamId: String, ext: String) =
        "${normalizeBase(base)}/movie/${enc(user)}/${enc(pass)}/$streamId.${ext.ifBlank { "mp4" }}"

    fun episode(base: String, user: String, pass: String, episodeId: String, ext: String) =
        "${normalizeBase(base)}/series/${enc(user)}/${enc(pass)}/$episodeId.${ext.ifBlank { "mp4" }}"

    /** Catch-up (timeshift) URL; [startUtc] is the programme start, provider expects server-local time. */
    fun timeshift(
        base: String, user: String, pass: String, streamId: String,
        startUtc: Long, durationMinutes: Int, serverZone: TimeZone = TimeZone.getTimeZone("UTC"),
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = serverZone }
        return "${normalizeBase(base)}/timeshift/${enc(user)}/${enc(pass)}/$durationMinutes/${fmt.format(Date(startUtc))}/$streamId.ts"
    }

    /** Resolves an Xtream `server_info.timezone` (IANA id such as "Europe/Berlin"); unknown or blank means UTC. */
    fun serverZone(id: String): TimeZone {
        val t = id.trim()
        if (t.isEmpty()) return TimeZone.getTimeZone("UTC")
        val zone = TimeZone.getTimeZone(t)
        // TimeZone.getTimeZone returns GMT for unknown ids; accept that only when GMT was actually requested.
        return if (zone.id == "GMT" && !t.equals("GMT", true)) TimeZone.getTimeZone("UTC") else zone
    }

    /** Switches an Xtream live URL between MPEG-TS and HLS according to the user's live-format setting. */
    fun applyLiveFormat(url: String, format: String): String {
        if (!url.contains("/live/")) return url
        val dot = url.lastIndexOf('.')
        if (dot < url.lastIndexOf('/')) return url
        val ext = url.substring(dot + 1)
        if (ext != "ts" && ext != "m3u8") return url
        return url.substring(0, dot + 1) + (if (format == "m3u8") "m3u8" else "ts")
    }

    /** Builds an M3U Plus download URL for Xtream-compatible providers. */
    fun m3u(base: String, user: String, pass: String) =
        "${normalizeBase(base)}/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"

    data class Login(val base: String, val username: String, val password: String)

    /**
     * Recognises an Xtream-style get.php / player_api.php URL that embeds a username and password
     * so a pasted M3U link can be converted into an Xtream Codes login.
     */
    fun parseLogin(url: String): Login? {
        val s = url.trim()
        val q = s.indexOf('?')
        if (q < 0) return null
        val path = s.substring(0, q)
        if (!path.endsWith("/get.php", true) && !path.endsWith("/player_api.php", true)) return null
        val params = s.substring(q + 1).split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).lowercase() to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val user = params["username"].orEmpty()
        val pass = params[PASS_KEY].orEmpty()
        if (user.isEmpty() || pass.isEmpty()) return null
        return Login(normalizeBase(path), user, pass)
    }

    private const val PASS_KEY = "password"
}