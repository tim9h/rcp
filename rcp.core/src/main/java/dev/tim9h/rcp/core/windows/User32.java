package dev.tim9h.rcp.core.windows;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

public interface User32 extends StdCallLibrary {

	User32 INSTANCE = Native.load("user32", User32.class);

	public static class LASTINPUTINFO extends Structure {

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("cbSize", "dwTime");
		}
	}

	public boolean getLastInputInfo(LASTINPUTINFO result);

}