package dev.tim9h.rcp.core.service;

import dev.tim9h.rcp.spi.TreeNode;

public interface ThemeService {

	void subscribeToThemeEvents();

	void setTheme(String theme, boolean persist);

	TreeNode<String> getThemeCommands();

	void createThemeMenu();

}