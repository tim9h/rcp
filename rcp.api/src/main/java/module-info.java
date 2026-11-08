module rcp.api {
	exports dev.tim9h.rcp.event;
	exports dev.tim9h.rcp.logging;
	exports dev.tim9h.rcp.settings;
	exports dev.tim9h.rcp.spi;
	exports dev.tim9h.rcp.service;
	exports dev.tim9h.rcp.util;

	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires org.apache.logging.log4j;
	requires org.apache.commons.lang3;
}