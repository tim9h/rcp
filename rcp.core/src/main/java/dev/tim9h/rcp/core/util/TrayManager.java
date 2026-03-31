package dev.tim9h.rcp.core.util;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.service.ModeService;
import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

@Singleton
public class TrayManager {

	@InjectLogger
	private Logger logger;

	private TrayIcon trayIcon;

	private SystemTray tray;

	private int trayIconClicks;

	private Timer timer;

	private Stage menuStage;
	private VBox menuPane;

	// Hidden owner stage to prevent menuStage from showing in taskbar
	private static Stage hiddenOwnerStage;

	@Inject
	private EventManager eventManager;

	@Inject
	private ModeService modeService;

	@Inject
	private Settings settings;

	@Inject
	public TrayManager(Injector injector) {
		injector.injectMembers(this);
		tray = SystemTray.getSystemTray();
		var image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon_small.png"));

		trayIcon = new TrayIcon(image, settings.getString(SettingsConsts.APPLICATION_TITLE));
		trayIcon.setImageAutoSize(true);

		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			logger.warn(() -> "Unable add tray icon to tray: " + e.getMessage());
		}

		timer = new Timer("trayDoubleclickListener");

		// JavaFX menu setup
		Platform.runLater(() -> {
			// Create hidden owner stage if not already present
			if (hiddenOwnerStage == null) {
				hiddenOwnerStage = new Stage(StageStyle.UTILITY);
				hiddenOwnerStage.setOpacity(0);
				hiddenOwnerStage.setWidth(1);
				hiddenOwnerStage.setHeight(1);
				hiddenOwnerStage.setIconified(true);
				hiddenOwnerStage.setAlwaysOnTop(false);
				hiddenOwnerStage.show();
				hiddenOwnerStage.toBack();
			}
			menuPane = new VBox();
			menuPane.setAlignment(Pos.TOP_LEFT);
			menuPane.setStyle(
					"-fx-background-color: white; -fx-padding: 5; -fx-border-color: gray; -fx-border-width: 1;");
			// Use only UNDECORATED style to avoid window decoration and taskbar icon
			menuStage = new Stage(StageStyle.UNDECORATED);
			menuStage.setAlwaysOnTop(true);
			menuStage.initOwner(hiddenOwnerStage);
			menuStage.setScene(new Scene(menuPane));
			// Add ESC key handler to close all menus
			menuStage.getScene().setOnKeyPressed(event -> {
				if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
					closeAllMenus();
				}
			});
			// Listen for application window focus loss
			menuStage.getScene().getWindow().focusedProperty().addListener((_, _, isNowFocused) -> {
				if (!isNowFocused) {
					closeAllMenus();
				}
			});
		});

		// Show JavaFX menu on tray icon right-click
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
					var mouse = MouseInfo.getPointerInfo().getLocation();
					Platform.runLater(() -> {
						var menuWidth = menuStage.getWidth();
						var menuHeight = menuStage.getHeight();
						Screen targetScreen = null;
						for (var screen : Screen.getScreens()) {
							var bounds = screen.getBounds();
							if (bounds.contains(mouse.getX(), mouse.getY())) {
								targetScreen = screen;
								break;
							}
						}
						if (targetScreen == null) {
							targetScreen = Screen.getPrimary();
						}
						var visualBounds = targetScreen.getVisualBounds();
						var x = mouse.getX();
						var y = mouse.getY();
						if (x + menuWidth > visualBounds.getMaxX()) {
							x = visualBounds.getMaxX() - menuWidth;
						}
						if (y + menuHeight > visualBounds.getMaxY()) {
							y = visualBounds.getMaxY() - menuHeight;
						}
						if (x < visualBounds.getMinX())
							x = visualBounds.getMinX();
						if (y < visualBounds.getMinY())
							y = visualBounds.getMinY();
						showMenuAndSubmenus(x, y);
					});
				}
			}
		});

		subscribeToEvents();
	}

	public void subscribeToEvents() {
		eventManager.post(new CcEvent("tray created"));
		eventManager.listen(CcEvent.EVENT_TOAST, args -> {
			if (args.length > 1) {
				showToast((String) args[0], (String) args[1]);
			}
		});
	}

	public void showToast(String caption, String text) {
		if (!modeService.isModeActive("dnd")) {
			trayIcon.displayMessage(caption, text, TrayIcon.MessageType.NONE);
		} else {
			logger.debug(() -> "Not showing toast due to DND mode");
		}
	}

	// JavaFX menu item creation
	public void createMenuItem(String name, String label, Runnable action) {
		Platform.runLater(() -> {
			var btn = new Button(label);
			btn.setAlignment(Pos.BASELINE_LEFT);
			btn.setStyle("-fx-background-color: transparent; -fx-padding: 4 8 4 8; -fx-text-alignment: left;");
			btn.setId(name);
			btn.setMaxWidth(Double.MAX_VALUE);
			btn.setOnAction(_ -> {
				action.run();
				menuStage.hide();
			});
			menuPane.getChildren().add(btn);
		});
	}

	public void createMenuItem(String name, String label, Runnable action, boolean withSeparator) {
		createMenuItem(name, label, action);
		if (withSeparator) {
			Platform.runLater(() -> {
				var sep = new Line(0, 0, 120, 0);
				sep.setStyle("-fx-stroke: #ccc; -fx-stroke-width: 1;");
				menuPane.getChildren().add(sep);
			});
		}
	}

	public void createDoubleClickAction(Runnable action) {
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					trayIconClicks++;
					var task = new TimerTask() {
						@Override
						public void run() {
							trayIconClicks = 0;
						}
					};
					timer.schedule(task, 500l);
					if (trayIconClicks == 2) {
						action.run();
						trayIconClicks = 0;
					}
				}
			}
		});
	}

	public void removeTrayIcon() {
		tray.remove(trayIcon);
	}

	// Data class for menu items
	public static class MenuItemData {
		public final String label;
		public final Runnable action;
		public final boolean checkable;
		public final boolean checked;

		public MenuItemData(String label, Runnable action) {
			this(label, action, false, false);
		}

		public MenuItemData(String label, Runnable action, boolean checkable, boolean checked) {
			this.label = label;
			this.action = action;
			this.checkable = checkable;
			this.checked = checked;
		}
	}

	// Data class for submenu definitions
	private static class SubMenuDef {
		final Node parentNode;
		final List<MenuItemData> items;
		Popup submenuPopup;
		VBox submenuPane;

		SubMenuDef(Node parentNode, List<MenuItemData> items) {
			this.parentNode = parentNode;
			this.items = items;
		}
	}

	private final List<SubMenuDef> subMenus = new ArrayList<>();

	// Submenu creation for JavaFX tray menu
	public void createSubMenu(String label, List<MenuItemData> items) {
		Platform.runLater(() -> {
			Button submenuButton = new Button(label + "  ▶");
			submenuButton.setMaxWidth(Double.MAX_VALUE);
			submenuButton.setFocusTraversable(true);
			submenuButton.setAlignment(Pos.BASELINE_LEFT);
			// Style to match other menu entries
			submenuButton
					.setStyle("-fx-background-color: transparent; -fx-padding: 4 8 4 8; -fx-text-alignment: left;");
			menuPane.getChildren().add(submenuButton);
			SubMenuDef def = new SubMenuDef(submenuButton, items);
			subMenus.add(def);
		});
	}

	// After menuStage.show() in showMenuAndSubmenus, create all submenu stages
	// eagerly
	private void showMenuAndSubmenus(double x, double y) {
		// 1. Estimate menu size if not yet shown
		var menuWidth = menuStage.getWidth();
		var menuHeight = menuStage.getHeight();
		if (menuWidth == 0 || menuHeight == 0) {
			menuPane.applyCss();
			menuPane.layout();
			menuWidth = menuPane.prefWidth(-1);
			menuHeight = menuPane.prefHeight(-1);
		}
		menuStage.setX(x);
		menuStage.setY(y);
		menuStage.show();
		menuStage.toFront();
		// 2. After showing, reposition with actual size
		Platform.runLater(() -> {
			var actualWidth = menuStage.getWidth();
			var actualHeight = menuStage.getHeight();
			var newX = menuStage.getX();
			var newY = menuStage.getY();
			Screen targetScreen = null;
			for (var screen : Screen.getScreens()) {
				var bounds = screen.getBounds();
				if (bounds.contains(newX, newY)) {
					targetScreen = screen;
					break;
				}
			}
			if (targetScreen == null) {
				targetScreen = Screen.getPrimary();
			}
			var visualBounds = targetScreen.getVisualBounds();
			if (newX + actualWidth > visualBounds.getMaxX()) {
				newX = visualBounds.getMaxX() - actualWidth;
			}
			if (newY + actualHeight > visualBounds.getMaxY()) {
				newY = visualBounds.getMaxY() - actualHeight;
			}
			if (newX < visualBounds.getMinX())
				newX = visualBounds.getMinX();
			if (newY < visualBounds.getMinY())
				newY = visualBounds.getMinY();
			menuStage.setX(newX);
			menuStage.setY(newY);
		});
		// 3. Ensure submenu popups and handlers are always set up
		for (var def : subMenus) {
			if (def.submenuPopup == null) {
				var submenuPopup = new Popup();
				var submenuPane = new VBox();
				submenuPane.setAlignment(Pos.TOP_LEFT);
				submenuPane.setStyle(
						"-fx-background-color: white; -fx-padding: 5; -fx-border-color: gray; -fx-border-width: 1;");
				for (var item : def.items) {
					var btn = new Button(item.label);
					btn.setAlignment(Pos.BASELINE_LEFT);
					btn.setStyle("-fx-background-color: transparent; -fx-padding: 4 8 4 8; -fx-text-alignment: left;");
					btn.setMaxWidth(Double.MAX_VALUE);
					btn.setOnAction(_ -> {
						item.action.run();
						submenuPopup.hide();
						menuStage.hide();
					});
					submenuPane.getChildren().add(btn);
				}
				submenuPopup.getContent().add(submenuPane);
				def.submenuPopup = submenuPopup;
				def.submenuPane = submenuPane;
			}
			var hideTimer = new PauseTransition(Duration.millis(150));
			hideTimer.setOnFinished(_ -> def.submenuPopup.hide());
			// Remove previous handlers to avoid stacking
			def.parentNode.setOnMouseEntered(null);
			def.parentNode.setOnMouseExited(null);
			def.submenuPane.setOnMouseEntered(null);
			def.submenuPane.setOnMouseExited(null);
			// Always attach fresh handlers
			def.parentNode.setOnMouseEntered(_ -> {
				hideTimer.stop();
				showSubmenuLeft(def.submenuPopup, def.submenuPane, def.parentNode, menuStage);
			});
			def.parentNode.setOnMouseExited(_ -> hideTimer.playFromStart());
			def.submenuPane.setOnMouseEntered(_ -> hideTimer.stop());
			def.submenuPane.setOnMouseExited(_ -> hideTimer.playFromStart());
			// Keyboard navigation: open submenu with RIGHT key
			if (def.parentNode instanceof Button btn) {
				btn.setOnKeyPressed(event -> {
					switch (event.getCode()) {
					case RIGHT:
						// No action for RIGHT when opening left
						break;
					case LEFT:
						hideTimer.stop();
						showSubmenuLeft(def.submenuPopup, def.submenuPane, btn, menuStage);
						if (!def.submenuPane.getChildren().isEmpty()
								&& def.submenuPane.getChildren().get(0) instanceof Button firstBtn) {
							Platform.runLater(firstBtn::requestFocus);
						}
						break;
					default:
						break;
					}
				});
			}
		}
	}

	// Helper to robustly show submenu to the left, using prefWidth for correct
	// layout
	private void showSubmenuLeft(Popup submenuPopup, VBox submenuPane, Node parentNode, Stage menuStage) {
		submenuPopup.hide(); // Always hide first to force reposition
		submenuPane.applyCss();
		submenuPane.layout();
		var paneWidth = submenuPane.prefWidth(-1);
		var paneHeight = submenuPane.prefHeight(-1);
		var sx = menuStage.getX() - paneWidth + 2;
		var sy = menuStage.getY() + parentNode.localToScene(0, 0).getY()
				+ parentNode.getBoundsInParent().getHeight() / 2 - paneHeight / 2;
		if (sx < 0)
			sx = 0;
		submenuPopup.show(menuStage, sx, Math.max(menuStage.getY(), sy));
	}

	// Helper method to close all menus and submenus
	private void closeAllMenus() {
		menuStage.hide();
		for (var def : subMenus) {
			if (def.submenuPopup != null)
				def.submenuPopup.hide();
		}
	}

}