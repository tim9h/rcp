package dev.tim9h.rcp.core;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.tim9h.rcp.core.ui.UiApplication;

public class Main {

	public static void main(String[] args) throws Exception {
		var logPath = Path.of(System.getProperty("logpath", "C:\\temp"));
		Files.createDirectories(logPath);
		UiApplication.main(args);
	}

}
