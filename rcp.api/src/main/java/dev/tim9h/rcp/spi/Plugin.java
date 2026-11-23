package dev.tim9h.rcp.spi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.util.RcpDirectories;
import javafx.scene.Node;

public interface Plugin {

	public String getName();

	public default String getId() {
		return getName().toLowerCase().replaceAll("\\s+", "");
	}

	/**
	 * Returns this plugin's persistent data directory, creating it on first use.
	 *
	 * @throws IOException if the directory cannot be created
	 */
	public default Path getDataDirectory() throws IOException {
		return Files.createDirectories(RcpDirectories.getPluginDataDirectory(getId()));
	}

	public default Optional<Node> getNode() throws IOException {
		return Optional.empty();
	}

	public default void init() {
		//
	}

	public default Optional<String> getStylesheet() {
		return Optional.empty();
	}

	public default List<Theme> getThemes() {
		return Collections.emptyList();
	}

	public default Gravity getGravity() {
		return new Gravity(Position.BOTTOM);
	}

	public default Optional<List<MenuContribution>> getMenuItems() {
		return Optional.empty();
	}

	public default void initBus(EventManager eventManager) {
		eventManager.listen(Event.EVENT_SHOWN, _ -> onShown());
		eventManager.listen(Event.EVENT_HIDDEN, _ -> onHidden());
		eventManager.listen(Event.EVENT_SETTINGS_CHANGED, _ -> onSettingsChanged());
	}

	public default void onShown() {
		// callback method
	}

	public default void onHidden() {
		// callback method
	}

	public default void onShutdown() {
		// callback method
	}

	public default void onSettingsChanged() {
		// callback method
	}

	public default Optional<List<Mode>> getModes() {
		return Optional.empty();
	}

	public default Optional<CommandNode> getCommands() {
		return Optional.empty();
	}

	public default Map<String, String> getSettingsContributions() {
		return Collections.emptyMap();
	}

}
