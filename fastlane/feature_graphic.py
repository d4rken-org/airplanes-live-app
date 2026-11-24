#!/usr/bin/env python3
"""Renders the Google Play feature graphic from the app icon and the first store screenshot.

Reads fastlane/metadata/android/en-US/images/icon.png and phoneScreenshots/1_map.png,
writes images/featureGraphic.png as a 1024x500 24-bit PNG without alpha.
Rerun it after retaking 1_map.png.

Usage: fastlane/feature_graphic.py [--screenshot PATH] [--out PATH] [--bold FONT.ttf] [--regular FONT.ttf]
Needs Pillow (pip install pillow) and Noto Sans (Debian/Ubuntu: fonts-noto-core).
"""
import argparse
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("ERROR: Pillow is missing, install it with: pip install pillow")

IMAGES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "metadata/android/en-US/images")
FONTS = "/usr/share/fonts/truetype/noto"
TAGLINE = "Live, unfiltered air traffic, worldwide"
BLUE = (6, 75, 117)
W, H = 1024, 500
S = 2  # supersampling factor


def rounded(image, radius):
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, image.width - 1, image.height - 1], radius, fill=255)
    out = image.copy()
    out.putalpha(mask)
    return out


def font(path, size):
    try:
        return ImageFont.truetype(path, size * S)
    except OSError:
        sys.exit(f"ERROR: Font not found: {path}")


def render(screenshot, bold, regular):
    icon = Image.open(os.path.join(IMAGES, "icon.png")).convert("RGBA")
    screen = Image.open(screenshot).convert("RGBA")
    if screen.size != (1080, 2400):
        sys.exit(f"ERROR: {screenshot} is {screen.size[0]}x{screen.size[1]}, the crop below expects 1080x2400")

    canvas = Image.new("RGBA", (W * S, H * S), BLUE)
    glow = Image.new("RGBA", canvas.size)
    draw = ImageDraw.Draw(glow)
    for x in range(canvas.width):
        draw.line([(x, 0), (x, canvas.height)], fill=(255, 255, 255, int(34 * x / canvas.width)))
    canvas.alpha_composite(glow)

    # Below the status bar and above the bottom navigation
    crop = screen.crop((0, 300, 1080, 2160))
    phone_h = 560 * S
    phone_w = crop.width * phone_h // crop.height
    bezel = 9 * S
    phone = Image.new("RGBA", (phone_w + 2 * bezel, phone_h + 2 * bezel))
    phone.alpha_composite(rounded(Image.new("RGBA", phone.size, (18, 20, 24, 255)), 40 * S))
    phone.alpha_composite(rounded(crop.resize((phone_w, phone_h), Image.LANCZOS), 32 * S), (bezel, bezel))
    phone = phone.rotate(-7, expand=True, resample=Image.BICUBIC)

    shadow = Image.new("RGBA", phone.size)
    shadow.putalpha(phone.getchannel("A").point(lambda a: int(a * 0.35)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14 * S))
    px, py = 640 * S, 28 * S
    canvas.alpha_composite(shadow, (px + 6 * S, py + 12 * S))
    canvas.alpha_composite(phone, (px, py))

    canvas.alpha_composite(rounded(icon.resize((112 * S, 112 * S), Image.LANCZOS), 26 * S), (72 * S, 96 * S))

    draw = ImageDraw.Draw(canvas)
    x = 72 * S
    draw.text((x, 228 * S), "airplanes.live", font=font(bold, 60), fill="white")
    draw.text((x, 304 * S), "Flight Tracker", font=font(regular, 40), fill="white")
    draw.text((x, 372 * S), TAGLINE, font=font(regular, 23), fill=(214, 230, 242))

    return canvas.resize((W, H), Image.LANCZOS).convert("RGB")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--screenshot", default=os.path.join(IMAGES, "phoneScreenshots/1_map.png"))
    parser.add_argument("--out", default=os.path.join(IMAGES, "featureGraphic.png"))
    parser.add_argument("--bold", default=os.path.join(FONTS, "NotoSans-Bold.ttf"))
    parser.add_argument("--regular", default=os.path.join(FONTS, "NotoSans-Regular.ttf"))
    args = parser.parse_args()
    render(args.screenshot, args.bold, args.regular).save(args.out, optimize=True)
    print(f"Saved {args.out}")


if __name__ == "__main__":
    main()
