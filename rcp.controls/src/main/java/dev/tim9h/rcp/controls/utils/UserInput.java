package dev.tim9h.rcp.controls.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import dev.tim9h.rcp.controls.CcTextField;

public class UserInput {

	private List<String> preceeding;

	private String query;

	private String rest;

	public UserInput(String userinput, int carret) {
		var inputWithoutPrefix = userinput.substring(2);
		var pos = carret - 2;
		var start = inputWithoutPrefix.substring(0, pos);
		var split = start.split(StringUtils.SPACE);

		if (!start.isBlank() && start.charAt(pos - 1) != ' ') {
			preceeding = Arrays.asList(Arrays.copyOf(split, split.length - 1));
			query = split[split.length - 1];
		} else {
			preceeding = Arrays.asList(split);
			query = StringUtils.EMPTY;
		}
		rest = inputWithoutPrefix.substring(pos, userinput.length() - 2);
	}

	public List<String> getPreceeding() {
		return preceeding;
	}

	public String getQuery() {
		return query;
	}

	public String getRest() {
		return rest;
	}

	@Override
	public String toString() {
		return CcTextField.COMMAND_PREFIX_SPACED + preceeding.stream().collect(Collectors.joining(StringUtils.SPACE))
				+ query + rest;
	}

	@Override
	public int hashCode() {
		return Objects.hash(preceeding, query, rest);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		var other = (UserInput) obj;
		return Objects.equals(preceeding, other.preceeding) && Objects.equals(query, other.query)
				&& Objects.equals(rest, other.rest);
	}

}
