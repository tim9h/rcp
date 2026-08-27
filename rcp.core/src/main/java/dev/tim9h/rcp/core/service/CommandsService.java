package dev.tim9h.rcp.core.service;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.core.util.EventHelper;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.spi.CommandNode;
import dev.tim9h.rcp.spi.StringNode;
import dev.tim9h.rcp.spi.TreeNode;

@Singleton
public class CommandsService {

	@InjectLogger
	private Logger logger;

	@Inject
	private ThemeService themeService;

	@Inject
	private EventManager eventManager;

	private CommandNode root = new CommandNode();

	private final Set<String> listenedCommands = new HashSet<>();

	@Inject
	public CommandsService(Injector injector) {
		injector.injectMembers(this);
		initDefaultCommands();
		listenAndRunCommands();
	}

	private void initDefaultCommands() {
		root.add(themeService.getThemeCommands().toCommandNode());
		root.add("restart", "exit", "modes", "setting", "plugindir", "clear", "logs");

		var commandPlugins = new CommandNode("plugins");
		commandPlugins.add("whitelist", "blacklist");
		root.add(commandPlugins);

		var commandSettings = new CommandNode("settings");
		commandSettings.add("overwrites", "reload");
		root.add(commandSettings);

		var commandReposition = new CommandNode("reposition");
		root.add(commandReposition);
	}

	public void propagateCommands() {
		eventManager.post(new CcEvent(CcEvent.EVENT_CLI_ADD_PROPOSALS, root.getChildren().toArray()));
	}

	public void add(StringNode node) {
		root.add(node.get());
	}

	public void add(TreeNode<String> node) {
		if (!node.get().isBlank()) {
			root.add(node.toCommandNode());
		} else {
			node.getChildren().forEach(c -> {
				if (root.getChildren().stream().filter(existing -> existing.getData().equals(c.get())).findAny()
						.isEmpty()) {
					root.add(c.toCommandNode());
				} else {
					var children = c.getChildren().stream().map(TreeNode::toCommandNode).toList();
					root.getChildren().stream().filter(existing -> existing.getData().equals(c.get())).findFirst()
							.ifPresent(existing -> existing.getChildren().addAll(children));
				}
			});
			logger.debug(() -> "Added command: " + node);
		}
	}

	public void add(CommandNode node) {
		if (!node.getData().isBlank()) {
			root.add(node);
			listenAndRunCommands(node);
		} else {
			node.getChildren().forEach(c -> {
				var existing = root.get(c.getData());
				if (existing == null) {
					root.add(c);
					listenAndRunCommands(c);
				} else {
					existing.getChildren().addAll(c.getChildren());
					if (existing.getCommand() == null) {
						existing.setCommand(c.getCommand());
					}
					if (c.hasArguments()) {
						existing.setHasArguments(true);
					}
				}
			});
			logger.debug(() -> "Added command: " + node);
		}
	}

	private void listenAndRunCommands() {
		root.getChildren().forEach(node -> {
			if (node.getCommand() != null || node.hasArguments()) {
				listenAndRunCommands(node);
			}
		});
	}

	private void listenAndRunCommands(CommandNode node) {
		if (node == null || node.getData().isBlank()) {
			return;
		}
		if (!listenedCommands.add(node.getData().toLowerCase())) {
			return;
		}
		eventManager.listen(node.getData(), payload -> execute(node, payload));
	}

	private void execute(CommandNode node, Object[] payload) {
		CommandNode current = node;
		var index = 0;
		while (true) {
			// Current node expects arguments.
			if (current.hasArguments()) {
				if (current.getCommand() == null) {
					eventManager.echo("No action defined for '" + current.getData() + "'");
					return;
				}
				var arguments = EventHelper.joinPayload(payload, index);
				if (arguments == null || arguments.isBlank()) {
					eventManager.echo("Missing argument for '" + current.getData());
					return;
				}
				current.getCommand().accept(arguments);
				return;
			}
			// No more input.
			if (payload == null || index >= payload.length) {
				if (current.getCommand() != null) {
					current.getCommand().accept(null);
				} else {
					eventManager.echo("Incomplete/Missing command '" + current.getData() + "'");
				}
				return;
			}
			var value = String.valueOf(payload[index]);
			var child = current.get(value);
			if (child == null) {
				eventManager.echo("Unknown command '" + value + "' after '" + current.getData() + "'");
				return;
			}
			current = child;
			index++;
		}
	}

}
