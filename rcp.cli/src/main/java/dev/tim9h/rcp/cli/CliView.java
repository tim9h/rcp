package dev.tim9h.rcp.cli;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.cli.query.InputConverter;
import dev.tim9h.rcp.controls.AnimatedLabel;
import dev.tim9h.rcp.controls.CcTextField;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Gravity;
import dev.tim9h.rcp.spi.Position;
import dev.tim9h.rcp.spi.TreeNode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class CliView implements CCard {

	@InjectLogger
	private Logger logger;

	@Inject
	private InputConverter ic;

	@Inject
	private AnimatedLabel lblInterpretation;

	@Inject
	private AnimatedLabel lblResponse;

	@Inject
	private CcTextField tfiInput;

	@Inject
	private Settings settings;

	@Inject
	private EventManager eventManager;

	@Override
	public String getName() {
		return "Console";
	}

	@Override
	public void initBus(EventManager em) {
		CCard.super.initBus(em);
		em.listen(CcEvent.EVENT_CLI_RESPONSE, args -> Platform.runLater(() -> showDetailedResponse(args)));
		em.listen(CcEvent.EVENT_CLI_REQUEST_FOCUS, _ -> tfiInput.requestFocus());
		em.listen(CcEvent.EVENT_CLI_ADD_PROPOSALS, data -> {
			if (data instanceof String[] sData) {
				Arrays.stream(sData).map(TreeNode::new).forEach(tfiInput::addCommand);
			} else {
				Arrays.stream(data).map(TreeNode.class::cast).forEach(tfiInput::addCommand);
			}
		});
		em.listen(CcEvent.EVENT_CLI_RESPONSE_COPY, _ -> copyResponseToClipboard());
	}

	private void copyResponseToClipboard() {
		var text = ic.getResponse();
		if (StringUtils.isNotBlank(text)) {
			lblInterpretation.setText("Copied");
			logger.debug(() -> "Updating clipboard with content: " + text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
	}

	private void showDetailedResponse(Object[] args) {
		if (args != null && args.length >= 2) {
			if (args[0] instanceof String details && args[1] instanceof String response) {
				var decaytime = AnimatedLabel.decaytime(details + response);
				lblInterpretation.showFadingText(decaytime, details);
				lblResponse.showFadingText(decaytime, response);

			} else if (args[0] instanceof List<?> details && args[1] instanceof List<?> response) {
				var detailsText = details.stream().map(Text.class::cast).map(text -> text.textProperty().get())
						.collect(Collectors.joining());
				var responseText = response.stream().map(Text.class::cast).map(text -> text.textProperty().get())
						.collect(Collectors.joining());
				var decaytime = AnimatedLabel.decaytime(detailsText + responseText);
				lblInterpretation.showFadingText(decaytime, details.toArray(new Text[0]));
				lblResponse.showFadingText(decaytime, response.toArray(new Text[0]));
			}
		}
	}

	@Override
	public Optional<Node> getNode() throws IOException {
		var inputGrid = new GridPane();
		ic.bind(tfiInput.textProperty(), lblInterpretation, lblResponse);
		inputGrid.add(tfiInput, 1, 0);
		GridPane.setHgrow(inputGrid, Priority.ALWAYS);
		var colSide = new ColumnConstraints();
		colSide.setPercentWidth(22);
		var colCenter = new ColumnConstraints();
		colCenter.setPercentWidth(60);
		inputGrid.getColumnConstraints().addAll(colSide, colCenter, colSide);
		lblInterpretation.getStyleClass().add("interpretation-label");
		calculateWidth();
		lblResponse.getStyleClass().add("response-label");
		tfiInput.setOnSubmit(value -> {
			CliView.this.submitInput(value);
			tfiInput.clear();
			tfiInput.requestFocus();
		});
		var outerPane = new VBox(lblInterpretation, lblResponse, inputGrid);
		outerPane.getStyleClass().add("plugin-cli");
		return Optional.of(outerPane);
	}
	
	private void calculateWidth() {
		var width = settings.getDouble("core.ui.stage.width").doubleValue() - 25;
		lblInterpretation.setClipWidth(width);
		lblResponse.setClipWidth(width);
	}

	@Override
	public Optional<String> getStylesheet() {
		return Optional.of(getClass().getResource("/css/cli.css").toExternalForm());
	}

	@Override
	public Gravity getGravity() {
		return new Gravity(Position.BOTTOM);
	}

	@Override
	public void onHidden() {
		lblInterpretation.setText(StringUtils.EMPTY);
		lblResponse.setText(StringUtils.EMPTY);
		tfiInput.setText(StringUtils.EMPTY);
	}

	@Override
	public void onShown() {
		tfiInput.requestFocus();
	}

	private void submitInput(String query) {
		if (query.startsWith(">")) {
			var split = query.substring(1).trim().split(" ");
			if (split.length == 1) {
				eventManager.post(new CcEvent(split[0]));
			} else {
				Object[] payload = Arrays.copyOfRange(split, 1, split.length);
				eventManager.post(new CcEvent(split[0], payload));
			}
		} else {
			openWebSearch(query);
		}
	}

	private void openWebSearch(String query) {
		if (StringUtils.isNotBlank(query) && Desktop.isDesktopSupported()
				&& Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			try {
				var url = String.format(settings.getString(CliViewFactory.SETTING_SEARCH_URL),
						URLEncoder.encode(query, StandardCharsets.UTF_8));
				Desktop.getDesktop().browse(new URI(url));
			} catch (IOException | URISyntaxException e) {
				logger.warn(() -> "Unable to open browser", e);
			}
		}

	}
	
	@Override
	public void onSettingsChanged() {
		calculateWidth();
	}

}
