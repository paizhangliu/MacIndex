#!/usr/bin/env python3

"""Load and validate the human-owned TOML machine catalog."""

import argparse
import json
import re
import unicodedata
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError as error:
    raise SystemExit("MacIndex catalog tooling requires Python 3.11 or newer.") from error

from catalog_taxonomy import load_catalog_taxonomy
from generate_resource_registry import load_resource_manifest, validate_webp_file


MACHINE_UID = re.compile(r"MI\d{6}")
SOUND_PROFILE_KEY = re.compile(r"[A-Z][A-Z0-9_]*")
SUPPORT_VALUES = ("Supported", "Vintage", "Obsolete", "N/A")
UID_SEQUENCE_PATH = Path(__file__).resolve().parents[1] / "catalog/machine_uid_sequence"

IDENTITY_FIELDS = {
    "model_numbers": re.compile(r"(?:[AM]\d{4}|M0001[A-Z]{1,2})"),
    "identifiers": re.compile(r"[A-Za-z][A-Za-z0-9]*\d*,\d+"),
    "gestalt_ids": re.compile(r"\d+"),
    "order_numbers": re.compile(r"[A-Z0-9]{4,5}"),
    "emc_numbers": re.compile(r"\d{3,4}(?:C|-1)?"),
}

OPTIONAL_TEXT_FIELDS = (
    "display", "ram", "rom", "software", "storage", "features", "expansion",
)

REQUIRED_SOURCE_KEYS = {
    "category", "picture_asset_key", "name", "introductions", "design",
    "support_status",
}

OPTIONAL_SOURCE_KEYS = {
    "aliases", "codenames", *IDENTITY_FIELDS, "processor",
    "processor_family_keys",
    "processor_logo_keys", "graphics", "graphics_logo_keys",
    *OPTIONAL_TEXT_FIELDS, "sound_profile", "links",
}


def fail(message):
    raise RuntimeError(message)


def normalize_search_text(value):
    """Match Java NFKC + String.trim() + Locale.ROOT lowercase semantics."""
    normalized = unicodedata.normalize("NFKC", value)
    start = 0
    end = len(normalized)
    while start < end and ord(normalized[start]) <= 0x20:
        start += 1
    while end > start and ord(normalized[end - 1]) <= 0x20:
        end -= 1
    return normalized[start:end].lower()


def validate_search_offsets(value, label):
    """Keep normalized UTF-16 offsets usable for highlighting authored text."""
    boundary = 0
    while boundary < len(value):
        boundary += 1
        prefix = value[:boundary]
        normalized = unicodedata.normalize("NFKC", prefix).lower()
        if len(prefix.encode("utf-16-le")) != len(normalized.encode("utf-16-le")):
            fail(f"Search normalization changes text offsets in {label}")


def validate_text(value, label, *, single_line=False):
    if not isinstance(value, str) or not value or value != value.strip():
        fail(f"Missing or unnormalized {label}")
    if "\r" in value or "\t" in value or "\n\n" in value:
        fail(f"Illegal whitespace in {label}")
    if single_line and "\n" in value:
        fail(f"Unexpected line break in {label}")
    return value


def read_string_array(document, key, machine_label, *, required=False):
    if key not in document:
        if required:
            fail(f"Missing {key} for {machine_label}")
        return ()
    values = document[key]
    if not isinstance(values, list) or not values:
        fail(f"Illegal {key} for {machine_label}")
    normalized = tuple(
        validate_text(value, f"{key} for {machine_label}", single_line=True)
        for value in values
    )
    if len(normalized) != len(set(normalized)):
        fail(f"Duplicate {key} for {machine_label}")
    return normalized


def read_introductions(document, machine_label):
    raw_introductions = document["introductions"]
    if not isinstance(raw_introductions, list) or not raw_introductions:
        fail(f"Missing introductions for {machine_label}")
    introductions = []
    dates = []
    for index, raw in enumerate(raw_introductions):
        if not isinstance(raw, dict) or set(raw) not in (
                {"year", "month"}, {"year", "month", "qualifier"}):
            fail(f"Illegal introduction {index} for {machine_label}")
        year = raw["year"]
        month = raw["month"]
        if type(year) is not int or year < 1900 or year > 9999 \
                or type(month) is not int or month < 1 or month > 12:
            fail(f"Illegal introduction date for {machine_label}")
        qualifier = raw.get("qualifier")
        if qualifier is not None:
            qualifier = validate_text(
                qualifier, f"introduction qualifier for {machine_label}",
                single_line=True,
            )
        introductions.append({
            "year": year,
            "month": month,
            "qualifier": qualifier,
        })
        dates.append((year, month))
    if len(dates) != len(set(dates)):
        fail(f"Duplicate introductions for {machine_label}")
    return tuple(introductions)


def read_identity_entries(document, field_name, machine_label):
    if field_name not in document:
        return ()
    raw_entries = document[field_name]
    if not isinstance(raw_entries, list) or not raw_entries:
        fail(f"Illegal {field_name} for {machine_label}")
    entries = []
    display_values = []
    search_values = []
    for index, raw in enumerate(raw_entries):
        allowed_keys = {"value", "qualifier"}
        if field_name == "order_numbers":
            allowed_keys.add("revisions")
        if not isinstance(raw, dict) or "value" not in raw \
                or not set(raw).issubset(allowed_keys):
            fail(f"Illegal {field_name} entry {index} for {machine_label}")
        value = validate_text(
            raw["value"], f"{field_name} value for {machine_label}",
            single_line=True,
        )
        qualifier = raw.get("qualifier")
        if qualifier is not None:
            qualifier = validate_text(
                qualifier, f"{field_name} qualifier for {machine_label}",
                single_line=True,
            )
        revisions = raw.get("revisions", ())
        if field_name == "order_numbers":
            if not isinstance(revisions, list) or not revisions:
                fail(f"Missing order number revisions for {machine_label}")
            if any(not isinstance(revision, str)
                   or re.fullmatch(r"[A-Z]", revision) is None
                   for revision in revisions):
                fail(f"Illegal order number revision for {machine_label}")
            if revisions != sorted(set(revisions)):
                fail(f"Duplicate or unordered order number revisions for {machine_label}")
        elif revisions:
            fail(f"Unexpected revisions on {field_name} for {machine_label}")
        display = (
            value if qualifier is None
            else f"{value} ({qualifier})"
        )
        pattern = IDENTITY_FIELDS.get(field_name)
        if pattern is not None and pattern.fullmatch(value) is None:
            fail(f'Illegal {field_name} value "{value}" for {machine_label}')
        validate_search_offsets(value, f"{field_name} for {machine_label}")
        entries.append({
            "value": value,
            "qualifier": qualifier,
            "revisions": tuple(revisions),
        })
        display_values.append(display)
        search_values.append(value)
    if len(display_values) != len(set(display_values)):
        fail(f"Duplicate displayed {field_name} for {machine_label}")
    normalized_search_values = [
        normalize_search_text(value) for value in search_values
    ]
    if len(normalized_search_values) != len(set(normalized_search_values)):
        fail(f"Duplicate searchable {field_name} for {machine_label}")
    return tuple(entries)


def parse_rich_text(marked_text, label):
    """Strip **model** marks and return Java UTF-16 spans over the plain text."""
    validate_text(marked_text, label)
    plain = []
    ranges = []
    open_start = None
    utf16_offset = 0
    index = 0
    while index < len(marked_text):
        if marked_text.startswith("**", index):
            if open_start is None:
                open_start = utf16_offset
            else:
                if open_start == utf16_offset:
                    fail(f"Empty rich-text mark in {label}")
                ranges.append((open_start, utf16_offset))
                open_start = None
            index += 2
            continue
        character = marked_text[index]
        if character == "\n" and open_start is not None:
            fail(f"Rich-text mark crosses a line in {label}")
        plain.append(character)
        utf16_offset += 2 if ord(character) > 0xFFFF else 1
        index += 1
    if open_start is not None:
        fail(f"Unclosed rich-text mark in {label}")
    plain_text = "".join(plain)
    validate_text(plain_text, label)
    return plain_text, tuple(ranges)


def read_links(document, machine_label):
    if "links" not in document:
        return ()
    raw_links = document["links"]
    if not isinstance(raw_links, list) or not raw_links:
        fail(f"Illegal links for {machine_label}")
    links = []
    identities = set()
    for index, raw in enumerate(raw_links):
        if not isinstance(raw, dict) or set(raw) != {"label", "url"}:
            fail(f"Illegal link {index} for {machine_label}")
        link_label = validate_text(
            raw["label"], f"link label for {machine_label}", single_line=True
        )
        url = validate_text(
            raw["url"], f"link URL for {machine_label}", single_line=True
        )
        if not url.startswith("https://"):
            fail(f'Illegal link URL "{url}" for {machine_label}')
        identity = (link_label, url)
        if identity in identities:
            fail(f"Duplicate link for {machine_label}")
        identities.add(identity)
        links.append({"label": link_label, "url": url})
    return tuple(links)


def load_machine_document(path, taxonomy):
    uid = path.stem
    machine_label = uid
    try:
        document = tomllib.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"Unable to read {path.name}: {error}")
    if not isinstance(document, dict) \
            or not REQUIRED_SOURCE_KEYS.issubset(document) \
            or not set(document).issubset(REQUIRED_SOURCE_KEYS | OPTIONAL_SOURCE_KEYS):
        fail(f"Illegal machine document fields for {machine_label}")

    category = validate_text(
        document["category"], f"category for {machine_label}", single_line=True
    )
    if category not in taxonomy["category_manufacturers"]:
        fail(f'Unknown category "{category}" for {machine_label}')
    name = validate_text(
        document["name"], f"name for {machine_label}", single_line=True
    )
    aliases = read_string_array(document, "aliases", machine_label)
    names = tuple(
        {"value": value, "qualifier": None, "revisions": ()}
        for value in (name, *aliases)
    )
    for entry in names:
        validate_search_offsets(entry["value"], f"name for {machine_label}")
    normalized_names = [
        normalize_search_text(entry["value"]) for entry in names
    ]
    if len(normalized_names) != len(set(normalized_names)):
        fail(f"Duplicate display name or alias for {machine_label}")
    codenames = read_identity_entries(document, "codenames", machine_label)

    machine = {
        "uid": uid,
        "category": category,
        "manufacturer_key": taxonomy["category_manufacturers"][category],
        "product_type_key": taxonomy["category_product_types"][category],
        "picture_asset_key": validate_text(
            document["picture_asset_key"],
            f"picture asset key for {machine_label}", single_line=True,
        ),
        "names": names,
        "codenames": codenames,
        "introductions": read_introductions(document, machine_label),
        "design": validate_text(document["design"], f"design for {machine_label}"),
        "support_status": document["support_status"],
        "links": read_links(document, machine_label),
    }

    for field_name in IDENTITY_FIELDS:
        machine[field_name] = read_identity_entries(
            document, field_name, machine_label
        )

    for field_name in ("processor", "graphics"):
        if field_name in document:
            marked = document[field_name]
            plain, ranges = parse_rich_text(
                marked, f"{field_name} for {machine_label}"
            )
        else:
            plain, ranges = None, ()
        machine[field_name] = plain
        machine[f"{field_name}_model_ranges"] = ranges

    machine["processor_family_keys"] = read_string_array(
        document, "processor_family_keys", machine_label
    )
    machine["processor_logo_keys"] = read_string_array(
        document, "processor_logo_keys", machine_label
    )
    machine["graphics_logo_keys"] = read_string_array(
        document, "graphics_logo_keys", machine_label
    )
    for field_name in OPTIONAL_TEXT_FIELDS:
        machine[field_name] = (
            validate_text(document[field_name], f"{field_name} for {machine_label}")
            if field_name in document else None
        )

    support_status = machine["support_status"]
    if not isinstance(support_status, str) or support_status not in SUPPORT_VALUES:
        fail(f'Unknown support status "{support_status}" for {machine_label}')
    sound_profile = document.get("sound_profile")
    if sound_profile is not None:
        sound_profile = validate_text(
            sound_profile, f"sound profile for {machine_label}", single_line=True
        )
        if SOUND_PROFILE_KEY.fullmatch(sound_profile) is None:
            fail(f'Illegal sound profile "{sound_profile}" for {machine_label}')
    machine["sound_profile"] = sound_profile
    return machine


def load_uid_sequence():
    try:
        sequence = int(UID_SEQUENCE_PATH.read_text(encoding="utf-8").strip())
    except (OSError, ValueError) as error:
        fail(f"Unable to read machine UID sequence: {error}")
    if sequence < 0 or sequence > 999999:
        fail(f"Illegal machine UID sequence: {sequence}")
    return sequence


def allocate_machine_uid():
    sequence = load_uid_sequence() + 1
    if sequence > 999999:
        fail("Machine UID sequence is exhausted")
    UID_SEQUENCE_PATH.write_text(f"{sequence}\n", encoding="utf-8")
    print(f"MI{sequence:06d}")


def load_validated_machines(machines_root, resource_manifest, taxonomy,
                            picture_assets):
    if not machines_root.is_dir():
        fail(f"Machine source directory does not exist: {machines_root}")
    unexpected = sorted(
        entry.name for entry in machines_root.iterdir()
        if not entry.is_file() or entry.suffix != ".toml"
        or MACHINE_UID.fullmatch(entry.stem) is None
    )
    if unexpected:
        fail("Unexpected machine source entries: " + ", ".join(unexpected))
    paths = sorted(machines_root.glob("*.toml"), key=lambda path: path.name)
    if not paths:
        fail("Machine catalog source is empty")

    uid_sequence = load_uid_sequence()
    machines = [load_machine_document(path, taxonomy) for path in paths]
    machine_uids = {machine["uid"] for machine in machines}
    if len(machine_uids) != len(machines):
        fail("Duplicate machine UID")
    if any(int(uid[2:]) > uid_sequence for uid in machine_uids):
        fail("Machine UID exceeds the current sequence")

    categories = {machine["category"] for machine in machines}
    expected_categories = set(taxonomy["categories"])
    if categories != expected_categories:
        fail(
            "Machine categories differ from taxonomy: "
            f"missing={sorted(expected_categories - categories)}, "
            f"unexpected={sorted(categories - expected_categories)}"
        )

    manufacturer_keys = set(resource_manifest["manufacturer_type_logos"])
    expected_manufacturer_keys = set(taxonomy["category_manufacturers"].values())
    if manufacturer_keys != expected_manufacturer_keys:
        fail(
            "Manufacturer type resource keys differ from taxonomy: "
            f"missing={sorted(expected_manufacturer_keys - manufacturer_keys)}, "
            f"unexpected={sorted(manufacturer_keys - expected_manufacturer_keys)}"
        )

    displayed_names = set()
    searchable_names = {}
    used_processor_logos = set()
    used_graphics_logos = set()
    used_sound_profiles = set()
    used_picture_assets = set()
    supported_processor_logos = set(resource_manifest["processor_logos"])
    supported_graphics_logos = set(resource_manifest["graphics_logos"])
    supported_sound_profiles = set(resource_manifest["sound_profiles"])
    processor_groups = {
        group["key"]
        for group in taxonomy["browse_definitions"]["processors"]["groups"]
    }

    for machine in machines:
        machine_label = machine["uid"]
        canonical_name = machine["names"][0]["value"]
        if canonical_name in displayed_names:
            fail(f'Duplicate displayed machine name "{canonical_name}"')
        displayed_names.add(canonical_name)
        for name_entry in machine["names"]:
            search_name = name_entry["value"]
            normalized_name = normalize_search_text(search_name)
            existing_machine = searchable_names.get(normalized_name)
            if existing_machine is not None:
                fail(
                    f'Duplicate searchable name "{search_name}" for '
                    f"{existing_machine} and {machine_label}"
                )
            searchable_names[normalized_name] = machine_label

        unknown_processor_groups = (
            set(machine["processor_family_keys"]) - processor_groups
        )
        if unknown_processor_groups:
            fail(
                f"Unknown processor family keys for {machine_label}: "
                + ", ".join(sorted(unknown_processor_groups))
            )

        for field_name, supported, used in (
            ("processor logo", supported_processor_logos, used_processor_logos),
            ("graphics logo", supported_graphics_logos, used_graphics_logos),
        ):
            values = set(machine[field_name.replace(" ", "_") + "_keys"])
            unknown = values - supported
            if unknown:
                fail(
                    f"Unknown {field_name} keys for {machine_label}: "
                    + ", ".join(sorted(unknown))
                )
            used.update(values)

        sound_profile = machine["sound_profile"]
        if sound_profile is not None:
            if sound_profile not in supported_sound_profiles:
                fail(f'Unknown sound profile "{sound_profile}" for {machine_label}')
            used_sound_profiles.add(sound_profile)

        picture = machine["picture_asset_key"]
        if MACHINE_UID.fullmatch(picture) is None:
            fail(f'Illegal picture asset key "{picture}" for {machine_label}')
        if picture not in picture_assets:
            fail(f'Missing picture asset "{picture}" for {machine_label}')
        used_picture_assets.add(picture)

    for label, configured, used in (
        ("processor logo", supported_processor_logos, used_processor_logos),
        ("graphics logo", supported_graphics_logos, used_graphics_logos),
        ("sound profile", supported_sound_profiles, used_sound_profiles),
    ):
        unused = sorted(configured - used)
        if unused:
            fail(f"Unused {label} keys: " + ", ".join(unused))

    unknown_picture_uids = picture_assets - machine_uids
    if unknown_picture_uids:
        fail(
            "Machine picture assets do not belong to current machine UIDs: "
            + ", ".join(sorted(unknown_picture_uids))
        )
    unused_picture_assets = picture_assets - used_picture_assets
    if unused_picture_assets:
        fail("Unused machine picture assets: " + ", ".join(sorted(unused_picture_assets)))
    return tuple(machines)


def validate_legacy_machine_names(machines, legacy_names_path, normalize_name):
    searchable_names_by_uid = {
        machine["uid"]: {
            entry["value"] for entry in machine["names"]
        } for machine in machines
    }
    try:
        legacy_names = json.loads(legacy_names_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"Unable to read legacy machine names: {error}")
    if not isinstance(legacy_names, dict) \
            or set(legacy_names) != {"schema", "names"} \
            or legacy_names["schema"] != 1 \
            or not isinstance(legacy_names["names"], list) \
            or not legacy_names["names"]:
        fail("Illegal legacy machine names document")
    names = set()
    for entry in legacy_names["names"]:
        if not isinstance(entry, dict) or set(entry) != {"name", "uid"}:
            fail("Illegal legacy machine name entry")
        name = entry["name"]
        uid = entry["uid"]
        normalized_name = normalize_name(name) if isinstance(name, str) else None
        if not name or name != name.strip() or normalized_name in names \
                or uid not in searchable_names_by_uid \
                or name not in searchable_names_by_uid[uid]:
            fail(f'Illegal legacy machine identity "{name}" -> "{uid}"')
        names.add(normalized_name)


def load_picture_assets(assets_root):
    picture_directory = assets_root / "machines"
    if not picture_directory.is_dir():
        fail(f"Machine picture directory does not exist: {picture_directory}")
    unexpected_files = [
        picture.name for picture in picture_directory.iterdir()
        if picture.is_file() and picture.suffix.lower() != ".webp"
    ]
    if unexpected_files:
        fail(f"Unexpected machine picture assets: {', '.join(unexpected_files)}")
    picture_assets = {picture.stem for picture in picture_directory.glob("*.webp")}
    if not picture_assets:
        fail("Machine picture assets are empty")
    for picture in picture_directory.glob("*.webp"):
        validate_webp_file(picture)
    return picture_assets


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allocate-uid", action="store_true")
    parser.add_argument("--machines", type=Path)
    parser.add_argument("--assets-root", type=Path)
    parser.add_argument("--resource-manifest", type=Path)
    parser.add_argument("--taxonomy", type=Path)
    parser.add_argument("--legacy-names", type=Path)
    arguments = parser.parse_args()

    if arguments.allocate_uid:
        if any(value is not None for value in (
                arguments.machines, arguments.assets_root, arguments.resource_manifest,
                arguments.taxonomy, arguments.legacy_names)):
            fail("UID allocation does not accept validation inputs")
        allocate_machine_uid()
        return
    if any(value is None for value in (
            arguments.machines, arguments.assets_root, arguments.resource_manifest,
            arguments.taxonomy, arguments.legacy_names)):
        fail("Machine source, assets, resource manifest, taxonomy, and legacy names are required")

    machines_root = arguments.machines.resolve()
    assets_root = arguments.assets_root.resolve()
    resource_manifest = load_resource_manifest(arguments.resource_manifest.resolve())
    taxonomy = load_catalog_taxonomy(arguments.taxonomy.resolve())
    picture_assets = load_picture_assets(assets_root)
    machines = load_validated_machines(
        machines_root, resource_manifest, taxonomy, picture_assets
    )
    validate_legacy_machine_names(
        machines, arguments.legacy_names.resolve(), normalize_search_text
    )
    print(f"Validated {len(machines)} machines from TOML authoring files.")


if __name__ == "__main__":
    main()
