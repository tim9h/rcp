package dev.tim9h.rcp.${pluginName};

import java.util.Map;
import java.util.Collections;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.Plugin;
import dev.tim9h.rcp.spi.PluginFactory;

public class ${pluginNameHyphened}ViewFactory implements PluginFactory  {

	static final String SETTING_FOO = "${pluginName}.sample.setting";

	@Inject 
	private ${pluginNameHyphened}View view;
	
	@Override
	public String getId() {
		return "${pluginName}";
	}

	@Override
	public Plugin create() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		// return Map.of(SETTING_FOO, "bar");
		return Collections.emptyMap();
	}

}
