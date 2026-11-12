#!/usr/bin/env python3
# Kitae for iPhone
# SPDX-License-Identifier: GPL-3.0-or-later
"""Draws Kitae's app icon: a barbell seen from the side, in Kitae's olive colours.

Writes the three 1024 x 1024 images the app icon set needs, for light and dark Home Screens
and for tinted icons, into ios/Kitae/Assets.xcassets/AppIcon.appiconset. Needs Pillow:

    python3 -m pip install Pillow
    python3 scripts/make_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 1024
# Drawn four times larger, then scaled down, for smooth edges.
SCALE = 4
FOLDER = Path(__file__).resolve().parent.parent / "ios" / "Kitae" / "Assets.xcassets" / "AppIcon.appiconset"

# Background and barbell for each look, from ios/Shared/Colors.xcassets: Olive with OliveSoft,
# and the dark Paper with the dark Olive.
LOOKS = {
    "AppIcon.png": ("#63611E", "#F7F3A1"),
    "AppIcon-Dark.png": ("#14140E", "#CECA75"),
    # Tinted icons are grey; iOS colours them.
    "AppIcon-Tinted.png": ("#000000", "#FFFFFF"),
}


def draw_barbell(draw: ImageDraw.ImageDraw, colour: str) -> None:
    def bar(left: int, top: int, right: int, bottom: int, radius: int) -> None:
        box = [left * SCALE, top * SCALE, right * SCALE, bottom * SCALE]
        draw.rounded_rectangle(box, radius=radius * SCALE, fill=colour)

    middle = SIZE // 2
    # The bar, end to end.
    bar(150, middle - 26, 874, middle + 26, 26)
    # The big plates, inside.
    bar(294, middle - 224, 390, middle + 224, 36)
    bar(634, middle - 224, 730, middle + 224, 36)
    # The small plates, outside.
    bar(222, middle - 150, 284, middle + 150, 26)
    bar(740, middle - 150, 802, middle + 150, 26)


def main() -> None:
    for name, (background, foreground) in LOOKS.items():
        # No transparency: the App Store refuses an icon with an alpha channel.
        image = Image.new("RGB", (SIZE * SCALE, SIZE * SCALE), background)
        draw_barbell(ImageDraw.Draw(image), foreground)
        image.resize((SIZE, SIZE), Image.LANCZOS).save(FOLDER / name, optimize=True)
        print(f"Wrote {FOLDER / name}")


if __name__ == "__main__":
    main()
