package dev.tim9h.rcp.core.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.KeyStroke;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.kieferlam.javafxblur.Blur;
import com.tulskiy.keymaster.common.Provider;

import dev.tim9h.rcp.core.service.ModeServiceImpl;
import dev.tim9h.rcp.core.service.ThemeService;
import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.util.BasicModule;
import dev.tim9h.rcp.core.util.CCardSorter;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.core.windows.WindowsUtils;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;
import dev.tim9h.rcp.spi.Position;
import dev.tim9h.rcp.spi.TreeNode;
import javafx.animation.Animation.Status;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class UiApplication extends Application {

	private static final String WHITELIST = "whitelist";

	private static final String BLACKLIST = "blacklist";

	private static final String RELOAD = "reload";

	private static final String SETTING = "setting";

	private static final String CONST_SETTINGS = "settings";

	private static final String PLUGINS = "plugins";

	private static final String CONST_REPOSITION = "reposition";

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	private ServiceLoader<CCardFactory> loader;

	private Stage stage;

	@Inject
	private Scene scene;

	private FadeTransition fade;

	@Inject
	private TrayManager tray;

	private Injector injector;

	@Inject
	private Settings settings;

	@Inject
	private WindowsUtils windowsUtils;

	@Inject
	private ModeServiceImpl modeService;

	@Inject
	private ThemeService themeService;

	private List<CCard> ccards;

	private double maxHeight;

	private static List<String> pluginBlacklist;

	private static List<String> pluginWhitelist;

	private static List<String> settingsOverwrites;

	public static void main(String[] args) throws ParseException {
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
		parseOptions(args);
		launch(args);
	}

	private static void parseOptions(String[] args) throws ParseException {
		var options = new Options();

		var optionBlacklist = new Option("b", BLACKLIST, true, "Do not activate specific plugins");
		optionBlacklist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionBlacklist);

		var optionWhitelist = new Option("w", WHITELIST, true, "Only activate specific plugins");
		optionWhitelist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionWhitelist);

		var optionSetting = new Option("s", SETTING, true, "Overwrite persisted setting");
		optionSetting.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionSetting);

		var parser = new DefaultParser();
		var parse = parser.parse(options, args);

		if (parse.hasOption(optionBlacklist) && parse.hasOption(optionWhitelist)) {
			throw new ParseException("Invalid combination of options: whitelist and blacklist");
		} else if (parse.hasOption(optionBlacklist)) {
			pluginBlacklist = Arrays.stream(parse.getOptionValues(optionBlacklist)).map(String::toLowerCase).toList();
		} else if (parse.hasOption(optionWhitelist)) {
			pluginWhitelist = Arrays.stream(parse.getOptionValues(optionWhitelist)).map(String::toLowerCase).toList();
		}
		if (parse.hasOption(optionSetting)) {
			settingsOverwrites = Arrays.asList(parse.getOptionValues(optionSetting));
		}
	}

	@Override
	public void start(Stage hiddenStage) throws Exception {
		injector = Guice.createInjector(new BasicModule());
		injector.injectMembers(this);
		settings.addOverwrites(settingsOverwrites);

		initServiceLoader();
		var cardContainer = initScene();
		createTray();
		initGlobalHotkeys();
		subscribeToDefaultEvents();

		hiddenStage.initStyle(StageStyle.UTILITY);
		hiddenStage.setOpacity(0);
		stage = createStage(hiddenStage);
		stage.setScene(scene);

		themeService.setTheme(settings.getString(SettingsConsts.THEME), true);

		hiddenStage.show();
		stage.show();
		maxHeight = cardContainer.getBoundsInParent().getHeight() + 13;

		if (settings.getBoolean(SettingsConsts.BLUR_ENABLED).booleanValue() && WindowsUtils.isWindows()) {
			// apply backdrop filter effect
			Blur.loadBlurLibrary();
			Blur.applyBlur(stage, Blur.BLUR_BEHIND);
		}
		scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> shutdown());

		modeService.initDefaultModes();
	}

	private Stage createStage(Stage hiddenStage) {
		stage = new Stage();
		stage.initOwner(hiddenStage);
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
		stage.setHeight(1);
		stage.setOpacity(0.01f);
		stage.setTitle(settings.getString(SettingsConsts.APPLICATION_TITLE));
		stage.setAlwaysOnTop(true);
		stage.setResizable(false);
		stage.initStyle(StageStyle.TRANSPARENT);

		// show panel when clicking on top
		stage.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (stage.getHeight() < 20) { // height is greater than 1 with DPI scaling
				toggleVisibility(true, false);
			}
		});

		// hide panel on focus loss
		stage.focusedProperty().addListener((obs, oldval, newval) -> {
			if (Boolean.TRUE.equals(oldval) && Boolean.FALSE.equals(newval)) {
				toggleVisibility(false, false);
			}
		});

		return stage;
	}

	private double calculateXposition() {
		Screen screen = null;
		int index = 0;
		for (Screen s : Screen.getScreens()) {
			if (index == settings.getInt(SettingsConsts.MONITOR).intValue()) {
				screen = s;
			}
			index++;
		}
		if (screen == null) {
			screen = Screen.getPrimary();
		}
		return (screen.getBounds().getMinX() + screen.getBounds().getWidth() / 2)
				- settings.getDouble(SettingsConsts.WIDTH).doubleValue() / 2;
	}

	private Pane initScene() {
		var cardContainer = new VBox();
		cardContainer.getStyleClass().add("card-container");
		initNodes(cardContainer);
		scene.setRoot(cardContainer);

		// hide panel when pressing ESC
		scene.setOnKeyReleased(event -> { // keyPressed consumes event too late
			if (event.getCode() == KeyCode.ESCAPE) {
				event.consume();
				toggleVisibility(false, true);
			}
		});
		return cardContainer;
	}

	private void unfocusStage() {
		if (WindowsUtils.isWindows()) {
			logger.debug(() -> "Unfocusing stage");
			if (settings.getBoolean(SettingsConsts.RESTORE_PREVIOUS_FOCUS).booleanValue()) {
				windowsUtils.focusPreviousWithTabSwitcher();
			} else {
				windowsUtils.setFocusToWindowsApp(settings.getString(SettingsConsts.FOCUS_APPLICATION));
			}
		} else {
			logger.warn(() -> "Unable to unfocus stage: not on windows");
		}
	}

	private void toggleVisibility(boolean show, boolean keyBind) {
		if (fade == null) {
			if (settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
				fade = new FadeTransition(Duration.millis(100), stage.getScene().getRoot());
			}
			stage.setOpacity(1.0f);
		} else if (fade.getStatus() == Status.RUNNING) {
			return;
		}

		if (show && stage.getHeight() < maxHeight) {
			show();
			stage.requestFocus();
		} else {
			hide();
			if (keyBind) {
				unfocusStage();
			}
		}
	}

	private void show() {
		if (settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			fade.setFromValue(0.0);
			fade.setToValue(1.0);
			fade.play();

			stage.setHeight(maxHeight - 100.0f);

			new Timer("showUiTimer").scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					if (stage.getHeight() < maxHeight) {
						stage.setHeight(stage.getHeight() + 1);
					} else {
						cancel();
					}
				}
			}, 0, 1);
		} else {
			stage.setHeight(maxHeight);
			stage.setOpacity(1);
		}
		eventManager.post(new CcEvent(CcEvent.EVENT_SHOWN));
	}

	private void hide() {
		if (settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			fade.setFromValue(1.0);
			fade.setToValue(0.0);
			fade.play();

			new Timer("hideUiTimer").scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					if (stage.getHeight() > maxHeight - 100.0f) {
						stage.setHeight(stage.getHeight() - 1);
					} else {
						cancel();
						stage.setHeight(1);
					}
				}
			}, 0, 1);
		} else {
			stage.setHeight(1);
			stage.setOpacity(0.01f);
		}
		eventManager.post(new CcEvent(CcEvent.EVENT_HIDDEN));
	}

	private void createTray() {
		tray.createMenuItem("exitApp", "Exit", this::shutdown, true);
		tray.createMenuItem(PLUGINS, "Open plugins directory", this::openPluginsDirectory, true);
		tray.createMenuItem(CONST_REPOSITION, "Reposition", this::reposition);
		tray.createMenuItem(RELOAD, "Restart Application", this::restartApplication);
		tray.createMenuItem("f5settings", "Reload Settings", settings::loadProperties);
		tray.createMenuItem(CONST_SETTINGS, "Open Settings", this::openSettingsFile);
		tray.createDoubleClickAction(() -> Platform.runLater(() -> toggleVisibility(true, false)));
	}

	private void initServiceLoader() {
		var jarDirectory = getJarDirectory();
		if (jarDirectory != null) {
			var pluginDirectory = Path.of(jarDirectory + "/plugins");
			logger.debug(() -> "Initializing service loader - jar mode");
			if (Files.exists(pluginDirectory)) {
				logger.debug(() -> "PLugins directory found: " + pluginDirectory);
				try (Stream<URL> urls = Files.list(pluginDirectory).filter(Files::isRegularFile)
						.filter(path -> path.toString().toLowerCase().endsWith(".jar")).map(this::toURL)) {
					var urlArray = urls.toArray(URL[]::new);
					logger.debug(() -> urlArray.length + " jars found");
					var ucl = new URLClassLoader(urlArray);
					loader = ServiceLoader.load(CCardFactory.class, ucl);
				} catch (IOException e) {
					logger.error(() -> "Unable to load plugin jars", e);
				}
			} else {
				logger.debug(() -> "PLugins directory not found: " + pluginDirectory.toFile().getAbsolutePath());
			}

		} else {
			logger.debug(() -> "Initializing service loader - non-jar-mode");
			loader = ServiceLoader.load(CCardFactory.class);
		}
	}

	private String getJarDirectory() {
		try {
			var sourcepath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			if (StringUtils.endsWithIgnoreCase(sourcepath, ".jar")) {
				return new File(sourcepath).getParentFile().getPath();
			} else {
				return null;
			}
		} catch (URISyntaxException e) {
			logger.error(() -> "Unable to detect source location", e);
			return null;
		}
	}

	private URL toURL(Path path) {
		try {
			return path.toUri().toURL();
		} catch (MalformedURLException e) {
			logger.error(() -> "Unable to create ULR for path " + path);
			return null;
		}
	}

	private void initNodes(VBox vbox) {
		ccards = loader.stream().filter(fac -> {
			if (pluginWhitelist != null) {
				var value = pluginWhitelist.contains(fac.get().getId().toLowerCase());
				if (value) {
					logger.info(() -> "Loading plugin " + fac.get().getId() + " (whitelist)");
				}
				return value;
			} else if (pluginBlacklist != null) {
				var value = !pluginBlacklist.contains(fac.get().getId().toLowerCase());
				if (!value) {
					logger.info(() -> "Skip loading plugin " + fac.get().getId() + " (blacklist)");
				}
				return value;
			}
			return true;
		}).map(fac -> {
			settings.addSettings(fac.get().getSettingsContributions());
			logger.info(() -> "Injecting " + fac.get().getClass().getSimpleName());
			return injector.getInstance(fac.get().getClass()).createCCard();
		}).sorted(new CCardSorter()).toList();
		var commands = new TreeNode<>(StringUtils.EMPTY);
		ccards.forEach(card -> {
			card.getStylesheet().ifPresent(scene.getStylesheets()::add);
			card.init();
			try {
				card.getNode().ifPresent(vbox.getChildren()::add);
				logger.info(() -> "Plugin loaded: " + card.getName());
			} catch (IOException e) {
				logger.error(() -> "Unable to initialize nodes", e);
			}
			card.initBus(eventManager);
			card.getModes().ifPresent(modes -> modes.forEach(mode -> {
				modeService.initMode(mode);
				commands.add(mode.getCommandTree());
			}));
			card.getMenuItems().ifPresent(list -> {
				Collections.reverse(list);
				list.forEach(menuItem -> tray.createMenuItem(menuItem.name(), menuItem.label(), menuItem.action()));
			});
			card.getModelessCommands().ifPresent(command -> {
				if (!command.get().isBlank()) {
					commands.add(command);
				} else {
					command.getChildren().forEach(commands::add);
				}
			});
		});
		addAdditionalCommands(commands);
		eventManager.post(new CcEvent(CcEvent.EVENT_CLI_ADD_PROPOSALS, commands.getChildren().toArray()));

		initSettings();
		settings.persistProperties();

		// create spacer between middle and bottom cards
		var upperCards = ccards.stream().filter(card -> card.getGravity().position() != Position.BOTTOM).count();
		var spacer = new Region();
		VBox.setVgrow(spacer, Priority.ALWAYS);
		vbox.getChildren().add((int) upperCards, spacer);
	}

	private TreeNode<String> addAdditionalCommands(TreeNode<String> commands) {
		commands.add(themeService.getThemeCommands());
		commands.add("restart", "exit", "modes", SETTING, "plugindir");

		var commandPlugins = new TreeNode<>(PLUGINS);
		commandPlugins.add(WHITELIST, BLACKLIST);
		commands.add(commandPlugins);

		var commandSettings = new TreeNode<>(CONST_SETTINGS);
		commandSettings.add("overwrites", RELOAD);
		commands.add(commandSettings);

		var commandReposition = new TreeNode<>(CONST_REPOSITION);
		commands.add(commandReposition);

		return commands;
	}

	private void initGlobalHotkeys() {
		// register hotkey to show/hide even if other window has focus
		Provider.getCurrentProvider(false).register(KeyStroke.getKeyStroke(settings.getString(SettingsConsts.HOT_KEY)),
				hotkey -> {
					logger.debug(() -> "Toggle Hotkey detected");
					Platform.runLater(() -> toggleVisibility(true, true));
				});
	}

	private void restartApplication() {
		logger.debug(() -> "Restarting application");
		try {
			var javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw";
			var sourcepath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile()
					.toString();
			if (StringUtils.endsWithIgnoreCase(sourcepath, ".jar")) {
				new ProcessBuilder(javaBin, "-jar", sourcepath).start();
				eventManager.post(new CcEvent(CcEvent.EVENT_RESTARTING));
				shutdown();
			} else {
				logger.warn(() -> "Unable to restart application: Not in jar mode");
				eventManager.echoAsync("Unable to restart: Not in jar mode");
			}
		} catch (IOException | URISyntaxException e) {
			logger.error(() -> "Unable to restart application", e);
		}
	}

	private void openSettingsFile() {
		if (WindowsUtils.isWindows()) {
			CompletableFuture.runAsync(() -> {
				try {
					new ProcessBuilder("cmd", "/c", "start", "/wait", "notepad",
							settings.getSettingsFile().getAbsolutePath()).start().waitFor();
					settings.loadProperties();
					eventManager.post(new CcEvent(CcEvent.EVENT_SETTINGS_CHANGED));
				} catch (InterruptedException | IOException e) {
					logger.warn(() -> "Unable to open settings file", e);
					Thread.currentThread().interrupt();
				}
			});
		} else {
			try {
				Desktop.getDesktop().edit(settings.getSettingsFile());
			} catch (IOException e) {
				logger.warn(() -> "Unable to open settings file (non windows)", e);
				Thread.currentThread().interrupt();
			}
			// Reloading of properties not yet implemented
		}
	}

	private void openPluginsDirectory() {
		var jarDirectory = getJarDirectory();
		if (jarDirectory != null) {
			try {
				var pluginDirectory = Path.of(jarDirectory + "/plugins");
				Desktop.getDesktop().open(pluginDirectory.toFile());
			} catch (IOException e) {
				logger.warn(() -> "Unable to open plugins directory: " + e.getMessage(), e);
			}
		} else {
			logger.warn(() -> "Unable to open plugins directory: Not in jar mode");
			eventManager.echo("Not in jar mode");
		}
	}

	private void initSettings() {
		var settingsMap = new HashMap<String, String>();
		settingsMap.put(SettingsConsts.APPLICATION_TITLE, "rcp");
		settingsMap.put(SettingsConsts.WIDTH, "500");
		settingsMap.put(SettingsConsts.ANIMATIONS_ENABLED, "true");
		settingsMap.put(SettingsConsts.BLUR_ENABLED, "true");
		settingsMap.put(SettingsConsts.RESTORE_PREVIOUS_FOCUS, "true");
		settingsMap.put(SettingsConsts.HOT_KEY, "alt SPACE");
		settingsMap.put(SettingsConsts.FOCUS_APPLICATION, "Vivaldi");
		settingsMap.put(SettingsConsts.MONITOR, "0");
		settingsMap.put(SettingsConsts.MODES, StringUtils.EMPTY);
		settingsMap.put(SettingsConsts.THEME, "deepdarkness");
		settings.addSettings(settingsMap);
	}

	private void shutdown() {
		logger.debug(() -> "Shutting down");
		eventManager.echoAsync("kthxbye.");
		eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING));
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				logger.error(() -> "Error while shutdown", e);
				Thread.currentThread().interrupt();
			}
			ccards.forEach(CCard::onShutdown);
			tray.removeTrayIcon();
			Platform.exit();
			System.exit(0);
		});
	}

	private void prepareShutdown() {
		logger.debug(() -> "Shutting down immediately");
		CompletableFuture.runAsync(() -> {
			ccards.forEach(CCard::onShutdown);
			tray.removeTrayIcon();
		}).thenRun(() -> eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING_FINISHED)));
	}

	private void subscribeToDefaultEvents() {
		eventManager.listen("exit", args -> shutdown());
		eventManager.listen("exitimmediately", args -> prepareShutdown());
		eventManager.listen(SETTING, this::handleSettingCommand);
		eventManager.listen(CONST_SETTINGS, this::handleSettingsCommand);
		eventManager.listen("restart", args -> restartApplication());
		eventManager.listen(PLUGINS, this::handlePluginsCommand);
		eventManager.listen("plugindir", args -> openPluginsDirectory());
		eventManager.listen(CONST_REPOSITION, args -> reposition());
	}

	private void handleSettingCommand(Object[] args) {
		if (args == null) {
			eventManager.echo("Missing setting key");
		} else if (args.length == 1) {
			var key = StringUtils.join(args);
			var val = settings.getString(key);
			eventManager.echo(key, StringUtils.defaultIfBlank(val, "Setting not found"));
		} else if (args.length > 1) {
			var key = (String) args[0];
			var val = StringUtils.join(Arrays.copyOfRange(args, 1, args.length), StringUtils.SPACE);
			settings.persist(key, val);
			logger.info(() -> "Setting " + key + " persisted");
			eventManager.echo("Setting persisted");
		}
	}

	private void handleSettingsCommand(Object[] args) {
		var join = StringUtils.join(args);
		if (RELOAD.equals(join)) {
			settings.loadProperties();
			eventManager.echo("Settings reloaded");
		} else if ("overwrites".equals(join)) {
			var overwrites = settings.getOverwrites().keySet();
			if (!overwrites.isEmpty()) {
				eventManager.echo("Overwriting",
						StringUtils.abbreviate(StringUtils.join(overwrites, ", "), settings.getCharWidth()));
			} else {
				eventManager.echo("No settings overwritten");
			}
		} else {
			hide();
			openSettingsFile();
		}
	}

	private void handlePluginsCommand(Object[] args) {
		var join = StringUtils.join(args);
		if (WHITELIST.equals(join)) {
			if (pluginWhitelist != null) {
				var list = pluginWhitelist.stream().collect(Collectors.joining(", "));
				eventManager.echo("Whitelisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
			} else {
				eventManager.echo("No plugins whitelisted");
			}
		} else if (BLACKLIST.equals(join)) {
			if (pluginBlacklist != null) {
				var list = pluginBlacklist.stream().collect(Collectors.joining(", "));
				eventManager.echo("Blacklisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
			} else {
				eventManager.echo("No plugins blacklisted");
			}
		} else {
			var pluginlist = ccards.stream().map(CCard::getName).sorted().collect(Collectors.joining(", "));
			logger.info(() -> "Active plugins: " + pluginlist);
			eventManager.echo("Active plugins", StringUtils.abbreviate(pluginlist, settings.getCharWidth()));
		}
	}

	private void reposition() {
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
	}

}