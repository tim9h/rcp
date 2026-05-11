package dev.tim9h.rcp.controls.tray;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class FxSystemTray {

	private static final String CSS_CLASS_TRAYMENU = "traymenu";

	private static final Logger logger = LogManager.getLogger(FxSystemTray.class);

	private Stage menuStage;

	private VBox menuPane;

	private static Stage hiddenOwnerStage;

	private final List<SubMenuDef> subMenus = new ArrayList<>();

	private TrayIcon trayIcon;

	private Timer timer;

	private int trayIconClicks;

	private String coreStyle;

	private String currentTheme;

	@Inject
	public FxSystemTray(Image trayImage, String applicationTitle) {
		var tray = SystemTray.getSystemTray();
		trayIcon = new TrayIcon(trayImage, applicationTitle);
		trayIcon.setImageAutoSize(true);
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			logger.warn(() -> "Unable to add tray icon, tray menu will not work", e);
		}
		setupJavaFxMenu();
		initDoubleClickListener();
	}

	private void initDoubleClickListener() {
		timer = new Timer("trayDoubleclickListener");
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
					var mouse = MouseInfo.getPointerInfo().getLocation();
					showMenuAt(mouse.getX(), mouse.getY());
				}
			}
		});
	}

	private void setupJavaFxMenu() {
		Platform.runLater(() -> {
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
			menuPane.getStyleClass().add(CSS_CLASS_TRAYMENU);
			menuStage = new Stage(StageStyle.UNDECORATED);
			menuStage.setAlwaysOnTop(true);
			menuStage.initOwner(hiddenOwnerStage);
			var scene = new Scene(menuPane);
			if (coreStyle != null) {
				scene.getStylesheets().add(coreStyle);
			}
			if (this.currentTheme != null) {
				scene.getStylesheets().add(this.currentTheme);
			}
			menuStage.setScene(scene);
			menuStage.getScene().setOnKeyPressed(event -> {
				if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
					closeAllMenus();
				}
			});
			menuStage.getScene().getWindow().focusedProperty().addListener((_, _, isNowFocused) -> {
				if (!isNowFocused) {
					closeAllMenus();
				}
			});
		});
	}

	public void showMenuAt(double x, double y) {
		Platform.runLater(() -> showMenuAndSubmenus(x, y));
	}

	public void createMenuItem(String label, Runnable action) {
		Platform.runLater(() -> {
			var btn = new Button(label);
			btn.setAlignment(Pos.BASELINE_LEFT);
			btn.setMaxWidth(Double.MAX_VALUE);
			btn.setOnAction(_ -> {
				action.run();
				closeAllMenus();
			});
			menuPane.getChildren().add(btn);
		});
	}

	public void createMenuItem(String label, Runnable action, boolean withSeparator) {
		createMenuItem(label, action);
		if (withSeparator) {
			Platform.runLater(() -> {
				var sep = new Separator();
				menuPane.getChildren().add(sep);
			});
		}
	}

	public void createSubMenu(String label, List<MenuItemData> items) {
		Platform.runLater(() -> {
			var hbox = new HBox();
			var labelNode = new Label(label);
			var region = new Region();
			var arrow = new Label("◀");
			HBox.setHgrow(region, Priority.ALWAYS);
			hbox.getChildren().addAll(labelNode, region, arrow);
			var submenuButton = new Button();
			submenuButton.setGraphic(hbox);
			submenuButton.setMaxWidth(Double.MAX_VALUE);
			submenuButton.setFocusTraversable(true);
			menuPane.getChildren().add(submenuButton);
			var def = new SubMenuDef(submenuButton, items);
			subMenus.add(def);
		});
	}

	private void showMenuAndSubmenus(double x, double y) {
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

		for (var def : subMenus) {
			if (def.submenuPopup == null) {
				def.submenuPopup = new Popup();
				def.submenuPane = new VBox();
				def.submenuPane.getStyleClass().add(CSS_CLASS_TRAYMENU);
				def.submenuPane.setPickOnBounds(true);

				if (coreStyle != null)
					def.submenuPane.getStylesheets().add(coreStyle);
				if (currentTheme != null)
					def.submenuPane.getStylesheets().add(currentTheme);

				for (var item : def.items) {
					var hbox = new HBox();
					var checkLabel = new Label(item.checked ? "🔘" : "");
					checkLabel.setPrefWidth(16);
					var spacer = new Label(" ");
					var itemLabel = new Label(item.label);
					hbox.getChildren().addAll(checkLabel, spacer, itemLabel);
					var btn = new Button();
					btn.setGraphic(hbox);
					btn.setAlignment(Pos.CENTER_LEFT);
					btn.setMaxWidth(Double.MAX_VALUE);
					btn.setOnAction(_ -> {
						if (item.checkable) {
							var updatedItems = def.items.stream().map(
									i -> new MenuItemData(i.label, i.action, i.checkable, i.label.equals(item.label)))
									.collect(Collectors.toList());
							def.items.clear();
							def.items.addAll(updatedItems);
							def.submenuPopup.hide();
							def.submenuPopup = null;
							def.submenuPane = null;
							showMenuAndSubmenus(menuStage.getX(), menuStage.getY());
						}
						item.action.run();
						closeAllMenus();
					});
					def.submenuPane.getChildren().add(btn);
				}
				def.submenuPopup.getContent().add(def.submenuPane);

				def.hideTimer = new PauseTransition(Duration.millis(400));
				def.hideTimer.setOnFinished(_ -> def.submenuPopup.hide());

				def.parentNode.setOnMouseEntered(_ -> {
					def.hideTimer.stop();
					if (!def.submenuPopup.isShowing()) {
						showSubmenuLeft(def.submenuPopup, def.submenuPane, def.parentNode, menuStage);
					}
				});

				def.parentNode.setOnMouseExited(_ -> {
					if (def.submenuPopup.isShowing()) {
						def.hideTimer.playFromStart();
					}
				});

				// Use Event Filter to capture events from child elements
				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_ENTERED, _ -> def.hideTimer.stop());
				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, _ -> def.hideTimer.stop());
				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
					// Only start timer if the mouse actually leaves the container bounds
					if (!def.submenuPane.getBoundsInLocal().contains(e.getX(), e.getY())) {
						def.hideTimer.playFromStart();
					}
				});

				if (def.parentNode instanceof Button btn) {
					btn.setOnKeyPressed(event -> {
						if (event.getCode() == javafx.scene.input.KeyCode.LEFT) {
							def.hideTimer.stop();
							showSubmenuLeft(def.submenuPopup, def.submenuPane, btn, menuStage);
							if (!def.submenuPane.getChildren().isEmpty()
									&& def.submenuPane.getChildren().get(0) instanceof Button firstBtn) {
								Platform.runLater(firstBtn::requestFocus);
							}
						}
					});
				}
			}
		}
	}

	private void showSubmenuLeft(Popup submenuPopup, VBox submenuPane, Node parentNode, Stage menuStage) {
		submenuPane.applyCss();
		submenuPane.layout();
		var paneWidth = submenuPane.prefWidth(-1);
		var paneHeight = submenuPane.prefHeight(-1);

		var screenPoint = parentNode.localToScreen(0, 0);
		if (screenPoint == null)
			return;

		var sx = screenPoint.getX() - paneWidth;
		var sy = screenPoint.getY() + parentNode.getBoundsInLocal().getHeight() / 2 - paneHeight / 2;

		var targetScreen = Screen.getScreensForRectangle(screenPoint.getX(), screenPoint.getY(), 1, 1).get(0);
		var bounds = targetScreen.getVisualBounds();

		if (sx < bounds.getMinX()) {
			sx = screenPoint.getX() + parentNode.getBoundsInLocal().getWidth() - 1;
		}

		if (sy + paneHeight > bounds.getMaxY())
			sy = bounds.getMaxY() - paneHeight;
		if (sy < bounds.getMinY())
			sy = bounds.getMinY();

		submenuPopup.show(menuStage, sx, sy);
	}

	private void closeAllMenus() {
		if (menuStage != null)
			menuStage.hide();
		for (var def : subMenus) {
			if (def.submenuPopup != null) {
				def.hideTimer.stop();
				def.submenuPopup.hide();
			}
		}
	}

	public void removeTrayIcon() {
		SystemTray.getSystemTray().remove(trayIcon);
	}

	public TrayIcon getTrayIcon() {
		return trayIcon;
	}

	public void showToast(String caption, String text) {
		trayIcon.displayMessage(caption, text, TrayIcon.MessageType.NONE);
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

	public void applyStyle(String stylesheet) {
		this.coreStyle = stylesheet;
		Platform.runLater(() -> {
			if (this.menuStage != null) {
				updateStylesheets(menuStage.getScene().getStylesheets(), stylesheet, true);
			}
			for (var def : subMenus) {
				if (def.submenuPane != null) {
					updateStylesheets(def.submenuPane.getStylesheets(), stylesheet, true);
				}
			}
		});
	}

	public void applyTheme(String themeUrl) {
		this.currentTheme = themeUrl;
		Platform.runLater(() -> {
			if (this.menuStage != null) {
				updateStylesheets(menuStage.getScene().getStylesheets(), themeUrl, false);
			}
			for (var def : subMenus) {
				if (def.submenuPane != null) {
					updateStylesheets(def.submenuPane.getStylesheets(), themeUrl, false);
				}
			}
		});
	}

	private void updateStylesheets(List<String> stylesheets, String url, boolean isCoreStyle) {
		if (isCoreStyle) {
			if (url != null && !stylesheets.contains(url)) {
				stylesheets.add(0, url); // Core style should be first
			}
		} else {
			// Remove old themes
			stylesheets.removeIf(style -> style.contains("/css/theme_") && !url.equals(style));
			if (url != null && !stylesheets.contains(url)) {
				stylesheets.add(url);
			}
		}
	}
}