# Villager News: Javafied

A Fabric mod that brings **Villager News** by **Element Animation** to Minecraft Java Edition, by converting the Bedrock add-on by **Oreville Studios** on your own computer.

The mod ships with none of the add-on's content. When you first start the game, you point it at your own copy of the add-on (`.mcaddon`). The mod converts it into a resource pack on your machine, and the villagers' models, animations, voices, dialogue and handbook all come from that conversion.

## Credits

- **Villager News**, its characters and its world are by [Element Animation](https://www.youtube.com/@ElementAnimation).
- **The Villager News Bedrock add-on** is by Oreville Studios and Element Animation. Every model, texture, sound, animation, line of dialogue and handbook page you see with this mod comes from their add-on.
- This is an unofficial fan port. It is not affiliated with or endorsed by Element Animation, Oreville Studios, Mojang or Microsoft. If you enjoy it, please support the original add-on and get it from its official source.
- Built with [Fabric](https://fabricmc.net), [GeckoLib](https://github.com/bernie-g/geckolib) and [mocha](https://github.com/unnamed/mocha).

## Requirements

- Minecraft Java Edition 26.2 with Fabric Loader 0.19.5 or later
- [Fabric API](https://modrinth.com/mod/fabric-api) and [GeckoLib](https://modrinth.com/mod/geckolib) 5.5.5 or later
- Java 25
- Your own copy of the Villager News add-on. The mod is mapped against add-on version 1.0.4.

## Using it

1. Install the mod, Fabric API and GeckoLib.
2. Start the game. The first-time setup screen asks for the path to your Villager News `.mcaddon` file. Paste it in and press **Convert**.
3. The converted pack is saved in your `resourcepacks` folder and enabled automatically, so you only need to convert once per add-on version.

In game:

- Villagers talk to you, and to each other, with the add-on's voices and subtitles.
- The add-on's items include the handbook, the microphone and the wearable hats. The handbook has the add-on's guide and its settings.
- `/villagernews summon <character>` spawns one of the add-on's characters.
- `/villagernews debug` toggles an overlay that shows what the villager you're looking at is saying, and why it's quiet.

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`.

For development, extract the add-on into `dev/addon-src/`, then run `./gradlew runConverter` and `./gradlew runClient`. The add-on's ids are obfuscated. Inside the mod everything uses readable names: [docs/names.md](docs/names.md) explains how they're mapped, and what to do when the add-on updates.

## What's in this repository

Only the mod's own code, plus the add-on's identifiers mapped to readable names. The add-on itself, and anything converted from it, stays out of the repository and out of the jar: `.gitignore` excludes `dev/` and `*.mcaddon`. Please keep it that way in contributions.

## License

The mod's own code is under the [MIT License](LICENSE). The license covers only this code. It grants nothing for Villager News, its characters, names or artwork, or any part of the add-on, which belong to their creators.
