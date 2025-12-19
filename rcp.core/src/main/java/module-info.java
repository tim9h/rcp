import dev.tim9h.rcp.spi.CCardFactory;

module rcp.core {
	exports dev.tim9h.rcp.core.logging;
	exports dev.tim9h.rcp.core.settings;
	exports dev.tim9h.rcp.core.service;
	exports dev.tim9h.rcp.core.util;
	exports dev.tim9h.rcp.core.windows;
	exports dev.tim9h.rcp.core.event;
	exports dev.tim9h.rcp.core;
	exports dev.tim9h.rcp.core.ui;
	exports dev.tim9h.rcp.core.plugin;

	requires transitive javafx.graphics;
	requires transitive rcp.api;
	requires com.google.common;
	requires transitive com.google.guice;
	requires com.sun.jna;
	requires com.sun.jna.platform;
	requires transitive java.desktop;
	requires javafx.base;
	requires javafx.swing;
	requires javafxblur;
	requires jkeymaster;
	requires org.apache.commons.lang3;
	requires org.apache.logging.log4j;
	requires javafx.controls;
	requires javafx.media;
	requires commons.cli;
	requires org.apache.logging.log4j.core;

	opens dev.tim9h.rcp.core.ui;
	opens dev.tim9h.rcp.core.service;
	opens dev.tim9h.rcp.core.util;
	opens dev.tim9h.rcp.core.settings;
	opens dev.tim9h.rcp.core.plugin;

	uses CCardFactory;
}