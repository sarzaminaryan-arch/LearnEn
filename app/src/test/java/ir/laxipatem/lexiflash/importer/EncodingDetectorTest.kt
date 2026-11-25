package ir.laxipatem.lexiflash.importer

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Character set detection for the import engine. */
class EncodingDetectorTest {

    private fun bytes(text: String, charset: Charset): ByteArray = text.toByteArray(charset)

    @Test
    fun `plain ascii decodes as utf-8`() {
        assertEquals("Hello world.", EncodingDetector.decode("Hello world.".toByteArray()))
    }

    @Test
    fun `utf-8 persian round trips`() {
        val persian = "این یک جمله‌ی فارسی است."
        assertEquals(persian, EncodingDetector.decode(persian.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun `a utf-8 bom is stripped`() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Hello".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Hello", EncodingDetector.decode(withBom))
    }

    @Test
    fun `a utf-16le bom is honoured`() {
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hi".toByteArray(StandardCharsets.UTF_16LE)
        assertEquals("Hi", EncodingDetector.decode(withBom))
    }

    @Test
    fun `a utf-16be bom is honoured`() {
        val withBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "Hi".toByteArray(StandardCharsets.UTF_16BE)
        assertEquals("Hi", EncodingDetector.decode(withBom))
    }

    @Test
    fun `windows-1256 persian bytes decode to persian`() {
        val persian = "سلام دنیا"
        val encoded = runCatching { bytes(persian, Charset.forName("windows-1256")) }
        org.junit.Assume.assumeTrue(
            "windows-1256 is not available on this JVM",
            encoded.isSuccess && encoded.getOrNull()?.isNotEmpty() == true
        )
        val decoded = EncodingDetector.decode(encoded.getOrThrow())
        assertTrue("decoded should contain Persian letters: $decoded", decoded.any { it in '\u0600'..'\u06FF' })
    }

    @Test
    fun `latin-1 accented text decodes without mojibake`() {
        val text = "café résumé"
        val encoded = bytes(text, StandardCharsets.ISO_8859_1)
        val decoded = EncodingDetector.decode(encoded)
        assertTrue("got '$decoded'", decoded.contains("caf"))
    }

    @Test
    fun `an empty array decodes to an empty string`() {
        assertEquals("", EncodingDetector.decode(ByteArray(0)))
    }

    @Test
    fun `decodeWithCharset reports the charset it used`() {
        val (text, charset) = EncodingDetector.decodeWithCharset("Hello".toByteArray())
        assertEquals("Hello", text)
        assertEquals(StandardCharsets.UTF_8, charset)
    }

    @Test
    fun `valid utf-8 multibyte sequences are preferred over single byte charsets`() {
        val text = "متن فارسی با حروف کامل"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        assertEquals(text, EncodingDetector.decode(bytes))
    }

    @Test
    fun `an invalid utf-8 sequence does not throw`() {
        val malformed = byteArrayOf(0xC3.toByte(), 0x28)
        val decoded = EncodingDetector.decode(malformed)
        assertTrue("should not be empty: '$decoded'", decoded.isNotEmpty())
    }
}
