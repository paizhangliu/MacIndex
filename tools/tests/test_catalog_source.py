#!/usr/bin/env python3

import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIXTURES = Path(__file__).resolve().parent / "fixtures"
sys.path.insert(0, str(ROOT / "tools"))

from catalog_source import (
    normalize_search_text,
    parse_rich_text,
    read_identity_entries,
    validate_legacy_machine_names,
    validate_search_offsets,
)


def load_fixture(name):
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def utf16_slice(value, start, end):
    encoded = value.encode("utf-16-le")
    return encoded[start * 2:end * 2].decode("utf-16-le")


class CatalogSourceTest(unittest.TestCase):

    def test_search_normalization_golden_cases(self):
        document = load_fixture("normalization_golden.json")
        self.assertEqual(1, document["schema"])
        self.assertTrue(document["cases"])
        for case in document["cases"]:
            with self.subTest(raw=case["raw"]):
                self.assertEqual(
                    case["normalized"], normalize_search_text(case["raw"])
                )

    def test_rich_text_golden_cases_use_java_utf16_offsets(self):
        document = load_fixture("text_range_golden.json")
        self.assertEqual(2, document["schema"])
        self.assertTrue(document["cases"])
        for case in document["cases"]:
            expected_ranges = tuple(
                (text_range["start"], text_range["end"])
                for text_range in case["ranges"]
            )
            with self.subTest(marked=case["marked"]):
                self.assertEqual(
                    (case["text"], expected_ranges),
                    parse_rich_text(case["marked"], "golden test case"),
                )
                for text_range in case["ranges"]:
                    self.assertEqual(
                        text_range["substring"],
                        utf16_slice(
                            case["text"], text_range["start"], text_range["end"]
                        ),
                    )

    def test_search_values_keep_normalized_utf16_offsets(self):
        validate_search_offsets("Mac ±", "stable value")
        with self.assertRaisesRegex(RuntimeError, "changes text offsets"):
            validate_search_offsets("Oﬃce", "expanding value")

    def test_codenames_use_the_same_structured_value_as_display_and_search(self):
        entries = read_identity_entries({
            "codenames": [
                {"value": "J185", "qualifier": "iMac20,1"},
                {"value": "Kanga"},
            ],
        }, "codenames", "fixture")
        self.assertEqual((
            {"value": "J185", "qualifier": "iMac20,1", "revisions": ()},
            {"value": "Kanga", "qualifier": None, "revisions": ()},
        ), entries)
        with self.assertRaisesRegex(RuntimeError, "Illegal codenames entry"):
            read_identity_entries(
                {"codenames": ["J185 (iMac20,1)"]}, "codenames", "fixture"
            )

    def test_order_numbers_require_structured_revisions(self):
        entries = read_identity_entries({
            "order_numbers": [
                {"value": "MC700", "revisions": ["A", "B"]},
            ],
        }, "order_numbers", "fixture")
        self.assertEqual(({
            "value": "MC700", "qualifier": None, "revisions": ("A", "B"),
        },), entries)
        with self.assertRaisesRegex(RuntimeError, "Missing order number revisions"):
            read_identity_entries(
                {"order_numbers": [{"value": "MC700"}]},
                "order_numbers", "fixture"
            )

    def test_legacy_names_validate_content_not_a_historical_count(self):
        machines = ({
            "uid": "MI000001",
            "names": ({"value": "Macintosh", "qualifier": None},),
        },)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "legacy_names.json"
            path.write_text(json.dumps({
                "schema": 1,
                "names": [{"name": "Macintosh", "uid": "MI000001"}],
            }), encoding="utf-8")
            validate_legacy_machine_names(machines, path, normalize_search_text)

            path.write_text(json.dumps({"schema": 1, "names": []}), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "Illegal legacy machine names document"):
                validate_legacy_machine_names(machines, path, normalize_search_text)


if __name__ == "__main__":
    unittest.main()
