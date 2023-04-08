package dev.tim9h.rcp.core.util;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

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

	private PopupMenu popupMenu;

	private TrayIcon trayIcon;

	private SystemTray tray;

	private int trayIconClicks;

	private Timer timer;

	@Inject
	private EventManager eventManager;

	@Inject
	private ModeService modeService;

	@Inject
	private Settings settings;

	@Inject
	public TrayManager(Injector injector) {
		injector.injectMembers(this);
		tray = SystemTray.getSystemTray();
		var image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon_small.png"));

		trayIcon = new TrayIcon(image, settings.getString(SettingsConsts.APPLICATION_TITLE));
		trayIcon.setImageAutoSize(true);

		popupMenu = new PopupMenu();
		trayIcon.setPopupMenu(popupMenu);

		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			logger.warn(() -> "Unable add tray icon to tray: " + e.getMessage());
		}

		timer = new Timer("trayDoubleclickListener");

		subscribeToEvents();
	}

	public void subscribeToEvents() {
		eventManager.post(new CcEvent("tray created"));
		eventManager.listen(CcEvent.EVENT_TOAST, args -> {
			if (args.length > 1) {
				showToast((String) args[0], (String) args[1]);
			}
		});
	}

	public void showToast(String caption, String text) {
		if (!modeService.isModeActive("dnd")) {
			trayIcon.displayMessage(caption, text, TrayIcon.MessageType.NONE);
		} else {
			logger.debug(() -> "Not showing toast due to DND mode");
		}
	}

	public void createMenuItem(String name, String label, Runnable action, boolean withSeparator) {
		createMenuItem(name, label, action);
		if (withSeparator) {
			popupMenu.insertSeparator(0);
		}
	}

	public void createMenuItem(String name, String label, Runnable action) {
		var menuItem = new MenuItem();
		menuItem.setName(name);
		menuItem.setLabel(label);
		menuItem.addActionListener(event -> action.run());
		popupMenu.insert(menuItem, 0);
	}

	public void createDoubleClickAction(Runnable action) {
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					trayIconClicks++;
					var task = new TimerTask() {
						@Override
						public void run() {
							trayIconClicks = 0;
						}
					};
					timer.schedule(task, 500l);
					if (trayIconClicks == 2) {
						action.run();
						trayIconClicks = 0;
					}
				}
			}
		});
	}

	public void removeTrayIcon() {
		tray.remove(trayIcon);
	}

}
