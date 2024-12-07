package dev.tim9h.rcp.spi;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import javafx.scene.Node;

public interface CCard {

	public String getName();

	public default Optional<Node> getNode() throws IOException {
		return Optional.empty();
	}

	public default void init() {
		//
	}

	public default Optional<String> getStylesheet() {
		return Optional.empty();
	}

	public default Gravity getGravity() {
		return new Gravity(Position.BOTTOM);
	}

	public default Optional<List<MenuContribution>> getMenuItems() {
		return Optional.empty();
	}

	public default void initBus(EventManager eventManager) {
		eventManager.listen(CcEvent.EVENT_SHOWN, _ -> onShown());
		eventManager.listen(CcEvent.EVENT_HIDDEN, _ -> onHidden());
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> onSettingsChanged());
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

	public default Optional<TreeNode<String>> getModelessCommands() {
		return Optional.empty();
	}

}
