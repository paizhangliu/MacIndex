#!/usr/bin/env python3

"""Load and validate the media owned by the MacIndex catalog bundle."""

import json
import re


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
        "default_logo_night_treatment",
        "logo_night_treatment_overrides", "manufacturer_type_logos",
        "processor_logos", "graphics_logos", "sound_profiles",
    }
    if not isinstance(manifest, dict) or set(manifest) != expected_keys:
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
                or treatment not in NIGHT_TREATMENTS:
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


def validate_catalog_resources(manifest, resources_root):
    logo_directory = resources_root / "logos"
    sound_directory = resources_root / "sounds"
    if not logo_directory.is_dir() or not sound_directory.is_dir():
        fail("Missing catalog logos/sounds resource directories")

    packaged_logos = {path.stem for path in logo_directory.glob("*.webp")}
    expected_logos = referenced_logos(manifest)
    if packaged_logos != expected_logos:
        fail(
            "Catalog logo resources differ from the manifest: "
            f"missing={sorted(expected_logos - packaged_logos)}, "
            f"unexpected={sorted(packaged_logos - expected_logos)}"
        )

    referenced_sounds = {
        resource
        for profile in manifest["sound_profiles"].values()
        for resource in (profile["startup"], profile["death"])
        if resource is not None
    }
    packaged_sounds = {path.stem for path in sound_directory.glob("*.flac")}
    if packaged_sounds != referenced_sounds:
        fail(
            "Catalog sound resources differ from the manifest: "
            f"missing={sorted(referenced_sounds - packaged_sounds)}, "
            f"unexpected={sorted(packaged_sounds - referenced_sounds)}"
        )
