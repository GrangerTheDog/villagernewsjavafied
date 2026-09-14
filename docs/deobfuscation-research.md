# Deobfuscating Villager News: is it worth it?

*Research note for Villager News: Javafied. It was written against add-on 1.0.4, the version in `dev/addon-src`. It contains identifiers only, never add-on content.*

## TL;DR

**Recommended: a partial approach, a "mapping layer".** Don't deobfuscate the add-on as a whole. Instead:

1. **Now (small job):** give the roughly 75 add-on names that our hand-written Java code uses readable names, in one mapping file per add-on version. Our code then says `ACCESSORY_PROPERTY` or `dialog("idle.employed")`, and the mapping says what the add-on 1.0.4 calls it (`p:mlxeez`, `lvigit`). Also give our own **items readable Java ids** (`villagernewsjavafied:mayor_hat` instead of `:cryhjc`). Do this **before anyone has worlds with these items in them**; it's a breaking change later.
2. **Leave everything the mod interprets as data obfuscated:** models, textures, animations, Molang, render controllers, sounds, dialog tables and behaviour sensors. Nobody reads those files. The runtime runs them as they are, and it keeps working when names change.
3. **Only when a new add-on version ships:** build a "matcher" that works out which new name belongs to which old one automatically, using fingerprints: the English subtitle text, sound and texture hashes, and geometry shapes.

Full deobfuscation (renaming everything in the converted pack and the script) costs a lot, and every add-on update brings the cost back. It gains us almost nothing, because the parts that would change are exactly the parts that already adapt on their own.

---

## 1. What is obfuscated, and how

| What | Example | How it's named | Stable across add-on versions? |
|---|---|---|---|
| Files (textures, sounds, entities, geometry files, items) | `textures/oreville/vn/dil.png`, `sounds/oreville/vn/baa.ogg`, `entity/ebp.json` | **One global counter** in traversal order: `dil, dim, din, dio…`, `a, baa, bab…`, entities `ebj…ebr`, attachables `ece…ecj`, BP items `ezo…ezt` | **No.** Adding one file shifts every name after it. |
| Entity/item/geometry ids | `oreville_vn:ilvfra`, `geometry.oreville_vn.-754165646` | Random-looking 6 letters; geometry ids look like a hash | Unknown |
| Molang variables, bone names, render controllers | `v.epwebi`, bone `lghhat`, `controller.render.oreville_vn.xutrjk` | Random-looking tokens; some bone names are partly readable (`...helmet`, `held_item`, `head`) | Unknown |
| Entity properties | `p:mlxeez` (what a villager wears), `p:gcfsvg` (has nose) | Random 6 letters | Unknown |
| Script (`ebi.js`, 860 KB) | variables, functions *and object keys* | Minified, then names **substituted token by token** (see below) | Unknown |
| Dialog ids | `lvigit`, `gmrypkswxeva` | Same token substitution. `gmrypk` + `swxeva` is clearly "*conversation-group* + *part-1*": 6 different conversations share the suffix `swxeva` | Unknown |
| **Not** obfuscated | English lang text (`text.oreville_vn.*` values), Minecraft's own ids (`minecraft:iron_helmet`), Bedrock component names, the literal keys `soundId`, `animationName`, `duration`, `weight` | | Yes (and these make great anchors, see §5) |

"Unknown" means we can't tell until a second version exists. If the obfuscator is **seeded** (for example, a hash of the original name with a fixed secret), ids stay the same across releases. If it re-rolls random names each build, every id changes. The file-name counter changes either way.

### The obfuscation broke parts of the add-on itself

The obfuscator renamed **object keys** in the script, but not the **strings** used to look them up. So in the real Bedrock add-on 1.0.4:

- **Weekday lines never play.** The table is keyed `{ezzwan: …, iyyzwp: …}` (formerly `Sunday`, `Monday`, …) but looked up with `"Sunday"`. The same goes for the April Fools and New Year's Eve lines (`"dgrqwy 1"` was presumably `"Apr 1"`).
- **Villager-to-villager conversations stop after the first line.** The next part is found by parsing a numbered id like `gossip_01_02`, but the ids became `gmrypkbayahw`.
- The add-on also asks for **14 held-item reactions that don't exist in the script at all** (bucket, emerald, boat, tools…), so those stay silent in Bedrock too.

Our port restores the first two as the add-on meant them (see `VillagerReactions`). That's a nice side benefit of reading the logic rather than just running it.

---

## 2. How the port depends on names today

| Part of the mod | Depends on obfuscated names? | Adapts to a new add-on version by itself? |
|---|---|---|
| Geometry, textures, animations, animation controllers, render controllers, materials, client entity scripts (all Molang) | Yes, but only **inside the data**, which references itself consistently | ✅ **Yes**: interpreted as-is |
| Sounds, lang, item icons, spawn-egg icons | Only inside the data | ✅ Yes |
| Dialog tables (523 dialogs, 2,213 lines) | Extracted by **shape** (`Dialog.<x>({id:…})`) plus ~11 obfuscated keys (`slhkqn` = lines, `aswuwr` = subtitles, `jqgklx`/`csiavd` = cooldowns…) in `ScriptDataConverter` | ⚠️ **Partly.** The shape matching survives; the 11 key names would need updating if they change. About half of them could be made shape-based. |
| Hurt voices and conversation tables | Found by **shape** | ✅ Mostly |
| Behaviour-pack sensors (~30 reactions: armour, effects, pushing, waking up…) | Interpreted as data; only the script-event name `oreville_vn:gjlxaa` is hard-coded | ✅ Mostly (1 name) |
| **Hand-ported logic**: idle chatter, calendar, conversations, hurt, item interactions | **~45 hard-coded dialog ids, tags and properties** (`VillagerReactions`, `VillagerItemReactions`) | ❌ **No.** Wrong ids fail *silently*: the villager just says nothing. |
| **Registered content**: items, spawn eggs, villager variants | **~25 ids** (`ModItems`, `VillagerVariantKeys`) used as the *Java registry ids* | ❌ **No, and it's the worst case.** Items in players' worlds are saved under these ids, so a renamed add-on id means lost items or missing textures. |
| Puppet port | 2 properties plus the client entity name `villager` | ❌ No |

**About 75 names in total** tie our Java code to add-on 1.0.4.

---

## 3. What happens when a new add-on version comes out

### Case A: the ids stay the same (seeded obfuscator), only files move

- Converter and runtime: **nothing to do.** They re-read everything from the new pack.
- Hand-ported logic: works, apart from any *new* features, which need porting like any other feature.
- New dialogs, reactions and entities: new work, but that's porting, not remapping.

### Case B: every id is re-rolled

- Everything data-driven: **still fine.**
- The ~75 hard-coded names: **all wrong at once.** Villagers go mostly quiet (only the sensor reactions keep working), hats stop showing on villagers, and the special villagers' spawn eggs break.
- Items: players' existing items become unknown items **unless the Java ids are our own readable ones** (recommendation 1).

**How much work Case B is for me:**

| Setup | Work per add-on update |
|---|---|
| Today (names scattered through the code, no tooling) | Re-find each of the ~75 names by reading the new `ebi.js` and data again. Realistically **1–2 sessions**, easy to miss one, and a miss fails silently. |
| With the mapping layer only | Same detective work, but in **one file**, with tests that fail loudly for any missing name. About **1 session**. |
| Mapping layer + matcher | Run the matcher, review the handful of low-confidence matches, and commit a new mapping file. **Well under a session**, mostly review. The old version keeps working through its own mapping file. |

We already have part of the safety net: `DialogLibraryTest.portedTriggersReferenceRealDialogs` and `BehaviorDefinitionsTest` fail if a referenced dialog disappears.

---

## 4. The options

### Option 0: do nothing (keep obfuscated ids in the code, as now)
- **Pros:** zero work now; every id can be traced 1:1 to the add-on.
- **Cons:** unreadable code (`"lvigit"`), comments carry the meaning, a new version means hunting through 8 files. **Item ids in saves are tied to the add-on's naming.**

### Option 1: mapping layer (recommended)
One file, e.g. `src/main/resources/mappings/villagernews-1.0.4.json`, holding readable name → add-on id:
```json
{ "dialog.idle.employed": "lvigit", "property.accessory": "p:mlxeez", "item.mayor_hat": "cryhjc", ... }
```
The converter detects the add-on version from its `manifest.json` (it says `1.0.4`) and records it. The server and client load the matching mapping, and Java code only uses readable keys.
- **Pros:**
  - Readable code.
  - A single place to update.
  - Can support several add-on versions side by side.
  - Readable, **stable Java item ids**, so worlds survive add-on updates.
  - Unknown versions can show a clear warning ("add-on 1.1 not mapped yet; some reactions are off") instead of failing silently.
- **Cons:**
  - Half a session of work now.
  - One more indirection when debugging.
  - Changing item ids now breaks the few test worlds that already contain items. That's fine today, painful later.
- Contains only identifiers and our own invented names, **no add-on content.** It's the same idea as Minecraft mappings.

### Option 2: full deobfuscation of the converted pack
Rename textures, bones, variables, controllers and so on to readable names while converting, rewriting every reference (Molang, render controllers, geometry, animations) consistently.
- **Pros:** nicer to browse `dev/converted`; easier to hand-debug a render controller.
- **Cons:**
  - Every name needs a human-chosen name. There are **thousands** (743 texture files, 2,235 sounds, 2,512 animations).
  - Every add-on update would need re-matching all of them.
  - Every rewrite risks a subtle break (Molang strings are code).
  - It adds nothing to what players see.
- **Verdict: not worth it.**

### Option 3: deobfuscate the script (`ebi.js`) itself
Produce a readable version with meaningful names (we already pretty-print it for research, in a local scratch folder).
- **Pros:** faster porting of the remaining ~450 triggers.
- **Cons:**
  - 35k lines. The naming is manual, and has to be redone for each version unless the matcher carries names over.
  - Must never be committed or shipped (it *is* the add-on's code).
- **Verdict:** do it only as a private dev aid, bit by bit, as the porting needs it. Keep it in the gitignored `dev/` folder.

### Option 4 (for later): the matcher
When version N+1 appears, match old ids to new ids by fingerprint:
- **Dialogs:** English subtitle text (not obfuscated!), sound file hashes, line count and durations.
- **Sounds / textures:** file content hashes.
- **Geometry:** bone tree shape and cube counts.
- **Entities / items:** component structure, and lang names (not obfuscated).
- **Properties:** type, range, default and the events that set them.
- **Script functions:** harder. We'd match by the dialogs, strings and Minecraft ids they use. We don't need this for the ported logic if the mapping covers its inputs.

- **Pros:** turns an update into a review task.
- **Cons:** about a session to build. Pointless until a second version exists to test it on.

---

## 5. Practical and legal notes (not legal advice)

- Mapping files and our Java code contain **identifiers only** (like `lvigit`) plus our own names. That keeps within the project rule of shipping no add-on content, the same way the current code references bone and property names.
- The deobfuscated or pretty-printed script, extracted data and converted pack stay **local and gitignored**, as now.
- Reading the obfuscated script to make our own interoperable implementation is what the project has been doing all along. The mapping layer doesn't change that. It just puts the names in one place.

---

## 6. Recommendation and next steps

| Step | When | Effort |
|---|---|---|
| Mapping layer, plus readable item ids (`mayor_hat`, `villager_nose`, `mayor_villager_spawn_egg`…) and a startup warning for unmapped add-on versions | **Soon.** Before anyone relies on worlds with these items. | ~½ session |
| Make the remaining script-extraction keys shape-based where possible | With the above | small |
| Keep porting triggers against the mapping (readable names from the start) | Ongoing | — |
| Matcher | When a new add-on version is released | ~1 session |
| Full pack or script deobfuscation | Not planned | — |

**Would our code adapt to a new version?** The data-driven 90% (visuals, animations, sounds, dialog data, sensor reactions) would, automatically. The hand-ported 10% (specific reactions, item interactions, item ids) would not. How much work that is depends on the setup: a few minutes of review with a mapping plus matcher, about a session with the mapping alone, or 1–2 sessions of detective work as things stand today.

---

## 7. Update: what another port taught us

We studied a public Fabric port of the same add-on for its *methods* only. Its jar and repo bundle the add-on's converted models, textures and sounds, so nothing was taken from it. Three findings change the picture above.

1. **The add-on documents itself.** Its script holds a guide entry for each dialog, keyed by the dialog id: `[<table>.<dialog id>]: {title: "Approach a Villager", body: "Trigger … Reaction …"}`. **378 of the 523 dialogs** carry an English title plus a trigger/reaction description; the rest are mostly conversation parts and line variants. The guide text isn't obfuscated, so:
   - **The mapping layer can be mostly generated.** Readable names come straight from the guide titles (`xfpjxq` → "Approach a Villager"), and a human only reviews them.
   - **The matcher for a new add-on version gets much easier.** Match old and new dialog ids by guide title and body first, and use sound hashes only for the rest.
   - **We get a porting checklist.** `tools/dialog_coverage.py` builds one locally (in `dev/dialog-coverage.md`): each dialog's trigger, and whether the port reaches it yet. At the time of writing: 74 of 523.
2. **The obfuscation has holes.** A few names survived: `DIALOG_CHEST`, `DIALOG_JUKEBOX`, `DIALOG_CRAFTING_TABLE`. The block reactions sit in shape-recognisable tables (`new Map([["minecraft:<block>", <dialog>], …])`), so the converter can extract them the same way it extracts dialogs.
3. **Bedrock's `sheep` material reads texture alpha as a dye mask**, not as opacity (Wooly's skin is alpha 3). The converter now makes those pixels opaque.

For comparison, the other port bundles pre-converted assets for Entity Model Features / Texture Features / Sound Features. That's simpler to run, but it redistributes the add-on. Ours converts the player's own copy on their device and interprets the add-on's logic at runtime. This note's recommendation stands, and point 1 makes option 1 (the mapping layer) cheaper than estimated in §6: roughly a quarter of a session, most of it generated.
