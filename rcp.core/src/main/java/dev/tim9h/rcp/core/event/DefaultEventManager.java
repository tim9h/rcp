package dev.tim9h.rcp.core.event;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import javafx.application.Platform;
import javafx.scene.text.Text;

@Singleton
public class DefaultEventManager implements EventManager {

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	private EventBus bus;

	// For request/response correlation
	private final ConcurrentHashMap<String, ResponseHandler> responseHandlers = new ConcurrentHashMap<>();

	public record ResponseHandler(CountDownLatch latch, Object[] payload) {
		public ResponseHandler(CountDownLatch latch) {
			this(latch, null);
		}
	}

	public DefaultEventManager() {
		bus = new EventBus("rcp");
		listen("clear", _ -> clear());
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
			if (Strings.CI.equals(event.name(), name)) {
				action.accept(event.payload());
			}
		});
	}

	@Override
	public void echo(String message) {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, StringUtils.EMPTY, message));
	}

	@Override
	public void echoAsync(String response) {
		Platform.runLater(() -> echo(response));
	}

	@Override
	public void echo(String details, String response) {
		post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE, details, response));
	}

	@Override
	public void echoAsync(String details, String response) {
		Platform.runLater(() -> echo(details, response));
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
	public void showToast(String message) {
		var appTitle = settings.getString("core.ui.title", "RCP");
		showToast(appTitle, message);
	}

	@Override
	public void showToastAsync(String title, String message) {
		Platform.runLater(() -> showToast(title, message));
	}

	@Override
	public void showToastAsync(String message) {
		Platform.runLater(() -> showToast(message));
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

	@Override
	public void postRequest(String eventName, String correlationId, Object... payload) {
		var eventPayload = new Object[payload != null ? payload.length + 1 : 1];
		eventPayload[0] = correlationId;
		if (payload != null) {
			System.arraycopy(payload, 0, eventPayload, 1, payload.length);
		}
		post(new CcEvent(eventName, eventPayload));
	}

	@Override
	public Object[] listenForResponse(String correlationId, long timeoutMs) {
		var latch = new CountDownLatch(1);
		var handler = new ResponseHandler(latch, null);
		responseHandlers.put(correlationId, handler);

		try {
			if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
				// Response received
				var handlerWithResponse = responseHandlers.remove(correlationId);
				return handlerWithResponse != null ? handlerWithResponse.payload : null;
			} else {
				// Timeout occurred
				responseHandlers.remove(correlationId);
				logger.warn(() -> "Timeout waiting for response with correlation ID: " + correlationId);
				return null;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			responseHandlers.remove(correlationId);
			logger.error(() -> "Interrupted while waiting for response with correlation ID: " + correlationId, e);
			return null;
		}
	}

	@Override
	public void postResponse(String correlationId, Object... payload) {
		var handler = responseHandlers.get(correlationId);
		if (handler != null) {
			// Create new handler with the payload and signal completion
			var updatedHandler = new ResponseHandler(handler.latch(), payload);
			responseHandlers.put(correlationId, updatedHandler);
			handler.latch().countDown();
		} else {
			logger.debug(() -> "No pending request for correlation ID: " + correlationId);
		}
	}

}
