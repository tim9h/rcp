package dev.tim9h.rcp.core.windows;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.Logger;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WINDOWINFO;

import dev.tim9h.rcp.logging.InjectLogger;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;

public class WindowsUtils {

	@InjectLogger
	private Logger logger;

	private static final User32 user32 = User32.INSTANCE;

	private IdleState state = IdleState.UNKNOWN;

	private static final int AFK_MINUTES = 10;

	private static final int IDLE_SECONDS = 30;

	public static boolean isWindows() {
		return System.getProperty("os.name").startsWith("Windows");
	}

	public void setFocusToWindowsApp(String applicationTitle) {
		var hWnd = user32.FindWindow(null, applicationTitle);
		if (user32.IsWindowVisible(hWnd)) {
			user32.ShowWindow(hWnd, WinUser.SW_SHOWNORMAL);
			user32.SetForegroundWindow(hWnd);
			user32.SetFocus(hWnd);
		} else {
			logger.warn(() -> applicationTitle + " not found");
		}
	}

	public void listAllWindows() {
		user32.EnumWindows((hWnd, _) -> {
			var windowText = new char[512];
			user32.GetWindowText(hWnd, windowText, 512);
			var wText = Native.toString(windowText);
			var winfo = new WINDOWINFO();
			user32.GetWindowInfo(hWnd, winfo);
			if (!wText.isEmpty()) {
				logger.info(() -> wText);
			}
			return true;
		}, null);
	}

	public void displayCurrentFocus() {
		var activeWindow = user32.GetActiveWindow();
		var wText = new char[512];
		user32.GetWindowText(activeWindow, wText, 512);
		logger.info(() -> ("Active window is " + Native.toString(wText)));
	}

	public void focusPreviousWithTabSwitcher() {
		logger.debug(() -> "Simulating Alt+Tab");
		var robot = new Robot();
		robot.keyPress(KeyCode.ALT);
		robot.keyPress(KeyCode.TAB);
		robot.keyRelease(KeyCode.ALT);
		robot.keyRelease(KeyCode.TAB);
	}

	private static int getIdleTimeMillisWin32() {
		var lastInputInfo = new User32.LASTINPUTINFO();
		User32.INSTANCE.GetLastInputInfo(lastInputInfo);
		return Kernel32.INSTANCE.GetTickCount() - lastInputInfo.dwTime;
	}

	public void registerIdleStateListener(IdleChangeListener listener) {
		if (!isWindows()) {
			logger.error(() -> "Idle state only imlpmented on windows");
		}

		var task = new TimerTask() {

			@Override
			public void run() {
				var idleSec = getIdleTimeMillisWin32() / 1000;
				IdleState newState;
				if (idleSec < IDLE_SECONDS)
					newState = IdleState.ONLINE;
				else
					newState = idleSec > AFK_MINUTES * 60 ? IdleState.AWAY : IdleState.IDLE;

				if (newState != state) {
					state = newState;
					switch (state) {
					case ONLINE:
						Platform.runLater(listener::onOnline);
						break;
					case AWAY:
						Platform.runLater(listener::onAway);
						break;
					case UNKNOWN:
						logger.debug(() -> "Idle state: unknown");
						Platform.runLater(listener::onUknown);
						break;
					case IDLE:
						Platform.runLater(listener::onIdle);
						break;
					default:
						throw new IllegalArgumentException("Unexpected value: " + state);
					}
				}
			}
		};
		var timer = new Timer("idleWatcher");
		timer.scheduleAtFixedRate(task, 1000, 1000);
	}

}
