package org.atari.raster.rmt.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The script syntax (plans/JAVA_SCRIPTING_PLAN.md, 3.2): one command per
 * line; {@code #} starts a comment (at the line's start or before a
 * token); blank lines are ignored; tokens are separated by whitespace and
 * may be quoted with {@code "..."} (the quotes are removed, blanks kept;
 * {@code \"} and {@code \\} are the two escapes); an unquoted token of the
 * form {@code name=value} (the name a letter followed by letters, digits,
 * {@code -} or {@code _}) is an option, whose value may itself be quoted
 * ({@code text="A B"}); everything else is a positional argument. Command
 * and option names are case-insensitive; values keep their case.
 */
public final class ScriptParser {

	private ScriptParser() {
	}

	public static List<ScriptCommand> parse(List<String> lines) throws ScriptException {
		List<ScriptCommand> commands = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			ScriptCommand command = parseLine(i + 1, lines.get(i));
			if (command != null) {
				commands.add(command);
			}
		}
		return commands;
	}

	/** One line into a command, or {@code null} for a blank/comment line. */
	static ScriptCommand parseLine(int lineNumber, String line) throws ScriptException {
		List<Token> tokens = tokenize(lineNumber, line);
		if (tokens.isEmpty()) {
			return null;
		}
		Token first = tokens.get(0);
		if (first.quoted || first.optionName != null) {
			throw new ScriptException(lineNumber, "A command name is expected at the start of the line.");
		}
		String name = first.text.toLowerCase(Locale.ROOT);
		List<String> arguments = new ArrayList<>();
		Map<String, String> options = new LinkedHashMap<>();
		for (Token token : tokens.subList(1, tokens.size())) {
			if (token.optionName != null) {
				if (options.containsKey(token.optionName)) {
					throw new ScriptException(lineNumber, "The option '" + token.optionName + "' is given twice.");
				}
				options.put(token.optionName, token.text);
			} else {
				arguments.add(token.text);
			}
		}
		return new ScriptCommand(lineNumber, name, List.copyOf(arguments), Map.copyOf(options));
	}

	private static final class Token {
		final String text;
		final boolean quoted;
		final String optionName;

		Token(String text, boolean quoted, String optionName) {
			this.text = text;
			this.quoted = quoted;
			this.optionName = optionName;
		}
	}

	private static List<Token> tokenize(int lineNumber, String line) throws ScriptException {
		List<Token> tokens = new ArrayList<>();
		int i = 0;
		int n = line.length();
		while (i < n) {
			char c = line.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (c == '#') {
				break; // comment to the end of the line
			}
			StringBuilder text = new StringBuilder();
			boolean startsQuoted = c == '"';
			String optionName = null;
			while (i < n && !Character.isWhitespace(line.charAt(i))) {
				c = line.charAt(i);
				if (c == '"') {
					i++;
					boolean closed = false;
					while (i < n) {
						char q = line.charAt(i);
						if (q == '\\' && i + 1 < n && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\')) {
							text.append(line.charAt(i + 1));
							i += 2;
							continue;
						}
						if (q == '"') {
							closed = true;
							i++;
							break;
						}
						text.append(q);
						i++;
					}
					if (!closed) {
						throw new ScriptException(lineNumber, "A closing quote is missing.");
					}
					continue;
				}
				if (c == '=' && optionName == null && !startsQuoted && isOptionName(text)) {
					optionName = text.toString().toLowerCase(Locale.ROOT);
					text.setLength(0);
					i++;
					continue;
				}
				text.append(c);
				i++;
			}
			tokens.add(new Token(text.toString(), startsQuoted, optionName));
		}
		return tokens;
	}

	private static boolean isOptionName(CharSequence s) {
		if (s.length() == 0 || !Character.isLetter(s.charAt(0))) {
			return false;
		}
		for (int i = 1; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
				return false;
			}
		}
		return true;
	}
}
