#!/usr/bin/env python3
#
# Works out which version a release run is building, and refuses the runs that
# cannot sensibly build one.
#
# There is no version number in the repository: it is the git tag, which
# app/build.gradle turns into a version name and a version code (v4.8.0 -> 4.8.0
# and 40800, two digits per part). What is left to decide is which string gradle
# is handed, and that is only interesting when the run has no tag to read:
#
#   tag push                   the tag, and the version input has to agree with
#                              it or stay empty - the apk of a run is attached to
#                              the release of the tag it ran on, so building
#                              anything else would file it there under the wrong
#                              version
#   dispatched off a branch    the version input, which is how a release whose
#                              upload half failed gets finished off the branch it
#                              was cut from
#   neither                    only a dry run, on gradle's unversioned fallback.
#                              uploading that would mean uploading a version code
#                              the store refuses, six minutes into the run
#
# The shape is checked here rather than left to gradle, which checks it again and
# is the one that counts: a typo in a dispatched version should not cost the
# gradle setup first. The version code is deliberately not computed here - one
# derivation of it, in the build, is enough.
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


def resolve(tag, given, uploads, log=print):
    """The version to build, or "" for none. Raises ValueError with the reason."""
    tag, given = tag.strip(), given.strip()

    if tag and given and given.removeprefix("v") != tag.removeprefix("v"):
        raise ValueError(
            f"the version input ({given}) is not the tag this ran on ({tag}). "
            "leave it blank to build the tag."
        )

    version = tag or given
    if not version:
        if uploads != "none":
            raise ValueError(
                "nothing to take a version from. push this as a v* tag, dispatch "
                "it on one, or fill in the version input."
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
    parser.add_argument("--tag", default="", help="tag the run was triggered by, if any")
    parser.add_argument("--input", default="", help="version input of a dispatched run")
    parser.add_argument(
        "--uploads",
        default="none",
        help="what the run publishes; only 'none' may go without a version",
    )
    args = parser.parse_args(argv)

    try:
        version = resolve(args.tag, args.input, args.uploads)
    except ValueError as reason:
        return fail(str(reason))

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as out:
            out.write(f"version={version}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
