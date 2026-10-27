# RCP

RCP is a (semi) rich client application based on JavaFX. Its start-menu-like user interface expands and collapses from the top middle of the screen.

The application can be extended with plugins. Plugins are JAR files that are discovered and initialized during startup from the `./plugins` directory. New plugins can easily be created with the provided Maven archetype in `rcp.plugin.archetype`.

## Requirements

- Java 27
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
javaw.exe -Dlogpath=... --enable-native-access=ALL-UNNAMED -jar rcp.core\target\rcp.core-....jar
```

Replace `...` with the desired log path and `....` with the generated project version.

Global hotkeys use the Windows format `MODIFIER+KEY`, for example `ALT+SPACE`,
`CTRL+ALT+R`, or `WIN+F1`. Supported modifiers are `CTRL`, `ALT`, `SHIFT`, and
`WIN`; supported keys include letters, digits, `F1`–`F24`, and named keys such as
`SPACE`, `ENTER`, `TAB`, `ESC`, `DELETE`, and arrow keys. This feature requires
64-bit Windows. Existing hotkey preferences are not migrated; update
`core.hotkey.showhide` in `~/.rcprc` to the new format if registration fails.
