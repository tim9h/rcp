package dev.tim9h.rcp.core.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.plugin.PluginLoader;
import dev.tim9h.rcp.core.util.TrayManager;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.Plugin;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

@Singleton
public class CoreService {

	@InjectLogger
	private Logger logger;

	private Settings settings;

	private PluginLoader pluginLoader;

	private TrayManager tray;

	private EventManager eventManager;

	private CommandsService commandsService;

	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

	@Inject
	public CoreService(Settings settings, PluginLoader pluginLoader, TrayManager tray, EventManager eventManager,
			CommandsService commandsService) {
		this.settings = settings;
		this.pluginLoader = pluginLoader;
		this.tray = tray;
		this.eventManager = eventManager;
		this.commandsService = commandsService;
		initCoreCommands();
	}

	public void parseArgs(String[] args) throws ParseException {
		var options = new Options();

		var optionBlacklist = new Option("b", "blacklist", true, "Do not activate specific plugins");
		optionBlacklist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionBlacklist);

		var optionWhitelist = new Option("w", "whitelist", true, "Only activate specific plugins");
		optionWhitelist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionWhitelist);

		var optionSetting = new Option("s", "setting", true, "Overwrite persisted setting");
		optionSetting.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionSetting);

		var parser = new DefaultParser();
		var parse = parser.parse(options, args);

		if (parse.hasOption(optionBlacklist) && parse.hasOption(optionWhitelist)) {
			throw new ParseException("Invalid combination of options: whitelist and blacklist");
		} else if (parse.hasOption(optionBlacklist)) {
			var blacklist = Arrays.stream(parse.getOptionValues(optionBlacklist)).map(String::toLowerCase).toList();
			pluginLoader.setPluginBlacklist(blacklist);
		} else if (parse.hasOption(optionWhitelist)) {
			var whitelist = Arrays.stream(parse.getOptionValues(optionWhitelist)).map(String::toLowerCase).toList();
			pluginLoader.setPluginWhitelist(whitelist);
		}
		if (parse.hasOption(optionSetting)) {
			settings.addOverwrites(Arrays.asList(parse.getOptionValues(optionSetting)));
		}
	}

	public void openLogFile() {
		var ctx = (LoggerContext) LogManager.getContext(false);
		var config = ctx.getConfiguration();
		var appenders = config.getAppenders();
		appenders.values().stream().filter(FileAppender.class::isInstance).map(FileAppender.class::cast)
				.map(FileAppender::getFileName).findFirst().ifPresent(fileName -> {
					try {
						Desktop.getDesktop().open(new File(fileName));
					} catch (IOException e) {
						logger.error(() -> "Unable to open log file", e);
					}
				});
	}

	public void restartApplication() {
		logger.debug(() -> "Restarting application");
		try {
			var javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw";
			var sourcepath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile()
					.toString();
			if (Strings.CS.endsWith(sourcepath, ".jar")) {
				new ProcessBuilder(javaBin, "-jar", sourcepath).start();
				eventManager.post(new CcEvent(CcEvent.EVENT_RESTARTING));
				shutdownWithFeedback();
			} else {
				logger.warn(() -> "Unable to restart application: Not in jar mode");
				eventManager.echo("Unable to restart: Not in jar mode");
				eventManager.showToast(settings.getAppTitle(), "Unable to restart: Not in jar mode");
			}
		} catch (IOException | URISyntaxException e) {
			logger.error(() -> "Unable to restart application", e);
			eventManager.showToast(settings.getAppTitle(), "Unable to restart: " + e.getMessage());
		}
	}

	public void shutdownWithFeedback() {
		if (!shuttingDown.compareAndSet(false, true)) {
			return;
		}
		eventManager.echo("kthxbye.");
		var delay = new PauseTransition(Duration.seconds(1));
		delay.setOnFinished(_ -> cleanUp().thenRun(this::exitApplication));
		delay.play();
	}

	public CompletableFuture<Void> cleanUp() {
		return CompletableFuture.runAsync(() -> {
			eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING));
			logger.debug(() -> "Shutting down plugins");
			pluginLoader.getPlugins().forEach(Plugin::onShutdown);
			tray.removeTrayIcon();
			logger.debug(() -> "Plugin cleanup complete");
			eventManager.post(CcEvent.EVENT_CLOSING_FINISHED);
		});
	}

	public void exitApplication() {
		logger.debug(() -> "Shutting down application");
		Platform.runLater(Platform::exit);
	}

	private void initCoreCommands() {
		//@formatter:off
		commandsService.add(new CommandBuilder()
				.command("exit", _ -> shutdownWithFeedback())
					.child("cleanup", _ -> cleanUp()).up()
					.child("force", _ -> exitApplication())
				.command("restart", _ -> restartApplication())
				.command("logs", _ -> openLogFile()).getRoot());
		//@formatter:on
	}

}
