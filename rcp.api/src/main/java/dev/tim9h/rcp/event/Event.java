package dev.tim9h.rcp.event;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javafx.scene.text.Text;

public record Event(String name, Object... payload) {

	public static final String EVENT_SHOWN = "RCP_SHOWN";

	public static final String EVENT_HIDDEN = "RCP_HIDDEN";

	public static final String EVENT_CLOSING = "RCP_CLOSING";

	public static final String EVENT_CLOSING_FINISHED = "RCP_CLOSING_FINISHED";

	public static final String EVENT_SETTINGS_CHANGED = "RCP_SETTINGS_CHANGED";

	public static final String EVENT_RESTARTING = "RCP_RESTARTING";

	public static final String EVENT_CLI_COMMAND = "CLI_COMMAND";

	public static final String EVENT_CLI_RESPONSE = "CLI_RESPONSE";

	public static final String EVENT_CLI_RESPONSE_COPY = "CLI_RESPONSE_COPY";

	public static final String EVENT_CLI_REQUEST_FOCUS = "CLI_REQUEST_FOCUS";

	public static final String EVENT_CLI_ADD_PROPOSALS = "CLI_CONTENT_ADD_PROPOSALS";

	public static final String EVENT_TOAST = "TRAY_SHOW_TOAST";

	public static final String EVENT_TTS = "TTS";

	public static final String EVENT_SAY = "TTS.SAY";

	public static final String EVENT_THEME_CHANGED = "THEME_CHANGED";

	public static final String EVENT_THEME_MODE_CHANGED = "THEME_MODE_CHANGED";

	public static final String EVENT_ALERT = "alert";

	public Event(String name) {
		this(name, (Object[]) null);
	}

	public Event(String name, String payload) {
		this(name, (Object) payload);
	}

	public Event(String name, String text1, String text2) {
		this(name, (Object) text1, (Object) text2);
	}

	public Event(String name, List<Text> text1, List<Text> text2) {
		this(name, (Object) text1, (Object) text2);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		var other = (Event) obj;
		return Objects.equals(name, other.name) && Arrays.deepEquals(payload, other.payload);
	}

	@Override
	public int hashCode() {
		final var prime = 31;
		var result = 1;
		result = prime * result + Arrays.deepHashCode(payload);
		result = prime * result + Objects.hash(name);
		return result;
	}

	@Override
	public final String toString() {
		return "Event [name=" + name + ", payload=" + Arrays.toString(payload) + "]";
	}

}
