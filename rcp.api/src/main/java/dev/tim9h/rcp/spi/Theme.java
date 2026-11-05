package dev.tim9h.rcp.spi;

/** A named application theme backed by a JavaFX stylesheet. */
public record Theme(String name, String stylesheet, boolean dark) {
	public Theme {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Theme name must not be blank");
		}
		if (stylesheet == null || stylesheet.isBlank()) {
			throw new IllegalArgumentException("Theme stylesheet must not be blank");
		}
	}
}