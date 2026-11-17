package app.gamenative.ui.screen.wow.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** URL boundary for Battle.net's browser challenge. Never include a rejected URL in an error. */
internal object BrowserAuthUrl {
    private const val MAX_URL_LENGTH = 16_384
    private const val MAX_TICKET_LENGTH = 2_048
    private val navigationDomains = listOf("battle.net", "blizzard.com", "battlenet.com.cn", "blizzard.cn")

    fun isInitialUrl(value: String): Boolean = httpsHost(value)?.let { belongsTo(it, "battle.net") } == true

    fun isNavigationUrl(value: String): Boolean = httpsHost(value)?.let { host ->
        navigationDomains.any { belongsTo(host, it) }
    } == true

    /** Only the main-frame callback may call this. Other URLs, including lookalike hosts, return null. */
    fun ticket(value: String): ByteArray? {
        val uri = parse(value) ?: return null
        if (!uri.scheme.equals("http", ignoreCase = true) || !uri.host.equals("localhost", ignoreCase = true) ||
            uri.port != 0 || uri.rawUserInfo != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/")
        ) return null
        val query = uri.rawQuery ?: return null
        if (';' in query) return null
        // Battle.net can add parameters next to the ticket, so others are ignored. Exactly one ST avoids
        // duplicate-key ambiguity.
        val tickets = query.split('&').filter { it.startsWith("ST=") }
        if (tickets.size != 1) return null
        val decoded = try {
            URLDecoder.decode(tickets.single().substring(3), StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded.isEmpty() || decoded.length > MAX_TICKET_LENGTH || decoded.any { it.code !in 33..126 }) return null
        return decoded.toByteArray(StandardCharsets.US_ASCII)
    }

    /**
     * The shape of an address the dialog refused, for BrowserLogin.log: its scheme, host, port, whether it has a path,
     * and the query parameter names. Never values or path segments, which can carry tickets or account details.
     */
    fun describe(value: String): String {
        val uri = try {
            URI(value.take(MAX_URL_LENGTH))
        } catch (_: Exception) {
            return "unparsable address (${value.substringBefore(':').take(16).filter { it.isLetterOrDigit() || it in "+-." }}:)"
        }
        if (uri.isOpaque) return "${uri.scheme}: address"
        val names = uri.rawQuery?.split('&', ';')?.map { it.substringBefore('=').take(32) }?.filter { it.isNotEmpty() }.orEmpty()
        val path = if (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") "no path" else "a path"
        return "${uri.scheme}://${uri.host?.lowercase(Locale.ROOT)}${if (uri.port >= 0) ":" + uri.port else ""}, $path, " +
            (if (names.isEmpty()) "no query" else "query names " + names.joinToString(",")) +
            (if (uri.rawFragment != null) ", a fragment" else "")
    }

    /** The dialog shows the site's origin only. The path and query can contain sign-in credentials. */
    fun displayOrigin(value: String): String? = httpsHost(value)?.let { "https://$it" }

    private fun httpsHost(value: String): String? {
        val uri = parse(value) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
        return uri.host?.lowercase(Locale.ROOT)
    }

    private fun belongsTo(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    private fun parse(value: String): URI? {
        if (value.length > MAX_URL_LENGTH || value.any { it.code <= 32 || it.code >= 127 }) return null
        return try {
            URI(value).takeUnless { it.isOpaque }
        } catch (_: Exception) {
            null
        }
    }
}
