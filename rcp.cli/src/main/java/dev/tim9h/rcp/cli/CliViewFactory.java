package dev.tim9h.rcp.cli;

import java.util.HashMap;
import java.util.Map;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;

public class CliViewFactory implements CCardFactory {

	public static final String SETTING_QUERYDELAY = "cli.query.delay";

	static final String SETTING_SEARCH_URL = "cli.url.search";

	static final String SETTING_SUGGEST_URL = "cli.url.suggest";

	@Inject
	private CliView cliView;

	@Override
	public String getId() {
		return "console";
	}

	@Override
	public CCard createCCard() {
		return cliView;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		var properties = new HashMap<String, String>();
		properties.put(SETTING_QUERYDELAY, "50");
		properties.put(SETTING_SEARCH_URL, "https://www.google.com/search?q=%s");
		properties.put(SETTING_SUGGEST_URL, "https://ac.duckduckgo.com/ac/?q=%s&type=json");
		return properties;
	}

}
