# Design decisions

Every ambiguous point in the specification is resolved here, with the reasoning.
The ordering principle throughout: **it compiles, then it is legally safe, then
it is simple, then it is featureful.** A small app that builds beats a large one
that does not.

---

## 1. Architecture

### 1.1 Manual dependency injection instead of Hilt
`AppContainer` is a plain class built once in `LexiFlashApp` and exposed to
screens as a parameter. Hilt would add KSP processors, generated code and a
learning curve for no benefit at this size. Screens receive the container and
create view models through `ViewModelFactories.containerFactory`.

### 1.2 No repository interface layer
`DeckRepository`, `ReviewRepository` and friends are concrete classes. There is
exactly one implementation of each and no plan for a second, so an interface per
repository would be indirection with no payoff. Tests exercise the pure domain
objects (FSRS, unlock rules, quiz generator) rather than mocks.

### 1.3 Room with `allowMainThreadQueries`
A deliberate exception to normal practice. Every DAO call already happens inside
`withContext(Dispatchers.IO)` in the repositories, and the main-thread guard would
only turn a would-be crash into a would-be crash. Enabling it also makes the
in-memory test path behave identically to production.

### 1.4 Schema export with no destructive migration
`exportSchema = true` with `version = 1` and no migration path yet. Adding
`fallbackToDestructiveMigration` would silently delete a learner's review history
on the first schema change, so it is not enabled.

---

## 2. Scheduling

### 2.1 FSRS-5 implemented from the reference, not from memory
The nineteen weights, `DECAY = -0.5` and `FACTOR = 19/81` come from the
open-spaced-repetition reference implementation, which takes precedence over any
figure in the brief where they disagreed. `FsrsTest` pins each of them.

### 2.2 "Again" schedules ten minutes, not one day
The reference scheduler puts a lapsed card into relearning. Rounding that to a
whole day would let a learner leave a card they just failed for twenty-four hours,
which is the opposite of what the schedule is for. `scheduledMinutes` therefore
carries `10` for Again and `scheduledDays` carries `0`.

### 2.3 Intervals are whole days, capped at 36500
Fractional-day scheduling would need a background job. Day granularity is what the
app's session model assumes, and 36500 days (a century) is the reference cap.

### 2.4 Review queue is snapshotted when the screen opens
A card rated Again is written with a ten-minute relearning interval but does not
reappear inside the same session; it comes back the next day. Reinserting it
mid-session would mean mutating the queue while the learner is walking it, and
the cognitive benefit of the immediate repeat is already served by the flashcard
self-check. This is a deliberate simplification.

---

## 3. Unlocking and quizzes

### 3.1 Eighty percent, best-of
A part unlocks the next when every card has been seen **and** the best quiz score
is at least 80 percent. Only the best attempt counts, so a learner is never
punished for a first bad run. The threshold and the rule live in `UnlockPolicy`
and are unit tested across the boundary (79 fails, 80 passes).

### 3.2 An empty part never blocks
A part with zero cards would make the requirement unsatisfiable forever, so it is
treated as passed. This keeps a truncated import usable.

### 3.3 Quiz questions are generated locally and deterministically
`QuizGenerator` takes a seed. No model, no network, no randomness that cannot be
reproduced in a test. Cards without a Persian translation cannot be used for
meaning or reverse questions because there would be no correct answer to show.

### 3.4 `bestQuizScores` is a suspend function, not a Flow
It runs one query per distinct part. A Flow would re-run every query on every
review-log change, so `ProgressViewModel` and `HomeViewModel` wrap it in
`flow { emit(...) }` inside `flatMapLatest`, which recomputes only when the deck
changes.

---

## 4. Content and licensing

### 4.1 Tatoeba only, human translations only
Bundled sentences come from Tatoeba under CC BY 2.0 FR (portions CC0 1.0).
Neither subtitles, transcripts, nor film dialogue are ever downloaded or scraped.
The shipped deck contains **no machine-translated text**: a Persian sentence is
included only when a human contributed it to Tatoeba as a translation.

### 4.2 Per-language exports, verified at runtime
`tools/build_deck.py` prefers `exports/per_language/eng/`, `pes/` and the
`pes-eng_links` file because they are three small downloads instead of a 370 MB
bulk dump. The column layout of a links file is not assumed: `load_links` samples
the rows, checks which column matches known sentence ids, and swaps if the file is
reversed. If the per-language route fails, the tool falls back to the bulk
exports, and CI falls back to the sample deck.

### 4.3 Machine translation only in the online flavour, only on request
ML Kit translation is used solely when a learner presses the translate button and
after they accept the disclaimer. The result is flagged `machineTranslated` and
labelled in the UI, and it never enters a bundled deck.

### 4.4 The disclaimer is required verbatim
`about_disclaimer` must contain the exact sentence "Not affiliated with TED,
Interstellar or any rights holder." `StringsParityTest` fails the build if it is
ever softened or removed.

### 4.5 The old project name is banned
The old name appears nowhere, including in string resources. A test enforces it.


---

## 5. Platforms and UI

### 5.1 Language switching without AppCompat
The AppCompat per-app-language API would add a dependency for one feature.
Instead `LexiFlashTheme` builds a configuration-aware context carrying the chosen
locale and provides it as `LocalContext`, which is what `stringResource` reads.
`LocalLayoutDirection` is overridden at the same time for RTL. Both happen inside
a `remember`, so flipping the language in Settings does not recreate the Activity
and does not lose navigation state.

### 5.2 Vector-only launcher icon
A text-only repository cannot hold a PNG. The launcher is an adaptive icon defined
by vector XML in `mipmap-anydpi-v26` plus a vector fallback for API 24–25. The
`IconMissingDensityFolder` and `MonochromeLauncherIcon` lint checks are disabled
because there are no density folders by design.

### 5.3 Dynamic colour on Android 12 and later is an opt-in
Material You palettes are available from Settings on Android 12 and newer. The
curated indigo/teal palette is the default so the app has a consistent identity;
below API 31 that same fixed palette is always used.

### 5.4 Contrast is checked on the fixed palettes
Body text meets WCAG AA against its paired light and dark surfaces. The test also
checks primary actions, semantic colours, the raised-card separation, and the
light/dark card outlines. Optional Material You colours are not covered.

### 5.5 Vazirmatn is bundled for both writing systems
The app ships Vazirmatn v33.003 in regular, medium, semibold and bold TTF weights.
Its Latin glyphs accompany Persian glyphs, so all Compose text uses the same
family instead of varying with a device's system fonts. The SIL Open Font License
is included in `app/src/main/assets/licenses/Vazirmatn-OFL.txt`.

### 5.6 Persian digits are opt-in
`persianDigits` defaults to off. Persian-Indic digits are a preference, not a
correctness requirement.

---

## 6. Import pipeline

### 6.1 One pipeline, many parsers
`SubtitleParser` is a two-method contract (`canParse`, `parse`). SRT, VTT, ASS,
TXT, CSV and deck.json each implement it, and `ImportService` picks the first
match. Adding a format means adding one class.

### 6.2 Character set detection is ordered, not guessed
BOM first, then a strict UTF-8 validation, then windows-1256 (the usual Persian
legacy encoding), then ISO-8859-1 as a last resort that cannot fail. A file is
never decoded with the wrong charset silently when a correct one is available.

### 6.3 Thirty percent time overlap pairs a bilingual file
`BilingualPairer` requires at least 30 percent overlap of the shorter cue before
it accepts a pair, and flags a Persian cue consumed a second time as `reused`.
Too strict a threshold loses valid pairs; too loose a one pairs unrelated lines.

### 6.4 Part sizes differ by at most one
`PartSplitter` produces contiguous, never-empty parts with sizes differing by at
most one and the remainder in the earlier parts. This matches the Python deck
builder exactly, so an imported deck and a generated deck split the same way.

### 6.5 Saving is transactional
A deck is written inside one Room transaction, so a failure mid-way cannot leave a
half-imported deck behind.

---

## 7. Storage and export

### 7.1 Storage Access Framework only
No storage permission is declared in any flavour. The platform grants read access
to exactly the file the learner picks.

### 7.2 Exports go to the cache directory and are shared by URI
`DeckExporter` writes into `cacheDir/exports` with a sanitised filename of at most
48 characters and hands out a FileProvider URI. Nothing is written to shared
storage, so nothing is left behind.

### 7.3 Unknown JSON fields are ignored
Rejecting them would break every older client the day a field is added.

---

## 8. Offline versus online

### 8.1 One interface, two source sets
`OnlineFeatures` lives in the main source set. Each flavour supplies its own
`OnlineFeaturesFactory` in the **same package**, so exactly one is compiled and no
conditional code leaks into either.

### 8.2 The offline build has no network code at all
Not merely "does not call the network": no INTERNET permission, no HTTP client, no
ML Kit dependency. The offline stub throws `OnlineFeatureUnavailableException` from
every method that would need a network. CI greps the *merged* manifest of the
offline variant and fails the build if INTERNET, a storage permission or a
network-state permission appears.

### 8.3 The catalogue URL must be HTTPS
The settings screen rejects anything that does not start with `https://`, which
rules out cleartext endpoints by construction. The default points at a
placeholder repository that the maintainer replaces before shipping.

### 8.4 Catalogue fetches are user-initiated
Opening the catalogue screen performs no request. A button press does. This keeps
the "offline by default" promise honest even in the online flavour.

---

## 9. Build and release

### 9.1 `gradle` from setup-gradle, not `./gradlew`
The repository is text-only, so it holds no `gradle-wrapper.jar`. CI uses the
Gradle 8.7 binary that `gradle/actions/setup-gradle` installs, and the README shows
`gradle wrapper --gradle-version 8.7` for anyone who prefers the wrapper locally.

### 9.2 Fixed toolchain versions
Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, KSP 1.9.24-1.0.20 and Compose compiler
1.5.14 are pinned in `gradle/libs.versions.toml` with `# VERIFY VERSION` comments.
The Compose compiler is the last release that supports Kotlin 1.9.

### 9.3 The deck build is allowed to fail
The weekly Tatoeba refresh runs with `continue-on-error`, because the app already
ships a working sample deck. A network outage must not turn into a red build.

### 9.4 Release signing is environment-first
`app/build.gradle.kts` reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`, then `keystore.properties`, then gives up and produces an unsigned
release. Debug builds never need a secret, and no keystore is ever committed.

### 9.5 Release AABs and APKs are both produced
Play wants the bundle; sideloading wants the APK. Producing both costs one extra
Gradle task.

---

## 10. Testing

### 10.1 JVM unit tests, no Robolectric
Everything with logic is pure Kotlin and testable on the JVM: FSRS, unlock rules,
streaks, quiz generation, all six parsers, character-set detection, the
preprocessor, the pairer, the splitter, the deck codec and string parity.
Instrumented tests are compiled but not run in CI.

### 10.2 The Python deck tool has its own suite
`tools/test_build_deck.py` covers the same cleaning and splitting rules as the
Kotlin side, so a divergence between the two implementations produces a test
failure rather than a subtly different deck.

### 10.3 CI proves the offline promise mechanically
Grepping the merged manifest is a build gate, not a review comment. A dependency
that drags in INTERNET fails the offline job immediately.
