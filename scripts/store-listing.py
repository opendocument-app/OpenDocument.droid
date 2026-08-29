#!/usr/bin/env python3
#
# The play listing: where it is kept, and the supply tree built out of it.
#
# Play keeps the release notes of every release it has taken, but only behind the
# console - nothing reads them back, and until now they were typed into the box at
# promotion. They are kept here instead, one file per locale per version code,
# `fastlane/metadata/android/<locale>/changelogs/41500.txt`, which is the name
# supply reads and what `app/build.gradle` derives from the version name.
#
#   scripts/store-listing.py --version v4.15.0                       check the notes
#   scripts/store-listing.py --version v4.15.0 --stage DIR           notes alone
#   scripts/store-listing.py --version v4.15.0 --stage DIR --app pro the whole listing
#
# The two apps share one listing and differ in two places, so what is staged is
# read in three passes - `fastlane/metadata/android/<locale>/`, then the app's own
# `all/`, then its `<locale>/` - and the last one to hold a file wins. A `${name}`
# left in any of that text is filled in the same way, from the app's own file, or
# with nothing where the app has none.
#
# A release run checks before it builds, so a version missing a translation fails
# in seconds rather than once both bundles are up - as does a locale that is no
# longer in the tree, or a listing with no title or description to put in it, both
# of which would otherwise leave that storefront saying what it says today.
# `scripts/store-copy.py` writes the release notes this reads.
#
# OpenDocument.ios has the same script against App Store Connect's shape.

import argparse
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
METADATA = ROOT / "fastlane" / "metadata" / "android"

# What each app says instead. `<app>/all/` is read into every locale, `<app>/de-DE/`
# into that one - so a title that is the same in every language is one file, and a
# sentence that has to be translated is fifteen.
APPS = ("pro", "lite")
EVERY_LOCALE = "all"

# The locales the listing is written in, and the language each is written in -
# which `scripts/store-copy.py` hands to the translator, rather than guess at from
# the directory name. Written down rather than read off the directories, because a
# locale that lost its directory - or only its full_description.txt - would
# otherwise drop out of the release without a word, and that storefront would go on
# showing the listing of whatever version last reached it. Dropping a language is a
# line deleted here.
LOCALES = {
    "cs-CZ": "Czech",
    "de-DE": "German",
    "en-US": "English",
    "es-ES": "Spanish, as written in Spain",
    "et": "Estonian",
    "fr-FR": "French",
    "hi-IN": "Hindi",
    "it-IT": "Italian",
    "ja-JP": "Japanese",
    "pl-PL": "Polish",
    "pt-BR": "Portuguese, as written in Brazil",
    "ru-RU": "Russian",
    "sv-SE": "Swedish",
    "tr-TR": "Turkish",
    "zh-CN": "Chinese, simplified",
}

# what play takes in one locale's "What's new". Far below the App Store's 4000, and
# the English already sits at 497 - so a translation has less room than the English
# used, not more, and saying so is the whole reason this is checked here.
LIMIT = 500

# The text of the listing, per locale, as supply names it. Listed rather than
# globbed so that adding a file here is a decision: everything in this set is
# pushed over whatever the play console currently says.
LOCALISED = (
    "title.txt",
    "short_description.txt",
    "full_description.txt",
    "video.txt",
)

# Of those, what no layer may leave unsaid. Supply uploads what it is handed and
# leaves the rest of the console alone, so a description missing here is not an
# empty listing - it is the old one still standing, which is the arrangement this
# replaces. `video.txt` is not required: a listing without a trailer is a listing.
REQUIRED = ("title.txt", "short_description.txt", "full_description.txt")

# Left behind deliberately, though supply would take it: `images/` holds an icon that
# predates the redesign, and the launcher icon is a job of its own. The pictures of
# the app are not in this tree at all - `scripts/store_screenshots.py` stages those,
# from a capture of the build going out.

# What play refuses, rather than truncates. Checked against what is staged, since
# that is what goes up - a title is short enough on its own and too long once an
# app has added a word to it.
LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
}

# Of the localised files, the ones play shows verbatim. An html entity in either
# is a leftover from wherever the text was copied through and reaches the store
# spelled out: pt-BR read "É&#39;o primeiro" for three releases before anyone
# looked. `full_description.txt` is deliberately not here - play takes a subset
# of html there, where `&amp;` is a spelling rather than a slip.
PLAIN = ("title.txt", "short_description.txt")
ENTITY = re.compile(r"&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

# The names a `${...}` may have. Declared, so that a misspelt one is an error
# rather than a sentence that quietly disappears from the store.
FILL_INS = ("ads",)

# the space in front comes with it, so a fill-in the app leaves empty does not
# leave a double space in the middle of a sentence
FILL_IN = re.compile(r"( ?)\$\{([a-z_]+)\}")


def version_code(version):
    """The version code play keys a release on, as app/build.gradle derives it.

    The same two-digits-per-part rule, with the same refusals: all three parts,
    none above 99. Kept in step by hand, because gradle cannot be asked for it
    without a configured build.
    """
    parts = version.strip().removeprefix("v").split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise ValueError(f"a version looks like 4.15.0 or v4.15.0, not '{version}'")

    numbers = [int(part) for part in parts]
    if any(number > 99 for number in numbers):
        raise ValueError(
            f"every part of '{version}' has to be below 100 for the "
            "two-digits-per-part version code to stay unambiguous"
        )
    return numbers[0] * 10000 + numbers[1] * 100 + numbers[2]


def shown(path):
    """A path as an error should read it: from the repository root where it is under it."""
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def locales(metadata=METADATA):
    """The locales of LOCALES, once the tree has been checked against it.

    A directory short of the list, or one beside it that the list does not name,
    is an error either way round: the first would ship a storefront the old
    listing, the second would leave a language written and never uploaded.
    """
    found = {
        d.name for d in metadata.iterdir() if (d / "full_description.txt").is_file()
    }
    problems = [
        f"{locale}: no {shown(metadata / locale / 'full_description.txt')}"
        for locale in LOCALES
        if locale not in found
    ]
    problems += [
        f"{locale}: a locale LOCALES does not name - add it there with the "
        "language it is written in, or delete the directory"
        for locale in sorted(found - set(LOCALES))
    ]
    if problems:
        raise ValueError(
            f"the listing is not written in the locales {Path(__file__).name} "
            "names:\n  " + "\n  ".join(problems)
        )
    return list(LOCALES)


def copy_path(locale, code, metadata=METADATA):
    """Where one locale's release notes for one version live."""
    return metadata / locale / "changelogs" / f"{code}.txt"


def sources(app, locale, metadata=METADATA):
    """Where one locale's text is read from, least specific first."""
    places = [metadata / locale]
    if app:
        own = metadata.parent.parent / f"metadata-{app}" / "android"
        places += [own / EVERY_LOCALE, own / locale]
    return places


def read(name, places):
    """The last of `places` to hold `name`, or None. An empty file does not count:
    supply reads one as "leave this be" rather than as "clear it", so a blank
    override would silently be no override at all."""
    found = None
    for place in places:
        path = place / name
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if text.strip():
            found = text
    return found


def fill_in(text, places, where):
    """Replace every `${name}` with what the app says, or with nothing."""

    def replace(match):
        space, name = match.groups()
        if name not in FILL_INS:
            raise ValueError(
                f"{where}: ${{{name}}} is not one of {', '.join(FILL_INS)} - "
                f"add it to FILL_INS in {Path(__file__).name} or fix the spelling"
            )
        said = (read(f"{name}.txt", places) or "").strip()
        return space + said if said else ""

    return FILL_IN.sub(replace, text)


def collect(code, metadata=METADATA):
    """The notes of every locale. Returns (texts by locale, reasons it is not usable)."""
    texts = {}
    problems = []

    for locale in locales(metadata):
        path = copy_path(locale, code, metadata)
        display = shown(path)

        if not path.is_file():
            problems.append(f"{locale}: no {display}")
            continue

        text = path.read_text(encoding="utf-8").strip()
        if not text:
            problems.append(f"{locale}: {display} is empty")
        elif len(text) > LIMIT:
            problems.append(
                f"{locale}: {display} is {len(text)} characters, over play's {LIMIT}"
            )
        else:
            texts[locale] = text

    return texts, problems


def stage(texts, directory, code, app=None, metadata=METADATA):
    """Write the metadata tree supply uploads.

    The release notes of this version always. With `app`, the rest of the listing
    text beside them, as that app says it - which is what makes this repository,
    rather than the play console, the place the listing is written.
    """
    directory = Path(directory)
    if app and app not in APPS:
        raise ValueError(f"no such app: {app} - one of {', '.join(APPS)}")

    oversized = []
    escaped = []
    unsaid = []

    def write(folder, name, text, places):
        text = fill_in(text, places, where=f"{folder.name}/{name}")
        limit = LIMITS.get(name)
        if limit and len(text.strip()) > limit:
            oversized.append(
                f"{folder.name}/{name} is {len(text.strip())} characters, "
                f"over play's {limit}"
            )
        if name in PLAIN:
            escaped.extend(
                f"{folder.name}/{name} says '&{entity};' where play shows no markup"
                for entity in ENTITY.findall(text)
            )
        (folder / name).write_text(text, encoding="utf-8")

    for locale, notes in texts.items():
        folder = directory / locale
        (folder / "changelogs").mkdir(parents=True, exist_ok=True)
        places = sources(app, locale, metadata)

        (folder / "changelogs" / f"{code}.txt").write_text(notes + "\n", encoding="utf-8")

        if not app:
            continue

        for name in LOCALISED:
            text = read(name, places)
            if text is None:
                if name in REQUIRED:
                    unsaid.append(
                        f"{locale}: no {name} in "
                        + ", ".join(f"{shown(place)}/" for place in places)
                    )
                continue
            write(folder, name, text, places)

    if unsaid:
        raise ValueError(
            f"the {app} listing says nothing where it has to:\n  "
            + "\n  ".join(unsaid)
            + "\nPlay would keep what the console says today instead."
        )

    if oversized:
        raise ValueError("play would refuse this listing:\n  " + "\n  ".join(oversized))

    if escaped:
        raise ValueError(
            "play would show this listing's markup as text:\n  " + "\n  ".join(escaped)
        )

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
        description="Check the store copy of one release, and stage it for supply."
    )
    parser.add_argument("--version", required=True, help="version, e.g. v4.15.0")
    parser.add_argument(
        "--stage", metavar="DIR", help="also write the supply metadata tree into DIR"
    )
    parser.add_argument(
        "--app",
        choices=APPS,
        help="stage the whole listing as this app says it, not the release notes alone",
    )
    args = parser.parse_args(argv)

    try:
        code = version_code(args.version)
        texts, problems = collect(code)
    except (OSError, ValueError) as reason:
        return fail(str(reason))

    if problems:
        return fail(
            f"no store copy to release {args.version} with:\n  "
            + "\n  ".join(problems)
            + f"\nRun scripts/store-copy.py {args.version} to write it."
        )

    if args.stage:
        try:
            stage(texts, args.stage, code, app=args.app)
        except (OSError, ValueError) as reason:
            return fail(str(reason))
        what = f"the {args.app} listing and notes" if args.app else "the notes"
        print(f"staged {what} of {len(texts)} locales for {code} in {args.stage}")
    else:
        print(
            f"{args.version} ({code}) has store copy in all {len(texts)} locales: "
            + ", ".join(texts)
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
