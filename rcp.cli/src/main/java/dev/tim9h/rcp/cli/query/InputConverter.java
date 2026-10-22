package dev.tim9h.rcp.cli.query;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.cli.CliView;
import dev.tim9h.rcp.cli.query.bean.InputResponse;
import dev.tim9h.rcp.controls.AnimatedLabel;
import dev.tim9h.rcp.controls.CcTextField;
import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.settings.Settings;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.text.Text;

@ImplementedBy(MathAndQueryInputConverter.class)
public abstract class InputConverter {

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		var thread = new Thread(r, "cli-input-converter");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicLong generation = new AtomicLong();

	private volatile int delayMillis = 50;

	private ScheduledFuture<?> pending;

	private volatile String formattedResponse;

	@Inject
	protected InputConverter(Injector injector) {
		injector.injectMembers(this);
		initSettings();
		initBus();
	}

	private void initBus() {
		eventManager.listen(Event.EVENT_SETTINGS_CHANGED, _ -> initSettings());
	}

	private void initSettings() {
		delayMillis = Math.max(0, settings.getInt(CliView.SETTING_QUERYDELAY).intValue());
	}

	public void bind(StringProperty inputProperty, AnimatedLabel interpretation, AnimatedLabel response) {
		inputProperty.addListener((ChangeListener<String>) (_, oldValue, newValue) -> schedule(
				() -> onInputChanged(interpretation, response, oldValue, newValue)));
	}

	private synchronized void schedule(Runnable task) {
		if (pending != null) {
			pending.cancel(false);
		}
		generation.incrementAndGet();
		pending = EXECUTOR.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Cancels a pending conversion and invalidates in-flight ones, so stale
	 * results are not shown.
	 */
	public synchronized void cancel() {
		if (pending != null) {
			pending.cancel(false);
		}
		generation.incrementAndGet();
	}

	private void onInputChanged(AnimatedLabel interpretation, AnimatedLabel response, String oldValue,
			String newValue) {
		var current = generation.get();
		if (StringUtils.isNotBlank(newValue)) {
			var process = process(newValue);
			if (process != null && current == generation.get()) {
				formattedResponse = process.response().stream().map(Text::getText)
						.collect(Collectors.joining(StringUtils.EMPTY));
				Platform.runLater(() -> {
					if (current == generation.get()) {
						interpretation.showText(process.interpretation());
						response.showText(process.responseFormatted());
					}
				});
			}
		} else if (oldValue == null || !oldValue.startsWith(CcTextField.COMMAND_PREFIX)) {
			Platform.runLater(() -> {
				interpretation.hideText();
				response.hideText();
			});
		}
	}

	protected abstract InputResponse process(String input);

	public String getResponse() {
		return StringUtils.stripToEmpty(formattedResponse);
	}

}
