package dev.tim9h.rcp.service;

import java.util.List;

import dev.tim9h.rcp.spi.Theme;

public interface ThemeService {

	String setTheme(String theme, boolean persist);

	void registerThemes(List<Theme> themes);

	void createThemeMenu();

}