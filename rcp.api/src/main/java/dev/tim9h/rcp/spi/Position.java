package dev.tim9h.rcp.spi;

public enum Position {

	TOP(0), MIDDLE(1), BOTTOM(2);

	private final int weight;

	Position(int weight) {
		this.weight = weight;
	}

	public int getWeight() {
		return weight;
	}

}