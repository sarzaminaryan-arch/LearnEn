package ir.laxipatem.lexiflash.online

/**
 * Default remote catalogue location, shared by both flavours so that the
 * Settings screen can display it even in the offline build.
 *
 * This is a documented placeholder: point it at your own catalogue repository
 * (or edit the URL in Settings) before shipping. The file format is described
 * in `docs/DECK_FORMAT.md`.
 */
object CatalogDefaults {
    const val DEFAULT_CATALOG_URL: String =
        "https://raw.githubusercontent.com/laxipatem/lexiflash-catalog/main/catalog.json"

    /** Largest catalogue or deck body accepted, in bytes. */
    const val MAX_DOWNLOAD_BYTES: Int = 10 * 1024 * 1024

    /** Connection timeout in milliseconds. */
    const val CONNECT_TIMEOUT_MS: Int = 10_000

    /** Read timeout in milliseconds. */
    const val READ_TIMEOUT_MS: Int = 20_000
}
