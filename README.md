# Villager News: Javafied

[![Build](https://github.com/GrangerTheDog/villagernewsjavafied/actions/workflows/build.yml/badge.svg)](https://github.com/GrangerTheDog/villagernewsjavafied/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/GrangerTheDog/villagernewsjavafied?label=release)](https://github.com/GrangerTheDog/villagernewsjavafied/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/GrangerTheDog/villagernewsjavafied/total)](https://github.com/GrangerTheDog/villagernewsjavafied/releases)
[![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-62b47a)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/loader-Fabric-dbd0b4)](https://fabricmc.net)
[![Needs GeckoLib](https://img.shields.io/badge/needs-GeckoLib%205.5.5-8a2be2)](https://modrinth.com/mod/geckolib)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Made with AI](https://img.shields.io/badge/made%20with-AI%20%28Claude%29-d97757)](#ai-disclosure)

A Fabric mod that brings **Villager News** by **Element Animation** to Minecraft Java Edition, by converting the Bedrock add-on by **Oreville Studios** on your own computer.

## Bring your own add-on

The mod's jar contains none of the add-on's assets: no models, textures, sounds, animations, dialogue or handbook text. It's only the code that knows how to read them.

Getting set up is easy. The first time you start the game, the mod asks where your copy of the Villager News add-on (`.mcaddon`) is. Paste its path once, and the mod converts it into a resource pack on your own machine. Everything you see and hear in game comes from that conversion. Nothing is downloaded, and nothing from the add-on ever leaves your computer.

## Features

- **Talking villagers.** Hundreds of voiced lines from the add-on, with subtitles and lip-sync. Villagers react to you (trading, hitting them, what you hold or do near them), chat with each other in two-villager conversations, talk about their work and their day, and cry out when they're hurt or in danger.
- **The add-on's look.** Its villager models and animations, with professions, biome outfits and level badges, and its own baby villagers.
- **Special characters.** The Mayor, Testificate Man, Villager #5, Villager #9, the Untouchable Villager and Wooly the Sheep, each with their own lines, and trades for the characters that trade. One of each turns up in a village somewhere in your world, well away from spawn.
- **Items.** The Villager News Handbook, the add-on's in-game guide with its search and settings. The microphone: hold right-click to speak into it. The Mayor Hat, Testificate Man helmet, moustache and villager nose, worn in 3D. You can also shear a villager's nose off.
- **Settings**, in the handbook: how chatty villagers are (muted, shy, chatty or super chatty), how often they use their rarer lines, and whether special characters appear. Subtitles and the villager style are set per player.
- Vanilla villager and wandering trader grunts are muted, as in the add-on, so the voice lines take over.

## Credits

- **Villager News**, its characters and its world are by [Element Animation](https://www.youtube.com/@ElementAnimation).
- **The Villager News Bedrock add-on** is by Oreville Studios and Element Animation. Every model, texture, sound, animation, line of dialogue and handbook page you see with this mod comes from their add-on.
- This is an unofficial fan port. It is not affiliated with or endorsed by Element Animation, Oreville Studios, Mojang or Microsoft. If you enjoy it, please support the original add-on and get it from its official source.
- Built with [Fabric](https://fabricmc.net), [GeckoLib](https://github.com/bernie-g/geckolib) and [mocha](https://github.com/unnamed/mocha).

## Requirements

- Minecraft Java Edition 26.2 with Fabric Loader 0.19.5 or later
- [Fabric API](https://modrinth.com/mod/fabric-api) and [GeckoLib](https://modrinth.com/mod/geckolib) 5.5.5 or later
- Java 25
- Your own copy of the Villager News add-on (see [Bring your own add-on](#bring-your-own-add-on)). The mod is mapped against add-on version 1.0.4.

## Using it

1. Install the mod, Fabric API and GeckoLib.
2. Start the game. The first-time setup screen asks for the path to your Villager News `.mcaddon` file. Paste it in and press **Convert**.
3. The converted pack is saved in your `resourcepacks` folder and enabled automatically, so you only need to convert once per add-on version.

Commands:

- `/villagernews summon <character>` spawns one of the add-on's characters.
- `/villagernews debug` toggles an overlay that shows what the villager (or trader) you're looking at is saying, and why it's quiet.

## Downloads

Every version is built and released automatically on the [Releases](https://github.com/GrangerTheDog/villagernewsjavafied/releases) page. Download `villagernewsjavafied-<version>.jar` (not the `-sources` one) and put it in your `mods` folder.

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. GitHub Actions builds every push. When `version` in `gradle.properties` changes on `main`, it releases that version with the jar attached.

For development, extract the add-on into `dev/addon-src/`, then run `./gradlew runConverter` and `./gradlew runClient`. The add-on's ids are obfuscated. Inside the mod everything uses readable names: [docs/names.md](docs/names.md) explains how they're mapped, and what to do when the add-on updates.

## What's in this repository

Only the mod's own code, plus the add-on's identifiers mapped to readable names. The add-on itself, and anything converted from it, stays out of the repository and out of the jar: `.gitignore` excludes `dev/` and `*.mcaddon`. Please keep it that way in contributions.

## AI disclosure

This mod was written with AI. Most of its code, and much of its documentation, was written by Claude (Anthropic's AI model) through Claude Code, with the maintainer directing the work, testing it in game and reviewing it. Commits Claude helped write say so in a `Co-Authored-By: Claude` line.

To build the converter, the AI read a local copy of the add-on the maintainer owns. None of the add-on's content was copied into this repository or the jar (see [What's in this repository](#whats-in-this-repository)).

## License

The mod's own code is under the [MIT License](LICENSE). The license covers only this code. It grants nothing for Villager News, its characters, names or artwork, or any part of the add-on, which belong to their creators.
