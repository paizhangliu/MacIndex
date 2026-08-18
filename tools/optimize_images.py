#!/usr/bin/env python3

import argparse
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MACHINE_DIRECTORY = ROOT / "app/src/main/assets/machines"
DRAWABLE_DIRECTORIES = (
    ROOT / "app/src/main/res/drawable",
    ROOT / "app/src/main/res/drawable-nodpi",
)

MACHINE_QUALITY = 90
DRAWABLE_QUALITY = 90
DRAWABLE_MAXIMUM_EDGE = 1024
DRAWABLE_MINIMUM_SIZE = 32 * 1024

# These small images have deliberate monochrome or inverted night-mode treatment.
PROTECTED_DRAWABLES = {
    "applelogo.webp",
    "cs125.webp",
    "everymac.webp",
    "github.webp",
    "intel.webp",
    "logorev2.webp",
    "macindexlogo.webp",
    "motorola.webp",
    "powerpc.webp",
}

RASTER_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def fail(message):
    raise RuntimeError(message)


def require_tool(name):
    if shutil.which(name) is None:
        fail(f"Required image tool is not installed: {name}")


def get_dimensions(path):
    result = subprocess.run(
        ["magick", "identify", "-format", "%w %h", str(path)],
        check=True,
        capture_output=True,
        text=True,
    )
    width, height = result.stdout.split()
    return int(width), int(height)


def is_lossy_webp(path):
    if path.suffix.lower() != ".webp":
        return False
    contents = path.read_bytes()
    return b"VP8 " in contents and b"VP8L" not in contents


def get_resized_dimensions(path):
    width, height = get_dimensions(path)
    maximum_edge = max(width, height)
    if maximum_edge <= DRAWABLE_MAXIMUM_EDGE:
        return None
    scale = DRAWABLE_MAXIMUM_EDGE / maximum_edge
    return max(1, round(width * scale)), max(1, round(height * scale))


def encode(source, quality, resized_dimensions=None):
    file_descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{source.stem}.", suffix=".webp", dir=source.parent
    )
    os.close(file_descriptor)
    Path(temporary_name).unlink()
    try:
        command = [
            "cwebp", "-quiet", "-q", str(quality), "-alpha_q", "100",
            "-alpha_method", "1", "-alpha_filter", "best", "-m", "6", "-mt",
            "-sharp_yuv", "-metadata", "all",
        ]
        if resized_dimensions is not None:
            command.extend(["-resize", str(resized_dimensions[0]), str(resized_dimensions[1])])
        command.extend([str(source), "-o", temporary_name])
        subprocess.run(command, check=True)
        return Path(temporary_name)
    except Exception:
        Path(temporary_name).unlink(missing_ok=True)
        raise


def replace_if_smaller(source, encoded):
    source_size = source.stat().st_size
    encoded_size = encoded.stat().st_size
    if encoded_size >= source_size:
        encoded.unlink()
        return source_size, source_size, source

    target = source.with_suffix(".webp")
    if target != source and target.exists():
        encoded.unlink()
        fail(f"Unable to replace {source}: {target.name} already exists")
    encoded.replace(target)
    if target != source:
        source.unlink()
    return source_size, encoded_size, target


def optimize_directory(paths, quality, resize_drawables, force):
    original_total = 0
    optimized_total = 0
    optimized_count = 0
    skipped_count = 0

    for source in sorted(paths):
        source_size = source.stat().st_size
        if resize_drawables and (
                source.name in PROTECTED_DRAWABLES
                or (source.suffix.lower() == ".webp"
                    and source_size <= DRAWABLE_MINIMUM_SIZE)):
            original_total += source_size
            optimized_total += source_size
            skipped_count += 1
            continue
        if not force and is_lossy_webp(source):
            original_total += source_size
            optimized_total += source_size
            skipped_count += 1
            continue

        resized_dimensions = get_resized_dimensions(source) if resize_drawables else None
        encoded = encode(source, quality, resized_dimensions)
        before, after, target = replace_if_smaller(source, encoded)
        original_total += before
        optimized_total += after
        if after < before:
            optimized_count += 1
            print(f"{target.relative_to(ROOT)}: {before} -> {after}")
        else:
            skipped_count += 1

    return original_total, optimized_total, optimized_count, skipped_count


def main():
    parser = argparse.ArgumentParser(
        description="Compress bundled MacIndex images without changing their presentation."
    )
    parser.add_argument(
        "--force", action="store_true",
        help="Re-encode WebP files that are already lossy. Use only for a deliberate quality change.",
    )
    arguments = parser.parse_args()

    require_tool("cwebp")
    require_tool("magick")

    machine_paths = list(MACHINE_DIRECTORY.glob("*.webp"))
    drawable_paths = [
        path
        for directory in DRAWABLE_DIRECTORIES
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() in RASTER_EXTENSIONS
    ]

    machine_result = optimize_directory(
        machine_paths, MACHINE_QUALITY, resize_drawables=False, force=arguments.force
    )
    drawable_result = optimize_directory(
        drawable_paths, DRAWABLE_QUALITY, resize_drawables=True, force=arguments.force
    )

    original_total = machine_result[0] + drawable_result[0]
    optimized_total = machine_result[1] + drawable_result[1]
    print(
        f"Optimized {machine_result[2]} machine images and {drawable_result[2]} drawables; "
        f"skipped {machine_result[3] + drawable_result[3]}."
    )
    print(
        f"Image size: {original_total / 1024 / 1024:.2f} MiB -> "
        f"{optimized_total / 1024 / 1024:.2f} MiB."
    )


if __name__ == "__main__":
    main()
