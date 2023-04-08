package dev.tim9h.rcp.controls.utils;

import java.util.Timer;
import java.util.TimerTask;

public class DelayedRunner {

	private Timer timer;

	private int delay;

	private int maxDelay = 1;

	private Runnable runnable;

	public DelayedRunner() {
		//
	}

	public void setMaxDelay(int maxDelay) {
		this.maxDelay = maxDelay;
	}

	public DelayedRunner(int delay) {
		this.maxDelay = delay;
	}

	public void runDelayed(Runnable run) {
		delay = maxDelay;
		runnable = run;
		if (timer == null) {
			timer = new Timer("delayTimer");
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					delay--;
					if (delay == 0) {
						runnable.run();
					}
				}
			}, 0, 10);
		}
	}

}
