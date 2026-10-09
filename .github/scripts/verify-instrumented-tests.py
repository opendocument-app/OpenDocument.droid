#!/usr/bin/env python3
"""Reject Android test runs that produced no results or only skipped tests."""

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def assumption_skipped(case):
    # AGP can encode JUnit assumptions as <failure> instead of <skipped>.
    failure = case.find("failure")
    if failure is None:
        return False
    kinds = ("org.junit.AssumptionViolatedException", "org.junit.internal.AssumptionViolatedException")
    return failure.get("type") in kinds or (failure.text or "").startswith(
        tuple(kind + ":" for kind in kinds)
    )


def verify(results, flavors):
    counts = {}
    for flavor in flavors:
        reports = list((results / flavor).rglob("TEST-*.xml"))
        if not reports:
            raise ValueError(f"{flavor}: no Android test reports")
        executed = 0
        for report in reports:
            root = ET.parse(report).getroot()
            for suite in root.iter("testsuite"):
                assumptions = sum(assumption_skipped(case) for case in suite.iter("testcase"))
                if int(suite.get("failures", 0)) > assumptions or int(suite.get("errors", 0)):
                    raise ValueError(f"{flavor}: failed tests in {report.name}")
            for case in root.iter("testcase"):
                if assumption_skipped(case) and case.find("error") is None:
                    continue
                if case.find("failure") is not None or case.find("error") is not None:
                    raise ValueError(f"{flavor}: failed test {case.get('name')}")
                executed += case.find("skipped") is None
        if not executed:
            raise ValueError(f"{flavor}: no tests executed")
        counts[flavor] = executed
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path,
                        default=Path("app/build/outputs/androidTest-results/connected/debug/flavors"))
    parser.add_argument("--flavors", nargs="+", default=["pro", "lite", "foss"])
    args = parser.parse_args()
    try:
        for flavor, count in verify(args.results, args.flavors).items():
            print(f"{flavor}: {count} Android tests executed")
    except (OSError, ValueError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
