package dev.tim9h.rcp.core.windows;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

public interface Kernel32 extends StdCallLibrary {

	Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

	public int GetTickCount();

}