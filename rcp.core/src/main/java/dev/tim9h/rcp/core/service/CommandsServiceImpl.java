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
public class CommandsServiceImpl implements CommandsService {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	private CommandNode root = new CommandNode();

	private final Set<String> listenedCommands = new HashSet<>();

	@Inject
	public CommandsServiceImpl(Injector injector) {
		injector.injectMembers(this);
		initDefaultCommands();
		listenAndRunCommands();
	}

	private void initDefaultCommands() {
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

	@Override
	public void propagateCommands() {
		eventManager.post(new CcEvent(CcEvent.EVENT_CLI_ADD_PROPOSALS, root.getChildren().toArray()));
	}

	@Override
	public void add(StringNode node) {
		root.add(node.get());
	}

	@Override
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

	@Override
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
		CommandNode argumentOwner = null;
		var argumentStart = -1;
		var index = 0;

		while (true) {
			// Nothing left to consume.
			if (payload == null || index >= payload.length) {

				// We previously matched a terminal argument value.
				if (argumentOwner != null) {
					argumentOwner.getArgumentCommand().accept(EventHelper.joinPayload(payload, argumentStart));
					return;
				}

				// Plain command with no arguments.
				if (current.getCommand() != null) {
					current.getCommand().accept(null);
					return;
				}

				eventManager.echo("Incomplete command '" + current.getData() + "'");
				return;
			}

			var value = String.valueOf(payload[index]);
			var child = current.get(value);

			if (child != null) {

				// A real executable child: continue traversing.
				if (child.getCommand() != null) {
					current = child;
					index++;
					continue;
				}

				// A terminal value belonging to the parent's argument command.
				if (current.getArgumentCommand() != null) {
					argumentOwner = current;
					argumentStart = index;

					current = child;
					index++;

					// If there is another token, this terminal value
					// cannot accept anything else.
					if (index < payload.length) {
						eventManager
								.echo("Unexpected argument '" + payload[index] + "' after '" + child.getData() + "'");
						return;
					}
					continue;
				}

				eventManager.echo("Command '" + child.getData() + "' has no command");
				return;
			}

			// No child matched. Let the current command consume
			// the remaining payload as its argument.
			if (current.getArgumentCommand() != null) {
				current.getArgumentCommand().accept(EventHelper.joinPayload(payload, index));
				return;
			}

			eventManager.echo("Unexpected argument '" + value + "' for command '" + current.getData() + "'");
			return;
		}
	}

}
