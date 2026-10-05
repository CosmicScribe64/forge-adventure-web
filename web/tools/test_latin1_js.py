"""Tests for latin1-js.py. Run: python3 -m unittest discover -s web/tools -p 'test_*.py'"""
import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("latin1_js", os.path.join(os.path.dirname(__file__), "latin1-js.py"))
latin1 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(latin1)


def segment(column_delta):
    # generated column, source 0, line 0, column 0: the shape of a minimal mapping
    return latin1.vlq_encode(column_delta) + "AAAA"


def mapping_columns(mappings_line):
    """The absolute generated columns of the segments on one line of a map."""
    cols, absolute = [], 0
    for seg in mappings_line.split(","):
        delta, _ = latin1.vlq_decode_first(seg)
        absolute += delta
        cols.append(absolute)
    return cols


class EscapeTest(unittest.TestCase):
    def test_basic_multilingual_plane_character(self):
        self.assertEqual(latin1.escape("\u20ac"), "\\u20ac")
        self.assertEqual(latin1.escape("\u0100"), "\\u0100")

    def test_astral_character_is_a_surrogate_pair(self):
        self.assertEqual(latin1.escape("\U0001F600"), "\\ud83d\\ude00")
        self.assertEqual(latin1.escape("\U00010000"), "\\ud800\\udc00")
        self.assertEqual(latin1.escape("\U0010FFFF"), "\\udbff\\udfff")

    def test_vlq_round_trip(self):
        for n in (0, 1, -1, 15, 16, -16, 31, 32, 1000, -1000, 123456):
            decoded, used = latin1.vlq_decode_first(latin1.vlq_encode(n) + "AAAA")
            self.assertEqual(decoded, n)
            self.assertEqual(used, len(latin1.vlq_encode(n)))


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def write(self, name, text):
        path = os.path.join(self.dir.name, name)
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text)
        return path

    def read(self, path):
        with open(path, encoding="utf-8", newline="") as f:
            return f.read()

    def run_main(self, text, map_path=None):
        path = self.write("app.js", text)
        with contextlib.redirect_stdout(io.StringIO()):
            latin1.main(path, map_path) if map_path else latin1.main(path)
        return self.read(path)

    def test_latin1_is_left_alone(self):
        text = 'var s = "caf\u00e9 \u00ff \u00a0";\n// \u00b5\n'
        self.assertEqual(self.run_main(text), text)

    def test_ascii_and_line_endings_are_kept(self):
        text = "a\r\nb\n\nc"
        self.assertEqual(self.run_main(text), text)

    def test_characters_above_ff_are_escaped(self):
        out = self.run_main('x = "\u2019s \u0141\u00f3d\u017a";')
        self.assertEqual(out, 'x = "\\u2019s \\u0141\u00f3d\\u017a";')
        out.encode("latin-1")  # every remaining character fits in one byte

    def test_astral_characters_become_surrogate_pairs(self):
        out = self.run_main('x = "\U0001F600";')
        self.assertEqual(out, 'x = "\\ud83d\\ude00";')

    def test_escaped_output_means_the_same_string(self):
        original = "\u2019\U0001F600\u4e2d"
        out = self.run_main('"' + original + '"')
        self.assertEqual(json.loads(out), original)

    def test_refuses_a_character_after_a_backslash(self):
        path = self.write("app.js", 'x = "\\\u2019";')
        with self.assertRaises(SystemExit) as caught:
            latin1.main(path)
        self.assertIn("follows a backslash", str(caught.exception))
        self.assertEqual(self.read(path), 'x = "\\\u2019";')  # left untouched

    def test_an_escaped_backslash_before_the_character_is_fine(self):
        out = self.run_main('x = "\\\\\u2019";')
        self.assertEqual(out, 'x = "\\\\\\u2019";')

    def test_a_backslash_before_latin1_is_fine(self):
        text = 'x = "\\\u00e9";'
        self.assertEqual(self.run_main(text), text)

    def test_source_map_columns_shift_by_the_extra_length_of_escapes(self):
        # line: ab"€"cd  with segments at columns 0, 3 (the euro), 5 (c)
        line = 'ab"\u20ac"cd'
        mappings = ",".join([segment(0), segment(3), segment(2)])
        map_path = self.write("app.js.map", json.dumps({"version": 3, "mappings": mappings}))
        self.run_main(line, map_path)
        with open(map_path, encoding="utf-8") as f:
            shifted = json.load(f)["mappings"]
        # the euro becomes 6 characters, so only what comes after it moves, by 5
        self.assertEqual(mapping_columns(shifted), [0, 3, 10])

    def test_source_map_counts_an_astral_character_as_two_units(self):
        # a"😀"b: the emoji is 2 UTF-16 units wide and becomes 12 characters (+10)
        line = 'a"\U0001F600"b'
        mappings = ",".join([segment(0), segment(5)])  # b is at UTF-16 column 5
        map_path = self.write("app.js.map", json.dumps({"version": 3, "mappings": mappings}))
        self.run_main(line, map_path)
        with open(map_path, encoding="utf-8") as f:
            shifted = json.load(f)["mappings"]
        self.assertEqual(mapping_columns(shifted), [0, 15])

    def test_source_map_only_shifts_the_line_with_the_escape(self):
        text = '"\u20ac"\nxyz'
        mappings = ";".join([segment(0) + "," + segment(2), segment(0) + "," + segment(2)])
        map_path = self.write("app.js.map", json.dumps({"version": 3, "mappings": mappings}))
        self.run_main(text, map_path)
        with open(map_path, encoding="utf-8") as f:
            lines = json.load(f)["mappings"].split(";")
        self.assertEqual(mapping_columns(lines[0]), [0, 7])
        self.assertEqual(lines[1], segment(0) + "," + segment(2))

    def test_source_map_with_no_escapes_is_unchanged(self):
        mappings = segment(0) + "," + segment(4)
        map_path = self.write("app.js.map", json.dumps({"version": 3, "mappings": mappings}))
        self.run_main("var a = 1;", map_path)
        with open(map_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["mappings"], mappings)


if __name__ == "__main__":
    unittest.main()
