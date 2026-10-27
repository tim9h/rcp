package dev.tim9h.rcp.core.windows;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.Logger;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WINDOWINFO;

import dev.tim9h.rcp.logging.InjectLogger;
import javafx.application.Platform;

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

	public long getForegroundWindowHandle() {
		if (!isWindows()) {
			return 0;
		}
		try {
			return NativeFocus.getForegroundWindowHandle();
		} catch (RuntimeException | LinkageError e) {
			logger.error("Unable to get the foreground window using FFM", e);
			return 0;
		}
	}

	public boolean restoreForegroundWindow(long windowHandle) {
		if (!isWindows() || windowHandle == 0) {
			return false;
		}
		try {
			return NativeFocus.restoreForegroundWindow(windowHandle);
		} catch (RuntimeException | LinkageError e) {
			logger.error("Unable to restore the foreground window using FFM", e);
			return false;
		}
	}

	private static final class NativeFocus {

		private static final MethodHandle GET_FOREGROUND_WINDOW;
		private static final MethodHandle IS_WINDOW;
		private static final MethodHandle IS_WINDOW_VISIBLE;
		private static final MethodHandle SET_FOREGROUND_WINDOW;

		static {
			var linker = Linker.nativeLinker();
			var user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
			GET_FOREGROUND_WINDOW = downcall(linker, user32, "GetForegroundWindow",
					FunctionDescriptor.of(ValueLayout.ADDRESS));
			IS_WINDOW = downcall(linker, user32, "IsWindow",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			IS_WINDOW_VISIBLE = downcall(linker, user32, "IsWindowVisible",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			SET_FOREGROUND_WINDOW = downcall(linker, user32, "SetForegroundWindow",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		}

		private static long getForegroundWindowHandle() {
			return ((MemorySegment) invoke(GET_FOREGROUND_WINDOW)).address();
		}

		private static boolean restoreForegroundWindow(long windowHandle) {
			var handle = MemorySegment.ofAddress(windowHandle);
			if ((int) invoke(IS_WINDOW, handle) == 0 || (int) invoke(IS_WINDOW_VISIBLE, handle) == 0) {
				return false;
			}
			return (int) invoke(SET_FOREGROUND_WINDOW, handle) != 0;
		}

		private static MethodHandle downcall(Linker linker, SymbolLookup lookup, String name,
				FunctionDescriptor descriptor) {
			var symbol = lookup.find(name).orElseThrow(() -> new IllegalStateException("Missing Windows API: " + name));
			return linker.downcallHandle(symbol, descriptor);
		}

		private static Object invoke(MethodHandle handle, Object... args) {
			try {
				return handle.invokeWithArguments(args);
			} catch (Throwable e) {
				throw new IllegalStateException("Windows focus API call failed", e);
			}
		}
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
