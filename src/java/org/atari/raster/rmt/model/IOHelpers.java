package org.atari.raster.rmt.model;

/**
 * Ported from IOHelpers.h/IOHelpers.cpp - the text and hex helpers the file
 * formats share: {@link Song}, {@link Instruments} and {@link Tracks} all
 * parse and write the TXT format with them, and the UI draws hex digits with
 * {@link #charH4}/{@link #charL4}. They lived on {@link Song} until plan 45
 * moved them here, giving them the home their C++ originals always had.
 */
public final class IOHelpers {

	private IOHelpers() {
	}

	private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

	/** IOHelpers.h's {@code CharH4}: the upper hex digit of a byte. Public because the UI's drawing code uses it exactly as C++'s does. */
	public static char charH4(int b) {
		return HEX_UPPER[(b >> 4) & 0xF];
	}

	/** IOHelpers.h's {@code CharL4}: the lower hex digit of a byte. */
	public static char charL4(int b) {
		return HEX_UPPER[b & 0xF];
	}

	/** Ported from IOHelpers.cpp's {@code Hexstr(char*, int)} - parses up to {@code len} leading uppercase-hex characters starting at {@code start}, returning -1 if there wasn't even one. Package-visible for reuse by {@link Instruments}/{@link Tracks}'s own TXT (de)serialization, matching {@link #nameToString}'s established widening. */
	public static int hexstr(String s, int start, int len) {
		int r = 0;
		int i = 0;
		for (; i < len && start + i < s.length(); i++) {
			char a = s.charAt(start + i);
			if (a >= '0' && a <= '9') {
				r = (r << 4) + (a - '0');
			} else if (a >= 'A' && a <= 'F') {
				r = (r << 4) + (a - 'A' + 10);
			} else {
				return i == 0 ? -1 : r;
			}
		}
		return i == 0 ? -1 : r;
	}

	/** Ported from IOHelpers.cpp's {@code Trimstr(char*)} - truncates at the first {@code \r} or {@code \n} found (a line already split on {@code \n} can still carry a trailing {@code \r}). */
	static String trimstr(String s) {
		int cr = s.indexOf('\r');
		if (cr >= 0) {
			return s.substring(0, cr);
		}
		int nl = s.indexOf('\n');
		return nl >= 0 ? s.substring(0, nl) : s;
	}

	/** One line read from a larger text, mirroring C++'s {@code istream::getline} - {@code content} excludes the {@code \n} delimiter; {@code nextPos} is the index right after it (or the text's end). */
	record Line(String content, int nextPos) {
	}

	static Line readLine(String text, int pos) {
		int nl = text.indexOf('\n', pos);
		if (nl < 0) {
			return new Line(text.substring(pos), text.length());
		}
		return new Line(text.substring(pos, nl), nl + 1);
	}

	/** Ported from IOHelpers.cpp's {@code NextSegment(istream&)} - returns the index right after the next {@code '['}, or the text's length if there isn't one. */
	static int nextSegment(String text, int pos) {
		int idx = text.indexOf('[', pos);
		return idx < 0 ? text.length() : idx + 1;
	}
}
