package ir.laxipatem.lexiflash.importer

import ir.laxipatem.lexiflash.data.model.DeckJson

/**
 * Orchestrates the whole offline import pipeline: bytes in, previewable deck out.
 *
 * The service is pure with respect to Android: it takes already-decoded bytes
 * (the UI reads them through the Storage Access Framework) and returns plain
 * data classes. All collaborators are injectable so the pipeline is exercised by
 * ordinary JUnit tests.
 */
class ImportService(
    private val preprocessor: Preprocessor = Preprocessor(),
    private val pairer: BilingualPairer = BilingualPairer(),
    parsers: List<SubtitleParser> = DEFAULT_PARSERS
) {

    private val parsers: List<SubtitleParser> = parsers

    /** Result of reading a file, before the user has confirmed anything. */
    data class SingleFileResult(
        val deckTitle: String,
        val cards: List<DraftCard>,
        val detectedFormat: String
    )

    /** Result of pairing two subtitle files. */
    data class BilingualResult(
        val deckTitle: String,
        val cards: List<DraftCard>,
        val pairedCount: Int,
        val pendingCount: Int
    )

    /** A card proposed by the import wizard and editable in the preview step. */
    data class DraftCard(
        val english: String,
        val persian: String?,
        val startMs: Long?,
        val pendingTranslation: Boolean,
        val machineTranslated: Boolean = false
    )

    /**
     * Imports one file of any supported format.
     *
     * @param bytes raw file bytes, decoded with [EncodingDetector].
     * @param fileName used for extension-based format detection.
     */
    fun importSingleFile(bytes: ByteArray, fileName: String, fallbackTitle: String): Result<SingleFileResult> =
        runCatching {
            val text = EncodingDetector.decode(bytes)
            if (text.isBlank()) {
                throw IllegalArgumentException("The file is empty.")
            }
            val head = text.take(HEAD_CHARS)
            val parser = detectParser(fileName, head)
                ?: throw IllegalArgumentException("Unsupported file format: $fileName")

            // A deck.json import keeps its own structure and title.
            if (parser is DeckJsonParser) {
                val cues = parser.parse(text)
                val deck = parser.lastDeck
                    ?: throw IllegalArgumentException("The deck file could not be read.")
                return@runCatching SingleFileResult(
                    deckTitle = deck.title,
                    cards = deckToDraftCards(deck, cues),
                    detectedFormat = "deck.json"
                )
            }

            val cues = parser.parse(text)
            val lines = preprocessor.process(cues)
            if (lines.isEmpty()) {
                throw IllegalArgumentException("No usable sentences were found in this file.")
            }
            SingleFileResult(
                deckTitle = titleFromFileName(fileName, fallbackTitle),
                cards = lines.map { line ->
                    val split = splitBilingualText(line.text)
                    DraftCard(
                        english = split.first,
                        persian = split.second,
                        startMs = line.startMs,
                        pendingTranslation = split.second.isNullOrBlank()
                    )
                },
                detectedFormat = parser::class.simpleName ?: "text"
            )
        }

    /**
     * Imports an English and a Persian subtitle file and pairs them by overlap.
     *
     * English cues whose Persian counterpart was already consumed by an earlier
     * cue are kept and flagged as pending, which is why [BilingualResult]
     * reports both a paired and a pending count.
     */
    fun importBilingual(
        englishBytes: ByteArray,
        englishName: String,
        persianBytes: ByteArray,
        persianName: String,
        fallbackTitle: String,
        /** When true, a reused Persian line is written to every English cue. */
        reuseTranslations: Boolean = true
    ): Result<BilingualResult> = runCatching {
        val englishText = EncodingDetector.decode(englishBytes)
        val persianText = EncodingDetector.decode(persianBytes)
        if (englishText.isBlank()) throw IllegalArgumentException("The English file is empty.")
        if (persianText.isBlank()) throw IllegalArgumentException("The Persian file is empty.")

        val englishParser = detectParser(englishName, englishText.take(HEAD_CHARS))
            ?: throw IllegalArgumentException("Unsupported English subtitle format: $englishName")
        val persianParser = detectParser(persianName, persianText.take(HEAD_CHARS))
            ?: throw IllegalArgumentException("Unsupported Persian subtitle format: $persianName")

        val englishCues = preprocessor.process(englishParser.parse(englishText))
        val persianCues = preprocessor.process(persianParser.parse(persianText))
        if (englishCues.isEmpty()) throw IllegalArgumentException("The English file has no usable lines.")
        if (persianCues.isEmpty()) throw IllegalArgumentException("The Persian file has no usable lines.")

        val englishRaw = englishParser.parse(englishText)
        val persianRaw = persianParser.parse(persianText)

        val bothTimed = englishRaw.all { it.startMs != null && it.endMs != null } &&
            persianRaw.all { it.startMs != null && it.endMs != null }

        val pairs = if (bothTimed) {
            pairer.pair(englishRaw, persianRaw)
        } else {
            pairer.pairByPosition(englishRaw, persianRaw)
        }

        val cards = ArrayList<DraftCard>()
        var paired = 0
        var pending = 0
        val seenEnglish = HashSet<String>()

        for (pair in pairs) {
            val english = preprocessor.cleanText(pair.english.text)
            if (!preprocessor.isUsable(english)) continue
            if (!seenEnglish.add(preprocessor.dedupeKey(english))) continue

            val rawPersian = pair.persianText?.let { preprocessor.cleanText(it) }
            val persian = rawPersian?.takeIf { it.isNotBlank() && containsPersianLetters(it) }
            val useTranslation = persian != null && (reuseTranslations || !pair.reused)

            if (useTranslation) paired++ else pending++
            cards += DraftCard(
                english = english,
                persian = if (useTranslation) persian else null,
                startMs = pair.english.startMs,
                pendingTranslation = !useTranslation
            )
        }

        if (cards.isEmpty()) {
            throw IllegalArgumentException("No usable sentence pairs were found.")
        }
        BilingualResult(
            deckTitle = titleFromFileName(englishName, fallbackTitle),
            cards = cards,
            pairedCount = paired,
            pendingCount = pending
        )
    }

    /**
     * Chooses a parser for [fileName] and [head].
     *
     * Extension wins where the format is unambiguous. Content detection then
     * covers files with a generic extension, and the null return lets the caller
     * show an "unsupported format" message instead of guessing.
     */
    fun detectParser(fileName: String, head: String): SubtitleParser? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".json") -> DeckJsonParser()
            lower.endsWith(".srt") -> SrtParser()
            lower.endsWith(".vtt") -> VttParser()
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> AssParser()
            lower.endsWith(".csv") || lower.endsWith(".tsv") -> CsvParser()
            lower.endsWith(".txt") -> TxtParser()
            else -> parsers.firstOrNull { it.canParse(fileName, head) }
        }
    }

    /** Splits a `english<TAB>persian` line produced by [CsvParser]. */
    private fun splitBilingualText(text: String): Pair<String, String?> {
        val tab = text.indexOf('\t')
        if (tab < 0) return text.trim() to null
        val english = text.substring(0, tab).trim()
        val persian = text.substring(tab + 1).trim().takeIf { it.isNotEmpty() && containsPersianLetters(it) }
        return english to persian
    }

    private fun deckToDraftCards(deck: DeckJson, cues: List<Cue>): List<DraftCard> {
        val cards = deck.allCards()
        return cards.mapIndexed { index, card ->
            DraftCard(
                english = card.english,
                persian = card.persian,
                startMs = card.startMs ?: cues.getOrNull(index)?.startMs,
                pendingTranslation = card.persian.isNullOrBlank(),
                machineTranslated = false
            )
        }
    }

    /** True when [text] contains at least three Arabic-script letters. */
    internal fun containsPersianLetters(text: String): Boolean =
        text.count { it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' } >= 3

    private fun titleFromFileName(fileName: String, fallback: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = base.substringBeforeLast('.', base).trim()
        val cleaned = withoutExtension.replace('_', ' ').replace('.', ' ').trim()
        return cleaned.ifEmpty { fallback }.take(80)
    }

    companion object {
        private const val HEAD_CHARS = 4096

        /** Default detection order; content sniffing stops at the first match. */
        val DEFAULT_PARSERS: List<SubtitleParser> = listOf(
            VttParser(),
            SrtParser(),
            AssParser(),
            DeckJsonParser(),
            CsvParser(),
            TxtParser()
        )
    }
}

/** A deck assembled in memory, ready to be written to the database. */
data class DraftDeck(
    val title: String,
    val license: String,
    val cards: List<ImportService.DraftCard>,
    /** Cards grouped into parts, each part non-empty. */
    val parts: List<List<ImportService.DraftCard>>,
    val source: String
) {
    val cardCount: Int get() = cards.size
    val partCount: Int get() = parts.size
}
