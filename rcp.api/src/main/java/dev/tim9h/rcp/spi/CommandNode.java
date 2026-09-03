package dev.tim9h.rcp.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public class CommandNode {

	private List<CommandNode> children = new ArrayList<>();

	private CommandNode parent;

	private String data = "";

	private Consumer<String> command;

	private Consumer<String> argumentCommand;

	private boolean hasArguments = false;

	public CommandNode() {
	}

	public CommandNode(String data) {
		this.data = data;
	}

	public CommandNode(String data, boolean hasArguments, Consumer<String> command) {
		this.data = data;
		this.hasArguments = hasArguments;
		this.command = command;
	}

	public CommandNode(String data, CommandNode parent) {
		this.data = data;
		this.parent = parent;
	}

	public List<CommandNode> getChildren() {
		return children;
	}

	public void setParent(CommandNode parent) {
		this.parent = parent;
	}

	public CommandNode add(String value) {
		return add(value, null);
	}

	public CommandNode add(String value, Consumer<String> command) {
		var child = new CommandNode(value, false, command);
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	public CommandNode add(String value, boolean hasArguments, Consumer<String> command) {
		var child = new CommandNode(value, hasArguments, command);
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	public CommandNode add(CommandNode child) {
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	public CommandNode add(String... values) {
		for (var value : values) {
			var child = new CommandNode(value);
			child.setParent(this);
			this.children.add(child);
		}
		return this;
	}

	public CommandNode add(CommandNode... nodes) {
		for (var node : nodes) {
			node.setParent(this);
			this.children.add(node);
		}
		return this;
	}

	public String getData() {
		return this.data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public Consumer<String> getCommand() {
		return this.command;
	}

	public void setCommand(Consumer<String> command) {
		this.command = command;
	}

	public Consumer<String> getArgumentCommand() {
		return argumentCommand;
	}

	public void setArgumentCommand(Consumer<String> argumentCommand) {
		this.argumentCommand = argumentCommand;
	}

	public boolean hasArguments() {
		return this.hasArguments;
	}

	public void setHasArguments(boolean hasArguments) {
		this.hasArguments = hasArguments;
	}

	public boolean isRoot() {
		return (this.parent == null);
	}

	public boolean isLeaf() {
		return children.isEmpty();
	}

	public void removeParent() {
		this.parent = null;
	}

	@Override
	public String toString() {
		return (String) data + (children.isEmpty() ? StringUtils.EMPTY : (StringUtils.SPACE + children));
	}

	public Stream<CommandNode> stream() {
		return getChildren().stream();
	}

	public CommandNode get(String value) {
		return stream().filter(child -> Strings.CI.equals(child.getData(), value)).findFirst().orElse(null);
	}

	public List<CommandNode> getChildrenOfChild(String value) {
		return stream().filter(child -> child.getData().equals(value)).flatMap(CommandNode::stream).toList();
	}

	public List<CommandNode> getChildrenOfChild(String value, Consumer<String> command) {
		return stream().filter(child -> child.getData().equals(value) && child.getCommand().equals(command))
				.flatMap(CommandNode::stream).toList();
	}

	public List<CommandNode> getChildrenOfChild(CommandNode node) {
		return getChildrenOfChild(node.getData(), node.getCommand());
	}

}