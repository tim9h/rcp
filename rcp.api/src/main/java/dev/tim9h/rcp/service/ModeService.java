package dev.tim9h.rcp.service;

import java.util.Set;

import dev.tim9h.rcp.spi.Mode;

public interface ModeService {

	void initMode(Mode mode);

	void initDefaultModes();

	boolean isModeActive(String name);

	Set<String> getActiveModes();

	boolean enableMode(String mode, boolean ephemeral);

	boolean disableMode(String mode, boolean ephemeral);

}