package dev.tim9h.rcp.controls.utils;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Debounces a task: every call to {@link #runDelayed(Runnable)} restarts the
 * delay and replaces the pending task, so the task only runs once, after
 * {@code maxDelay * 10} milliseconds without further calls. The task runs on a
 * daemon thread owned by this instance that is released when idle.
 */
public class DelayedRunner {

	private static final int TICK_MILLIS = 10;

	private ScheduledThreadPoolExecutor executor;

	private ScheduledFuture<?> pending;

	private volatile int maxDelay = 1;

	public DelayedRunner() {
		//
	}

	public DelayedRunner(int delay) {
		this.maxDelay = delay;
	}

	public void setMaxDelay(int maxDelay) {
		this.maxDelay = maxDelay;
	}

	public synchronized void runDelayed(Runnable run) {
		cancel();
		if (executor == null) {
			executor = new ScheduledThreadPoolExecutor(1, r -> {
				var thread = new Thread(r, "delayTimer");
				thread.setDaemon(true);
				return thread;
			});
			executor.setKeepAliveTime(5, TimeUnit.SECONDS);
			executor.allowCoreThreadTimeOut(true);
			executor.setRemoveOnCancelPolicy(true);
		}
		pending = executor.schedule(run, (long) Math.max(1, maxDelay) * TICK_MILLIS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Cancels the pending task, if it has not started yet.
	 */
	public synchronized void cancel() {
		if (pending != null) {
			pending.cancel(false);
			pending = null;
		}
	}

}
