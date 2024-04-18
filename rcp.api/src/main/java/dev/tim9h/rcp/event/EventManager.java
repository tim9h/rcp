package dev.tim9h.rcp.event;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.text.Text;

public interface EventManager {

	public void post(CcEvent event);

	public void listen(String name, Consumer<Object[]> action);

	public void echo(String response);

	public void echoAsync(String response);

	public void echo(String details, String response);

	public void echoAsync(String details, String response);

	public void echo(List<Text> details, List<Text> response);

	public void echoAsync(List<Text> details, List<Text> response);

	public void clear();

	public void clearAsync();

	public void showWaitingIndicator();

	public void showWaitingIndicatorAsync();

	public void showToast(String title, String message);

	public void showToastAsync(String title, String message);

	public void textToSpeech(String text);

	public void textToSpeechAsync(String text);

}
