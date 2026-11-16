#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the website's screenshots in docs/screens/ from the two apps' own screenshots.

    python3 scripts/make_web_screens.py PATH/TO/ANDROID/PICTURES

iPhone: docs/screenshots/NAME-light.png and NAME-dark.png, used as they are.
Android: the pictures Kamae for Android's screen tests draw, in
android/build/outputs/roborazzi after `./gradlew :android:testDebugUnitTest
-Proborazzi.test.record=true`, or the "screenshots" artifact of the Check workflow on GitHub.
They're the screen alone, light as NAME.png and dark as NAME-dark.png.
Everything is resized to 720 px wide and saved as WebP. Needs Pillow.
"""
import sys
from pathlib import Path

from PIL import Image

WIDTH = 720
ROOT = Path(__file__).resolve().parent.parent
IOS = ["today", "workout", "exercise-progress", "exercise-howto", "history", "you"]
# The website's name: the screen test's.
ANDROID = {
    "today": "today",
    "workout": "workout",
    "exercise-progress": "exercise",
    "exercise-howto": "exercise-how-to",
    "history": "history",
    "you": "you",
}


def save(image: Image.Image, path: Path) -> None:
    image = image.convert("RGB")
    image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
    image.save(path, "WEBP", quality=82, method=6)
    print(path.name, image.size)


def main(android: Path) -> None:
    out = ROOT / "docs" / "screens"
    out.mkdir(exist_ok=True)
    for name in IOS:
        for mode in ("light", "dark"):
            save(Image.open(ROOT / "docs" / "screenshots" / f"{name}-{mode}.png"), out / f"ios-{name}-{mode}.webp")
    for name, test in ANDROID.items():
        for mode, suffix in (("light", ""), ("dark", "-dark")):
            save(Image.open(android / f"{test}{suffix}.png"), out / f"android-{name}-{mode}.webp")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
