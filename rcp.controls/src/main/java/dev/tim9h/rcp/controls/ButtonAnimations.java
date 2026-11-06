package dev.tim9h.rcp.controls;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.util.Duration;

/** Installs subtle hover and pressed animations on buttons in a scene graph. */
public final class ButtonAnimations {

	private static final String PARENT_LISTENER_KEY = ButtonAnimations.class.getName() + ".parentListener";

	private static final String BUTTON_STATE_KEY = ButtonAnimations.class.getName() + ".buttonState";

	private static final Duration DURATION = Duration.millis(120);

	private ButtonAnimations() {
		// Utility class
	}

	/**
	 * Installs animations recursively and watches parent nodes for controls added
	 * later.
	 *
	 * @param node              the root node to process
	 * @param animationsEnabled whether animations are currently enabled
	 */
	public static void install(Node node, BooleanSupplier animationsEnabled) {
		if (node instanceof ButtonBase button) {
			installButton(button, animationsEnabled);
		}

		if (node instanceof Parent parent && !parent.getProperties().containsKey(PARENT_LISTENER_KEY)) {
			parent.getProperties().put(PARENT_LISTENER_KEY, Boolean.TRUE);
			parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
				while (change.next()) {
					if (change.wasAdded()) {
						change.getAddedSubList().forEach(child -> install(child, animationsEnabled));
					}
				}
			});
			parent.getChildrenUnmodifiable().forEach(child -> install(child, animationsEnabled));
		}
	}

	private static void installButton(ButtonBase button, BooleanSupplier animationsEnabled) {
		if (button.getProperties().containsKey(BUTTON_STATE_KEY)) {
			return;
		}

		var state = new ButtonState(button, animationsEnabled);
		button.getProperties().put(BUTTON_STATE_KEY, state);
		button.hoverProperty().addListener((_, _, _) -> state.update());
		button.pressedProperty().addListener((_, _, _) -> state.update());
	}

	private static final class ButtonState {

		private final ButtonBase button;

		private final BooleanSupplier animationsEnabled;

		private Timeline animation;

		private ButtonState(ButtonBase button, BooleanSupplier animationsEnabled) {
			this.button = button;
			this.animationsEnabled = animationsEnabled;
		}

		private void update() {
			if (animation != null) {
				animation.stop();
			}

			var scale = button.isPressed() ? 0.96 : button.isHover() ? 1.02 : 1.0;
			if (!animationsEnabled.getAsBoolean()) {
				button.setScaleX(scale);
				button.setScaleY(scale);
				return;
			}

			animation = new Timeline(
					new KeyFrame(DURATION, new KeyValue(button.scaleXProperty(), scale, Interpolator.EASE_BOTH),
							new KeyValue(button.scaleYProperty(), scale, Interpolator.EASE_BOTH)));
			animation.play();
		}
	}
}