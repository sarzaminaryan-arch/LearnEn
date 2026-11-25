package ir.laxipatem.lexiflash.data.model

/**
 * Hand-written RFC 8259 JSON parser producing [JsonValue] trees.
 *
 * Written by hand rather than using `org.json` so that the exact same parsing
 * code runs on device, in JVM unit tests and in the Python-generated content
 * pipeline. The parser is iterative on depth only through recursion; the
 * configured [maxDepth] guards against pathological input.
 */
object JsonParser {

    /** Parses [text]; throws [JsonParseException] on malformed input. */
    fun parse(text: String, maxDepth: Int = 64): JsonValue =
        Reader(text, maxDepth).parseDocument()

    /** Parses [text], returning null instead of throwing. */
    fun parseOrNull(text: String, maxDepth: Int = 64): JsonValue? = try {
        parse(text, maxDepth)
    } catch (_: JsonParseException) {
        null
    }

    private class Reader(private val text: String, private val maxDepth: Int) {
        private var pos = 0

        fun parseDocument(): JsonValue {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            if (pos != text.length) fail("Unexpected trailing content")
            return value
        }

        private fun parseValue(depth: Int): JsonValue {
            if (depth > maxDepth) fail("Nesting deeper than $maxDepth levels")
            if (pos >= text.length) fail("Unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> JsonValue.Str(parseString())
                't' -> parseLiteral("true", JsonValue.Bool(true))
                'f' -> parseLiteral("false", JsonValue.Bool(false))
                'n' -> parseLiteral("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun parseObject(depth: Int): JsonValue {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return JsonValue.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a string key")
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries[key] = parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonValue.Obj(entries)
                    }
                    else -> fail("Expected ',' or '}' in object")
                }
            }
        }

        private fun parseArray(depth: Int): JsonValue {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return JsonValue.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items += parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonValue.Arr(items)
                    }
                    else -> fail("Expected ',' or ']' in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= text.length) fail("Unterminated string")
                when (val c = text[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) fail("Unterminated escape")
                        when (val escape = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Truncated \\u escape")
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: fail("Bad \\u escape '$hex'")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Unknown escape '\\$escape'")
                        }
                    }
                    else -> {
                        if (c < ' ') fail("Raw control character in string")
                        sb.append(c)
                    }
                }
            }
        }

        private fun parseNumber(): JsonValue {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && text[pos].isDigit()) pos++
            if (peek() == '.') {
                pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            val slice = text.substring(start, pos)
            val value = slice.toDoubleOrNull() ?: fail("Invalid number '$slice'")
            return JsonValue.Num(value)
        }

        private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, pos)) fail("Expected '$literal'")
            pos += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < text.length) {
                when (text[pos]) {
                    ' ', '\t', '\n', '\r' -> pos++
                    else -> return
                }
            }
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null

        private fun expect(expected: Char) {
            if (peek() != expected) fail("Expected '$expected'")
            pos++
        }

        private fun fail(message: String): Nothing {
            val context = text.substring(maxOf(0, pos - 20), minOf(text.length, pos + 20))
                .replace("\n", "\\n")
            throw JsonParseException("$message at offset $pos near \"$context\"")
        }
    }
}
