package dev.tim9h.rcp.core.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.controls.tray.MenuItemData;
import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.service.ThemeService;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.Theme;
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

	private TrayManager trayManager;

	private CommandsService commandsService;

	private Boolean darkTheme;

	private final Map<String, Theme> themes = new LinkedHashMap<>();

	private String activeThemeStylesheet;

	private boolean themeCommandRegistered;

	@Inject
	public ThemeServiceImpl(Scene scene, Settings settings, EventManager eventManager, TrayManager trayManager,
			CommandsService commandsService) {
		this.scene = scene;
		this.settings = settings;
		this.eventManager = eventManager;
		this.trayManager = trayManager;
		this.commandsService = commandsService;

		scene.setFill(Color.rgb(20, 20, 20, 0.01f));
		var stylesheet = getClass().getResource("/css/core.css").toExternalForm();
		scene.getStylesheets().add(stylesheet);
		trayManager.applyStyle(stylesheet);

		registerThemes(getBuiltInThemes());
		registerThemeCommand();
		themeCommandRegistered = true;
	}

	public void registerThemeCommand() {
		commandsService.add(new CommandBuilder().command("theme", _ -> {
			var current = settings.getString(SettingsConsts.THEME);
			eventManager.echo("Current theme", StringUtils.capitalize(current));
		}).arguments().argumentAction(theme -> {
			var newTheme = setTheme(theme, true);
			if (newTheme == null) {
				eventManager.echo("Theme not found", StringUtils.capitalize(theme));
			} else {
				eventManager.echo("Activating theme", StringUtils.capitalize(newTheme));
				trayManager.setSelection("Theme", theme);
			}
		}).children(getThemeNames()).getRoot());
	}

	@Override
	public String setTheme(String theme, boolean persist) {
		if (theme == null || theme.isBlank()) {
			return null;
		}
		var normalizedTheme = theme.toLowerCase(Locale.ROOT);
		var selectedTheme = themes.get(normalizedTheme);
		if (selectedTheme == null) {
			return null;
		} else {
			if (persist) {
				settings.persist(SettingsConsts.THEME, theme);
			}
			var themeUrl = selectedTheme.stylesheet();
			if (!scene.getStylesheets().contains(themeUrl)) {
				logger.info(() -> "Setting theme to " + theme);
				if (activeThemeStylesheet != null) {
					scene.getStylesheets().remove(activeThemeStylesheet);
				}
				scene.getStylesheets().add(themeUrl);
				activeThemeStylesheet = themeUrl;
				trayManager.applyTheme(themeUrl);
				updateWindowsBackdropEffects(selectedTheme.dark());
			}
		}
		return theme;
	}

	private void updateWindowsBackdropEffects(boolean darkThemeNew) {
		if (darkTheme == null) {
			eventManager.post(Event.EVENT_THEME_MODE_CHANGED, darkThemeNew ? "dark" : "light");
			darkTheme = darkThemeNew;
			return;
		}
		if (darkTheme && !darkThemeNew) {
			eventManager.post(Event.EVENT_THEME_MODE_CHANGED, "light");
		} else if (!darkTheme && darkThemeNew) {
			eventManager.post(Event.EVENT_THEME_MODE_CHANGED, "dark");
		}
		darkTheme = darkThemeNew;
	}

	private static boolean isDarkTheme(String theme) {
		return switch (theme.toLowerCase(Locale.ROOT)) {
		case "hoth", "laurelin", "telperion" -> false;
		default -> true;
		};
	}

	@Override
	public void registerThemes(List<Theme> themesToRegister) {
		var registeredNames = new ArrayList<String>();
		for (var theme : themesToRegister) {
			var name = theme.name().toLowerCase(Locale.ROOT);
			if (themes.putIfAbsent(name, theme) != null) {
				logger.warn(() -> "Ignoring duplicate theme '" + theme.name() + "'");
				continue;
			}
			registeredNames.add(StringUtils.capitalize(theme.name()));
		}
		if (themeCommandRegistered && !registeredNames.isEmpty()) {
			commandsService.add(
					new CommandBuilder().command("theme").children(registeredNames.toArray(String[]::new)).getRoot());
			commandsService.propagateCommands("theme");
		}
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
		var items = Arrays.stream(getThemeNames())
				.map(theme -> new MenuItemData(StringUtils.capitalize(theme), () -> Platform.runLater(() -> {
					setTheme(theme, true);
					eventManager.post(Event.EVENT_THEME_CHANGED, theme);
				}), true, settings.getString(SettingsConsts.THEME).equalsIgnoreCase(theme))).toList();
		trayManager.createSubMenu("Theme", items, true);
	}

	private String[] getThemeNames() {
		return themes.values().stream().map(Theme::name).map(StringUtils::capitalize).toArray(String[]::new);
	}

	private List<Theme> getBuiltInThemes() {
		return getFileNames("css").stream().filter(file -> file.startsWith("theme_") && file.endsWith(".css"))
				.map(file -> {
					var name = file.substring("theme_".length(), file.length() - ".css".length());
					var stylesheet = getClass().getResource("/css/" + file).toExternalForm();
					return new Theme(name, stylesheet, isDarkTheme(name));
				}).toList();
	}

}