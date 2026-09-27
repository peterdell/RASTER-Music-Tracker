package org.atari.raster.rmt.script;

import java.util.List;
import java.util.Map;

/**
 * One parsed script line: the command name (lower case), its positional
 * arguments and its {@code name=value} options (names lower case, in the
 * order written). {@code line} is the 1-based line number for messages.
 */
public record ScriptCommand(int line, String name, List<String> arguments, Map<String, String> options) {

	/** The argument at {@code index}, or {@code null}. */
	public String argument(int index) {
		return index < arguments.size() ? arguments.get(index) : null;
	}

	/** The option's value, or {@code defaultValue} when the script did not give it. */
	public String option(String name, String defaultValue) {
		return options.getOrDefault(name, defaultValue);
	}
}
