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
    return FORMATTING.sub("", text.encode("latin-1", "backslashreplace").decode("unicode_escape")).strip()


def main():
    script = SCRIPT.read_text(encoding="utf-8")
    dialogs = json.loads(DIALOGS.read_text())["dialogs"]
    guide = {}
    for dialog_id, title, body in GUIDE_ENTRY.findall(script):
        trigger, _, reaction = clean(body).partition("Reaction")
        guide[dialog_id] = (clean(title), trigger.replace("Trigger", "", 1).strip(), reaction.strip())

    # Dialogs are keyed by readable name; the guide and the behavior pack use the add-on's ids.
    name_of = {d["id"]: name for name, d in dialogs.items()}
    java = "\n".join(p.read_text() for p in (ROOT / "src").rglob("*.java"))
    in_java = {d for d in dialogs if f'"{d}"' in java}
    by_sensor = set()
    for entity in ENTITIES.glob("*.json"):
        by_sensor |= {name_of.get(i, i) for i in re.findall(r"scriptevent oreville_vn:\w+ (\w+)", entity.read_text())}
    by_sensor &= set(dialogs)

    # Conversation parts are reached through their chain: covered once its first line (or its group) is.
    data = json.loads(DIALOGS.read_text())
    groups = set(re.findall(r'GROUP_\w+ = "(\w+)"', java))
    by_chain = set()
    for chain in data.get("conversations", []):
        if chain[0] in in_java or dialogs.get(chain[0], {}).get("group") in groups:
            by_chain |= set(chain)
    # Hurt voices and death cries are played from sound tables, not as dialogs.
    hurt = set(data.get("hurt_sounds", {}).get("adult", [])) | set(data.get("hurt_sounds", {}).get("baby", []))
    death = set()
    for entity in (ROOT / "dev/converted/assets/villagernewsjavafied/bedrock/entity").glob("*.json"):
        effects = json.loads(entity.read_text())["minecraft:client_entity"]["description"].get("sound_effects", {})
        death |= set(effects.values())
    by_sound = {d for d, v in dialogs.items() if v["lines"] and all(l["sound"] in hurt | death for l in v["lines"])}
    # The resource pack's own controllers play some lines (death cries): an animation named after the line's sound id.
    bedrock = ROOT / "dev/converted/assets/villagernewsjavafied/bedrock"
    played = set()
    for controllers in (bedrock / "animation_controllers").glob("*.json"):
        for controller in json.loads(controllers.read_text()).get("animation_controllers", {}).values():
            for state in controller.get("states", {}).values():
                played |= {a if isinstance(a, str) else next(iter(a)) for a in state.get("animations", [])}
    by_sound |= {d for d, v in dialogs.items() if v["lines"] and all(l["sound"].split(":")[-1] in played for l in v["lines"])}

    rows = []
    for dialog_id, dialog in dialogs.items():
        title, trigger, _ = guide.get(dialog["id"], ("", "", ""))
        how = ("java" if dialog_id in in_java else "sensor" if dialog_id in by_sensor
               else "chain" if dialog_id in by_chain else "sound" if dialog_id in by_sound else "")
        rows.append((how == "", title or "~", dialog_id, len(dialog["lines"]), how, trigger))
    rows.sort()

    covered = sum(1 for r in rows if not r[0])
    lines = [
        "# Dialog coverage (local report, not committed)",
        "",
        f"{covered} of {len(rows)} dialogs reachable ({len(in_java)} hand-ported, {len(by_sensor - in_java)} via sensors, "
        f"{len(by_chain - in_java - by_sensor)} as conversation parts, {len(by_sound - in_java - by_sensor - by_chain)} as hurt/death sounds); "
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
