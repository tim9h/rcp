package dev.tim9h.rcp.core.windows;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

record WindowsHotkey(int modifiers, int virtualKey) {

	private static final int MOD_ALT = 0x0001;
	private static final int MOD_CONTROL = 0x0002;
	private static final int MOD_SHIFT = 0x0004;
	private static final int MOD_WIN = 0x0008;

	private static final Map<String, Integer> MODIFIER_KEYS = Map.of("ALT", MOD_ALT, "CTRL", MOD_CONTROL, "SHIFT",
			MOD_SHIFT, "WIN", MOD_WIN);

	private static final Map<String, Integer> NAMED_KEYS = Map.ofEntries(Map.entry("SPACE", 0x20),
			Map.entry("ENTER", 0x0D), Map.entry("TAB", 0x09), Map.entry("ESC", 0x1B), Map.entry("ESCAPE", 0x1B),
			Map.entry("BACKSPACE", 0x08), Map.entry("DELETE", 0x2E), Map.entry("INSERT", 0x2D), Map.entry("HOME", 0x24),
			Map.entry("END", 0x23), Map.entry("PAGEUP", 0x21), Map.entry("PAGEDOWN", 0x22), Map.entry("LEFT", 0x25),
			Map.entry("UP", 0x26), Map.entry("RIGHT", 0x27), Map.entry("DOWN", 0x28), Map.entry("PRINTSCREEN", 0x2C),
			Map.entry("PAUSE", 0x13));

	private static final int MOD_NOREPEAT = 0x4000;

	static WindowsHotkey parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Hotkey must not be blank (example: ALT+SPACE)");
		}

		var tokens = value.toUpperCase(Locale.ROOT).split("\\+");
		var modifiers = 0;
		var seenModifiers = new HashSet<String>();
		var virtualKey = -1;
		for (var token : tokens) {
			var part = token.trim();
			if (part.isEmpty()) {
				throw new IllegalArgumentException("Invalid hotkey syntax: " + value);
			}
			var modifier = MODIFIER_KEYS.get(part);
			if (modifier != null) {
				if (!seenModifiers.add(part)) {
					throw new IllegalArgumentException("Duplicate hotkey modifier: " + part);
				}
				modifiers |= modifier;
				continue;
			}
			if (virtualKey != -1) {
				throw new IllegalArgumentException("Specify exactly one key in hotkey: " + value);
			}
			virtualKey = parseKey(part);
		}

		if (modifiers == 0) {
			throw new IllegalArgumentException("Hotkey must include CTRL, ALT, SHIFT, or WIN");
		}
		if (virtualKey == -1) {
			throw new IllegalArgumentException("Hotkey must include a key (example: ALT+SPACE)");
		}
		return new WindowsHotkey(modifiers | MOD_NOREPEAT, virtualKey);
	}

	private static int parseKey(String token) {
		var namedKey = NAMED_KEYS.get(token);
		if (namedKey != null) {
			return namedKey;
		}
		if (token.length() == 1) {
			var character = token.charAt(0);
			if (character >= 'A' && character <= 'Z' || character >= '0' && character <= '9') {
				return character;
			}
		}
		if (token.matches("F([1-9]|1[0-9]|2[0-4])")) {
			return 0x70 + Integer.parseInt(token.substring(1)) - 1;
		}
		throw new IllegalArgumentException("Unsupported Windows hotkey key: " + token);
	}
}
