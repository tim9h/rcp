package dev.tim9h.rcp.controls.tray;

public class MenuItemData {

	public final String label;

	public final Runnable action;

	public final boolean checkable;

	public final boolean checked;

	public MenuItemData(String label, Runnable action) {
		this(label, action, false, false);
	}

	public MenuItemData(String label, Runnable action, boolean checkable, boolean checked) {
		this.label = label;
		this.action = action;
		this.checkable = checkable;
		this.checked = checked;
	}

}