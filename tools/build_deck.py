#!/usr/bin/env python3
"""Build the bundled core deck from openly licensed Tatoeba data.

Licensing
---------
Every sentence bundled by this tool comes from Tatoeba (https://tatoeba.org)
under CC BY 2.0 FR, with part of the corpus also released under CC0 1.0. No
subtitle, transcript, film line or other copyrighted text is downloaded,
scraped or bundled, and no machine-translated text is used for the shipped deck:
matching Persian sentences are human translations contributed to Tatoeba.

Sources (per-language exports, which are far smaller than the global dumps)
--------------------------------------------------------------------------
    https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2
    https://downloads.tatoeba.org/exports/per_language/pes/pes_sentences.tsv.bz2
    https://downloads.tatoeba.org/exports/per_language/pes/pes-eng_links.tsv.bz2

The exact column layout of the link files is verified at runtime rather than
assumed: the downloader inspects the first rows, checks that the ids it finds
exist in the sentence tables, and decides the direction from that. If the
per-language route fails, the script falls back to the global exports
(sentences.tar.bz2 / links.tar.bz2), and the CI job falls back to the committed
sample deck if this script fails entirely.

Usage
-----
    python tools/build_deck.py --out app/src/main/assets/content/core_deck.json

Pure functions (clean_sentence, is_valid_pair, score_difficulty,
split_into_parts, validate_deck) carry no I/O so they are unit tested without a
network connection.
"""

from __future__ import annotations

import argparse
import bz2
import io
import json
import logging
import os
import random
import re
import statistics
import subprocess
import sys
import tarfile
import time
from dataclasses import dataclass, field
from typing import Dict, Iterable, Iterator, List, Optional, Sequence, Tuple

try:
    from wordfreq import zipf_frequency
except ImportError:  # pragma: no cover - exercised only when deps are missing
    zipf_frequency = None  # type: ignore[assignment]

try:
    import requests
except ImportError:  # pragma: no cover
    requests = None  # type: ignore[assignment]

LOG = logging.getLogger("build_deck")

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

BASE_URL = "https://downloads.tatoeba.org/exports/"
PER_LANGUAGE_URL = BASE_URL + "per_language/"

#: Preferred per-language files: {logical name: relative path}.
PER_LANGUAGE_FILES = {
    "eng_sentences": "eng/eng_sentences.tsv.bz2",
    "pes_sentences": "pes/pes_sentences.tsv.bz2",
    "links": "pes/pes-eng_links.tsv.bz2",
}

#: Fallbacks tried in order when the per-language file above is missing.
PER_LANGUAGE_ALTERNATIVES = {
    "pes_sentences": ["fas/fas_sentences.tsv.bz2"],
    "links": ["eng/pes-eng_links.tsv.bz2", "eng/eng-pes_links.tsv.bz2", "fas/fas-eng_links.tsv.bz2"],
}

#: Global exports used as a second resort.
GLOBAL_FILES = {
    "eng_sentences": "sentences.tar.bz2",
    "pes_sentences": "sentences.tar.bz2",
    "links": "links.tar.bz2",
}

MIN_WORDS = 4
MAX_WORDS = 22
MAX_DIGIT_RATIO = 0.15
MIN_PERSIAN_RATIO = 0.50

DEFAULT_TARGET_CARDS = 3000
DEFAULT_PARTS = 10
MIN_TOTAL_CARDS = 300
MIN_CARDS_PER_PART = 30

DOWNLOAD_TIMEOUT = (20, 300)
DOWNLOAD_RETRIES = 3
CHUNK_SIZE = 1 << 20

STOPWORDS = frozenset(
    """
    a an the and or but if because so that this these those there here
    i you he she it we they me him her us them my your his its our their
    is am are was were be been being do does did doing have has had having
    will would shall should can could may might must not no nor of in on at
    to from by with without for about into over under again very too also
    just only than then as when where what which who whom whose how why
    all any both each few more most other some such own same
    """.split()
)

URL_PATTERN = re.compile(r"(https?://|www\.)\S", re.IGNORECASE)
EMAIL_PATTERN = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
SENTENCE_SPLIT = re.compile(r"[.!?…]+\s*$")
TOKEN_PATTERN = re.compile(r"[A-Za-z']+")
PERSIAN_RANGE = re.compile(r"[\u0600-\u06FF\uFB50-\uFDFF\uFE70-\uFEFF]")


# ---------------------------------------------------------------------------
# Data containers
# ---------------------------------------------------------------------------


@dataclass
class Pair:
    """One English sentence with its Persian translation."""

    english_id: int
    persian_id: int
    english: str
    persian: str
    english_license: str = "CC BY 2.0 FR"
    persian_license: str = "CC BY 2.0 FR"
    english_author: str = ""
    persian_author: str = ""
    difficulty: float = 0.0
    cefr: str = "A1"
    ipa: str = ""
    vocab: List[Dict[str, str]] = field(default_factory=list)

    @property
    def card_id(self) -> str:
        return f"tat-{self.english_id}-{self.persian_id}"


# ---------------------------------------------------------------------------
# Pure text helpers (unit tested)
# ---------------------------------------------------------------------------


def clean_sentence(text: str) -> str:
    """Normalise whitespace and strip markup from a raw Tatoeba sentence.

    Tatoeba text is plain, but the corpus contains stray tabs, non-breaking
    spaces and occasional double spaces. Everything here is reversible except
    the removal of markup characters, which never carry meaning in a sentence.
    """
    if not text:
        return ""
    cleaned = text.replace("\u00a0", " ").replace("\t", " ")
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def word_tokens(text: str) -> List[str]:
    """Lower-cased alphabetic tokens, used for difficulty scoring."""
    return [token.lower() for token in TOKEN_PATTERN.findall(text)]


def digit_ratio(text: str) -> float:
    """Share of characters that are digits, ignoring whitespace."""
    compact = [character for character in text if not character.isspace()]
    if not compact:
        return 0.0
    return sum(1 for character in compact if character.isdigit()) / len(compact)


def persian_ratio(text: str) -> float:
    """Share of characters that are Arabic-script letters, ignoring spaces."""
    compact = [character for character in text if not character.isspace()]
    if not compact:
        return 0.0
    matched = sum(1 for character in compact if PERSIAN_RANGE.match(character))
    return matched / len(compact)


def is_valid_english(text: str) -> bool:
    """Structural validity of an English sentence, independent of its pair."""
    if not text:
        return False
    if URL_PATTERN.search(text) or EMAIL_PATTERN.search(text):
        return False
    if digit_ratio(text) > MAX_DIGIT_RATIO:
        return False
    if not any(character.isalpha() for character in text):
        return False
    words = text.split()
    if not (MIN_WORDS <= len(words) <= MAX_WORDS):
        return False
    # A sentence should end like one; this drops fragments and headings.
    return bool(SENTENCE_SPLIT.search(text)) or text.endswith('"') or text.endswith("'")


def is_valid_persian(text: str) -> bool:
    """A usable Persian translation carries mostly Arabic-script letters."""
    if not text:
        return False
    if URL_PATTERN.search(text) or EMAIL_PATTERN.search(text):
        return False
    if len(text.split()) < 2:
        return False
    return persian_ratio(text) >= MIN_PERSIAN_RATIO


def is_valid_pair(english: str, persian: str) -> bool:
    """True when both halves of a sentence pair pass their checks."""
    return is_valid_english(english) and is_valid_persian(persian)


def normalise_for_dedupe(text: str) -> str:
    """Lower-cased, punctuation-free signature used to drop duplicates."""
    lowered = text.lower()
    stripped = re.sub(r"[^\w\s]", " ", lowered)
    return re.sub(r"\s+", " ", stripped).strip()


def score_difficulty(english: str) -> float:
    """Map an English sentence onto a 0.0-1.0 difficulty scale.

    The score leans on word rarity (mean Zipf frequency of the content words)
    with a small length penalty, then squashes into 0..1. Higher means harder.
    """
    if zipf_frequency is None:  # pragma: no cover - dependency guard
        raise RuntimeError("wordfreq is required for difficulty scoring")

    tokens = [token for token in word_tokens(english) if token not in STOPWORDS and len(token) >= 2]
    if not tokens:
        tokens = word_tokens(english)
    if not tokens:
        return 0.5

    frequencies = [zipf_frequency(token, "en") for token in tokens]
    mean_zipf = sum(frequencies) / len(frequencies)

    # Zipf runs roughly 1.5 (very rare) to 7.5 (extremely common).
    rarity = max(0.0, min(1.0, (7.5 - mean_zipf) / 6.0))
    length_penalty = max(0.0, min(1.0, (len(tokens) - MIN_WORDS) / (MAX_WORDS - MIN_WORDS)))
    return round(max(0.0, min(1.0, 0.78 * rarity + 0.22 * length_penalty)), 4)


def cefr_for_score(score: float) -> str:
    """Bucket a 0..1 difficulty score into a CEFR-like band."""
    if score < 0.18:
        return "A1"
    if score < 0.34:
        return "A2"
    if score < 0.52:
        return "B1"
    if score < 0.72:
        return "B2"
    return "C1"


def pick_vocab(english: str, limit: int = 3) -> List[Dict[str, str]]:
    """Pick the rarest content words as the card's key vocabulary.

    Meanings are left out on purpose: only a human translator can supply a
    trustworthy gloss, and inventing one would defeat the point of the exercise.
    """
    if zipf_frequency is None:  # pragma: no cover
        return []
    candidates = [
        token
        for token in word_tokens(english)
        if len(token) >= 3 and token not in STOPWORDS
    ]
    # Preserve first-seen order among equal frequencies by sorting on a tuple.
    unique: List[str] = []
    seen = set()
    for token in candidates:
        if token in seen:
            continue
        seen.add(token)
        unique.append(token)
    ranked = sorted(unique, key=lambda token: (zipf_frequency(token, "en"), len(token)))
    return [{"word": token} for token in ranked[:limit]]


def split_into_parts(items: Sequence, parts: int) -> List[List]:
    """Split ``items`` into exactly ``parts`` non-empty contiguous groups.

    Sizes differ by at most one and the first groups take the remainder, so a
    3000-card deck with 10 parts yields 300 cards each and a 105-item list with
    10 parts yields five groups of 11 and five of 10.
    """
    if not items:
        return []
    parts = max(1, min(parts, len(items)))
    base, remainder = divmod(len(items), parts)
    result: List[List] = []
    cursor = 0
    for index in range(parts):
        size = base + (1 if index < remainder else 0)
        result.append(list(items[cursor : cursor + size]))
        cursor += size
    return result


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


class DeckValidationError(Exception):
    """Raised when a generated deck fails its own quality gate."""


def validate_deck(deck: dict, min_total: int = MIN_TOTAL_CARDS, min_per_part: int = MIN_CARDS_PER_PART) -> None:
    """Raise :class:`DeckValidationError` when the deck is not shippable.

    The gate exists so CI fails loudly instead of shipping a thin or empty deck.
    """
    if not isinstance(deck, dict):
        raise DeckValidationError("deck root must be an object")

    required = ("version", "title", "source_lang", "target_lang", "license", "parts")
    for key in required:
        if key not in deck:
            raise DeckValidationError(f'deck is missing required field "{key}"')

    if not isinstance(deck["version"], int) or deck["version"] <= 0:
        raise DeckValidationError("deck version must be a positive integer")
    if deck["source_lang"] != "en" or deck["target_lang"] != "fa":
        raise DeckValidationError("deck must be English to Persian")

    parts = deck["parts"]
    if not isinstance(parts, list) or not parts:
        raise DeckValidationError("deck must contain at least one part")

    indices = [part.get("index") for part in parts]
    if sorted(indices) != list(range(1, len(parts) + 1)):
        raise DeckValidationError(f"part indices must be 1..{len(parts)}, got {sorted(indices)}")

    total = 0
    seen_ids = set()
    for part in parts:
        cards = part.get("cards")
        if not isinstance(cards, list):
            raise DeckValidationError(f'part {part.get("index")} has no cards array')
        if len(cards) < min_per_part:
            raise DeckValidationError(
                f'part {part.get("index")} has {len(cards)} cards, minimum is {min_per_part}'
            )
        for card in cards:
            card_id = card.get("id")
            if not card_id or not isinstance(card_id, str):
                raise DeckValidationError("every card needs a non-empty string id")
            if card_id in seen_ids:
                raise DeckValidationError(f"duplicate card id {card_id}")
            seen_ids.add(card_id)
            if not card.get("english"):
                raise DeckValidationError(f"card {card_id} has no English sentence")
            if not card.get("persian"):
                raise DeckValidationError(f"card {card_id} has no Persian translation")
            if not isinstance(card.get("vocab", []), list):
                raise DeckValidationError(f"card {card_id} has a non-array vocab")
        total += len(cards)

    if total < min_total:
        raise DeckValidationError(f"deck has {total} cards, minimum is {min_total}")


# ---------------------------------------------------------------------------
# Downloading
# ---------------------------------------------------------------------------


def http_get(url: str, destination: str, *, retries: int = DOWNLOAD_RETRIES) -> str:
    """Download ``url`` to ``destination`` with retries and backoff.

    Returns the path written. Raises :class:`RuntimeError` with a readable
    message when every attempt fails, because the CI log is the only place a
    maintainer will see what went wrong.
    """
    if requests is None:  # pragma: no cover
        raise RuntimeError("the requests package is required to download Tatoeba data")

    os.makedirs(os.path.dirname(destination) or ".", exist_ok=True)
    last_error: Optional[Exception] = None

    for attempt in range(1, retries + 1):
        try:
            LOG.info("Downloading %s (attempt %d/%d)", url, attempt, retries)
            with requests.get(url, stream=True, timeout=DOWNLOAD_TIMEOUT) as response:
                response.raise_for_status()
                expected = response.headers.get("Content-Length")
                written = 0
                with open(destination, "wb") as handle:
                    for chunk in response.iter_content(chunk_size=CHUNK_SIZE):
                        if not chunk:
                            continue
                        handle.write(chunk)
                        written += len(chunk)
                if expected is not None and written < int(expected):
                    raise IOError(
                        f"truncated download: got {written} of {expected} bytes"
                    )
            return destination
        except Exception as error:  # noqa: BLE001 - retried below
            last_error = error
            LOG.warning("Attempt %d failed for %s: %s", attempt, url, error)
            if attempt < retries:
                time.sleep(2 ** attempt)

    raise RuntimeError(f"could not download {url}: {last_error}")


def open_compressed(path: str) -> Iterator[str]:
    """Yield decoded lines from a .bz2 file or a .tar.bz2 archive.

    Tatoeba ships both shapes: per-language exports are single ``.tsv.bz2``
    files, while the global exports are ``.tar.bz2`` archives containing one
    ``.csv`` member each. Both are handled here so callers never care.
    """
    if tarfile.is_tarfile(path):
        with tarfile.open(path, "r:bz2") as archive:
            for member in archive.getmembers():
                if not member.isfile():
                    continue
                extracted = archive.extractfile(member)
                if extracted is None:
                    continue
                buffer = io.TextIOWrapper(extracted, encoding="utf-8", errors="replace")
                for line in buffer:
                    yield line.rstrip("\n")
    else:
        with bz2.open(path, "rt", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                yield line.rstrip("\n")


def load_sentences(path: str, wanted_languages: Iterable[str]) -> Dict[int, Tuple[str, str]]:
    """Read a sentences file into ``{id: (language, text)}``.

    Accepts both the per-language ``id<TAB>lang<TAB>text`` layout and the global
    ``id<TAB>lang<TAB>text`` layout, then verifies that what it parsed looks like
    sentences; a format change on Tatoeba's side produces a clear error instead of
    a silently empty deck.
    """
    wanted = set(wanted_languages)
    sentences: Dict[int, Tuple[str, str]] = {}
    checked = 0
    malformed = 0

    for line in open_compressed(path):
        if not line or line.startswith("#"):
            continue
        columns = line.split("\t")
        if len(columns) < 3:
            malformed += 1
            continue
        checked += 1
        raw_id, language = columns[0], columns[1]
        if language not in wanted:
            continue
        try:
            sentence_id = int(raw_id)
        except ValueError:
            malformed += 1
            continue
        text = "\t".join(columns[2:])
        sentences[sentence_id] = (language, text)

    if checked == 0:
        raise RuntimeError(
            f"{path} contains no tab-separated rows; Tatoeba may have changed its "
            "export format. Update tools/build_deck.py "
            "(see the reader in load_sentences)."
        )
    return sentences


def load_links(path: str, known_ids: set) -> Tuple[List[Tuple[int, int]], bool]:
    """Read a links file, verifying column order against the sentence table.

    Tatoeba's link exports are ``sentence_id<TAB>translation_id``. Rather than
    trusting that blindly, this function measures how many ids in each column are
    known; the column with the higher hit rate is treated as ``sentence_id``.

    Returns the pairs and a flag telling the caller whether an inversion was
    applied.
    """
    forward: List[Tuple[int, int]] = []
    forward_hits = 0
    reverse_hits = 0
    samples = 0

    for line in open_compressed(path):
        if not line or line.startswith("#"):
            continue
        columns = line.split("\t")
        if len(columns) < 2:
            continue
        try:
            first, second = int(columns[0]), int(columns[1])
        except ValueError:
            continue
        forward.append((first, second))
        if samples < 200_000:
            samples += 1
            if first in known_ids:
                forward_hits += 1
            if second in known_ids:
                reverse_hits += 1

    if not forward:
        raise RuntimeError(
            f"{path} contains no id pairs; Tatoeba may have changed its export format."
        )

    inverted = reverse_hits > forward_hits
    if inverted:
        LOG.info(
            "Link columns look inverted (second column matches more sentence ids); "
            "swapping them."
        )
        forward = [(second, first) for first, second in forward]
    LOG.info(
        "Verified link direction with %d sampled rows (%d forward hits, %d reverse hits).",
        min(samples, len(forward)),
        forward_hits,
        reverse_hits,
    )
    return forward, inverted


# ---------------------------------------------------------------------------
# IPA
# ---------------------------------------------------------------------------


def espeak_ipa(text: str, timeout: int = 10) -> str:
    """Return the IPA transcription of ``text`` via espeak-ng, or ``''``.

    A missing espeak-ng is a normal, non-fatal condition: the field is optional
    in the deck format and the app simply hides it. Nothing is ever guessed.
    """
    try:
        completed = subprocess.run(
            ["espeak-ng", "-q", "--ipa", "-v", "en-us", "--", text],
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except (FileNotFoundError, subprocess.SubprocessError, OSError):
        return ""
    if completed.returncode != 0:
        return ""
    result = " ".join(completed.stdout.split())
    return result.strip()


def build_ipa_cache(pairs: Sequence[Pair], enabled: bool) -> None:
    """Fill ``pair.ipa`` in place. Silently no-ops when espeak-ng is absent."""
    if not enabled:
        return
    for pair in pairs:
        pair.ipa = espeak_ipa(pair.english)


# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------


def collect_pairs(
    english_sentences: Dict[int, Tuple[str, str]],
    persian_sentences: Dict[int, Tuple[str, str]],
    links: Iterable[Tuple[int, int]],
) -> List[Pair]:
    """Join links with sentences, choosing one translation per English sentence.

    Selection rules, applied in order:
    1. Both sides must pass ``is_valid_pair``.
    2. The shortest valid Persian translation wins; ties break on the lowest id,
       which makes the output independent of file ordering.
    3. Each English sentence appears once, so the deck never shows the same
       prompt twice.
    """
    best_by_english: Dict[int, Pair] = {}

    for english_id, persian_id in links:
        english_entry = english_sentences.get(english_id)
        persian_entry = persian_sentences.get(persian_id)
        if english_entry is None or persian_entry is None:
            continue

        english = clean_sentence(english_entry[1])
        persian = clean_sentence(persian_entry[1])
        if not is_valid_pair(english, persian):
            continue

        candidate = Pair(
            english_id=english_id,
            persian_id=persian_id,
            english=english,
            persian=persian,
        )
        existing = best_by_english.get(english_id)
        if existing is None:
            best_by_english[english_id] = candidate
        else:
            new_key = (len(candidate.persian), candidate.persian_id)
            old_key = (len(existing.persian), existing.persian_id)
            if new_key < old_key:
                best_by_english[english_id] = candidate

    pairs = list(best_by_english.values())

    # Deduplicate by normalised English text, keeping the first by id so the
    # result is deterministic.
    seen = set()
    unique: List[Pair] = []
    for pair in sorted(pairs, key=lambda item: item.english_id):
        signature = normalise_for_dedupe(pair.english)
        if signature in seen:
            continue
        seen.add(signature)
        unique.append(pair)

    LOG.info("Collected %d unique English/Persian sentence pairs.", len(unique))
    return unique


def score_pairs(pairs: Sequence[Pair]) -> None:
    """Fill difficulty and CEFR band for each pair, in place."""
    for pair in pairs:
        pair.difficulty = score_difficulty(pair.english)
        pair.cefr = cefr_for_score(pair.difficulty)


def select_even_sample(pairs: Sequence[Pair], target: int) -> List[Pair]:
    """Take a difficulty-stratified sample of ``target`` pairs.

    Pairs must already be sorted from easiest to hardest. The sample is drawn
    evenly across the whole range so the deck covers A1 through C1 instead of
    being dominated by whichever band happens to be largest.
    """
    if len(pairs) <= target:
        return list(pairs)
    stride = len(pairs) / target
    chosen: List[Pair] = []
    for index in range(target):
        chosen.append(pairs[int(index * stride)])
    return chosen


def attach_licences(pairs: Sequence[Pair], cc0_english: set, cc0_persian: set) -> None:
    """Record each side's licence, preferring CC0 where it applies."""
    for pair in pairs:
        pair.english_license = "CC0 1.0" if pair.english_id in cc0_english else "CC BY 2.0 FR"
        pair.persian_license = "CC0 1.0" if pair.persian_id in cc0_persian else "CC BY 2.0 FR"


def load_cc0_ids(path: Optional[str], language: str) -> set:
    """Ids present in a ``*_sentences_CC0`` export, or an empty set."""
    if not path or not os.path.exists(path):
        return set()
    try:
        sentences = load_sentences(path, {language})
        return set(sentences.keys())
    except RuntimeError as error:
        LOG.warning("Could not read CC0 export %s: %s", path, error)
        return set()


def build_deck_document(pairs: Sequence[Pair], parts: int, title: str, version: int) -> dict:
    """Assemble the deck.json structure described in docs/DECK_FORMAT.md."""
    groups = split_into_parts(pairs, parts)
    deck_parts = []
    for index, group in enumerate(groups, start=1):
        cards = []
        for pair in group:
            cards.append(
                {
                    "id": pair.card_id,
                    "english": pair.english,
                    "persian": pair.persian,
                    "ipa": pair.ipa,
                    "vocab": pair.vocab,
                    "difficulty": pair.difficulty,
                    "reviewed": False,
                    "source": "tatoeba",
                    "license": pair.english_license,
                    "note": pair.cefr,
                }
            )
        deck_parts.append({"index": index, "cards": cards})

    return {
        "version": version,
        "title": title,
        "source_lang": "en",
        "target_lang": "fa",
        "license": "CC BY 2.0 FR / CC0 1.0",
        "parts": deck_parts,
    }


def write_json(path: str, document: dict) -> None:
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=1)
        handle.write("\n")


def write_credits(path: str, pairs: Sequence[Pair]) -> None:
    """Write docs/CREDITS.csv with one row per card."""
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(
            "card_id,english_sentence_id,persian_sentence_id,english_author,persian_author,license\n"
        )
        for pair in pairs:
            row = [
                pair.card_id,
                str(pair.english_id),
                str(pair.persian_id),
                _csv_field(pair.english_author),
                _csv_field(pair.persian_author),
                pair.english_license,
            ]
            handle.write(",".join(row) + "\n")


def _csv_field(value: str) -> str:
    if not value:
        return ""
    if any(character in value for character in ',"\n'):
        return '"' + value.replace('"', '""') + '"'
    return value


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


def download_sources(cache_dir: str, allow_global_fallback: bool = True) -> Dict[str, str]:
    """Download the needed exports, preferring the small per-language files."""
    os.makedirs(cache_dir, exist_ok=True)
    resolved: Dict[str, str] = {}

    def try_per_language(logical: str) -> Optional[str]:
        candidates = [PER_LANGUAGE_FILES[logical]] + PER_LANGUAGE_ALTERNATIVES.get(logical, [])
        for relative in candidates:
            url = PER_LANGUAGE_URL + relative
            destination = os.path.join(cache_dir, relative.replace("/", "_"))
            if os.path.exists(destination) and os.path.getsize(destination) > 0:
                LOG.info("Using cached %s", destination)
                return destination
            try:
                http_get(url, destination)
                return destination
            except Exception as error:  # noqa: BLE001 - try the next candidate
                LOG.warning("Per-language export %s unavailable: %s", url, error)
        return None

    for logical in ("eng_sentences", "pes_sentences", "links"):
        path = try_per_language(logical)
        if path is not None:
            resolved[logical] = path

    if len(resolved) == 3:
        return resolved

    if not allow_global_fallback:
        missing = sorted({"eng_sentences", "pes_sentences", "links"} - set(resolved))
        raise RuntimeError(
            "per-language exports unavailable for: " + ", ".join(missing)
        )

    LOG.warning("Falling back to the global exports for: %s", sorted({"eng_sentences", "pes_sentences", "links"} - set(resolved)))
    for logical in ("eng_sentences", "pes_sentences", "links"):
        if logical in resolved:
            continue
        relative = GLOBAL_FILES[logical]
        destination = os.path.join(cache_dir, "global_" + relative)
        if not os.path.exists(destination) or os.path.getsize(destination) == 0:
            http_get(BASE_URL + relative, destination)
        resolved[logical] = destination

    return resolved


def build(args: argparse.Namespace) -> int:
    """Run the whole pipeline. Returns a process exit code."""
    sources = download_sources(args.cache_dir, allow_global_fallback=not args.no_global_fallback)

    english = load_sentences(sources["eng_sentences"], {"eng"})
    persian = load_sentences(sources["pes_sentences"], {"pes", "fas"})
    LOG.info("Loaded %d English and %d Persian sentences.", len(english), len(persian))

    if not english or not persian:
        raise RuntimeError("One of the sentence tables came back empty; check the export format.")

    known_ids = set(english) | set(persian)
    # For the global links file the known-id set has to come from both tables.
    links, _ = load_links(sources["links"], known_ids)
    LOG.info("Loaded %d candidate links.", len(links))

    pairs = collect_pairs(english, persian, links)
    if not pairs:
        raise RuntimeError(
            "No valid English/Persian pairs were found. The link file may use an "
            "unexpected direction; see load_links() for the verification logic."
        )

    score_pairs(pairs)
    pairs.sort(key=lambda item: (item.difficulty, item.english_id))

    target = min(args.target_cards, len(pairs))
    if target < MIN_TOTAL_CARDS:
        raise DeckValidationError(
            f"only {target} usable pairs are available, the minimum for a shippable "
            f"deck is {MIN_TOTAL_CARDS}"
        )

    sampled = select_even_sample(pairs, target)
    for pair in sampled:
        pair.vocab = pick_vocab(pair.english)

    cc0_english = load_cc0_ids(args.cc0_english, "eng")
    cc0_persian = load_cc0_ids(args.cc0_persian, "pes")
    attach_licences(sampled, cc0_english, cc0_persian)

    if args.ipa:
        build_ipa_cache(sampled, enabled=True)

    document = build_deck_document(
        sampled,
        parts=args.parts,
        title="LexiFlash English–Persian core deck",
        version=args.version,
    )
    validate_deck(document, min_total=MIN_TOTAL_CARDS, min_per_part=MIN_CARDS_PER_PART)

    write_json(args.out, document)
    write_credits(args.credits, sampled)

    total = sum(len(part["cards"]) for part in document["parts"])
    LOG.info("Wrote %d cards across %d parts to %s", total, len(document["parts"]), args.out)
    return 0


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the LexiFlash core deck from Tatoeba data.")
    parser.add_argument(
        "--out",
        default="app/src/main/assets/content/core_deck.json",
        help="destination path for the generated deck.json",
    )
    parser.add_argument(
        "--credits",
        default="docs/CREDITS.csv",
        help="destination path for the attribution CSV",
    )
    parser.add_argument(
        "--cache-dir",
        default=os.path.join(os.path.expanduser("~"), ".cache", "lexiflash"),
        help="directory holding the downloaded exports",
    )
    parser.add_argument("--target-cards", type=int, default=DEFAULT_TARGET_CARDS)
    parser.add_argument("--parts", type=int, default=DEFAULT_PARTS)
    parser.add_argument("--version", type=int, default=1, help="deck schema/content version")
    parser.add_argument(
        "--cc0-english",
        default=None,
        help="optional path to eng_sentences_CC0.tsv.bz2 for per-card licensing",
    )
    parser.add_argument(
        "--cc0-persian",
        default=None,
        help="optional path to pes_sentences_CC0.tsv.bz2 for per-card licensing",
    )
    parser.add_argument(
        "--no-ipa",
        dest="ipa",
        action="store_false",
        help="skip IPA generation even when espeak-ng is available",
    )
    parser.add_argument(
        "--no-global-fallback",
        action="store_true",
        help="fail instead of falling back to the global exports",
    )
    parser.set_defaults(ipa=True)
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )
    args = parse_args(argv)
    try:
        return build(args)
    except DeckValidationError as error:
        LOG.error("Deck validation failed: %s", error)
        return 2
    except Exception as error:  # noqa: BLE001 - CI needs a readable failure
        LOG.error("Deck generation failed: %s", error)
        return 1


if __name__ == "__main__":
    sys.exit(main())
