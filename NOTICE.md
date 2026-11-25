# NOTICE

## LexiFlash Persian (فلش‌لکس)

Copyright 2026 LexiFlash Persian contributors.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full
text.

---

## Bundled sentence data

The English and Persian sentences shipped in the bundled deck are derived from
**Tatoeba**, a collection of example sentences and their translations contributed
by volunteers.

* Source: <https://tatoeba.org>
* Licence: **CC BY 2.0 FR** (Creative Commons Attribution 2.0 France) for the
  majority of the corpus, with a subset released under **CC0 1.0** (public domain
  dedication).
* Licence texts: <https://creativecommons.org/licenses/by/2.0/fr/>
  and <https://creativecommons.org/publicdomain/zero/1.0/>
* Per-sentence attribution: `docs/CREDITS.csv` in this repository lists the
  Tatoeba sentence identifiers behind every generated card, so each sentence can
  be traced back to its contributors.

### What "derived from" means here

The toolkit in `tools/` downloads the published Tatoeba exports, selects sentence
pairs, and writes them into the app's deck format. Sentences are reproduced
verbatim apart from trimming surrounding whitespace. Persian translations are the
human translations contributed to Tatoeba; **no machine-translated text is used in
the bundled deck**.

### What is *not* bundled

No subtitle file, film dialogue, television transcript, TED talk transcript,
lyrics, book excerpt or other copyrighted text is downloaded, scraped, stored or
distributed by this project or by its tooling.

---

## The hand-written sample deck

`app/src/main/assets/content/sample_deck.json` contains 30 sentence pairs written
expressly for this project. They are licensed under the Apache License 2.0, the
same as the code, and are not copied from any corpus.

---

## Third-party software

The app is assembled from open-source components. Each remains under its own
licence; the list below records what is used and where to read the terms.

| Component | Licence | Project |
|---|---|---|
| AndroidX Core, Activity, Lifecycle, Navigation | Apache 2.0 | <https://developer.android.com/jetpack> |
| Jetpack Compose (UI, Material 3, Foundation) | Apache 2.0 | <https://developer.android.com/jetpack/compose> |
| AndroidX Room | Apache 2.0 | <https://developer.android.com/training/data-storage/room> |
| AndroidX DataStore | Apache 2.0 | <https://developer.android.com/topic/libraries/architecture/datastore> |
| AndroidX DocumentFile | Apache 2.0 | <https://developer.android.com/jetpack/androidx/releases/documentfile> |
| Kotlin standard library and coroutines | Apache 2.0 | <https://kotlinlang.org> |
| ML Kit Translate (online flavour only) | Google Terms / proprietary | <https://developers.google.com/ml-kit/terms> |

ML Kit is present **only in the `online` flavour**. The `offline` flavour links no
Google service, no network library and requests no `INTERNET` permission.

### The FSRS algorithm

The spaced-repetition scheduler implements **FSRS-5**, published by the
open-spaced-repetition community. Its documentation and reference implementations
are available at <https://github.com/open-spaced-repetition>. The algorithm
description is used as a specification; no source file was copied, and the
implementation here is an independent Kotlin port with its own tests.

---

## Trademarks

"Android", "Google Play" and "ML Kit" are trademarks of Google LLC. "Tatoeba" is
the name of the Tatoeba project.

**Not affiliated with TED, Interstellar or any rights holder.**
