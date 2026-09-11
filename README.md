# RCP

RCP is a (semi) rich client application based on JavaFX. Its start-menu-like user interface expands and collapses from the top middle of the screen.

The application can be extended with plugins. Plugins are JAR files that are discovered and initialized during startup from the `./plugins` directory. New plugins can easily be created with the provided Maven archetype in `rcp.plugin.archetype`.

## Requirements

- Java 26
- Windows (currently the supported platform)

## Command-line arguments

- `--blacklist <plugin>...`: do not load the listed plugins.
- `--whitelist <plugin>...`: load only the listed plugins.
- `--setting <key=value>...`: overwrite persisted settings for the current run.

`--blacklist` and `--whitelist` cannot be used together. Application settings are persisted in `~/.rcprc`.

## Building

From the repository root:

```text
mvn clean install
```

## Running

After building, run the shaded core JAR with `javaw.exe`:

```text
javaw.exe -Dlogpath=... -jar rcp.core\target\rcp.core-....jar
```

Replace `...` with the desired log path and `....` with the generated project version.
