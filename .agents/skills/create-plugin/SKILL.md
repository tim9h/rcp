---
name: create-plugin
description: Create/bootstrap a new RCP plugin as a sibling repo using the rcp.plugin.archetype. Use when asked to create, scaffold, bootstrap or start a new rcp plugin.
---

# Bootstrap a new RCP plugin

Plugins live in **separate sibling repos** (`rcp.<name>`), not in this repo. They implement `dev.tim9h.rcp.spi.Plugin` (see `rcp.api/src/main/java/dev/tim9h/rcp/spi/Plugin.java`; all methods except `getName` are optional defaults) and are loaded from `./plugins` at startup only. Read `AGENTS.md` first.

## Procedure
1. Ask for (or derive) the plugin name: lowercase, no spaces (e.g. `clock`) → `pluginName`; and a capitalized class prefix (e.g. `Clock`) → `pluginNameHyphened`.
2. Build and install the archetype (not part of the root `<modules>`):
   `cd rcp.plugin.archetype && mvn -B install`
3. Generate the plugin next to this repo (sibling directory, `../`):
   ```
   mvn -B archetype:generate -DarchetypeGroupId=dev.tim9h -DarchetypeArtifactId=rcp.pluginarchetype \
     -DarchetypeVersion=<version in rcp.plugin.archetype/pom.xml> \
     -DgroupId=dev.tim9h -DartifactId=rcp.<name> -Dversion=0.0.1-SNAPSHOT -Dpackage=dev.tim9h.rcp.<name> \
     -DpluginName=<name> -DpluginNameHyphened=<Name> -DinteractiveMode=false
   ```
4. Fix the generated output (the template is known to be incomplete):
   - `<Name>View.java` lacks `import java.util.Collections;` and `import java.util.Map;`.
   - `pom.xml` parent is `dev.tim9h:rcp.parent` `0.0.1-SNAPSHOT`: align with the root `pom.xml` version, and make sure `rcp.parent` is installed (`mvn -B install -DskipTests` in this repo) so it resolves. The jar plugin `outputDirectory` points to `../rcp.parent/rcp.core/target/plugins`; adjust to this checkout's directory name if different.
   - `module-info.java` (`module rcp.<name>`) must `requires` every module the plugin uses (e.g. `rcp.controls`, `javafx.*`) and `exports` its package.
   - `META-INF/services/dev.tim9h.rcp.spi.Plugin` must list the fully-qualified plugin class.
5. Build: `mvn -B verify` in the new plugin (needs JDK 27). The jar lands in `rcp.core/target/plugins`; copy to `./plugins` next to the runnable core jar to try it.
6. Flesh out as needed: `getNode()` (UI), `getCommands()`/`getModes()` (CLI, registered via `EVENT_CLI_ADD_PROPOSALS`), `getSettingsContributions()` (namespaced keys like `<name>.some.setting`), `initBus(EventManager)` for event handling. Keep `init()`/`getNode()` cheap and never block the JavaFX thread; see the `review-plugin-performance` skill.
7. Initialise git (`git init`), add `.gitignore` (`target/`, Eclipse files) and a README. Don't commit generated css or secrets.
