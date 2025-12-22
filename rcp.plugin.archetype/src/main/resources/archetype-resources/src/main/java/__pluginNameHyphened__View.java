package dev.tim9h.rcp.${pluginName};

import org.apache.logging.log4j.Logger;
import com.google.inject.Inject;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.Plugin;

public class ${pluginNameHyphened}View implements Plugin {
	
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

}
