package dev.tim9h.rcp.core.windows;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Singleton;

/**
 * Registers a Windows global hotkey and listens for it on a dedicated
 * message-loop thread.
 */
@Singleton
public final class WindowsHotkeyService implements AutoCloseable {

	private static final Logger logger = LogManager.getLogger(WindowsHotkeyService.class);

	private static final int HOTKEY_ID = 0x5243;
	private static final int WM_HOTKEY = 0x0312;
	private static final int WM_QUIT = 0x0012;
	private static final int PM_NOREMOVE = 0x0000;
	private static final int MSG_SIZE = 48;

	private static final MethodHandle REGISTER_HOTKEY;
	private static final MethodHandle UNREGISTER_HOTKEY;
	private static final MethodHandle GET_MESSAGE;
	private static final MethodHandle PEEK_MESSAGE;
	private static final MethodHandle POST_THREAD_MESSAGE;
	private static final MethodHandle GET_CURRENT_THREAD_ID;
	private static final MethodHandle GET_LAST_ERROR;

	private final Runnable callback;
	private final WindowsHotkey hotkey;
	private final CompletableFuture<Void> ready = new CompletableFuture<>();
	private final Thread thread;

	private volatile int nativeThreadId;
	private volatile boolean closed;

	static {
		var linker = Linker.nativeLinker();
		var library = SymbolLookup.libraryLookup("user32.dll", Arena.global());
		var kernel = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
		REGISTER_HOTKEY = downcall(linker, library, "RegisterHotKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		UNREGISTER_HOTKEY = downcall(linker, library, "UnregisterHotKey",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
		GET_MESSAGE = downcall(linker, library, "GetMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		PEEK_MESSAGE = downcall(linker, library, "PeekMessageW",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		POST_THREAD_MESSAGE = downcall(linker, library, "PostThreadMessageW",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		GET_CURRENT_THREAD_ID = downcall(linker, kernel, "GetCurrentThreadId",
				FunctionDescriptor.of(ValueLayout.JAVA_INT));
		GET_LAST_ERROR = downcall(linker, kernel, "GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT));
	}

	private WindowsHotkeyService(String hotkeySetting, Runnable callback) {
		this.hotkey = WindowsHotkey.parse(hotkeySetting);
		this.callback = callback;
		this.thread = new Thread(this::messageLoop, "global-hotkey");
		this.thread.setDaemon(true);
		this.thread.start();
		try {
			ready.join();
		} catch (CompletionException e) {
			throw new IllegalStateException("Unable to register global hotkey: " + hotkeySetting, e.getCause());
		}
	}

	public static WindowsHotkeyService start(String hotkeySetting, Runnable callback) {
		logger.info(() -> "Registering global hotkey: " + hotkeySetting);
		if (!WindowsUtils.isWindows()) {
			throw new UnsupportedOperationException("Global hotkeys are only supported on Windows");
		}
		if (ValueLayout.ADDRESS.byteSize() != Long.BYTES) {
			throw new UnsupportedOperationException("Global hotkeys require 64-bit Windows");
		}
		return new WindowsHotkeyService(hotkeySetting, callback);
	}

	private void messageLoop() {
		var registered = false;
		try (var arena = Arena.ofConfined()) {
			var message = arena.allocate(MSG_SIZE, Long.BYTES);
			nativeThreadId = invoke(GET_CURRENT_THREAD_ID);
			// RegisterHotKey with a null window handle requires a thread message queue.
			invoke(PEEK_MESSAGE, message, MemorySegment.NULL, 0, 0, PM_NOREMOVE);
			if (invoke(REGISTER_HOTKEY, MemorySegment.NULL, HOTKEY_ID, hotkey.modifiers(), hotkey.virtualKey()) == 0) {
				var error = invoke(GET_LAST_ERROR);
				throw new IllegalStateException("RegisterHotKey failed with Windows error " + error);
			}
			registered = true;

			// Ensure this thread has a message queue before start() returns.
			ready.complete(null);

			int result;
			while ((result = invoke(GET_MESSAGE, message, MemorySegment.NULL, 0, 0)) > 0) {
				if (message.get(ValueLayout.JAVA_INT, 8) == WM_HOTKEY
						&& message.get(ValueLayout.JAVA_LONG, 16) == HOTKEY_ID) {
					try {
						callback.run();
					} catch (RuntimeException e) {
						logger.error("Global hotkey callback failed", e);
					}
				}
			}
			if (result < 0 && !closed) {
				throw new IllegalStateException("GetMessageW failed");
			}
		} catch (Exception e) {
			ready.completeExceptionally(e);
			if (!closed) {
				logger.error("Global hotkey message loop failed", e);
			}
		} finally {
			if (registered && invoke(UNREGISTER_HOTKEY, MemorySegment.NULL, HOTKEY_ID) == 0 && !closed) {
				logger.warn("UnregisterHotKey failed with Windows error {}", invoke(GET_LAST_ERROR));
			}
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (nativeThreadId != 0
				&& invoke(POST_THREAD_MESSAGE, nativeThreadId, WM_QUIT, MemorySegment.NULL, MemorySegment.NULL) == 0) {
			logger.warn("Unable to post WM_QUIT to global hotkey thread (Windows error {})", invoke(GET_LAST_ERROR));
			thread.interrupt();
		}
		try {
			thread.join(5000);
			if (thread.isAlive()) {
				throw new IllegalStateException("Global hotkey thread did not stop");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while stopping global hotkey thread", e);
		}
	}

	private static MethodHandle downcall(Linker linker, SymbolLookup lookup, String name,
			FunctionDescriptor descriptor) {
		var symbol = lookup.find(name).orElseThrow(() -> new IllegalStateException("Missing Windows API: " + name));
		return linker.downcallHandle(symbol, descriptor);
	}

	private static int invoke(MethodHandle handle, Object... args) {
		try {
			return (int) handle.invokeWithArguments(args);
		} catch (Throwable e) {
			throw new IllegalStateException("Windows native call failed", e);
		}
	}
}
