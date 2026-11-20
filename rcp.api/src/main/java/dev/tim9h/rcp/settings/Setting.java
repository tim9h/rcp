package dev.tim9h.rcp.settings;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;

public class Setting<T> {

	private final Settings settings;

	private boolean loaded;

	private T value;

	private final T defaultValue;

	private final String settingsKey;

	public Setting(Settings settings, EventManager eventManager, String name, T defaultValue) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.settingsKey = Objects.requireNonNull(name, "name");
		this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(eventManager, "eventManager").listen(Event.EVENT_SETTINGS_CHANGED, _ -> invalidate());
	}

	public synchronized T get() {
		if (!loaded) {
			value = loadValue();
			loaded = true;
		}
		return value;
	}

	private T loadValue() {
		if (settings.getString(settingsKey) == null) {
			return defaultValue;
		}

		Object loadedValue;
		if (defaultValue instanceof String stringValue) {
			loadedValue = settings.getString(settingsKey, stringValue);
		} else if (defaultValue instanceof Integer) {
			loadedValue = Objects.requireNonNullElse(settings.getInt(settingsKey), defaultValue);
		} else if (defaultValue instanceof Double) {
			loadedValue = Objects.requireNonNullElse(settings.getDouble(settingsKey), defaultValue);
		} else if (defaultValue instanceof Boolean) {
			loadedValue = Objects.requireNonNullElse(settings.getBoolean(settingsKey), defaultValue);
		} else if (defaultValue instanceof Long) {
			loadedValue = Objects.requireNonNullElse(settings.getLong(settingsKey), defaultValue);
		} else if (defaultValue instanceof List<?>) {
			loadedValue = settings.getStringList(settingsKey);
		} else if (defaultValue instanceof Set<?>) {
			loadedValue = settings.getStringSet(settingsKey);
		} else {
			throw new IllegalArgumentException("Unsupported setting type: " + defaultValue.getClass().getName());
		}
		@SuppressWarnings("unchecked")
		var typedValue = (T) loadedValue;
		return typedValue;
	}

	private synchronized void invalidate() {
		loaded = false;
		value = null;
	}

}
