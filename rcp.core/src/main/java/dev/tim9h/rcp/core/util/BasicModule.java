package dev.tim9h.rcp.core.util;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

import dev.tim9h.rcp.core.event.DefaultEventManager;
import dev.tim9h.rcp.core.logging.Log4jTypeListener;
import dev.tim9h.rcp.core.service.ModeServiceImpl;
import dev.tim9h.rcp.core.service.ModeService;
import dev.tim9h.rcp.core.settings.SettingsImpl;
import dev.tim9h.rcp.core.ui.InjectableScene;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.settings.Settings;
import javafx.scene.Scene;

public class BasicModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(EventManager.class).to(DefaultEventManager.class);
		bind(Settings.class).to(SettingsImpl.class);
		bind(Scene.class).to(InjectableScene.class);
		bind(ModeService.class).to(ModeServiceImpl.class);

		bindListener(Matchers.any(), new Log4jTypeListener());
	}

}
