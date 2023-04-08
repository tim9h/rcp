package dev.tim9h.rcp.spi;

import java.util.Collections;
import java.util.Map;

public interface CCardFactory {

	public String getId();

	public CCard createCCard();

	public default Map<String, String> getSettingsContributions() {
		return Collections.emptyMap();
	}

}
