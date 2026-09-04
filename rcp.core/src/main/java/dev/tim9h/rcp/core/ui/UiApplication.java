package dev.tim9h.rcp.core.ui;

import java.io.IOException;

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

	private static final double COLLAPSED_HEIGHT = 1.0;

	private static final Duration ANIMATION_DURATION = Duration.millis(100);

	private final DoubleProperty animatedHeight = new SimpleDoubleProperty();

	private Timeline heightAnimation;

	private boolean expanded = false;

	private static String[] argsGlobal;

	private static final double WINDOW_VERTICAL_PADDING = 13.0;

	private Provider hotkeyProvider;

	private static final double HIDDEN_ROOT_OPACITY = 0.01;

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

		// Make sure JavaFX has applied CSS and calculated the layout
		cardContainer.applyCss();
		cardContainer.layout();

		maxHeight = Math.ceil(cardContainer.prefHeight(-1)) + WINDOW_VERTICAL_PADDING;

		initAnimation();

		if (settings.getBoolean(SettingsConsts.BLUR_ENABLED).booleanValue() && WindowsUtils.isWindows()) {
			// apply backdrop filter effect
			Blur.loadBlurLibrary();
			Blur.applyBlur(stage, Blur.BLUR_BEHIND);
		}
		scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, _ -> coreService.shutdown());

		modeService.initDefaultModes();
	}

	private Stage createStage(Stage hiddenStage) {
		var result = new Stage();

		result.initOwner(hiddenStage);
		result.setX(calculateXposition());
		result.setY(0);
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
		animatedHeight.addListener((_, _, newValue) -> stage.setHeight(newValue.doubleValue()));
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
			show(fromHotkey);
		} else {
			hide(fromHotkey);
		}
	}

	private void show(boolean fromHotkey) {
		stage.setOpacity(1.0);
		stage.getScene().getRoot().setOpacity(HIDDEN_ROOT_OPACITY);

		if (!settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
			stage.setHeight(maxHeight);
			stage.getScene().getRoot().setOpacity(1.0);
			eventManager.post(new CcEvent(CcEvent.EVENT_SHOWN));
			return;
		}

		if (fade == null) {
			fade = new FadeTransition(ANIMATION_DURATION, stage.getScene().getRoot());
		}

		fade.stop();
		fade.setFromValue(HIDDEN_ROOT_OPACITY);
		fade.setToValue(1.0);
		fade.play();

		animateHeight(maxHeight, () -> {
			eventManager.post(new CcEvent(CcEvent.EVENT_SHOWN));
			stage.requestFocus();
		});
	}

	private void hide(boolean fromHotkey) {
		if (!settings.getBoolean(SettingsConsts.ANIMATIONS_ENABLED).booleanValue()) {
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
		animateHeight(COLLAPSED_HEIGHT, () -> {
			makeStageInvisible();
			eventManager.post(new CcEvent(CcEvent.EVENT_HIDDEN));
			if (fromHotkey) {
				unfocusStage();
			}
		});
	}

	private void makeStageInvisible() {
		stage.setHeight(COLLAPSED_HEIGHT);
		stage.setOpacity(1.0);
		stage.getScene().getRoot().setOpacity(HIDDEN_ROOT_OPACITY);
	}

	private void createTray() {
		themeService.createThemeMenu();
		tray.createMenuItem("Open plugins directory", pluginLoader::openPluginsDirectory);
		tray.createMenuItem("Reposition", this::reposition);
		tray.createMenuItem("Restart Application", coreService::restartApplication, true);
		tray.createMenuItem("Reload Settings", settings::loadProperties);
		tray.createMenuItem("Open Settings", settings::openSettingsFile, true);
		tray.createMenuItem("Exit", coreService::shutdown);
		tray.createDoubleClickAction(() -> Platform.runLater(() -> setExpanded(!expanded, false)));
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
		hotkeyProvider = Provider.getCurrentProvider(false);
		hotkeyProvider.register(KeyStroke.getKeyStroke(settings.getString(SettingsConsts.HOT_KEY)),
				_ -> Platform.runLater(() -> setExpanded(!expanded, true)));
	}

	private void subscribeToUiEvents() {
		eventManager.listen("reposition", _ -> reposition());
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> reposition());
		eventManager.listen(CcEvent.EVENT_THEME_CHANGED, _ -> {
			setExpanded(true, false);
			stage.requestFocus();
		});
	}

	private void reposition() {
		stage.setX(calculateXposition());
		stage.setY(0);
		stage.setWidth(settings.getDouble(SettingsConsts.WIDTH).doubleValue());
	}

	@Override
	public void stop() throws Exception {
		// unregister/shutdown hotkey provider here
		// unregister EventManager listeners here
		// stop animations
		// remove tray resources if necessary
		if (heightAnimation != null) {
			heightAnimation.stop();
		}
		if (fade != null) {
			fade.stop();
		}
		super.stop();
	}

}