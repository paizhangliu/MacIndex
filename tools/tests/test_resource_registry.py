#!/usr/bin/env python3

import tempfile
import unittest
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from generate_resource_registry import validate_webp_file


def riff(*chunks):
    contents = b"WEBP"
    for chunk_type, payload in chunks:
        contents += chunk_type + len(payload).to_bytes(4, "little") + payload
        if len(payload) & 1:
            contents += b"\0"
    return b"RIFF" + len(contents).to_bytes(4, "little") + contents


def vp8_header(width, height):
    return (b"\0\0\0\x9d\x01\x2a"
            + width.to_bytes(2, "little")
            + height.to_bytes(2, "little"))


class ResourceRegistryTest(unittest.TestCase):

    def validate(self, payload):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "asset.webp"
            path.write_bytes(payload)
            validate_webp_file(path)

    def test_accepts_complete_still_webp_structure(self):
        self.validate(riff((b"VP8 ", vp8_header(320, 240))))

    def test_rejects_length_consistent_invalid_image_payload(self):
        with self.assertRaisesRegex(RuntimeError, "VP8 image header"):
            self.validate(riff((b"VP8 ", b"not-an-image")))

    def test_rejects_truncated_chunk_and_animation(self):
        truncated = riff((b"VP8 ", vp8_header(1, 1)))[:-2]
        truncated = truncated[:4] + (len(truncated) - 8).to_bytes(4, "little") \
            + truncated[8:]
        with self.assertRaisesRegex(RuntimeError, "Truncated WebP chunk"):
            self.validate(truncated)
        with self.assertRaisesRegex(RuntimeError, "Animated WebP"):
            self.validate(riff((b"ANIM", b"\0" * 6)))


if __name__ == "__main__":
    unittest.main()
