package dev.tim9h.rcp.core.event;

import com.google.common.eventbus.Subscribe;

import dev.tim9h.rcp.event.Event;

@FunctionalInterface
public interface EventListener {

	@Subscribe
	public void eventReceived(Event event);

}