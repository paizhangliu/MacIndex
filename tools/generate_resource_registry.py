#!/usr/bin/env python3

"""Generate the Android resource registry from the catalog resource manifest."""

import argparse
import json
import re
from pathlib import Path


SEMANTIC_KEY = re.compile(r"[a-z0-9][a-z0-9_]*")
RESOURCE_NAME = re.compile(r"[a-z][a-z0-9_]*")
RUNTIME_SOUND_KEY = re.compile(r"[A-Z][A-Z0-9_]*")
NIGHT_TREATMENTS = {"DARKEN", "WHITE_TINT", "MONOCHROME"}


def fail(message):
    raise RuntimeError(message)


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail(f'Duplicate resource manifest key "{key}"')
        result[key] = value
    return result


def referenced_logos(manifest):
    resources = set(manifest["manufacturer_type_logos"].values())
    for mapping_name in ("processor_logos", "graphics_logos"):
        resources.update(
            resource
            for mapped_resources in manifest[mapping_name].values()
            for resource in mapped_resources
        )
    return resources


def load_resource_manifest(path):
    try:
        manifest = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_keys,
        )
    except (OSError, ValueError) as error:
        fail(f"Unable to read resource manifest: {error}")
    expected_keys = {
        "schema", "default_logo_night_treatment",
        "logo_night_treatment_overrides", "manufacturer_type_logos",
        "processor_logos", "graphics_logos", "sound_profiles",
    }
    if not isinstance(manifest, dict) or set(manifest) != expected_keys \
            or manifest["schema"] != 4:
        fail("Illegal resource manifest document")

    default_night_treatment = manifest["default_logo_night_treatment"]
    if default_night_treatment not in NIGHT_TREATMENTS:
        fail("Illegal default logo night treatment")

    for mapping_name in (
        "manufacturer_type_logos", "processor_logos", "graphics_logos",
    ):
        mapping = manifest[mapping_name]
        if not isinstance(mapping, dict) or not mapping:
            fail(f"Illegal {mapping_name} mapping")
        for key, value in mapping.items():
            if SEMANTIC_KEY.fullmatch(key) is None:
                fail(f'Illegal {mapping_name} key "{key}"')
            resources = [value] if isinstance(value, str) else value
            if mapping_name == "manufacturer_type_logos" \
                    and not isinstance(value, str):
                fail(f'Manufacturer type logo "{key}" must name one resource')
            if not isinstance(resources, list) or not resources \
                    or len(resources) != len(set(resources)) \
                    or any(not isinstance(resource, str)
                           or RESOURCE_NAME.fullmatch(resource) is None
                           for resource in resources):
                fail(f'Illegal resources for {mapping_name} key "{key}"')

    all_referenced_logos = referenced_logos(manifest)
    night_overrides = manifest["logo_night_treatment_overrides"]
    if not isinstance(night_overrides, dict):
        fail("Illegal logo night treatment overrides")
    orphan_overrides = set(night_overrides) - all_referenced_logos
    if orphan_overrides:
        fail(
            "Logo night treatment overrides are not referenced: "
            + ", ".join(sorted(orphan_overrides))
        )
    for resource, treatment in night_overrides.items():
        if RESOURCE_NAME.fullmatch(resource) is None \
                or treatment not in NIGHT_TREATMENTS \
                or treatment == default_night_treatment:
            fail(f'Illegal logo asset policy for "{resource}"')

    sound_profiles = manifest["sound_profiles"]
    if not isinstance(sound_profiles, dict) or not sound_profiles:
        fail("Illegal sound_profiles mapping")
    expected_sound_fields = {"startup", "death"}
    for sound_key, profile in sound_profiles.items():
        if not isinstance(sound_key, str) \
                or RUNTIME_SOUND_KEY.fullmatch(sound_key) is None \
                or not isinstance(profile, dict) \
                or set(profile) != expected_sound_fields:
            fail(f'Illegal sound profile "{sound_key}"')
        for resource_kind in ("startup", "death"):
            resource = profile[resource_kind]
            if resource is not None and (
                    not isinstance(resource, str)
                    or RESOURCE_NAME.fullmatch(resource) is None):
                fail(f'Illegal {resource_kind} sound for "{sound_key}"')
        if profile["startup"] is None and profile["death"] is not None:
            fail(f'Death sound exists without startup sound for "{sound_key}"')
    return manifest


def validate_webp_file(path):
    """Validate the complete RIFF structure and the encoded image header."""
    try:
        payload = path.read_bytes()
    except OSError as error:
        fail(f"Unable to read WebP resource {path}: {error}")
    if len(payload) < 12 or payload[:4] != b"RIFF" or payload[8:12] != b"WEBP" \
            or int.from_bytes(payload[4:8], "little") + 8 != len(payload):
        fail(f"Illegal WebP resource: {path}")

    position = 12
    canvas_dimensions = None
    image_dimensions = None
    while position < len(payload):
        if position + 8 > len(payload):
            fail(f"Truncated WebP chunk header: {path}")
        chunk_type = payload[position:position + 4]
        chunk_size = int.from_bytes(payload[position + 4:position + 8], "little")
        data_start = position + 8
        data_end = data_start + chunk_size
        padded_end = data_end + (chunk_size & 1)
        if data_end > len(payload) or padded_end > len(payload):
            fail(f"Truncated WebP chunk: {path}")
        chunk = payload[data_start:data_end]

        if chunk_type == b"VP8X":
            if canvas_dimensions is not None or len(chunk) != 10 \
                    or chunk[1:4] != b"\0\0\0":
                fail(f"Illegal WebP extended header: {path}")
            canvas_dimensions = (
                int.from_bytes(chunk[4:7], "little") + 1,
                int.from_bytes(chunk[7:10], "little") + 1,
            )
        elif chunk_type == b"VP8 ":
            if image_dimensions is not None or len(chunk) < 10 \
                    or chunk[0] & 1 or chunk[3:6] != b"\x9d\x01\x2a":
                fail(f"Illegal WebP VP8 image header: {path}")
            image_dimensions = (
                int.from_bytes(chunk[6:8], "little") & 0x3fff,
                int.from_bytes(chunk[8:10], "little") & 0x3fff,
            )
        elif chunk_type == b"VP8L":
            if image_dimensions is not None or len(chunk) < 5 or chunk[0] != 0x2f:
                fail(f"Illegal WebP VP8L image header: {path}")
            packed_dimensions = int.from_bytes(chunk[1:5], "little")
            if packed_dimensions >> 29:
                fail(f"Unsupported WebP VP8L version: {path}")
            image_dimensions = (
                (packed_dimensions & 0x3fff) + 1,
                ((packed_dimensions >> 14) & 0x3fff) + 1,
            )
        elif chunk_type in (b"ANIM", b"ANMF"):
            fail(f"Animated WebP is not supported: {path}")
        position = padded_end

    if image_dimensions is None or 0 in image_dimensions \
            or canvas_dimensions is not None and canvas_dimensions != image_dimensions:
        fail(f"Illegal WebP image dimensions: {path}")


def validate_android_resources(manifest, resources_root):
    logo_directory = resources_root / "drawable-nodpi"
    sound_directory = resources_root / "raw"
    if not logo_directory.is_dir() or not sound_directory.is_dir():
        fail("Missing base drawable-nodpi/raw resource directories")

    for name in referenced_logos(manifest):
        path = logo_directory / f"{name}.webp"
        if not path.is_file():
            fail(f"Missing logo resource: {name}")
        validate_webp_file(path)

    referenced_sounds = {
        resource
        for profile in manifest["sound_profiles"].values()
        for resource in (profile["startup"], profile["death"])
        if resource is not None
    }
    for name in referenced_sounds:
        path = sound_directory / f"{name}.flac"
        try:
            header = path.read_bytes()[:4]
        except OSError as error:
            fail(f"Unable to read sound resource {name}: {error}")
        if header != b"fLaC":
            fail(f"Illegal FLAC resource: {path}")


def logo_constant(resource):
    return "LOGO_" + resource.upper()


def logo_array(resources):
    return "new LogoAsset[]{" + ", ".join(
        logo_constant(resource) for resource in resources
    ) + "}"


def logo_switch_lines(mapping, return_array):
    lines = []
    for key, resources in mapping.items():
        values = resources if isinstance(resources, list) else [resources]
        returned = logo_array(values) if return_array else logo_constant(values[0])
        lines.extend((f'            case "{key}":', f"                return {returned};"))
    return lines


def generate_java(manifest):
    type_lines = logo_switch_lines(manifest["manufacturer_type_logos"], False)
    processor_lines = logo_switch_lines(manifest["processor_logos"], True)
    graphics_lines = logo_switch_lines(manifest["graphics_logos"], True)
    logo_lines = []
    for resource in sorted(referenced_logos(manifest)):
        treatment = manifest["logo_night_treatment_overrides"].get(
            resource, manifest["default_logo_night_treatment"]
        )
        logo_lines.extend((
            f"    private static final LogoAsset {logo_constant(resource)} = new LogoAsset(",
            f"            R.drawable.{resource}, LogoAsset.NightTreatment.{treatment});",
        ))
    sound_lines = [
        "            case UNSPECIFIED:",
        "                return new int[]{0, 0};",
    ]
    for sound_key, profile in manifest["sound_profiles"].items():
        startup = f'R.raw.{profile["startup"]}' if profile["startup"] else "0"
        death = f'R.raw.{profile["death"]}' if profile["death"] else "0"
        sound_lines.extend((
            f"            case {sound_key}:",
            f"                return new int[]{{{startup}, {death}}};",
        ))

    lines = [
        "package com.macindex.macindex.resources;", "",
        "import androidx.annotation.NonNull;", "import androidx.annotation.Nullable;", "",
        "import com.macindex.macindex.R;",
        "import com.macindex.macindex.catalog.CatalogFormatException;",
        "import com.macindex.macindex.catalog.Machine;", "",
        "import java.util.ArrayList;", "import java.util.List;", "",
        "/** Generated from catalog/resource_manifest.json. Do not edit. */",
        "public final class MachineResourceRegistry {", "", *logo_lines, "",
        "    private MachineResourceRegistry() {", "    }", "",
        "    @Nullable",
        "    public static LogoAsset processorTypeLogo(@NonNull final Machine machine) {",
        "        if (machine.processorFamilyKeys().isEmpty()) {", "            return null;", "        }",
        "        switch (machine.manufacturerKey()) {", *type_lines,
        "            default:",
        "                throw unknownKey(\"manufacturer\", machine.manufacturerKey(), machine);",
        "        }", "    }", "",
        "    @NonNull",
        "    public static LogoAsset[] processorLogos(@NonNull final Machine machine) {",
        "        return logos(machine.processorLogoKeys(), machine, true);", "    }", "",
        "    @NonNull",
        "    public static LogoAsset[] graphicsLogos(@NonNull final Machine machine) {",
        "        return logos(machine.graphicsLogoKeys(), machine, false);", "    }", "",
        "    @NonNull",
        "    public static int[] soundResources(@NonNull final Machine machine) {",
        "        switch (machine.soundProfile()) {", *sound_lines,
        "            default:",
        "                throw new CatalogFormatException(\"Unknown sound profile \"",
        "                        + machine.soundProfile() + \" for \" + machine.uid());",
        "        }", "    }", "",
        "    @NonNull",
        "    private static LogoAsset[] logos(@NonNull final List<String> keys,",
        "                                      @NonNull final Machine machine,",
        "                                      final boolean processor) {",
        "        final List<LogoAsset> resources = new ArrayList<>();",
        "        for (String key : keys) {",
        "            final LogoAsset[] mapped = processor ? processorLogo(key, machine)",
        "                    : graphicsLogo(key, machine);",
        "            for (LogoAsset resource : mapped) {", "                resources.add(resource);",
        "            }", "        }", "        return resources.toArray(new LogoAsset[0]);",
        "    }", "",
        "    @NonNull",
        "    private static LogoAsset[] processorLogo(@NonNull final String key,",
        "                                               @NonNull final Machine machine) {",
        "        switch (key) {", *processor_lines, "            default:",
        "                throw unknownKey(\"processor logo\", key, machine);",
        "        }", "    }", "",
        "    @NonNull",
        "    private static LogoAsset[] graphicsLogo(@NonNull final String key,",
        "                                              @NonNull final Machine machine) {",
        "        switch (key) {", *graphics_lines, "            default:",
        "                throw unknownKey(\"graphics logo\", key, machine);",
        "        }", "    }", "",
        "    @NonNull",
        "    private static CatalogFormatException unknownKey(@NonNull final String kind,",
        "                                                     @NonNull final String key,",
        "                                                     @NonNull final Machine machine) {",
        "        return new CatalogFormatException(",
        "                \"Unknown \" + kind + \" key \" + key + \" for \" + machine.uid());",
        "    }", "}", "",
    ]
    return "\n".join(lines)


def write_if_changed(output_path, content):
    if output_path.is_file() and output_path.read_text(encoding="utf-8") == content:
        return False
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_suffix(output_path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(output_path)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--resources-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    manifest = load_resource_manifest(arguments.manifest.resolve())
    validate_android_resources(manifest, arguments.resources_root.resolve())
    content = generate_java(manifest)
    output = arguments.output.resolve()
    action = "Generated" if write_if_changed(output, content) else "Verified"
    print(f"{action} resource registry from {arguments.manifest}.")


if __name__ == "__main__":
    main()
