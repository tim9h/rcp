---
name: review-plugin-performance
description: Review an RCP plugin (code in a sibling plugin repo or in rcp.core) for performance and memory issues. Use when asked to review, audit or optimize a plugin, or check for leaks, slow startup, UI thread blocking, or excessive resource usage.
---

# Review RCP plugins for performance and memory

Plugins implement `dev.tim9h.rcp.spi.Plugin`, are loaded from `./plugins` at startup only, and run inside a long-lived JavaFX launcher that is shown/hidden repeatedly. Anything leaked or left running stays for the whole process lifetime. Read `AGENTS.md` first for architecture.

## Procedure
1. Locate the plugin class(es) (`Plugin` implementations, `META-INF/services` registration) and list all lifecycle methods used: `init`, `initBus`, `getNode`, `onShown`, `onHidden`, `onShutdown`, `onSettingsChanged`, commands/modes.
2. Walk through the checklist below; cite `file:line` for every finding.
3. Report findings grouped by severity (High/Medium/Low) with: location, why it matters, concrete fix. Skip style nits. If nothing is found, say so.

## Checklist

### Threading / responsiveness
- No blocking work (network, disk, `Thread.sleep`, process exec, heavy parsing) on the JavaFX Application Thread: event listeners, command handlers, `onShown`/`onHidden`, `getNode`, `init`.
- UI updates from background threads go through `Platform.runLater`; avoid flooding it (coalesce high-frequency updates).
- `init()` and `getNode()` run during startup: they must be cheap; defer expensive work lazily or to a background thread.
- Use shared/bounded executors; no unbounded `new Thread`/`Executors.newCachedThreadPool`; threads should be daemon and named.

### Background activity while hidden
- Timers, `Timeline`/`Animation`, `ScheduledExecutorService`, polling and listeners should pause on `onHidden` and resume on `onShown` unless truly needed.
- Polling intervals sensible; no busy loops; HTTP calls have timeouts and are cached where data rarely changes.

### Memory leaks
- Every `eventManager.listen(...)`, JavaFX listener (`addListener`, bindings, `setOnAction` capturing `this`), global key hook, or scheduled task has a matching removal/cancel in `onShutdown`. Prefer weak listeners or unbind for short-lived nodes.
- Static collections, caches and singletons are bounded (size/TTL, e.g. Guava `CacheBuilder`) and never hold `Node`s, `Stage`s or large images.
- Nodes removed from the scene graph are not referenced elsewhere; scene graph does not grow on each `onShown`.
- Closeables are closed (streams, `HttpClient` responses, `ExecutorService`, `WatchService`, native handles/JNA resources, media players: `dispose()`).
- Images/media: scaled on load (`new Image(url, w, h, true, true, true)`), not decoded repeatedly, released when hidden if large.

### Allocation / CPU hot paths
- No work inside frequently-fired callbacks (animation pulses, mouse move, CLI keystroke/autocomplete proposals) beyond O(1); avoid regex compilation, `String.format`, JSON/XML parsing, reflection there; precompile `Pattern`s, reuse `ObjectMapper`/`HttpClient`.
- Avoid repeated `Settings` lookups of unchanged values in loops; read once and refresh in `onSettingsChanged`.
- Efficient collections, no needless copies or streams in tight loops; no logging at INFO+ in hot paths and no string concatenation for disabled log levels (use Log4j2 `{}` placeholders).
- Scene graph: avoid huge/nested node trees, heavy effects (`DropShadow`, `GaussianBlur`) and CSS reapplication per update; reuse nodes/cells; use virtualized controls (`ListView`) for long lists.

### Startup and footprint
- No unnecessary dependencies bundled; avoid duplicating libs already provided by core (Guava, Log4j2, commons).
- Static initializers and eager singletons do no I/O.
- Plugin `getStylesheet()` returns a small stylesheet; no per-show stylesheet re-adding.

## Optional verification
Where possible suggest measurement: JFR (`-XX:+FlightRecorder`), `jcmd <pid> GC.class_histogram` before/after repeated show/hide cycles (instance counts of plugin classes/`Node`s must stay flat), and VisualVM for thread counts.
