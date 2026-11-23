package dev.tim9h.rcp.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves the per-user directories used by RCP and its plugins.
 */
public final class RcpDirectories {

	private static final String APPLICATION_DIRECTORY = "rcp";

	private RcpDirectories() {
	}

	/**
	 * Returns the application data directory, such as {@code %APPDATA%\rcp} on Windows.
	 */
	public static Path getApplicationDirectory() {
		var userHome = Path.of(System.getProperty("user.home"));
		var osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		Path configDirectory;

		if (osName.startsWith("windows")) {
			var appData = System.getenv("APPDATA");
			var appDataPath = appData == null || appData.isBlank() ? null : Path.of(appData);
			configDirectory = appDataPath != null && appDataPath.isAbsolute() ? appDataPath
					: userHome.resolve("AppData").resolve("Roaming");
		} else if (osName.contains("mac")) {
			configDirectory = userHome.resolve("Library").resolve("Application Support");
		} else {
			var xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
			var xdgPath = xdgConfigHome == null || xdgConfigHome.isBlank() ? null : Path.of(xdgConfigHome);
			configDirectory = xdgPath != null && xdgPath.isAbsolute() ? xdgPath : userHome.resolve(".config");
		}

		return configDirectory.resolve(APPLICATION_DIRECTORY).toAbsolutePath().normalize();
	}

	/**
	 * Returns a plugin's directory under the application data directory.
	 *
	 * @param pluginId the plugin ID used as the directory name
	 * @return the plugin-specific data directory
	 * @throws IllegalArgumentException if the ID does not resolve to a direct child directory
	 */
	public static Path getPluginDataDirectory(String pluginId) {
		Objects.requireNonNull(pluginId, "pluginId");
		var applicationDirectory = getApplicationDirectory();
		var pluginDirectory = applicationDirectory.resolve(pluginId).normalize();
		if (!applicationDirectory.equals(pluginDirectory.getParent())) {
			throw new IllegalArgumentException("Plugin ID must be a single directory name: " + pluginId);
		}
		return pluginDirectory;
	}
}