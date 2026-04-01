package dev.tim9h.rcp.core.util;

import java.awt.Toolkit;
import java.util.List;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import dev.tim9h.rcp.controls.tray.FxSystemTray;
import dev.tim9h.rcp.controls.tray.MenuItemData;
import dev.tim9h.rcp.core.service.ModeService;
import dev.tim9h.rcp.core.settings.SettingsConsts;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;

@Singleton
public class TrayManager {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private ModeService modeService;

	@Inject
	private Settings settings;

	private FxSystemTray systemTray;

	@Inject
	public TrayManager(Injector injector) {
		injector.injectMembers(this);

		var image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon_small.png"));
		var title = settings.getString(SettingsConsts.APPLICATION_TITLE);
		systemTray = new FxSystemTray(image, title);

		subscribeToEvents();
	}

	private void subscribeToEvents() {
		eventManager.post(new CcEvent("tray created"));
		eventManager.listen(CcEvent.EVENT_TOAST, args -> {
			if (args.length > 1) {
				showToast((String) args[0], (String) args[1]);
			}
		});
	}

	public void showToast(String caption, String text) {
		if (!modeService.isModeActive("dnd")) {
			systemTray.showToast(caption, text);
		} else {
			logger.debug(() -> "Not showing toast due to DND mode");
		}
	}

	public void createMenuItem(String label, Runnable action) {
		systemTray.createMenuItem(label, action);
	}

	public void createMenuItem(String label, Runnable action, boolean withSeparator) {
		systemTray.createMenuItem(label, action, withSeparator);
	}

	public void createDoubleClickAction(Runnable action) {
		systemTray.createDoubleClickAction(action);
	}

	public void createSubMenu(String label, List<MenuItemData> items) {
		systemTray.createSubMenu(label, items);
	}

	public void removeTrayIcon() {
		systemTray.removeTrayIcon();
	}

}