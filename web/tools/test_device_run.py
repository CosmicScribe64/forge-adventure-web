"""device-run.py without a device: the probe is injected once, with the harmless-error list read from webtest.py."""
import importlib.util
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("device_run", os.path.join(HERE, "device-run.py"))
device_run = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device_run)


class Args:
    cap = 2.0
    platform = "none"


class DeviceRunTest(unittest.TestCase):
    def setUp(self):
        device_run.A = Args()

    def test_allowed_errors_come_from_webtest(self):
        allowed = device_run.allowed_errors()
        self.assertTrue(allowed and all(isinstance(p, str) for p in allowed))

    def test_probe_goes_first_in_head_once(self):
        html = b"<html><head><title>x</title></head><body><head></body></html>"
        out = device_run.inject(html)
        self.assertEqual(out.count(b"window.__deviceProbe="), 1)
        self.assertTrue(out.startswith(b"<html><head><script>window.__deviceProbe="))
        self.assertTrue(out.endswith(b"<body><head></body></html>"))

    def test_probe_is_is_one_iife(self):
        js = open(os.path.join(HERE, "device-probe.js")).read()
        self.assertTrue(js.lstrip().startswith("//") and js.rstrip().endswith("})();"))
        self.assertIn("window.forgeTest.cmd", js)


if __name__ == "__main__":
    unittest.main()
