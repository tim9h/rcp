package dev.tim9h.rcp.core.service;

import java.awt.CheckboxMenuItem;
import java.awt.Menu;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.TreeNode;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

@Singleton
public class ThemeServiceImpl implements ThemeService {

	@InjectLogger
	private Logger logger;

	private Scene scene;

	private Settings settings;

	private EventManager eventManager;

	private ModeService modeService;

	private TrayManager trayManager;

	private Menu themeMenu;

	@Inject
	public ThemeServiceImpl(Scene scene, Settings settings, EventManager eventManager, ModeService modeService,
			TrayManager trayManager) {
		this.scene = scene;
		this.settings = settings;
		this.eventManager = eventManager;
		this.modeService = modeService;
		this.trayManager = trayManager;

		scene.setFill(Color.rgb(20, 20, 20, 0.01f));
		scene.getStylesheets().add(getClass().getResource("/css/core.css").toExternalForm());

		subscribeToThemeEvents();
	}

	@Override
	public void subscribeToThemeEvents() {
		eventManager.listen("theme", args -> {
			if (!modeService.isModeActive("alert")) {
				var theme = StringUtils.join(args).toLowerCase();
				eventManager.echo("Activating theme", StringUtils.capitalize(theme));
				setTheme(theme, true);
			} else {
				eventManager.echo("Alert theme active");
			}
		});
	}

	@Override
	public void setTheme(String theme, boolean persist) {
		var url = getClass().getResource(String.format("/css/theme_%s.css", theme.toLowerCase()));
		if (url == null) {
			var current = settings.getString(SettingsConsts.THEME);
			eventManager.echo("Current theme", StringUtils.capitalize(current));
		} else if (!scene.getStylesheets().contains(url.toExternalForm())) {
			logger.info(() -> "Setting theme to " + theme);
			scene.getStylesheets().add(url.toExternalForm());
			scene.getStylesheets()
					.removeIf(style -> style.contains("/css/theme_") && !url.toExternalForm().equals(style));
			if (persist) {
				settings.persist(SettingsConsts.THEME, theme);
			}
			selectThemeCheckbox(theme);
		}
	}

	private void selectThemeCheckbox(String theme) {
		for (var i = 0; i < themeMenu.getItemCount(); i++) {
			var item = themeMenu.getItem(i);
			if (item instanceof CheckboxMenuItem check) {
				check.setState(Strings.CI.equals(theme, check.getName()));
			}
		}
	}

	@Override
	public TreeNode<String> getThemeCommands() {
		var themes = getThemeNames();
		var node = new TreeNode<>("theme");
		if (themes.length != 0) {
			node.add(themes);
		}
		logger.debug(() -> "The following themes were found: " + node.getChildren());
		return node;
	}

	private List<String> getFileNames(String directory) {
		var jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		if (jarFile.isFile()) { // Run with JAR file
			return getFileNamesJarMode(directory, jarFile);
		} else { // Run with IDE
			return getFileNamesIdeMode(directory);
		}
	}

	private List<String> getFileNamesIdeMode(String directory) {
		var filenames = new ArrayList<String>();
		var url = getClass().getResource("/" + directory);
		if (url != null) {
			try {
				var apps = new File(url.toURI());
				for (var app : apps.listFiles()) {
					filenames.add(app.getName());
				}
			} catch (URISyntaxException e) {
				logger.error(() -> "Unable to getFilenames for directory " + directory + " in IDE mode", e);
			}
		}
		return filenames;
	}

	private List<String> getFileNamesJarMode(String directory, File jarFile) {
		var filenames = new ArrayList<String>();
		try (var jar = new JarFile(jarFile)) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				var name = entries.nextElement().getName();
				if (name.startsWith(directory + "/")) {
					var filename = name.substring(directory.length() + 1);
					if (!filename.isBlank()) {
						filenames.add(filename);
					}
				}
			}
		} catch (IOException e) {
			logger.error(() -> "Unable to get file names for directory " + directory + " in Jar mode", e);
		}
		return filenames;
	}

	@Override
	public void createThemeMenu() {
		themeMenu = new Menu("Theme");

		Arrays.stream(getThemeNames()).forEach(theme -> {
			var menuItem = new CheckboxMenuItem(theme);
			menuItem.setName(theme.toLowerCase());
			menuItem.addItemListener(_ -> Platform.runLater(() -> {
				setTheme(theme, true);
				eventManager.post(CcEvent.EVENT_THEME_CHANGED, theme);
			}));
			themeMenu.add(menuItem);
		});
		trayManager.createSubMenu(themeMenu, true);
	}

	private String[] getThemeNames() {
		return getFileNames("css").stream().filter(file -> file.startsWith("theme_") && !file.endsWith(".map"))
				.map(file -> file.replace("theme_", StringUtils.EMPTY).replace(".css", StringUtils.EMPTY))
				.map(StringUtils::capitalize).toArray(String[]::new);
	}

}
