#!/usr/bin/env python3
#
# Writes the store copy of one release: the English "What's new" text, and one
# translation per locale the listing has.
#
#   scripts/store-copy.py v4.15.0              write whatever is missing
#   scripts/store-copy.py v4.15.0 --english    rewrite the English too
#   scripts/store-copy.py v4.15.0 --dry-run    print it, write nothing
#
# One `claude -p` per language rather than one call holding all of them. Each
# agent is given that locale's own full_description.txt and the notes of the
# release before it, so it reaches for the words the listing already uses in that
# language instead of translating the English afresh every release. They run at
# the same time, and a language that comes back wrong is retried on its own.
#
# A second agent then reads the draft against the English, in the same language,
# and rewrites what reads as English wearing that language's words. `--no-review`
# skips it.
#
# The English is written from the CHANGELOG.md section of that version, or from
# Unreleased while the heading is still open. A file that is already there is
# left alone and translated, since that is the copy that was reviewed.
#
# Play allows 500 characters and refuses the release over it. That is an eighth of
# what the App Store allows, and every language this is translated into is longer
# than the English - so the English is held well under it rather than at it, and a
# translation that will not fit is a reason to shorten the English, not to ship it
# and find out at promotion.
#
# Nothing here uploads: `scripts/store-listing.py` checks and stages what this
# writes, and the release run uploads it.
#
# OpenDocument.ios has the same script against App Store Connect's shape.

import argparse
import concurrent.futures
import importlib.util
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load(path, name):
    """Import a sibling script, whose file name is not an identifier."""
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


listing = load(ROOT / "scripts" / "store-listing.py", "store_listing")
changelog = load(ROOT / ".github" / "scripts" / "changelog-section.py", "changelog_section")

SOURCE = "en-US"

# The English is held here rather than at listing.LIMIT, because every language
# below is longer than English. Measured over 4.15.0: French came back a quarter
# longer, German and Spanish a fifth, and 390 characters of English became 491 of
# French - nine short of what play takes. Written at 497, as 4.15.0 first was,
# there is no translation of it that fits at all.
ENGLISH_BUDGET = 400

# What each locale directory is asking to be written in. A locale with no name
# here is refused rather than guessed at, since the guess would be uploaded.
LANGUAGES = {
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

APP = (
    "OpenDocument Reader, an Android app for reading and editing documents made "
    "with LibreOffice and OpenOffice"
)

ENGLISH_PROMPT = """You are writing the Google Play "What's new" text for version {version} of {app}.

Below is the developer-facing changelog of this release, and the text that was written for the release before it.

Write the same release for the people who use the app:
- one paragraph per change, separated by a blank line, matching the sample
- four paragraphs at most; leave out anything nobody would notice from the outside
- plain words. No jargon, no version numbers, no names of internals, nothing that reads like marketing
- say what is different for them, not what was implemented
- no bullets, no dashes at the start of a line

**The whole text must be under {budget} characters.** Play refuses 500, and this is translated into fifteen languages that are all longer than English. Count as you write and cut the least interesting paragraph rather than run over.

Reply with those paragraphs and nothing else.

<changelog>
{section}
</changelog>

<previous-release>
{previous}
</previous-release>
"""

TRANSLATION_PROMPT = """You are translating the Google Play "What's new" text of {app} into {language}, for its {locale} listing.

The app has said things in {language} before. Its store description is below, and so are the notes of an earlier release where there are any. Use the words they use for the parts of the app - document, page, search, edit, save - rather than translating the English afresh.

- one paragraph out for every paragraph in, in the same order, blank line between them
- write it as {language} is written, not as English word order carried over
- leave "OpenDocument Reader" and the format names (ODT, ODS, ODP, PDF, DOCX) alone

**The whole text must be under {limit} characters.** The English below is {length}. Play refuses anything longer and the release cannot go out. Where {language} needs more room than English, say the same thing in fewer words - do not drop a paragraph.

Reply with the translated paragraphs and nothing else.

<english>
{english}
</english>

<store-description>
{description}
</store-description>

<earlier-release>
{previous}
</earlier-release>
"""

REVIEW_PROMPT = """You are a {language} speaker reading the Google Play "What's new" text of {app} before it goes out in the {locale} listing. It has been translated from the English below, and you are the last person to see it.

Change a paragraph when it says something the English does not, when it drops something the English says, or when it reads like translated English rather than {language}: a word borrowed as it sounds rather than as it means, English word order carried over, an English word left standing where {language} has an ordinary one of its own, or a word nobody would use for that part of the app. The store description below is how the app already speaks {language}; a paragraph should not contradict it.

This is read by someone using the app, not building it, so a word out of the workshop - engine, render, parser - is wrong even where it is accurate.

Leave alone a paragraph that is already right. A rewrite that is only different is worse than no rewrite.

**Your reply must stay under {limit} characters**, which is what Play takes.

Reply with the paragraphs as they should go out - the ones you changed and the ones you did not - and nothing else. One paragraph for every paragraph in the English, in the same order, blank line between them.

<english>
{english}
</english>

<translation>
{draft}
</translation>

<store-description>
{description}
</store-description>
"""


def paragraphs(text):
    return [block for block in text.strip().split("\n\n") if block.strip()]


def previous_copy(locale, code):
    """The newest release before this one that has copy in this locale, or ""."""
    folder = listing.METADATA / locale / "changelogs"
    if not folder.is_dir():
        return ""

    earlier = []
    for path in folder.glob("*.txt"):
        if not path.stem.isdigit():
            continue  # README.md and anything else not named after a version code
        if int(path.stem) < code:
            earlier.append((int(path.stem), path))

    if not earlier:
        return ""
    return max(earlier)[1].read_text(encoding="utf-8").strip()


def ask(prompt, model):
    """One agent, one answer. Raises RuntimeError with what the CLI said."""
    result = subprocess.run(
        [
            "claude",
            "--print",
            "--output-format", "text",
            "--model", model,
            # nothing here needs the repository, and a tool call would only be a
            # way for the answer to arrive as something other than the text
            "--disallowed-tools", "Bash,Edit,Write,Read,Glob,Grep,WebFetch,WebSearch,Task",
            "--strict-mcp-config",
            "--no-session-persistence",
        ],
        input=prompt,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"claude exited {result.returncode}")

    answer = result.stdout.strip()
    if not answer:
        raise RuntimeError("claude answered with nothing")
    return answer


def check(text, limit, against=None):
    """What is wrong with a piece of copy, or None."""
    if not text.strip():
        return "it is empty"
    if len(text) > limit:
        return f"it is {len(text)} characters, over the {limit} allowed here"
    if any(line.lstrip().startswith(("- ", "* ", "• ")) for line in text.splitlines()):
        return "it is written as a bullet list, and the house style is paragraphs"
    if against is not None and len(paragraphs(text)) != len(paragraphs(against)):
        return (
            f"it has {len(paragraphs(text))} paragraphs against the English "
            f"text's {len(paragraphs(against))}"
        )
    return None


def write(locale, code, text, dry_run):
    path = listing.copy_path(locale, code)
    if dry_run:
        print(f"\n--- {path.relative_to(ROOT)} ({len(text)} characters)\n{text}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text + "\n", encoding="utf-8")


def produce(prompt, model, attempts, limit, against=None):
    """Ask until the answer holds up. Raises RuntimeError with the last reason."""
    reason = None
    for _ in range(attempts):
        try:
            answer = ask(prompt, model)
        except RuntimeError as failure:
            reason = str(failure)
            continue
        reason = check(answer, limit, against=against)
        if reason is None:
            return answer
    raise RuntimeError(reason)


def english(version, code, model, attempts):
    """The source text: what is on disk, or a fresh one from the changelog."""
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    try:
        section = changelog.section(text, version)
        wrote = version
    except ValueError:
        # the heading is still open, which is where a version being cut sits
        section = changelog.section(text, "Unreleased")
        wrote = "Unreleased"
    print(f"writing the English from the {wrote} section of CHANGELOG.md")

    return produce(
        ENGLISH_PROMPT.format(
            version=version,
            app=APP,
            section=section,
            previous=previous_copy(SOURCE, code),
            budget=ENGLISH_BUDGET,
        ),
        model,
        attempts,
        ENGLISH_BUDGET,
    )


def translate(locale, code, source, model, attempts, review=True):
    # the description is shown to the agent as the app already speaks that
    # language, so the ${...} an app fills in comes out rather than being read
    # as something the listing says
    path = listing.METADATA / locale / "full_description.txt"
    description = listing.fill_in(
        path.read_text(encoding="utf-8"), [], where=path.name
    ).strip()

    draft = produce(
        TRANSLATION_PROMPT.format(
            app=APP,
            language=LANGUAGES[locale],
            locale=locale,
            english=source,
            length=len(source),
            limit=listing.LIMIT,
            description=description,
            previous=previous_copy(locale, code),
        ),
        model,
        attempts,
        listing.LIMIT,
        against=source,
    )
    if not review:
        return draft

    # A second reader of the same language, because what a first draft gets
    # wrong is not something the draft can see: a word borrowed for its sound
    # rather than its sense reads fine to whoever wrote it.
    return produce(
        REVIEW_PROMPT.format(
            app=APP,
            language=LANGUAGES[locale],
            locale=locale,
            english=source,
            draft=draft,
            limit=listing.LIMIT,
            description=description,
        ),
        model,
        attempts,
        listing.LIMIT,
        against=source,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Write the store copy of one release, one agent per language."
    )
    parser.add_argument("version", help="version, e.g. v4.15.0")
    parser.add_argument(
        "--english",
        action="store_true",
        help="rewrite the English text even though there is one",
    )
    parser.add_argument(
        "--locales",
        help="only these, comma separated - to redo one language that came out wrong",
    )
    parser.add_argument("--dry-run", action="store_true", help="print the copy, write nothing")
    parser.add_argument("--model", default="opus", help="model the agents run on")
    parser.add_argument("--jobs", type=int, default=5, help="languages translated at once")
    parser.add_argument("--attempts", type=int, default=2, help="tries per language")
    parser.add_argument(
        "--no-review",
        action="store_false",
        dest="review",
        help="take the first draft, without a second reader of that language",
    )
    args = parser.parse_args(argv)

    try:
        code = listing.version_code(args.version)
        known = listing.locales()
    except (OSError, ValueError) as reason:
        print(reason, file=sys.stderr)
        return 1

    unnamed = [locale for locale in known if locale not in LANGUAGES]
    if unnamed:
        print(
            f"no language written down for {', '.join(unnamed)}: add it to LANGUAGES in "
            f"{Path(__file__).name} rather than let an agent guess",
            file=sys.stderr,
        )
        return 1

    wanted = known
    if args.locales:
        wanted = [locale.strip() for locale in args.locales.split(",") if locale.strip()]
        unknown = [locale for locale in wanted if locale not in known]
        if unknown:
            print(f"no such locale: {', '.join(unknown)}", file=sys.stderr)
            return 1

    source_path = listing.copy_path(SOURCE, code)
    if args.english or not source_path.is_file():
        try:
            source = english(args.version, code, args.model, args.attempts)
        except (RuntimeError, ValueError) as reason:
            print(f"{SOURCE}: {reason}", file=sys.stderr)
            return 1
        write(SOURCE, code, source, args.dry_run)
    else:
        source = source_path.read_text(encoding="utf-8").strip()
        print(f"translating the {source_path.relative_to(ROOT)} already written")
        if len(source) > ENGLISH_BUDGET:
            print(
                f"warning: it is {len(source)} characters, and play stops at "
                f"{listing.LIMIT}. Languages that need more room than English - "
                f"German, Russian, Polish - may not fit. Rewrite it under "
                f"{ENGLISH_BUDGET} with --english if they come back over.",
                file=sys.stderr,
            )

    targets = [
        locale
        for locale in wanted
        if locale != SOURCE
        and (args.english or args.locales or not listing.copy_path(locale, code).is_file())
    ]
    if not targets:
        print(f"every locale already has copy for {code}")
        return 0

    print(f"translating into {', '.join(targets)}")

    failed = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        running = {
            pool.submit(
                translate, locale, code, source, args.model, args.attempts, args.review
            ): locale
            for locale in targets
        }
        for done in concurrent.futures.as_completed(running):
            locale = running[done]
            try:
                write(locale, code, done.result(), args.dry_run)
            except (RuntimeError, OSError) as reason:
                failed.append(locale)
                print(f"{locale}: {reason}", file=sys.stderr)
            else:
                print(f"{locale} done")

    if failed:
        print(
            f"\n{len(failed)} came back wrong. Run again with "
            f"--locales {','.join(failed)} to redo only those.",
            file=sys.stderr,
        )
        return 1

    print("\nread the diff before committing it - it goes to the store as written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
