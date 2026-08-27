package dev.tim9h.rcp.core.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EventHelper {

	private EventHelper() {
		// hide implicit public constructor
	}

	public static String joinPayload(Object[] payload, int from) {
		if (payload == null || from >= payload.length) {
			return null;
		}
		return Arrays.stream(payload).skip(from).map(String::valueOf).collect(Collectors.joining(" "));
	}

}
