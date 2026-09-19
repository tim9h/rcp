package dev.tim9h.rcp.controls.tray;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class MenuItemData {

	public final String label;

	public final Runnable action;

	public final boolean checkable;

	private final BooleanProperty checkedProperty;

	public MenuItemData(String label, Runnable action) {
		this(label, action, false, false);
	}

	public MenuItemData(String label, Runnable action, boolean checkable, boolean checked) {
		this.label = label;
		this.action = action;
		this.checkable = checkable;
		this.checkedProperty = new SimpleBooleanProperty(checked);
	}

	public boolean isChecked() {
		return checkedProperty.get();
	}

	public void setChecked(boolean checked) {
		checkedProperty.set(checked);
	}

	public BooleanProperty checkedProperty() {
		return checkedProperty;
	}

}