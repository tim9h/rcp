package dev.tim9h.rcp.core.windows;

public interface IdleChangeListener {

	public void onIdle();

	public void onAway();

	public void onOnline();

	public void onUknown();

}
