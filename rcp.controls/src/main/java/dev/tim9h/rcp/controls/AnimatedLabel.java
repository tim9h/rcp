package dev.tim9h.rcp.controls;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class AnimatedLabel extends FlowPane {

	private static final String SETTINGS_ANIMATIONS_ENABLED = "core.ui.animations";

	public static final String SETTING_TEXT_ANIMATION_DURATION = "ui.components.label.animationduration";

	public static final String SETTING_TEXT_ANIMATION_STYLE = "ui.components.label.animations";

	private static final String ANIMATION_TYPING = "type";

	private static final String ANIMATION_FADE = "fade";

	private Settings settings;

	private Duration duration;

	private List<String> animationStyles;

	private FadeTransition fadeIn;

	private FadeTransition fadeOut;

	private Timeline timeline;

	private AtomicInteger charIndex = new AtomicInteger(0);

	private Timer timer;

	private TextFlow textFlow;

	private Rectangle clip;

	@InjectLogger
	private Logger logger;

	@Inject
	public AnimatedLabel(Settings settings, EventManager eventManager) {
		this.settings = settings;
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, _ -> {
			duration = null;
			animationStyles = null;
		});

		fadeIn = new FadeTransition(getAnimationDuration(), this);
		fadeIn.setFromValue(0.0);
		fadeIn.setToValue(1.0);
		timeline = new Timeline();

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
				settings.addSetting(SETTING_TEXT_ANIMATION_DURATION, "500");
				return getAnimationDuration();
			}
		}
		return duration;
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
		if (!getText().equals(text)) {
			if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
				if (getAnimationStyles().contains(ANIMATION_FADE)) {
					logger.debug(() -> "Animation Fade for text: " + text);
					fadeIn.play();
				}
				if (getAnimationStyles().contains(ANIMATION_TYPING)) {
					logger.debug(() -> "Animation Type for text: " + text);
					typeText(text);
				}
				if (getAnimationStyles().isEmpty()) {
					logger.debug(() -> "No supported animation for text: " + text);
					setText(text);
				}
			}
			logger.debug(() -> "Animations disabled for text: " + text);
			setText(text);
		}
	}

	private void typeText(String text) {
		if (!getAnimationStyles().contains(ANIMATION_FADE)) {
			setOpacity(1);
		}
		timeline.stop();
		if (Animation.Status.STOPPED.equals(timeline.getStatus())) {
			timeline.getKeyFrames().clear();
			var stringbuilder = new StringBuilder();
			charIndex.set(0);
			timeline.getKeyFrames().add(new KeyFrame(Duration.millis(10), _ -> {
				if (charIndex.get() < text.length()) {
					stringbuilder.append(text.charAt(charIndex.get()));
					setText(stringbuilder.toString());
					charIndex.incrementAndGet();
				}
			}));
			timeline.setCycleCount(text.length());
			timeline.play();
		} else {
			logger.warn(() -> "Unable to animate text: " + text + " because timeline is not stopped");
		}

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
		logger.debug(() -> "Showing fading text: " + text + " for " + decaytime + "ms with timer " + timer);
		logger.debug(() -> "Trying to cancel timer " + timer);
		timer.cancel();
		var purge = timer.purge();
		logger.debug(() -> "Purged " + purge + " tasks for timer " + timer);
		showText(text);
		timer = new Timer(this.getClass().getTypeName() + "HidingTimer");
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				hideText();
			}
		}, decaytime);
	}

	public void showFadingText(int decaytime, Text... texts) {
		logger.debug(() -> "Showing fading texts: " + texts + " for " + decaytime + "ms with timer " + timer);
		logger.debug(() -> "Trying to cancel timer " + timer);
		timer.cancel();
		var purge = timer.purge();
		logger.debug(() -> "Purged " + purge + " tasks for timer " + timer);
		showText(texts);
		timer = new Timer(this.getClass().getTypeName() + "HidingTimer");
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				hideText();
			}
		}, decaytime);
	}

	public void hideText() {
		logger.debug(() -> "Hiding text " + getText());
		if (!getText().isBlank()) {
			if (settings.getBoolean(SETTINGS_ANIMATIONS_ENABLED).booleanValue()) {
				logger.debug(() -> "Fading out text " + getText());
				fadeOut.play();
				fadeOut.setOnFinished(_ -> setText(StringUtils.EMPTY));
			} else {
				setText(StringUtils.EMPTY);
			}
		} else {
			logger.debug(() -> "Not hiding text, because it is already empty");
		}
	}

}
