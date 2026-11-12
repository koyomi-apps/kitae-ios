#!/usr/bin/env python3
# Kitae for iPhone
# SPDX-License-Identifier: GPL-3.0-or-later
"""Draws the README's picture of Kitae: four screenshots from docs/screenshots side by side on
Kitae's colours, with a caption under each, once for light mode and once for dark.

    python3 scripts/readme_screens.py

Needs Pillow (pip3 install Pillow). Writes docs/screenshots/readme-light.png and readme-dark.png."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
FONT = ROOT / "ios" / "Shared" / "Fonts" / "SpaceMono-Regular.ttf"

# Each screenshot's name without -light or -dark, and its caption.
SCREENS = [
    ("today", ".plan your split"),
    ("workout", ".tick off your sets"),
    ("exercise-howto", ".see how it's done"),
    ("you", ".watch it add up"),
]

# Kitae's Card, InkSoft and Rule colours, and a shadow.
THEMES = {
    "light": {"canvas": (244, 240, 230), "text": (94, 92, 78), "edge": (28, 28, 22, 34), "shadow": (70, 60, 20, 70)},
    "dark": {"canvas": (30, 30, 24), "text": (179, 176, 160), "edge": (230, 226, 217, 40), "shadow": (0, 0, 0, 150)},
}

WIDTH, SIDE, GAP, TOP, BOTTOM = 2000, 92, 58, 84, 150
PHONE = (WIDTH - 2 * SIDE - (len(SCREENS) - 1) * GAP) // len(SCREENS)
CORNER = round(PHONE * 0.142)  # about the iPhone screen's own corners
EDGE = 2


def rounded(size, radius, scale=4):
    """A mask for a rounded rectangle, drawn large and scaled down for smooth corners."""
    w, h = size
    big = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), radius * scale, fill=255)
    return big.resize(size, Image.LANCZOS)


def tinted(mask, rgba):
    """A layer of one colour, seen through the mask."""
    layer = Image.new("RGBA", mask.size, rgba[:3] + (0,))
    layer.putalpha(mask.point(lambda a: a * rgba[3] // 255))
    return layer


def draw(theme):
    colours = THEMES[theme]
    shots = []
    for name, _ in SCREENS:
        shot = Image.open(SHOTS / f"{name}-{theme}.png").convert("RGBA")
        shots.append(shot.resize((PHONE, round(shot.height * PHONE / shot.width)), Image.LANCZOS))
    height = shots[0].height
    canvas = Image.new("RGBA", (WIDTH, TOP + height + BOTTOM), colours["canvas"] + (255,))

    # Soft shadows under the screens, then a thin edge around each one, then the screens.
    shadow = Image.new("L", canvas.size, 0)
    for i in range(len(shots)):
        x = SIDE + i * (PHONE + GAP)
        ImageDraw.Draw(shadow).rounded_rectangle((x, TOP + 22, x + PHONE, TOP + 22 + height), CORNER, fill=255)
    canvas.alpha_composite(tinted(shadow.filter(ImageFilter.GaussianBlur(34)), colours["shadow"]))
    screen = rounded((PHONE, height), CORNER)
    edge = tinted(rounded((PHONE + 2 * EDGE, height + 2 * EDGE), CORNER + EDGE), colours["edge"])
    font = ImageFont.truetype(str(FONT), 30)
    text = ImageDraw.Draw(canvas)
    for i, (shot, (_, caption)) in enumerate(zip(shots, SCREENS)):
        x = SIDE + i * (PHONE + GAP)
        canvas.alpha_composite(edge, (x - EDGE, TOP - EDGE))
        canvas.paste(shot, (x, TOP), screen)
        text.text((x + PHONE / 2, TOP + height + 70), caption, font=font, fill=colours["text"], anchor="mm")

    canvas.putalpha(rounded(canvas.size, 44))
    canvas.save(SHOTS / f"readme-{theme}.png", optimize=True)


if __name__ == "__main__":
    for theme in THEMES:
        draw(theme)
