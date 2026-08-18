#!/usr/bin/env python3

"""Compile human-owned machine TOML files into deterministic catalog textproto."""

import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from catalog_taxonomy import load_catalog_taxonomy
from catalog_source import (
    load_picture_assets,
    load_validated_machines,
    normalize_search_text,
    validate_legacy_machine_names,
)
from generate_resource_registry import load_resource_manifest


SUPPORT_ENUMS = {
    "Supported": "CATALOG_SUPPORT_STATUS_SUPPORTED",
    "Vintage": "CATALOG_SUPPORT_STATUS_VINTAGE",
    "Obsolete": "CATALOG_SUPPORT_STATUS_OBSOLETE",
    "N/A": "CATALOG_SUPPORT_STATUS_NOT_APPLICABLE",
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
    definitions = []
    for grouping_key, definition in taxonomy["browse_definitions"].items():
        definitions.append({
            "key": grouping_key,
            "groups": tuple({
                "key": group["key"],
                "label": group["label"],
                "section_key": group["section_key"],
            } for group in definition["groups"]),
        })
    years = sorted({
        introduction["year"]
        for machine in machines
        for introduction in machine["introductions"]
    })
    definitions.append({
        "key": "years",
        "groups": tuple({
            "key": str(year), "label": str(year), "section_key": None,
        } for year in years),
    })
    return tuple(definitions)


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


def write_machine(writer, indent, machine):
    writer.string(indent, "uid", machine["uid"])
    writer.string(indent, "manufacturer_key", machine["manufacturer_key"])
    writer.string(indent, "product_type_key", machine["product_type_key"])
    writer.string(indent, "picture_asset_key", machine["picture_asset_key"])
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
    if machine["sound_profile"] is not None:
        writer.scalar(
            indent, "sound_profile",
            "CATALOG_SOUND_PROFILE_" + machine["sound_profile"],
        )
    for link in machine["links"]:
        writer.message(
            indent, "links",
            lambda child_indent, value=link: write_link(writer, child_indent, value),
        )


def write_browse_group(writer, indent, group):
    writer.string(indent, "key", group["key"])
    writer.string(indent, "label", group["label"])
    if group["section_key"]:
        writer.string(indent, "section_key", group["section_key"])


def write_browse_definition(writer, indent, definition):
    writer.string(indent, "key", definition["key"])
    for group in definition["groups"]:
        writer.message(
            indent, "groups",
            lambda child_indent, value=group: write_browse_group(
                writer, child_indent, value
            ),
        )


def compile_catalog_textproto(machines_root, assets_root,
                              resource_manifest_path, taxonomy_path,
                              legacy_names_path):
    resource_manifest = load_resource_manifest(resource_manifest_path)
    taxonomy = load_catalog_taxonomy(taxonomy_path)
    picture_assets = load_picture_assets(assets_root)
    machines = load_validated_machines(
        machines_root, resource_manifest, taxonomy, picture_assets
    )
    validate_legacy_machine_names(
        machines, legacy_names_path, normalize_search_text
    )

    definitions = browse_definitions(taxonomy, machines)
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
    parser.add_argument("--resource-manifest", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--legacy-names", type=Path, required=True)
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
        machines_root, assets_root, arguments.resource_manifest.resolve(),
        arguments.taxonomy.resolve(), arguments.legacy_names.resolve(),
    )
    action = "Generated" if write_if_changed(output_path, payload) else "Verified"
    digest = hashlib.sha256(payload).hexdigest()
    print(
        f"{action} catalog textproto with {machine_count} machines, "
        f"{len(payload)} bytes, SHA-256 {digest}."
    )


if __name__ == "__main__":
    main()
