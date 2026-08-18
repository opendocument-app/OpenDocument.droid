#!/usr/bin/env python3
#
# The play store screenshots: which ones there are, and the supply tree built out
# of what a capture run wrote.
#
# Unlike the store copy, these are not committed. A picture of the app is only
# worth as much as the app it was taken from, so they are taken during the
# release run, from the build going out, and handed to supply from there.
# `fastlane android screenshots` takes them; this says what a full set is.
#
#   scripts/store_screenshots.py --languages          what to capture
#   scripts/store_screenshots.py                      check what was captured
#   scripts/store_screenshots.py --stage DIR          check it and stage it
#
# An underscore in the name, where every other script here has a dash:
# `frame-screenshots.py` imports this one, and a dash cannot be imported.
#
# OpenDocument.ios has the same script against App Store Connect's shape.

import argparse
import os
import shutil
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENSHOTS = ROOT / "fastlane" / "screenshots"

# Store locale -> the language its documents are written in. The one copy of
# this: `make-screenshot-documents.py` checks its own languages against it and
# writes it into the test apk's assets, which is where `ScreenshotTests` reads it
# rather than holding a table that could disagree.
#
# `None` would mean the app has no such language, so that locale reads the
# English pictures; nothing is None today. The keys are the locales
# `scripts/store-listing.py` names, and the same directories `fastlane/metadata`
# has.
LOCALES = {
    "cs-CZ": "cs",
    "de-DE": "de",
    "en-US": "en",
    "es-ES": "es",
    "et": "et",
    "fr-FR": "fr",
    "hi-IN": "hi",
    "it-IT": "it",
    "ja-JP": "ja",
    "pl-PL": "pl",
    "pt-BR": "pt-BR",
    "ru-RU": "ru",
    "sv-SE": "sv",
    "tr-TR": "tr",
    "zh-CN": "zh",
}

FALLBACK = "en-US"

# What one device shows, in the order the store shows them. The same names the
# screenshot test writes - see `ScreenshotTests.kt` - and supply uploads a
# locale's pictures in filename order, which is why they are numbered.
SCREENS = (
    "01-recents",
    "02-text",
    "03-sheet",
    "04-edit",
    "05-pdf",
    "06-office",
)

# The devices photographed, and the directory supply uploads each one to. Play
# keeps a set per form factor and shows the phone one everywhere it has nothing
# better, so the tablet set is what makes the listing a tablet listing.
#
# `sevenInchScreenshots` is deliberately not among them: nothing is made for a
# 7" tablet in particular, and play falls back to the phone pictures there.
DIRECTORIES = {
    "phone": "phoneScreenshots",
    "tablet": "tenInchScreenshots",
}

# What the framed picture is, per device, in pixels. Not the size of the capture:
# play refuses a screenshot whose long side is more than twice its short one, and
# a Pixel 9 Pro XL is 1344x2992 - 2.23:1 - before anything is drawn around it. So
# `frame-screenshots.py` draws onto a canvas of its own and the capture is a
# picture inside it, which is what the frame is for anyway.
#
# 16:9 for the phone, 16:10 for the tablet, both the proportions of the device
# they stand for rather than of the screenshot inside them.
CANVASES = {
    "phone": (1440, 2560),
    "tablet": (1600, 2560),
}

# What play takes per form factor. Under two and it refuses the listing; over
# eight and it ignores the rest.
LEAST, MOST = 2, 8


def languages():
    """The locales worth capturing: the ones the app can be photographed in."""
    return [locale for locale, language in LOCALES.items() if language]


def borrowed():
    """The locales that read another one's pictures."""
    return [locale for locale, language in LOCALES.items() if not language]


def size(path):
    """The pixel size of a PNG, off its header rather than through a library."""
    with path.open("rb") as file:
        header = file.read(24)

    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError(f"{path.name} is not a PNG")

    return struct.unpack(">II", header[16:24])


def named(stem):
    """The (device, screen) a picture's name says it is, or (None, None).

    `phone-02-text`. The device is written into the name by the capture run,
    which is the only thing that knows which emulator it was driving - a framed
    picture is the size of its canvas, and two devices could share one.
    """
    for device in DIRECTORIES:
        for screen in SCREENS:
            if stem == f"{device}-{screen}":
                return device, screen

    return None, None


def collect(directory):
    """What one capture run wrote. Returns (files by locale and device, problems)."""
    directory = Path(directory)
    found = {}
    problems = []

    for locale in languages():
        folder = directory / locale
        if not folder.is_dir():
            problems.append(f"{locale}: no {folder}")
            continue

        pictures = {}
        for path in sorted(folder.glob("*.png")):
            device, screen = named(path.stem)
            if device is None:
                problems.append(
                    f"{locale}: {path.name} is not one of "
                    + ", ".join(f"{d}-{s}" for d in DIRECTORIES for s in SCREENS)
                )
                continue

            try:
                width, height = size(path)
            except (OSError, ValueError) as reason:
                problems.append(f"{locale}: {reason}")
                continue

            if (width, height) != CANVASES[device]:
                wanted = "x".join(str(side) for side in CANVASES[device])
                problems.append(f"{locale}: {path.name} is {width}x{height}, not {wanted}")
                continue

            pictures.setdefault(device, {})[screen] = path

        for device in DIRECTORIES:
            missing = [screen for screen in SCREENS if screen not in pictures.get(device, {})]
            if missing:
                problems.append(f"{locale}: no {device} {', '.join(missing)}")

        found[locale] = pictures

    return found, problems


def stage(found, directory):
    """Write the screenshots into the metadata tree supply uploads.

    Into the same directory `scripts/store-listing.py` stages the text in, under
    the `images/` subdirectory supply reads a locale's pictures from - so one
    tree is handed over and one edit goes to play.

    The borrowed locales are copied from the English rather than left out: what
    supply does not upload for a locale, play keeps - which would be whatever was
    there before this release.
    """
    directory = Path(directory)

    for locale, pictures in found.items():
        for device, screens in pictures.items():
            folder = directory / locale / "images" / DIRECTORIES[device]
            folder.mkdir(parents=True, exist_ok=True)
            for screen, path in screens.items():
                shutil.copyfile(path, folder / f"{screen}.png")

    for locale in borrowed():
        source = directory / FALLBACK / "images"
        target = directory / locale / "images"
        shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(source, target)

    return directory


def fail(message):
    if os.environ.get("GITHUB_ACTIONS"):
        # also surfaces as an annotation on the run, not only inside the step log
        print(f"::error::{message}")
    else:
        print(message, file=sys.stderr)
    return 1


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Check a run of play store screenshots, and stage it for supply."
    )
    parser.add_argument(
        "--languages",
        action="store_true",
        help="print the locales to capture, one per line, and do nothing else",
    )
    parser.add_argument(
        "--screenshots",
        metavar="DIR",
        default=SCREENSHOTS,
        help=f"where the capture run wrote (default {SCREENSHOTS.relative_to(ROOT)})",
    )
    parser.add_argument(
        "--stage",
        metavar="DIR",
        help="also write the screenshots into the supply metadata tree in DIR",
    )
    args = parser.parse_args(argv)

    if args.languages:
        print("\n".join(languages()))
        return 0

    if not LEAST <= len(SCREENS) <= MOST:
        return fail(f"play takes {LEAST} to {MOST} screenshots per device, not {len(SCREENS)}")

    found, problems = collect(args.screenshots)

    if problems:
        return fail(
            "no full set of screenshots to release with:\n  "
            + "\n  ".join(problems)
            + "\nRun `bundle exec fastlane android screenshots` to take them."
        )

    if args.stage:
        try:
            stage(found, args.stage)
        except OSError as reason:
            return fail(str(reason))
        print(
            f"staged {len(SCREENS)} screenshots per device for "
            f"{len(found) + len(borrowed())} locales in {args.stage}"
        )
    else:
        pictures = sum(len(screens) for locale in found.values() for screens in locale.values())
        print(f"{pictures} screenshots in all {len(found)} captured locales: {', '.join(found)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
