package dev.tim9h.rcp.controls;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class AnimatedLabel extends FlowPane {

	private static final String SETTINGS_ANIMATIONS_ENABLED = "core.ui.animations";

	public static final String SETTING_FADE_ANIMATION_DURATION = "ui.components.label.fade.duration";

	public static final String SETTING_TYPE_ANIMATION_INTERVAL = "ui.components.label.type.interval";

	public static final String SETTING_TEXT_ANIMATION_STYLE = "ui.components.label.animations";

	private static final String ANIMATION_TYPING = "type";

	private static final String ANIMATION_FADE = "fade";

	private Settings settings;

	private Duration fadeDuration;

	private Duration typeInterval;

	private List<String> animationStyles;

	private FadeTransition fadeIn;

	private FadeTransition fadeOut;

	private Timeline timeline;

	private TextFlow textFlow;

	private Rectangle clip;

	private PauseTransition hideDelay;

	@InjectLogger
	private Logger logger;

	@Inject
	public AnimatedLabel(Settings settings, EventManager eventManager) {
		this.settings = settings;
		eventManager.listen(Event.EVENT_SETTINGS_CHANGED, _ -> clearSettingsCache());

		fadeIn = new FadeTransition(getAnimationDuration(), this);
		fadeIn.setFromValue(0.0);
		fadeIn.setToValue(1.0);
		timeline = new Timeline();

		fadeOut = new FadeTransition(getAnimationDuration(), this);
		fadeOut.setFromValue(1.0);
		fadeOut.setToValue(0.0);

		hideDelay = new PauseTransition();
		hideDelay.setOnFinished(_ -> hideText());

		textFlow = new TextFlow();
		textFlow.setTextAlignment(TextAlignment.CENTER);
		getChildren().add(textFlow);

		clip = new Rectangle();
		setClip(clip);
		setMaxWidth(Double.MAX_VALUE);

		clip.heightProperty().bind(heightProperty());

		setText(StringUtils.EMPTY);
	}

	private void clearSettingsCache() {
		fadeDuration = null;
		typeInterval = null;
		animationStyles = null;
	}

	public void setClipWidth(double width) {
		clip.setWidth(width);

		// center the text
		textFlow.setMinWidth(width);
		setMinWidth(width);
	}

	private Duration getAnimationDuration() {
		if (fadeDuration == null) {
			try {
				fadeDuration = Duration.millis(settings.getDouble(SETTING_FADE_ANIMATION_DURATION).doubleValue());
			} catch (NullPointerException _) {
				settings.addSetting(SETTING_FADE_ANIMATION_DURATION, "500");
				return getAnimationDuration();
			}
		}
		return fadeDuration;
	}

	private Duration getTypeInterval() {
		if (typeInterval == null) {
			try {
				typeInterval = Duration.millis(settings.getDouble(SETTING_TYPE_ANIMATION_INTERVAL).doubleValue());
			} catch (NullPointerException _) {
				settings.addSetting(SETTING_TYPE_ANIMATION_INTERVAL, "10");
				return getTypeInterval();
			}
		}
		return typeInterval;
	}

	private List<String> getAnimationStyles() {
		if (animationStyles == null) {
			animationStyles = settings.getStringList(SETTING_TEXT_ANIMATION_STYLE);
			if (animationStyles.isEmpty()) {
				var allStyles = List.of(ANIMATION_FADE, ANIMATION_TYPING);
				settings.addSetting(SETTING_TEXT_ANIMATION_STYLE, allStyles);
				animationStyles = allStyles;
			}
		}
		return animationStyles;
	}

	public void showText(String text) {
		if (getText().equals(text)) {
			return;
		}

		// Cancel any pending hide and an existing fade-out.
		hideDelay.stop();
		fadeOut.stop();

		if (!settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
			timeline.stop();
			setOpacity(1);
			setText(text);
			return;
		}

		var styles = getAnimationStyles();

		if (styles.contains(ANIMATION_FADE) && getOpacity() < 1) {
			fadeIn.playFromStart();
		}

		if (styles.contains(ANIMATION_TYPING)) {
			typeText(text);
		} else {
			timeline.stop();
			setText(text);
		}
	}

	private void typeText(String text) {
		timeline.stop();
		timeline.getKeyFrames().clear();

		var visibleText = new StringBuilder(getText());
		var animationLength = Math.max(visibleText.length(), text.length());

		if (animationLength == 0) {
			setText(StringUtils.EMPTY);
			return;
		}

		int[] index = { 0 };

		timeline.getKeyFrames().add(new KeyFrame(getTypeInterval(), _ -> {
			var i = index[0];

			if (i < text.length()) {
				// Replace existing characters or append new ones.
				if (i < visibleText.length()) {
					visibleText.setCharAt(i, text.charAt(i));
				} else {
					visibleText.append(text.charAt(i));
				}
			} else if (visibleText.length() > text.length()) {
				// Remove leftover characters one at a time.
				visibleText.deleteCharAt(visibleText.length() - 1);
			}

			setText(visibleText.toString());
			index[0]++;
		}));

		timeline.setCycleCount(animationLength);
		timeline.setOnFinished(_ -> setText(text));
		timeline.playFromStart();
	}

	public void showText(List<Text> texts) {
		showText(texts.toArray(new Text[0]));
	}

	public void showText(Text... texts) {
		var text = Arrays.stream(texts).map(node -> node.textProperty().get()).collect(Collectors.joining());
		showText(text);
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
		hideDelay.stop();

		showText(text);

		hideDelay.setDuration(Duration.millis(decaytime));
		hideDelay.playFromStart();
	}

	public void showFadingText(int decaytime, Text... texts) {
		var text = Arrays.stream(texts).map(Text::getText).collect(Collectors.joining());
		showFadingText(decaytime, text);
	}

	public void hideText() {
		if (getText().isBlank()) {
			return;
		}

		timeline.stop();
		fadeIn.stop();

		if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
			fadeOut.setOnFinished(_ -> setText(StringUtils.EMPTY));
			fadeOut.playFromStart();
		} else {
			setText(StringUtils.EMPTY);
		}
	}

}
