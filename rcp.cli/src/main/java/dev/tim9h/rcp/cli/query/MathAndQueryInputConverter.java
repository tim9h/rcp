package dev.tim9h.rcp.cli.query;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;

import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.settings.Settings;
import javafx.scene.text.Text;

public class MathAndQueryInputConverter extends InputConverter {

	private DoubleEvaluator evaluator;

	@Inject
	private QueryService queryService;

	@Inject
	private Settings settings;

	@Inject
	public MathAndQueryInputConverter(Injector injector) {
		super(injector);
		evaluator = new DoubleEvaluator();
	}

	@Override
	protected Pair<List<Text>, List<Text>> process(String input) {
		if (input.startsWith(">")) {
			return null;
		}
		try {
			var result = evaluator.evaluate(input);
			return Pair.of(Arrays.asList(new Text("Calculator")),
					Arrays.asList(new Text(removeTailingZeroes(result.doubleValue()))));
		} catch (IllegalArgumentException e) {
			return processNonMath(input);
		}
	}

	private static String removeTailingZeroes(double d) {
		if (d == (long) d) {
			return String.format("%d", Long.valueOf((long) d));
		}
		return String.format("%s", Double.valueOf(d));
	}

	private Pair<List<Text>, List<Text>> processNonMath(String input) {
		var answer = queryService.query(input);
		var maxLength = settings.getCharWidth();
		if (answer != null && StringUtils.isNotBlank(answer.getAbstractText())) {
			var interpretation = WordUtils.capitalize(answer.getEntity());
			return Pair.of(Arrays.asList(new Text(interpretation)),
					Arrays.asList(new Text(StringUtils.abbreviate(answer.getAbstractText(), maxLength))));
		} else {
			var suggestions = queryService.getSuggestions(input);
			if (!suggestions.isEmpty()) {
				var suggestionsLabel = StringUtils.abbreviate(StringUtils.join(suggestions, ", "), maxLength);
				return Pair.of(Arrays.asList(new Text("Suggestions")), Arrays.asList(new Text(suggestionsLabel)));
			}
			return null;
		}
	}

}
