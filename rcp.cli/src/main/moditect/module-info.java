module rcp.cli {
    exports dev.tim9h.rcp.cli.query.bean;
    exports dev.tim9h.rcp.cli;
    exports dev.tim9h.rcp.cli.query;

    provides dev.tim9h.rcp.spi.Plugin
        with dev.tim9h.rcp.cli.CliView;

    requires transitive rcp.api;
    requires transitive com.google.guice;
    requires java.desktop;
    requires transitive javafx.base;
    requires transitive rcp.controls;
    requires javafx.controls;
    requires javafx.graphics;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;

    opens dev.tim9h.rcp.cli to com.google.guice;
}