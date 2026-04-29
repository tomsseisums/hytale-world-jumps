# Hytale Plugin Template

A ready-to-use starting point for creating Hytale server plugins with Java, _or Kotlin_. If you've
been using the Asset Editor and want to start writing server-side logic — custom commands, event
handling, gameplay systems — this is the simplest place to begin.

## How to start?

1. Copy the template by downloading it or using the "Use this template" button.
2. [Configure or Install the Java SDK](https://hytalemodding.dev/en/docs/guides/plugin/setting-up-env)
   to use the latest 25 from JetBrains or similar.
3. Open the project in your favorite IDE, we
   recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/download).
4. Optionally, run `./gradlew` if your IDE does not automtically synchronizes.
5. Run the devserver with the Run Configuration created, or `./gradlew devServer`.

> On Windows, use `.\gradlew.bat` instead of `./gradlew`, this script is here to run the
> Gradle without you needing to install the tooling itself, only the Java is required.

With that you will be prompted in the output to authorize your server, and then you can start
developing your plugin while the server is live reloading the code changes.

From here,
the [HytaleModding guides](https://hytalemodding.dev/en/docs/guides/plugin/build-and-test) cover
more details!

## Scaffoldit Plugin

While there are multiple plugins made for Hytale, the template currently uses a zero-boilerplate one
where you only need the absolute minimum to start. However, you do have access to everything as
normal if you know what you are doing.

For in-depth configuration, you can visit the [ScaffoldIt Plugin Docs](https://scaffoldit.dev).

## Optional: Allow `/inventory clear` in Creative worlds

Per-world inventories are restored automatically on world transitions, so players don't normally need to do anything. If you want to give them an in-chat escape hatch — `/inventory clear` (alias `/inv clear`) — Hytale gates that command behind the `hytale:Builder` group by default, which would also unlock a host of other Builder commands you probably don't want exposed.

This plugin can grant **only** `hytale.system.command.inventory.clear`, **only while the player is in a managed Creative world**, and revoke it on exit/disconnect. Set the following in the plugin config (`devserver/mods/<plugin>/config.json` in dev, or the equivalent on a real server):

```json
{
  "AllowClearInventoryInCreative": true
}
```

Default is `false`. With it enabled, the permission is added per-user on entry to a Creative world and removed when leaving, so survival/default-world players never gain it.

## Troubleshooting

- **Gradle sync fails in IntelliJ** –
  _Check that Java 25 is installed and configured under File → Project Structure → SDKs._
- **Build fails with missing dependencies** –
  _Run `./gradlew build --refresh-dependencies`. Make sure you have internet access!_
- **Permission denied on `./gradlew`** –
  _Run `chmod +x gradlew` (macOS/Linux)._
- **Hot-reload doesn't work** –
  _Verify you're using JetBrains Runtime, not a regular JDK._

## Resources

- [Hytale Modding Guides](https://hytalemodding.dev)
- [Hytale Modding Discord](https://discord.gg/hytalemodding)
- [ScaffoldIt Plugin Docs](https://scaffoldit.dev)

## License

Add your own after copying the template, though we recommend using MIT, BSD, or Apache to keep
the modding community open!
