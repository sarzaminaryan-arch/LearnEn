package ir.laxipatem.lexiflash.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Picks the most likely character set for a byte blob.
 *
 * Order of suspicion:
 * 1. A byte order mark: UTF-8, UTF-16LE or UTF-16BE. The BOM is stripped.
 * 2. Strict UTF-8. Any illegal sequence rules it out.
 * 3. Windows-1256, the usual legacy encoding of Persian text on Windows.
 * 4. ISO-8859-1, which accepts every byte and is therefore the last resort.
 *
 * The detector returns plain [String]s, so it is unit tested with raw byte
 * arrays and no Android dependencies.
 */
object EncodingDetector {

    private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF_16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF_16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /** The name of the charset that decodes [bytes] best. */
    fun detectCharset(bytes: ByteArray): Charset = when {
        startsWith(bytes, UTF_8_BOM) -> Charsets.UTF_8
        startsWith(bytes, UTF_16LE_BOM) -> Charsets.UTF_16LE
        startsWith(bytes, UTF_16BE_BOM) -> Charsets.UTF_16BE
        isValidUtf8(bytes) -> Charsets.UTF_8
        else -> charsetOrNull("windows-1256") ?: Charsets.ISO_8859_1
    }

    /** Decodes [bytes] with the detected charset, removing any BOM. */
    fun decode(bytes: ByteArray): String {
        var payload = bytes
        val charset = detectCharset(bytes)
        payload = when {
            startsWith(payload, UTF_8_BOM) -> payload.copyOfRange(3, payload.size)
            startsWith(payload, UTF_16LE_BOM) || startsWith(payload, UTF_16BE_BOM) ->
                payload.copyOfRange(2, payload.size)
            else -> payload
        }
        // A UTF-16 payload should have been caught by its BOM; if a caller hands
        // us BOMless UTF-16 we fall back to the byte-based decision above.
        return String(payload, charset)
    }

    /** Same as [decode] but also reports which charset won, for diagnostics. */
    fun decodeWithCharset(bytes: ByteArray): Pair<String, Charset> {
        val charset = detectCharset(bytes)
        return decode(bytes) to charset
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (index in prefix.indices) {
            if (bytes[index] != prefix[index]) return false
        }
        return true
    }

    private fun charsetOrNull(name: String): Charset? =
        runCatching { Charset.forName(name) }.getOrNull()
}
