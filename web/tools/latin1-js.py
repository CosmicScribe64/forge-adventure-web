#!/usr/bin/env python3
"""Rewrite every character above U+00FF in a JavaScript file as a \\uXXXX escape, in place.

Chrome keeps a script's source text in memory for the life of the page. If every character fits
in one byte, V8 stores it as a one-byte string; a single character above U+00FF (a curly quote
in a card name or quest text) makes it a two-byte string, which doubles the cost. For app.js
that was 145 MB instead of about 76 MB. TeaVM writes such characters raw inside string literals
and comments, where an escape means the same thing.

A character right after a backslash would change meaning if escaped (the escape would turn a
backslash plus the character into a backslash plus text), so the script refuses to run then.

With a source map as a second argument, the generated columns in the map are moved to match, since
an escape is longer than the character it replaces (the lines of a minified app.js are long, so
the columns matter).

Usage: latin1-js.py FILE [FILE.map]
"""
import json
import re
import sys


def escape(ch):
    code = ord(ch)
    if code > 0xFFFF:
        code -= 0x10000
        return "\\u%04x\\u%04x" % (0xD800 + (code >> 10), 0xDC00 + (code & 0x3FF))
    return "\\u%04x" % code


B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"


def vlq_decode_first(segment):
    """The first VLQ value of a mappings segment and the number of characters it used."""
    value, shift, i = 0, 0, 0
    while True:
        digit = B64.index(segment[i])
        i += 1
        value |= (digit & 31) << shift
        shift += 5
        if not digit & 32:
            break
    return (-(value >> 1) if value & 1 else value >> 1), i


def vlq_encode(number):
    value = (-number << 1) | 1 if number < 0 else number << 1
    out = ""
    while True:
        digit = value & 31
        value >>= 5
        if value:
            digit |= 32
        out += B64[digit]
        if not value:
            return out


def shift_map(lines, map_path):
    """Moves each generated column in the map by the extra length of the escapes before it.
    lines are the original, unescaped lines; columns count UTF-16 units, as source maps do."""
    with open(map_path, encoding="utf-8") as f:
        sm = json.load(f)
    out_lines = []
    for number, mapping in enumerate(sm["mappings"].split(";")):
        if not mapping:
            out_lines.append(mapping)
            continue
        # (column in the original line, characters added there), in order
        line = lines[number] if number < len(lines) else ""
        points, col = [], 0
        for ch in line:
            width = 2 if ord(ch) > 0xFFFF else 1
            if ord(ch) > 0xFF:
                points.append((col, len(escape(ch)) - width))
            col += width
        if not points:
            out_lines.append(mapping)
            continue
        segments, absolute, extra, idx, prev_new = [], 0, 0, 0, 0
        for seg in mapping.split(","):
            delta, used = vlq_decode_first(seg)
            absolute += delta
            while idx < len(points) and points[idx][0] < absolute:
                extra += points[idx][1]
                idx += 1
            new = absolute + extra
            segments.append(vlq_encode(new - prev_new) + seg[used:])
            prev_new = new
        out_lines.append(",".join(segments))
    sm["mappings"] = ";".join(out_lines)
    with open(map_path, "w", encoding="utf-8") as f:
        json.dump(sm, f, separators=(",", ":"), ensure_ascii=False)


def main(path, map_path=None):
    with open(path, encoding="utf-8", newline="") as f:
        text = f.read()
    if re.search(r"(?<!\\)(?:\\\\)*\\[^\x00-\xff]", text):
        sys.exit(f"{path}: a character above U+00FF follows a backslash; escaping it would change its meaning")
    if map_path:
        shift_map(text.split("\n"), map_path)
    out, count = re.subn(r"[^\x00-\xff]", lambda m: escape(m.group()), text)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(out)
    print(f"{path}: {count} characters escaped")


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    main(*sys.argv[1:])
