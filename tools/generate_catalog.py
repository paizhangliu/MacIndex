#!/usr/bin/env python3

"""Compile human-owned machine TOML files into deterministic catalog textproto."""

import argparse
import json
import tempfile
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError as error:
    raise SystemExit("MacIndex catalog tooling requires Python 3.11 or newer.") from error

from catalog_taxonomy import load_catalog_taxonomy
from catalog_source import (
    MACHINE_UID,
    load_picture_assets,
    load_validated_machines,
    validate_legacy_machine_names,
)
from catalog_resources import load_resource_manifest, validate_catalog_resources
from catalog_search import (
    compile_derived_search_values,
    compile_search_lexicon,
    load_search_aliases,
)


SUPPORT_ENUMS = {
    "Supported": "CATALOG_SUPPORT_STATUS_SUPPORTED",
    "Vintage": "CATALOG_SUPPORT_STATUS_VINTAGE",
    "Obsolete": "CATALOG_SUPPORT_STATUS_OBSOLETE",
    "N/A": "CATALOG_SUPPORT_STATUS_NOT_APPLICABLE",
}

LOGO_NIGHT_ENUMS = {
    "DARKEN": "CATALOG_LOGO_NIGHT_TREATMENT_DARKEN",
    "WHITE_TINT": "CATALOG_LOGO_NIGHT_TREATMENT_WHITE_TINT",
    "MONOCHROME": "CATALOG_LOGO_NIGHT_TREATMENT_MONOCHROME",
}

def fail(message):
    raise RuntimeError(message)


def text_string(value):
    """Return one deterministic protobuf text-format UTF-8 string literal."""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


class TextProtoWriter:
    """Small schema-shaped writer; it knows field names, never wire numbers."""

    def __init__(self):
        self.lines = []

    def scalar(self, indent, field_name, value):
        self.lines.append(f"{'  ' * indent}{field_name}: {value}")

    def string(self, indent, field_name, value):
        self.scalar(indent, field_name, text_string(value))

    def message(self, indent, field_name, body):
        self.lines.append(f"{'  ' * indent}{field_name} {{")
        body(indent + 1)
        self.lines.append(f"{'  ' * indent}}}")

    def finish(self):
        return ("\n".join(self.lines) + "\n").encode("utf-8")


def browse_definitions(taxonomy, machines):
    ordered_machines = sorted(machines, key=_earliest_introduction)
    definitions = []
    for grouping_key, definition in taxonomy["browse_definitions"].items():
        definitions.append({
            "key": grouping_key,
            "groups": tuple({
                "key": group["key"],
                "label": group["label"],
                "section_key": group["section_key"],
                "machine_uids": tuple(
                    machine["uid"] for machine in ordered_machines
                    if _matches_browse_group(machine, grouping_key, group["key"])
                ),
            } for group in definition["groups"]),
        })
    years = sorted({
        introduction["year"]
        for machine in machines
        for introduction in machine["introductions"]
    })
    year_groups = []
    for year in years:
        year_machines = [
            machine for machine in ordered_machines
            if _matches_browse_group(machine, "years", str(year))
        ]
        year_machines.sort(key=lambda machine: _earliest_introduction_month(machine, year))
        year_groups.append({
            "key": str(year), "label": str(year), "section_key": None,
            "machine_uids": tuple(machine["uid"] for machine in year_machines),
        })
    definitions.append({
        "key": "years",
        "groups": tuple(year_groups),
    })
    return tuple(definitions)


def _earliest_introduction(machine):
    return min(
        (introduction["year"], introduction["month"])
        for introduction in machine["introductions"]
    )


def _earliest_introduction_month(machine, year):
    return min(
        introduction["month"]
        for introduction in machine["introductions"]
        if introduction["year"] == year
    )


def _matches_browse_group(machine, grouping, key):
    if grouping == "names":
        return machine["product_type_key"] == key
    if grouping == "processors":
        return key in machine["processor_family_keys"]
    if grouping == "years":
        return any(str(value["year"]) == key for value in machine["introductions"])
    fail(f'Unknown browse grouping "{grouping}"')


def write_strings(writer, indent, field_name, values):
    for value in values:
        writer.string(indent, field_name, value)


def write_introduction(writer, indent, introduction):
    writer.scalar(indent, "year", introduction["year"])
    writer.scalar(indent, "month", introduction["month"])
    if introduction["qualifier"] is not None:
        writer.string(indent, "qualifier", introduction["qualifier"])


def write_range(writer, indent, start, end):
    writer.scalar(indent, "start_inclusive", start)
    writer.scalar(indent, "end_exclusive", end)


def write_link(writer, indent, link):
    writer.string(indent, "label", link["label"])
    writer.string(indent, "url", link["url"])


def write_identity_value(writer, indent, entry):
    writer.string(indent, "value", entry["value"])
    if entry["qualifier"] is not None:
        writer.string(indent, "qualifier", entry["qualifier"])
    write_strings(writer, indent, "revisions", entry["revisions"])


def write_identity_values(writer, indent, field_name, entries):
    for entry in entries:
        writer.message(
            indent, field_name,
            lambda child_indent, value=entry: write_identity_value(
                writer, child_indent, value
            ),
        )


def write_search_value(writer, indent, value):
    writer.string(indent, "value", value["value"])
    writer.scalar(indent, "field", value["field"])
    if value["exact_token_only"]:
        writer.scalar(indent, "exact_token_only", "true")
    writer.scalar(indent, "display_mapping", value["display_mapping"])
    if value["display_value"] is not None:
        writer.string(indent, "display_value", value["display_value"])
    if value["fixed_display_range"] is not None:
        start, end = value["fixed_display_range"]
        writer.message(
            indent, "fixed_display_range",
            lambda child_indent: write_range(writer, child_indent, start, end),
        )
    if value["canonical_name"]:
        writer.scalar(indent, "canonical_name", "true")


def write_machine(writer, indent, machine):
    writer.string(indent, "uid", machine["uid"])
    writer.string(indent, "manufacturer_key", machine["manufacturer_key"])
    writer.string(indent, "product_type_key", machine["product_type_key"])
    writer.string(indent, "picture_asset_key", machine["picture_asset_key"])
    writer.string(indent, "type_logo_key", machine["type_logo_key"])
    write_identity_values(writer, indent, "names", machine["names"])
    for introduction in machine["introductions"]:
        writer.message(
            indent, "introductions",
            lambda child_indent, value=introduction: write_introduction(
                writer, child_indent, value
            ),
        )

    for field_name in (
            "model_numbers", "identifiers", "gestalt_ids", "order_numbers",
            "codenames", "emc_numbers"):
        write_identity_values(writer, indent, field_name, machine[field_name])

    if machine["processor"] is not None:
        writer.string(indent, "processor", machine["processor"])
    write_strings(
        writer, indent, "processor_family_keys", machine["processor_family_keys"]
    )
    write_strings(
        writer, indent, "processor_logo_keys", machine["processor_logo_keys"]
    )
    for start, end in machine["processor_model_ranges"]:
        writer.message(
            indent, "processor_model_ranges",
            lambda child_indent, range_start=start, range_end=end: write_range(
                writer, child_indent, range_start, range_end
            ),
        )

    if machine["graphics"] is not None:
        writer.string(indent, "graphics", machine["graphics"])
    write_strings(
        writer, indent, "graphics_logo_keys", machine["graphics_logo_keys"]
    )
    for start, end in machine["graphics_model_ranges"]:
        writer.message(
            indent, "graphics_model_ranges",
            lambda child_indent, range_start=start, range_end=end: write_range(
                writer, child_indent, range_start, range_end
            ),
        )

    for field_name in (
            "display", "ram", "rom", "software", "storage", "features",
            "expansion"):
        if machine[field_name] is not None:
            writer.string(indent, field_name, machine[field_name])
    writer.string(indent, "design", machine["design"])
    writer.scalar(indent, "support_status", SUPPORT_ENUMS[machine["support_status"]])
    if machine["startup_sound_key"] is not None:
        writer.string(indent, "startup_sound_key", machine["startup_sound_key"])
    if machine["death_sound_key"] is not None:
        writer.string(indent, "death_sound_key", machine["death_sound_key"])
    for link in machine["links"]:
        writer.message(
            indent, "links",
            lambda child_indent, value=link: write_link(writer, child_indent, value),
        )
    for value in machine["derived_search_values"]:
        writer.message(
            indent, "derived_search_values",
            lambda child_indent, item=value: write_search_value(
                writer, child_indent, item
            ),
        )


def write_browse_group(writer, indent, group):
    writer.string(indent, "key", group["key"])
    writer.string(indent, "label", group["label"])
    if group["section_key"]:
        writer.string(indent, "section_key", group["section_key"])
    write_strings(writer, indent, "machine_uids", group["machine_uids"])


def write_browse_definition(writer, indent, definition):
    writer.string(indent, "key", definition["key"])
    for group in definition["groups"]:
        writer.message(
            indent, "groups",
            lambda child_indent, value=group: write_browse_group(
                writer, child_indent, value
            ),
        )


def load_retired_machines(path, active_uids):
    try:
        document = tomllib.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"Unable to read retired machine table: {error}")
    if not isinstance(document, dict) or set(document) != {"retired_machines"}:
        fail("Illegal retired machine document")

    retired = document["retired_machines"]
    if not isinstance(retired, list):
        fail("Illegal retired machine table")
    retired_uids = set()
    normalized_retired = []
    for index, entry in enumerate(retired):
        if not isinstance(entry, dict) or set(entry) not in (
                {"uid", "previous_name"},
                {"uid", "previous_name", "replacement_uid"}):
            fail(f"Illegal retired machine {index}")
        uid = entry["uid"]
        previous_name = entry["previous_name"]
        replacement_uid = entry.get("replacement_uid")
        if not isinstance(uid, str) or MACHINE_UID.fullmatch(uid) is None \
                or uid in active_uids or uid in retired_uids:
            fail(f'Illegal retired machine UID "{uid}"')
        if not isinstance(previous_name, str) or not previous_name \
                or previous_name != previous_name.strip():
            fail(f"Illegal retired machine name for {uid}")
        if replacement_uid is not None and (
                not isinstance(replacement_uid, str)
                or replacement_uid not in active_uids):
            fail(f"Illegal replacement UID for {uid}")
        retired_uids.add(uid)
        normalized_retired.append({
            "uid": uid,
            "previous_name": previous_name,
            "replacement_uid": replacement_uid,
        })
    return tuple(normalized_retired)


def resolve_machine_resources(machine, resource_manifest):
    resolved = dict(machine)
    resolved["type_logo_key"] = resource_manifest["manufacturer_type_logos"][
        machine["manufacturer_key"]
    ]
    resolved["processor_logo_keys"] = tuple(
        resource
        for semantic_key in machine["processor_logo_keys"]
        for resource in resource_manifest["processor_logos"][semantic_key]
    )
    resolved["graphics_logo_keys"] = tuple(
        resource
        for semantic_key in machine["graphics_logo_keys"]
        for resource in resource_manifest["graphics_logos"][semantic_key]
    )
    sound_profile = machine["sound_profile"]
    sounds = resource_manifest["sound_profiles"].get(sound_profile, {
        "startup": None, "death": None,
    })
    resolved["startup_sound_key"] = sounds["startup"]
    resolved["death_sound_key"] = sounds["death"]
    return resolved


def write_retired_machine(writer, indent, retired):
    writer.string(indent, "uid", retired["uid"])
    writer.string(indent, "previous_name", retired["previous_name"])
    if retired["replacement_uid"] is not None:
        writer.string(indent, "replacement_uid", retired["replacement_uid"])


def write_logo_asset(writer, indent, key, treatment):
    writer.string(indent, "key", key)
    writer.scalar(indent, "night_treatment", LOGO_NIGHT_ENUMS[treatment])


def write_search_lexicon(writer, indent, lexicon):
    for field_name in (
            "phrase_candidates", "atomic_phrases",
            "compact_name_aliases", "part_number_stems"):
        write_strings(writer, indent, field_name, lexicon[field_name])


def compile_catalog_textproto(machines_root, assets_root, retired_machines_path,
                              resource_manifest_path, taxonomy_path,
                              legacy_names_path, search_aliases_path):
    resource_manifest = load_resource_manifest(resource_manifest_path)
    validate_catalog_resources(resource_manifest, assets_root)
    taxonomy = load_catalog_taxonomy(taxonomy_path)
    picture_assets = load_picture_assets(assets_root)
    source_machines = load_validated_machines(
        machines_root, resource_manifest, taxonomy, picture_assets
    )
    validate_legacy_machine_names(source_machines, legacy_names_path)
    search_aliases = load_search_aliases(search_aliases_path, source_machines)
    retired_machines = load_retired_machines(
        retired_machines_path, {machine["uid"] for machine in source_machines}
    )
    machines = []
    for machine in source_machines:
        resolved = resolve_machine_resources(machine, resource_manifest)
        resolved["derived_search_values"] = compile_derived_search_values(
            machine, search_aliases
        )
        machines.append(resolved)
    machines = tuple(machines)

    definitions = browse_definitions(taxonomy, machines)
    search_lexicon = compile_search_lexicon(machines)
    writer = TextProtoWriter()
    for machine in machines:
        writer.message(
            0, "machines",
            lambda indent, value=machine: write_machine(writer, indent, value),
        )
    for definition in definitions:
        writer.message(
            0, "browse_definitions",
            lambda indent, value=definition: write_browse_definition(
                writer, indent, value
            ),
        )
    for retired in retired_machines:
        writer.message(
            0, "retired_machines",
            lambda indent, value=retired: write_retired_machine(
                writer, indent, value
            ),
        )
    referenced_logos = {
        machine["type_logo_key"] for machine in machines
    } | {
        key
        for machine in machines
        for key in machine["processor_logo_keys"] + machine["graphics_logo_keys"]
    }
    for key in sorted(referenced_logos):
        treatment = resource_manifest["logo_night_treatment_overrides"].get(
            key, resource_manifest["default_logo_night_treatment"]
        )
        writer.message(
            0, "logo_assets",
            lambda indent, asset_key=key, value=treatment: write_logo_asset(
                writer, indent, asset_key, value
            ),
        )
    writer.message(
        0, "search_lexicon",
        lambda indent: write_search_lexicon(writer, indent, search_lexicon),
    )
    return writer.finish(), len(machines)


def write_if_changed(output_path, payload):
    if output_path.is_file() and output_path.read_bytes() == payload:
        return False
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
                prefix=f".{output_path.name}.", suffix=".tmp",
                dir=output_path.parent, delete=False) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(payload)
        temporary_path.replace(output_path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--machines", type=Path, required=True)
    parser.add_argument("--assets-root", type=Path, required=True)
    parser.add_argument("--retired-machines", type=Path, required=True)
    parser.add_argument("--resource-manifest", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--legacy-names", type=Path, required=True)
    parser.add_argument("--search-aliases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    machines_root = arguments.machines.resolve()
    assets_root = arguments.assets_root.resolve()
    output_path = arguments.output.resolve()
    if not machines_root.is_dir():
        fail(f"Machine source directory does not exist: {machines_root}")
    if not assets_root.is_dir():
        fail(f"Catalog assets root does not exist: {assets_root}")

    payload, machine_count = compile_catalog_textproto(
        machines_root, assets_root, arguments.retired_machines.resolve(),
        arguments.resource_manifest.resolve(),
        arguments.taxonomy.resolve(), arguments.legacy_names.resolve(),
        arguments.search_aliases.resolve(),
    )
    action = "Generated" if write_if_changed(output_path, payload) else "Verified"
    print(
        f"{action} catalog textproto with {machine_count} machines, "
        f"{len(payload)} bytes."
    )


if __name__ == "__main__":
    main()
