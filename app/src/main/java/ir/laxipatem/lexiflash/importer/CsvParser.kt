package ir.laxipatem.lexiflash.importer

/**
 * Delimited text with an optional bilingual second column.
 *
 * Accepted shapes:
 * * `english,persian` / `english<TAB>persian`
 * * `english` alone
 *
 * Quoted fields are supported (RFC 4180): `"He said, ""hi""",سلام`. The
 * delimiter is chosen from the header or the first data line, tabs winning over
 * commas when both appear, because a Persian sentence is far more likely to
 * contain a comma than a tab.
 */
class CsvParser : SubtitleParser {

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".csv", ignoreCase = true) || fileName.endsWith(".tsv", ignoreCase = true)) {
            return true
        }
        val firstLine = head.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return firstLine.contains('\t') || (firstLine.count { it == ',' } >= 1 && firstLine.contains("-->").not())
    }

    override fun parse(text: String): List<Cue> {
        val normalized = normalizeNewlines(text)
        val physicalLines = normalized.split('\n')
        if (physicalLines.all { it.isBlank() }) return emptyList()

        // A quoted field may contain a newline, so records have to be assembled
        // before they are split. Splitting on '\n' first would tear such a row in
        // half and produce two broken cards.
        val records = joinQuotedRecords(physicalLines).filter { it.isNotBlank() }
        if (records.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(records.first())
        val cues = ArrayList<Cue>()
        var rowIndex = 0

        for (line in records) {
            val fields = splitRow(line, delimiter)
            rowIndex++
            if (fields.isEmpty()) continue

            val first = fields[0].trim()
            if (first.isEmpty()) continue

            // A header row is dropped when it names the columns.
            if (rowIndex == 1 && isHeader(fields)) continue

            val second = fields.getOrNull(1)?.trim().orEmpty()
            // The tab is the separator the preview splits bilingual cues on, so a
            // Persian second column is always kept next to its English sentence.
            val combined = if (second.isEmpty()) first else "$first\t$second"
            cues += Cue(startMs = null, endMs = null, text = combined)
        }
        return cues
    }

    private fun detectDelimiter(firstLine: String): Char {
        val tabs = countOutsideQuotes(firstLine, '\t')
        val commas = countOutsideQuotes(firstLine, ',')
        val semicolons = countOutsideQuotes(firstLine, ';')
        return when {
            tabs > 0 && tabs >= commas -> '\t'
            commas > 0 -> ','
            semicolons > 0 -> ';'
            else -> '\t'
        }
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (character in line) {
            when {
                character == '"' -> inQuotes = !inQuotes
                character == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    /** RFC 4180 field splitter; tolerates a stray quote by treating it literally. */
    internal fun splitRow(line: String, delimiter: Char): List<String> {
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val character = line[index]
            when {
                inQuotes && character == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 2
                    continue
                }
                character == '"' -> inQuotes = !inQuotes
                character == delimiter && !inQuotes -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    /**
     * Joins physical lines into logical CSV records, honouring quoted newlines.
     *
     * A record is complete once the number of unescaped quotes is even, which is
     * the same rule a spreadsheets application applies.
     */
    private fun joinQuotedRecords(lines: List<String>): List<String> {
        val records = ArrayList<String>()
        val current = StringBuilder()
        for (line in lines) {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
            if (isBalanced(current)) {
                records += current.toString()
                current.setLength(0)
            }
        }
        // An unterminated quote still yields its text rather than vanishing.
        if (current.isNotEmpty()) records += current.toString()
        return records
    }

    private fun isBalanced(record: CharSequence): Boolean {
        var quotes = 0
        var index = 0
        while (index < record.length) {
            val character = record[index]
            if (character == '"') {
                if (index + 1 < record.length && record[index + 1] == '"') {
                    index += 2
                    continue
                }
                quotes++
            }
            index++
        }
        return quotes % 2 == 0
    }

    private fun isHeader(fields: List<String>): Boolean {
        val first = fields.firstOrNull()?.trim()?.lowercase().orEmpty()
        val second = fields.getOrNull(1)?.trim()?.lowercase().orEmpty()
        return (first == "english" || first == "en" || first == "sentence" || first == "source") &&
            (second.isEmpty() || second == "persian" || second == "fa" || second == "translation" ||
                second == "target")
    }
}
