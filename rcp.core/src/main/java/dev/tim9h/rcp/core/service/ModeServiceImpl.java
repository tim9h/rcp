package dev.tim9h.rcp.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.core.windows.IdleChangeListener;
import dev.tim9h.rcp.core.windows.WindowsUtils;
import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.service.ModeService;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.CommandNode;
import dev.tim9h.rcp.spi.Mode;

@Singleton
public class ModeServiceImpl implements ModeService {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	private final Set<String> activeModes;

	private final Set<String> ephemeralModes;

	private final Map<String, Mode> registeredModes = new HashMap<>();

	@Inject
	private Settings settings;

	@Inject
	private WindowsUtils windowsUtils;

	@Inject
	private CommandsService commandsService;

	@Inject
	public ModeServiceImpl(Injector injector) {
		injector.injectMembers(this);
		activeModes = new HashSet<>(settings.getStringSet(SettingsConsts.MODES));
		ephemeralModes = new HashSet<>();
		listenToEvents();
	}

	private void listenToEvents() {
		//@formatter:off
		commandsService.add(new CommandBuilder()
			.command("modes", _ -> {
				var modes = getActiveModes();
				if (modes.isEmpty()) {
					eventManager.echo("No modes active");
				} else {
					eventManager.echo("Active modes", StringUtils.join(modes, ", "));
				}
			}).getRoot());
		//@formatter:on
	}

	@Override
	public void initDefaultModes() {
		var modes = List.of(createDndMode(), createAfkMode());
		modes.forEach(this::initMode);
		// plugin commands were already propagated to the CLI, so send these separately
		commandsService.propagateCommands(modes.stream().map(Mode::getName).toArray(String[]::new));
	}

	private Mode createDndMode() {
		return new Mode() {

			@Override
			public String getName() {
				return "dnd";
			}

			@Override
			public void onEnable() {
				eventManager.post(new Event("logiled", "off"));
				eventManager.post(new Event("stop"));
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
			public boolean isEphemeral() {
				return true;
			}

			@Override
			public void onInit() {
				windowsUtils.registerIdleStateListener(new IdleChangeListener() {

					@Override
					public void onUknown() {
						logger.warn(() -> "Unkown idle state");
						disableMode("idle", true);
						disableMode("afk", true);
					}

					@Override
					public void onOnline() {
						disableMode("idle", true);
						if (isModeActive("afk")) {
							disableMode("afk", true);
							eventManager.echo("Welcome back");
						}
					}

					@Override
					public void onIdle() {
						enableMode("idle", true);
					}

					@Override
					public void onAway() {
						disableMode("idle", true);
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

	@Override
	public void initMode(Mode mode) {
		var name = mode.getName();
		registeredModes.put(name, mode);
		if (mode.isEphemeral()) {
			if (activeModes.remove(name)) {
				persistActiveModes();
			}
		} else if (activeModes.contains(name)) {
			mode.onEnable();
			eventManager.post(new Event(name));
		}
		commandsService.add(createModeCommand(mode));
		mode.onInit();
	}

	@Override
	public boolean enableMode(String mode, boolean ephemeral) {
		if (StringUtils.isBlank(mode) || isModeActive(mode)) {
			return false;
		}

		if (!"idle".equals(mode)) {
			logger.info(() -> String.format("Mode %s enabled", mode));
		}
		if (ephemeral) {
			ephemeralModes.add(mode);
		} else {
			activeModes.add(mode);
			persistActiveModes();
		}
		eventManager.post(new Event(modeEventName(mode), "on"));
		return true;
	}

	@Override
	public boolean disableMode(String mode, boolean ephemeral) {
		if (StringUtils.isBlank(mode)) {
			return false;
		}

		var removed = ephemeral ? ephemeralModes.remove(mode) : activeModes.remove(mode);
		if (!removed) {
			return false;
		}
		if (!ephemeral) {
			persistActiveModes();
		}
		if (!"idle".equals(mode)) {
			logger.info(() -> String.format("Mode %s disabled", mode));
		}
		eventManager.post(new Event(modeEventName(mode), "off"));
		return true;
	}

	private void disableMode(Mode mode) {
		if (disableMode(mode.getName(), mode.isEphemeral())) {
			mode.onDisable();
		} else {
			eventManager.echo("Not in " + mode.getName() + " mode");
		}
	}

	private void persistActiveModes() {
		settings.persist(SettingsConsts.MODES, activeModes);
	}

	private static String modeEventName(String mode) {
		return "MODE_" + mode.toUpperCase(Locale.ROOT);
	}

	private CommandNode createModeCommand(Mode mode) {
		var modeCommand = mode.getModeCommands().map(built -> {
			// CommandBuilder returns a blank root wrapping the actual mode node
			if (built.getData().isBlank()) {
				var node = built.get(mode.getName());
				if (node != null) {
					node.detach();
					return node;
				}
				var wrapper = new CommandNode(mode.getName());
				new ArrayList<>(built.getChildren()).forEach(wrapper::add);
				return wrapper;
			}
			return built;
		}).orElseGet(() -> new CommandNode(mode.getName()));
		modeCommand.setCommand(_ -> activateMode(mode));
		if (modeCommand.get("off") == null) {
			modeCommand.add(new CommandNode("off", false, _ -> disableMode(mode)));
		}
		prepareModeCommands(mode, modeCommand);
		return modeCommand;
	}

	private void prepareModeCommands(Mode mode, CommandNode node) {
		var argumentCommand = node.getArgumentCommand();
		if (argumentCommand != null) {
			node.setArgumentCommand(arg -> executeModeCommand(mode, () -> argumentCommand.accept(arg)));
		}
		for (var child : node.getChildren()) {
			if ("off".equalsIgnoreCase(child.getData())) {
				continue;
			}
			wrapCommands(mode, child);
		}
	}

	private void wrapCommands(Mode mode, CommandNode node) {
		var command = node.getCommand();
		if (command != null) {
			node.setCommand(arg -> executeModeCommand(mode, () -> command.accept(arg)));
		}
		var argumentCommand = node.getArgumentCommand();
		if (argumentCommand != null) {
			node.setArgumentCommand(arg -> executeModeCommand(mode, () -> argumentCommand.accept(arg)));
		}
		for (var child : node.getChildren()) {
			if (!"off".equalsIgnoreCase(child.getData())) {
				wrapCommands(mode, child);
			}
		}
	}

	private void executeModeCommand(Mode mode, Runnable command) {
		if (!mode.requiresInitialization()) {
			enableMode(mode.getName(), mode.isEphemeral());
		}
		command.run();
	}

	private void activateMode(Mode mode) {
		if (enableMode(mode.getName(), mode.isEphemeral())) {
			mode.onEnable();
		} else {
			eventManager.echo(String.format("Already in %s mode", mode.getName()));
		}
	}

	@Override
	public boolean isModeActive(String name) {
		return activeModes.contains(name) || ephemeralModes.contains(name);
	}

	@Override
	public Set<String> getActiveModes() {
		var modes = new HashSet<>(activeModes);
		modes.addAll(ephemeralModes);
		return modes;
	}

}