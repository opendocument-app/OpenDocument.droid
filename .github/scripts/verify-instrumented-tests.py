#!/usr/bin/env python3
"""Reject Android test runs that produced no results or only skipped tests."""

import argparse
from html import escape
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ASSUMPTIONS = (
    "org.junit.AssumptionViolatedException",
    "org.junit.internal.AssumptionViolatedException",
)


def assumption_skipped(case):
    # AGP can encode JUnit assumptions as <failure> instead of <skipped>.
    failure = case.find("failure")
    return failure is not None and (
        failure.get("type") or (failure.text or "").split(":", 1)[0]
    ) in ASSUMPTIONS


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


def summarize(results, flavors):
    """Describe every flavor, including partial reports from an unsuccessful Gradle run."""
    lines = ["## Android device tests", "",
             "| Flavor | Passed | Failed | Skipped |", "| --- | ---: | ---: | ---: |"]
    details = []
    for flavor in flavors:
        reports = sorted((results / flavor).rglob("TEST-*.xml"))
        passed = failed = skipped = 0
        problems = []
        for report in reports:
            try:
                root = ET.parse(report).getroot()
            except (OSError, ET.ParseError) as error:
                problems.append(f"Cannot read {report.name}: {error}")
                continue
            report_failures = 0
            for case in root.iter("testcase"):
                errors = list(case.findall("error"))
                if not assumption_skipped(case):
                    errors.extend(case.findall("failure"))
                if errors:
                    failed += 1
                    report_failures += 1
                    name = ".".join(filter(None, [case.get("classname"), case.get("name")]))
                    message = "\n\n".join(
                        "\n".join(filter(None, [error.get("message"), error.text]))
                        or error.get("type") or "No failure details recorded."
                        for error in errors
                    )
                    details.append((f"{flavor}: {name or 'unnamed test'}", message))
                elif case.find("skipped") is not None or assumption_skipped(case):
                    skipped += 1
                else:
                    passed += 1
            # Runner failures may only have suite counters, with no failed testcase.
            if not report_failures:
                for suite in root.iter("testsuite"):
                    try:
                        assumptions = sum(assumption_skipped(c) for c in suite.iter("testcase"))
                        has_failure = (int(suite.get("failures", 0)) > assumptions
                                       or int(suite.get("errors", 0)) > 0)
                    except ValueError:
                        problems.append(f"Invalid suite counts in {report.name}")
                        break
                    if has_failure:
                        problems.append(f"{report.name}: suite reports a failure or runner error "
                                        "without a failed test case. See the test artifacts.")
                        break
        label = escape(flavor).replace("|", "&#124;")
        lines.append(f"| {label} | {passed} | {failed} | {skipped} |")
        if not reports:
            problems.append("No test reports were produced. Check the emulator and Gradle logs.")
        elif passed + failed == 0:
            problems.append("No tests executed.")
        details.extend((f"{flavor}: report problem", problem) for problem in problems)
    lines.append("")
    for title, message in details[:50]:
        if len(message) > 4000:
            message = message[:4000] + "\n… See the test artifacts for the full failure."
        lines.extend([f"<details><summary>{escape(title)}</summary>", "",
                      f"<pre>{escape(message)}</pre>", "", "</details>", ""])
    if len(details) > 50:
        lines.append(f"{len(details) - 50} more failures or report problems; see the test artifacts.")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path,
                        default=Path("app/build/outputs/androidTest-results/connected/debug/flavors"))
    parser.add_argument("--flavors", nargs="+", default=["pro", "lite", "foss"])
    parser.add_argument("--summary", action="store_true",
                        help="Print a Markdown summary without changing test validation")
    args = parser.parse_args()
    if args.summary:
        print(summarize(args.results, args.flavors))
        return 0
    try:
        for flavor, count in verify(args.results, args.flavors).items():
            print(f"{flavor}: {count} Android tests executed")
    except (OSError, ValueError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
