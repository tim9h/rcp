package dev.tim9h.rcp.core.plugin;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.service.CommandsService;
import dev.tim9h.rcp.core.service.ModeService;
import dev.tim9h.rcp.core.util.CCardSorter;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;

@Singleton
public class PluginLoader {

	@InjectLogger
	private Logger logger;

	private Injector injector;

	private EventManager eventManager;

	@Inject
	private Settings settings;

	@Inject
	private CommandsService commandsService;

	@Inject
	private ModeService modeService;

	@Inject
	private TrayManager tray;

	private List<String> pluginBlacklist;

	private List<String> pluginWhitelist;

	private List<CCard> plugins;

	@Inject
	public PluginLoader(Injector injector, EventManager eventManager) {
		this.injector = injector;
		this.eventManager = eventManager;
		subscribeEvents();
	}

	public List<String> getPluginBlacklist() {
		if (pluginBlacklist == null) {
			pluginBlacklist = new ArrayList<>();
		}
		return pluginBlacklist;
	}

	public void setPluginBlacklist(List<String> pluginBlacklist) {
		this.pluginBlacklist = pluginBlacklist;
	}

	public List<String> getPluginWhitelist() {
		if (pluginWhitelist == null) {
			pluginWhitelist = new ArrayList<>();
		}
		return pluginWhitelist;
	}

	public void setPluginWhitelist(List<String> pluginWhitelist) {
		this.pluginWhitelist = pluginWhitelist;
	}

	private void subscribeEvents() {
		eventManager.listen("plugindir", _ -> openPluginsDirectory());
		eventManager.listen("plugins", this::handlePluginsCommand);
	}

	private void handlePluginsCommand(Object[] args) {
		var join = StringUtils.join(args);
		if ("whitelist".equals(join)) {
			if (!getPluginWhitelist().isEmpty()) {
				var list = getPluginWhitelist().stream().collect(Collectors.joining(", "));
				eventManager.echo("Whitelisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
			} else {
				eventManager.echo("No plugins whitelisted");
			}
		} else if ("blacklist".equals(join)) {
			if (!getPluginBlacklist().isEmpty()) {
				var list = getPluginBlacklist().stream().collect(Collectors.joining(", "));
				eventManager.echo("Blacklisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
			} else {
				eventManager.echo("No plugins blacklisted");
			}
		} else {
			var pluginlist = getPlugins().stream().map(CCard::getName).sorted().collect(Collectors.joining(", "));
			logger.info(() -> "Active plugins: " + pluginlist);
			eventManager.echo("Active plugins", StringUtils.abbreviate(pluginlist, settings.getCharWidth()));
		}
	}

	public void openPluginsDirectory() {
		var jarDirectory = getJarDirectory();
		if (jarDirectory != null) {
			try {
				var pluginDirectory = Path.of(jarDirectory + "/plugins");
				Desktop.getDesktop().open(pluginDirectory.toFile());
			} catch (IOException e) {
				logger.warn(() -> "Unable to open plugins directory: " + e.getMessage(), e);
			}
		} else {
			logger.warn(() -> "Unable to open plugins directory: Not in jar mode");
			eventManager.echo("Not in jar mode");
		}
	}

	private String getJarDirectory() {
		try {
			var sourcepath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			if (Strings.CS.endsWith(sourcepath, ".jar")) {
				return new File(sourcepath).getParentFile().getPath();
			} else {
				return null;
			}
		} catch (URISyntaxException e) {
			logger.error(() -> "Unable to detect source location", e);
			return null;
		}
	}

	public List<CCard> loadPlugins() {
		plugins = createPluginFactories().stream().map(fac -> {
			logger.info(() -> "Injecting " + fac.getClass().getSimpleName());
			return injector.getInstance(fac.getClass()).createCCard();
		}).sorted(new CCardSorter()).toList();
		plugins.forEach(this::initPlugin);
		commandsService.propagateCommands();
		return plugins;
	}

	private List<CCardFactory> createPluginFactories() {
		return createServiceLoader().stream().filter(filterCards()).map(fac -> {
			settings.addSettings(fac.get().getSettingsContributions());
			return fac.get();
		}).toList();
	}

	private ServiceLoader<CCardFactory> createServiceLoader() {
		var jarDirectory = getJarDirectory();
		if (jarDirectory != null) {
			var pluginDirectory = Path.of(jarDirectory + "/plugins");
			logger.debug(() -> "Initializing service loader - jar mode");
			if (Files.exists(pluginDirectory)) {
				logger.debug(() -> "PLugins directory found: " + pluginDirectory);
				try (Stream<URL> urls = Files.list(pluginDirectory).filter(Files::isRegularFile)
						.filter(path -> path.toString().toLowerCase().endsWith(".jar")).map(this::toURL)) {
					var urlArray = urls.toArray(URL[]::new);
					logger.debug(() -> urlArray.length + " jars found");
					var ucl = new URLClassLoader(urlArray);
					return ServiceLoader.load(CCardFactory.class, ucl);
				} catch (IOException e) {
					logger.error(() -> "Unable to load plugin jars", e);
				}
			} else {
				logger.debug(() -> "Plugins directory not found: " + pluginDirectory.toFile().getAbsolutePath());
			}
		}
		logger.debug(() -> "Initializing service loader - non-jar-mode");
		return ServiceLoader.load(CCardFactory.class);
	}

	private URL toURL(Path path) {
		try {
			return path.toUri().toURL();
		} catch (MalformedURLException _) {
			logger.error(() -> "Unable to create ULR for path " + path);
			return null;
		}
	}

	private Predicate<? super Provider<CCardFactory>> filterCards() {
		return fac -> {
			if (!getPluginWhitelist().isEmpty()) {
				boolean value = getPluginWhitelist().contains(fac.get().getId().toLowerCase());
				if (value) {
					logger.info(() -> "Loading plugin " + fac.get().getId() + " (whitelist)");
				}
				return value;
			} else if (!getPluginBlacklist().isEmpty()) {
				boolean value = !getPluginBlacklist().contains(fac.get().getId().toLowerCase());
				if (!value) {
					logger.info(() -> "Skip loading plugin " + fac.get().getId() + " (blacklist)");
				}
				return value;
			}
			return true;
		};
	}

	public void initPlugin(CCard plugin) {
		plugin.init();
		plugin.initBus(eventManager);
		plugin.getModes().ifPresent(modes -> {
			modes.forEach(mode -> {
				modeService.initMode(mode);
				commandsService.add(mode.getCommandTree());
			});
		});
		plugin.getModelessCommands().ifPresent(commandsService::add);
		plugin.getMenuItems().ifPresent(menuItems -> {
			Collections.reverse(menuItems);
			menuItems.forEach(menuItem -> tray.createMenuItem(menuItem.name(), menuItem.label(), menuItem.action()));
		});
	}

	public List<CCard> getPlugins() {
		if (plugins == null) {
			return loadPlugins();
		}
		return plugins;
	}

}
