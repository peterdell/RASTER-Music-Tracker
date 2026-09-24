package com.wudsn.tools.rmt.model;

/** Ported from CStringUtility (src/cpp/StringUtility.h/.cpp) - fully. */
public final class StringUtility {

	private StringUtility() {
	}

	public static boolean endsWithNoCase(String string, String suffix) {
		String right = string.length() >= suffix.length() ? string.substring(string.length() - suffix.length()) : string;
		return right.equalsIgnoreCase(suffix);
	}
}
