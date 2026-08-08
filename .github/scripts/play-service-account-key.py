#!/usr/bin/env python3
#
# Reads the play console service account key out of the environment and writes a
# normalised copy of it to the given path.
#
# fastlane's own "doesn't seem to be a JSON file" comes at the upload, the one step
# of a release that cannot be re-run - play refuses a code it has already accepted.
# So the key is checked here, up front, and with an error that names the problem.
#
# Base64 (how the keystore secret is stored, so an easy mistake), a BOM or clipboard
# whitespace, and hand-unescaped private_key line breaks are all repaired rather than
# reported. The key comes in through the environment and is never printed.

import argparse
import base64
import json
import os
import sys

REQUIRED_FIELDS = ("type", "client_email", "private_key")


def fail(message):
    if os.environ.get("GITHUB_ACTIONS"):
        # ::error:: also annotates the run itself
        print(f"::error::{message}")
    else:
        print(message, file=sys.stderr)
    return 1


def parse(text, strict=True):
    try:
        return json.loads(text, strict=strict)
    except ValueError:
        return None


def read_key(raw, log=print):
    """The key as a dict, from json, base64 of json, or json with raw newlines."""
    raw = raw.strip().lstrip("\ufeff")

    key = parse(raw)
    if key is not None:
        return key

    # the line breaks a wrapping encoder leaves behind go first, since validate
    # rejects everything outside the alphabet
    try:
        key = parse(base64.b64decode("".join(raw.split()), validate=True).decode("utf-8"))
    except Exception:
        key = None
    if key is not None:
        log("the key is base64 encoded, decoding it")
        return key

    # json has no way to hold a real line break inside a string, so a private_key
    # that was re-wrapped by hand does not parse. reading it leniently is enough
    # to repair it: json.dump writes those breaks back out escaped
    key = parse(raw, strict=False)
    if key is not None:
        log("the key has unescaped line breaks in it, escaping them")
    return key


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Write a checked, normalised copy of the play service account key."
    )
    parser.add_argument("output", help="path to write the normalised key to")
    parser.add_argument(
        "--variable",
        default="GOOGLE_PLAY_SERVICE_ACCOUNT",
        help="environment variable holding the key (default: %(default)s)",
    )
    args = parser.parse_args(argv)

    raw = os.environ.get(args.variable)
    if not raw:
        return fail(f"{args.variable} is not set")

    key = read_key(raw)
    if not isinstance(key, dict):
        return fail(
            f"{args.variable} is neither a service account json nor base64 of one. "
            "Paste the file the play console handed out whole, without re-encoding "
            "or reformatting it."
        )

    missing = [field for field in REQUIRED_FIELDS if not key.get(field)]
    if missing:
        return fail(
            f"{args.variable} parses as json but is missing {', '.join(missing)} - "
            "it is not a service account key"
        )
    if key["type"] != "service_account":
        return fail(f"{args.variable} is a '{key['type']}' key. supply needs a service_account one.")

    # 0600 and os.open rather than a plain write: the file is a credential, and
    # on a shared checkout the default mode would hand it to everyone
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as out:
        json.dump(key, out)
    print(f"service account key written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
