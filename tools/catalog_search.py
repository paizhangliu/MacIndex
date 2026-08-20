#!/usr/bin/env python3

"""Compile Catalog-owned aliases and processor headings into search values."""

import json
import unicodedata
from pathlib import Path

from catalog_source import normalize_search_text, validate_search_offsets, validate_text


FIELD_NAME = "CATALOG_SEARCH_FIELD_NAME"
FIELD_INTRODUCTION = "CATALOG_SEARCH_FIELD_INTRODUCTION"
FIELD_PROCESSOR = "CATALOG_SEARCH_FIELD_PROCESSOR"

MAPPING_DIRECT = "CATALOG_SEARCH_DISPLAY_MAPPING_DIRECT"
MAPPING_COMPACT_WHITESPACE = "CATALOG_SEARCH_DISPLAY_MAPPING_COMPACT_WHITESPACE"
MAPPING_FIXED = "CATALOG_SEARCH_DISPLAY_MAPPING_FIXED"


def fail(message):
    raise RuntimeError(message)


def _utf16_length(value):
    return len(value.encode("utf-16-le")) // 2


def _utf16_slice(value, start, end):
    encoded = value.encode("utf-16-le")
    return encoded[start * 2:end * 2].decode("utf-16-le")


def _string_list(value, label, *, allow_empty=False):
    if not isinstance(value, list) or (not value and not allow_empty):
        fail(f"Illegal {label}")
    result = tuple(
        validate_text(item, label, single_line=True)
        for item in value
    )
    if len(result) != len(set(result)):
        fail(f"Duplicate {label}")
    return result


def _optional_string_list(document, key, label):
    return _string_list(document.get(key, []), label, allow_empty=True)


def _read_processor_alias(raw, index):
    label = f"processor alias {index}"
    if not isinstance(raw, dict) or set(raw) != {
            "term", "display_term", "processor_family_keys"}:
        fail(f"Illegal {label}")
    return {
        "term": validate_text(raw["term"], f"term for {label}", single_line=True),
        "display_term": validate_text(
            raw["display_term"], f"display term for {label}", single_line=True
        ),
        "processor_family_keys": _string_list(
            raw["processor_family_keys"], f"processor family keys for {label}"
        ),
    }


def _read_machine_alias(raw, index):
    label = f"machine alias {index}"
    allowed = {
        "term", "product_type_keys", "uids", "excluded_uids", "display_terms",
    }
    if not isinstance(raw, dict) or "term" not in raw \
            or not set(raw).issubset(allowed):
        fail(f"Illegal {label}")
    alias = {
        "term": validate_text(raw["term"], f"term for {label}", single_line=True),
        "product_type_keys": _optional_string_list(
            raw, "product_type_keys", f"product type keys for {label}"
        ),
        "uids": _optional_string_list(raw, "uids", f"UIDs for {label}"),
        "excluded_uids": _optional_string_list(
            raw, "excluded_uids", f"excluded UIDs for {label}"
        ),
        "display_terms": _optional_string_list(
            raw, "display_terms", f"display terms for {label}"
        ),
    }
    if not alias["product_type_keys"] and not alias["uids"]:
        fail(f"Missing selector for {label}")
    return alias


def load_search_aliases(path, machines):
    try:
        document = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"Unable to read search aliases: {error}")
    if not isinstance(document, dict) or set(document) != {
            "intel_families", "processor_aliases", "machine_aliases"}:
        fail("Illegal search alias document")

    intel_families = _string_list(document["intel_families"], "Intel families")
    processor_aliases = tuple(
        _read_processor_alias(raw, index)
        for index, raw in enumerate(document["processor_aliases"])
    ) if isinstance(document["processor_aliases"], list) else fail(
        "Illegal processor aliases"
    )
    machine_aliases = tuple(
        _read_machine_alias(raw, index)
        for index, raw in enumerate(document["machine_aliases"])
    ) if isinstance(document["machine_aliases"], list) else fail(
        "Illegal machine aliases"
    )

    for label, aliases in (
            ("processor alias", processor_aliases),
            ("machine alias", machine_aliases)):
        normalized_terms = [normalize_search_text(alias["term"]) for alias in aliases]
        if len(normalized_terms) != len(set(normalized_terms)):
            fail(f"Duplicate {label} term")

    machine_uids = {machine["uid"] for machine in machines}
    product_type_keys = {machine["product_type_key"] for machine in machines}
    processor_family_keys = {
        key for machine in machines for key in machine["processor_family_keys"]
    }
    for alias in processor_aliases:
        unknown = set(alias["processor_family_keys"]) - processor_family_keys
        if unknown:
            fail("Unknown processor alias family keys: " + ", ".join(sorted(unknown)))
        if not any(set(alias["processor_family_keys"])
                   & set(machine["processor_family_keys"]) for machine in machines):
            fail(f'Processor alias "{alias["term"]}" matches no machines')

    for alias in machine_aliases:
        unknown_products = set(alias["product_type_keys"]) - product_type_keys
        unknown_uids = (set(alias["uids"]) | set(alias["excluded_uids"])) - machine_uids
        if unknown_products:
            fail("Unknown machine alias product types: "
                 + ", ".join(sorted(unknown_products)))
        if unknown_uids:
            fail("Unknown machine alias UIDs: " + ", ".join(sorted(unknown_uids)))
        matched = [
            machine for machine in machines
            if _machine_alias_matches(alias, machine)
        ]
        if not matched:
            fail(f'Machine alias "{alias["term"]}" matches no machines')

    return {
        "intel_families": intel_families,
        "processor_aliases": processor_aliases,
        "machine_aliases": machine_aliases,
    }


def _search_value(value, field, *, exact=False, mapping=MAPPING_DIRECT,
                  display_value=None, fixed_range=None, canonical_name=False):
    validate_search_offsets(value, "derived search value")
    return {
        "value": value,
        "field": field,
        "exact_token_only": exact,
        "display_mapping": mapping,
        "display_value": display_value,
        "fixed_display_range": fixed_range,
        "canonical_name": canonical_name,
    }


def compile_derived_search_values(machine, aliases):
    values = []
    for index, name in enumerate(machine["names"]):
        source = name["value"]
        compact = "".join(character for character in source if not character.isspace())
        if compact != source:
            values.append(_search_value(
                compact, FIELD_NAME, mapping=MAPPING_COMPACT_WHITESPACE,
                display_value=source, canonical_name=index == 0,
            ))

    _append_processor_values(machine, aliases, values)

    years = set()
    for introduction in machine["introductions"]:
        year = introduction["year"]
        if year not in years:
            years.add(year)
            values.append(_search_value(
                str(year), FIELD_INTRODUCTION, exact=True,
            ))

    display_name = machine["names"][0]["value"]
    for alias in aliases["machine_aliases"]:
        if not _machine_alias_matches(alias, machine):
            continue
        values.append(_search_value(
            alias["term"], FIELD_NAME, exact=True, mapping=MAPPING_FIXED,
            display_value=display_name,
            fixed_range=_machine_alias_display_range(alias, display_name),
        ))
    return tuple(values)


def compile_search_lexicon(machines):
    """Compile Catalog-specific query vocabulary once for every client."""
    phrase_candidates = set()
    atomic_phrases = set()
    compact_name_aliases = set()
    part_number_stems = set()

    for machine in machines:
        for entry in machine["order_numbers"]:
            part_number_stems.add(normalize_search_text(entry["value"]))

        human_values = [
            (entry["value"], False, False)
            for field_name in ("names", "codenames")
            for entry in machine[field_name]
        ]
        for value in machine["derived_search_values"]:
            field = value["field"]
            if field == FIELD_NAME and value["exact_token_only"]:
                compact_name_aliases.add(normalize_search_text(value["value"]))
            if field in (FIELD_NAME, FIELD_PROCESSOR):
                human_values.append((
                    value["value"], value["exact_token_only"],
                    field == FIELD_PROCESSOR,
                ))

        for value, exact_token_only, processor in human_values:
            normalized = normalize_search_text(value)
            if " " not in normalized:
                continue
            if processor:
                atomic_phrases.add(normalized)
            if exact_token_only:
                continue
            for start in _semantic_word_starts(value):
                suffix = normalize_search_text(value[start:])
                if " " in suffix:
                    phrase_candidates.add(suffix)

    return {
        "phrase_candidates": _java_sorted(phrase_candidates),
        "atomic_phrases": _java_sorted(atomic_phrases),
        "compact_name_aliases": _java_sorted(compact_name_aliases),
        "part_number_stems": _java_sorted(part_number_stems),
    }


def _java_sorted(values):
    return tuple(sorted(values, key=lambda value: value.encode("utf-16-be")))


def _semantic_word_starts(value):
    starts = [0]
    for boundary in range(1, len(value)):
        if _is_human_word(value[boundary]) \
                and _is_human_text_boundary(value, boundary):
            starts.append(boundary)
    return starts


def _is_human_text_boundary(value, boundary):
    previous = value[boundary - 1]
    current = value[boundary]
    if not _is_human_word(previous) or not _is_human_word(current):
        return True
    if previous.islower() and current.isupper():
        return True
    if previous.isupper() and current.isupper() \
            and boundary + 1 < len(value) and value[boundary + 1].islower():
        uppercase_count = 0
        index = boundary - 1
        while index >= 0 and value[index].isupper():
            uppercase_count += 1
            if uppercase_count >= 2:
                return True
            index -= 1
    return False


def _is_human_word(character):
    category = unicodedata.category(character)
    return category.startswith("L") or category == "Nd"


def _machine_alias_matches(alias, machine):
    if machine["uid"] in alias["excluded_uids"]:
        return False
    return machine["product_type_key"] in alias["product_type_keys"] \
        or machine["uid"] in alias["uids"]


def _machine_alias_display_range(alias, display_name):
    normalized_name = normalize_search_text(display_name)
    for display_term in alias["display_terms"]:
        normalized_term = normalize_search_text(display_term)
        start = normalized_name.find(normalized_term)
        if start >= 0:
            return (
                _utf16_length(normalized_name[:start]),
                _utf16_length(normalized_name[:start + len(normalized_term)]),
            )
    return 0, _utf16_length(display_name)


def _append_processor_values(machine, aliases, destination):
    search_terms = set()
    processor = machine["processor"]
    if processor is not None:
        for start, end in machine["processor_model_ranges"]:
            _append_processor_heading_terms(
                _utf16_slice(processor, start, end), aliases["intel_families"],
                search_terms, destination,
            )
    machine_families = set(machine["processor_family_keys"])
    for alias in aliases["processor_aliases"]:
        if machine_families & set(alias["processor_family_keys"]):
            _add_processor_alias(
                alias["term"], alias["display_term"], search_terms, destination
            )


def _append_processor_heading_terms(
        raw_heading, intel_families, search_terms, destination):
    heading = raw_heading
    if heading.startswith("Dual "):
        heading = heading[5:]
    elif heading.startswith("Optional "):
        heading = heading[9:]
    if heading.startswith("Intel "):
        _append_intel_terms(
            heading[6:], intel_families, search_terms, destination
        )
    elif heading.startswith("PowerPC "):
        _append_powerpc_terms(heading[8:], search_terms, destination)
    elif heading.startswith("Motorola "):
        _append_motorola_terms(heading[9:], search_terms, destination)
    elif heading.startswith("Apple "):
        _append_apple_terms(heading[6:], search_terms, destination)


def _append_intel_terms(description, intel_families, search_terms, destination):
    _append_quoted_terms(description, search_terms, destination)

    family = next((candidate for candidate in intel_families
                   if description == candidate
                   or description.startswith(candidate + " ")
                   or description.startswith(candidate + " (")), None)
    if family is None:
        _add_processor_fact(_first_word(description), search_terms, destination)
        return

    if family == "Pentium 4":
        _add_processor_fact("Pentium", search_terms, destination)
    _add_processor_fact(family, search_terms, destination)
    if family.startswith("Core i"):
        _add_processor_alias(family[5:], family, search_terms, destination)
    elif family == "Core 2 Duo":
        _add_processor_alias("C2D", family, search_terms, destination)
    elif family == "Core 2 Extreme":
        _add_processor_alias("C2E", family, search_terms, destination)

    remainder = description[len(family):].strip()
    if remainder.startswith("("):
        close = remainder.find(")")
        if close > 1:
            _add_processor_fact(remainder[1:close], search_terms, destination)
    elif remainder:
        quoted_codename = remainder.find(' ("')
        _add_processor_fact(
            remainder if quoted_codename < 0 else remainder[:quoted_codename],
            search_terms, destination,
        )


def _append_quoted_terms(value, search_terms, destination):
    start = value.find('"')
    while start >= 0:
        end = value.find('"', start + 1)
        if end < 0:
            return
        _add_processor_fact(value[start + 1:end], search_terms, destination)
        start = value.find('"', end + 1)


def _append_powerpc_terms(description, search_terms, destination):
    qualifier = description.find(" (")
    _add_processor_fact(
        description if qualifier < 0 else description[:qualifier],
        search_terms, destination,
    )
    if qualifier >= 0 and qualifier + 2 < len(description):
        close = description.find(")", qualifier + 2)
        if close > qualifier + 2:
            generation = _first_word(description[qualifier + 2:close])
            if generation in ("G3", "G4", "G5"):
                _add_processor_fact(generation, search_terms, destination)


def _append_motorola_terms(description, search_terms, destination):
    _add_processor_fact(_first_word(description), search_terms, destination)


def _append_apple_terms(description, search_terms, destination):
    generation = _first_word(description)
    if generation in ("T1", "T2", "A12Z"):
        _add_processor_fact(generation, search_terms, destination)
        return
    if generation == "A18" and description.startswith("A18 Pro"):
        family = "A18 Pro"
        _add_processor_fact(family, search_terms, destination)
        _add_processor_alias("A18Pro", family, search_terms, destination)
        return
    if len(generation) != 2 or generation[0] != "M" \
            or generation[1] < "1" or generation[1] > "5":
        return
    remainder = description[len(generation):].strip()
    modifier = _first_word(remainder)
    if modifier not in ("Pro", "Max", "Ultra"):
        _add_processor_fact(generation, search_terms, destination)
        return
    family = f"{generation} {modifier}"
    _add_processor_fact(family, search_terms, destination)
    _add_processor_alias(generation + modifier, family, search_terms, destination)
    _add_processor_alias(
        generation + modifier[0], family, search_terms, destination
    )


def _first_word(value):
    trimmed = value.strip()
    end = trimmed.find(" ")
    return trimmed if end < 0 else trimmed[:end]


def _add_processor_fact(raw_term, search_terms, destination):
    term = raw_term.strip()
    normalized = normalize_search_text(term)
    if term and normalized not in search_terms:
        search_terms.add(normalized)
        destination.append(_search_value(term, FIELD_PROCESSOR))


def _add_processor_alias(term, display_term, search_terms, destination):
    normalized = normalize_search_text(term)
    if normalized not in search_terms:
        search_terms.add(normalized)
        destination.append(_search_value(
            term, FIELD_PROCESSOR, exact=True, mapping=MAPPING_FIXED,
            display_value=display_term,
            fixed_range=(0, _utf16_length(display_term)),
        ))
