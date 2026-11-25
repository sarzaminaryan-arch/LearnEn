"""Offline unit tests for the deck generation tool.

These tests never touch the network. They cover the cleaning rules, the
duplicate removal, the part splitting, the difficulty ordering and the schema
gate that CI relies on.
"""

from __future__ import annotations

import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import build_deck as bd  # noqa: E402


# ---------------------------------------------------------------------------
# clean_sentence
# ---------------------------------------------------------------------------


class TestCleanSentence:
    def test_collapses_whitespace(self):
        assert bd.clean_sentence("  Hello   there,\tworld.  ") == "Hello there, world."

    def test_replaces_non_breaking_space(self):
        assert bd.clean_sentence("Hello\u00a0world.") == "Hello world."

    def test_handles_empty_input(self):
        assert bd.clean_sentence("") == ""

    def test_handles_none_like_input(self):
        assert bd.clean_sentence(None or "") == ""


# ---------------------------------------------------------------------------
# Validity rules
# ---------------------------------------------------------------------------


class TestIsValidEnglish:
    def test_accepts_a_normal_sentence(self):
        assert bd.is_valid_english("She walked to the market this morning.")

    def test_rejects_a_url(self):
        assert not bd.is_valid_english("Visit https://example.com for more.")

    def test_rejects_an_email(self):
        assert not bd.is_valid_english("Write to someone@example.com right now.")

    def test_rejects_too_few_words(self):
        assert not bd.is_valid_english("Hello.")

    def test_rejects_too_many_words(self):
        long_sentence = " ".join(["word"] * (bd.MAX_WORDS + 1)) + "."
        assert not bd.is_valid_english(long_sentence)

    def test_rejects_digit_heavy_text(self):
        assert not bd.is_valid_english("Call 1234 5678 9012 3456 now please.")

    def test_accepts_a_quoted_sentence(self):
        assert bd.is_valid_english('He said, "I will be there soon."')


class TestIsValidPersian:
    def test_accepts_a_persian_sentence(self):
        assert bd.is_valid_persian("او هر روز صبح زود از خواب بیدار می‌شود.")

    def test_rejects_latin_only_text(self):
        assert not bd.is_valid_persian("This is not Persian text.")

    def test_rejects_single_word(self):
        assert not bd.is_valid_persian("سلام")

    def test_rejects_url(self):
        assert not bd.is_valid_persian("این را ببینید https://example.com برای بیشتر")


class TestIsValidPair:
    def test_accepts_a_good_pair(self):
        assert bd.is_valid_pair(
            "The children played in the park all afternoon.",
            "بچه‌ها تمام بعدازظهر در پارک بازی کردند.",
        )

    def test_rejects_when_persian_is_bad(self):
        assert not bd.is_valid_pair(
            "The children played in the park all afternoon.",
            "English text only here",
        )

    def test_rejects_when_english_is_bad(self):
        assert not bd.is_valid_pair("Too short.", "خیلی کوتاه است")


# ---------------------------------------------------------------------------
# Dedupe
# ---------------------------------------------------------------------------


class TestDedupe:
    def test_normalisation_ignores_case_and_punctuation(self):
        assert bd.normalise_for_dedupe("Hello, World!") == bd.normalise_for_dedupe("hello world")

    def test_collect_pairs_removes_duplicates(self):
        english = {
            1: ("eng", "The quick brown fox jumps over the lazy dog."),
            2: ("eng", "the quick brown fox jumps over the lazy dog!"),
        }
        persian = {
            10: ("pes", "روباه قهوه‌ای سریع از روی سگ تنبل می‌پرد."),
            20: ("pes", "روباه سریع قهوه‌ای روی سگ تنبل می‌جهد."),
        }
        links = [(1, 10), (2, 20)]
        pairs = bd.collect_pairs(english, persian, links)
        assert len(pairs) == 1

    def test_collect_pairs_chooses_shortest_translation(self):
        english = {1: ("eng", "I would like a cup of tea, please.")}
        persian = {
            10: ("pes", "من یک فنجان چای می‌خواهم لطفاً اگر امکان دارد."),
            11: ("pes", "یک فنجان چای لطفاً."),
        }
        pairs = bd.collect_pairs(english, persian, [(1, 10), (1, 11)])
        assert len(pairs) == 1
        assert pairs[0].persian_id == 11


# ---------------------------------------------------------------------------
# Difficulty
# ---------------------------------------------------------------------------


@pytest.mark.skipif(bd.zipf_frequency is None, reason="wordfreq not installed")
class TestDifficulty:
    def test_scores_are_bounded(self):
        score = bd.score_difficulty("The cat sat on the mat near the door.")
        assert 0.0 <= score <= 1.0

    def test_common_sentence_is_easier_than_rare_one(self):
        easy = bd.score_difficulty("I want to go to the store with my friend today.")
        hard = bd.score_difficulty(
            "The ubiquitous proliferation of bureaucratic impedimenta "
            "exacerbates administrative inefficiency considerably."
        )
        assert easy < hard

    def test_cefr_buckets_are_ordered(self):
        assert bd.cefr_for_score(0.0) == "A1"
        assert bd.cefr_for_score(0.25) == "A2"
        assert bd.cefr_for_score(0.45) == "B1"
        assert bd.cefr_for_score(0.65) == "B2"
        assert bd.cefr_for_score(0.95) == "C1"

    def test_pick_vocab_prefers_rarer_words(self):
        vocab = bd.pick_vocab("The magnificent cathedral dominates the medieval skyline.")
        words = [entry["word"] for entry in vocab]
        assert words
        assert "the" not in words
        assert len(words) <= 3

    def test_pick_vocab_skips_short_words(self):
        vocab = bd.pick_vocab("A cat sat on a mat by an ox.")
        assert all(len(entry["word"]) >= 3 for entry in vocab)


# ---------------------------------------------------------------------------
# Part splitting
# ---------------------------------------------------------------------------


class TestSplitIntoParts:
    def test_splits_evenly(self):
        parts = bd.split_into_parts(list(range(3000)), 10)
        assert len(parts) == 10
        assert all(len(part) == 300 for part in parts)

    def test_first_parts_take_the_remainder(self):
        parts = bd.split_into_parts(list(range(105)), 10)
        assert len(parts) == 10
        assert [len(part) for part in parts] == [11, 11, 11, 11, 11, 10, 10, 10, 10, 10]

    def test_preserves_order(self):
        flat = [item for part in bd.split_into_parts(list(range(50)), 7) for item in part]
        assert flat == list(range(50))

    def test_never_produces_empty_parts(self):
        parts = bd.split_into_parts(list(range(3)), 10)
        assert len(parts) == 3
        assert all(parts)

    def test_empty_input(self):
        assert bd.split_into_parts([], 10) == []


# ---------------------------------------------------------------------------
# Ordering
# ---------------------------------------------------------------------------


class TestOrdering:
    @pytest.mark.skipif(bd.zipf_frequency is None, reason="wordfreq not installed")
    def test_sorted_easy_to_hard(self):
        sentences = [
            "The cat sat on the warm mat by the door.",
            "The ubiquitous proliferation of regulations exacerbates inefficiency.",
            "I want to eat an apple now.",
        ]
        scored = [(bd.score_difficulty(sentence), sentence) for sentence in sentences]
        scored.sort()
        assert scored[0][1] == "I want to eat an apple now."
        assert scored[-1][1].startswith("The ubiquitous")

    def test_even_sample_covers_the_range(self):
        pairs = [object() for _ in range(1000)]
        sample = bd.select_even_sample(pairs, 100)
        assert len(sample) == 100

    def test_even_sample_returns_all_when_under_target(self):
        pairs = [object() for _ in range(50)]
        assert len(bd.select_even_sample(pairs, 100)) == 50


# ---------------------------------------------------------------------------
# Validation gate
# ---------------------------------------------------------------------------


def make_deck(total_cards: int, parts: int = 10, version: int = 1) -> dict:
    groups = bd.split_into_parts(list(range(total_cards)), parts)
    deck_parts = []
    for index, group in enumerate(groups, start=1):
        cards = []
        for offset in group:
            cards.append(
                {
                    "id": f"card-{offset}",
                    "english": "A perfectly ordinary sentence for testing.",
                    "persian": "یک جمله‌ی کاملاً معمولی برای آزمایش.",
                    "vocab": [],
                    "reviewed": False,
                }
            )
        deck_parts.append({"index": index, "cards": cards})
    return {
        "version": version,
        "title": "Test deck",
        "source_lang": "en",
        "target_lang": "fa",
        "license": "CC BY 2.0 FR",
        "parts": deck_parts,
    }


class TestValidateDeck:
    def test_accepts_a_valid_deck(self):
        bd.validate_deck(make_deck(300))

    def test_rejects_too_few_cards(self):
        with pytest.raises(bd.DeckValidationError, match="minimum"):
            bd.validate_deck(make_deck(200))

    def test_rejects_a_thin_part(self):
        deck = make_deck(300)
        deck["parts"][0]["cards"] = deck["parts"][0]["cards"][:5]
        with pytest.raises(bd.DeckValidationError, match="minimum is 30"):
            bd.validate_deck(deck)

    def test_rejects_missing_field(self):
        deck = make_deck(300)
        del deck["license"]
        with pytest.raises(bd.DeckValidationError, match="license"):
            bd.validate_deck(deck)

    def test_rejects_wrong_languages(self):
        deck = make_deck(300)
        deck["target_lang"] = "de"
        with pytest.raises(bd.DeckValidationError, match="English to Persian"):
            bd.validate_deck(deck)

    def test_rejects_non_contiguous_indices(self):
        deck = make_deck(300)
        deck["parts"][0]["index"] = 99
        with pytest.raises(bd.DeckValidationError, match="indices"):
            bd.validate_deck(deck)

    def test_rejects_duplicate_ids(self):
        deck = make_deck(300)
        deck["parts"][1]["cards"][0]["id"] = deck["parts"][0]["cards"][0]["id"]
        with pytest.raises(bd.DeckValidationError, match="duplicate"):
            bd.validate_deck(deck)

    def test_rejects_card_without_translation(self):
        deck = make_deck(300)
        deck["parts"][0]["cards"][0]["persian"] = ""
        with pytest.raises(bd.DeckValidationError, match="Persian"):
            bd.validate_deck(deck)


# ---------------------------------------------------------------------------
# Link direction verification
# ---------------------------------------------------------------------------


class TestLoadLinks:
    def _write_bz2(self, tmp_path, content: str):
        import bz2

        path = tmp_path / "links.tsv.bz2"
        path.write_bytes(bz2.compress(content.encode("utf-8")))
        return str(path)

    def test_forward_direction_is_kept(self, tmp_path):
        path = self._write_bz2(tmp_path, "1\t100\n2\t200\n3\t300\n")
        known = {1, 2, 3, 100, 200, 300}
        links, inverted = bd.load_links(path, known)
        assert not inverted
        assert links[0] == (1, 100)

    def test_inverted_direction_is_swapped(self, tmp_path):
        # The second column holds the ids we know, so the file is reversed.
        path = self._write_bz2(tmp_path, "100\t1\n200\t2\n300\t3\n400\t1\n")
        known = {1, 2, 3}
        links, inverted = bd.load_links(path, known)
        assert inverted
        assert links[0][0] in known

    def test_empty_file_raises(self, tmp_path):
        path = self._write_bz2(tmp_path, "")
        with pytest.raises(RuntimeError):
            bd.load_links(path, set())


# ---------------------------------------------------------------------------
# Sentence loading
# ---------------------------------------------------------------------------


class TestLoadSentences:
    def _write_bz2(self, tmp_path, content: str):
        import bz2

        path = tmp_path / "sentences.tsv.bz2"
        path.write_bytes(bz2.compress(content.encode("utf-8")))
        return str(path)

    def test_parses_tab_separated_rows(self, tmp_path):
        path = self._write_bz2(tmp_path, "1\teng\tHello world.\n2\tpes\tسلام دنیا.\n")
        sentences = bd.load_sentences(path, {"eng"})
        assert sentences == {1: ("eng", "Hello world.")}

    def test_keeps_tabs_inside_the_text(self, tmp_path):
        path = self._write_bz2(tmp_path, "1\teng\tHello\tthere.\n")
        sentences = bd.load_sentences(path, {"eng"})
        assert sentences[1][1] == "Hello\tthere."

    def test_raises_on_unknown_format(self, tmp_path):
        path = self._write_bz2(tmp_path, "this is not a tsv row\n")
        with pytest.raises(RuntimeError, match="tab-separated"):
            bd.load_sentences(path, {"eng"})


# ---------------------------------------------------------------------------
# Document assembly
# ---------------------------------------------------------------------------


class TestBuildDeckDocument:
    def test_document_shape(self):
        pairs = [
            bd.Pair(
                english_id=index,
                persian_id=index + 1000,
                english="Simple sentence number one here.",
                persian="جمله‌ی ساده‌ی شماره یک اینجا.",
                difficulty=0.3,
                cefr="A2",
            )
            for index in range(1, 301)
        ]
        document = bd.build_deck_document(pairs, parts=10, title="T", version=1)
        assert document["source_lang"] == "en"
        assert document["target_lang"] == "fa"
        assert len(document["parts"]) == 10
        assert document["parts"][0]["index"] == 1
        assert document["parts"][0]["cards"][0]["id"].startswith("tat-1-")

    def test_document_serialises_to_json(self):
        pairs = [
            bd.Pair(1, 2, "A sentence for the file.", "یک جمله برای فایل.", 0.1, "A1")
        ]
        document = bd.build_deck_document(pairs, parts=1, title="T", version=1)
        encoded = json.dumps(document, ensure_ascii=False)
        assert "یک جمله" in encoded


# ---------------------------------------------------------------------------
# Credits
# ---------------------------------------------------------------------------


class TestCredits:
    def test_credits_csv_escapes_commas(self, tmp_path):
        pair = bd.Pair(1, 2, "Sentence.", "جمله.", english_author="Doe, Jane")
        path = str(tmp_path / "credits.csv")
        bd.write_credits(path, [pair])
        content = open(path, encoding="utf-8").read()
        assert '"Doe, Jane"' in content
        assert content.splitlines()[0].startswith("card_id,")
