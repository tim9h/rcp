package dev.tim9h.rcp.cli.query;

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
import dev.tim9h.rcp.controls.utils.DelayedRunner;
import dev.tim9h.rcp.event.Event;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.settings.Settings;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.text.Text;

@ImplementedBy(MathAndQueryInputConverter.class)
public abstract class InputConverter extends DelayedRunner {

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private final AtomicLong generation = new AtomicLong();

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
		setMaxDelay(settings.getInt(CliView.SETTING_QUERYDELAY).intValue());
	}

	public void bind(StringProperty inputProperty, AnimatedLabel interpretation, AnimatedLabel response) {
		inputProperty.addListener((ChangeListener<String>) (_, oldValue, newValue) -> schedule(
				() -> onInputChanged(interpretation, response, oldValue, newValue)));
	}

	private void schedule(Runnable task) {
		generation.incrementAndGet();
		runDelayed(task);
	}

	/**
	 * Cancels a pending conversion and invalidates in-flight ones, so stale
	 * results are not shown.
	 */
	@Override
	public void cancel() {
		super.cancel();
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
