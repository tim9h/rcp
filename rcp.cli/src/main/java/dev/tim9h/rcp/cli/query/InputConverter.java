package dev.tim9h.rcp.cli.query;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.cli.CliViewFactory;
import dev.tim9h.rcp.cli.query.bean.InputResponse;
import dev.tim9h.rcp.controls.AnimatedLabel;
import dev.tim9h.rcp.controls.utils.DelayedRunner;
import dev.tim9h.rcp.event.CcEvent;
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

	private String formattedResponse;

	@Inject
	protected InputConverter(Injector injector) {
		injector.injectMembers(this);
		initSettings();
		initBus();
	}

	private void initBus() {
		eventManager.listen(CcEvent.EVENT_SETTINGS_CHANGED, data -> initSettings());
	}

	private void initSettings() {
		setMaxDelay(settings.getInt(CliViewFactory.SETTING_QUERYDELAY).intValue());
	}

	public void bind(StringProperty inputProperty, AnimatedLabel interpretation, AnimatedLabel response) {
		inputProperty.addListener((ChangeListener<String>) (observable, oldValue, newValue) -> CompletableFuture
				.runAsync(() -> runDelayed(() -> onInputChanged(interpretation, response, oldValue, newValue))));
	}

	private void onInputChanged(AnimatedLabel interpretation, AnimatedLabel response, String oldValue,
			String newValue) {
		if (StringUtils.isNotBlank(newValue)) {
			var process = process(newValue);
			if (process != null) {
				formattedResponse = process.response().stream().map(Text::getText)
						.collect(Collectors.joining(StringUtils.EMPTY));
				Platform.runLater(() -> {
					if (process != null) {
						interpretation.showText(process.interpretation());
						response.showText(process.responseFormatted());
					}
				});
			}
		} else if (!oldValue.startsWith(">")) {
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
