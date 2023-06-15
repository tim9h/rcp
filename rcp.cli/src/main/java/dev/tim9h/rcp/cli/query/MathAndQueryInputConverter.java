package dev.tim9h.rcp.cli.query;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.cli.query.bean.InputResponse;
import dev.tim9h.rcp.settings.Settings;
import javafx.scene.text.Text;

public class MathAndQueryInputConverter extends InputConverter {

	private DoubleEvaluator evaluator;

	@Inject
	private QueryService queryService;

	@Inject
	private Settings settings;

	private DecimalFormat formatter;

	@Inject
	public MathAndQueryInputConverter(Injector injector) {
		super(injector);
		evaluator = new DoubleEvaluator();
		formatter = (DecimalFormat) NumberFormat.getInstance();
		var symbols = formatter.getDecimalFormatSymbols();
		symbols.setGroupingSeparator(' ');
		formatter.setDecimalFormatSymbols(symbols);
	}

	@Override
	protected InputResponse process(String input) {
		if (input.startsWith(">")) {
			return null;
		}
		try {
			var result = evaluator.evaluate(input);
			return new InputResponse(Arrays.asList(new Text("Calculator")),
					Arrays.asList(new Text(removeTailingZeroes(result.doubleValue()))),
					new Text(formatNumber(result.doubleValue())));
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

	private String formatNumber(double d) {
		if (d == (long) d) {
			return formatter.format(d);
		}
		return String.format("%s", Double.valueOf(d));
	}

	private InputResponse processNonMath(String input) {
		var answer = queryService.query(input);
		var maxLength = settings.getCharWidth();
		if (answer != null && StringUtils.isNotBlank(answer.getAbstractText())) {
			var interpretation = WordUtils.capitalize(answer.getEntity());
			return new InputResponse(Arrays.asList(new Text(interpretation)),
					Arrays.asList(new Text(StringUtils.abbreviate(answer.getAbstractText(), maxLength))));
		} else {
			var suggestions = queryService.getSuggestions(input);
			if (!suggestions.isEmpty()) {
				var suggestionsLabel = StringUtils.abbreviate(StringUtils.join(suggestions, ", "), maxLength);
				return new InputResponse(Arrays.asList(new Text("Suggestions")),
						Arrays.asList(new Text(suggestionsLabel)));
			}
			return new InputResponse(Arrays.asList(new Text(StringUtils.EMPTY)),
					Arrays.asList(new Text(StringUtils.EMPTY)));
		}
	}

}
