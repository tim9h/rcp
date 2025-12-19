package dev.tim9h.rcp.core.service;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
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

	private TreeNode<String> root = new StringNode();

	@Inject
	public CommandsService(Injector injector) {
		injector.injectMembers(this);
		initDefaultCommands();
	}

	private void initDefaultCommands() {
		root.add(themeService.getThemeCommands());
		root.add("restart", "exit", "modes", "setting", "plugindir", "clear", "logs");

		var commandPlugins = new TreeNode<>("plugins");
		commandPlugins.add("whitelist", "blacklist");
		root.add(commandPlugins);

		var commandSettings = new TreeNode<>("settings");
		commandSettings.add("overwrites", "reload");
		root.add(commandSettings);

		var commandReposition = new TreeNode<>("reposition");
		root.add(commandReposition);
	}

	public void propagateCommands() {
		eventManager.post(new CcEvent(CcEvent.EVENT_CLI_ADD_PROPOSALS, root.getChildren().toArray()));
	}

	public void add(StringNode node) {
		root.add(node);
	}

	public void add(TreeNode<String> node) {
		if (!node.get().isBlank()) {
			root.add(node);
		} else {
			node.getChildren().forEach(c -> {
				if (root.getChildren().stream().filter(existing -> existing.get().equals(c.get())).findAny()
						.isEmpty()) {
					root.add(c);
				} else {
					root.getChildren().stream().filter(existing -> existing.get().equals(c.get())).findFirst()
							.ifPresent(existing -> existing.getChildren().addAll(c.getChildren()));
				}
			});
			logger.debug(() -> "Added command: " + node);
		}
	}

}
