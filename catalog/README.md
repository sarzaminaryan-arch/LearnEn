# Adding a downloadable lesson

The online catalogue has no fixed item limit. To publish another lesson:

1. Add a valid `deck.json` under `catalog/decks/`. Each vocabulary item should
   include both fields, for example `{ "word": "borrow", "meaning": "قرض گرفتن" }`.
2. Add one record to `catalog/catalog.json` with a unique `id`, a user-facing
   `title` and `description`, a raw GitHub `url` to the deck JSON, and its
   `card_count`.
3. Validate the deck with `python tools/validate_deck.py catalog/decks/<file>.json`.
4. Merge the content change to `main`; the app's existing catalogue URL then
   serves the new lesson. The learner taps **Download**, and the deck appears
   with the other decks on the Learn screen.

The app does not hard-code the catalogue count. Keep IDs unique and include
Persian meanings for the selected vocabulary so they can be shown on the back of
flashcards.
