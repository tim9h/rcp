package dev.tim9h.rcp.core.settings;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.windows.WindowsUtils;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;

@Singleton
public class SettingsImpl implements Settings {

	@InjectLogger
	private Logger logger;

	private Properties properties;

	private Path propertiesPath;

	@Inject
	private EventManager eventManager;

	private Map<String, Object> overwrites;

	@Inject
	public SettingsImpl(Injector injector) {
		injector.injectMembers(this);
		overwrites = new HashMap<>();
		if (properties == null) {
			loadProperties();
		}
		initDefaultSettings();
		subscribeToSettingsEvents();
	}

	@Override
	public void loadProperties() {
		properties = new Properties();
		propertiesPath = Path.of(System.getProperty("user.home") + "/.rcprc");
		logger.debug(() -> "Loading properties from " + propertiesPath);

		if (!Files.exists(propertiesPath)) {
			try {
				Files.createFile(propertiesPath);
			} catch (IOException e) {
				logger.error(() -> "Unable to create properties file: " + e.getMessage());
			}
		}

		try (var inputStream = Files.newInputStream(propertiesPath)) {
			properties.load(inputStream);
		} catch (IOException e) {
			logger.error(() -> "Unable to load properties: " + e.getMessage());
		}
	}

	@Override
	public void persistProperties() {
		logger.debug(() -> "Updating persisted properties");
		try (OutputStream outputStream = Files.newOutputStream(propertiesPath)) {
			properties.store(outputStream, null);
			eventManager.post(new CcEvent(CcEvent.EVENT_SETTINGS_CHANGED));
		} catch (IOException e) {
			logger.error(() -> "Unable to persist properties in " + propertiesPath.toString() + ": " + e.getMessage());
		}
	}

	@Override
	public void addSetting(String property, String value) {
		addSettings(Map.of(property, value));
	}

	@Override
	public void addSetting(String property, List<String> values) {
		addSetting(property, values.stream().sorted().collect(Collectors.joining(",")));
	}

	@Override
	public void addSettings(Map<String, String> settings) {
		settings.entrySet().stream().forEach(set -> {
			if (!overwrites.containsKey(set.getKey())) {
				logger.debug(() -> String.format("Setting loaded: %s", set.getKey()));
			}
			properties.putIfAbsent(set.getKey(), set.getValue());
		});
	}

	@Override
	public File getSettingsFile() {
		return propertiesPath.toFile();
	}

	private String getProperty(String property) {
		if (overwrites.containsKey(property)) {
			return (String) overwrites.get(property);
		} else {
			return properties.getProperty(property);
		}
	}

	@Override
	public Integer getInt(String property) {
		return NumberUtils.createInteger(getProperty(property));
	}

	@Override
	public Double getDouble(String property) {
		return NumberUtils.createDouble(getProperty(property));
	}

	@Override
	public Boolean getBoolean(String property) {
		return Boolean.valueOf(getProperty(property));
	}

	@Override
	public String getString(String property) {
		return getProperty(property);
	}

	@Override
	public Long getLong(String property) {
		return NumberUtils.createLong(getProperty(property));
	}

	@Override
	public List<String> getStringList(String property) {
		String value = getProperty(property);
		if (value != null) {
			return Arrays.asList(value.split(","));
		}
		return Collections.emptyList();
	}

	@Override
	public Set<String> getStringSet(String property) {
		String value = getProperty(property);
		if (StringUtils.isBlank(value)) {
			return Collections.emptySet();
		}
		return new HashSet<>(Arrays.asList(value.split(",")));
	}

	@Override
	public void persist(String property, Object value) {
		if (overwrites.containsKey(property)) {
			overwrites.put(property, value);
		} else {
			properties.put(property, value);
			persistProperties();
		}
	}

	@Override
	public void persist(String property, Collection<String> value) {
		persist(property, value.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(",")));
	}

	@Override
	public void addOverwrites(List<String> settingsOverwrites) {
		if (settingsOverwrites != null) {
			settingsOverwrites.forEach(setting -> {
				var key = setting.substring(0, setting.indexOf('='));
				var val = setting.substring(key.length() + 1);
				overwrites.put(key, val);
				logger.info(() -> "Overwriting setting " + key);
			});
		}
	}

	@Override
	public Map<String, Object> getOverwrites() {
		return overwrites;
	}

	@Override
	public int getCharWidth() {
		return (int) (getInt("core.ui.stage.width").doubleValue() / 6.9);
	}

	@Override
	public void openSettingsFile() {
		if (WindowsUtils.isWindows()) {
			CompletableFuture.runAsync(() -> {
				try {
					new ProcessBuilder("cmd", "/c", "start", "/wait", "notepad", getSettingsFile().getAbsolutePath())
							.start().waitFor();
					loadProperties();
					eventManager.post(new CcEvent(CcEvent.EVENT_SETTINGS_CHANGED));
				} catch (InterruptedException | IOException e) {
					logger.warn(() -> "Unable to open settings file", e);
					Thread.currentThread().interrupt();
				}
			});
		} else {
			try {
				Desktop.getDesktop().edit(getSettingsFile());
			} catch (IOException e) {
				logger.warn(() -> "Unable to open settings file (non windows)", e);
				Thread.currentThread().interrupt();
			}
			// Reloading of properties not yet implemented
		}
	}

	private void initDefaultSettings() {
		var settingsMap = new HashMap<String, String>();
		settingsMap.put(SettingsConsts.APPLICATION_TITLE, "rcp");
		settingsMap.put(SettingsConsts.WIDTH, "500");
		settingsMap.put(SettingsConsts.BOTTOM_SPACER, "false");
		settingsMap.put(SettingsConsts.ANIMATIONS_ENABLED, "true");
		settingsMap.put(SettingsConsts.BLUR_ENABLED, "true");
		settingsMap.put(SettingsConsts.RESTORE_PREVIOUS_FOCUS, "true");
		settingsMap.put(SettingsConsts.HOT_KEY, "alt SPACE");
		settingsMap.put(SettingsConsts.FOCUS_APPLICATION, "Vivaldi");
		settingsMap.put(SettingsConsts.MONITOR, "0");
		settingsMap.put(SettingsConsts.MODES, StringUtils.EMPTY);
		settingsMap.put(SettingsConsts.THEME, "deepdarkness");
		addSettings(settingsMap);
		persistProperties();
	}

	public void handleSettingCommand(Object[] args) {
		if (args == null) {
			eventManager.echo("Missing setting key");
		} else if (args.length == 1 && StringUtils.split((String) args[0], "=").length == 2) {
			var split = StringUtils.split((String) args[0], "=");
			persist(split[0], split[1]);
			logger.info(() -> "Setting " + split[0] + " persisted");
			eventManager.echo("Setting persisted");
		} else if (args.length == 1) {
			var key = StringUtils.join(args);
			var val = getString(key);
			eventManager.echo(key, StringUtils.defaultIfBlank(val, "Setting not found"));
		} else if (args.length > 1) {
			var key = (String) args[0];
			var val = StringUtils.join(Arrays.copyOfRange(args, 1, args.length), StringUtils.SPACE);
			persist(key, val);
			logger.info(() -> "Setting " + key + " persisted");
			eventManager.echo("Setting persisted");
		}
	}

	@Override
	public void handleSettingsCommand(Object[] args) {
		var join = StringUtils.join(args);
		if ("reload".equals(join)) {
			loadProperties();
			eventManager.echo("Settings reloaded");
		} else if ("overwrites".equals(join)) {
			var overwrites = getOverwrites().keySet();
			if (!overwrites.isEmpty()) {
				eventManager.echo("Overwriting",
						StringUtils.abbreviate(StringUtils.join(overwrites, ", "), getCharWidth()));
			} else {
				eventManager.echo("No settings overwritten");
			}
		} else {
			openSettingsFile();
		}
	}

	private void subscribeToSettingsEvents() {
		eventManager.listen("setting", this::handleSettingCommand);
		eventManager.listen("settings", this::handleSettingsCommand);
	}

}
