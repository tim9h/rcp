package dev.tim9h.rcp.core.plugin;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.service.CommandsService;
import dev.tim9h.rcp.core.service.ModeService;
import dev.tim9h.rcp.core.util.PluginNodeSorter;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.Plugin;

@Singleton
public class PluginLoader {

	@InjectLogger
	private Logger logger;

	private Injector injector;

	private EventManager eventManager;

	private Settings settings;

	private CommandsService commandsService;

	private ModeService modeService;

	private TrayManager tray;

	private List<String> pluginBlacklist;

	private List<String> pluginWhitelist;

	private List<Plugin> plugins;

	private ModuleLayer pluginLayer;

	@Inject
	public PluginLoader(Injector injector, EventManager eventManager, Settings settings,
			CommandsService commandsService, ModeService modeService, TrayManager tray) {
		this.injector = injector;
		this.eventManager = eventManager;
		this.settings = settings;
		this.commandsService = commandsService;
		this.modeService = modeService;
		this.tray = tray;
		initPluginCommands();
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

	public void initPluginCommands() {
		//@formatter:off
		commandsService.add(new CommandBuilder()
			.command("plugins", _ -> {
				var pluginlist = getPlugins().stream().map(Plugin::getName).sorted().collect(Collectors.joining(", "));
				logger.info(() -> "Active plugins: " + pluginlist);
				eventManager.echo("Active plugins", StringUtils.abbreviate(pluginlist, settings.getCharWidth()));
			})
			.child("whitelist", _ -> {
				if (!getPluginWhitelist().isEmpty()) {
					var list = getPluginWhitelist().stream().collect(Collectors.joining(", "));
					eventManager.echo("Whitelisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
				} else {
					eventManager.echo("No plugins whitelisted");
				}
			}).up()
			.child("blacklist", _ -> {
				if (!getPluginBlacklist().isEmpty()) {
					var list = getPluginBlacklist().stream().collect(Collectors.joining(", "));
					eventManager.echo("Blacklisted plugins", StringUtils.abbreviate(list, settings.getCharWidth()));
				} else {
					eventManager.echo("No plugins blacklisted");
				}
			})
			.command("plugindir", _ -> openPluginsDirectory()).getRoot());
		//@formatter:on 
	}

	public void openPluginsDirectory() {
		var jarDirectory = getPluginDirectory();
		if (jarDirectory != null) {
			try {
				var pluginDirectory = Path.of(jarDirectory + "/plugins");
				Desktop.getDesktop().open(pluginDirectory.toFile());
			} catch (IOException e) {
				logger.warn(() -> "Unable to open plugins directory: " + e.getMessage(), e);
				eventManager.showToast(settings.getAppTitle(), "Unable to open plugins directory: " + e.getMessage());
			}
		} else {
			logger.warn(() -> "Unable to open plugins directory: Not in jar mode");
			eventManager.showToast(settings.getAppTitle(), "Unable to open plugins directory: Not in jar mode");
			eventManager.echo("Not in jar mode");
		}
	}

	private Path getPluginDirectory() {
		try {
			var location = PluginLoader.class.getProtectionDomain().getCodeSource().getLocation();

			var codeSource = Path.of(location.toURI());

			return codeSource.resolveSibling("plugins");

		} catch (URISyntaxException e) {
			throw new IllegalStateException("Unable to determine plugin directory", e);
		}
	}

	public List<Plugin> loadPlugins() {
		if (plugins == null) {
			plugins = new ArrayList<>();

			createServiceLoader().forEach(plugins::add);

			plugins = plugins.stream().filter(filterCards()).sorted(new PluginNodeSorter()).toList();

			plugins.forEach(plugin -> {
				logger.info(() -> "Injecting " + plugin.getName());

				try {
					injector.injectMembers(plugin);
					logger.debug(() -> "Injected " + plugin.getName());

					initPlugin(plugin);
					logger.debug(() -> "Initialized " + plugin.getName());
				} catch (Throwable e) {
					logger.error(() -> "Failed initializing plugin " + plugin.getName(), e);
					throw e;
				}
			});

			commandsService.propagateCommands();
		}

		return plugins;
	}

	private ServiceLoader<Plugin> createServiceLoader() {
		var pluginDirectory = getPluginDirectory();

		logger.debug(() -> "Initializing plugin module loader: " + pluginDirectory.toAbsolutePath());

		if (!Files.isDirectory(pluginDirectory)) {
			logger.debug(() -> "Plugins directory not found: " + pluginDirectory.toAbsolutePath());

			return ServiceLoader.load(Plugin.class);
		}

		return createModuleServiceLoader(pluginDirectory);
	}

	private ServiceLoader<Plugin> createModuleServiceLoader(Path pluginDirectory) {

		var parentLayer = ModuleLayer.boot();

		var finder = ModuleFinder.of(pluginDirectory);

		var moduleNames = finder.findAll().stream().map(ref -> ref.descriptor().name()).collect(Collectors.toSet());

		logger.debug(() -> "Plugin modules found: " + moduleNames);

		if (moduleNames.isEmpty()) {
			return ServiceLoader.load(Plugin.class);
		}

		var configuration = parentLayer.configuration().resolve(finder, ModuleFinder.of(), moduleNames);

		pluginLayer = parentLayer.defineModulesWithManyLoaders(configuration, ClassLoader.getSystemClassLoader());
//				.layer();

		logger.debug(() -> "Plugin layer modules: " + pluginLayer.modules());

		return ServiceLoader.load(pluginLayer, Plugin.class);
	}

	private Predicate<Plugin> filterCards() {
		return plugin -> {
			if (!getPluginWhitelist().isEmpty()) {
				var pluginWhitelisted = getPluginWhitelist().contains(plugin.getId().toLowerCase());
				if (pluginWhitelisted) {
					logger.info(() -> "Loading plugin " + plugin.getName() + " (whitelist)");
				} else {
					logger.debug(() -> "Skipping plugin " + plugin.getName() + " (not whitelist)");
				}
				return pluginWhitelisted;
			} else if (!getPluginBlacklist().isEmpty()) {
				var pluginBlacklisted = !getPluginBlacklist().contains(plugin.getId().toLowerCase());
				if (!pluginBlacklisted) {
					logger.info(() -> "Skip loading plugin " + plugin.getName() + " (blacklist)");
				} else {
					logger.debug(() -> "Loading plugin " + plugin.getName() + " (not blacklist)");
				}
				return pluginBlacklisted;
			}
			return true;
		};
	}

	@SuppressWarnings("deprecation")
	public void initPlugin(Plugin plugin) {
		try {
			plugin.init();
			plugin.initBus(eventManager);
			plugin.getModes().ifPresent(modes -> modes.forEach(mode -> {
				modeService.initMode(mode);
				commandsService.add(mode.getCommandTree());
			}));
			plugin.getModelessCommands().ifPresent(commandsService::add);
			plugin.getCommands().ifPresent(commandsService::add);
			plugin.getMenuItems().ifPresent(menuItems -> {
				Collections.reverse(menuItems);
				menuItems.forEach(menuItem -> tray.createMenuItem(menuItem.label(), menuItem.action()));
			});
			settings.addSettings(plugin.getSettingsContributions());
		} catch (Exception e) {
			logger.error(() -> "Unable to initialize plugin " + plugin.getName(), e);
			eventManager.post(CcEvent.EVENT_ALERT);
		}
	}

	public List<Plugin> getPlugins() {
		if (plugins == null) {
			return loadPlugins();
		}
		return plugins;
	}

}
