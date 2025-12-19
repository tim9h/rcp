package dev.tim9h.rcp.core.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import javax.swing.KeyStroke;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.kieferlam.javafxblur.Blur;
import com.tulskiy.keymaster.common.Provider;

import dev.tim9h.rcp.core.plugin.PluginLoader;
import dev.tim9h.rcp.core.service.ModeServiceImpl;
import dev.tim9h.rcp.core.service.ThemeService;
import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.util.BasicModule;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.core.windows.WindowsUtils;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Position;
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

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

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

	@Inject
	private PluginLoader pluginLoader;

	private double maxHeight;

	private static String[] argsGlobal;

	public static void main(String[] args) {
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
		argsGlobal = args;
		launch(args);
	}

	@Override
	public void start(Stage hiddenStage) throws Exception {
		injector = Guice.createInjector(new BasicModule());
		injector.injectMembers(this);

		parseArgs();

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
		scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, _ -> shutdown());

		modeService.initDefaultModes();
	}

	private void parseArgs() throws ParseException {
		var options = new Options();

		var optionBlacklist = new Option("b", "blacklist", true, "Do not activate specific plugins");
		optionBlacklist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionBlacklist);

		var optionWhitelist = new Option("w", "blacklist", true, "Only activate specific plugins");
		optionWhitelist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionWhitelist);

		var optionSetting = new Option("s", "setting", true, "Overwrite persisted setting");
		optionSetting.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionSetting);

		var parser = new DefaultParser();
		var parse = parser.parse(options, argsGlobal);

		if (parse.hasOption(optionBlacklist) && parse.hasOption(optionWhitelist)) {
			throw new ParseException("Invalid combination of options: whitelist and blacklist");
		} else if (parse.hasOption(optionBlacklist)) {
			var blacklist = Arrays.stream(parse.getOptionValues(optionBlacklist)).map(String::toLowerCase).toList();
			pluginLoader.setPluginBlacklist(blacklist);
		} else if (parse.hasOption(optionWhitelist)) {
			var whitelist = Arrays.stream(parse.getOptionValues(optionWhitelist)).map(String::toLowerCase).toList();
			pluginLoader.setPluginWhitelist(whitelist);
		}
		if (parse.hasOption(optionSetting)) {
			settings.addOverwrites(Arrays.asList(parse.getOptionValues(optionSetting)));
		}
	}

	private Stage createStage(Stage hiddenStage) {
		stage = new Stage();
		stage.initOwner(hiddenStage);
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
		makeStageInvisible();
		stage.setTitle(settings.getString(SettingsConsts.APPLICATION_TITLE));
		stage.setAlwaysOnTop(true);
		stage.setResizable(false);
		stage.initStyle(StageStyle.TRANSPARENT);

		// show panel when clicking on top
		stage.addEventFilter(MouseEvent.MOUSE_PRESSED, _ -> {
			if (stage.getHeight() < 20) { // height is greater than 1 with DPI scaling
				toggleVisibility(true, false);
			}
		});

		// hide panel on focus loss
		stage.focusedProperty().addListener((_, oldval, newval) -> {
			if (Boolean.TRUE.equals(oldval) && Boolean.FALSE.equals(newval)) {
				toggleVisibility(false, false);
			}
		});

		return stage;
	}

	public void initNodes(VBox vbox) {
		var plugins = pluginLoader.loadPlugins();
		plugins.forEach(card -> initPluginUi(vbox, card));

		// create spacer between middle and bottom cards
		if (settings.getBoolean(SettingsConsts.BOTTOM_SPACER).booleanValue()) {
			var upperCards = plugins.stream().filter(card -> card.getGravity().position() != Position.BOTTOM).count();
			var spacer = new Region();
			VBox.setVgrow(spacer, Priority.ALWAYS);
			vbox.getChildren().add((int) upperCards, spacer);
		}
	}

	private double calculateXposition() {
		Screen screen = null;
		var index = 0;
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
			makeStageInvisible();
		}
		eventManager.post(new CcEvent(CcEvent.EVENT_HIDDEN));
	}

	private void makeStageInvisible() {
		stage.setHeight(1);
		stage.setOpacity(0.01f);
	}

	private void createTray() {
		themeService.createThemeMenu();
		tray.createMenuItem("plugins", "Open plugins directory", pluginLoader::openPluginsDirectory, true);
		tray.createMenuItem("reposition", "Reposition", this::reposition);
		tray.createMenuItem("reload", "Restart Application", this::restartApplication);
		tray.createMenuItem("f5settings", "Reload Settings", settings::loadProperties);
		tray.createMenuItem("settings", "Open Settings", settings::openSettingsFile, true);
		tray.createMenuItem("exitApp", "Exit", this::shutdown);
		tray.createDoubleClickAction(() -> Platform.runLater(() -> toggleVisibility(true, false)));
	}

	private void initPluginUi(VBox vbox, CCard plugin) {
		plugin.getStylesheet().ifPresent(scene.getStylesheets()::add);
		try {
			plugin.getNode().ifPresent(vbox.getChildren()::add);
			logger.info(() -> "Plugin UI loaded: " + plugin.getName());
		} catch (IOException e) {
			logger.error(() -> "Unable to initialize plugin UI for " + plugin.getName(), e);
		}
	}

	private void initGlobalHotkeys() {
		// register hotkey to show/hide even if other window has focus
		Provider.getCurrentProvider(false).register(KeyStroke.getKeyStroke(settings.getString(SettingsConsts.HOT_KEY)),
				_ -> Platform.runLater(() -> toggleVisibility(true, true)));
	}

	private void restartApplication() {
		logger.debug(() -> "Restarting application");
		try {
			var javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw";
			var sourcepath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile()
					.toString();
			if (Strings.CS.endsWith(sourcepath, ".jar")) {
				new ProcessBuilder(javaBin, "-jar", sourcepath).start();
				eventManager.post(new CcEvent(CcEvent.EVENT_RESTARTING));
				shutdown();
			} else {
				logger.warn(() -> "Unable to restart application: Not in jar mode");
				eventManager.echo("Unable to restart: Not in jar mode");
			}
		} catch (IOException | URISyntaxException e) {
			logger.error(() -> "Unable to restart application", e);
		}
	}

	private void shutdown() {
		logger.debug(() -> "Shutting down");
		eventManager.echo("kthxbye.");
		eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING));
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error(() -> "Error while shutdown", e);
				Thread.currentThread().interrupt();
			}
			pluginLoader.getPlugins().forEach(CCard::onShutdown);
			tray.removeTrayIcon();
			Platform.exit();
			Platform.runLater(() -> System.exit(0));
		});
	}

	private void prepareShutdown() {
		logger.debug(() -> "Shutting down immediately");
		CompletableFuture.runAsync(() -> {
			pluginLoader.getPlugins().forEach(CCard::onShutdown);
			tray.removeTrayIcon();
		}).thenRun(() -> eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING_FINISHED)));
	}

	private void subscribeToDefaultEvents() {
		eventManager.listen("exit", _ -> shutdown());
		eventManager.listen("exitimmediately", _ -> prepareShutdown());
		eventManager.listen("setting", this::handleSettingCommand);
		eventManager.listen("settings", this::handleSettingsCommand);
		eventManager.listen("restart", _ -> restartApplication());
		eventManager.listen("reposition", _ -> reposition());
		eventManager.listen("clear", _ -> eventManager.clear());
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> reposition());
		eventManager.listen(CcEvent.EVENT_THEME_CHANGED, _ -> {
			show();
			stage.requestFocus();
		});
		eventManager.listen("logs", _ -> openLogFile());
	}

	private void handleSettingCommand(Object[] args) {
		if (args == null) {
			eventManager.echo("Missing setting key");
		} else if (args.length == 1 && StringUtils.split((String) args[0], "=").length == 2) {
			var split = StringUtils.split((String) args[0], "=");
			settings.persist(split[0], split[1]);
			logger.info(() -> "Setting " + split[0] + " persisted");
			eventManager.echo("Setting persisted");
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
		if ("reload".equals(join)) {
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
			settings.openSettingsFile();
		}
	}

	private void openLogFile() {
		var ctx = (LoggerContext) LogManager.getContext(false);
		var config = ctx.getConfiguration();
		var appenders = config.getAppenders();
		appenders.values().stream().filter(FileAppender.class::isInstance).map(FileAppender.class::cast)
				.map(FileAppender::getFileName).findFirst().ifPresent(fileName -> {
					try {
						Desktop.getDesktop().open(new File(fileName));
					} catch (IOException e) {
						logger.error(() -> "Unable to open log file", e);
					}
				});

	}

	private void reposition() {
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
	}

}