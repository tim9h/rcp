package dev.tim9h.rcp.spi;

public record Gravity(int weight, Position position) {

	public Gravity(Position position) {
		this(0, position);
	}

}