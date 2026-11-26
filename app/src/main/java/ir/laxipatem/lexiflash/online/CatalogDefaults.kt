package ir.laxipatem.lexiflash.online

/**
 * Default remote catalogue location, shared by both flavours so that the
 * Settings screen can display it even in the offline build.
 *
 * The catalogue lives in this repository under `catalog/`, so the URL that ships
 * is a URL that actually resolves. The previous default pointed at a separate
 * `laxipatem/lexiflash-catalog` repository that does not exist, which is why
 * every attempt to load the catalogue on a real device answered HTTP 404. The
 * file format is described in `docs/DECK_FORMAT.md`.
 *
 * `raw.githubusercontent.com` serves a plain-text file with no redirect, which
 * is what [HttpFetcher][ir.laxipatem.lexiflash.platform.HttpFetcher] expects: it
 * follows redirects but refuses anything that is not HTTPS.
 */
object CatalogDefaults {
    const val DEFAULT_CATALOG_URL: String =
        "https://raw.githubusercontent.com/sarzaminaryan-arch/LearnEn/main/catalog/catalog.json"

    /** Largest catalogue or deck body accepted, in bytes. */
    const val MAX_DOWNLOAD_BYTES: Int = 10 * 1024 * 1024

    /** Connection timeout in milliseconds. */
    const val CONNECT_TIMEOUT_MS: Int = 10_000

    /** Read timeout in milliseconds. */
    const val READ_TIMEOUT_MS: Int = 20_000
}
