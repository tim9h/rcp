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

import dev.tim9h.rcp.settings.Settings;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

	private Popup menuPopup;

	private VBox menuPane;

	private static Stage hiddenOwnerStage;

	private final List<SubMenuDef> subMenus = new ArrayList<>();

	private TrayIcon trayIcon;

	private Timer timer;

	private int trayIconClicks;

	private String coreStyle;

	private String currentTheme;

	private Settings settings;

	public FxSystemTray(Image trayImage, String applicationTitle, Settings settings) {
		this.settings = settings;
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
			// Prevent JavaFX from closing when the popup hides
			Platform.setImplicitExit(false);

			if (hiddenOwnerStage == null) {
				hiddenOwnerStage = new Stage(StageStyle.UTILITY);
				hiddenOwnerStage.setOpacity(0);
				hiddenOwnerStage.setWidth(1);
				hiddenOwnerStage.setHeight(1);
				hiddenOwnerStage.setX(-10000);
				hiddenOwnerStage.setY(-10000);

				hiddenOwnerStage.setScene(new Scene(new Region(), 1, 1));
				// IMPORTANT: Do not set iconified to true, it breaks OS focus tracking for
				// popups
				hiddenOwnerStage.setAlwaysOnTop(false);
				hiddenOwnerStage.show();
			}
			menuPane = new VBox();
			menuPane.setAlignment(Pos.TOP_LEFT);
			menuPane.getStyleClass().add(CSS_CLASS_TRAYMENU);
			menuPane.setFocusTraversable(true);

			menuPopup = new Popup();
			// Handled manually using custom focus listeners to ensure smooth fade out
			// transition
			menuPopup.setAutoHide(false);
			menuPopup.getContent().add(menuPane);

			if (coreStyle != null) {
				menuPane.getStylesheets().add(coreStyle);
			}
			if (this.currentTheme != null) {
				menuPane.getStylesheets().add(this.currentTheme);
			}

			menuPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
				if (event.getCode() == KeyCode.UP) {
					navigate(menuPane, -1);
					event.consume();
				} else if (event.getCode() == KeyCode.DOWN) {
					navigate(menuPane, 1);
					event.consume();
				} else if (event.getCode() == KeyCode.ESCAPE) {
					closeAllMenus();
					event.consume();
				} else if (event.getCode() == KeyCode.ENTER) {
					if (menuPane.getScene() != null && menuPane.getScene().getFocusOwner() instanceof Button btn) {
						var isSubmenuBtn = subMenus.stream().anyMatch(sm -> sm.parentNode.equals(btn));
						if (!isSubmenuBtn) {
							btn.fire();
							event.consume();
						}
					}
				}
			});

			// Close all menus when main menu loses focus, allowing smooth fade outs
			menuPopup.focusedProperty().addListener((_, _, isNowFocused) -> {
				if (!isNowFocused) {
					Platform.runLater(() -> {
						var anySubmenuFocused = subMenus.stream()
								.anyMatch(sm -> sm.submenuPopup != null && sm.submenuPopup.isFocused());
						if (!anySubmenuFocused && !menuPopup.isFocused()) {
							closeAllMenus();
						}
					});
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
			btn.setFocusTraversable(true);
			btn.setAlignment(Pos.BASELINE_LEFT);
			btn.setMaxWidth(Double.MAX_VALUE);
			btn.setOnAction(_ -> {
				action.run();
				closeAllMenus();
			});
			// Sync hover with keyboard focus
			btn.setOnMouseEntered(_ -> btn.requestFocus());
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
			// Sync hover with keyboard focus
			submenuButton.setOnMouseEntered(_ -> submenuButton.requestFocus());
			menuPane.getChildren().add(submenuButton);
			var def = new SubMenuDef(submenuButton, items);
			subMenus.add(def);
		});
	}

	public void createSubMenu(String label, List<MenuItemData> items, boolean withSeparator) {
		createSubMenu(label, items);
		if (withSeparator) {
			Platform.runLater(() -> {
				var sep = new Separator();
				menuPane.getChildren().add(sep);
			});
		}

	}

	private void showMenuAndSubmenus(double x, double y) {
		// Move the owner stage to the mouse position and request focus
		// so the popup can properly grab the OS focus context
		hiddenOwnerStage.setX(x);
		hiddenOwnerStage.setY(y);
		hiddenOwnerStage.requestFocus();

		menuPane.applyCss();
		menuPane.layout();

		menuPane.setOpacity(0);
		menuPopup.show(hiddenOwnerStage, x, y);
		fadeIn(menuPane);

		Platform.runLater(() -> {
			var actualWidth = menuPane.prefWidth(-1);
			var actualHeight = menuPane.prefHeight(-1);
			var newX = menuPopup.getX();
			var newY = menuPopup.getY();
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

			menuPopup.setX(newX);
			menuPopup.setY(newY);

			// Explicitly request focus on the popup and container
			menuPopup.requestFocus();
			menuPane.requestFocus();
		});

		for (var def : subMenus) {
			if (def.submenuPopup == null) {
				def.submenuPopup = new Popup();
				def.submenuPane = new VBox();
				def.submenuPane.getStyleClass().add(CSS_CLASS_TRAYMENU);
				def.submenuPane.setPickOnBounds(true);
				def.submenuPane.setFocusTraversable(true);

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
					btn.setFocusTraversable(true);
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
							showMenuAndSubmenus(menuPopup.getX(), menuPopup.getY());
						}
						item.action.run();
						closeAllMenus();
					});
					// Sync hover with keyboard focus in submenu
					btn.setOnMouseEntered(_ -> btn.requestFocus());
					def.submenuPane.getChildren().add(btn);
				}
				def.submenuPopup.getContent().add(def.submenuPane);

				def.hideTimer = new PauseTransition(Duration.millis(400));
				def.hideTimer.setOnFinished(_ -> fadeOutAndHide(def.submenuPopup, def.submenuPane));

				def.parentNode.setOnMouseEntered(_ -> {
					def.hideTimer.stop();
					// Ensure button has focus when hovered to sync with keyboard
					def.parentNode.requestFocus();
					if (!def.submenuPopup.isShowing()) {
						showSubmenuLeft(def.submenuPopup, def.submenuPane, def.parentNode, menuPopup);
					}
				});

				def.parentNode.setOnMouseExited(_ -> {
					if (def.submenuPopup.isShowing()) {
						def.hideTimer.playFromStart();
					}
				});

				def.submenuPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
					if (event.getCode() == KeyCode.UP) {
						navigate(def.submenuPane, -1);
						event.consume();
					} else if (event.getCode() == KeyCode.DOWN) {
						navigate(def.submenuPane, 1);
						event.consume();
					} else if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT) {
						fadeOutAndHide(def.submenuPopup, def.submenuPane);
						def.parentNode.requestFocus();
						event.consume();
					} else if (event.getCode() == KeyCode.ESCAPE) {
						closeAllMenus();
						event.consume();
					} else if (event.getCode() == KeyCode.ENTER) {
						if (def.submenuPane.getScene() != null
								&& def.submenuPane.getScene().getFocusOwner() instanceof Button focusedBtn) {
							focusedBtn.fire();
							event.consume();
						}
					}
				});

				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_ENTERED, _ -> def.hideTimer.stop());
				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, _ -> def.hideTimer.stop());
				def.submenuPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
					if (!def.submenuPane.getBoundsInLocal().contains(e.getX(), e.getY())) {
						def.hideTimer.playFromStart();
					}
				});

				// Close submenus on focus loss
				def.submenuPopup.focusedProperty().addListener((_, _, isNowFocused) -> {
					if (!isNowFocused) {
						Platform.runLater(() -> {
							if (!def.submenuPopup.isFocused() && !menuPopup.isFocused()) {
								closeAllMenus();
							}
						});
					}
				});

				if (def.parentNode instanceof Button btn) {
					btn.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
						if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT
								|| event.getCode() == KeyCode.ENTER) {
							def.hideTimer.stop();
							if (!def.submenuPopup.isShowing()) {
								showSubmenuLeft(def.submenuPopup, def.submenuPane, btn, menuPopup);
							}
							Platform.runLater(def.submenuPane::requestFocus);
							event.consume();
						}
					});
				}
			}
		}
	}

	private void showSubmenuLeft(Popup submenuPopup, VBox submenuPane, Node parentNode, Popup menuPopup) {
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

		submenuPane.setOpacity(0);
		submenuPopup.show(menuPopup, sx, sy);
		fadeIn(submenuPane);
	}

	private void closeAllMenus() {
		if (menuPopup != null && menuPopup.isShowing()) {
			fadeOutAndHide(menuPopup, menuPane);
		}
		for (var def : subMenus) {
			if (def.submenuPopup != null && def.submenuPopup.isShowing()) {
				def.hideTimer.stop();
				fadeOutAndHide(def.submenuPopup, def.submenuPane);
			}
		}
	}

	private void fadeIn(Node node) {
		if (settings.getBoolean("core.ui.animations").booleanValue()) {
			var ft = new FadeTransition(Duration.millis(150), node);
			ft.setFromValue(0.0);
			ft.setToValue(1.0);
			ft.play();
		}
	}

	private void fadeOutAndHide(Popup popup, Node node) {
		if (settings.getBoolean("core.ui.animations").booleanValue()) {
			if (!popup.isShowing())
				return;
			var ft = new FadeTransition(Duration.millis(150), node);
			ft.setFromValue(node.getOpacity());
			ft.setToValue(0.0);
			ft.setOnFinished(_ -> popup.hide());
			ft.play();
		} else {
			popup.hide();
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
			if (this.menuPane != null) {
				updateStylesheets(menuPane.getStylesheets(), stylesheet, true);
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
			if (this.menuPane != null) {
				updateStylesheets(menuPane.getStylesheets(), themeUrl, false);
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
				stylesheets.add(0, url);
			}
		} else {
			stylesheets.removeIf(style -> style.contains("/css/theme_") && !url.equals(style));
			if (url != null && !stylesheets.contains(url)) {
				stylesheets.add(url);
			}
		}
	}

	private void navigate(VBox pane, int direction) {
		var buttons = pane.getChildren().stream().filter(n -> n instanceof Button && !n.isDisabled())
				.map(n -> (Button) n).collect(Collectors.toList());

		if (buttons.isEmpty())
			return;

		var scene = pane.getScene();
		var focused = scene != null ? scene.getFocusOwner() : null;
		int currentIndex = buttons.indexOf(focused);

		if (currentIndex == -1) {
			// If moving up, jump to last. If moving down, jump to first.
			if (direction > 0) {
				buttons.get(0).requestFocus();
			} else {
				buttons.get(buttons.size() - 1).requestFocus();
			}
		} else {
			int nextIndex = (currentIndex + direction + buttons.size()) % buttons.size();
			buttons.get(nextIndex).requestFocus();
		}
	}
}