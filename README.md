# RCP

RCP is a (semi) rich client application based on JavaFX. Its start-menu-like user interface expands and collapses from the top middle of the screen.

The application can be extended with plugins. Plugins are JAR files that are discovered and initialized during startup from the `./plugins` directory. New plugins can easily be created with the provided Maven archetype in `rcp.plugin.archetype`.

## Requirements

- JDK 27 to build the app-image
- 64-bit Windows (supported runtime platform)

## Command-line arguments

- `--blacklist <plugin>...`: do not load the listed plugins.
- `--whitelist <plugin>...`: load only the listed plugins.
- `--setting <key=value>...`: overwrite persisted settings for the current run.

`--blacklist` and `--whitelist` cannot be used together. Application settings are persisted in
`%APPDATA%\rcp\application.properties` on Windows.

## Building

From the repository root:

```text
mvn clean package
```

On Windows, this creates the portable app-image at `rcp.core/target/RCP`. The image
includes its Java runtime and launcher; no separate Java installation is required.
Copy or extract the entire `RCP` directory to the desired location and run
`RCP.exe`.

## Running

The launcher sets the required native-access options and writes logs to
`%APPDATA%\rcp\rcp.log` on Windows (creating the directory if needed). This is the
same per-user application directory used for settings and plugin data. Plugin JARs
are loaded at startup from the `plugins` directory beside `RCP.exe`; for example:

For IDE launches, `-Dlogpath` overrides the log directory (for example,
`-Dlogpath=C:\temp\logs` writes to `C:\temp\logs\rcp.log`).

```text
RCP\
  RCP.exe
  plugins\
    plugin.jar
```

The launcher accepts the application options described above, for example
`RCP.exe --setting core.ui.animations=false`.

Global hotkeys use the Windows format `MODIFIER+KEY`, for example `ALT+SPACE`,
`CTRL+ALT+R`, or `WIN+F1`. Supported modifiers are `CTRL`, `ALT`, `SHIFT`, and
`WIN`; supported keys include letters, digits, `F1`–`F24`, and named keys such as
`SPACE`, `ENTER`, `TAB`, `ESC`, `DELETE`, and arrow keys. This feature requires
64-bit Windows. Existing hotkey preferences are not migrated; update
`core.hotkey.showhide` in `%APPDATA%\rcp\application.properties` to the new format if registration fails.
