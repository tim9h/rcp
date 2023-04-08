package dev.tim9h.rcp.core.service;

import java.util.Collection;

import dev.tim9h.rcp.spi.Mode;

public interface ModeService {

	void setModes(Collection<String> modes);

	void initMode(Mode mode);

	void initDefaultModes();

	public boolean isModeActive(String name);

}