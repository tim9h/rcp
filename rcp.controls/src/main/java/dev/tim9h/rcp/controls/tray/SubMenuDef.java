package dev.tim9h.rcp.controls.tray;

import java.util.List;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

class SubMenuDef {

	final Node parentNode;

	final String label;

	final List<MenuItemData> items;

	Popup submenuPopup;

	VBox submenuPane;

	PauseTransition hideTimer;

	SubMenuDef(Node parentNode, String label, List<MenuItemData> items) {
		this.parentNode = parentNode;
		this.label = label;
		this.items = items;
	}

}