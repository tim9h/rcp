package dev.tim9h.rcp.core.service;

import dev.tim9h.rcp.spi.CommandNode;
import dev.tim9h.rcp.spi.StringNode;
import dev.tim9h.rcp.spi.TreeNode;

public interface CommandsService {

	void propagateCommands();

	void add(CommandNode node);

	void add(TreeNode<String> node);

	void add(StringNode node);

}