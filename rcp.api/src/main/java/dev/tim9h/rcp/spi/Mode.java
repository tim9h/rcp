package dev.tim9h.rcp.spi;

public interface Mode {

	public String getName();

	public void onEnable();

	public void onDisable();

	public default void onCommand(String command, String... args) {
		//
	}

	public default void onInit() {
		//
	}

	public default boolean requiresInitialization() {
		return true;
	}

	public default TreeNode<String> getCommandTree() {
		var node = new TreeNode<>(getName());
		node.add("off");
		return node;
	}

}
