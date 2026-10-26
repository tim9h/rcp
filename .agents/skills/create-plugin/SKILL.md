---
name: create-plugin
description: Create/bootstrap a new RCP plugin as a sibling repo (written directly, no Maven archetype). Use when asked to create, scaffold, bootstrap or start a new rcp plugin.
---

# Bootstrap a new RCP plugin

Plugins live in **separate sibling repos** (`../rcp.<name>`), not in this repo. They implement `dev.tim9h.rcp.spi.Plugin` (`rcp.api/src/main/java/dev/tim9h/rcp/spi/Plugin.java`; only `getName()` is abstract, the rest are default methods) and are loaded from `./plugins` at startup only. Read `AGENTS.md` first. Do not use the Maven archetype; write the files directly.

## Procedure
1. Get the plugin name: lowercase, no spaces (`<name>`, e.g. `clock`) and a capitalized class prefix (`<Name>`, e.g. `Clock`). Ask if unclear.
2. Look at an existing plugin (e.g. `rcp.cli` in this repo, or `../rcp.clock`, `../rcp.weather`) if present and mirror its structure, `.gitignore` and conventions. Re-read `Plugin.java` and `rcp.api` for the current API; don't rely on stale snippets.
3. Read the root `pom.xml` for the current `rcp.parent` version, and install the parent/API locally: `mvn -B install -DskipTests` in this repo.
4. Create `../rcp.<name>/` with:
   - `pom.xml`: `artifactId` `rcp.<name>`, packaging `jar`, parent `dev.tim9h:rcp.parent:<version from root pom>`, dependencies `rcp.api`, `rcp.controls`, `log4j-api` (versions come from the parent's `dependencyManagement`; add any new versions to the root `pom.xml`, never inline). Third-party runtime dependencies must be declared as Maven dependencies and included as artifacts in the `maven-shade-plugin`.
   - `src/main/java/module-info.java`: `module rcp.<name>` that `exports dev.tim9h.rcp.<name>`, `requires transitive rcp.api`, `com.google.guice`, `org.apache.logging.log4j`, `javafx.controls` (plus whatever else is used).
   - `src/main/java/dev/tim9h/rcp/<name>/<Name>Plugin.java`: implements `Plugin`, `getName()` returns `<name>`; inject via Guice `@Inject` (`EventManager`, `Settings`) and `@InjectLogger Logger`; use tabs. Override only what is needed: `getNode()`, `getCommands()`/`getModes()`, `getSettingsContributions()` (keys namespaced `<name>.…`), `initBus(EventManager)`, lifecycle hooks. Include the needed imports (`java.util.Map`, `Collections`, …).
   - `src/main/resources/META-INF/services/dev.tim9h.rcp.spi.Plugin` containing `dev.tim9h.rcp.<name>.<Name>Plugin`.
   - `.gitignore` (`target/`, `.project`, `.classpath`, `.settings/`, generated css) and a short `README.md`.
5. Build with JDK 27: `mvn -B verify` in the new plugin; fix until it compiles. Copy the built jar into `./plugins` beside the core jar to try it (Windows GUI, not testable headless).
6. `git init` the new repo. Never commit secrets or generated files.

Keep `init()`/`getNode()` cheap and never block the JavaFX thread; see the `review-plugin-performance` skill.
