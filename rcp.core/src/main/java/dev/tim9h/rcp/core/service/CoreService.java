package dev.tim9h.rcp.core.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

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
import dev.tim9h.rcp.spi.CCard;
import javafx.application.Platform;

@Singleton
public class CoreService {

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	@Inject
	private PluginLoader pluginLoader;
	
	@Inject
	private TrayManager tray;
	
	private EventManager eventManager;
	
	@Inject
	public CoreService(EventManager eventManager) {
		this.eventManager = eventManager;
		subscribeToDefaultEvents();
	}

	public void parseArgs(String[] args) throws ParseException {
		var options = new Options();

		var optionBlacklist = new Option("b", "blacklist", true, "Do not activate specific plugins");
		optionBlacklist.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(optionBlacklist);

		var optionWhitelist = new Option("w", "blacklist", true, "Only activate specific plugins");
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
				shutdown();
			} else {
				logger.warn(() -> "Unable to restart application: Not in jar mode");
				eventManager.echo("Unable to restart: Not in jar mode");
			}
		} catch (IOException | URISyntaxException e) {
			logger.error(() -> "Unable to restart application", e);
		}
	}
	
	public void shutdown() {
		logger.debug(() -> "Shutting down");
		eventManager.echo("kthxbye.");
		eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING));
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error(() -> "Error while shutdown", e);
				Thread.currentThread().interrupt();
			}
			pluginLoader.getPlugins().forEach(CCard::onShutdown);
			tray.removeTrayIcon();
			Platform.exit();
			Platform.runLater(() -> System.exit(0));
		});
	}
	
	public void prepareShutdown() {
		logger.debug(() -> "Shutting down immediately");
		CompletableFuture.runAsync(() -> {
			pluginLoader.getPlugins().forEach(CCard::onShutdown);
			tray.removeTrayIcon();
		}).thenRun(() -> eventManager.post(new CcEvent(CcEvent.EVENT_CLOSING_FINISHED)));
	}
	
	private void subscribeToDefaultEvents() {
		eventManager.listen("exit", _ -> shutdown());
		eventManager.listen("exitimmediately", _ -> prepareShutdown());
		eventManager.listen("restart", _ -> restartApplication());
		eventManager.listen("logs", _ -> openLogFile());
	}

}
