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
            "mac_mini", "nmac_pro", "imac_pro", "powerbook_normal", "powerbook_duo",
            "ibook", "macbook_pro", "macbook_normal", "macbook_air", "mac_studio",
        ),
        (
            "Compact Macintosh", "Macintosh II", "Macintosh LC", "Macintosh Quadra",
            "Macintosh Performa", "Macintosh Centris", "Macintosh Server",
            "Power Macintosh", "iMac", "eMac", "Xserve", "Mac mini", "Mac Pro",
            "iMac Pro", "Macintosh PowerBook", "Macintosh PowerBook Duo", "iBook",
            "MacBook Pro", "MacBook", "MacBook Air", "Mac Studio",
        ),
    ),
    "processors": (
        "sprocessor",
        (
            "68000", "68020", "68030", "68040", "601", "603", "604", "g3", "g4", "g5",
            "netburst", "p6", "core", "penryn", "nehalem", "westmere", "snb", "ivb",
            "haswell", "broadwell", "skylake", "kabylake", "coffeelake", "amberlake",
            "cascadelake", "cometlake", "icelake", "tigerlake", "a12", "m1", "m2",
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
            "Intel Tiger Lake", "Apple A12Z", "Apple M1", "Apple M2", "Apple M3",
            "Apple M4", "Apple M5", "Apple A18 Pro",
        ),
    ),
    "years": (
        "syear",
        tuple(str(year) for year in range(1984, 2027)),
        tuple(str(year) for year in range(1984, 2027)),
    ),
}

DIRECTORY_COLUMNS = (
    "name", "sname", "syear", "stype", "sprocessor", "smodel", "sident",
    "sgestalt", "sorder", "semc",
)

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


def load_directory(connection):
    directory = []
    machines = []
    for category_id, table_name in enumerate(CATEGORIES):
        columns = ", ".join(("id",) + DIRECTORY_COLUMNS)
        rows = connection.execute(
            f'SELECT {columns} FROM "{table_name}" ORDER BY id'
        ).fetchall()
        for expected_database_id, row in enumerate(rows):
            database_id = row[0]
            if database_id != expected_database_id:
                fail(f"Illegal database ID {database_id} in category {table_name}")
            machine_id = len(directory)
            directory.append((machine_id, category_id, database_id) + row[1:])
            machine = {
                "machine_id": machine_id,
                "category": table_name,
                **dict(zip(DIRECTORY_COLUMNS, row[1:])),
            }
            validate_directory_values(machine)
            machines.append(machine)
    if not directory:
        fail("Machine directory is empty")
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
                labels TEXT NOT NULL
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
                sprocessor, smodel, sident, sgestalt, sorder, semc
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            directory,
        )
        connection.executemany(
            """
            INSERT INTO main_filter (filter, column_name, keywords, labels)
            VALUES (?, ?, ?, ?)
            """,
            (
                (filter_name, column_name, ",".join(keywords), "\n".join(labels))
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
               sprocessor, smodel, sident, sgestalt, sorder, semc
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
        directory, machines = load_directory(connection)
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
