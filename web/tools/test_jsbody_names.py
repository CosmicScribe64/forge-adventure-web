"""Checks that no @JSBody script declares a variable that a minified build may give to one of its parameters.

TeaVM's obfuscation renames the parameters of a JSBody to the first letters of the alphabet (a static method with
two parameters gets b and c, an instance method a for this, then b and c), but leaves the script text alone. A script
that declares `var b` in such a method therefore replaces its own parameter. This happened in Http.takePrefetched:
`var b = p && p[url]; delete p[url]` became `delete p[b]` on a 22 MB array, a string of 22 million numbers, and the
entry was never deleted (0.7 GB of temporary strings in WebKit, see wiki/concepts/memory-budget.md).
The test flags every single-letter name that the script declares (var, let, const, function and arrow parameters)
among the first parameter-count-plus-one letters, in every @JSBody under web/src.
"""
import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "web", "src")

STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')


def annotation_text(source, start):
    """Text of the parentheses of the annotation whose '(' is at start, string literals respected."""
    depth, i, in_str = 0, start, False
    while i < len(source):
        c = source[i]
        if in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return source[start + 1:i]
        i += 1
    return ""


def js_bodies():
    for base, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            source = open(path, encoding="utf-8").read()
            for m in re.finditer(r"@JSBody\s*\(", source):
                args = annotation_text(source, m.end() - 1)
                after = source[m.end() + len(args):m.end() + len(args) + 400]
                instance = "static" not in after.split(";")[0].split("{")[0]
                pm = re.search(r"params\s*=\s*(\{[^}]*\}|\"[^\"]*\")", args)
                params = re.findall(r'"([^"]*)"', pm.group(1)) if pm else []
                sm = re.search(r"script\s*=\s*", args)
                script_expr = args[sm.end():] if sm else ""
                script = "".join(s.replace('\\"', '"').replace("\\\\", "\\") for s in STRING.findall(script_expr))
                yield os.path.relpath(path, ROOT), m.start(), len(params), instance, script


def declared_names(script):
    names = set(re.findall(r"\b(?:var|let|const)\s+([A-Za-z_$][\w$]*)", script))
    for decl in re.findall(r"\b(?:var|let|const)\s+([^;]*)", script):
        names.update(re.findall(r",\s*([A-Za-z_$][\w$]*)\s*=", decl))
    for group in re.findall(r"function\s*[\w$]*\s*\(([^)]*)\)", script) + re.findall(r"\(([^()]*)\)\s*=>", script):
        names.update(n.strip() for n in group.split(",") if n.strip())
    names.update(re.findall(r"\b([A-Za-z_$][\w$]*)\s*=>", script))
    return names


class JsBodyNames(unittest.TestCase):
    def test_found_the_annotations(self):
        self.assertGreater(len(list(js_bodies())), 20)

    def test_script_variables_do_not_collide_with_minified_parameter_names(self):
        problems = []
        for path, _, count, instance, script in js_bodies():
            minified = {chr(ord("a") + i) for i in range(count + (1 if instance else 0) + 1)}
            clash = sorted(declared_names(script) & minified)
            if clash:
                problems.append(f"{path}: declares {clash} in a script with {count} parameter(s): {script[:70]!r}")
        self.assertEqual([], problems, "rename these variables (long names), see this file's docstring")


if __name__ == "__main__":
    unittest.main()
