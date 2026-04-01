module rcp.controls {
	exports dev.tim9h.rcp.controls;
	exports dev.tim9h.rcp.controls.utils;
	exports dev.tim9h.rcp.controls.tray;

	requires transitive rcp.api;
	requires transitive com.google.guice;
	requires javafx.base;
	requires transitive javafx.controls;
	requires javafx.graphics;
	requires org.apache.commons.lang3;
	requires org.apache.logging.log4j;
	requires dev.tim9h.collections;
	requires java.desktop;

	opens dev.tim9h.rcp.controls;
}