package dev.tim9h.rcp.event;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.text.Text;

public interface EventManager {

	void post(Event event);

	void post(String eventName);

	void post(String eventName, String payload);

	void listen(String eventName, Consumer<Object[]> action);

	void unsubscribe(String eventName);

	void unsubscribe(String eventName, Object subscriber);

	void echo(String response);

	void echo(String details, String response);

	void echoAsync(String response);

	void echoAsync(String details, String response);

	void echo(List<Text> details, List<Text> response);

	void clear();

	void clearAsync();

	void showWaitingIndicator();

	void showWaitingIndicatorAsync();

	void showToast(String title, String message);

	void showToastAsync(String title, String message);

	void textToSpeech(String text);

	void textToSpeechAsync(String text);

	void say(String text);

	void sayAsync(String text);

	/**
	 * Post a request event with correlation ID for request/response pattern
	 * 
	 * @param eventName     the event name
	 * @param correlationId unique ID to correlate requests and responses
	 * @param payload       optional payload
	 */
	void postRequest(String eventName, String correlationId, Object... payload);

	/**
	 * Listen for a response event with the given correlation ID (blocks with
	 * timeout)
	 * 
	 * @param correlationId unique ID to correlate requests and responses
	 * @param timeoutMs     maximum time to wait in milliseconds
	 * @return the response payload or null if timeout occurs
	 */
	public Object[] listenForResponse(String correlationId, long timeoutMs);

	/**
	 * Post a response event with correlation ID (used by event listeners to send
	 * back responses)
	 * 
	 * @param correlationId unique ID to correlate requests and responses
	 * @param payload       response payload
	 */
	void postResponse(String correlationId, Object... payload);

}
