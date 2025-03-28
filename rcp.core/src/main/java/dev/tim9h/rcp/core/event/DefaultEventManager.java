package dev.tim9h.rcp.core.event;

import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.EventBus;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import javafx.application.Platform;
import javafx.scene.text.Text;

@Singleton
public class DefaultEventManager implements EventManager {

	@InjectLogger
	private Logger logger;

	private EventBus bus;

	public DefaultEventManager() {
		bus = new EventBus("rcp");
	}

	@Override
	public void post(CcEvent event) {
		bus.post(event);
	}
	
	@Override
	public void post(String eventName) {
		post(new CcEvent(eventName));
	}
	
	@Override
	public void post(String eventName, String payload) {
		post(new CcEvent(eventName, payload));
	}

	@Override
	public void listen(String name, Consumer<Object[]> action) {
		bus.register((EventListener) event -> {
			if (StringUtils.equalsIgnoreCase(event.name(), name)) {
				action.accept(event.payload());
			}
		});
	}

	@Override
	public void echo(String message) {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, StringUtils.EMPTY, message));
	}

	@Override
	public void echo(String details, String response) {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, details, response));
	}

	@Override
	public void echo(List<Text> details, List<Text> response) {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, details, response));
	}

	@Override
	public void clear() {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, StringUtils.EMPTY, StringUtils.EMPTY));
	}

	@Override
	public void clearAsync() {
		Platform.runLater(this::clear);
	}

	@Override
	public void showWaitingIndicator() {
		echo(StringUtils.EMPTY, "...");
	}

	@Override
	public void showWaitingIndicatorAsync() {
		Platform.runLater(this::showWaitingIndicator);
	}

	@Override
	public void showToast(String title, String message) {
		post(new CcEvent(CcEvent.EVENT_TOAST, title, message));
	}

	@Override
	public void showToastAsync(String title, String message) {
		Platform.runLater(() -> showToast(title, message));
	}

	@Override
	public void textToSpeech(String text) {
		post(new CcEvent(CcEvent.EVENT_TTS, text));
	}

	@Override
	public void textToSpeechAsync(String text) {
		Platform.runLater(() -> textToSpeech(text));
	}

	@Override
	public void say(String text) {
		post(new CcEvent(CcEvent.EVENT_SAY, text));

	}

	@Override
	public void sayAsync(String text) {
		Platform.runLater(() -> say(text));
	}

}
