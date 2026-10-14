# AGENTS.md – rcp.parent

Trust these instructions; only search if something here is missing or wrong.

## What this is
RCP: a JavaFX "start-menu-like" desktop launcher (expands from top-middle of the screen) with a plugin system. Plugins are JARs loaded from `./plugins` at startup. Plugins live in **separate sibling repos** (`rcp.clock`, `rcp.media`, `rcp.weather`, …); this repo is only the host + API. Windows is the only supported platform (uses JNA/jkeymaster native hooks). ~65 Java files, Maven multi-module, Java 27, JavaFX 28-ea, Guice (DI), Log4j2, Guava, commons-*. Root package `dev.tim9h.rcp`.

## Modules (`pom.xml` `<modules>` order = build order)
- `rcp.api` – public plugin API: `event`, `logging`, `service`, `settings`, `spi` (plugin SPI) under `src/main/java/dev/tim9h/rcp/`.
- `rcp.controls` – reusable JavaFX controls.
- `rcp.core` – the application. Main class `dev.tim9h.rcp.core.Main` (shaded runnable JAR; `UiApplication` is the JavaFX app). Packages: `event, logging, plugin, service, settings, ui, util, windows`. Sass in `src/main/resources/sass` is compiled to `css` by dart-sass-maven-plugin during build (generated `*.css` is gitignored – never commit it). SPI registrations: `src/main/resources/META-INF/services`.
- `rcp.cli` – CLI view/command handling.
- `rcp.plugin.archetype` – Maven archetype for new plugins (not in `<modules>`; build separately).
- Config: dependency/plugin versions are all in the root `pom.xml` properties/`dependencyManagement` – add new versions there. `.mvn/jvm.config` sets `--enable-native-access=ALL-UNNAMED`. No linter/formatter config; follow existing style (tabs, Log4j2 logging via `Logger`, Guice injection).

## Build / test (validated; Maven 4.0.0-rc-6, JDK Temurin 27)
- Always use JDK 27 (`java -version`); older JDKs fail on `java.version=27`.
- Build + test: `mvn -B verify` (CI command for PRs). Full build ≈ 5–10 s once deps are cached. `mvn -B test` also works.
- There are currently **no unit tests** (`src/test` is empty/resources only), so "tests pass" means the build compiles. Add tests under `<module>/src/test/java` if you add logic.
- Dependency `dev.tim9h:collections` comes from GitHub Packages (`https://maven.pkg.github.com/tim9h/collections`) and needs credentials in `settings.xml` (server id `gh-read-packages`; CI uses `PACKAGES_READ_USERNAME`/`PACKAGES_READ_TOKEN`). If resolution fails with 401/403 and the artifact isn't in `~/.m2`, that's an environment issue, not a code bug. Offline (`-o`) works when cached.
- Observed once: `maven-jar-plugin` failed on `rcp.core` with "Could not create modular JAR file" right after a previous build; re-running `mvn -B verify` succeeded. If it recurs, run `mvn clean verify`.
- Always run `mvn -B verify` before finishing; do not run `deploy` (publishes to GitHub Packages).

## Run (manual, Windows GUI – not usable in headless agents)
`javaw.exe -Dlogpath=<dir> --enable-native-access=ALL-UNNAMED -jar rcp.core\target\rcp.core-<version>.jar`
CLI args: `--blacklist <plugin>...` | `--whitelist <plugin>...` (mutually exclusive), `--setting key=value...`. Settings persist in `~/.rcprc`.

## CI (`.github/workflows/maven.yml`)
Triggers on push/PR to `master` and `develop`. Ubuntu, JDK 27 temurin: PRs run `mvn -B verify`; pushes run `mvn -B deploy` and, on `master`, create a GitHub release with `rcp.core/target/rcp.core-<version>.jar`. Dependabot is configured in `.github/dependabot.yml`. Code must therefore compile on Linux (don't break compilation with Windows-only imports outside Windows-specific packages such as `core/windows`).

## Tips
- Repo root files: `pom.xml`, `README.md`, `.mvn/`, `.github/`, module dirs. `target/` and Eclipse files (`.project`, `.classpath`, `.settings`) are gitignored/IDE-only.
- Commit style: conventional commits, e.g. `refactor(command): ...`, `build(pom): ...`.
- Public API changes in `rcp.api` affect all external plugin repos; keep them backward compatible where possible.
