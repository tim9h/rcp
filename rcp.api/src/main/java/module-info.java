module rcp.api {
	exports dev.tim9h.rcp.event;
	exports dev.tim9h.rcp.logging;
	exports dev.tim9h.rcp.settings;
	exports dev.tim9h.rcp.spi;

	requires transitive javafx.graphics;
	requires org.apache.commons.lang3;
}