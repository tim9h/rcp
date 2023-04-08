package dev.tim9h.rcp.controls;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

public class IconButton extends StackPane {

	private static final double SIZE = 30;

	@Inject
	private EventManager eventManager;

	private Button button;

	public IconButton() {
		button = new Button();
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

	public void setOnAction(EventHandler<ActionEvent> value) {
		button.setOnAction(value);
		button.setOnMouseReleased(e -> eventManager.post(new CcEvent(CcEvent.EVENT_CLI_REQUEST_FOCUS)));
	}

}
