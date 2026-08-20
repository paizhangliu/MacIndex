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
from catalog_search import (
    FIELD_NAME,
    FIELD_PROCESSOR,
    MAPPING_DIRECT,
    compile_search_lexicon,
)
from generate_catalog import browse_definitions


def load_fixture(name):
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def utf16_slice(value, start, end):
    encoded = value.encode("utf-16-le")
    return encoded[start * 2:end * 2].decode("utf-16-le")


class CatalogSourceTest(unittest.TestCase):

    def test_search_normalization_golden_cases(self):
        document = load_fixture("normalization_golden.json")
        self.assertTrue(document["cases"])
        for case in document["cases"]:
            with self.subTest(raw=case["raw"]):
                self.assertEqual(
                    case["normalized"], normalize_search_text(case["raw"])
                )

    def test_rich_text_golden_cases_use_java_utf16_offsets(self):
        document = load_fixture("text_range_golden.json")
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
        with self.assertRaisesRegex(RuntimeError, "require search boundary review"):
            validate_search_offsets("q\u0301", "combining value")

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

    def test_legacy_names_require_a_valid_nonempty_mapping(self):
        machines = ({
            "uid": "MI000001",
            "names": ({"value": "Macintosh", "qualifier": None},),
        },)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "legacy_names.json"
            path.write_text(json.dumps({
                "names": [{"name": "Macintosh", "uid": "MI000001"}],
            }), encoding="utf-8")
            validate_legacy_machine_names(machines, path)

            path.write_text(json.dumps({"names": []}), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "Illegal legacy machine names document"):
                validate_legacy_machine_names(machines, path)

    def test_catalog_compiles_query_vocabulary_and_browse_membership(self):
        machine = {
            "uid": "MI000001",
            "product_type_key": "notebook",
            "processor_family_keys": ("m1",),
            "introductions": ({"year": 2020, "month": 11},),
            "names": ({"value": "MacBook Pro"},),
            "codenames": ({"value": "DBLite Fixture"},),
            "order_numbers": ({"value": "MC700"},),
            "derived_search_values": (
                {
                    "value": "MBP", "field": FIELD_NAME,
                    "exact_token_only": True, "display_mapping": MAPPING_DIRECT,
                },
                {
                    "value": "M1 Pro", "field": FIELD_PROCESSOR,
                    "exact_token_only": False, "display_mapping": MAPPING_DIRECT,
                },
            ),
        }
        lexicon = compile_search_lexicon((machine,))
        self.assertIn("macbook pro", lexicon["phrase_candidates"])
        self.assertIn("book pro", lexicon["phrase_candidates"])
        self.assertIn("lite fixture", lexicon["phrase_candidates"])
        self.assertIn("m1 pro", lexicon["atomic_phrases"])
        self.assertIn("mbp", lexicon["compact_name_aliases"])
        self.assertEqual(("mc700",), lexicon["part_number_stems"])

        older = dict(machine, uid="MI000002",
                     introductions=({"year": 2019, "month": 1},))
        taxonomy = {"browse_definitions": {
            "names": {"groups": ({
                "key": "notebook", "label": "Notebook", "section_key": None,
            },)},
            "processors": {"groups": ({
                "key": "m1", "label": "M1", "section_key": None,
            },)},
        }}
        definitions = browse_definitions(taxonomy, (machine, older))
        self.assertEqual(
            ("MI000002", "MI000001"),
            definitions[0]["groups"][0]["machine_uids"],
        )


if __name__ == "__main__":
    unittest.main()
