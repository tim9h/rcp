package dev.tim9h.rcp.cli.query.bean;

import java.util.List;

import javafx.scene.text.Text;

public record InputResponse(List<Text> interpretation, List<Text> response, Text responseFormatted) {

	public InputResponse(List<Text> interpretation, List<Text> response) {
		this(interpretation, response, response.get(0));
	}

	public InputResponse(List<String> interpretation, List<String> response, String responseFormatted) {
		this(interpretation.stream().map(Text::new).toList(), response.stream().map(Text::new).toList(),
				new Text(responseFormatted));
	}

}
