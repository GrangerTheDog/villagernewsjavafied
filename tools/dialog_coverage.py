#!/usr/bin/env python3
"""Dev tool: which of the add-on's dialogs the port can already trigger.

Reads the local (gitignored) add-on copy in dev/addon-src and the converted
data in dev/converted, and writes dev/dialog-coverage.md: every dialog id with
the trigger its in-game guide describes, and how the port reaches it (a
hand-ported trigger in Java, a behavior-pack sensor, or not yet).

The add-on's script keeps a guide entry per dialog, keyed by the dialog id:
    [<table>.<dialog id>]: {title: "...", body: "§eTrigger\\n\\n§7...§eReaction..."}
Nothing here is committed; the report stays in dev/.

Usage: python3 tools/dialog_coverage.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = next((ROOT / "dev/addon-src").glob("*BP/scripts/**/*.js"))
DIALOGS = ROOT / "dev/converted/server/dialogs.json"
ENTITIES = ROOT / "dev/converted/server/entities"
OUT = ROOT / "dev/dialog-coverage.md"

GUIDE_ENTRY = re.compile(r'\[[A-Za-z_$][\w$]*\.(\w+)\]:\{title:"((?:[^"\\]|\\.)*)",body:"((?:[^"\\]|\\.)*)"\}')
FORMATTING = re.compile(r"§.")


def clean(text):
    return FORMATTING.sub("", text.encode().decode("unicode_escape")).strip()


def main():
    script = SCRIPT.read_text(encoding="utf-8")
    dialogs = json.loads(DIALOGS.read_text())["dialogs"]
    guide = {}
    for dialog_id, title, body in GUIDE_ENTRY.findall(script):
        trigger, _, reaction = clean(body).partition("Reaction")
        guide[dialog_id] = (clean(title), trigger.replace("Trigger", "", 1).strip(), reaction.strip())

    java = "\n".join(p.read_text() for p in (ROOT / "src").rglob("*.java"))
    in_java = {d for d in dialogs if f'"{d}"' in java}
    by_sensor = set()
    for entity in ENTITIES.glob("*.json"):
        by_sensor |= set(re.findall(r"scriptevent oreville_vn:\w+ (\w+)", entity.read_text()))
    by_sensor &= set(dialogs)

    rows = []
    for dialog_id, dialog in dialogs.items():
        title, trigger, _ = guide.get(dialog_id, ("", "", ""))
        how = "java" if dialog_id in in_java else "sensor" if dialog_id in by_sensor else ""
        rows.append((how == "", title or "~", dialog_id, len(dialog["lines"]), how, trigger))
    rows.sort()

    covered = sum(1 for r in rows if not r[0])
    lines = [
        "# Dialog coverage (local report, not committed)",
        "",
        f"{covered} of {len(rows)} dialogs reachable ({len(in_java)} hand-ported, {len(by_sensor - in_java)} via sensors); "
        f"{len(guide)} have a guide entry.",
        "",
        "| covered | guide title | dialog | lines | trigger |",
        "|---|---|---|---|---|",
    ]
    for missing, title, dialog_id, count, how, trigger in rows:
        lines.append(f"| {how or '-'} | {title} | `{dialog_id}` | {count} | {trigger} |")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{covered}/{len(rows)} covered; {len(guide)} guide entries -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
