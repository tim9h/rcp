package dev.tim9h.rcp.${pluginName};

import java.util.Map;
import java.util.Collections;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;

public class ${pluginNameHyphened}ViewFactory implements CCardFactory  {

	static final String SETTINGR_FOO = "${pluginName}.sample.setting";

	@Inject 
	private ${pluginNameHyphened}View view;
	
	@Override
	public String getId() {
		return "${pluginName}";
	}

	@Override
	public CCard createCCard() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		// return Map.of(SETTINGR_FOO, "bar");
		return Collections.emptyMap();
	}

}
