# Readable names

The add-on's identifiers are obfuscated (`qawras`, `ilvfra`, `p:gcfsvg`). Inside the mod, everything goes by readable names in Minecraft style instead: `start_work`, `mayor`, `nose`. This note covers where those names come from, how to use them, and what to do when the add-on updates.

## Where names come from

**Dialogs** are named while the add-on is converted, from the add-on itself. `DialogNames` applies these rules in order:

1. The names file, if it lists the dialog.
2. The dialog's guide title, if no other dialog has the same one: "Start Work" becomes `start_work`, and "Shear Off a Villager's Nose" becomes `shear_off_a_villagers_nose`.
3. For a later part of a two-villager conversation, the name of its opening line plus the part number: `gossip_chain_part_2`.
4. Otherwise the dialog keeps its id. The converter prints these, so they can be added to the names file.

For add-on 1.0.4 the guide titles name 451 of the 523 dialogs, and conversation chains name 24 more. The names file covers the remaining 57, which are mostly the special characters' own lines and titles shared by two dialogs.

**Everything else** comes from the names file for the add-on's version: `src/main/resources/villagernewsjavafied/names/villager_news-<version>.json`. It holds only identifiers and our own names, never add-on content. Its sections are:

- `characters`, `entities` (helper entities, like the `trade_tier_probe`) and `items`
- `properties`
- `dialog_groups` and `dialog_tags`
- `script_events` and `script_keys` (the script's own property names, which the converter reads)
- `guide_pages`, `variables` and `bones`
- `dialogs`

`index.json` lists the versions there are files for.

## In the code

- **Dialogs:** use the name as a string. The converted `dialogs.json` is keyed by name, and each dialog keeps its `id`, the add-on's id, for debugging:

  ```java
  Reactions.say(villager, "start_work", Options.DEFAULT);
  ```

- **Characters, items, properties:** use our names. `AddonNames` translates them where the add-on's id is needed:

  ```java
  AddonNames.character("mayor")      // -> "ilvfra", e.g. to find its Bedrock definition
  AddonNames.property("nose")        // -> "p:gcfsvg"
  AddonNames.nameOf(Kind.ITEM, id)   // add-on id -> our name
  properties.named("sign")           // a villager's property, by our name
  ```

- **Item registry ids** are the readable names (`villagernewsjavafied:mayor_hat`, `mayor_spawn_egg`). The converter writes icons, models and lang keys under the same names.
- **Saved data uses readable names**, so a world keeps its data when the add-on's ids change. That covers each villager's character (`mayor`) and its properties (`accessory` = `mayor_hat`).
- In the reaction classes, constants that aren't dialogs get a prefix: `ITEM_`, `PROPERTY_`, `GROUP_`, `TAG_` or `BLOCK_`. `DialogLibraryTest` checks that every other string constant is a real dialog.

## When the add-on updates

The converter reads the add-on's version from its manifest and records it in the converted pack's `manifest.json` (`addonVersion`).

- **A version with a names file:** nothing to do.
- **A new version:** the newest names file is used, and the log says so. Dialogs named from guide titles keep working even when every id changes. The log also lists names the mod uses that the converted add-on doesn't have, as `No dialog named '...'` and `... dialogs from the names file aren't in the converted add-on`.

To support a new version:

1. Convert it: `./gradlew runConverter` with the new add-on in `dev/addon-src`.
2. Copy the newest names file to `villager_news-<new version>.json` and add the version to `index.json`.
3. Update the ids the log and the tests point at: run `./gradlew test`, where `AddonNamesTest` and `DialogLibraryTest` fail on anything missing. For dialogs the converter printed without a name, match them to the old ones by what they say, and add them.
4. `python3 tools/readable_pack.py` helps with the matching (see below).

## Browsing the add-on readably

`python3 tools/readable_pack.py` writes a copy of the converted Bedrock definitions to `dev/readable/`. It includes client entities, attachables, controllers, animations, behavior entities and trade tables:

- Files are named after what they define (`entity/villager.json`, `attachables/handbook.json`).
- Ids are replaced by our names: `p:nose`, `oreville_vn:mayor`, `scriptevent oreville_vn:dialog start_work`, `v.mouth_open`.
- `dev/readable/INDEX.md` lists every name and its id.

Nothing loads the copy, and like the rest of `dev/` it is never committed.
