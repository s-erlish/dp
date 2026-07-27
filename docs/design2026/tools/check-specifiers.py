#!/usr/bin/env python3
"""
check-specifiers.py - keep section 5.2 of 42-copy-register.md honest.

5.2 claims to name «every parameterised string in the product». The first edition
did not: `Servers_SelectedCount` and `server_selected_reconnect_prompt` were in
section 3 and missing from 5.2, `servers_deleted_count` and `Settings_UiScaleValue`
were in 5.2 and in no section-3 row, and the UI-scale row invented an **Android**
key for a setting 3.6.1 marks Android `-`. All four are the same defect: a
hand-maintained index of a hand-maintained table.

This script extracts every section-3 cell containing a format specifier, derives the
key pair and the argument count, and diffs both directions against 5.2. Any
asymmetry is an error.

    python3 docs/design2026/tools/check-specifiers.py --emit    # print the 5.2 rows
    python3 docs/design2026/tools/check-specifiers.py --check   # exit 1 on asymmetry

A row's «Типы» and «Примечание» cells are written by hand and are preserved by key;
this script owns the row *set* and the argument *count*, which is the half that
crashes at runtime when it drifts.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REGISTER = Path("/home/user/dp/docs/design2026/42-copy-register.md")

ANDROID_SPEC = re.compile(r"%(\d+)\$[sd]")
ANDROID_BARE = re.compile(r"%(?!%)(?!\d+\$)[sd]")
PC_SPEC = re.compile(r"\{(\d+)[^}]*\}")
S3 = re.compile(r"^## 3\. ")
S4 = re.compile(r"^## 4\. ")
S52 = re.compile(r"^### 5\.2 ")
S53 = re.compile(r"^### 5\.3 ")

MULTI = [
    ("Заголовок", "Строка", "Действие"),
    ("Заголовок", "Тело", "Левая", "Правая"),
]


def cell(s: str) -> str:
    return s.strip().strip("`").strip()


def rows_of(text: str, start: re.Pattern, end: re.Pattern):
    inside = False
    header = None
    for n, line in enumerate(text.split("\n"), 1):
        if start.match(line):
            inside = True
        elif end.match(line):
            inside = False
        if not inside or not line.startswith("|"):
            header = None
            continue
        cols = [c.strip() for c in line.split("|")[1:-1]]
        if all(c and set(c) <= set("-: ") for c in cols):
            continue
        if header is None:
            header = cols
            continue
        yield n, header, cols


def scan_section3(text: str):
    """key-pair -> (russian, argcount)"""
    found: dict[tuple[str, str], tuple[str, int]] = {}
    for n, header, cols in rows_of(text, S3, S4):
        if "Android" not in header or "PC" not in header:
            continue
        labels = None
        for cand in MULTI:
            if all(x in header for x in cand):
                labels = cand
                break
        if labels:
            texts = [cell(cols[header.index(x)]) for x in labels]
        elif "Русский" in header:
            texts = [cell(cols[header.index("Русский")])]
        else:
            continue
        a_cell, p_cell = cols[header.index("Android")], cols[header.index("PC")]
        a_keys = [k.strip().strip("`") for k in a_cell.split(",")] if "`" in a_cell else [cell(a_cell)]
        p_keys = [k.strip().strip("`") for k in p_cell.split(",")] if "`" in p_cell else [cell(p_cell)]
        for i, t in enumerate(texts):
            if "%" not in t.replace("%%", "") and "{" not in t:
                continue
            idx = {int(m) for m in ANDROID_SPEC.findall(t)}
            idx |= {int(m) + 1 for m in PC_SPEC.findall(t)}
            if ANDROID_BARE.search(t):
                idx.add(-1)                      # a bare %s: flagged, not counted
            if not idx:
                continue
            ak = a_keys[i] if i < len(a_keys) else a_keys[-1]
            pk = p_keys[i] if i < len(p_keys) else p_keys[-1]
            found[(ak, pk)] = (t, max(idx))
    return found


def scan_52(text: str):
    listed: dict[tuple[str, str], str] = {}
    for n, header, cols in rows_of(text, S52, S53):
        if "Android" not in header or "PC" not in header:
            continue
        a = cols[header.index("Android")]
        p = cols[header.index("PC")]
        ak = re.search(r"`([A-Za-z0-9_]+)`", a)
        pk = re.search(r"`([A-Za-z0-9_]+)`", p)
        listed[(ak.group(1) if ak else "-", pk.group(1) if pk else "-")] = cols[
            header.index("Аргументы по порядку")] if "Аргументы по порядку" in header else ""
    return listed


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--emit", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    text = REGISTER.read_text(encoding="utf-8")
    found = scan_section3(text)
    listed = scan_52(text)

    if args.emit:
        for (ak, pk), (t, cnt) in sorted(found.items()):
            a = f"`{ak}` `{t}`" if ak != "-" else "-"
            p = f"`{pk}`" if pk != "-" else "-"
            print(f"| {a} | {p} | {cnt} arg(s) |")
        return 0

    missing = sorted(set(found) - set(listed))
    extra = sorted(set(listed) - set(found))
    bare = [k for k, (t, c) in found.items() if c == -1]
    for k in missing:
        print(f"MISSING from 5.2: {k}  «{found[k][0]}»")
    for k in extra:
        print(f"NOT IN section 3: {k}")
    for k in bare:
        print(f"BARE %s (5.1.1): {k}")
    if missing or extra or bare:
        print(f"\n{len(missing)} missing, {len(extra)} orphaned, {len(bare)} bare.")
        return 1
    print(f"5.2 and section 3 agree: {len(found)} parameterised strings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
