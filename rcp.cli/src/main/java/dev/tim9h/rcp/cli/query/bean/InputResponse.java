package dev.tim9h.rcp.cli.query.bean;

import java.util.List;

import javafx.scene.text.Text;

public record InputResponse(List<Text> interpretation, List<Text> response, Text responseFormatted) {

	public InputResponse(List<Text> interpretation, List<Text> response) {
		this(interpretation, response, response.get(0));
	}

}
