package dev.tim9h.rcp.settings;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Settings {

	public void loadProperties();

	public void persistProperties();

	public void addSetting(String property, String value);

	public void addSetting(String property, List<String> values);

	public void addSettings(Map<String, String> settings);

	public File getSettingsFile();

	public Integer getInt(String property);

	public Double getDouble(String property);

	public Boolean getBoolean(String property);

	public String getString(String property);

	public Long getLong(String property);

	public List<String> getStringList(String property);

	public Set<String> getStringSet(String property);

	public void persist(String property, Object value);

	public void persist(String property, Collection<String> value);

	public void addOverwrites(List<String> settingsOverwrites);

	public Map<String, Object> getOverwrites();

	public int getCharWidth();
	
	public void openSettingsFile();
	
}
