package dev.tim9h.rcp.spi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;

public class CommandBuilder {

	private final CommandNode root = new CommandNode();

	private final Deque<CommandNode> stack = new ArrayDeque<>();

	public CommandBuilder command(String name) {
		return command(name, false, null);
	}

	public CommandBuilder command(String name, Consumer<String> command) {
		return command(name, false, command);
	}

	public CommandBuilder command(String name, boolean hasArguments, Consumer<String> command) {
		var node = new CommandNode(name, hasArguments, command);
		root.add(node);
		stack.clear();
		stack.push(node);
		return this;
	}

	public CommandBuilder child(String name) {
		return child(name, false, null);
	}

	public CommandBuilder child(String name, Consumer<String> command) {
		return child(name, false, command);
	}

	public CommandBuilder child(String name, boolean hasArguments, Consumer<String> command) {
		var child = current().add(name, hasArguments, command);
		stack.push(child);
		return this;
	}

	public CommandBuilder children(String... names) {
		for (var name : names) {
			current().add(name);
		}
		return this;
	}

	public CommandBuilder arguments() {
		current().setHasArguments(true);
		return this;
	}

	public CommandBuilder argumentAction(Consumer<String> command) {
		current().setHasArguments(true);
		current().setArgumentCommand(command);
		return this;
	}

	public CommandBuilder up() {
		if (stack.size() > 1) {
			stack.pop();
		}

		return this;
	}

	public Optional<CommandNode> build() {
		if (root.getChildren().isEmpty()) {
			return Optional.empty();
		}
		validate(root);
		return Optional.of(root);
	}

	public CommandNode getRoot() {
		validate(root);
		return root;
	}

	private CommandNode current() {
		if (stack.isEmpty()) {
			throw new IllegalStateException("No current command.");
		}
		return stack.peek();
	}

	private void validate(CommandNode node) {
		if (!node.isRoot() && (node.getData() == null || node.getData().isBlank())) {
			throw new IllegalStateException("Command name must not be blank.");
		}
		for (var child : node.getChildren()) {
			validate(child);
		}
	}

}