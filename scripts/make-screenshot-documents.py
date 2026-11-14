#!/usr/bin/env python3
"""Builds the documents the play store screenshots are taken of.

A reader's screenshots are mostly the document it is reading, so these are
written rather than borrowed: a report, a spreadsheet, a deck and the rest of a
folder around them, in every language the store has a listing for. The
screenshot of the German store shows a German document.

The documents themselves come from `sampledoc`
(https://github.com/opendocument-app/sampledoc), shared with the iOS app and
pinned in `scripts/requirements.txt`. What stays here is where they go and
which locale reads which language, from `store_screenshots.LOCALES`.

They are written into the *test* apk's assets, not the app's, and laid out on
the device by `ScreenshotTests`. An instrumented test runs in the app's own
process, so nothing about the screenshots has to exist in a build that ships:
no back door, no debug-only asset, no line in `MainActivity`.

    python3 -m pip install -r scripts/requirements.txt
    python3 scripts/make-screenshot-documents.py
    python3 scripts/make-screenshot-documents.py --language en    one of them

What it writes is not committed - the screenshot lane runs this before it
builds. The packages are reproducible, so a rerun writes the same documents.
"""

import argparse
import json
import re
from pathlib import Path

import store_screenshots as store

SAMPLES = (
    Path(__file__).resolve().parent.parent / "app" / "src" / "androidTest" / "assets" / "screenshots"
)

REQUIREMENTS = Path(__file__).resolve().with_name("requirements.txt")


def pinned() -> str:
    """The sampledoc version requirements.txt asks for."""
    for line in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        found = re.fullmatch(r"sampledoc==(\S+)", line.strip())
        if found:
            return found.group(1)

    raise SystemExit(f"{REQUIREMENTS.name} pins no sampledoc==<version>")


def main(argv=None) -> None:
    # The languages the store is photographed in, and so the ones there are
    # documents for. The locale table travels with the documents rather than
    # being written out a second time in `ScreenshotTests`: which locale reads
    # which language's documents is one fact, and a second copy can only disagree.
    spoken = sorted({language for language in store.LOCALES.values() if language})

    parser = argparse.ArgumentParser(description="Write the documents the store screenshots open.")
    parser.add_argument(
        "--language", action="append", choices=spoken,
        help="only this language, repeatable; default is all of them. What to reach for when a "
             "change is worded in English first and the rest are to follow.")
    args = parser.parse_args(argv)

    wanted = pinned()
    try:
        import sampledoc
    except ImportError:
        raise SystemExit(
            f"sampledoc is not installed - python3 -m pip install -r scripts/{REQUIREMENTS.name}"
        ) from None

    # A different version writes different documents, and the screenshots would
    # change with whoever happened to take them.
    if sampledoc.__version__ != wanted:
        raise SystemExit(
            f"sampledoc {sampledoc.__version__} is installed but {wanted} is pinned - "
            f"python3 -m pip install -r scripts/{REQUIREMENTS.name}"
        )

    missing = sorted(set(spoken) - set(sampledoc.languages()))
    if missing:
        raise SystemExit(
            f"store_screenshots.py photographs languages sampledoc has no documents in: {missing}"
        )

    written = sampledoc.generate(SAMPLES, languages=args.language or spoken)

    details = sampledoc.manifest(store.LOCALES)
    details["languages"] = {language: details["languages"][language] for language in spoken}
    (SAMPLES / "screenshot-names.json").write_text(
        json.dumps(details, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8"
    )

    print(f"wrote {len(written)} documents in {len(args.language or spoken)} languages to {SAMPLES}")


if __name__ == "__main__":
    main()
