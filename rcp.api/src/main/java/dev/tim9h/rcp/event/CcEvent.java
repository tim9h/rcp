package dev.tim9h.rcp.event;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dev.tim9h.rcp.spi.TreeNode;
import javafx.scene.text.Text;

public record CcEvent(String name, Object... payload) {

	public static final String EVENT_SHOWN = "CC_SHOWN";

	public static final String EVENT_HIDDEN = "CC_HIDDEN";

	public static final String EVENT_CLOSING = "CC_CLOSING";

	public static final String EVENT_CLOSING_FINISHED = "CC_CLOSING_FINISHED";

	public static final String EVENT_SETTINGS_CHANGED = "CC_SETTINGS_CHANGED";

	public static final String EVENT_RESTARTING = "CC_RESTARTING";

	public static final String EVENT_CLI_RESPONSE = "CLI_RESPONSE";

	public static final String EVENT_CLI_RESPONSE_COPY = "CLI_RESPONSE_COPY";

	public static final String EVENT_CLI_REQUEST_FOCUS = "CLI_REQUEST_FOCUS";

	public static final String EVENT_CLI_ADD_PROPOSALS = "CLI_CONTENT_ADD_PROPOSALS";

	public static final String EVENT_TOAST = "TRAY_SHOW_TOAST";

	public static final String EVENT_TTS = "TTS";

	public CcEvent(String name) {
		this(name, (Object[]) null);
	}

	public CcEvent(String name, String payload) {
		this(name, (Object) payload);
	}

	public CcEvent(String name, String text1, String text2) {
		this(name, (Object) text1, (Object) text2);
	}

	public CcEvent(String name, TreeNode<String> payload) {
		this(name, (Object) payload);
	}

	public CcEvent(String name, List<Text> text1, List<Text> text2) {
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
		CcEvent other = (CcEvent) obj;
		return Objects.equals(name, other.name) && Arrays.deepEquals(payload, other.payload);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.deepHashCode(payload);
		result = prime * result + Objects.hash(name);
		return result;
	}

	@Override
	public String toString() {
		return "CcEvent [name=" + name + ", payload=" + Arrays.toString(payload) + "]";
	}

}
