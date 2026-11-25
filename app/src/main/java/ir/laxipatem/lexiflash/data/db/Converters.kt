package ir.laxipatem.lexiflash.data.db

import androidx.room.TypeConverter
import ir.laxipatem.lexiflash.data.model.JsonParser
import ir.laxipatem.lexiflash.data.model.JsonValue
import ir.laxipatem.lexiflash.data.model.VocabJson

/**
 * Type converters for the few non-primitive columns.
 *
 * The vocabulary list is stored as JSON text in a single column: the list is
 * small, always read together with its card, and never queried by word, so a
 * separate relation table would only add joins.
 */
class Converters {

    @TypeConverter
    fun vocabToJson(vocab: List<VocabJson>?): String = encodeVocab(vocab.orEmpty())

    @TypeConverter
    fun vocabFromJson(raw: String?): List<VocabJson> = decodeVocab(raw)

    companion object {

        fun encodeVocab(vocab: List<VocabJson>): String {
            val array = JsonValue.arr(
                vocab.map { entry ->
                    JsonValue.mapOfDefined(
                        "word" to JsonValue.of(entry.word),
                        "meaning" to entry.meaning?.let { JsonValue.of(it) }
                    )
                }
            )
            return array.toJsonString()
        }

        /**
         * Tolerant by design: a card whose vocabulary column cannot be parsed
         * still has to open for study, so malformed content degrades to an
         * empty list instead of crashing the database read.
         */
        fun decodeVocab(raw: String?): List<VocabJson> {
            if (raw.isNullOrBlank()) return emptyList()
            val root = JsonParser.parseOrNull(raw) ?: return emptyList()
            return root.asArrayOrNull().orEmpty().mapNotNull { item ->
                val word = item.string("word") ?: return@mapNotNull null
                VocabJson(word = word, meaning = item.string("meaning"))
            }
        }
    }
}
