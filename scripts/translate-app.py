#!/usr/bin/env python3
#
# Translates the app's own strings, one agent per language.
#
#   scripts/translate-app.py                   fill in what is missing
#   scripts/translate-app.py --languages de,fr only these
#   scripts/translate-app.py --all             translate everything again
#   scripts/translate-app.py --dry-run         print it, write nothing
#
# `values/strings.xml` is the source. Every other `values-<language>/strings.xml`
# is written from it: the keys it has, in the order it has them, and nothing else.
# A key that leaves the source leaves every translation with it, which is what the
# `toast_error_generic` left behind in fifteen languages was.
#
# One `claude -p` per language, given the whole English file rather than the lines
# being translated - the comments in it say what a string is for, and "Support us"
# is a different sentence on a button than in a sentence. A second agent of the
# same language then reads the whole file back against the English.
#
# There is no region qualifier: `values-de` answers for every German-speaking
# region and nothing here differs by one. Crowdin used to write `values-de-rDE`
# beside it and the two drifted apart; see git history.
#
# The values arrive as plain text and the escaping is put on here, so an agent
# cannot write an apostrophe that stops the resource compiling.

import argparse
import concurrent.futures
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "values" / "strings.xml"

# What each `values-` directory is asking to be written in. A directory with no
# language here is refused rather than guessed at, since the guess would ship.
LANGUAGES = {
    "ca": "Catalan",
    "cs": "Czech",
    "da": "Danish",
    "de": "German",
    "es": "Spanish",
    "et": "Estonian",
    "fr": "French",
    "ga": "Irish",
    "hi": "Hindi",
    "it": "Italian",
    "ja": "Japanese",
    "ko": "Korean",
    "pl": "Polish",
    "pt": "Portuguese, as written in Brazil",
    "ru": "Russian",
    "sl": "Slovenian",
    "sv": "Swedish",
    "tr": "Turkish",
    "zh": "Chinese, simplified",
}

APP = (
    "OpenDocument Reader, an Android app for reading and editing documents made "
    "with LibreOffice and OpenOffice. It opens a document, shows it, and can read "
    "it aloud, search it, print it and edit its text."
)

TRANSLATE_PROMPT = """You are translating the interface of {app} into {language}.

Below is the whole English strings file. Read it for context - the comments say what a string is for, and the same English word is a different {language} word on a button than in a sentence.

Translate the {count} strings listed under <wanted>. {have}

- use the words {language} speakers actually see in their own phone's interface for open, save, edit, search, print, page, document, settings, cancel
- a button label stays a button label: as short as the English, and in whatever form {language} puts on a button
- leave "OpenDocument Reader", "ODR Pro", "Pro", "GitHub", "LibreOffice", "OpenOffice", the file extensions and the email address support@opendocument.app exactly as they are
- write plain text. No XML, no escaping, no backslashes - apostrophes and quotes are written normally and handled afterwards
- "…" stays "…"

Reply with a JSON object mapping each name under <wanted> to its {language} text, and nothing else. No markdown fence, no commentary.

<english-file>
{english}
</english-file>

<wanted>
{wanted}
</wanted>
{existing}
"""

REVIEW_PROMPT = """You are a {language} speaker going over the interface of {app} before it ships. It has been translated from the English below, and you are the last person to see it.

Change a string when it says something the English does not, when it reads like translated English rather than {language}, or when it is not the word this language's phones use for that thing: a word borrowed as it sounds rather than as it means, English word order carried over, an English word left standing where {language} has an ordinary one, or a term nobody would use for that part of an app. Watch for a label that has quietly become a sentence, and for a button whose text no longer fits on a button.

These are read by someone using the app, not building it, so a word out of the workshop - render, parse, engine, cache - is wrong even where it is accurate.

Leave alone a string that is already right. A rewrite that is only different is worse than no rewrite.

Reply with a JSON object holding **every** name below and the text as it should ship - the ones you changed and the ones you did not. Nothing else, no markdown fence. Plain text values, no XML and no backslashes.

<english>
{english}
</english>

<translation>
{draft}
</translation>
"""

# What must survive translation untouched: say it in the prompt, then check it.
KEPT = re.compile(r"support@opendocument\.app|OpenDocument Reader|ODR Pro|GitHub|LibreOffice|OpenOffice")


def read_source():
    """key -> English text, in file order, skipping what is not translated."""
    text = SOURCE.read_text(encoding="utf-8")
    found = {}
    for match in re.finditer(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', text, re.S):
        name, attrs, value = match.groups()
        if 'translatable="false"' in attrs:
            continue
        found[name] = unescape(value)
    return found


def read_translation(language):
    path = RES / f"values-{language}" / "strings.xml"
    if not path.is_file():
        return {}
    text = path.read_text(encoding="utf-8")
    return {
        name: unescape(value)
        for name, value in re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S)
    }


def unescape(value):
    value = value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return re.sub(r"\\(['\"@?\\])", r"\1", value)


def escape(value):
    """What aapt needs. The order matters: the ampersand first, or it eats its own."""
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return value.replace("\\", "\\\\").replace("'", r"\'").replace('"', r"\"")


def write(language, order, values):
    path = RES / f"values-{language}" / "strings.xml"
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key in order:
        if key in values:
            lines.append(f'    <string name="{key}">{escape(values[key])}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def ask(prompt, model):
    result = subprocess.run(
        [
            "claude",
            "--print",
            "--output-format", "text",
            "--model", model,
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
    if not result.stdout.strip():
        raise RuntimeError("claude answered with nothing")
    return result.stdout


def parse(answer, wanted, english):
    """The JSON object out of an answer, checked. Raises RuntimeError with what is wrong."""
    text = answer.strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", text, re.S)
    if fence:
        text = fence.group(1).strip()
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < start:
        raise RuntimeError("no JSON object in the answer")

    try:
        got = json.loads(text[start : end + 1])
    except json.JSONDecodeError as reason:
        raise RuntimeError(f"the answer is not JSON: {reason}") from reason
    if not isinstance(got, dict):
        raise RuntimeError("the answer is not a JSON object")

    missing = [key for key in wanted if key not in got]
    if missing:
        raise RuntimeError(f"nothing for {', '.join(missing[:5])}")
    extra = [key for key in got if key not in wanted]
    if extra:
        raise RuntimeError(f"invented {', '.join(extra[:5])}")

    for key in wanted:
        value = got[key]
        if not isinstance(value, str) or not value.strip():
            raise RuntimeError(f"{key} came back empty")
        if "\n" in value:
            raise RuntimeError(f"{key} came back as more than one line")
        # a name the English keeps has to survive being translated around
        for keep in set(KEPT.findall(english[key])):
            if keep not in value:
                raise RuntimeError(f"{key} lost {keep!r}")

    return {key: got[key].strip() for key in wanted}


def produce(prompt, model, attempts, wanted, english):
    reason = None
    for _ in range(attempts):
        try:
            return parse(ask(prompt, model), wanted, english)
        except RuntimeError as failure:
            reason = str(failure)
    raise RuntimeError(reason)


def translate(language, english, wanted, model, attempts, review):
    have = read_translation(language)
    kept = {key: value for key, value in have.items() if key in english and key not in wanted}

    if wanted:
        existing = ""
        if kept:
            existing = (
                f"\n<already-translated>\nThese are already in {LANGUAGES[language]}. "
                "Match their wording and register; do not repeat them in your reply.\n"
                + json.dumps(kept, ensure_ascii=False, indent=1)
                + "\n</already-translated>\n"
            )
        fresh = produce(
            TRANSLATE_PROMPT.format(
                app=APP,
                language=LANGUAGES[language],
                count=len(wanted),
                have=(
                    f"The other {len(kept)} are already translated and shown at the end."
                    if kept
                    else "Nothing in this app has been translated into it yet."
                ),
                english=SOURCE.read_text(encoding="utf-8"),
                wanted="\n".join(f"{key}: {english[key]}" for key in wanted),
                existing=existing,
            ),
            model,
            attempts,
            wanted,
            english,
        )
        kept.update(fresh)

    if not review:
        return kept

    # A second reader of the same language, over the whole file: what a first draft
    # gets wrong is not something the draft can see, and a string translated three
    # years ago beside one translated today is where the two stop agreeing.
    every = [key for key in english if key in kept]
    return produce(
        REVIEW_PROMPT.format(
            app=APP,
            language=LANGUAGES[language],
            english=json.dumps({key: english[key] for key in every}, ensure_ascii=False, indent=1),
            draft=json.dumps({key: kept[key] for key in every}, ensure_ascii=False, indent=1),
        ),
        model,
        attempts,
        every,
        english,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Translate the app's strings, one agent per language."
    )
    parser.add_argument("--languages", help="only these, comma separated")
    parser.add_argument(
        "--all", action="store_true", help="translate every string again, not only the missing ones"
    )
    parser.add_argument("--dry-run", action="store_true", help="print what would be written")
    parser.add_argument("--model", default="opus", help="model the agents run on")
    parser.add_argument("--jobs", type=int, default=5, help="languages at once")
    parser.add_argument("--attempts", type=int, default=3, help="tries per language")
    parser.add_argument(
        "--no-review",
        action="store_false",
        dest="review",
        help="take the first draft, without a second reader of that language",
    )
    args = parser.parse_args(argv)

    english = read_source()
    order = list(english)
    print(f"{len(order)} translatable strings in {SOURCE.relative_to(ROOT)}")

    found = sorted(
        folder.name.removeprefix("values-")
        for folder in RES.glob("values-*")
        if (folder / "strings.xml").is_file() and folder.name.removeprefix("values-") in LANGUAGES
    )
    unnamed = sorted(
        folder.name.removeprefix("values-")
        for folder in RES.glob("values-*")
        if (folder / "strings.xml").is_file()
        and folder.name.removeprefix("values-") not in LANGUAGES
        and folder.name.removeprefix("values-") not in ("night", "v35")
    )
    if unnamed:
        print(
            f"no language written down for {', '.join(unnamed)}: add it to LANGUAGES in "
            f"{Path(__file__).name} rather than let an agent guess",
            file=sys.stderr,
        )
        return 1

    wanted = found
    if args.languages:
        wanted = [name.strip() for name in args.languages.split(",") if name.strip()]
        unknown = [name for name in wanted if name not in LANGUAGES]
        if unknown:
            print(f"no such language: {', '.join(unknown)}", file=sys.stderr)
            return 1

    work = {}
    for language in wanted:
        have = read_translation(language)
        missing = [key for key in order if key not in have] if not args.all else list(order)
        stale = [key for key in have if key not in english]
        if missing or stale or args.all:
            work[language] = missing
        if stale:
            print(f"values-{language}: dropping {', '.join(stale)}")

    if not work:
        print("every language has every string")
        return 0

    for language, missing in sorted(work.items()):
        print(f"values-{language:3} {len(order) - len(missing):3}/{len(order)} translated")

    failed = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        running = {
            pool.submit(
                translate, language, english, missing, args.model, args.attempts, args.review
            ): language
            for language, missing in work.items()
        }
        for done in concurrent.futures.as_completed(running):
            language = running[done]
            try:
                values = done.result()
            except (RuntimeError, OSError) as reason:
                failed.append(language)
                print(f"{language}: {reason}", file=sys.stderr)
                continue
            if args.dry_run:
                print(f"\n--- values-{language} ({len(values)} strings)")
                for key in order:
                    if key in values:
                        print(f"  {key}: {values[key]}")
            else:
                write(language, order, values)
            print(f"{language} done, {len(values)}/{len(order)}")

    if failed:
        print(
            f"\n{len(failed)} came back wrong. Run again with "
            f"--languages {','.join(failed)} to redo only those.",
            file=sys.stderr,
        )
        return 1

    print("\nread the diff before committing it - this is what the app shows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
