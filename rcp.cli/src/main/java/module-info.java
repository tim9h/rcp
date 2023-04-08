module rcp.cli {
	exports dev.tim9h.rcp.cli.query.bean;
	exports dev.tim9h.rcp.cli;
	exports dev.tim9h.rcp.cli.query;

	requires transitive rcp.api;
	requires com.google.gson;
	requires transitive com.google.guice;
	requires java.desktop;
	requires transitive javafx.base;
	requires transitive rcp.controls;
	requires javafx.controls;
	requires javafx.graphics;
	requires javaluator;
	requires org.apache.commons.lang3;
	requires org.apache.commons.text;
	requires org.apache.logging.log4j;
	requires java.base;
}