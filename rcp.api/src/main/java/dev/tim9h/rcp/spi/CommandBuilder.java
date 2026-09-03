package dev.tim9h.rcp.spi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;

public class CommandBuilder {

	private final Deque<CommandNode> stack = new ArrayDeque<>();

	private CommandNode root;

	public CommandBuilder command(String name) {
		return command(name, false, null);
	}

	public CommandBuilder command(String name, Consumer<String> command) {
		var node = new CommandNode(name);
		node.setCommand(command);

		if (root == null) {
			root = node;
		} else {
			current().add(node);
		}

		stack.push(node);
		return this;
	}

	public CommandBuilder command(String name, boolean hasArguments, Consumer<String> command) {
		var node = new CommandNode(name, hasArguments, command);

		if (root == null) {
			root = node;
		} else {
			current().add(node);
		}

		stack.push(node);
		return this;
	}

	public CommandBuilder arguments() {
		current().setHasArguments(true);
		return this;
	}

	public CommandBuilder arguments(boolean hasArguments) {
		current().setHasArguments(hasArguments);
		return this;
	}

	public CommandBuilder argumentAction(Consumer<String> command) {
		current().setHasArguments(true);
		current().setCommand(command);
		return this;
	}

	public CommandBuilder action(Consumer<String> command) {
		current().setArgumentCommand(command);
		return this;
	}

	public CommandBuilder up() {
		if (!stack.isEmpty()) {
			stack.pop();
		}

		return this;
	}

	public Optional<CommandNode> build() {
		if (root == null) {
			return Optional.empty();
		}

		validate(root);

		return Optional.of(root);
	}

	public CommandNode getRoot() {
		validate(root);
		return root;
	}

	private void validate(CommandNode node) {
		if (node.getData() == null || node.getData().isBlank()) {
			throw new IllegalStateException("Command name must not be blank.");
		}

		for (CommandNode child : node.getChildren()) {
			validate(child);
		}
	}

	private CommandNode current() {
		if (stack.isEmpty()) {
			throw new IllegalStateException("No current command.");
		}

		return stack.peek();
	}

	public CommandBuilder child(String name, Consumer<String> command) {
		current().add(name, command);
		return this;
	}

	public CommandBuilder child(String name, boolean hasArguments, Consumer<String> command) {
		current().add(name, hasArguments, command);
		return this;
	}

	public CommandBuilder child(String name) {
		current().add(name);
		return this;
	}

	public CommandBuilder children(String... names) {
		current().add(names);
		return this;
	}

}