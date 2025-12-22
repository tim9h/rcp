package dev.tim9h.rcp.spi;

import java.util.Collections;
import java.util.Map;

public interface PluginFactory {

	public String getId();

	public Plugin create();

	public default Map<String, String> getSettingsContributions() {
		return Collections.emptyMap();
	}

}
