package dev.tim9h.rcp.core.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.windows.IdleChangeListener;
import dev.tim9h.rcp.core.windows.WindowsUtils;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.Mode;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

@Singleton
public class ModeServiceImpl implements ModeService {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private ThemeService themeService;

	private Set<String> activeModes;

	private Set<String> ephemeralModes;

	@Inject
	private Settings settings;

	@Inject
	private WindowsUtils windowsUtils;

	@Inject
	public ModeServiceImpl(Injector injector) {
		injector.injectMembers(this);
		activeModes = new HashSet<>(settings.getStringSet(SettingsConsts.MODES));
		ephemeralModes = new HashSet<>();
		listenToEvents();
	}

	private void listenToEvents() {
		eventManager.listen("modes", _ -> CompletableFuture.runAsync(() -> {
			if (activeModes.isEmpty() && ephemeralModes.isEmpty()) {
				eventManager.echo("No modes active");
			} else {
				eventManager.echo("Active modes",
						StringUtils.join(Iterables.concat(activeModes, ephemeralModes), ", "));
			}
		}));
	}

	@Override
	public void initDefaultModes() {
		var modes = Arrays.asList(createAlertMode(), createDndMode(), createAfkMode());
		modes.forEach(mode -> {
			initMode(mode);
			eventManager.post(new CcEvent(CcEvent.EVENT_CLI_ADD_PROPOSALS, mode.getCommandTree()));
		});
	}

	private Mode createAlertMode() {
		return new Mode() {

			private MediaPlayer alert;

			@Override
			public String getName() {
				return "alert";
			}

			@Override
			public void onEnable() {
				themeService.setTheme("lethan", false);
				eventManager.echo("ALARM!", "Alert mode activated");

				if (alert == null) {
					var source = getClass().getResource("/media/imperial_alert.mp3").toExternalForm();
					alert = new MediaPlayer(new Media(source));
					alert.setCycleCount(120);
					alert.setVolume(0.2);
				}
				Platform.runLater(alert::play);
			}

			@Override
			public void onDisable() {
				themeService.setTheme(settings.getString(SettingsConsts.THEME), false);
				eventManager.echo(StringUtils.EMPTY, "Alert mode deactivated");
				Platform.runLater(alert::stop);
			}
		};

	}

	private Mode createDndMode() {
		return new Mode() {

			@Override
			public String getName() {
				return "dnd";
			}

			@Override
			public void onEnable() {
				eventManager.post(new CcEvent("logiled", "off"));
				eventManager.post(new CcEvent("stop"));
				eventManager.echo("DND enabled");
			}

			@Override
			public void onDisable() {
				eventManager.echo("DND disabled");
			}
		};
	}

	private Mode createAfkMode() {
		return new Mode() {

			@Override
			public String getName() {
				return "afk";
			}

			@Override
			public void onInit() {
				windowsUtils.registerIdleStateListener(new IdleChangeListener() {

					@Override
					public void onUknown() {
						logger.warn(() -> "Unkown idle state");
						disableMode("idle");
						disableMode("afk");
					}

					@Override
					public void onOnline() {
						disableMode("idle");
						if (isModeActive("afk")) {
							disableMode("afk");
							eventManager.echo("Welcome back");
						}
					}

					@Override
					public void onIdle() {
						enableMode("idle", true);
					}

					@Override
					public void onAway() {
						disableMode("idle");
						enableMode("afk", true);
						eventManager.echo("See you later");
					}
				});
			}

			@Override
			public void onEnable() {
				eventManager.echo("See you later");
			}

			@Override
			public void onDisable() {
				eventManager.echo("Welcome back");
			}
		};
	}

	public Set<String> getModes() {
		return activeModes;
	}

	@Override
	public void setModes(Collection<String> modes) {
		activeModes.addAll(modes);
	}

	private boolean enableMode(String mode) {
		return enableMode(mode, false);
	}

	private boolean enableMode(String mode, boolean ephermeral) {
		if (!isModeActive(mode) && !"".equals(mode)) {
			logger.info(() -> String.format("Mode %s enabled", mode));
			if (!ephermeral) {
				activeModes.add(mode);
				settings.persist(SettingsConsts.MODES, activeModes);
			} else {
				ephemeralModes.add(mode);
			}
			eventManager.post(new CcEvent("MODE_" + mode.toUpperCase(), "on"));
			return true;
		}
		return false;
	}

	public boolean disableMode(String mode) {
		if (isModeActive(mode)) {
			if (activeModes.remove(mode)) {
				settings.persist(SettingsConsts.MODES, activeModes);
			} else {
				ephemeralModes.remove(mode);
			}
			logger.info(() -> String.format("Mode %s disabled", mode));
			eventManager.post(new CcEvent("MODE_" + mode.toUpperCase(), "off"));
			return true;
		}
		return false;
	}

	@Override
	public void initMode(Mode mode) {
		if (isModeActive(mode.getName())) {
			mode.onEnable();
			eventManager.post(new CcEvent(mode.getName()));
		}
		eventManager.listen(mode.getName(), args -> {
			if (args == null) {
				activateMode(mode);
			} else if ("off".equals(args[0])) {
				disableMode(mode);
			} else {
				executeModeCommand(mode, args);
			}
		});
		mode.onInit();
	}

	private void activateMode(Mode mode) {
		if (enableMode(mode.getName())) {
			mode.onEnable();
		} else {
			eventManager.echo(String.format("Already in %s mode", mode.getName()));
		}
	}

	private void disableMode(Mode mode) {
		if (disableMode(mode.getName())) {
			mode.onDisable();
		} else {
			eventManager.echo(String.format("Not in %s mode", mode.getName()));
		}
	}

	private void executeModeCommand(Mode mode, Object[] args) {
		if (!mode.requiresInitialization()) {
			enableMode(mode.getName());
		}
		if (args.length > 1) {
			var commandParams = args.length > 1 ? (String[]) Arrays.copyOfRange(args, 1, args.length) : new String[] {};
			mode.onCommand((String) args[0], commandParams);
		} else {
			mode.onCommand((String) args[0]);
		}
	}

	@Override
	public boolean isModeActive(String name) {
		return activeModes.contains(name) || ephemeralModes.contains(name);
	}

}
