package org.atari.raster.rmt.model;

/**
 * The file formats of the song: the module codec ({@link #makeModule}/
 * {@link #decodeModule} with {@link #songToAta}/{@link #ataToSong}), the
 * {@code .rmt}, {@code .txt} and {@code .rmw} load/save pairs, and the
 * {@link #checkBeforeFileSave} gate. All of it lived on {@link Song} until
 * plan 45 moved it here: these are stateless transformations between bytes
 * and the song's state, the same species as {@link SongExporter} and
 * {@link RmtExporter} - a {@code final} class of static methods taking the
 * {@link Song} they work on. The name follows C++'s own tier for the same
 * concern, {@code IO_Song.cpp} (the bodies currently sit in
 * {@code SongIO.cpp}, the mirror of this class).
 *
 * <p>The few {@link Song} fields these methods read and write beyond its
 * public API are package-private - the Java spelling of the C++
 * {@code friend} (plans/45_JAVA_SONG_SPLIT_PLAN.md).
 */
public final class SongIO {

	private SongIO() {
	}

	private static int unsignedByte(byte[] buf, int index) {
		return buf[index] & 0xFF;
	}

	/** Encodes {@code song}/{@code songGo} into the Atari module's song-data byte format, returning the number of bytes actually used. */
	public static int songToAta(Song song, byte[] dest, int max, int adr) {
		int channels = song.getChannels();
		int len = 0;
		for (int sline = 0; sline < Song.SONGLEN; sline++) {
			int apos = sline * channels;
			if (apos + channels > max) {
				return len; // buffer overflow
			}

			int go = song.songGo[sline];
			if (go >= 0) {
				// There is a goto line
				dest[apos] = (byte) 254; // go command
				dest[apos + 1] = (byte) go; // number where to jump
				int goadr = (adr + go * channels) & 0xFFFF;
				dest[apos + 2] = (byte) (goadr & 0xff); // low byte
				dest[apos + 3] = (byte) (goadr >> 8); // high byte
				if (channels > 4) {
					for (int j = 4; j < channels; j++) {
						dest[apos + j] = (byte) 255; // to make sure this is the correct line
					}
				}
				len = sline * channels + 4; // this is the end for now (goto has 4 bytes for 8 tracks)
			} else {
				// There are track numbers
				for (int i = 0; i < channels; i++) {
					int j = song.song[sline][i];
					if (j >= 0 && j < Tracks.TRACKSNUM) {
						dest[apos + i] = (byte) j;
						len = (sline + 1) * channels; // this is the end for now
					} else {
						dest[apos + i] = (byte) 255;
					}
				}
			}
		}
		return len;
	}

	/**
	 * Decodes the Atari module's song-data byte format (the {@link #songToAta} counterpart) into {@code song}/{@code songGo}.
	 * Always returns {@code true} (C++'s {@code BOOL} return is unconditional in every branch of the original).
	 */
	public static boolean ataToSong(Song song, byte[] sour, int len, int adr) {
		int channels = song.getChannels();
		int i = 0;
		int col = 0;
		int line = 0;
		while (i < len) {
			int b = unsignedByte(sour, i);
			// C++ also checks "b >= 0", tautological for its unsigned char b.
			if (b < Tracks.TRACKSNUM) {
				song.song[line][col] = b;
			} else if (b == 254 && col == 0) {
				int ptr = unsignedByte(sour, i + 2) | (unsignedByte(sour, i + 3) << 8); // goto vector
				int go = (ptr - adr) / channels;
				if (go >= 0 && go < (len / channels) && go < Song.SONGLEN) {
					song.songGo[line] = go;
				} else {
					song.songGo[line] = 0; // place of invalid jump and jump to line 0
				}
				i += channels;
				if (i >= len) {
					return true; // this is the end of goto
				}
				line++;
				if (line >= Song.SONGLEN) {
					return true;
				}
				continue;
			} else {
				song.song[line][col] = -1;
			}

			col++;
			if (col >= channels) {
				line++;
				if (line >= Song.SONGLEN) {
					return true; // so that it does not overflow
				}
				col = 0;
			}
			i++;
		}
		return true;
	}

	/**
	 * {@code CSong::TestBeforeFileSave()}: validates the song before an RMT
	 * save/export - a module must build, the song must not be empty, no
	 * "go to" may point past the last used line or at another "go to"
	 * (errors); consecutive "go to" lines and runs of empty lines are
	 * warnings the user may ignore; a song without a final "go to" gets one
	 * to line 0 (after an information box). Returns whether saving may
	 * proceed. The boxes go through {@code messages}.
	 */
	public static boolean checkBeforeFileSave(Song song, Messages messages) {
		// Try to create a module
		byte[] mem = new byte[Memory.SIZE];
		int adrModule = RmtModuleFile.DEFAULT_LOAD_ADDRESS;
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		if (makeModule(song, mem, adrModule, SongIOType.RMT, instrumentSavedFlags, trackSavedFlags) < 0) {
			return false; // Dump out if the module could not be created
		}

		// and now it will be checked whether the song ends with GOTO line and if there is no GOTO on GOTO line
		StringBuilder errmsg = new StringBuilder();
		StringBuilder wrnmsg = new StringBuilder();
		int[] trx = new int[Song.SONGLEN];
		int last = -1;
		int empty = 0;
		for (int i = 0; i < Song.SONGLEN; i++) {
			if (song.songGo[i] >= 0) {
				trx[i] = 2;
				last = i;
			} else {
				trx[i] = 0;
				for (int j = 0; j < song.getChannels(); j++) {
					if (song.song[i][j] >= 0 && song.song[i][j] < Tracks.TRACKSNUM) {
						trx[i] = 1;
						last = i; // tracks
						break;
					}
				}
			}
		}

		if (last < 0) {
			errmsg.append("Error: Song is empty.\n");
		}

		for (int i = 0; i <= last; i++) {
			boolean testTooManyEmptyLines;
			if (song.songGo[i] >= 0) {
				// there is a goto line
				int go = song.songGo[i]; // where is goto set to?
				if (go > last) {
					errmsg.append(String.format("Error: Song line [%02X]: Go to line over last used song line.\n", i));
				}
				if (song.songGo[go] >= 0) {
					errmsg.append(String.format("Error: Song line [%02X]: Recursive \"go to line\" to \"go to line\".\n", i));
				}
				if (i > 0 && song.songGo[i - 1] >= 0) {
					wrnmsg.append(String.format("Warning: Song line [%02X]: More \"go to line\" on subsequent lines.\n", i));
				}
				testTooManyEmptyLines = true; // goto TestTooManyEmptyLines
			} else {
				// are there tracks or empty lines?
				testTooManyEmptyLines = trx[i] != 0;
				if (!testTooManyEmptyLines) {
					empty++;
				}
			}
			if (testTooManyEmptyLines) {
				if (empty > 1) {
					wrnmsg.append(String.format("Warning: Song lines [%02X-%02X]: Too many empty song lines (%d) waste memory.\n", i - empty, i - 1, empty));
				}
				empty = 0;
			}
		}

		if (last >= 0 && trx[last] == 1) {
			String gotoline = String.format("Song line[%02X]: Unexpected end of song.\nYou have to use \"go to line\" at the end of song.\n\nSong line [00] will be used by default.", last + 1);
			messages.sendInformationMessage("Warning", gotoline);
			if (last + 1 < Song.SONGLEN) {
				song.songGo[last + 1] = 0; // force a goto line to the first track line (C++ writes past the array when last is the final line)
			}
		}

		// If the warning or error messages aren't empty, something did happen
		if (errmsg.length() > 0 || wrnmsg.length() > 0) {
			// If there are warnings without errors, the choice is left to ignore them
			if (errmsg.length() == 0) {
				wrnmsg.append("\nIgnore warnings and save anyway?");
				return messages.sendQuestionMessage("Warnings", wrnmsg.toString(), MessageButtons.YES_NO) == MessageAnswer.YES;
			}
			// Otherwise, if there are any errors, always return failure
			messages.sendErrorMessage("Errors", errmsg.toString() + wrnmsg);
			return false;
		}
		return true;
	}

	/**
	 * Encodes the song into RMT's plain-text {@code .txt} format, returning
	 * the built text directly instead of C++'s {@code std::ostream&} output
	 * parameter (matching {@code SapFile.export()}'s established idiom).
	 *
	 * <p>{@code g_Instruments.SaveAll}/{@code g_Tracks.SaveAll}'s
	 * {@code [INSTRUMENT]}/{@code [TRACK]} sections (previously omitted -
	 * see {@code plans/15_JAVA_SONGEDITING_PLAN.md}'s
	 * "IO_Instruments.cpp/IO_Tracks.cpp" write-up) are now ported as
	 * {@link Instruments#saveAllTxt}/{@link Tracks#saveAllTxt}.
	 */
	public static String saveTxt(Song song) {
		int channels = song.getChannels();
		StringBuilder s = new StringBuilder();

		s.append("[MODULE]\n");
		s.append(String.format("RMT: %X\n", channels));
		s.append("NAME: ").append(song.getName()).append("\n");
		s.append(String.format("MAXTRACKLEN: %02X\n", song.tracks.getMaxTrackLength()));
		s.append(String.format("MAINSPEED: %02X\n", song.mainSpeed));
		s.append(String.format("INSTRSPEED: %X\n", song.instrumentSpeed));
		s.append(String.format("VERSION: %02X\n", RmtFormatVersion.V1));
		s.append("\n"); // gap
		s.append("[SONG]\n");

		// Looking for the length of the song
		int songLength = -1;
		for (int i = 0; i < Song.SONGLEN; i++) {
			if (song.songGo[i] >= 0) {
				songLength = i;
				continue;
			}
			for (int j = 0; j < channels; j++) {
				if (song.song[i][j] >= 0 && song.song[i][j] < Tracks.TRACKSNUM) {
					songLength = i;
					break;
				}
			}
		}

		// Write the song
		for (int i = 0; i <= songLength; i++) {
			if (song.songGo[i] >= 0) {
				s.append(String.format("Go to line %02X\n", song.songGo[i]));
				continue;
			}
			for (int j = 0; j < channels; j++) {
				int t = song.song[i][j];
				if (t >= 0 && t < Tracks.TRACKSNUM) {
					s.append(IOHelpers.charH4(t)).append(IOHelpers.charL4(t));
				} else {
					s.append("--");
				}
				if (j + 1 == channels) {
					s.append("\n"); // for the last end of the line
				} else {
					s.append(" "); // between them
				}
			}
		}

		s.append("\n"); // gap

		// Now save the instruments and tracks to the output
		s.append(song.instruments.saveAllTxt());
		s.append(song.tracks.saveAllTxt());

		return s.toString();
	}

	/**
	 * Decodes RMT's plain-text {@code .txt} format (the {@link #saveTxt}
	 * counterpart) from {@code text}, taking the whole file content
	 * directly instead of C++'s {@code std::istream&} (matching
	 * {@code SapFile}'s established idiom - the C++ test itself already
	 * builds the whole string upfront via a {@code std::istringstream}).
	 *
	 * <p>The {@code [INSTRUMENT]}/{@code [TRACK]} segments now decode via
	 * {@link Instruments#loadInstrumentTxt}/{@link Tracks#loadTrackTxt}
	 * (previously skipped - see {@link #saveTxt}'s javadoc). Always succeeds -
	 * C++'s {@code LoadTxt} has no failure return, matching
	 * {@link Song#playPressedTonesInit}'s established "drop the always-true
	 * return" reasoning; a {@code "RMT:"} line sets the song's channel count.
	 */
	public static void loadTxt(Song song, String text, Undo undo) {
		song.clearSong(Song.Channels.STEREO, undo); // always clear 8 tracks

		song.tracks.initTracks();

		// Read until the first "[" is found. This indicates a segment [.....]
		int pos = IOHelpers.nextSegment(text, 0);

		while (pos < text.length()) {
			IOHelpers.Line header = IOHelpers.readLine(text, pos);
			String line = IOHelpers.trimstr(header.content());
			pos = header.nextPos();

			if (line.equals("MODULE]")) {
				while (pos < text.length()) {
					// Check for next segment start '['
					char b = text.charAt(pos);
					pos++;
					if (b == '[') {
						break;
					}
					if (b == '\n') {
						// A blank line (saveTxt() writes one as a "gap"
						// before the next segment) - not real content, so
						// it must not be handed to IOHelpers.readLine() below (see
						// AtariTrackerDriver... no, see this method's C++
						// counterpart's own fix comment in SongEditing.cpp).
						continue;
					}
					// Not a segment start so save the read character and get the rest of the line
					IOHelpers.Line rest = IOHelpers.readLine(text, pos);
					String kvLine = IOHelpers.trimstr(b + rest.content());
					pos = rest.nextPos();

					// Split on the ": " (COLON + SPACE) point
					int colonSpace = kvLine.indexOf(": ");
					if (colonSpace < 0) {
						continue;
					}
					String key = kvLine.substring(0, colonSpace + 1);
					String value = kvLine.substring(colonSpace + 2);

					// Process each of the possible commands in a [MODULE]
					switch (key) {
					case "RMT:" -> {
						// RMT version indicator: 4 or 8
						int v = IOHelpers.hexstr(value, 0, 2);
						song.setChannels(v <= Song.Channels.MONO ? Song.Channels.MONO : Song.Channels.STEREO);
					}
					case "NAME:" -> {
						// Set the name of the song.
						java.util.Arrays.fill(song.songName, ' ');
						int lname = Math.min(value.length(), SongInfo.SONG_NAME_MAX_LEN);
						for (int i = 0; i < lname; i++) {
							song.songName[i] = value.charAt(i);
						}
					}
					case "MAXTRACKLEN:" -> {
						// Set how long a track is: MAXTRACKLEN: 00-FF
						int v = IOHelpers.hexstr(value, 0, 2);
						song.tracks.setMaxTrackLength(v == 0 ? 256 : v);
						song.tracks.initTracks(); // reinitialize
					}
					case "MAINSPEED:" -> {
						// Set the play speed: MAINSPEED: 01-FF
						int v = IOHelpers.hexstr(value, 0, 2);
						if (v > 0) {
							song.mainSpeed = v;
						}
					}
					case "INSTRSPEED:" -> {
						// Set the instrument speed: INSTRSPEED: 01-FF
						int v = IOHelpers.hexstr(value, 0, 1);
						if (v > 0) {
							song.instrumentSpeed = v;
						}
					}
					default -> {
						// VERSION: not needed for TXT yet, and anything unrecognized
					}
					}
				}
			} else if (line.equals("SONG]")) {
				int idx;
				for (idx = 0; pos < text.length() && idx < Song.SONGLEN; idx++) {
					// Read the song line. Dump out if its the next section
					char b = text.charAt(pos);
					pos++;
					if (b == '[') {
						break;
					}
					if (b == '\n') {
						idx--; // this iteration didn't consume a real song line
						continue;
					}
					IOHelpers.Line rest = IOHelpers.readLine(text, pos);
					pos = rest.nextPos();
					String content = b + rest.content();

					// The line is one of two types: "Go to line XX" or "-- -- -- --"
					if (content.startsWith("Go to line ")) {
						int go = IOHelpers.hexstr(content, 11, 2);
						if (go >= 0 && go < Song.SONGLEN) {
							song.songGo[idx] = go;
						}
						continue;
					}
					for (int i = 0; i < song.getChannels(); i++) {
						int track = IOHelpers.hexstr(content, i * 3, 2);
						if (track >= 0 && track < Tracks.TRACKSNUM) {
							song.song[idx][i] = track;
						}
					}
				}
			} else if (line.equals("INSTRUMENT]")) {
				// Pass the instrument loading to the Instruments class
				pos = song.instruments.loadInstrumentTxt(text, pos);
			} else if (line.equals("TRACK]")) {
				// Pass the track loading to the Tracks class
				pos = song.tracks.loadTrackTxt(text, pos);
			} else {
				pos = IOHelpers.nextSegment(text, pos); // look for the beginning of the next segment
			}
		}

	}

	private static final int RMW_MAIN_PARAMS_COUNT = 31;

	private static void writeIntLE(java.io.ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 24) & 0xFF);
	}

	private static int readIntLE(byte[] data, int pos) {
		return unsignedByte(data, pos) | (unsignedByte(data, pos + 1) << 8) | (unsignedByte(data, pos + 2) << 16) | (unsignedByte(data, pos + 3) << 24);
	}

	private static int indexOf(byte[] data, byte value, int from) {
		for (int i = from; i < data.length; i++) {
			if (data[i] == value) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Encodes the song into RMT's binary {@code .rmw} project-save format,
	 * returning the built bytes directly instead of C++'s
	 * {@code std::ostream&} output parameter (matching {@link #saveTxt}'s
	 * reasoning). Each of the 31 "main parameters" is written as a 4-byte
	 * little-endian int (matching this project's native x86/x64 byte order
	 * and the just-fixed {@code sizeof(int)} - see
	 * {@code plans/15_JAVA_SONGEDITING_PLAN.md}'s sub-batch 8 entry for that
	 * fix's own history).
	 *
	 * <p><b>16 of the 31 "main parameters" are UI settings</b>, C++'s
	 * globals {@code g_activepart}, {@code g_active_ti}, {@code g_prove},
	 * {@code g_respectvolume}, {@code g_trackLinePrimaryHighlight},
	 * {@code g_tracklinealtnumbering}, {@code g_displayflatnotes},
	 * {@code g_usegermannotation}, {@code g_cursoractview},
	 * {@code g_keyboard_layout}, {@code g_keyboard_escresetatarisound},
	 * {@code g_keyboard_swapenter}, {@code g_keyboard_playautofollow},
	 * {@code g_keyboard_updowncontinue},
	 * {@code g_keyboard_RememberOctavesAndVolumes} and (a second time, as the
	 * C++ macro lists it) {@code g_keyboard_escresetatarisound} - in that
	 * order, {@link #RMW_UI_PARAMS_COUNT} of them at index
	 * {@link #RMW_UI_PARAMS_INDEX}. The model does not own them, so the
	 * caller passes them ({@code uiParams}; the UI's session state) and gets
	 * them back from {@link #loadRMW} to apply, as C++'s load writes the
	 * globals. The overload without them writes zeros (the model tests); the
	 * cross-program comparison ({@code CrossProgramExportTest}) checks the
	 * real values against {@code Rmt.exe}. Before 2026-09-27 the port always
	 * wrote zeros there.
	 *
	 * <p>{@code g_Instruments.SaveAll}/{@code g_Tracks.SaveAll} (previously
	 * omitted - see {@link #saveTxt}'s javadoc) are now ported as
	 * {@link Instruments#saveAllRmw}/{@link Tracks#saveAllRmw} - RMW saves
	 * every instrument/track unconditionally, unlike TXT's non-empty-only
	 * rule.
	 */
	/** Where the 16 UI settings sit among the 31 main parameters, and how many there are. */
	public static final int RMW_UI_PARAMS_INDEX = 8;
	public static final int RMW_UI_PARAMS_COUNT = 16;

	public static byte[] saveRMW(Song song) {
		return saveRMW(song, new int[RMW_UI_PARAMS_COUNT]);
	}

	/** {@code uiParams}: the 16 UI settings in the order the class javadoc lists. */
	public static byte[] saveRMW(Song song, int[] uiParams) {
		if (uiParams.length != RMW_UI_PARAMS_COUNT) {
			throw new IllegalArgumentException("uiParams must have " + RMW_UI_PARAMS_COUNT + " entries, not " + uiParams.length);
		}
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

		byte[] versionBytes = RmtVersion.RMT_VERSION_STRING.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		out.write(versionBytes, 0, versionBytes.length);
		out.write('\n'); // std::endl

		for (int i = 0; i < SongInfo.SONG_NAME_MAX_LEN; i++) {
			out.write(song.songName[i]);
		}
		out.write(0); // the extra byte C++'s m_songname[SONG_NAME_MAX_LEN + 1] has, beyond this port's own (SONG_NAME_MAX_LEN)-sized array

		writeIntLE(out, RMW_MAIN_PARAMS_COUNT);
		int[] mainParams = new int[RMW_MAIN_PARAMS_COUNT];
		int[] own = { song.getChannels(), song.speed, song.mainSpeed, song.instrumentSpeed, song.songActiveLine, song.songPlayLine, song.trackActiveLine, song.trackPlayLine };
		System.arraycopy(own, 0, mainParams, 0, own.length);
		System.arraycopy(uiParams, 0, mainParams, RMW_UI_PARAMS_INDEX, RMW_UI_PARAMS_COUNT); // the 16 UI settings - see the javadoc
		int[] rest = { song.trackActiveCol, song.trackActiveCur, song.activeInstr, song.volume, song.octave, song.infoAct.ordinal(), song.songNameCursor };
		System.arraycopy(rest, 0, mainParams, RMW_UI_PARAMS_INDEX + RMW_UI_PARAMS_COUNT, rest.length);
		for (int param : mainParams) {
			writeIntLE(out, param);
		}

		// Write a complete song and songgo
		for (int i = 0; i < Song.SONGLEN; i++) {
			for (int j = 0; j < Song.SONGTRACKS; j++) {
				writeIntLE(out, song.song[i][j]);
			}
		}
		for (int i = 0; i < Song.SONGLEN; i++) {
			writeIntLE(out, song.songGo[i]);
		}

		out.writeBytes(song.instruments.saveAllRmw());
		out.writeBytes(song.tracks.saveAllRmw());

		return out.toByteArray();
	}

	/** {@code success} is C++'s own {@code bool} return; {@code uiParams} the 16 UI settings the file carries (see {@link #saveRMW(Song, int[])}) - only meaningful when {@code success}. */
	public record LoadRmwResult(boolean success, int[] uiParams) {
	}

	/**
	 * Decodes RMT's binary {@code .rmw} project-save format (the
	 * {@link #saveRMW} counterpart) from {@code data}, taking the whole
	 * file content directly instead of C++'s {@code std::istream&}
	 * (matching {@link #loadTxt}'s reasoning). See {@link #saveRMW}'s
	 * javadoc for the 16 UI settings, which come back in the result for the
	 * caller to apply; instrument/track data now decodes via
	 * {@link Instruments#loadAllRmw}/{@link Tracks#loadAllRmw}.
	 */
	public static LoadRmwResult loadRMW(Song song, byte[] data, Undo undo) {
		song.clearSong(Song.Channels.STEREO, undo); // always clear 8 tracks

		int[] uiParams = new int[RMW_UI_PARAMS_COUNT];
		int nl = indexOf(data, (byte) '\n', 0);
		if (nl < 0) {
			return new LoadRmwResult(false, uiParams);
		}
		String fileVersion = new String(data, 0, nl, java.nio.charset.StandardCharsets.US_ASCII);
		int pos = nl + 1;
		if (!fileVersion.equals(RmtVersion.RMT_VERSION_STRING)) {
			// Guard-only: version mismatch. C++'s SendErrorMessage isn't
			// reproduced - Song holds no Messages reference (matches
			// instrChangeApply's established reasoning).
			return new LoadRmwResult(false, uiParams);
		}

		for (int i = 0; i < SongInfo.SONG_NAME_MAX_LEN; i++) {
			song.songName[i] = (char) unsignedByte(data, pos + i);
		}
		pos += SongInfo.SONG_NAME_MAX_LEN + 1; // + the extra byte C++'s m_songname has

		int p = readIntLE(data, pos); // number of main parameters
		pos += 4;

		// Mirrors C++'s mainparams[] order exactly - a parameter the file
		// doesn't actually include (p < 31) keeps its current value, the
		// same as C++ leaving a not-yet-overwritten local variable at its
		// pre-existing (clearSong()-defaulted) value.
		int[] mainParams = {
				song.getChannels(), song.speed, song.mainSpeed, song.instrumentSpeed,
				song.songActiveLine, song.songPlayLine, song.trackActiveLine, song.trackPlayLine,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				song.trackActiveCol, song.trackActiveCur, song.activeInstr, song.volume, song.octave, song.infoAct.ordinal(), song.songNameCursor
		};
		for (int i = 0; i < p && i < mainParams.length; i++) {
			mainParams[i] = readIntLE(data, pos);
			pos += 4;
		}

		if (mainParams[0] != Song.Channels.MONO && mainParams[0] != Song.Channels.STEREO) {
			return new LoadRmwResult(false, uiParams); // a channel count no tracker driver plays
		}
		song.setChannels(mainParams[0]);
		song.speed = mainParams[1];
		song.mainSpeed = mainParams[2];
		song.instrumentSpeed = mainParams[3];
		song.songActiveLine = mainParams[4];
		song.songPlayLine = mainParams[5];
		song.trackActiveLine = mainParams[6];
		song.trackPlayLine = mainParams[7];
		System.arraycopy(mainParams, RMW_UI_PARAMS_INDEX, uiParams, 0, RMW_UI_PARAMS_COUNT); // the 16 UI settings, for the caller to apply
		song.trackActiveCol = mainParams[24];
		song.trackActiveCur = mainParams[25];
		song.activeInstr = mainParams[26];
		song.volume = mainParams[27];
		song.octave = mainParams[28];
		song.infoAct = EditArea.values()[mainParams[29]];
		song.songNameCursor = mainParams[30];

		// Read the complete song and songgo
		for (int i = 0; i < Song.SONGLEN; i++) {
			for (int j = 0; j < Song.SONGTRACKS; j++) {
				song.song[i][j] = readIntLE(data, pos);
				pos += 4;
			}
		}
		for (int i = 0; i < Song.SONGLEN; i++) {
			song.songGo[i] = readIntLE(data, pos);
			pos += 4;
		}

		pos = song.instruments.loadAllRmw(data, pos);
		song.tracks.loadAllRmw(data, pos);

		return new LoadRmwResult(true, uiParams);
	}

	/**
	 * Decodes an RMT {@code .rmt} module file (two concatenated
	 * {@link AtariIO#loadBinaryBlock} blocks: the module data, then an
	 * optional song/instrument names block) from {@code data}. Always
	 * returns {@code true} on the one path any test reaches - the "missing
	 * names block" branch is characterized as a real, non-failure outcome
	 * in C++ too (an unconditional {@code SendInformationMessage} "stripped
	 * RMT" dialog, not reproduced here per {@link #loadTxt}'s established
	 * reasoning, followed by {@code return true}). The two guard-only
	 * failure paths (corrupted first block; {@link #decodeModule} rejecting
	 * it) return {@code success == false} without reproducing their
	 * {@code SendErrorMessage} calls either.
	 */
	public record LoadRmtResult(boolean success, int moduleAddress) {
	}

	public static LoadRmtResult loadRMT(Song song, byte[] data) {
		byte[] mem = new byte[Atari.MEMORY_SIZE];

		AtariIO.BinaryBlockResult mainBlock = AtariIO.loadBinaryBlock(data, 0, mem);
		if (mainBlock.length() <= 0) {
			return new LoadRmtResult(false, 0); // did not retrieve any data in the first block
		}

		byte[] instrumentLoadedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackLoadedFlags = new byte[Tracks.TRACKSNUM];
		int version = decodeModule(song, mem, mainBlock.fromAddr(), mainBlock.toAddr() + 1, instrumentLoadedFlags, trackLoadedFlags);
		if (version == 0) {
			return new LoadRmtResult(false, 0); // bad RMT data format or old tracker version
		}

		// RMT - now read the second block with names
		AtariIO.BinaryBlockResult namesBlock = AtariIO.loadBinaryBlock(data, mainBlock.inputBytesConsumed(), mem);
		if (namesBlock.length() < 1) {
			return new LoadRmtResult(true, mainBlock.fromAddr()); // stripped RMT module - song/instrument names are missing, not a failure
		}

		// Parse the song name (until we hit the terminating zero)
		int idx = 0;
		while (idx < SongInfo.SONG_NAME_MAX_LEN) {
			int ch = unsignedByte(mem, namesBlock.fromAddr() + idx);
			if (ch == 0) {
				break;
			}
			song.songName[idx] = (char) ch;
			idx++;
		}
		for (int k = idx; k < SongInfo.SONG_NAME_MAX_LEN; k++) {
			song.songName[k] = ' '; // fill in the gaps
		}

		int addrInstrumentNames = namesBlock.fromAddr() + idx + 1; // +1 that's the zero behind the name
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			if (instrumentLoadedFlags[i] == 0) {
				continue; // this instrument wasn't loaded
			}
			char[] name = song.instruments.getName(i);
			int nameIdx = 0;
			while (nameIdx < name.length) {
				int ch = unsignedByte(mem, addrInstrumentNames + nameIdx);
				if (ch == 0) {
					break;
				}
				name[nameIdx] = (char) ch;
				nameIdx++;
			}
			for (int k = nameIdx; k < name.length; k++) {
				name[k] = ' '; // fill in the gaps
			}
			addrInstrumentNames += nameIdx + 1; // +1 is zero behind the name
		}

		return new LoadRmtResult(true, mainBlock.fromAddr()); // The main block of the module is OK => take its boot address (C++: g_rmtstripped_adr_module = fromAddr)
	}

	/**
	 * Encodes the song/tracks/instruments (per {@code iotype}'s save-all-vs-
	 * save-used-only rule) into the Atari RMT module byte format at
	 * {@code mem[addr...]}, returning the address of the first byte past the
	 * last one used, or {@code -1} if the song does not make a module - a
	 * track too event-dense to encode, a speed outside the header's range,
	 * or a module that does not fit at {@code addr}. C++'s
	 * {@code SendErrorMessage} isn't reproduced, matching
	 * {@link Song#instrChangeApply}'s established reasoning.
	 *
	 * <p>Since plan 60 M2 a wrapper over the container:
	 * {@link RmtModuleCodec#fromSong} decides what is saved and
	 * {@link RmtModule#write} lays it out. The flags mark the instruments
	 * and tracks the module holds.
	 */
	public static int makeModule(Song song, byte[] mem, int addr, SongIOType iotype, byte[] instrumentSavedFlags, byte[] trackSavedFlags) {
		java.util.Arrays.fill(instrumentSavedFlags, (byte) 0);
		java.util.Arrays.fill(trackSavedFlags, (byte) 0);
		try {
			RmtModule module = RmtModuleCodec.fromSong(song, iotype);
			int endOfModule = module.write(mem, addr);
			for (int number : module.instruments().keySet()) {
				instrumentSavedFlags[number] = 1;
			}
			for (int number : module.tracks().keySet()) {
				trackSavedFlags[number] = 1;
			}
			return endOfModule; // address of the first byte past the last one used
		} catch (RmtModule.FormatException e) {
			return -1;
		}
	}

	/**
	 * Decodes an Atari RMT module byte format at {@code mem[fromAddr..endAddr)}
	 * back into this {@link Song} (plus the shared {@code Tracks}/
	 * {@code Instruments} collaborators). Omits C++'s
	 * {@code g_Instruments.Update(instrumentNr)} call's Atari-memory write for
	 * each decoded instrument (see {@link Instruments#update} - its
	 * display-hint half is done); no {@code Atari} dependency is modeled on
	 * {@link Song}, and no test observes it.
	 *
	 * <p>C++ reads {@code InstrToAta}/{@code TrackToAta}/{@link #ataToSong}'s
	 * input directly from {@code mem} at a pointer offset; since Java arrays
	 * can't be sliced without copying, each gets a
	 * {@code Arrays.copyOfRange} view instead.
	 *
	 * <p>Returns the module's format version, 0 on failure - ambiguous with a
	 * genuinely decoded version-0 file, an existing C++ design wart, not
	 * introduced here.
	 *
	 * <p>Since plan 60 M2 a wrapper over the container -
	 * {@link RmtModule#parse}, then {@link RmtModuleCodec#toSong} - so a
	 * module the container rejects, format version 0 included, leaves the
	 * song as it was.
	 */
	public static int decodeModule(Song song, byte[] mem, int fromAddr, int endAddr, byte[] instrumentLoadedFlags, byte[] trackLoadedFlags) {
		java.util.Arrays.fill(instrumentLoadedFlags, (byte) 0);
		java.util.Arrays.fill(trackLoadedFlags, (byte) 0);
		RmtModule module;
		try {
			module = RmtModule.parse(mem, fromAddr, endAddr);
			RmtModuleCodec.toSong(module, song);
		} catch (RmtModule.FormatException e) {
			return 0;
		}
		for (int number : module.instruments().keySet()) {
			instrumentLoadedFlags[number] = 1;
		}
		for (int number : module.tracks().keySet()) {
			trackLoadedFlags[number] = 1;
		}
		return module.formatVersion();
	}
}
