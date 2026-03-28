package dev.tim9h.rcp.${pluginName};

import org.apache.logging.log4j.Logger;
import com.google.inject.Inject;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.Plugin;

public class ${pluginNameHyphened}View implements Plugin {
	
	static final String SETTING_FOO = "${pluginName}.sample.setting";
	
	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;
	
	@Override
	public String getName() {
		return "${pluginName}";
	}
	
	@Override
	public Map<String, String> getSettingsContributions() {
		// return Map.of(SETTING_FOO, "bar");
		return Collections.emptyMap();
	}

}
