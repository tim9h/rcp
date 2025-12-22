package dev.tim9h.rcp.core.ui;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.KeyStroke;

import org.apache.logging.log4j.Logger;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.kieferlam.javafxblur.Blur;
import com.tulskiy.keymaster.common.Provider;

import dev.tim9h.rcp.core.plugin.PluginLoader;
import dev.tim9h.rcp.core.service.CoreService;
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
import dev.tim9h.rcp.spi.Plugin;
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

	@Inject
	private CoreService coreService;

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
		
		try {
			injector.injectMembers(this);
		} catch (Exception e) {
			System.err.println("Unable to inject members: " + e.getMessage());
			e.printStackTrace();
			Platform.exit();
			return;
		}

		coreService.parseArgs(argsGlobal);

		var cardContainer = initScene();
		createTray();
		initGlobalHotkeys();
		subscribeToUiEvents();

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
		scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, _ -> coreService.shutdown());

		modeService.initDefaultModes();
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
		tray.createMenuItem("reload", "Restart Application", coreService::restartApplication);
		tray.createMenuItem("f5settings", "Reload Settings", settings::loadProperties);
		tray.createMenuItem("settings", "Open Settings", settings::openSettingsFile, true);
		tray.createMenuItem("exitApp", "Exit", coreService::shutdown);
		tray.createDoubleClickAction(() -> Platform.runLater(() -> toggleVisibility(true, false)));
	}

	private void initPluginUi(VBox vbox, Plugin plugin) {
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

	private void subscribeToUiEvents() {
		eventManager.listen("reposition", _ -> reposition());
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> reposition());
		eventManager.listen(CcEvent.EVENT_THEME_CHANGED, _ -> {
			show();
			stage.requestFocus();
		});
	}

	private void reposition() {
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
	}

}