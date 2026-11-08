package dev.tim9h.rcp.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

public final class SvgIconUtil {

	private static final Logger LOGGER = LogManager.getLogger(SvgIconUtil.class);

	private static final Pattern SVG_PATH = Pattern.compile("<path[^>]* d=\"([^\"]+)\"");

	private SvgIconUtil() {
		// Utility class
	}

	public static SVGPath loadPath(InputStream input, String resourcePath, double scale) throws IOException {
		if (input == null) {
			throw new IOException("Missing SVG resource: " + resourcePath);
		}
		try (input) {
			var svg = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			var matcher = SVG_PATH.matcher(svg);
			if (!matcher.find()) {
				throw new IOException("Invalid SVG resource: " + resourcePath);
			}
			var path = new SVGPath();
			path.setContent(matcher.group(1));
			path.setScaleX(scale);
			path.setScaleY(scale);
			return path;
		}
	}

	public static StackPane loadIcon(InputStream input, String resourcePath, Label colorSource, double size) {
		try {
			var icon = loadPath(input, resourcePath, size / 24);
			icon.fillProperty().bind(colorSource.textFillProperty());
			var graphic = new StackPane(icon);
			graphic.setMinSize(size, size);
			graphic.setPrefSize(size, size);
			graphic.setMaxSize(size, size);
			return graphic;
		} catch (IOException | RuntimeException e) {
			LOGGER.error(() -> "Unable to load SVG icon: " + resourcePath, e);
			return null;
		}
	}
}
