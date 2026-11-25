# Items that need a human review before release

This file lists everything in the repository that is deliberately provisional,
auto-generated, or that could not be verified without running the Android build.

Read it before shipping. Every item names where to look and what "done" means.

---

## 1. Verify the toolchain versions resolve

**Status:** pinned but never resolved locally (the build machine had no JDK).

`gradle/libs.versions.toml` carries `# VERIFY VERSION` next to each entry. The
first CI run is the first real resolution. If something does not exist, the build
fails with a clear "could not find" message.

| Concern | Check |
|---|---|
| KSP `1.9.24-1.0.20` exists for Kotlin 1.9.24 | `gradle :app:dependencies` or the first CI run |
| Compose compiler `1.5.14` pairs with Kotlin 1.9.24 | documented mapping for 1.9.24 |
| Compose BOM `2024.06.00` is fetchable | CI dependency resolution |
| Room `2.6.1` + KSP produce `app/schemas/…/1.json` | first successful build |

**Done when:** both flavours build in CI.

---

## 2. Confirm the merged-manifest check finds the right file

The CI step locates the merged manifest with `find`, because its path has moved
between AGP versions. It fails loudly rather than silently passing when the file is
missing.

**Done when:** the `offline` job runs the check and prints "offline manifest is
clean".

---

## 3. Regenerate the core deck from real Tatoeba data

**Status:** the app ships `sample_deck.json` (30 hand-written pairs, 3 parts).

The generated `core_deck.json` (≈3000 cards, 10 parts) is produced by
`tools/build_deck.py` and is **not** committed, because it is a build artefact and
because the sandbox that produced this repository could not reach
`downloads.tatoeba.org`.

```bash
pip install -r tools/requirements.txt
python tools/build_deck.py --out app/src/main/assets/content/core_deck.json
python tools/validate_deck.py app/src/main/assets/content/core_deck.json
```

**Done when:** `BundledDeckTest.the generated core deck parses when it is present`
runs (not skipped) and passes.

### Sub-items for that script

* Confirm the per-language export filenames still exist:
  `exports/per_language/eng/eng_sentences.tsv.bz2`,
  `.../pes/pes_sentences.tsv.bz2`, `.../pes/pes-eng_links.tsv.bz2`.
  If `pes` is absent, the script already falls back to `fas`.
* Confirm the links file column direction. The script samples the rows and swaps
  if reversed, logging which it chose — read that log line.
* Optional: install `espeak-ng` so the `ipa` field is populated. Without it the
  field is simply empty and the app hides it.

---

## 4. Persian language quality

**Status:** translations were written by the author of this repository; no native
speaker review has been recorded.

Every string in `values-fa/strings.xml` and every Persian sentence in
`sample_deck.json` should be read by a native speaker.

Particular points to check:

* The zero-width non-joiner in `فلش‌لکس` (`app_name`) — it must survive translation
  tools and copy-paste.
* `about_disclaimer` in Persian: it carries the required English sentence verbatim
  after the Persian translation, which is intentional.
* Plural forms. Persian has its own pluralisation behaviour; the app copies the
  English `one`/`other` split, which is a simplification.

**Done when:** a native speaker has signed off, or the author accepts the risk in
writing.

---

## 5. Colour contrast

**Status:** verified for the fixed palette by `ui/ContrastTest`.

Not covered: Material You dynamic colour, which the system generates. It is
designed to be contrast-correct, but it is not tested here.

One change was made as a direct result of the contrast audit: the amber accent
moved from `#C88B00` (2.94:1 on white, under the 3:1 rule for non-text UI) to
`#B87F00`, and a darker `#8A5A00` was added for accent-coloured **text**.

---

## 6. Accessibility beyond contrast

**Status:** touch targets are 48dp by construction and every icon has a content
description. Not verified with TalkBack.

**Done when:** a screen-reader pass over Home, Study, Quiz and Settings has been
done on a real device.

---

## 7. Text-to-speech

**Status:** uses the platform `TextToSpeech` API with `QUEUE_FLUSH`.

Not verified: behaviour when no English voice is installed. The code detects the
missing language and offers the install prompt, but the fallback path has not run
on a device.

**Done when:** tested on a device with a stripped-down TTS engine.

---

## 8. The catalogue URL is a placeholder

`CatalogDefaults.DEFAULT_CATALOG_URL` points at
`https://raw.githubusercontent.com/laxipatem/lexiflash-catalog/main/catalog.json`,
a repository that does not exist. This is the **only** permitted placeholder in the
codebase and it was called out as such in the brief.

**Done when:** a real catalogue exists, or the catalogue screen is hidden by
removing the entry point. The screen already degrades gracefully: it shows an
offline note in the `offline` flavour and an error in `online`.

---

## 9. Release signing

**Status:** the workflow reads secrets and warns when they are absent.

Before the first Play upload:

1. Generate an upload keystore.
2. `base64 -w0 upload.jks > key.txt` and add it as the `KEYSTORE_BASE64` secret.
3. Add `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
4. Run the Release workflow once and confirm the produced APK is signed
   (`apksigner verify --print-certs app-offline-release.apk`).
5. Enrol in Play App Signing.

**Done when:** the workflow's "Sign-check the produced APKs" step prints `signed`.

---

## 10. Store listing material does not exist yet

There are no screenshots and no feature graphic. Both are required for a Play
listing. They cannot live in this repository (text-only), so they belong in the
store listing, not in Git. See `docs/STORE_CHECKLIST.md`.

---

## 11. Instrumented tests are compiled but never run

There is no emulator in CI. The `androidTest` source set exists to keep the
dependency wiring honest.

**Done when:** someone runs `gradle :app:connectedOfflineDebugAndroidTest` on a
device at least once.

---

## 12. Android 14 and later: predictive back

`android:enableOnBackInvokedCallback` is not set. Back navigation works through
the default behaviour. Worth revisiting once the app is on a device running
Android 14+.

---

## 13. Verify the FSRS numbers against the reference again

`FsrsTest` pins the 19 weights, `DECAY = -0.5` and `FACTOR = 19/81`. If the
reference implementation revises the weight set, that test is the tripwire.

The three assumptions worth re-reading:

1. **Again on a review schedules 10 minutes, not a day.** Matches the reference's
   relearning step; the app rounds it to minutes rather than days.
2. **Intervals are whole days.** The reference allows fractional stability; the app
   schedules on a day grid.
3. **A new card rated Again counts as a lapse.** The reference treats a failed
   first exposure as a lapse for stability purposes; the app increments `lapses`
   to match.
