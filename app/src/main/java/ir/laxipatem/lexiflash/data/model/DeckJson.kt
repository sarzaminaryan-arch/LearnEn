package ir.laxipatem.lexiflash.data.model

/**
 * In-memory representation of a `deck.json` document, plus validation and
 * (de)serialisation. See `docs/DECK_FORMAT.md` for the authoritative schema.
 *
 * Nothing here touches Android, so the whole pipeline is covered by plain JVM
 * unit tests.
 */
data class DeckJson(
    val version: Int,
    val title: String,
    val sourceLang: String,
    val targetLang: String,
    val license: String,
    val parts: List<DeckPartJson>
) {
    val cardCount: Int get() = parts.sumOf { it.cards.size }

    fun allCards(): List<DeckCardJson> = parts.flatMap { it.cards }
}

data class DeckPartJson(
    val index: Int,
    val cards: List<DeckCardJson>
)

data class DeckCardJson(
    val id: String,
    val english: String,
    val persian: String?,
    val ipa: String?,
    val vocab: List<VocabJson>,
    val note: String?,
    val difficulty: Double?,
    val reviewed: Boolean,
    val source: String?,
    val license: String?,
    val startMs: Long?
)

data class VocabJson(
    val word: String,
    val meaning: String?
)

/** A validation failure with a message that can be shown to the user. */
class DeckValidationException(message: String) : Exception(message)

/**
 * Schema constants. Anything outside these bounds is rejected rather than
 * silently accepted, so a corrupt or hostile deck cannot exhaust memory.
 */
object DeckSchema {
    const val CURRENT_VERSION: Int = 1
    const val MAX_CARDS: Int = 50_000
    const val MAX_PARTS: Int = 200
    const val MAX_TEXT_LENGTH: Int = 2_000
    const val MAX_VOCAB_PER_CARD: Int = 12
}

object DeckJsonCodec {

    /**
     * Parses and validates a deck document.
     *
     * @throws DeckValidationException when the document violates the schema.
     */
    fun parse(text: String): DeckJson {
        val root = JsonParser.parseOrNull(text)
            ?: throw DeckValidationException("The file is not valid JSON.")
        return fromJson(root)
    }

    /** Parses without throwing; returns the failure inside the [Result]. */
    fun parseResult(text: String): Result<DeckJson> = try {
        Result.success(parse(text))
    } catch (e: DeckValidationException) {
        Result.failure(e)
    } catch (e: JsonParseException) {
        Result.failure(DeckValidationException(e.message ?: "Invalid JSON"))
    }

    fun fromJson(root: JsonValue): DeckJson {
        if (root !is JsonValue.Obj) {
            throw DeckValidationException("The deck root must be a JSON object.")
        }

        val version = root.int("version")
            ?: throw DeckValidationException("Missing required field \"version\".")
        if (version <= 0) {
            throw DeckValidationException("Field \"version\" must be a positive integer.")
        }
        if (version > DeckSchema.CURRENT_VERSION) {
            throw DeckValidationException(
                "This deck was written for schema version $version, but this app only supports up to " +
                    "version ${DeckSchema.CURRENT_VERSION}. Please update the app."
            )
        }

        val title = root.string("title")?.trim()
        if (title.isNullOrEmpty()) {
            throw DeckValidationException("Missing required field \"title\".")
        }

        val sourceLang = root.string("source_lang")?.trim()
        if (sourceLang.isNullOrEmpty()) {
            throw DeckValidationException("Missing required field \"source_lang\".")
        }
        val targetLang = root.string("target_lang")?.trim()
        if (targetLang.isNullOrEmpty()) {
            throw DeckValidationException("Missing required field \"target_lang\".")
        }

        val license = root.string("license")?.trim().orEmpty()

        val rawParts = root.array("parts")
            ?: throw DeckValidationException("Missing required array \"parts\".")
        if (rawParts.isEmpty()) {
            throw DeckValidationException("\"parts\" must contain at least one part.")
        }
        if (rawParts.size > DeckSchema.MAX_PARTS) {
            throw DeckValidationException(
                "\"parts\" has ${rawParts.size} entries, the maximum is ${DeckSchema.MAX_PARTS}."
            )
        }

        val parts = rawParts.mapIndexed { position, rawPart ->
            if (rawPart !is JsonValue.Obj) {
                throw DeckValidationException("Part ${position + 1} must be a JSON object.")
            }
            val index = rawPart.int("index")
                ?: throw DeckValidationException("Part ${position + 1} is missing \"index\".")

            val rawCards = rawPart.array("cards")
                ?: throw DeckValidationException("Part $index is missing the \"cards\" array.")

            val cards = rawCards.mapIndexed { cardPosition, rawCard ->
                parseCard(rawCard, index, cardPosition)
            }
            DeckPartJson(index = index, cards = cards)
        }

        // Part indices must be unique and form a contiguous 1..N run.
        val indices = parts.map { it.index }
        val distinct = indices.toSet()
        if (distinct.size != indices.size) {
            throw DeckValidationException("Part indices must be unique, found: ${indices.sorted()}.")
        }
        val expected = (1..parts.size).toList()
        if (indices.sorted() != expected) {
            throw DeckValidationException(
                "Part indices must be contiguous starting at 1. Expected $expected but found ${indices.sorted()}."
            )
        }

        val totalCards = parts.sumOf { it.cards.size }
        if (totalCards > DeckSchema.MAX_CARDS) {
            throw DeckValidationException(
                "The deck has $totalCards cards, the maximum is ${DeckSchema.MAX_CARDS}."
            )
        }

        val cardIds = parts.flatMap { part -> part.cards.map { it.id } }
        val duplicate = cardIds.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) {
            throw DeckValidationException("Card id \"${duplicate.key}\" appears ${duplicate.value} times; ids must be unique.")
        }

        return DeckJson(
            version = version,
            title = title,
            sourceLang = sourceLang,
            targetLang = targetLang,
            license = license,
            parts = parts.sortedBy { it.index }
        )
    }

    private fun parseCard(raw: JsonValue, partIndex: Int, position: Int): DeckCardJson {
        if (raw !is JsonValue.Obj) {
            throw DeckValidationException("Card ${position + 1} of part $partIndex must be a JSON object.")
        }
        val where = "Card ${position + 1} of part $partIndex"

        val id = raw.string("id")?.trim()
        if (id.isNullOrEmpty()) throw DeckValidationException("$where is missing a non-empty \"id\".")
        if (id.length > 128) throw DeckValidationException("$where has an id longer than 128 characters.")

        val english = raw.string("english")?.trim()
        if (english.isNullOrEmpty()) throw DeckValidationException("$where is missing a non-empty \"english\".")
        if (english.length > DeckSchema.MAX_TEXT_LENGTH) {
            throw DeckValidationException("$where has an English sentence longer than ${DeckSchema.MAX_TEXT_LENGTH} characters.")
        }

        val persian = raw.string("persian")?.trim()?.takeIf { it.isNotEmpty() }
            ?: run {
                // An explicit null is allowed only when the card is marked pending.
                val explicitNull = raw.get("persian") == null || raw.get("persian") is JsonValue.Null
                if (!explicitNull) null else null
            }

        val ipa = raw.string("ipa")?.trim()?.takeIf { it.isNotEmpty() }
        val note = raw.string("note")?.trim()?.takeIf { it.isNotEmpty() }

        val vocabValue = raw.get("vocab")
        val vocab: List<VocabJson> = when {
            vocabValue == null || vocabValue is JsonValue.Null -> emptyList()
            else -> {
                val items = vocabValue.asArrayOrNull()
                    ?: throw DeckValidationException("$where has a \"vocab\" entry that is not an array.")
                if (items.size > DeckSchema.MAX_VOCAB_PER_CARD) {
                    throw DeckValidationException(
                        "$where lists ${items.size} vocabulary items, the maximum is ${DeckSchema.MAX_VOCAB_PER_CARD}."
                    )
                }
                items.mapIndexed { vocabPosition, item ->
                    if (item !is JsonValue.Obj) {
                        throw DeckValidationException("$where has a vocabulary entry that is not an object.")
                    }
                    val word = item.string("word")?.trim()
                    if (word.isNullOrEmpty()) {
                        throw DeckValidationException("$where has a vocabulary entry with an empty \"word\".")
                    }
                    VocabJson(word = word, meaning = item.string("meaning")?.trim()?.takeIf { it.isNotEmpty() })
                }
            }
        }

        val difficulty = when (val value = raw.get("difficulty")) {
            null, is JsonValue.Null -> null
            is JsonValue.Num -> value.value.coerceIn(0.0, 1.0)
            is JsonValue.Str -> when (value.value.trim().uppercase()) {
                "A1" -> 0.0
                "A2" -> 0.25
                "B1" -> 0.5
                "B2" -> 0.75
                "C1", "C2" -> 1.0
                else -> value.value.trim().toDoubleOrNull()?.coerceIn(0.0, 1.0)
            }
            else -> null
        }

        val reviewed = raw.bool("reviewed") ?: false
        val source = raw.string("source")?.trim()?.takeIf { it.isNotEmpty() }
        val license = raw.string("license")?.trim()?.takeIf { it.isNotEmpty() }
        val startMs = raw.long("start_ms")?.takeIf { it >= 0 }

        return DeckCardJson(
            id = id,
            english = english,
            persian = persian,
            ipa = ipa,
            vocab = vocab,
            note = note,
            difficulty = difficulty,
            reviewed = reviewed,
            source = source,
            license = license,
            startMs = startMs
        )
    }

    // --- Writer -------------------------------------------------------------

    fun toJson(deck: DeckJson): JsonValue.Obj = JsonValue.mapOfDefined(
        "version" to JsonValue.of(deck.version),
        "title" to JsonValue.of(deck.title),
        "source_lang" to JsonValue.of(deck.sourceLang),
        "target_lang" to JsonValue.of(deck.targetLang),
        "license" to JsonValue.of(deck.license),
        "parts" to JsonValue.arr(
            deck.parts.map { part ->
                JsonValue.mapOfDefined(
                    "index" to JsonValue.of(part.index),
                    "cards" to JsonValue.arr(part.cards.map { card -> cardToJson(card) })
                )
            }
        )
    )

    /** Serialises a deck as pretty-printed JSON, ready to be shared or committed. */
    fun encode(deck: DeckJson): String = toJson(deck).toPrettyJsonString()

    private fun cardToJson(card: DeckCardJson): JsonValue.Obj = JsonValue.mapOfDefined(
        "id" to JsonValue.of(card.id),
        "start_ms" to card.startMs?.let { JsonValue.of(it) },
        "english" to JsonValue.of(card.english),
        "persian" to card.persian?.let { JsonValue.of(it) },
        "ipa" to card.ipa?.let { JsonValue.of(it) },
        "vocab" to JsonValue.arr(
            card.vocab.map { entry ->
                JsonValue.mapOfDefined(
                    "word" to JsonValue.of(entry.word),
                    "meaning" to entry.meaning?.let { JsonValue.of(it) }
                )
            }
        ),
        "note" to card.note?.let { JsonValue.of(it) },
        "difficulty" to card.difficulty?.let { JsonValue.of(it) },
        "reviewed" to JsonValue.of(card.reviewed),
        "source" to card.source?.let { JsonValue.of(it) },
        "license" to card.license?.let { JsonValue.of(it) }
    )
}
