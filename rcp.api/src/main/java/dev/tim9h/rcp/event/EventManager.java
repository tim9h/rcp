package dev.tim9h.rcp.event;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.text.Text;

public interface EventManager {

	public void post(CcEvent event);

	public void post(String eventName);

	public void post(String eventName, String payload);

	public void listen(String name, Consumer<Object[]> action);

	public void echo(String response);

	public void echo(String details, String response);

	public void echoAsync(String response);

	public void echoAsync(String details, String response);

	public void echo(List<Text> details, List<Text> response);

	public void clear();

	public void clearAsync();

	public void showWaitingIndicator();

	public void showWaitingIndicatorAsync();

	public void showToast(String title, String message);

	public void showToast(String message);

	public void showToastAsync(String title, String message);

	public void showToastAsync(String message);

	public void textToSpeech(String text);

	public void textToSpeechAsync(String text);

	public void say(String text);

	public void sayAsync(String text);

	/**
	 * Post a request event with correlation ID for request/response pattern
	 * 
	 * @param eventName    the event name
	 * @param correlationId unique ID to correlate requests and responses
	 * @param payload      optional payload
	 */
	public void postRequest(String eventName, String correlationId, Object... payload);

	/**
	 * Listen for a response event with the given correlation ID (blocks with timeout)
	 * 
	 * @param correlationId unique ID to correlate requests and responses
	 * @param timeoutMs     maximum time to wait in milliseconds
	 * @return              the response payload or null if timeout occurs
	 */
	public Object[] listenForResponse(String correlationId, long timeoutMs);

	/**
	 * Post a response event with correlation ID (used by event listeners to send back responses)
	 * 
	 * @param correlationId unique ID to correlate requests and responses
	 * @param payload       response payload
	 */
	public void postResponse(String correlationId, Object... payload);

}
