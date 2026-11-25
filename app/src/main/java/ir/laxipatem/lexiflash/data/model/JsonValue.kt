package ir.laxipatem.lexiflash.data.model

/**
 * A tiny, dependency-free JSON tree.
 *
 * The app needs to read and write two JSON documents (deck files and the online
 * catalogue). Pulling in a serialisation library for that would be a large
 * dependency for very little gain, and `org.json` is only a stub on the JVM
 * unit-test classpath. This implementation is ~200 lines, has no reflection and
 * behaves identically on device and in plain JVM tests.
 *
 * It implements RFC 8259: objects, arrays, strings with escapes, numbers,
 * booleans and null. Duplicate object keys keep the last value.
 */
sealed class JsonValue {

    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue()

    // --- Accessors used by the deck and catalogue readers -------------------

    fun asStringOrNull(): String? = (this as? Str)?.value

    fun asDoubleOrNull(): Double? = when (this) {
        is Num -> value
        is Str -> value.trim().toDoubleOrNull()
        is Bool -> if (value) 1.0 else 0.0
        else -> null
    }

    fun asIntOrNull(): Int? = asDoubleOrNull()?.let {
        if (it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE) it.toInt() else null
    }

    fun asLongOrNull(): Long? = asDoubleOrNull()?.let {
        if (it.isFinite() && it >= Long.MIN_VALUE && it <= Long.MAX_VALUE) it.toLong() else null
    }

    fun asBoolOrNull(): Boolean? = when (this) {
        is Bool -> value
        is Str -> when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    fun asArrayOrNull(): List<JsonValue>? = (this as? Arr)?.items

    operator fun get(key: String): JsonValue? = (this as? Obj)?.entries?.get(key)

    /** Convenience: `obj.string("title")`. */
    fun string(key: String): String? = get(key)?.asStringOrNull()

    fun int(key: String): Int? = get(key)?.asIntOrNull()

    fun long(key: String): Long? = get(key)?.asLongOrNull()

    fun bool(key: String): Boolean? = get(key)?.asBoolOrNull()

    fun array(key: String): List<JsonValue>? = get(key)?.asArrayOrNull()

    fun obj(key: String): JsonValue? = get(key)

    // --- Writer -------------------------------------------------------------

    /** Serialises this tree back to compact JSON text. */
    fun toJsonString(): String = buildString { writeTo(this) }

    /** Serialises this tree with two-space indentation, for exported decks. */
    fun toPrettyJsonString(): String = buildString { writePretty(this, 0) }

    private fun writeTo(out: StringBuilder) {
        when (this) {
            is Null -> out.append("null")
            is Bool -> out.append(if (value) "true" else "false")
            is Num -> out.append(formatNumber(value))
            is Str -> writeEscaped(out, value)
            is Arr -> {
                out.append('[')
                items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    item.writeTo(out)
                }
                out.append(']')
            }
            is Obj -> {
                out.append('{')
                var first = true
                for ((key, value) in entries) {
                    if (!first) out.append(',')
                    first = false
                    writeEscaped(out, key)
                    out.append(':')
                    value.writeTo(out)
                }
                out.append('}')
            }
        }
    }

    private fun writePretty(out: StringBuilder, depth: Int) {
        val pad = "  ".repeat(depth + 1)
        val closingPad = "  ".repeat(depth)
        when (this) {
            is Arr -> {
                if (items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                items.forEachIndexed { index, item ->
                    out.append(pad)
                    item.writePretty(out, depth + 1)
                    if (index != items.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.append(closingPad).append(']')
            }
            is Obj -> {
                if (entries.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                var index = 0
                val lastIndex = entries.size - 1
                for ((key, value) in entries) {
                    out.append(pad)
                    writeEscaped(out, key)
                    out.append(": ")
                    value.writePretty(out, depth + 1)
                    if (index != lastIndex) out.append(',')
                    out.append('\n')
                    index++
                }
                out.append(closingPad).append('}')
            }
            else -> writeTo(out)
        }
    }

    companion object {

        fun of(value: String?): JsonValue = if (value == null) Null else Str(value)

        fun of(value: Int?): JsonValue = if (value == null) Null else Num(value.toDouble())

        fun of(value: Long?): JsonValue = if (value == null) Null else Num(value.toDouble())

        fun of(value: Double?): JsonValue = if (value == null) Null else Num(value)

        fun of(value: Boolean?): JsonValue = if (value == null) Null else Bool(value)

        fun obj(vararg pairs: Pair<String, JsonValue>): Obj = Obj(linkedMapOf(*pairs))

        fun obj(pairs: Map<String, JsonValue>): Obj = Obj(LinkedHashMap(pairs))

        fun arr(items: List<JsonValue>): Arr = Arr(items)

        /** Omits a key entirely when the value is JSON null. */
        fun mapOfDefined(vararg pairs: Pair<String, JsonValue?>): Obj {
            val map = LinkedHashMap<String, JsonValue>()
            for ((key, value) in pairs) {
                if (value != null) map[key] = value
            }
            return Obj(map)
        }

        private fun formatNumber(value: Double): String {
            if (!value.isFinite()) return "0"
            val asLong = value.toLong()
            return if (asLong.toDouble() == value) asLong.toString() else value.toString()
        }

        private fun writeEscaped(out: StringBuilder, raw: String) {
            out.append('"')
            for (character in raw) {
                when (character) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> {
                        if (character < ' ') {
                            out.append("\\u")
                            out.append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            out.append(character)
                        }
                    }
                }
            }
            out.append('"')
        }
    }
}

/** Thrown when JSON text cannot be parsed. */
class JsonParseException(message: String) : Exception(message)
