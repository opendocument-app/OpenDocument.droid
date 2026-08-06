#!/usr/bin/env python3
#
# Prints the CHANGELOG.md section for one version, and fails when there is none.
#
# The release run reads it before building, so missing release copy costs seconds
# rather than a version, and again in the record job, where it becomes the body of
# the drafted github release. Being read is what stops the changelog rotting.
#
# OpenDocument.ios has the same script against `## [1.37] - 2026-08-02` headings.

import argparse
import os
import re
import sys

# `## 4.13.0`, not `###`, which belongs to whichever section it sits in
HEADING = re.compile(r"^## +(.+?)\s*$")


def section(text, version):
    """The body under `## <version>`. Raises ValueError if it is missing or empty."""
    # an optional v, so the workflow can hand its input straight over
    wanted = version.strip().removeprefix("v")

    found = False
    collecting = False
    body = []
    for line in text.splitlines():
        heading = HEADING.match(line)
        if heading:
            if collecting:
                break
            if heading.group(1).removeprefix("v") == wanted:
                found = collecting = True
            continue
        if collecting:
            body.append(line)

    if not found:
        raise ValueError(
            f"CHANGELOG.md has no '## {wanted}' section. Cut the Unreleased heading "
            f"to '## {wanted}' before releasing it - that copy is the release body."
        )

    body = "\n".join(body).strip("\n")
    if not body.strip():
        raise ValueError(
            f"the '## {wanted}' section of CHANGELOG.md is empty. A release with "
            "nothing user facing in it should say so rather than nothing."
        )
    return body


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Print the CHANGELOG.md section of one version."
    )
    parser.add_argument("--version", required=True, help="version to look up, e.g. v4.8.0")
    parser.add_argument("--file", default="CHANGELOG.md", help="changelog to read")
    args = parser.parse_args(argv)

    try:
        with open(args.file) as changelog:
            print(section(changelog.read(), args.version))
    except (OSError, ValueError) as reason:
        # as in resolve-version.py: the annotation form only counts on stdout
        if os.environ.get("GITHUB_ACTIONS"):
            print(f"::error::{reason}")
        else:
            print(reason, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
