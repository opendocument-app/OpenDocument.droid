import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "results", Path(__file__).with_name("verify-instrumented-tests.py")
)
results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)


class InstrumentedResultsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def report(self, flavor, body):
        directory = self.root / flavor
        directory.mkdir(exist_ok=True)
        (directory / "TEST-device.xml").write_text(body)

    def test_requires_every_flavor(self):
        self.report("pro", '<testsuite><testcase name="passed"/></testsuite>')
        with self.assertRaisesRegex(ValueError, "lite: no Android test reports"):
            results.verify(self.root, ["pro", "lite"])

    def test_rejects_empty_and_skipped_suites(self):
        for body in ['<testsuite/>', '<testsuite><testcase><skipped/></testcase></testsuite>']:
            with self.subTest(body=body):
                self.report("pro", body)
                with self.assertRaisesRegex(ValueError, "no tests executed"):
                    results.verify(self.root, ["pro"])

    def test_rejects_failure_and_runner_error(self):
        for body in [
            '<testsuite failures="1"><testcase/></testsuite>',
            '<testsuite errors="1"/>',
            '<testsuite><testcase><failure/></testcase></testsuite>',
            '<testsuite><testcase><error/></testcase></testsuite>',
        ]:
            with self.subTest(body=body):
                self.report("pro", body)
                with self.assertRaises(ValueError):
                    results.verify(self.root, ["pro"])

    def test_counts_only_executed_cases(self):
        self.report("pro", '<testsuites><testsuite><testcase/><testcase><skipped/></testcase>'
                           '</testsuite><testsuite><testcase/></testsuite></testsuites>')
        self.assertEqual({"pro": 2}, results.verify(self.root, ["pro"]))

    def test_agp_assumption_failures_count_as_skips(self):
        assumption = ('<testcase><failure>org.junit.AssumptionViolatedException: '
                      'not a screenshot run</failure></testcase>')
        self.report("pro", '<testsuite failures="1"><testcase/>' + assumption + '</testsuite>')
        self.assertEqual({"pro": 1}, results.verify(self.root, ["pro"]))
        self.report("pro", '<testsuite failures="1">' + assumption + '</testsuite>')
        with self.assertRaisesRegex(ValueError, "no tests executed"):
            results.verify(self.root, ["pro"])

    def test_rejects_malformed_reports(self):
        self.report("pro", '<testsuite>')
        with self.assertRaises(results.ET.ParseError):
            results.verify(self.root, ["pro"])

    def test_summary_includes_assertion_and_counts(self):
        self.report("foss", '''<testsuites><testsuite failures="1">
            <testcase name="passed"/>
            <testcase name="ignored"><skipped/></testcase>
            <testcase classname="MainActivityTests" name="palette">
                <failure>java.lang.AssertionError: expected #000000
at MainActivityTests.kt:618</failure>
            </testcase></testsuite></testsuites>''')
        summary = results.summarize(self.root, ["foss"])
        self.assertIn("| foss | 1 | 1 | 1 |", summary)
        self.assertIn("foss: MainActivityTests.palette", summary)
        self.assertIn("java.lang.AssertionError: expected #000000", summary)
        self.assertIn("MainActivityTests.kt:618", summary)

    def test_summary_handles_missing_malformed_and_runner_reports(self):
        self.report("pro", '<testsuite>')
        self.report("foss", '<testsuite errors="1"/>')
        summary = results.summarize(self.root, ["pro", "lite", "foss"])
        self.assertIn("Cannot read TEST-device.xml", summary)
        self.assertIn("No test reports were produced", summary)
        self.assertIn("suite reports a failure or runner error", summary)
        self.assertIn("No tests executed", summary)

    def test_summary_treats_assumptions_as_skips_but_preserves_errors(self):
        self.report("pro", '''<testsuite failures="2" errors="1">
            <testcase><failure type="org.junit.AssumptionViolatedException"/></testcase>
            <testcase name="runner"><failure>org.junit.AssumptionViolatedException: skip</failure>
                <error message="runner stopped"/></testcase>
            </testsuite>''')
        summary = results.summarize(self.root, ["pro"])
        self.assertIn("| pro | 0 | 1 | 1 |", summary)
        self.assertIn("runner stopped", summary)
        self.assertNotIn("org.junit.AssumptionViolatedException", summary)

    def test_summary_escapes_report_text(self):
        self.report("pro", '''<testsuite><testcase name="&lt;b&gt;test&lt;/b&gt;">
            <failure message="expected &lt;script&gt;">&lt;/pre&gt;&lt;img src=x&gt;</failure>
            </testcase></testsuite>''')
        summary = results.summarize(self.root, ["pro"])
        self.assertIn("&lt;b&gt;test&lt;/b&gt;", summary)
        self.assertIn("expected &lt;script&gt;", summary)
        self.assertIn("&lt;/pre&gt;&lt;img src=x&gt;", summary)
        self.assertNotIn("<img", summary)

    def test_summary_bounds_long_failures(self):
        case = '<testcase name="failed"><failure>' + ('x' * 5000) + '</failure></testcase>'
        self.report("pro", '<testsuite>' + case * 51 + '</testsuite>')
        summary = results.summarize(self.root, ["pro"])
        self.assertEqual(50, summary.count("<details>"))
        self.assertIn("1 more failures or report problems", summary)
        self.assertNotIn('x' * 4001, summary)
