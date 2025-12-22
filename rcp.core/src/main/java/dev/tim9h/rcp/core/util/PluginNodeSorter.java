package dev.tim9h.rcp.core.util;

import java.util.Comparator;

import org.apache.commons.lang3.math.NumberUtils;

import dev.tim9h.rcp.spi.Plugin;

public class PluginNodeSorter implements Comparator<Object> {

	@Override
	public int compare(Object object1, Object object2) {
		var card1 = (Plugin) object1;
		var card2 = (Plugin) object2;
		if (card1.getGravity().position() == card2.getGravity().position()) {
			return Integer.compare(card1.getGravity().weight(), card2.getGravity().weight());
		} else {
			return Integer.compare(card1.getGravity().position().getWeight(),
					card2.getGravity().position().getWeight());
		}
	}

}
