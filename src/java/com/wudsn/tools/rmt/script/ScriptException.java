package com.wudsn.tools.rmt.script;

/** A script error with the 1-based line it belongs to (0 when it concerns the file as a whole). */
public final class ScriptException extends Exception {

	private static final long serialVersionUID = 1L;

	private final int line;

	public ScriptException(int line, String message) {
		super(message);
		this.line = line;
	}

	public int getLine() {
		return line;
	}

	/** {@code "line 3: message"} or just the message for line 0. */
	public String getLocatedMessage() {
		return line > 0 ? "line " + line + ": " + getMessage() : getMessage();
	}
}
