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

	private static final String CSS_BUTTON = "-fx-background-color: transparent; -fx-padding: 4 8 4 8; -fx-text-alignment: left;";

	private static final String CSS_MENU = "-fx-background-color: white; -fx-padding: 5; -fx-border-color: gray; -fx-border-width: 1;";

	private static final Logger logger = LogManager.getLogger(FxSystemTray.class);

	private Stage menuStage;

	private VBox menuPane;

	private static Stage hiddenOwnerStage;

	private final List<SubMenuDef> subMenus = new ArrayList<>();

	private TrayIcon trayIcon;

	private Timer timer;

	private int trayIconClicks;

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
			menuPane.getStyleClass().add("traymenu");
			menuPane.setStyle(CSS_MENU);
			menuStage = new Stage(StageStyle.UNDECORATED);
			menuStage.setAlwaysOnTop(true);
			menuStage.initOwner(hiddenOwnerStage);
			menuStage.setScene(new Scene(menuPane));
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
			btn.setStyle(CSS_BUTTON);
			btn.setMaxWidth(Double.MAX_VALUE);
			btn.setOnAction(_ -> {
				action.run();
				menuStage.hide();
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
			var arrow = new Label("▶");
			HBox.setHgrow(region, Priority.ALWAYS);
			hbox.getChildren().addAll(labelNode, region, arrow);
			var submenuButton = new Button();
			submenuButton.setGraphic(hbox);
			submenuButton.setMaxWidth(Double.MAX_VALUE);
			submenuButton.setFocusTraversable(true);
			submenuButton.setStyle(CSS_BUTTON);
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
				var submenuPopup = new Popup();
				var submenuPane = new VBox();
				submenuPane.setAlignment(Pos.TOP_LEFT);
				submenuPane.setStyle(CSS_MENU);
				submenuPane.getStyleClass().add("traymenu");
				for (var item : def.items) {
					var hbox = new HBox();
					var checkLabel = new Label(item.checked ? "✓" : "");
					checkLabel.setPrefWidth(16);
					var spacer = new Label(" ");
					var itemLabel = new Label(item.label);
					hbox.getChildren().addAll(checkLabel, spacer, itemLabel);
					var btn = new Button();
					btn.setGraphic(hbox);
					btn.setAlignment(Pos.BASELINE_LEFT);
					btn.setStyle(CSS_BUTTON);
					btn.setMaxWidth(Double.MAX_VALUE);
					btn.setOnAction(_ -> {
						if (item.checkable) {
							var updatedItems = def.items.stream()
									.map(i -> new MenuItemData(i.label, i.action, i.checkable,
											i.label.equals(item.label)))
									.collect(Collectors.toList());
							def.items.clear();
							def.items.addAll(updatedItems);
							def.submenuPopup = null;
							def.submenuPane = null;
							showMenuAndSubmenus(menuStage.getX(), menuStage.getY());
						}
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
			def.parentNode.setOnMouseEntered(null);
			def.parentNode.setOnMouseExited(null);
			def.submenuPane.setOnMouseEntered(null);
			def.submenuPane.setOnMouseExited(null);
			def.parentNode.setOnMouseEntered(_ -> {
				hideTimer.stop();
				showSubmenuLeft(def.submenuPopup, def.submenuPane, def.parentNode, menuStage);
			});
			def.parentNode.setOnMouseExited(_ -> hideTimer.playFromStart());
			def.submenuPane.setOnMouseEntered(_ -> hideTimer.stop());
			def.submenuPane.setOnMouseExited(_ -> hideTimer.playFromStart());
			if (def.parentNode instanceof Button btn) {
				btn.setOnKeyPressed(event -> {
					switch (event.getCode()) {
					case RIGHT:
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

	private void showSubmenuLeft(Popup submenuPopup, VBox submenuPane, Node parentNode, Stage menuStage) {
		submenuPopup.hide();
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

	private void closeAllMenus() {
		menuStage.hide();
		for (var def : subMenus) {
			if (def.submenuPopup != null)
				def.submenuPopup.hide();
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

}
