# Deck format (`deck.json`)

The interchange format LexiFlash uses to bundle, import and export decks. One
flat JSON document holds the whole deck, including its parts, so a deck is a
single file a learner can share.

The Kotlin implementation is `DeckJsonCodec` in
`app/src/main/java/ir/laxipatem/lexiflash/data/model/DeckJson.kt`; the Python
mirror used by the build tool is `validate_deck.py` in `tools/`. If they ever
disagree, the Kotlin validator is authoritative, because that is what runs on the
device.

---

## Top-level object

```json
{
  "version": 1,
  "title": "LexiFlash English–Persian core deck",
  "source_lang": "en",
  "target_lang": "fa",
  "license": "CC BY 2.0 FR / CC0 1.0",
  "parts": [
    { "index": 1, "cards": [ /* … */ ] }
  ]
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `version` | integer | yes | Currently `1`. A document with a higher number is rejected so an old app never half-imports a newer deck. |
| `title` | string | yes | 1–200 characters after trimming. Used as the deck name. |
| `source_lang` | string | yes | Must be `"en"`. |
| `target_lang` | string | yes | Must be `"fa"`. |
| `license` | string | yes | Free text, shown in the deck details. Empty is permitted. |
| `parts` | array | yes | 1–200 objects, indices unique and contiguous starting at 1. |

### Why `license` is required

Even when empty, the field forces an author to think about redistribution rights
before bundling someone else's sentences. The app displays whatever value is
present on the deck detail screen.

---

## Part object

| Field | Type | Required | Rules |
|---|---|---|---|
| `index` | integer | yes | Unique across the deck. Contiguous from 1. Drives unlock order. |
| `cards` | array | yes | At least one card. |

The Kotlin validator tolerates a non-contiguous sequence but the Python deck
validator for shipped decks requires exactly `1..n`, because the generated deck
always satisfies it.

---

## Card object

```json
{
  "id": "tat-1234-5678",
  "english": "The train leaves at seven in the evening.",
  "persian": "قطار ساعت هفت عصر حرکت می‌کند.",
  "ipa": "ðə tɹeɪn liːvz ət ˈsɛvən ɪn ði ˈiːvnɪŋ",
  "vocab": [{ "word": "train", "meaning": "قطار" }],
  "note": "A1",
  "difficulty": 0.12,
  "start_ms": 1500,
  "reviewed": true,
  "source": "tatoeba",
  "license": "CC BY 2.0 FR"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | string | yes | Non-empty, ≤128 characters, unique across the whole deck. Stable across versions, so review history survives a deck upgrade. |
| `english` | string | yes | 1–2000 characters. |
| `persian` | string or null | no | When null or blank, the card is *pending*: it can still be studied as a cloze but quizzes skip it until translated. |
| `ipa` | string or null | no | International Phonetic Alphabet transcription of the English sentence. Hidden when absent. |
| `vocab` | array | no | At most 12 entries, each `{ "word": string (required), "meaning": string (optional) }`. |
| `note` | string or null | no | Free-form note, currently the CEFR band. |
| `difficulty` | number or null | no | `0.0` to `1.0`. Used only for ordering the deck; the app does not adapt to it. |
| `start_ms` | integer or null | no | Original timestamp when the source had timing. Preserved on round trips. |
| `reviewed` | boolean | no | Defaults to `false`. A `true` value means the translation was checked by a human. |
| `source` | string or null | no | Provenance, for example `"tatoeba"` or `"original"`. |
| `license` | string or null | no | Per-card licence when it differs from the deck-level one. |

### Unknown fields

Ignored, not rejected. This lets a newer exporter add fields without breaking
older clients. The writer only emits the fields listed above.

---

## Translation state

Three values matter for the UI:

| State | `persian` | `reviewed` | How it renders |
|---|---|---|---|
| Translated | text | `true` | Normal card. |
| Machine translated | text | `false` | Labelled **Machine translation** in the import preview and on the card. |
| Pending | null or `""` | `false` | Flagged **Needs translation**; quizzes skip it. |

The app never presents machine output as a human translation.

---

## Practical limits

Enforced on the device so a hostile file cannot exhaust memory:

| Limit | Value |
|---|---|
| Cards per deck | 50,000 |
| Parts per deck | 200 |
| Card text length | 2,000 characters |
| Vocabulary entries per card | 12 |
| Card id length | 128 characters |
| JSON nesting depth | 64 |

Files are read with the Storage Access Framework, so no storage permission is
ever requested.

---

## A minimal valid deck

```json
{
  "version": 1,
  "title": "Two cards",
  "source_lang": "en",
  "target_lang": "fa",
  "license": "CC0 1.0",
  "parts": [
    {
      "index": 1,
      "cards": [
        { "id": "a", "english": "I drink tea every morning.", "persian": "من هر صبح چای می‌نوشم." },
        { "id": "b", "english": "She walks to school.", "persian": "او پیاده به مدرسه می‌رود." }
      ]
    }
  ]
}
```

Two cards is enough to import but far too few to study properly: the app's own
generator refuses to produce a deck under 300 cards (see
`tools/build_deck.py`).

---

## Validating a file

```bash
python tools/validate_deck.py my_deck.json
# add --allow-small when checking a hand-made or sample deck
python tools/validate_deck.py --allow-small my_deck.json
```

The script prints a one-line JSON summary on success and exits non-zero with a
readable message on the first failure class.
