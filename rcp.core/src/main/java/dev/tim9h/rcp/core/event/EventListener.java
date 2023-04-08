package dev.tim9h.rcp.core.event;

import com.google.common.eventbus.Subscribe;

import dev.tim9h.rcp.event.CcEvent;

@FunctionalInterface
public interface EventListener {

	@Subscribe
	public void eventReceived(CcEvent event);

}