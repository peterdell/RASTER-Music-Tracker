package org.atari.raster.rmt.model;

/**
 * Ported from CASMFileBuilder (src/cpp/ASMFileBuilder.h/.cpp) - fully,
 * matching {@code ASMFileBuilderTests.cpp}'s own complete coverage (no
 * globals, no dependency on any not-yet-ported class).
 *
 * <p>C++'s {@code CString& strCode} output parameter (always overwritten as
 * the method's first statement, then appended to) becomes a returned
 * {@link Result} pairing the built text with the method's own {@code int}
 * return value - simpler than threading a mutable string buffer through as
 * a parameter, and Java strings are immutable anyway.
 *
 * <p><b>A fragile contract preserved as-is</b>: {@link #buildTracksData}'s
 * trailing validity check scans {@code trackPos[0..65535]} unconditionally
 * (not just the {@code [from, to)} range actually processed), so the array
 * passed in must always have at least 65536 entries - already characterized
 * in {@code ASMFileBuilderTests.cpp}, not something to harden against here.
 */
public final class AsmFileBuilder {

	private AsmFileBuilder() {
	}

	/** The text built by one of this class's methods, paired with its own {@code int} return value (a count, or 0 signaling a validation failure). */
	public record Result(String code, int size) {
	}

	public static Result buildInstrumentData(String instrumentsLabel, byte[] buf, int from, int to, int[] info, AssemblerFormat assemblerFormat) {
		StringBuilder code = new StringBuilder("\n\n; Instrument data\n");

		int sizeInstruments = 0;
		if (!instrumentsLabel.isEmpty()) {
			// Make the instruments relocatable
			code.append(assemblerFormat == AssemblerFormat.ATASM ? "* = " + instrumentsLabel + "\n" : "org " + instrumentsLabel + "\n");
		}

		for (int i = from, l = 0; i < to; i++, l++) {
			if (info[i] != 0) {
				code.append("\n?Instrument_").append(info[i] - 1);
				info[i] = 0;
				l = 0;
			}
			if (l % 16 == 0) {
				code.append("\n    {{byte}} ");
			} else {
				code.append(",");
			}
			code.append(String.format("$%02x", buf[i] & 0xFF));

			sizeInstruments++;
		}

		return new Result(code.toString(), sizeInstruments);
	}

	public static Result buildTracksData(String tracksLabel, byte[] buf, int from, int to, int[] trackPos, AssemblerFormat assemblerFormat) {
		StringBuilder code = new StringBuilder("\n\n; Track data");

		int sizeTrack = 0;
		if (!tracksLabel.isEmpty()) {
			// Make the track data relocatable
			code.append(assemblerFormat == AssemblerFormat.ATASM ? "\n* = " + tracksLabel + "\n" : "\norg " + tracksLabel + "\n");
		}
		for (int i = from, l = 0; i < to; i++, l++) {
			if (trackPos[i] != 0) {
				code.append(String.format("\n?Track_%02x", trackPos[i] - 1));
				trackPos[i] = 0;
				l = 0;
			}
			if (l % 16 == 0) {
				code.append("\n    {{byte}} ");
			} else {
				code.append(",");
			}
			code.append(String.format("$%02x", buf[i] & 0xFF));

			sizeTrack++;
		}
		for (int i = 0; i < 65536; i++) {
			if (trackPos[i] != 0) {
				return new Result(code.toString(), 0);
			}
		}
		return new Result(code.toString(), sizeTrack);
	}

	public static Result buildSongData(String songLinesLabel, byte[] buf, int offsetSong, int len, int start, int numTracks, AssemblerFormat assemblerFormat) {
		StringBuilder code = new StringBuilder("\n\n; Song data\n");

		int sizeSongLines = 0;
		if (!songLinesLabel.isEmpty()) {
			code.append(assemblerFormat == AssemblerFormat.ATASM ? "\n* = " + songLinesLabel + "\n" : "\norg " + songLinesLabel + "\n");
		}

		code.append("?SongData");
		int jmp = 0;
		int l = 0;
		for (int i = offsetSong; i < len; i++, l++) {
			if (jmp == -2) {
				jmp = 0x10000 + (buf[i] & 0xFF);
				continue;
			} else if (jmp > 0) {
				jmp = (0xFFFF & (jmp | ((buf[i] & 0xFF) << 8))) - start;
				if (0 == ((jmp - offsetSong) % numTracks) && jmp >= offsetSong && jmp < len) {
					int lnum = (jmp - offsetSong) / numTracks;
					if (assemblerFormat == AssemblerFormat.ATASM) {
						code.append(String.format(",<?line_%02x,>?line_%02x", lnum, lnum));
					} else {
						code.append(String.format(",l(__line_%02x),h(__line_%02x)", lnum, lnum));
					}
				} else {
					// C++'s format string uses a "% 04x"/"% x" space flag,
					// which C++ silently drops for a hex conversion but
					// Java's Formatter rejects outright (space flag +
					// 'x' conversion throws) - reproduced by hand instead,
					// matching the exact same visible output (a single
					// literal space, not one contributed by the flag).
					code.append("; ERROR malformed file(song jump bad $ ")
							.append(String.format("%04x", jmp))
							.append("[")
							.append(String.format("%x", offsetSong))
							.append(":")
							.append(String.format("%x", len))
							.append("])\n");
					if (assemblerFormat == AssemblerFormat.ATASM) {
						code.append(String.format(",<($%x+?SongData),>($%x+?SongData)", jmp, jmp));
					} else {
						code.append(String.format(",l($%x+__SongData),h($%x+__SongData)", jmp, jmp));
					}
				}
				jmp = 0;
				// Allows terminating song on last JUMP
				if (i + 1 == len && numTracks == 8) {
					l += 4;
				}
				continue;
			} else if (jmp == -1) {
				jmp = -2;
			}

			if (l % numTracks == 0) {
				code.append(String.format("\n?Line_%02x  {{byte}} ", l / numTracks));
			} else {
				code.append(",");
			}
			code.append(String.format("$%02x", buf[i] & 0xFF));

			if ((buf[i] & 0xFF) == 0xfe) {
				if ((l % numTracks) != 0) {
					return new Result(code.toString(), 0);
				} else {
					jmp = -1;
				}
			}

			sizeSongLines++;
		}
		code.append("\n");

		return new Result(code.toString(), sizeSongLines);
	}
}
