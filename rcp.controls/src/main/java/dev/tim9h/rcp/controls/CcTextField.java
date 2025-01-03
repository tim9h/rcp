package dev.tim9h.rcp.controls;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.collections.LimitedIterableStack;
import dev.tim9h.rcp.controls.utils.UserInput;
import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.TreeNode;
import javafx.event.Event;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class CcTextField extends TextField {

	private static final String HISTORY = "history";

	private static final String COMMAND_PREFIX = ">";

	public static final String COMMAND_PREFIX_SPACED = COMMAND_PREFIX + StringUtils.SPACE;

	private Consumer<String> submitAction;

	private IndexRange selection;

	private String selectedText;

	private TreeNode<String> commands;

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;
	
	private LimitedIterableStack<String> inputHistory;

	@Inject
	public CcTextField(Injector injector) {
		injector.injectMembers(this);
		setContextMenu(null);
		getStyleClass().add("cli-input");
		setOnKeyPressed(this::handleKeyPressed);
		setOnKeyReleased(this::handleKeyReleased);
		setOnKeyTyped(this::handleKeyTyped);
		disableContextMenu();
		saveSelection();
		initCommandMode();
		disableTabNavigation();
		initHistory();
		eventManager.listen("clear", _ -> inputHistory.clear());
	}

	private void initCommandMode() {
		textProperty().addListener((_, oldval, newval) -> {
			if (!oldval.startsWith(COMMAND_PREFIX) && newval.startsWith(COMMAND_PREFIX)) {
				getStyleClass().add("highlight");
				textProperty().setValue(COMMAND_PREFIX_SPACED);
			} else if (oldval.startsWith(COMMAND_PREFIX) && !newval.startsWith(COMMAND_PREFIX)) {
				getStyleClass().remove("highlight");
			}
		});
	}

	private void saveSelection() {
		selectionProperty()
				.addListener((_, _, newval) -> selection = newval.getStart() != newval.getEnd() ? newval : null);
	}

	private void handleKeyTyped(KeyEvent event) {
		if (event.getCharacter().equals("(")) {
			if (selection == null && StringUtils.isBlank(getSelectedText())) {
				var caretPosition = getCaretPosition();
				textProperty()
						.setValue(new StringBuilder(textProperty().getValue()).insert(caretPosition, ")").toString());
				positionCaret(caretPosition);
			}
		} else if (event.getCharacter().equals(")")) {
			var caretPosition = getCaretPosition();
			if (textProperty().getValue().length() > caretPosition
					&& textProperty().getValue().charAt(caretPosition) == ')') {
				textProperty()
						.setValue(new StringBuilder(textProperty().getValue()).deleteCharAt(caretPosition).toString());
				positionCaret(caretPosition);
			}
		}
	}

	private void disableContextMenu() {
		addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, Event::consume);
	}

	private void handleKeyPressed(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
			eventManager.post(new CcEvent(CcEvent.EVENT_CLI_RESPONSE_COPY));
		} else if (event.getCode() == KeyCode.ENTER && submitAction != null) {
			logger.debug(() -> "submitting");
			inputHistory.push(textProperty().getValue());
			submitAction.accept(textProperty().getValue());
		} else if (event.getCode() == KeyCode.DELETE && event.isShiftDown()) {
			clear();
		} else if (event.getCode() == KeyCode.BACK_SPACE) {
			handleBackspace();
		} else if (selection != null && event.getCode() == KeyCode.DIGIT8 && event.isShiftDown()) {
			selectedText = textProperty().getValue().substring(selection.getStart(), selection.getEnd());
		} else if (event.getCode() == KeyCode.P && event.isShiftDown() && event.isControlDown()) {
			handleCtrlShiftP();
		} else if (event.getCode() == KeyCode.UP) {
			historyPrevious();
		} else if (event.getCode() == KeyCode.DOWN) {
			historyNext();
		}
		if (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN) {
			inputHistory.resetCursor();
            getStyleClass().remove(HISTORY);
		}
	}

	private void historyPrevious() {
		if (!getStyleClass().contains(HISTORY)) {
			getStyleClass().add(HISTORY);
		}
		var prev = inputHistory.previous();
		textProperty().set(StringUtils.defaultString(prev));
		positionCaret(getText().length());
	}
	
	private void historyNext() {
		if (!getStyleClass().contains(HISTORY)) {
			getStyleClass().add(HISTORY);
		}
		var next = inputHistory.next();
		textProperty().set(StringUtils.defaultString(next));
		positionCaret(getText().length());
	}

	private void handleCtrlShiftP() {
		if (textProperty().getValue().startsWith(COMMAND_PREFIX)) {
			textProperty().set(StringUtils.EMPTY);
		} else {
			setText(COMMAND_PREFIX_SPACED);
			positionCaret(getText().length());
		}
	}

	private void handleBackspace() {
		var caretPosition = getCaretPosition();
		if (textProperty().getValue().length() > caretPosition
				&& textProperty().getValue().charAt(caretPosition) == ')') {
			textProperty()
					.setValue(new StringBuilder(textProperty().getValue()).deleteCharAt(caretPosition).toString());
			positionCaret(caretPosition);
		} else if (textProperty().getValue().equals(COMMAND_PREFIX)) {
			textProperty().setValue(StringUtils.EMPTY);
		}
	}

	private void handleTab() {
		var input = textProperty().getValue();
		if (input.startsWith(COMMAND_PREFIX_SPACED)) {
			var userinput = new UserInput(input, getCaretPosition());
			var suggestions = getSuggestions(userinput);
			logger.debug(() -> "Suggestions for " + userinput.getQuery() + ": " + suggestions);
			if (suggestions.size() == 1) {
				var preceeding = userinput.getPreceeding().stream().collect(Collectors.joining(StringUtils.SPACE));
				preceeding = preceeding.isBlank() ? StringUtils.EMPTY : preceeding + StringUtils.SPACE;
				textProperty().set(COMMAND_PREFIX_SPACED + preceeding + suggestions.get(0) + userinput.getRest());
				positionCaret((COMMAND_PREFIX_SPACED + preceeding + suggestions.get(0)).length());
				eventManager.clear();
			} else {
				completeAmbiguous(userinput, suggestions);
			}
		}
	}

	private void completeAmbiguous(UserInput userinput, List<String> suggestions) {
		if (!suggestions.isEmpty()) {
			eventManager.echo(StringUtils.abbreviate(suggestions.stream().collect(Collectors.joining(", ")),
					settings.getCharWidth()));
			var preceeding = userinput.getPreceeding().stream().collect(Collectors.joining(StringUtils.SPACE));
			preceeding = preceeding.isBlank() ? StringUtils.EMPTY : preceeding + StringUtils.SPACE;
			var commonPrefix = StringUtils.getCommonPrefix(suggestions.toArray(new String[0]));
			textProperty().set(COMMAND_PREFIX_SPACED + preceeding + commonPrefix + userinput.getRest());
			positionCaret((COMMAND_PREFIX_SPACED + preceeding + commonPrefix).length());
		} else {
			eventManager.clear();
		}
	}

	private List<String> getSuggestions(UserInput input) {
		var node = getCommands();
		for (var command : input.getPreceeding()) {
			if (!command.isBlank()) {
				node = node.get(command);
				if (node == null) {
					break;
				}
			}
		}
		return node != null
				? node.stream().map(TreeNode<String>::get).filter(n -> n.startsWith(input.getQuery())).toList()
				: Collections.emptyList();
	}

	private void handleKeyReleased(KeyEvent event) {
		if (selectedText != null) {
			var caretPosition = getCaretPosition();
			textProperty().setValue(
					new StringBuilder(textProperty().getValue()).insert(caretPosition, selectedText).toString());
			selectRange(caretPosition - 1, caretPosition + selectedText.length() + 1);
			selectedText = null;
		}
		if (event.getCode().equals(KeyCode.TAB)) {
			handleTab();
		}
	}

	public void setOnSubmit(Consumer<String> consumer) {
		this.submitAction = consumer;
	}

	public void disableTabNavigation() {
		addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode() == KeyCode.TAB) {
				event.consume();
			}
		});
	}

	public TreeNode<String> getCommands() {
		if (commands == null) {
			commands = new TreeNode<>(StringUtils.EMPTY);
		}
		return commands;
	}

	public void addCommand(TreeNode<String> command) {
		getCommands().add(command);
	}
	
	private void initHistory() {
		var size = settings.getInt("cli.history.size");
		if (size == null) {
			settings.addSetting("cli.history.size", "5");
			size = 5;
		}
		inputHistory = new LimitedIterableStack<>(size);
	}

}
