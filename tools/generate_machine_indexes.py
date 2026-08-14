#!/usr/bin/env python3

import argparse
import json
import re
import sqlite3
from pathlib import Path


CATEGORIES = (
    "compact_mac", "mac_ii", "mac_lc", "mac_quadra", "mac_performa_68k", "mac_centris",
    "mac_server_68k", "powerbook_68k", "powerbook_duo_68k", "power_mac_classic",
    "mac_performa_ppc", "mac_server_ppc_classic", "apple_network_server",
    "powerbook_ppc_classic",
    "powerbook_duo_ppc", "power_mac", "imac_ppc", "emac", "mac_mini_ppc",
    "mac_server_ppc", "xserve_ppc", "powerbook_ppc", "ibook", "mac_pro_intel",
    "imac_intel", "imac_pro_intel", "mac_mini_intel", "xserve_intel",
    "macbook_pro_intel", "macbook_intel", "macbook_air_intel", "mac_pro_arm",
    "imac_arm", "mac_mini_arm", "macbook_pro_arm", "macbook_air_arm", "macbook_arm",
    "mac_studio",
)

MANUFACTURERS = {
    "all": CATEGORIES,
    "apple68k": (
        "compact_mac", "mac_ii", "mac_lc", "mac_quadra", "mac_performa_68k",
        "mac_centris", "mac_server_68k", "powerbook_68k", "powerbook_duo_68k",
    ),
    "appleppc": (
        "power_mac_classic", "mac_performa_ppc", "mac_server_ppc_classic", "apple_network_server",
        "powerbook_ppc_classic", "powerbook_duo_ppc", "power_mac", "imac_ppc", "emac",
        "mac_mini_ppc", "mac_server_ppc", "xserve_ppc", "powerbook_ppc", "ibook",
    ),
    "appleintel": (
        "mac_pro_intel", "imac_intel", "imac_pro_intel", "mac_mini_intel",
        "xserve_intel", "macbook_pro_intel", "macbook_intel", "macbook_air_intel",
    ),
    "applearm": (
        "mac_pro_arm", "imac_arm", "mac_mini_arm", "macbook_pro_arm",
        "macbook_air_arm", "macbook_arm", "mac_studio",
    ),
}

FILTERS = {
    "names": (
        "stype",
        (
            "compact_mac", "mac_ii", "mac_lc", "mac_quadra", "mac_performa",
            "mac_centris", "power_mac", "power_mac_g3_g4_g5", "imac_normal", "emac",
            "mac_mini", "nmac_pro", "imac_pro", "mac_studio", "powerbook_normal",
            "powerbook_duo", "powerbook_g3_g4_g5", "ibook", "macbook_pro",
            "macbook_normal", "macbook_air", "macbook_neo",
            "workgroup_server", "apple_network_server", "mac_server", "xserve",
        ),
        (
            "Compact Macintosh", "Macintosh II", "Macintosh LC", "Macintosh Quadra",
            "Macintosh Performa", "Macintosh Centris", "Power Macintosh",
            "Power Mac G3/G4/G5", "iMac", "eMac", "Mac mini", "Mac Pro", "iMac Pro",
            "Mac Studio", "Macintosh PowerBook", "Macintosh PowerBook Duo",
            "PowerBook G3/G4/G5", "iBook", "MacBook Pro", "MacBook", "MacBook Air",
            "MacBook Neo", "Workgroup Server", "Apple Network Server",
            "Macintosh Server", "Xserve",
        ),
    ),
    "processors": (
        "sprocessor",
        (
            "68000", "68020", "68030", "68040", "601", "603", "604", "g3", "g4", "g5",
            "netburst", "p6", "core", "penryn", "nehalem", "westmere", "snb", "ivb",
            "haswell", "broadwell", "skylake", "kabylake", "coffeelake", "amberlake",
            "cascadelake", "cometlake", "icelake", "a12z", "m1", "m2",
            "m3", "m4", "m5", "a18",
        ),
        (
            "Motorola 68000", "Motorola 68020", "Motorola 68030", "Motorola 68040",
            "PowerPC 601", "PowerPC 603", "PowerPC 604", "PowerPC G3", "PowerPC G4",
            "PowerPC G5", "Intel NetBurst", "Intel P6 (Yonah)", "Intel Core",
            "Intel Penryn", "Intel Nehalem", "Intel Westmere", "Intel Sandy Bridge",
            "Intel Ivy Bridge", "Intel Haswell", "Intel Broadwell", "Intel Skylake",
            "Intel Kaby Lake", "Intel Coffee Lake", "Intel Amber Lake",
            "Intel Cascade Lake", "Intel Comet Lake", "Intel Ice Lake",
            "Apple A12Z", "Apple M1", "Apple M2", "Apple M3",
            "Apple M4", "Apple M5", "Apple A18 Pro",
        ),
    ),
    "years": (
        "syear",
        tuple(str(year) for year in range(1984, 2027)),
        tuple(str(year) for year in range(1984, 2027)),
    ),
}

FILTER_SECTIONS = {
    "names": ((0, "desktop"), (14, "laptop"), (22, "server")),
    "processors": (),
    "years": (),
}

DIRECTORY_COLUMNS = (
    "name", "sname", "syear", "stype", "sprocessor", "smodel", "sident",
    "sgestalt", "sorder", "semc",
)

FORMAT_SOURCE_COLUMNS = ("processor", "graphics")

MACHINE_COLUMNS = (
    "id", "stype", "sound", "pic", "name", "sname", "year", "syear",
    "model", "smodel", "ident", "sident", "gestalt", "sgestalt", "order",
    "sorder", "emc", "semc", "processor", "sprocessor", "processorid",
    "graphics", "graphicsid", "display", "ram", "rom", "software",
    "storage", "features", "expansion", "design", "support", "links", "uid",
)

REQUIRED_TEXT_COLUMNS = (
    "stype", "pic", "name", "sname", "year", "syear", "design", "support",
    "links",
)

SUPPORT_VALUES = ("Supported", "Vintage", "Obsolete", "N/A")

SOUND_VALUES = ("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "PB", "T2", "N")

# Keep these in sync with MachineHelper.getProcessorImage/getGraphicsImage.
PROCESSOR_IMAGE_VALUES = (
    "7400", "7410", "7440", "7445", "7447", "7450", "7455", "750", "750cx",
    "750cxe", "750fx", "970", "970fx", "970mp", "A12Z", "core2duo", "core2ex", "coreduo",
    "corei3_1", "corei3_2", "corei3_8", "corei3_10", "corei5", "corei5_1",
    "corei5_2", "corei5_4", "corei5_5", "corei5_6", "corei5_7", "corei5_8",
    "corei5_9", "corei5_10", "corei7", "corei7_1", "corei7_2", "corei7_4",
    "corei7_5", "corei7_6", "corei7_7", "corei7_8", "corei7_9", "corei7_10",
    "corei9_8", "corei9_9", "corei9_10", "corem", "corem3_6", "corem3_7",
    "corem5_6", "corem7_6", "coresolo", "m1", "m1max", "m1pro", "m1ultra",
    "p4ht", "t1", "t2", "xeon_1", "xeon_2", "xeon_6", "xeon_a", "xeon_b",
)

GRAPHICS_IMAGE_VALUES = (
    "amdfirepro", "amdradeon", "amdradeon2013", "amdradeon2016", "amdradeonvega",
    "ati", "atiradeon2000", "atiradeon2004", "atiradeon2007", "intelhd",
    "inteliris", "inteliris2020", "nvgeforce2mx", "nvgeforce3", "nvgeforce4",
    "nvgeforce6", "nvgeforce7", "nvgeforce2008", "nvgeforcefx", "nvgeforcegt2012",
    "nvgeforcegtx2012", "nvquadro", "nvquadro2008",
)

DISPLAYED_SEARCH_COLUMNS = (
    ("model", "smodel"), ("ident", "sident"), ("gestalt", "sgestalt"),
    ("order", "sorder"), ("emc", "semc"),
)

DISPLAY_YEAR = re.compile(r"\d{4}\.(?:[1-9]|1[0-2])(?: \([^\n]+\))?")
SORTING_YEAR = re.compile(r"\d{4}\.(?:[1-9]|1[0-2])")

PROCESSOR_SPEED = re.compile(r"\d+(?:\.\d+)?\s+(?:MHz|GHz)")

PROCESSOR_MODEL_PREFIXES = (
    "Motorola ", "PowerPC ", "Dual PowerPC ", "Intel Core ", "Intel Xeon ",
    "Dual Intel Xeon ", "Quad Intel Xeon ", "Intel Pentium ", "Intel 486",
    "Apple ", "Cyrix ", "AT&T ",
)

APPLE_PROCESSOR_MODEL = re.compile(
    r"Apple (?:A18 Pro|M[1-5](?: Pro| Max| Ultra)?|T[12])(?: \([^)]+\))?"
)

GRAPHICS_MODEL_PREFIXES = (
    "ATI ", "AMD ", "Dual AMD ", "NVIDIA ", "Intel ", "Apple ",
    "Chips and Technologies ", "Cirrus Logic ", "IMS ",
    "Macintosh II Video Card",
)

GRAPHICS_DETAIL = re.compile(
    r" (?:(?:up to )?\d+(?:\.\d+)?(?:–\d+(?:\.\d+)?)?"
    r"(?: or \d+(?:\.\d+)?)? (?:KB|MB|GB)|Revision [A-Z]|"
    r"unified memory architecture)"
)

GRAPHICS_MODEL_ONLY = ("ATI Rage 128 Pro", "Apple 8-core GPU", "Intel GMA 900")

PICTURE_ID = re.compile(r"[a-z0-9_]+")
IMAGE_VALUE_ID = re.compile(r"[A-Za-z0-9_]+")
MACHINE_UID = re.compile(r"MI\d{6}")
UID_SEQUENCE_PATH = Path(__file__).with_name("machine_uid_sequence")
OLD_MACHINE_NAMES = "old_machine_names.json"

DIRECTORY_VALUE_PATTERNS = {
    # The first compact Macs have genuine regional suffixes, such as M0001AP.
    "smodel": re.compile(r"(?:[AM]\d{4}|M0001[A-Z]{1,2})"),
    # The original iMac uses the genuine identifier iMac,1.
    "sident": re.compile(r"[A-Za-z][A-Za-z0-9]*\d*,\d+"),
    "sgestalt": re.compile(r"\d+"),
    # Apple currently uses both four- and five-character parts before xx.
    "sorder": re.compile(r"[A-Z0-9]{4,5}xx/[A-Z]"),
    # A handful of early EMC numbers use C or -1 revisions.
    "semc": re.compile(r"\d{3,4}(?:C|-1)?"),
}


def fail(message):
    raise RuntimeError(message)


def validate_category_configuration(connection):
    actual_categories = {
        row[0] for row in connection.execute(
            "SELECT name FROM sqlite_master "
            "WHERE type = 'table' AND sql LIKE '%processorid%'"
        )
    }
    expected_categories = set(CATEGORIES)
    if actual_categories != expected_categories:
        missing = sorted(expected_categories - actual_categories)
        unexpected = sorted(actual_categories - expected_categories)
        fail(
            "Machine category configuration differs from the database: "
            f"missing={missing}, unexpected={unexpected}"
        )

    configured_categories = [
        category
        for manufacturer, categories in MANUFACTURERS.items()
        if manufacturer != "all"
        for category in categories
    ]
    if (set(configured_categories) != expected_categories
            or len(configured_categories) != len(expected_categories)):
        fail("Manufacturer groups do not cover every machine category exactly once")


def validate_directory_values(machine):
    for column_name, pattern in DIRECTORY_VALUE_PATTERNS.items():
        raw_value = machine[column_name]
        if raw_value is None:
            continue
        values = raw_value.split("~")
        if len(values) != len(set(values)):
            fail(
                f'Duplicate {column_name} value for '
                f'{machine["category"]}/{machine["database_id"]}'
            )
        for value in values:
            if pattern.fullmatch(value) is None:
                fail(
                    f'Illegal {column_name} "{value}" for '
                    f'{machine["category"]}/{machine["database_id"]}'
                )


def get_displayed_search_values(value):
    if value is None:
        return set()
    return {
        re.sub(r"\s+\([^\n]*\)$", "", line.strip())
        for line in value.splitlines()
        if line.strip()
    } - {"BTO/CTO"}


def get_search_values(value):
    if value is None:
        return set()
    return {item.strip() for item in value.split("~") if item.strip()}


def validate_ram_information(value, machine_name):
    if value is None:
        return
    lines = value.splitlines()
    if "standard" not in lines[0]:
        fail(f"RAM information does not begin with a standard capacity for {machine_name}")
    maximum_index = next(
        (index for index, line in enumerate(lines) if "maximum" in line),
        len(lines),
    )
    for line in lines:
        if "standard" in line and "configurable" in line:
            fail(f'RAM line mixes standard and configurable capacities in "{line}"')
        if "configurable maximum" in line:
            fail(f'RAM line mixes configurable and maximum capacities in "{line}"')
    if any("configurable" in line for line in lines[maximum_index + 1:]):
        fail(f"RAM configurable capacity follows a maximum for {machine_name}")
    if any("actual maximum" in line for line in lines) and not any(
        marker in line
        for line in lines
        for marker in ("Apple-supported maximum", "Apple-tested maximum", "usable maximum")
    ):
        fail(f"RAM actual maximum lacks an official or usable ceiling for {machine_name}")
    if any("can be installed" in line for line in lines):
        fail(f"RAM information uses legacy installable-capacity wording for {machine_name}")


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


def validate_machine_data(connection, uid_sequence):
    displayed_names = set()
    searchable_names = {}
    machine_uids = set()
    for table_name in CATEGORIES:
        columns = tuple(
            row[1] for row in connection.execute(
                f'PRAGMA table_info("{table_name}")'
            )
        )
        if columns != MACHINE_COLUMNS:
            fail(f"Illegal machine table schema: {table_name}")

        rows = connection.execute(
            f'SELECT * FROM "{table_name}" ORDER BY id'
        ).fetchall()
        for expected_database_id, row in enumerate(rows):
            machine = dict(zip(MACHINE_COLUMNS, row))
            if machine["id"] != expected_database_id:
                fail(f'Illegal database ID {machine["id"]} in category {table_name}')
            machine_name = f'{table_name}/{machine["id"]}'

            if MACHINE_UID.fullmatch(machine["uid"] or "") is None:
                fail(f'Illegal machine UID "{machine["uid"]}" for {machine_name}')
            if int(machine["uid"][2:]) > uid_sequence:
                fail(f'Machine UID exceeds the current sequence for {machine_name}')
            if machine["uid"] in machine_uids:
                fail(f'Duplicate machine UID "{machine["uid"]}"')
            machine_uids.add(machine["uid"])

            for column_name in REQUIRED_TEXT_COLUMNS:
                if not machine[column_name]:
                    fail(f"Missing {column_name} for {machine_name}")

            if machine["name"] in displayed_names:
                fail(f'Duplicate displayed machine name "{machine["name"]}"')
            displayed_names.add(machine["name"])

            for column_name in MACHINE_COLUMNS[1:]:
                value = machine[column_name]
                if value is None:
                    continue
                if not isinstance(value, str):
                    fail(f"Non-text {column_name} for {machine_name}")
                if value != value.strip() or not value:
                    fail(f"Unnormalized {column_name} for {machine_name}")
                if "\r" in value or "\t" in value or "\n\n" in value:
                    fail(f"Illegal whitespace in {column_name} for {machine_name}")
                if "Not Applicable/No Info" in value:
                    fail(f"Obsolete placeholder in {column_name} for {machine_name}")
                if column_name != "support" and value == "N/A":
                    fail(f"Literal N/A in {column_name} for {machine_name}")

            validate_ram_information(machine["ram"], machine_name)

            for token_column in ("sname", "sprocessor"):
                if machine[token_column] is None:
                    continue
                tokens = machine[token_column].split("~")
                normalized_tokens = [token.casefold() for token in tokens]
                if any(not token or token != token.strip() for token in tokens):
                    fail(f"Illegal {token_column} token for {machine_name}")
                if len(normalized_tokens) != len(set(normalized_tokens)):
                    fail(f"Duplicate {token_column} token for {machine_name}")

            search_names = get_search_values(machine["sname"])
            if machine["name"] not in search_names:
                fail(f"Displayed name is not searchable for {machine_name}")
            for search_name in search_names:
                normalized_name = search_name.casefold()
                existing_machine = searchable_names.get(normalized_name)
                if existing_machine is not None:
                    fail(
                        f'Duplicate searchable name "{search_name}" for '
                        f"{existing_machine} and {machine_name}"
                    )
                searchable_names[normalized_name] = machine_name

            if machine["support"] not in SUPPORT_VALUES:
                fail(f'Unknown support value "{machine["support"]}" for {machine_name}')
            if machine["sound"] is not None and machine["sound"] not in SOUND_VALUES:
                fail(f'Unknown sound value "{machine["sound"]}" for {machine_name}')
            for image_column in ("processorid", "graphicsid"):
                image_values = machine[image_column]
                if image_values is None:
                    continue
                image_values = image_values.split(",")
                if len(image_values) != len(set(image_values)):
                    fail(f"Duplicate {image_column} value for {machine_name}")
                if any(IMAGE_VALUE_ID.fullmatch(value) is None for value in image_values):
                    fail(f'Illegal {image_column} "{machine[image_column]}" for {machine_name}')
                supported_values = (PROCESSOR_IMAGE_VALUES if image_column == "processorid"
                                    else GRAPHICS_IMAGE_VALUES)
                unknown_values = sorted(set(image_values) - set(supported_values))
                if unknown_values:
                    fail(
                        f"Unknown {image_column} values for {machine_name}: "
                        + ", ".join(unknown_values)
                    )

            display_years = machine["year"].split("\n")
            if any(DISPLAY_YEAR.fullmatch(year) is None for year in display_years):
                fail(f'Illegal display year "{machine["year"]}" for {machine_name}')
            sorting_years = machine["syear"].split(", ")
            if any(SORTING_YEAR.fullmatch(year) is None for year in sorting_years):
                fail(f'Illegal sorting year "{machine["syear"]}" for {machine_name}')
            display_year_values = {
                re.match(r"\d{4}\.\d+", year).group() for year in display_years
            }
            if len(sorting_years) != len(set(sorting_years)):
                fail(f"Duplicate sorting year for {machine_name}")
            if sorting_years != sorted(
                sorting_years,
                key=lambda year: tuple(int(value) for value in year.split(".")),
            ):
                fail(f"Unsorted sorting year for {machine_name}")
            if display_year_values != set(sorting_years):
                fail(f"Display and sorting years differ for {machine_name}")

            for display_column, search_column in DISPLAYED_SEARCH_COLUMNS:
                displayed = get_displayed_search_values(machine[display_column])
                searchable = get_search_values(machine[search_column])
                if displayed != searchable:
                    fail(
                        f"Displayed and searchable {display_column} values differ for "
                        f"{machine_name}"
                    )

            links = re.split(r"(?<=\.html);", machine["links"])
            if links == ["N"]:
                continue
            if len(links) != len(set(links)):
                fail(f"Duplicate links for {machine_name}")
            for link in links:
                label, separator, url_path = link.rpartition(",https://")
                if not separator or not label or not url_path:
                    fail(f'Illegal link "{link}" for {machine_name}')


def validate_old_machine_names(connection, database_path):
    active_uids = {
        row[0]
        for table_name in CATEGORIES
        for row in connection.execute(f'SELECT uid FROM "{table_name}"')
    }
    old_names_path = database_path.parent / OLD_MACHINE_NAMES
    try:
        old_names = json.loads(old_names_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"Unable to read old machine names: {error}")
    if not isinstance(old_names, dict) \
            or set(old_names) != {"schema", "names"} or old_names["schema"] != 1 \
            or not isinstance(old_names["names"], list) or not old_names["names"]:
        fail("Illegal old machine names document")
    names = set()
    for entry in old_names["names"]:
        if not isinstance(entry, dict) or set(entry) != {"name", "uid"}:
            fail("Illegal old machine name entry")
        name = entry["name"]
        uid = entry["uid"]
        normalized_name = name.casefold() if isinstance(name, str) else None
        if not name or name != name.strip() or normalized_name in names \
                or uid not in active_uids:
            fail(f'Illegal old machine identity "{name}" -> "{uid}"')
        names.add(normalized_name)


def get_serialized_sections(filter_name):
    sections = FILTER_SECTIONS[filter_name]
    category_count = len(FILTERS[filter_name][1])
    previous_position = -1
    for position, section_name in sections:
        if position <= previous_position or position < 0 or position >= category_count:
            fail(f"Illegal section position for {filter_name}")
        if re.fullmatch(r"[a-z0-9_]+", section_name) is None:
            fail(f"Illegal section name for {filter_name}")
        previous_position = position
    return ";".join(f"{position}:{section_name}" for position, section_name in sections)


def get_processor_model_range(line):
    model_start = len("Optional ") if line.startswith("Optional ") else 0
    quantity = re.match(r"\d+ - ", line[model_start:])
    if quantity is not None:
        model_start += quantity.end()
    model_line = line[model_start:]
    if not model_line.startswith(PROCESSOR_MODEL_PREFIXES):
        return None

    speed = PROCESSOR_SPEED.search(model_line)
    if speed is not None:
        model_prefix = model_line[:speed.start()]
        if model_prefix.endswith((" at ", ", ")):
            fail(f'Obsolete processor model separator in "{line}"')
        model_end = len(model_prefix.rstrip())
    elif model_line.startswith("Apple "):
        apple_model = APPLE_PROCESSOR_MODEL.match(model_line)
        if apple_model is None:
            fail(f'Unknown Apple processor model "{line}"')
        model_end = apple_model.end()
    elif " FPU" in model_line:
        model_end = model_line.index(" FPU") + len(" FPU")
    else:
        fail(f'Processor model has no detail boundary: "{line}"')

    separator = model_line[model_end:]
    if separator and (not separator.startswith(" ") or separator.startswith("  ")):
        fail(f'Illegal processor model separator in "{line}"')
    if separator.startswith((" at ", ", ", " and ")):
        fail(f'Obsolete processor model separator in "{line}"')
    # Qualifiers and quantities belong to the model label visually.  Keeping
    # the complete prefix bold avoids leaving a weak fragment at line start.
    return 0, model_start + model_end


def get_graphics_model_range(line):
    if line.startswith("Optional "):
        fail(f'Obsolete graphics qualifier in "{line}"')
    model_line = line
    if not model_line.startswith(GRAPHICS_MODEL_PREFIXES):
        return None
    detail = GRAPHICS_DETAIL.search(model_line)
    if detail is not None:
        model_end = detail.start()
        model_prefix = model_line[:model_end]
        if model_prefix.endswith((",", " and", " or", " at")):
            fail(f'Obsolete graphics model separator in "{line}"')
    elif model_line in GRAPHICS_MODEL_ONLY:
        model_end = len(model_line)
    else:
        fail(f'Graphics model has no detail boundary: "{line}"')
    return 0, model_end


def get_format_ranges(value, range_getter):
    if value is None:
        return ""
    ranges = []
    line_start = 0
    for line in value.split("\n"):
        model_range = range_getter(line)
        if model_range is not None:
            ranges.append(
                f"{line_start + model_range[0]}:{line_start + model_range[1]}"
            )
        line_start += len(line) + 1
    return ";".join(ranges)


def load_picture_assets(database_path):
    picture_directory = database_path.parent / "machines"
    if not picture_directory.is_dir():
        fail(f"Machine picture directory does not exist: {picture_directory}")
    unexpected_files = [
        picture.name
        for picture in picture_directory.iterdir()
        if picture.is_file() and picture.suffix.lower() != ".webp"
    ]
    if unexpected_files:
        fail(f"Unexpected machine picture assets: {', '.join(unexpected_files)}")
    picture_assets = {
        picture.stem
        for picture in picture_directory.glob("*.webp")
    }
    if not picture_assets:
        fail("Machine picture assets are empty")
    return picture_assets


def load_directory(connection, picture_assets):
    directory = []
    machines = []
    used_picture_assets = set()
    for category_id, table_name in enumerate(CATEGORIES):
        columns = ", ".join(
            ("id", "uid") + DIRECTORY_COLUMNS + ("pic",) + FORMAT_SOURCE_COLUMNS
        )
        rows = connection.execute(
            f'SELECT {columns} FROM "{table_name}" ORDER BY id'
        ).fetchall()
        for expected_database_id, row in enumerate(rows):
            database_id = row[0]
            machine_uid = row[1]
            if database_id != expected_database_id:
                fail(f"Illegal database ID {database_id} in category {table_name}")
            machine_id = len(directory)
            directory_values = row[2:2 + len(DIRECTORY_COLUMNS)]
            picture = row[2 + len(DIRECTORY_COLUMNS)]
            processor, graphics = row[-2:]
            if picture is None:
                fail(f"Missing picture for {table_name}/{database_id}")
            if PICTURE_ID.fullmatch(picture) is None:
                fail(
                    f'Illegal picture ID "{picture}" for '
                    f"{table_name}/{database_id}"
                )
            if picture not in picture_assets:
                fail(
                    f'Missing picture asset "{picture}" for '
                    f"{table_name}/{database_id}"
                )
            used_picture_assets.add(picture)
            processor_format = get_format_ranges(
                processor, get_processor_model_range
            )
            graphics_format = get_format_ranges(
                graphics, get_graphics_model_range
            )
            directory.append(
                (machine_id, machine_uid, category_id, database_id)
                + directory_values
                + (picture, processor_format, graphics_format)
            )
            machine = {
                "machine_id": machine_id,
                "uid": machine_uid,
                "category": table_name,
                "database_id": database_id,
                **dict(zip(DIRECTORY_COLUMNS, directory_values)),
            }
            validate_directory_values(machine)
            machines.append(machine)
    if not directory:
        fail("Machine directory is empty")
    unused_picture_assets = picture_assets - used_picture_assets
    if unused_picture_assets:
        fail(
            "Unused machine picture assets: "
            + ", ".join(sorted(unused_picture_assets))
        )
    return directory, machines


def get_sorting_year(machine):
    raw_year = machine["syear"]
    if raw_year is None:
        fail(f'Missing sorting year for machine {machine["machine_id"]}')
    first_year = raw_year.split(", ")[0].split(".")
    if len(first_year) != 2:
        fail(f'Illegal sorting year "{raw_year}" for machine {machine["machine_id"]}')
    year, month = (int(value) for value in first_year)
    if month < 1 or month > 12:
        fail(f'Illegal sorting month "{raw_year}" for machine {machine["machine_id"]}')
    return year * 100 + month


def matches_filter_value(column_name, raw_value, keyword):
    raw_value = raw_value.lower()
    keyword = keyword.lower()
    if column_name == "stype":
        return raw_value == keyword
    if column_name == "sprocessor":
        return keyword in raw_value.split("~")
    return keyword in raw_value


def validate_filter_values(machines):
    for filter_name in ("names", "processors"):
        column_name, keywords, unused_labels = FILTERS[filter_name]
        for machine in machines:
            raw_value = machine[column_name]
            if raw_value is None:
                continue
            if column_name == "sprocessor":
                processor_values = {
                    value.lower() for value in raw_value.split("~") if value
                }
                known_values = {keyword.lower() for keyword in keywords}
                unknown_values = sorted(processor_values - known_values)
                if unknown_values:
                    fail(
                        f"Unknown processor filter values for "
                        f'{machine["category"]}/{machine["database_id"]}: '
                        + ", ".join(unknown_values)
                    )
            matched = [
                keyword for keyword in keywords
                if matches_filter_value(column_name, raw_value, keyword)
            ]
            if not matched:
                fail(
                    f'Unknown {filter_name} filter value "{raw_value}" for '
                    f'{machine["category"]}/{machine["database_id"]}'
                )
            if filter_name == "names" and len(matched) != 1:
                fail(
                    f'Ambiguous names filter value "{raw_value}" for '
                    f'{machine["category"]}/{machine["database_id"]}'
                )


def build_main_cache(machines):
    validate_filter_values(machines)
    cache = []
    for manufacturer, categories in MANUFACTURERS.items():
        included_categories = set(categories)
        for filter_name, (column_name, keywords, labels) in FILTERS.items():
            if len(keywords) != len(labels):
                fail(f"Filter label count does not match {filter_name}")
            positions = []
            for keyword in keywords:
                matched = [
                    machine["machine_id"]
                    for machine in machines
                    if machine["category"] in included_categories
                    and machine[column_name] is not None
                    and matches_filter_value(
                        column_name, machine[column_name], keyword
                    )
                ]
                matched.sort(key=lambda machine_id: get_sorting_year(machines[machine_id]))
                positions.append(",".join(str(machine_id) for machine_id in matched))
            cache.append((manufacturer, filter_name, ";".join(positions)))
    return cache


def write_indexes(connection, directory, cache):
    connection.execute("BEGIN IMMEDIATE")
    try:
        connection.execute("DROP TABLE IF EXISTS main_cache")
        connection.execute("DROP TABLE IF EXISTS main_filter")
        connection.execute("DROP TABLE IF EXISTS machine_directory")
        connection.execute(
            """
            CREATE TABLE machine_directory (
                machine_id INTEGER PRIMARY KEY,
                uid TEXT NOT NULL UNIQUE,
                category_id INTEGER NOT NULL,
                database_id INTEGER NOT NULL,
                name TEXT,
                sname TEXT,
                syear TEXT,
                stype TEXT,
                sprocessor TEXT,
                smodel TEXT,
                sident TEXT,
                sgestalt TEXT,
                sorder TEXT,
                semc TEXT,
                picture_asset TEXT NOT NULL,
                processor_format TEXT NOT NULL,
                graphics_format TEXT NOT NULL,
                UNIQUE (category_id, database_id)
            )
            """
        )
        connection.execute(
            """
            CREATE TABLE main_filter (
                filter TEXT PRIMARY KEY,
                column_name TEXT NOT NULL,
                keywords TEXT NOT NULL,
                labels TEXT NOT NULL,
                sections TEXT NOT NULL
            )
            """
        )
        connection.execute(
            """
            CREATE TABLE main_cache (
                manufacturer TEXT NOT NULL,
                filter TEXT NOT NULL,
                positions TEXT NOT NULL,
                PRIMARY KEY (manufacturer, filter)
            )
            """
        )
        connection.executemany(
            """
            INSERT INTO machine_directory (
                machine_id, uid, category_id, database_id, name, sname, syear, stype,
                sprocessor, smodel, sident, sgestalt, sorder, semc,
                picture_asset, processor_format, graphics_format
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            directory,
        )
        connection.executemany(
            """
            INSERT INTO main_filter (filter, column_name, keywords, labels, sections)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                (
                    filter_name, column_name, ",".join(keywords), "\n".join(labels),
                    get_serialized_sections(filter_name),
                )
                for filter_name, (column_name, keywords, labels) in FILTERS.items()
            ),
        )
        connection.executemany(
            "INSERT INTO main_cache (manufacturer, filter, positions) VALUES (?, ?, ?)",
            cache,
        )
        connection.commit()
    except Exception:
        connection.rollback()
        raise


def verify_indexes(connection, directory, cache):
    integrity_result = connection.execute("PRAGMA quick_check").fetchone()
    if integrity_result is None or integrity_result[0] != "ok":
        fail("SQLite quick check failed")

    actual_directory = connection.execute(
        """
        SELECT machine_id, uid, category_id, database_id, name, sname, syear, stype,
               sprocessor, smodel, sident, sgestalt, sorder, semc,
               picture_asset, processor_format, graphics_format
        FROM machine_directory
        ORDER BY machine_id
        """
    ).fetchall()
    if actual_directory != directory:
        fail("Generated machine directory is missing or outdated")

    actual_filters = {
        filter_name: (column_name, tuple(keywords.split(",")), tuple(labels.split("\n")))
        for filter_name, column_name, keywords, labels in connection.execute(
            "SELECT filter, column_name, keywords, labels FROM main_filter"
        )
    }
    if actual_filters != FILTERS:
        fail("Generated main filters are missing or outdated")

    actual_sections = {
        filter_name: sections
        for filter_name, sections in connection.execute(
            "SELECT filter, sections FROM main_filter"
        )
    }
    expected_sections = {
        filter_name: get_serialized_sections(filter_name)
        for filter_name in FILTERS
    }
    if actual_sections != expected_sections:
        fail("Generated main filter sections are missing or outdated")

    actual_cache = {
        (manufacturer, filter_name): positions
        for manufacturer, filter_name, positions in connection.execute(
            "SELECT manufacturer, filter, positions FROM main_cache"
        )
    }
    expected_cache = {
        (manufacturer, filter_name): positions
        for manufacturer, filter_name, positions in cache
    }
    if actual_cache != expected_cache:
        fail("Generated main cache is missing or outdated")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--allocate-uid", action="store_true")
    parser.add_argument("database", type=Path, nargs="?")
    arguments = parser.parse_args()

    if arguments.allocate_uid:
        if arguments.check or arguments.database is not None:
            fail("UID allocation does not accept other arguments")
        allocate_machine_uid()
        return
    if arguments.database is None:
        fail("Database path is required")

    database_path = arguments.database.resolve()
    if not database_path.is_file():
        fail(f"Database does not exist: {database_path}")

    if arguments.check:
        connection = sqlite3.connect(
            f"file:{database_path}?mode=ro", uri=True
        )
    else:
        connection = sqlite3.connect(database_path)

    try:
        uid_sequence = load_uid_sequence()
        validate_category_configuration(connection)
        validate_machine_data(connection, uid_sequence)
        validate_old_machine_names(connection, database_path)
        picture_assets = load_picture_assets(database_path)
        directory, machines = load_directory(connection, picture_assets)
        cache = build_main_cache(machines)
        if arguments.check:
            verify_indexes(connection, directory, cache)
            print(f"Verified {len(directory)} machines and {len(cache)} main cache entries.")
        else:
            write_indexes(connection, directory, cache)
            verify_indexes(connection, directory, cache)
            print(f"Generated {len(directory)} machines and {len(cache)} main cache entries.")
    finally:
        connection.close()


if __name__ == "__main__":
    main()
