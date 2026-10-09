package dev.tim9h.rcp.spi;

import java.util.Optional;

public interface Mode {

	public String getName();

	public void onEnable();

	public void onDisable();

	public default void onInit() {
		//
	}

	public default boolean requiresInitialization() {
		return true;
	}

	public default Optional<CommandNode> getModeCommands() {
		return Optional.empty();
	}

}
