#!/usr/bin/env python3
"""
derive-copy-delta.py - derive the two Δ columns of 42-copy-register.md mechanically.

Why this exists
---------------
The register's first edition carried a single hand-written Δ column whose `=` code
claimed «both platforms already ship exactly this string, do not touch it». A probe
of twelve rows marked `=` found twelve Android keys that exist in no `values*/`
folder at all. A hand-maintained column over ~700 rows is not maintainable and was
not maintained; this script derives it from the two codebases instead.

The two axes
------------
  Текст  - what the platforms render today, against the approved Russian cell:
             =   every platform that shows this concept already renders exactly it
             A   Android's rendered wording differs (or Android renders nothing yet)
             P   PC's rendered wording differs (or PC renders nothing yet)
             AP  both differ
  Ключи  - whether the named resource key exists, per platform:
             A✓          the key exists today under exactly this name
             A+          the key does not exist and must be created
             A←old_name  a key named `old_name` already carries this exact Russian
                         string, so this row is a rename, not a new string
             A-          Android does not show this concept
             A≡          the row reuses a key declared on another row
           and the same five shapes with the `P` prefix for the desktop.

`Текст = A` together with `Ключи = A+` is the old `A+` code: Android must gain the
key and the string. `Текст = AP` with `A+ P+` is the old `N`. Nothing is lost and
nothing is asserted that a grep cannot check.

Usage
-----
    python3 docs/design2026/tools/derive-copy-delta.py            # report, no write
    python3 docs/design2026/tools/derive-copy-delta.py --rewrite  # rewrite in place
    python3 docs/design2026/tools/derive-copy-delta.py --check    # exit 1 when stale

Inputs, all read-only:
    /home/user/dp/V2rayNG/app/src/main/res/values/*.xml
    /home/user/dp/V2rayNG/app/src/main/res/values-ru/*.xml
    /home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.*.cs
    /home/user/dp/docs/design2026/42-copy-register.md
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from pathlib import Path

ANDROID_RES = Path("/home/user/dp/V2rayNG/app/src/main/res")
PC_COMMON = Path("/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common")
REGISTER = Path("/home/user/dp/docs/design2026/42-copy-register.md")

STRING_RE = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)
PLURALS_RE = re.compile(r'<plurals\s+name="([^"]+)"')
ADD_RE = re.compile(
    r'\bAdd\(\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)', re.S)
ADDPLURAL_RE = re.compile(r'\bAddPlural\(\s*"([^"]+)"')

SHARED_MARKERS = ("shared", "общ", "undo", "none", "см.")


# ── loading the two codebases ───────────────────────────────────────────────

def unescape_android(v: str) -> str:
    v = v.strip()
    if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
        v = v[1:-1]
    v = v.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return html.unescape(v).strip()


def unescape_cs(v: str) -> str:
    return v.replace('\\"', '"').replace("\\n", "\n").replace("\\\\", "\\").strip()


def load_android():
    """Android is read twice over: `values/` is the default locale (which D-S9 turns
    Russian) and `values-ru/` is what a Russian device reads today. A key whose
    Russian lives only in `values-ru/` is flagged `✓ru`: it exists, it reads right on
    a Russian phone, and it still ships English to everyone else."""
    default: dict[str, str] = {}
    ru: dict[str, str] = {}
    plurals: set[str] = set()
    for folder, sink in (("values", default), ("values-ru", ru)):
        for f in sorted((ANDROID_RES / folder).glob("*.xml")):
            raw = f.read_text(encoding="utf-8", errors="replace")
            for name, body in STRING_RE.findall(raw):
                sink.setdefault(name, unescape_android(body))
            plurals.update(PLURALS_RE.findall(raw))
    keys = {k: ru.get(k, default.get(k, "")) for k in set(default) | set(ru)}
    by_text: dict[str, list[str]] = {}
    for k in sorted(keys):
        by_text.setdefault(norm(keys[k]), []).append(k)
    return keys, by_text, plurals, default


def load_pc():
    keys: dict[str, str] = {}
    plurals: set[str] = set()
    for f in sorted(PC_COMMON.glob("L.*.cs")):
        raw = f.read_text(encoding="utf-8", errors="replace")
        for name, ru, _en in ADD_RE.findall(raw):
            keys.setdefault(name, unescape_cs(ru))
        plurals.update(ADDPLURAL_RE.findall(raw))
    by_text: dict[str, list[str]] = {}
    for k in sorted(keys):
        by_text.setdefault(norm(keys[k]), []).append(k)
    return keys, by_text, plurals


def norm(s: str) -> str:
    """Compare the way a reader does: collapse space, unify typographic variants,
    ignore ё/е and a trailing period."""
    for sp in (" ", " ", " "):
        s = s.replace(sp, " ")
    s = s.replace("‑", "-").replace("–", "-").replace("—", "-")
    s = s.replace("ё", "е").replace("Ё", "Е")
    s = re.sub(r"\s+", " ", s).strip()
    return s.rstrip(".").lower()


def cell(s: str) -> str:
    return s.strip().strip("`").strip()


def pick_twin(candidates: list[str], target: str) -> str:
    """Deterministic rename source: prefer a key that shares a name token with the
    key this register wants, then the alphabetically first."""
    if not candidates:
        return ""
    toks = set(re.split(r"[_.]", target.lower())) - {"common", "set", "account", "servers"}
    scored = sorted(candidates, key=lambda k: (
        -len(toks & set(re.split(r"[_.]", k.lower()))), len(k), k))
    return scored[0]


# ── per-platform state ──────────────────────────────────────────────────────

def platform_state(prefix: str, key_cell: str, approved: str, keys, by_text, default=None):
    """Return (code, participates, current_text_or_None)."""
    raw = key_cell.strip()
    plain = cell(raw)
    if plain in {"-", "—", "–", ""}:
        return f"{prefix}-", False, None
    if "`" not in raw:                       # prose, not a key list: "shared keys", "undo…"
        twin = pick_twin(by_text.get(norm(approved), []), plain)
        return f"{prefix}≡", True, (keys[twin] if twin else None)

    def locale_suffix(k: str) -> str:
        """`✓ru` = the key's Russian lives only in values-ru/, so the default locale
        still ships the upstream English (W-1, D-S9)."""
        if default is None:
            return ""
        return "" if norm(default.get(k, "")) == norm(approved) else "†"

    key = re.split(r"[,;]", plain)[0].strip().strip("`").strip()
    if key in keys:
        cur = keys[key]
        suf = locale_suffix(key) if norm(cur) == norm(approved) else ""
        return f"{prefix}✓{suf}", True, cur
    twin = pick_twin(by_text.get(norm(approved), []), key)
    if twin:
        return f"{prefix}←{twin}{locale_suffix(twin)}", True, keys[twin]
    return f"{prefix}+", True, None


def derive(approved: str, a_cell: str, p_cell: str, a_keys, a_by_text, p_keys, p_by_text,
           a_default=None):
    a_code, a_in, a_txt = platform_state("A", a_cell, approved, a_keys, a_by_text, a_default)
    p_code, p_in, p_txt = platform_state("P", p_cell, approved, p_keys, p_by_text)
    tgt = norm(approved)
    a_ok = a_in and a_txt is not None and norm(a_txt) == tgt
    p_ok = p_in and p_txt is not None and norm(p_txt) == tgt
    changed = []
    if a_in and not a_ok:
        changed.append("A")
    if p_in and not p_ok:
        changed.append("P")
    text_code = "".join(changed) if changed else "="
    return text_code, f"{a_code} {p_code}"


# ── the register ────────────────────────────────────────────────────────────

S3_START = re.compile(r"^## 3\. ")
S3_END = re.compile(r"^## 4\. ")
NOTE_RE = re.compile(r"\((R-\d+|9\.\d+|C\d+|W-\d+)\)")

# tables whose row carries several strings at once: header label -> key position
MULTI = {
    ("Заголовок", "Строка", "Действие"): 3,      # 3.8 empty states
    ("Заголовок", "Тело", "Левая", "Правая"): 4,  # 3.10 dialogs
}


def row_texts(header: list[str], stripped: list[str]) -> list[str] | None:
    for labels in MULTI:
        if all(lbl in header for lbl in labels):
            vals = [cell(stripped[header.index(lbl)]) for lbl in labels]
            return [v for v in vals if v not in {"-", "—", ""}]
    if "Русский" in header:
        return [cell(stripped[header.index("Русский")])]
    return None


def multi_derive(texts, a_cell, p_cell, a_keys, a_by_text, p_keys, p_by_text, a_default=None):
    a_keys_l = [k.strip().strip("`") for k in re.split(r",", cell(a_cell))]
    p_keys_l = [k.strip().strip("`") for k in re.split(r",", cell(p_cell))]
    t_codes, a_codes, p_codes = set(), [], []
    for i, t in enumerate(texts):
        ak = a_keys_l[i] if i < len(a_keys_l) else (a_keys_l[-1] if a_keys_l else "-")
        pk = p_keys_l[i] if i < len(p_keys_l) else (p_keys_l[-1] if p_keys_l else "-")
        tc, kc = derive(t, f"`{ak}`", f"`{pk}`", a_keys, a_by_text, p_keys, p_by_text, a_default)
        if tc != "=":
            t_codes.update(tc)
        a_codes.append(kc.split(" ")[0])
        p_codes.append(kc.split(" ")[1])
    text_code = ("A" if "A" in t_codes else "") + ("P" if "P" in t_codes else "") or "="
    def squash(cs):
        uniq = sorted(set(cs), key=cs.index)
        return uniq[0] if len(uniq) == 1 else " ".join(uniq)
    return text_code, f"{squash(a_codes)} {squash(p_codes)}"


def migrate_header(cols: list[str]) -> list[str] | None:
    """Turn a trailing `Δ` column into `Текст | Ключи`. Returns new cols or None."""
    stripped = [c.strip() for c in cols]
    if "Δ" not in stripped:
        return None
    i = stripped.index("Δ")
    return cols[:i] + [" Текст ", " Ключи "] + cols[i + 1:]


def rewrite(text: str, a_keys, a_by_text, p_keys, p_by_text, a_default=None):
    lines = text.split("\n")
    out: list[str] = []
    report: list[str] = []
    in_s3 = False
    header: list[str] | None = None
    migrating = False
    for line in lines:
        if S3_START.match(line):
            in_s3 = True
        elif S3_END.match(line):
            in_s3 = False
        if not in_s3 or not line.startswith("|"):
            out.append(line)
            header = None
            continue
        cols = line.split("|")[1:-1]
        stripped = [c.strip() for c in cols]
        if all(c and set(c) <= set("-: ") for c in stripped):     # separator row
            out.append("|" + "|".join(["---"] * len(header)) + "|" if header else line)
            continue
        if header is None:
            new_cols = migrate_header(cols)
            migrating = new_cols is not None
            cols = new_cols if migrating else cols
            header = [c.strip() for c in cols]
            out.append("|" + "|".join(cols) + "|")
            continue
        if "Текст" not in header or "Ключи" not in header:
            out.append(line)
            continue
        t_i, k_i = header.index("Текст"), header.index("Ключи")
        if migrating:                     # the body still has one Δ cell
            cols = cols[:t_i] + [cols[t_i], ""] + cols[t_i + 1:]
            stripped = [c.strip() for c in cols]
        if len(cols) != len(header):
            out.append(line)
            continue
        a_i = header.index("Android") if "Android" in header else None
        p_i = header.index("PC") if "PC" in header else None
        texts = row_texts(header, stripped)
        if a_i is None or p_i is None or not texts:
            out.append("|" + "|".join(cols) + "|")
            continue
        if len(texts) > 1:
            t_code, k_code = multi_derive(texts, stripped[a_i], stripped[p_i],
                                          a_keys, a_by_text, p_keys, p_by_text, a_default)
        else:
            t_code, k_code = derive(texts[0], stripped[a_i], stripped[p_i],
                                    a_keys, a_by_text, p_keys, p_by_text, a_default)
        old = stripped[t_i]
        note = ""
        m = NOTE_RE.search(old)
        if m:
            note = " " + m.group(0)
        new_t, new_k = f"`{t_code}`{note}", f"`{k_code}`"
        if old != new_t or stripped[k_i] != new_k:
            report.append(f"{texts[0][:40]:<42} {old:<14} -> {new_t:<14} | {new_k}")
        cols[t_i] = f" {new_t} "
        cols[k_i] = f" {new_k} "
        out.append("|" + "|".join(cols) + "|")
    return "\n".join(out), report


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rewrite", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    a_keys, a_by_text, a_plurals, a_default = load_android()
    p_keys, p_by_text, p_plurals = load_pc()
    print(f"# android: {len(a_keys)} strings, {len(a_plurals)} plurals", file=sys.stderr)
    print(f"# pc:      {len(p_keys)} strings, {len(p_plurals)} plurals", file=sys.stderr)

    src = REGISTER.read_text(encoding="utf-8")
    new, report = rewrite(src, a_keys, a_by_text, p_keys, p_by_text, a_default)

    if args.rewrite:
        REGISTER.write_text(new, encoding="utf-8")
        print(f"rewrote {len(report)} cells", file=sys.stderr)
        return 0
    if args.check:
        if new != src:
            print("\n".join(report))
            print(f"\nSTALE: {len(report)} Δ cells disagree with the codebases.")
            return 1
        print("Δ columns agree with both codebases.")
        return 0
    print("\n".join(report[:80]))
    print(f"\n{len(report)} cells would change.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
