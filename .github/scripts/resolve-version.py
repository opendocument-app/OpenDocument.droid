#!/usr/bin/env python3
#
# Works out which version a release run is building, from the run's `version` input,
# and refuses the runs that cannot sensibly build one. Only a dry run may go without
# one, on gradle's unversioned fallback - uploading that means a code the store refuses.
#
# gradle checks the shape again and is the one that counts; checking it here as well
# only saves a typo the six minutes of a build. The version code is left to gradle -
# one derivation of it is enough. OpenDocument.ios has the same script.
#
# Prints the resolved version and writes it to GITHUB_OUTPUT as `version`, empty
# when there is none. Run it by hand to see what a dispatch would build.

import argparse
import os
import re
import sys

# what app/build.gradle accepts: an optional v, all three parts, each below 100 so
# that two digits per part stays unambiguous. Two-part versions were once padded
# with a zero, which let one build be tagged under two names; the build refuses
# them now, and this has to refuse the same ones or it stops being a check
VERSION = re.compile(r"^v?[0-9]{1,2}(\.[0-9]{1,2}){2}$")


def fail(message):
    if os.environ.get("GITHUB_ACTIONS"):
        # shown in the log the same way, and additionally as an annotation on the
        # run itself rather than only somewhere in the middle of a step
        print(f"::error::{message}")
    else:
        print(message, file=sys.stderr)
    return 1


def boolean(value):
    """A workflow input as it reaches a shell: the string "true" or "false"."""
    if value.strip().lower() in ("true", "1"):
        return True
    if value.strip().lower() in ("false", "0", ""):
        return False
    raise ValueError(f"'{value}' is not true or false")


def resolve(given, dry_run, log=print):
    """The version to build, or "" for none. Raises ValueError with the reason."""
    version = given.strip()

    if not version:
        if not dry_run:
            raise ValueError(
                "nothing to take a version from: fill in the version input, or "
                "tick dry_run to build without uploading."
            )
        log("no version given - building gradle's unversioned fallback")
        return ""

    if not VERSION.match(version):
        raise ValueError(
            f"'{version}' is not a version: expected something like v4.8.0 - all "
            "three parts, each below 100"
        )
    log(f"building {version}")
    return version


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Resolve the version a release run builds."
    )
    parser.add_argument("--input", default="", help="version input of the run")
    parser.add_argument(
        "--dry-run",
        default="false",
        help="whether the run publishes nothing; only a dry run may go without a version",
    )
    args = parser.parse_args(argv)

    try:
        version = resolve(args.input, boolean(args.dry_run))
    except ValueError as reason:
        return fail(str(reason))

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as out:
            out.write(f"version={version}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
