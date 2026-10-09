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
