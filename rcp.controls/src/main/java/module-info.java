module rcp.controls {
	exports dev.tim9h.rcp.controls;
	exports dev.tim9h.rcp.controls.utils;

	requires transitive rcp.api;
	requires com.google.guice;
	requires javafx.base;
	requires transitive javafx.controls;
	requires javafx.graphics;
	requires org.apache.commons.lang3;
	requires org.apache.logging.log4j;
}