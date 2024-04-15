package dev.tim9h.rcp.controls;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.settings.Settings;
import javafx.animation.FadeTransition;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class AnimatedLabel extends FlowPane {

	private static final String SETTINGS_ANIMATIONS_ENABLED = "core.ui.animations";

	public static final String SETTING_TEXT_ANIMATION_DURATION = "ui.components.label.animationduration";

	private Settings settings;

	private Duration duration;

	private FadeTransition fadeIn;

	private FadeTransition fadeOut;

	private Timer timer;

	private TextFlow textFlow;

	private Rectangle clip;

	@Inject
	public AnimatedLabel(Settings settings, EventManager eventManager) {
		this.settings = settings;
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, data -> duration = null);

		fadeIn = new FadeTransition(getAnimationDuration(), this);
		fadeIn.setFromValue(0.0);
		fadeIn.setToValue(1.0);

		fadeOut = new FadeTransition(getAnimationDuration(), this);
		fadeOut.setFromValue(1.0);
		fadeOut.setToValue(0.0);

		timer = new Timer("labelHidingTimer");

		textFlow = new TextFlow();
		textFlow.setTextAlignment(TextAlignment.CENTER);
		getChildren().add(textFlow);

		clip = new Rectangle();
		setClip(clip);
		setMaxWidth(Double.MAX_VALUE);

		clip.heightProperty().bind(heightProperty());

		setText(StringUtils.EMPTY);
	}

	public void setClipWidth(double width) {
		clip.setWidth(width);

		// center the text
		textFlow.setMinWidth(width);
		setMinWidth(width);
	}

	private Duration getAnimationDuration() {
		if (duration == null) {
			try {
				duration = Duration.millis(settings.getDouble(SETTING_TEXT_ANIMATION_DURATION).doubleValue());
			} catch (NullPointerException e) {
				settings.addSetting(SETTING_TEXT_ANIMATION_DURATION, "250");
				return getAnimationDuration();
			}
		}
		return duration;
	}

	public void showText(String text) {
		if (!getText().equals(text)) {
			if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
				fadeIn.play();
			}
			setText(text);
		}
	}

	public void showText(List<Text> texts) {
		showText(texts.toArray(new Text[0]));
	}

	public void showText(Text... texts) {
		var text = Arrays.stream(texts).map(node -> node.textProperty().get()).collect(Collectors.joining());
		if (!getText().equals(text)) {
			if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
				fadeIn.play();
			}
			setText(texts);
		}
	}

	public void setText(String text) {
		textFlow.getChildren().clear();
		var textnode = new Text(text);
		textnode.getStyleClass().add("text");
		textnode.getStyleClass().addAll(getStyleClass());
		textFlow.getChildren().add(textnode);
	}

	public void setText(List<Text> texts) {
		setText(texts.toArray(new Text[0]));
	}

	public void setText(Text... texts) {
		Arrays.stream(texts).forEach(text -> {
			text.getStyleClass().add("text");
			text.getStyleClass().addAll(getStyleClass());
		});
		textFlow.getChildren().clear();
		textFlow.getChildren().addAll(texts);
	}

	public String getText() {
		return textFlow.getChildren().stream().map(node -> ((Text) node).textProperty().get())
				.collect(Collectors.joining());
	}

	public void showFadingText(String text) {
		showFadingText(decaytime(text), text);
	}

	public void showFadingText(List<Text> texts) {
		showFadingText(texts.toArray(new Text[0]));
	}

	public void showFadingText(Text... texts) {
		var text = Arrays.stream(texts).map(node -> node.textProperty().get()).collect(Collectors.joining());
		showFadingText(decaytime(text), texts);
	}

	public static int decaytime(String text) {
		return 1500 + (text.length() * 100);
	}

	public void showFadingText(int decaytime, String text) {
		timer.cancel();
		timer.purge();
		showText(text);
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				hideText();
			}
		}, decaytime);
	}

	public void showFadingText(int decaytime, Text... texts) {
		timer.cancel();
		timer.purge();
		showText(texts);
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				hideText();
			}
		}, decaytime);
	}

	public void hideText() {
		if (!getText().isBlank()) {
			if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
				fadeOut.play();
				fadeOut.setOnFinished(e -> setText(StringUtils.EMPTY));
			} else {
				setText(StringUtils.EMPTY);
			}
		}
	}

}
