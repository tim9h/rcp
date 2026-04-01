package dev.tim9h.rcp.controls.tray;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

class SubMenuDef {

	final Node parentNode;

	final List<MenuItemData> items;

	Popup submenuPopup;

	VBox submenuPane;

	SubMenuDef(Node parentNode, List<MenuItemData> items) {
		this.parentNode = parentNode;
		this.items = items;
	}

}