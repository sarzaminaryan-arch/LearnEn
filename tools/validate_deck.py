#!/usr/bin/env python3
"""Validate a deck.json file against the schema documented in docs/DECK_FORMAT.md.

Used by CI before a generated deck is shipped, and by hand when checking a deck
someone else produced. Exits non-zero on the first problem class it finds and
prints every violation it collected.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import List

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from build_deck import (  # noqa: E402
    MIN_CARDS_PER_PART,
    MIN_TOTAL_CARDS,
    DeckValidationError,
    validate_deck,
)

MAX_CARDS = 50_000
MAX_PARTS = 200
MAX_TEXT_LENGTH = 2_000
MAX_VOCAB_PER_CARD = 12


def deep_check(deck: dict) -> List[str]:
    """Extra structural checks that the generation gate does not need.

    These mirror the limits enforced by the Kotlin validator on the device, so a
    deck that passes here will also import there.
    """
    problems: List[str] = []

    if deck.get("version", 0) > 1:
        problems.append(f"version {deck['version']} is newer than the app supports (1)")

    parts = deck.get("parts", [])
    if len(parts) > MAX_PARTS:
        problems.append(f"{len(parts)} parts exceeds the {MAX_PARTS} part limit")

    seen = set()
    total = 0
    for part in parts:
        index = part.get("index")
        for card in part.get("cards", []):
            total += 1
            card_id = card.get("id", "")
            if card_id in seen:
                problems.append(f"duplicate card id {card_id!r}")
            seen.add(card_id)
            if len(card_id) > 128:
                problems.append(f"card id longer than 128 characters in part {index}")
            for field in ("english", "persian"):
                value = card.get(field)
                if value is not None and len(value) > MAX_TEXT_LENGTH:
                    problems.append(f"card {card_id!r} field {field} exceeds {MAX_TEXT_LENGTH} characters")
            vocab = card.get("vocab", [])
            if len(vocab) > MAX_VOCAB_PER_CARD:
                problems.append(f"card {card_id!r} has {len(vocab)} vocab entries (max {MAX_VOCAB_PER_CARD})")
            difficulty = card.get("difficulty")
            if difficulty is not None and not (0.0 <= float(difficulty) <= 1.0):
                problems.append(f"card {card_id!r} difficulty {difficulty} is outside 0..1")

    if total > MAX_CARDS:
        problems.append(f"{total} cards exceeds the {MAX_CARDS} card limit")

    return problems


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Validate a LexiFlash deck.json file.")
    parser.add_argument("path", help="path to the deck.json file")
    parser.add_argument(
        "--allow-small",
        action="store_true",
        help=f"skip the {MIN_TOTAL_CARDS} card / {MIN_CARDS_PER_PART} per-part minimums",
    )
    args = parser.parse_args(argv)

    if not os.path.isfile(args.path):
        print(f"error: {args.path} does not exist", file=sys.stderr)
        return 1

    try:
        with open(args.path, encoding="utf-8") as handle:
            deck = json.load(handle)
    except json.JSONDecodeError as error:
        print(f"error: {args.path} is not valid JSON: {error}", file=sys.stderr)
        return 1

    try:
        if args.allow_small:
            deck.setdefault("parts", [])
            validate_deck(deck, min_total=0, min_per_part=0)
        else:
            validate_deck(deck)
    except DeckValidationError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    problems = deep_check(deck)
    if problems:
        for problem in problems:
            print(f"error: {problem}", file=sys.stderr)
        return 1

    total = sum(len(part["cards"]) for part in deck["parts"])
    # A JSON-safe summary is handy when this runs inside CI logs.
    print(
        json.dumps(
            {
                "file": args.path,
                "title": deck.get("title"),
                "version": deck.get("version"),
                "parts": len(deck["parts"]),
                "cards": total,
                "license": deck.get("license"),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
