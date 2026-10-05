#!/usr/bin/env python3
"""Rewrite every character above U+00FF in a JavaScript file as a \\uXXXX escape, in place.

Chrome keeps a script's source text in memory for the life of the page. If every character fits
in one byte, V8 stores it as a one-byte string; a single character above U+00FF (a curly quote
in a card name or quest text) makes it a two-byte string, which doubles the cost. For app.js
that was 145 MB instead of about 76 MB. TeaVM writes such characters raw inside string literals
and comments, where an escape means the same thing.

A character right after a backslash would change meaning if escaped (the escape would turn a
backslash plus the character into a backslash plus text), so the script refuses to run then.

Usage: latin1-js.py FILE
"""
import re
import sys


def escape(ch):
    code = ord(ch)
    if code > 0xFFFF:
        code -= 0x10000
        return "\\u%04x\\u%04x" % (0xD800 + (code >> 10), 0xDC00 + (code & 0x3FF))
    return "\\u%04x" % code


def main(path):
    with open(path, encoding="utf-8", newline="") as f:
        text = f.read()
    if re.search(r"(?<!\\)(?:\\\\)*\\[^\x00-\xff]", text):
        sys.exit(f"{path}: a character above U+00FF follows a backslash; escaping it would change its meaning")
    out, count = re.subn(r"[^\x00-\xff]", lambda m: escape(m.group()), text)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(out)
    print(f"{path}: {count} characters escaped")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
