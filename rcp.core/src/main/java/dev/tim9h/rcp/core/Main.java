package dev.tim9h.rcp.core;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.tim9h.rcp.core.ui.UiApplication;
import dev.tim9h.rcp.util.RcpDirectories;

public class Main {

	public static void main(String[] args) throws Exception {
		var logPath = Path.of(System.getProperty("logpath", RcpDirectories.getApplicationDirectory().toString()));
		Files.createDirectories(logPath);
		System.setProperty("logpath", logPath.toString());
		UiApplication.main(args);
	}

}
