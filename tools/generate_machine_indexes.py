#!/usr/bin/env python3

import argparse
import re
import sqlite3
from pathlib import Path


CATEGORIES = (
    "compact_mac", "mac_ii", "mac_lc", "mac_quadra", "mac_performa_68k", "mac_centris",
    "mac_server_68k", "powerbook_68k", "powerbook_duo_68k", "power_mac_classic",
    "mac_performa_ppc", "mac_server_ppc_classic", "powerbook_ppc_classic",
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
        "power_mac_classic", "mac_performa_ppc", "mac_server_ppc_classic",
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
            "mac_centris", "mac_server", "power_mac", "imac_normal", "emac", "xserve",
            "mac_mini", "nmac_pro", "imac_pro", "mac_studio", "powerbook_normal",
            "powerbook_duo", "ibook", "macbook_pro", "macbook_normal", "macbook_air",
            "macbook_neo",
        ),
        (
            "Compact Macintosh", "Macintosh II", "Macintosh LC", "Macintosh Quadra",
            "Macintosh Performa", "Macintosh Centris", "Macintosh Server",
            "Power Macintosh", "iMac", "eMac", "Xserve", "Mac mini", "Mac Pro",
            "iMac Pro", "Mac Studio", "Macintosh PowerBook", "Macintosh PowerBook Duo",
            "iBook", "MacBook Pro", "MacBook", "MacBook Air", "MacBook Neo",
        ),
    ),
    "processors": (
        "sprocessor",
        (
            "68000", "68020", "68030", "68040", "601", "603", "604", "g3", "g4", "g5",
            "netburst", "p6", "core", "penryn", "nehalem", "westmere", "snb", "ivb",
            "haswell", "broadwell", "skylake", "kabylake", "coffeelake", "amberlake",
            "cascadelake", "cometlake", "icelake", "a12", "m1", "m2",
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
    "names": ((0, "desktop"), (15, "laptop")),
    "processors": (),
    "years": (),
}

DIRECTORY_COLUMNS = (
    "name", "sname", "syear", "stype", "sprocessor", "smodel", "sident",
    "sgestalt", "sorder", "semc",
)

FORMAT_SOURCE_COLUMNS = ("processor", "graphics")

PROCESSOR_SPEED = re.compile(r"\d+(?:\.\d+)?\s+(?:MHz|GHz)")

PROCESSOR_MODEL_PREFIXES = (
    "Motorola ", "PowerPC ", "Dual PowerPC ", "Intel Core ", "Intel Xeon ",
    "Dual Intel Xeon ", "Quad Intel Xeon ", "Intel Pentium ", "Intel 486",
    "Apple ", "Cyrix ", "AT&T ", "MOS Technology ",
)

APPLE_PROCESSOR_MODEL = re.compile(
    r"Apple (?:A18 Pro|M[1-5](?: Pro| Max| Ultra)?|T[12])(?: \([^)]+\))?"
)

GRAPHICS_MODEL_PREFIXES = (
    "ATI ", "AMD ", "Dual AMD ", "NVIDIA ", "Intel ", "Apple ",
    "Chips and Technologies ", "IMS ", "Macintosh II Video Card",
)

GRAPHICS_DETAIL = re.compile(
    r" (?:(?:up to )?\d+(?:\.\d+)?(?:–\d+(?:\.\d+)?)?"
    r"(?: or \d+(?:\.\d+)?)? (?:KB|MB|GB)|Revision [A-Z]|"
    r"unified memory architecture)"
)

GRAPHICS_MODEL_ONLY = ("ATI Rage 128 Pro", "Apple 8-core GPU", "Intel GMA 900")

PICTURE_ID = re.compile(r"[a-z0-9_]+")

DIRECTORY_VALUE_PATTERNS = {
    # The first compact Macs have genuine regional suffixes, such as M0001AP.
    "smodel": re.compile(r"(?:[AM]\d{4}|M0001[A-Z]{1,2})"),
    # The original iMac uses the genuine identifier iMac,1.
    "sident": re.compile(r"[A-Za-z][A-Za-z0-9]*\d*,\d+"),
    "sgestalt": re.compile(r"\d+"),
    # Apple currently uses both four- and five-character parts before xx.
    "sorder": re.compile(r"[A-Z0-9]{4,5}(?:xx/[A-Z])?"),
}


def fail(message):
    raise RuntimeError(message)


def validate_directory_values(machine):
    for column_name, pattern in DIRECTORY_VALUE_PATTERNS.items():
        raw_value = machine[column_name]
        if raw_value is None:
            continue
        for value in raw_value.split("~"):
            if pattern.fullmatch(value) is None:
                fail(
                    f'Illegal {column_name} "{value}" for '
                    f'{machine["category"]}/{machine["database_id"]}'
                )


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
    elif model_line.startswith("MOS Technology "):
        io_processor = re.match(r"MOS Technology \S+", model_line)
        if io_processor is None:
            fail(f'Unknown I/O processor model "{line}"')
        model_end = io_processor.end()
    else:
        fail(f'Processor model has no detail boundary: "{line}"')

    separator = model_line[model_end:]
    if separator and (not separator.startswith(" ") or separator.startswith("  ")):
        fail(f'Illegal processor model separator in "{line}"')
    if separator.startswith((" at ", ", ", " and ")):
        fail(f'Obsolete processor model separator in "{line}"')
    return model_start, model_start + model_end


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
    current_picture = None
    used_picture_assets = set()
    for category_id, table_name in enumerate(CATEGORIES):
        columns = ", ".join(
            ("id",) + DIRECTORY_COLUMNS + ("pic",) + FORMAT_SOURCE_COLUMNS
        )
        rows = connection.execute(
            f'SELECT {columns} FROM "{table_name}" ORDER BY id'
        ).fetchall()
        for expected_database_id, row in enumerate(rows):
            database_id = row[0]
            if database_id != expected_database_id:
                fail(f"Illegal database ID {database_id} in category {table_name}")
            machine_id = len(directory)
            directory_values = row[1:1 + len(DIRECTORY_COLUMNS)]
            picture = row[1 + len(DIRECTORY_COLUMNS)]
            processor, graphics = row[-2:]
            if picture is not None:
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
                current_picture = picture
                used_picture_assets.add(picture)
            if current_picture is None:
                fail(f"Missing picture for {table_name}/{database_id}")
            processor_format = get_format_ranges(
                processor, get_processor_model_range
            )
            graphics_format = get_format_ranges(
                graphics, get_graphics_model_range
            )
            directory.append(
                (machine_id, category_id, database_id)
                + directory_values
                + (current_picture, processor_format, graphics_format)
            )
            machine = {
                "machine_id": machine_id,
                "category": table_name,
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


def build_main_cache(machines):
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
                    and keyword.lower() in machine[column_name].lower()
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
                machine_id, category_id, database_id, name, sname, syear, stype,
                sprocessor, smodel, sident, sgestalt, sorder, semc,
                picture_asset, processor_format, graphics_format
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        SELECT machine_id, category_id, database_id, name, sname, syear, stype,
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
    parser.add_argument("database", type=Path)
    arguments = parser.parse_args()

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
