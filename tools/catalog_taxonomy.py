#!/usr/bin/env python3

"""Load and validate the sole authoring manifest for catalog taxonomy."""

import json
import re


KEY = re.compile(r"[a-z0-9_]+")
EXPECTED_BROWSE_DEFINITIONS = {"names", "processors"}
EXPECTED_BROWSE_SECTIONS = {"desktop", "laptop", "server"}


def fail(message):
    raise RuntimeError(message)


def _reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail(f'Duplicate taxonomy JSON key "{key}"')
        result[key] = value
    return result


def _require_key(value, label):
    if not isinstance(value, str) or KEY.fullmatch(value) is None:
        fail(f"Illegal {label}: {value}")
    return value


def _require_unique_keys(entries, label):
    keys = []
    for entry in entries:
        if not isinstance(entry, dict) or "key" not in entry:
            fail(f"Illegal {label} entry")
        keys.append(_require_key(entry["key"], f"{label} key"))
    if len(keys) != len(set(keys)):
        fail(f"Duplicate {label} key")
    return tuple(keys)


def load_catalog_taxonomy(path):
    try:
        raw = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (OSError, ValueError) as error:
        fail(f"Unable to read catalog taxonomy: {error}")
    if not isinstance(raw, dict) or set(raw) != {
            "categories", "browse_definitions"
    }:
        fail("Illegal catalog taxonomy document")

    categories = raw["categories"]
    if not isinstance(categories, list) or not categories:
        fail("Catalog taxonomy has no categories")
    category_keys = _require_unique_keys(categories, "category")
    category_manufacturers = {}
    category_product_types = {}
    for entry in categories:
        if set(entry) != {"key", "manufacturer", "product_type"}:
            fail(f'Illegal category entry "{entry.get("key")}"')
        category = entry["key"]
        manufacturer = _require_key(entry["manufacturer"], "manufacturer key")
        product_type = _require_key(entry["product_type"], "product type key")
        category_manufacturers[category] = manufacturer
        category_product_types[category] = product_type

    definitions = raw["browse_definitions"]
    if not isinstance(definitions, list) or not definitions:
        fail("Catalog taxonomy has no browse definitions")
    definition_keys = _require_unique_keys(definitions, "browse definition")
    if set(definition_keys) != EXPECTED_BROWSE_DEFINITIONS:
        fail("Catalog taxonomy must define names and processors")
    normalized_definitions = {}
    for definition in definitions:
        if set(definition) != {"key", "groups"}:
            fail(f'Illegal browse definition "{definition.get("key")}"')
        definition_key = definition["key"]
        groups = definition["groups"]
        if not isinstance(groups, list) or not groups:
            fail(f'Empty browse definition "{definition_key}"')
        _require_unique_keys(groups, f"{definition_key} group")
        sections = []
        normalized_groups = []
        for position, group in enumerate(groups):
            if set(group) not in ({"key", "label"}, {"key", "label", "section"}):
                fail(f'Illegal group "{group.get("key")}" in {definition_key}')
            label = group["label"]
            if not isinstance(label, str) or not label or label != label.strip():
                fail(f'Illegal label for group "{group["key"]}"')
            section = group.get("section")
            if section is not None:
                section = _require_key(section, "browse section")
                if section not in EXPECTED_BROWSE_SECTIONS:
                    fail(
                        f'Unknown browse section "{section}" in '
                        f'"{definition_key}"'
                    )
                sections.append((position, section))
            normalized_groups.append({
                "key": group["key"],
                "label": label,
                "section_key": section,
            })
        if sections and sections[0][0] != 0:
            fail(f'First section in "{definition_key}" must begin at group zero')
        normalized_definitions[definition_key] = {
            "groups": tuple(normalized_groups),
        }

    product_type_keys = {
        group["key"] for group in normalized_definitions["names"]["groups"]
    }
    configured_product_types = set(category_product_types.values())
    if configured_product_types != product_type_keys:
        fail(
            "Category product types differ from the names browse groups: "
            f"missing={sorted(product_type_keys - configured_product_types)}, "
            f"unexpected={sorted(configured_product_types - product_type_keys)}"
        )

    return {
        "categories": category_keys,
        "category_manufacturers": category_manufacturers,
        "category_product_types": category_product_types,
        "browse_definitions": normalized_definitions,
    }
