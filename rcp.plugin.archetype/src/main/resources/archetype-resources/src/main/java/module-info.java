module rcp.${pluginName} {
	exports dev.tim9h.rcp.${pluginName};

	requires transitive rcp.api;
	requires com.google.guice;
	requires org.apache.logging.log4j;
	requires transitive javafx.controls;
}