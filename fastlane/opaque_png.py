#!/usr/bin/env python3
"""Rewrites store screenshots in place as 24-bit PNGs without an alpha channel.

Google Play takes screenshots and the feature graphic only as "JPEG or 24-bit PNG (no alpha)".
Compose renders and `adb screencap` both write RGBA, even when every pixel is opaque.
Refuses a file with a pixel that is not fully opaque, since dropping alpha would change how it looks.

Usage: fastlane/opaque_png.py FILE.png [FILE.png ...]
Needs Pillow (pip install pillow).
"""
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("ERROR: Pillow is missing, install it with: pip install pillow")


def main(paths):
    failed = 0
    for path in paths:
        with Image.open(path) as image:
            image.load()
        if image.mode == "RGB":
            continue
        rgba = image.convert("RGBA")
        if rgba.getchannel("A").getextrema() != (255, 255):
            print(f"ERROR: {path} has transparent pixels, left unchanged", file=sys.stderr)
            failed += 1
            continue
        rgba.convert("RGB").save(path, optimize=True)
        print(f"Removed alpha: {path}")
    return 1 if failed else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1:]))
