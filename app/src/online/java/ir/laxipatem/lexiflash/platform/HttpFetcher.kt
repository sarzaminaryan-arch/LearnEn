package ir.laxipatem.lexiflash.platform

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a bounded HTTP GET. */
internal data class HttpTextResponse(
    val body: String,
    val contentType: String?,
    val finalUrl: String
)

/**
 * Minimal HTTPS-only fetcher built on `HttpURLConnection`.
 *
 * Deliberately not a general-purpose HTTP client. It refuses plaintext HTTP,
 * refuses redirects to plaintext HTTP, enforces a byte ceiling while reading
 * (so a huge or endless response cannot exhaust memory) and never sends
 * cookies or credentials.
 */
internal object HttpFetcher {

    private const val USER_AGENT = "LexiFlash/1.0 (+https://github.com/laxipatem)"

    private class HttpStatusException(message: String) : IOException(message)

    suspend fun getText(url: String, maxBytes: Int): Result<HttpTextResponse> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(fetch(url, maxBytes))
            } catch (e: HttpStatusException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Result.failure(IOException("That address is not a valid URL.", e))
            } catch (e: SecurityException) {
                Result.failure(IOException("The connection was blocked by the platform.", e))
            }
        }

    private fun fetch(rawUrl: String, maxBytes: Int): HttpTextResponse {
        val url = URL(rawUrl)
        requireHttps(url)

        var connection: HttpURLConnection? = null
        try {
            var current = url
            var redirects = 0
            while (true) {
                connection?.disconnect()
                connection = (current.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = ir.laxipatem.lexiflash.online.CatalogDefaults.CONNECT_TIMEOUT_MS
                    readTimeout = ir.laxipatem.lexiflash.online.CatalogDefaults.READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/json, text/plain;q=0.9, */*;q=0.5")
                    setRequestProperty("Accept-Encoding", "identity")
                }

                val code = connection.responseCode
                Log.d("LexiImport", "HTTP response code=$code host=${current.host}")
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw HttpStatusException("The server redirected without a Location header.")
                    if (redirects++ >= MAX_REDIRECTS) {
                        throw HttpStatusException("Too many redirects.")
                    }
                    val next = URL(current, location)
                    requireHttps(next)
                    current = next
                    continue
                }
                if (code !in 200..299) {
                    throw HttpStatusException("The server answered with HTTP $code.")
                }

                val declaredLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
                if (declaredLength != null && declaredLength > maxBytes) {
                    throw HttpStatusException(
                        "The response is larger than the ${maxBytes / (1024 * 1024)} MB limit."
                    )
                }

                val body = readBounded(connection, maxBytes)
                Log.d("LexiImport", "HTTP response body bytes=${body.size} host=${current.host}")
                val charset = charsetOf(connection.contentType)
                return HttpTextResponse(
                    // ByteArray.toString expects the Charset itself, not its name.
                    body = body.toString(charset),
                    contentType = connection.contentType,
                    finalUrl = current.toString()
                )
            }
        } finally {
            connection?.disconnect()
        }
    }

    private const val MAX_REDIRECTS = 3

    private fun requireHttps(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("Only HTTPS addresses are allowed, got ${url.protocol}.")
        }
    }

    /** Reads at most [maxBytes] + 1 bytes so oversize bodies are detectable. */
    private fun readBounded(connection: HttpURLConnection, maxBytes: Int): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val output = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
        var total = 0
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw HttpStatusException(
                        "The response is larger than the ${maxBytes / (1024 * 1024)} MB limit."
                    )
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    /**
     * Picks UTF-8 unless the server names a charset. The metadata check is
     * deliberately soft: a missing or odd Content-Type never fails the request,
     * because the body is validated as JSON by the caller anyway.
     */
    private fun charsetOf(contentType: String?): java.nio.charset.Charset {
        if (contentType != null) {
            val marker = "charset="
            val index = contentType.lowercase().indexOf(marker)
            if (index >= 0) {
                val raw = contentType.substring(index + marker.length)
                    .substringBefore(';')
                    .trim()
                    .trim('"')
                runCatching { return java.nio.charset.Charset.forName(raw) }
            }
        }
        return Charsets.UTF_8
    }
}

