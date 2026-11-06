package dev.tim9h.rcp.controls;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Shape;

public class IconButton extends StackPane {

	private static final double SIZE = 30;

	@Inject
	private EventManager eventManager;

	private Button button;

	public IconButton() {
		button = new Button();
		StackPane.setMargin(button, new Insets(0, 4, 0, 4));
		button.getStyleClass().add("icon-button");
		getChildren().add(button);
		setAlignment(Pos.CENTER);
		button.setMaxHeight(SIZE);
		button.setMinHeight(SIZE);
		button.setMaxWidth(SIZE);
		button.setMinWidth(SIZE);
	}

	public void setLabel(char label) {
		button.setText(String.valueOf(label));
	}

	public void setGraphic(Node graphic) {
		button.textProperty().unbind();
		button.setText(null);
		button.setGraphic(graphic);
	}

	public void bindGraphicFill(Shape graphic) {
		graphic.fillProperty().bind(button.textFillProperty());
	}

	public void setOnAction(EventHandler<ActionEvent> value) {
		button.setOnAction(value);
		button.setOnMouseReleased(_ -> eventManager.post(new Event(Event.EVENT_CLI_REQUEST_FOCUS)));
	}

	public void bindLabel(ObservableValue<String> label) {
		button.textProperty().bind(label);
	}

}
