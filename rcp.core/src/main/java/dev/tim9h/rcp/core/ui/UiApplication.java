package dev.tim9h.rcp.core.ui;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.swing.KeyStroke;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.tulskiy.keymaster.common.Provider;

import dev.tim9h.javafxblur2.WindowsBackdrop;
import dev.tim9h.rcp.core.plugin.PluginLoader;
import dev.tim9h.rcp.core.service.CommandsService;
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
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.Plugin;
import dev.tim9h.rcp.spi.Position;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

	@Inject
	private CommandsService commandsService;

	private double maxHeight;

	private static final double COLLAPSED_HEIGHT = 1.0;

	private static final Duration ANIMATION_DURATION = Duration.millis(150);

	private final DoubleProperty animatedHeight = new SimpleDoubleProperty();

	private final DoubleProperty animatedY = new SimpleDoubleProperty();

	private Timeline heightAnimation;

	private Timeline positionAnimation;

	private boolean expanded = false;

	private static String[] argsGlobal;

	private static final double WINDOW_VERTICAL_PADDING = 13.0;

	private Provider hotkeyProvider;

	private static final double HIDDEN_ROOT_OPACITY = 0.01;

	private static final int APPLICATION_CORNERS = 16;

	private static final double TOP_MARGIN = 10.0;

	private boolean nativeCornersEnabled;

	private boolean blurEnabled;

	private static final double HIDDEN_STAGE_OPACITY = 0.2; // any lower value makes the stage unclickable

	public static void main(String[] args) {
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
		argsGlobal = args;
		launch(args);
	}

	@Override
	public void start(Stage hiddenStage) throws Exception {
		try {
			Guice.createInjector(new BasicModule()).injectMembers(this);
		} catch (Exception e) {
			LogManager.getLogger(UiApplication.class).error(() -> "Unable to inject members", e);
			Platform.exit();
			return;
		}

		registerExceptionHandler();

		coreService.parseArgs(argsGlobal);

		initUiCommands();
		var cardContainer = initScene();
		createTray();
		initGlobalHotkeys();
		subscribeToUiEvents();

		hiddenStage.initStyle(StageStyle.UTILITY);
		hiddenStage.setOpacity(0);
		stage = createStage(hiddenStage);

		stage.setScene(scene);

		hiddenStage.show();
		stage.show();

		blurEnabled = settings.getBoolean(SettingsConsts.BLUR_ENABLED).booleanValue() && WindowsUtils.isWindows();
		themeService.setTheme(settings.getString(SettingsConsts.THEME), true);

		// Make sure JavaFX has applied CSS and calculated the layout
		cardContainer.applyCss();
		cardContainer.layout();

		maxHeight = Math.ceil(cardContainer.prefHeight(-1)) + WINDOW_VERTICAL_PADDING;

		initAnimation();

		if (blurEnabled) {
			WindowsBackdrop.roundCorners(stage, APPLICATION_CORNERS);
		}
		scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
			event.consume();
			coreService.cleanUp().thenRun(coreService::exitApplication);
		});

		modeService.initDefaultModes();
	}

	private Stage createStage(Stage hiddenStage) {
		var result = new Stage();

		result.initOwner(hiddenStage);
		result.setX(calculateXposition());
		result.setY(calculateScreenTop());
		result.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
		result.setHeight(COLLAPSED_HEIGHT);
		result.setOpacity(0.01);
		result.setTitle(settings.getString(SettingsConsts.APPLICATION_TITLE));
		result.setAlwaysOnTop(true);
		result.setResizable(false);
		result.initStyle(StageStyle.TRANSPARENT);
		result.addEventFilter(MouseEvent.MOUSE_PRESSED, _ -> {
			if (!expanded) {
				setExpanded(true, false);
			}
		});
		result.focusedProperty().addListener((_, wasFocused, isFocused) -> {
			if (wasFocused && !isFocused && expanded) {
				setExpanded(false, false);
			}
		});
		return result;
	}

	private void initAnimation() {
		animatedHeight.addListener((_, _, newValue) -> {
			var height = newValue.doubleValue();
			stage.setHeight(height);
			if (nativeCornersEnabled && blurEnabled) {
				WindowsBackdrop.roundCorners(stage, APPLICATION_CORNERS);
			}
		});
		animatedY.addListener((_, _, newValue) -> stage.setY(newValue.doubleValue()));
	}

	private void animateHeight(double targetHeight, Runnable onFinished) {
		if (heightAnimation != null) {
			heightAnimation.stop();
		}
		var currentHeight = stage.getHeight();
		heightAnimation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(animatedHeight, currentHeight)),
				new KeyFrame(ANIMATION_DURATION, new KeyValue(animatedHeight, targetHeight, Interpolator.EASE_BOTH)));
		heightAnimation.setOnFinished(_ -> {
			if (onFinished != null) {
				onFinished.run();
			}
		});
		heightAnimation.play();
	}

	private void animatePosition(double targetY) {
		if (positionAnimation != null) {
			positionAnimation.stop();
		}
		var currentY = stage.getY();
		positionAnimation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(animatedY, currentY)),
				new KeyFrame(ANIMATION_DURATION, new KeyValue(animatedY, targetY, Interpolator.EASE_BOTH)));
		positionAnimation.play();
	}

	private void stopAnimations() {
		if (settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			if (heightAnimation != null) {
				heightAnimation.stop();
			}
			if (positionAnimation != null) {
				positionAnimation.stop();
			}
			if (fade != null) {
				fade.stop();
			}
		}
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

	private double calculateScreenTop() {
		Screen screen = null;
		var index = 0;
		for (var s : Screen.getScreens()) {
			if (index == settings.getInt(SettingsConsts.MONITOR).intValue()) {
				screen = s;
				break;
			}
			index++;
		}
		if (screen == null) {
			screen = Screen.getPrimary();
		}
		return screen.getBounds().getMinY();
	}

	private Pane initScene() {
		var cardContainer = new VBox();
		cardContainer.getStyleClass().add("card-container");
		initNodes(cardContainer);
		scene.setRoot(cardContainer);

		// hide panel when pressing ESC
		scene.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				event.consume();
				setExpanded(false, true);
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

	private void setExpanded(boolean expanded, boolean fromHotkey) {
		if (this.expanded == expanded) {
			return;
		}
		this.expanded = expanded;
		if (expanded) {
			show();
		} else {
			hide(fromHotkey);
		}
	}

	private void show() {
		stopAnimations();
		stage.setX(calculateXposition());

		var screenTop = calculateScreenTop();
		var expandedY = screenTop + TOP_MARGIN;

		stage.setOpacity(1.0);
		stage.getScene().getRoot().setOpacity(HIDDEN_ROOT_OPACITY);

		if (blurEnabled) {
			WindowsBackdrop.roundCorners(stage, APPLICATION_CORNERS);
		}

		if (!settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			stage.setY(expandedY);
			stage.setHeight(maxHeight);
			stage.getScene().getRoot().setOpacity(1.0);

			if (blurEnabled) {
				WindowsBackdrop.roundCorners(stage, APPLICATION_CORNERS);
			}

			eventManager.post(new CcEvent(CcEvent.EVENT_SHOWN));
			stage.requestFocus();
			return;
		}

		if (fade == null) {
			fade = new FadeTransition(ANIMATION_DURATION, stage.getScene().getRoot());
		}

		fade.stop();
		fade.setFromValue(HIDDEN_ROOT_OPACITY);
		fade.setToValue(1.0);
		fade.play();

		animatePosition(expandedY);

		animateHeight(maxHeight, () -> {
			if (blurEnabled) {
				WindowsBackdrop.roundCorners(stage, APPLICATION_CORNERS);
			}
			eventManager.post(new CcEvent(CcEvent.EVENT_SHOWN));
			stage.requestFocus();
		});
	}

	private void hide(boolean fromHotkey) {
		stopAnimations();
		var screenTop = calculateScreenTop();

		if (!settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			stage.setY(screenTop);
			makeStageInvisible();

			eventManager.post(new CcEvent(CcEvent.EVENT_HIDDEN));
			if (fromHotkey) {
				unfocusStage();
			}
			return;
		}
		if (fade == null) {
			fade = new FadeTransition(ANIMATION_DURATION, stage.getScene().getRoot());
		}
		fade.stop();
		fade.setFromValue(stage.getScene().getRoot().getOpacity());
		fade.setToValue(HIDDEN_ROOT_OPACITY);
		fade.play();

		animatePosition(screenTop);

		animateHeight(COLLAPSED_HEIGHT, () -> {
			if (blurEnabled) {
				WindowsBackdrop.clearRoundedCorners(stage);
			}
			makeStageInvisible();
			eventManager.post(new CcEvent(CcEvent.EVENT_HIDDEN));
			if (fromHotkey) {
				unfocusStage();
			}
		});
	}

	private void makeStageInvisible() {
		nativeCornersEnabled = false;
		if (blurEnabled) {
			WindowsBackdrop.clearRoundedCorners(stage);
		}
		stage.setHeight(COLLAPSED_HEIGHT);
		stage.setX(calculateXposition());
		stage.setY(calculateScreenTop());

		stage.setOpacity(HIDDEN_STAGE_OPACITY);
		stage.getScene().getRoot().setOpacity(HIDDEN_ROOT_OPACITY);
	}

	private void createTray() {
		themeService.createThemeMenu();
		tray.createMenuItem("Open plugins directory", pluginLoader::openPluginsDirectory);
		tray.createMenuItem("Reposition", this::reposition);
		tray.createMenuItem("Restart Application", coreService::restartApplication, true);
		tray.createMenuItem("Reload Settings", settings::loadProperties);
		tray.createMenuItem("Open Settings", settings::openSettingsFile, true);
		tray.createMenuItem("Exit", coreService::shutdownWithFeedback);
		tray.createDoubleClickAction(() -> Platform.runLater(() -> setExpanded(!expanded, false)));
	}

	private void initPluginUi(VBox vbox, Plugin plugin) {
		plugin.getStylesheet().ifPresent(scene.getStylesheets()::add);
		try {
			plugin.getNode().ifPresent(vbox.getChildren()::add);
			logger.info(() -> "Plugin UI loaded: " + plugin.getName());
		} catch (IOException e) {
			logger.error(() -> "Unable to initialize plugin UI for " + plugin.getName(), e);
			eventManager.post(CcEvent.EVENT_ALERT);
		}
	}

	private void initGlobalHotkeys() {
		hotkeyProvider = Provider.getCurrentProvider(false);
		hotkeyProvider.register(KeyStroke.getKeyStroke(settings.getString(SettingsConsts.HOT_KEY)),
				_ -> Platform.runLater(() -> setExpanded(!expanded, true)));
	}

	private void subscribeToUiEvents() {
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> reposition());
		eventManager.listen(CcEvent.EVENT_THEME_CHANGED, _ -> {
			setExpanded(true, false);
			stage.requestFocus();
		});
		eventManager.listen(CcEvent.EVENT_THEME_MODE_CHANGED, mode -> {
			var s = StringUtils.join(mode);
			if (blurEnabled) {
				logger.info(() -> "Switching backdrop tint to " + s);
				WindowsBackdrop.apply(stage, "dark".equals(s) ? WindowsBackdrop.DARK_TINT : WindowsBackdrop.LIGHT_TINT);
			}
		});
	}

	private void initUiCommands() {
		commandsService.add(new CommandBuilder().command("reposition", _ -> reposition()).getRoot());
	}

	private void reposition() {
		stage.setX(calculateXposition());
		var y = calculateScreenTop();
		if (expanded) {
			y += TOP_MARGIN;
		}
		stage.setY(y);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
	}

	@Override
	public void stop() throws Exception {
		logger.debug(() -> "Stopping JavaFX application");
		if (heightAnimation != null) {
			heightAnimation.stop();
		}
		if (positionAnimation != null) {
			positionAnimation.stop();
		}
		if (fade != null) {
			fade.stop();
		}
		nativeCornersEnabled = false;

		CompletableFuture.runAsync(() -> {
			if (hotkeyProvider != null) {
				hotkeyProvider.reset();
				hotkeyProvider.stop();
			}
		}).whenComplete((_, error) -> {
			if (error != null) {
				logger.error(() -> "Unable to clean up hotkey", error);
			}
			try {
				super.stop();
			} catch (Exception e) {
				logger.error(() -> "Unable to stop JavaFX application", e);
			}
			System.exit(0);
		});
	}

	private void registerExceptionHandler() {
		Thread.currentThread().setUncaughtExceptionHandler((_, exception) -> {
			logger.error(() -> "Unhandled JavaFX exception", exception);
			eventManager.post(CcEvent.EVENT_ALERT);
		});
	}

}