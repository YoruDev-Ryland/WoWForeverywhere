package app.gamenative.wow.addons.provider

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal val ProviderJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/**
 * Performs a request on the IO dispatcher and returns the body text. Returns null on 404, throws
 * [IOException] on other non-2xx responses and network failures.
 */
internal suspend fun OkHttpClient.fetchText(
    url: String,
    headers: Headers = Headers.headersOf(),
    postJson: String? = null,
): String? = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).headers(headers)
    if (postJson != null) builder.post(postJson.toRequestBody("application/json".toMediaType()))
    newCall(builder.build()).execute().use { response ->
        when {
            response.code == 404 -> null
            !response.isSuccessful -> throw IOException("HTTP ${response.code} for ${response.request.url.encodedPath}")
            else -> response.body.string()
        }
    }
}
