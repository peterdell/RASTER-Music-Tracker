package com.wudsn.tools.rmt.model;

/**
 * Ported from CSong (src/cpp/Song.h, SongCore.cpp, SongEditing.cpp). Started
 * as a deliberately minimal slice, just what {@link Undo} needs (see
 * {@code plans/JAVA_PORT_PLAN.md} for the full scoping rationale), then
 * grew to cover all of {@code SongCore.cpp} - the already-tested,
 * globals-free "safe cluster" C++ split out from the rest of
 * {@code Song.cpp}/{@code IO_Song.cpp} for the same reason
 * {@code Tuning.cpp}/{@code TuningTables.cpp} and
 * {@code Instruments.cpp}/{@code InstrumentsCore.cpp} were split. CSong
 * itself is a much larger class ("the God Object" per this project's own
 * characterization-testing notes); everything else - playback, file I/O,
 * the GUI-adjacent editing surface in {@code SongEditing.cpp} - stays
 * deferred.
 *
 * <p><b>{@code g_tracks4_8} becomes an explicit parameter</b> on every
 * method that reads it in C++ ({@link #getTracks}, {@link #isStereo},
 * {@link #songToAta}, {@link #ataToSong}), matching this project's
 * established idiom for globals a ported method actually needs.
 *
 * <p><b>No {@code CPokeyStream} yet</b>: {@link #songPlayNextLine} omits
 * C++'s {@code m_pokeyStream}-consulting "song is done" check - it's always
 * null in every existing CSong test, and {@code PokeyStream} isn't ported.
 *
 * <p><b>No {@code CTrackClipboard} yet</b>: {@link #blockDeselect} is a
 * documented no-op - C++'s version delegates to {@code g_TrackClipboard},
 * which isn't ported. This only affects cosmetic block-selection state
 * after an undo/redo cursor jump, not the actual data being restored.
 *
 * <p><b>No UI/window-title tracking</b>: C++'s {@code InsertEvent()}-side
 * {@code g_changes}/{@code SetRMTTitle()} bookkeeping has no Java
 * equivalent (no window exists) and isn't ported onto {@link Undo} either.
 *
 * <p><b>{@link #stop} takes an {@link Undo} parameter</b> rather than a
 * stored field, avoiding a circular constructor dependency (C++'s
 * {@code CSong::Stop()} calls the global {@code g_Undo.Separator()}, while
 * {@code CUndo::Undo()}/{@code Redo()} call the global {@code g_Song.Stop()}
 * - two free-standing globals referencing each other). It also omits the
 * real body's {@code g_SongTimer.WaitForTimerRoutineProcessed()} call - no
 * live-playback/timer subsystem exists yet, and it's already a no-op in the
 * C++ test environment for the same reason (see {@code SongEditingStub.cpp}).
 */
public final class Song {

	public static final int SONGLEN = 256;
	public static final int SONGTRACKS = 8;

	private final Instruments instruments;

	private final int[][] song = new int[SONGLEN][SONGTRACKS];
	private final int[] songGo = new int[SONGLEN]; // if >= 0, then GO applies
	private final Bookmark bookmark = new Bookmark();

	// Song info section (matches SongInfo/TInfo's fields exactly)
	private final char[] songName = new char[SongInfo.SONG_NAME_MAX_LEN];
	private int speed;
	private int mainSpeed;
	private int instrumentSpeed;
	private int songNameCursor;

	private int songActiveLine;
	private int songPlayLine; // which song line is currently being played
	private int trackActiveLine;
	private int trackPlayLine; // which line of a track is currently being played
	private int trackActiveCol;
	private int trackActiveCur;

	private int activeInstr;
	private EditArea infoAct = EditArea.NAME;
	private boolean ntsc;

	private PlayMode playMode = PlayMode.PLAY_STOP;
	private int quantizationNote = -1;
	private int quantizationInstr = -1;
	private int quantizationVol = -1;

	private final int[] playPtNote = new int[SONGTRACKS];
	private final int[] playPtInstr = new int[SONGTRACKS];
	private final int[] playPtVolume = new int[SONGTRACKS];

	public Song(Instruments instruments) {
		this.instruments = instruments;
	}

	/** The song name, trimmed of trailing whitespace - matches C++'s null-terminated-CString-then-TrimRight() semantics. */
	public String getName() {
		int end = 0;
		while (end < songName.length && songName[end] != '\0') {
			end++;
		}
		return new String(songName, 0, end).stripTrailing();
	}

	public int getTracks(int tracks4_8) {
		return tracks4_8;
	}

	public boolean isStereo(int tracks4_8) {
		return getTracks(tracks4_8) > 4;
	}

	public boolean isNTSC() {
		return ntsc;
	}

	public int getInstrumentSpeed() {
		return instrumentSpeed;
	}

	/**
	 * Mirrors {@link #setPlayPressedTonesSilence} except the volume slot is
	 * reset to -1 instead of 0 - matches C++'s always-true BOOL return being
	 * dropped for the same reason as that sibling method.
	 */
	public void playPressedTonesInit() {
		for (int t = 0; t < SONGTRACKS; t++) {
			playPtNote[t] = -1;
			playPtInstr[t] = -1;
			playPtVolume[t] = -1;
		}
	}

	public int getActiveInstr() {
		return activeInstr;
	}

	public int getActiveColumn() {
		return trackActiveCol;
	}

	public int getPlayLine() {
		return trackPlayLine;
	}

	public void setPlayLine(int line) {
		trackPlayLine = line;
	}

	public int songGetPlayLine() {
		return songPlayLine;
	}

	public void songSetPlayLine(int line) {
		songPlayLine = line;
	}

	public void songTrackGoDec() {
		songGo[songActiveLine] = (songGo[songActiveLine] - 1) & 0xff;
	}

	public void songTrackGoInc() {
		songGo[songActiveLine] = (songGo[songActiveLine] + 1) & 0xff;
	}

	/** Finds a free track near the default track for {@code column} at or before {@code songline}, falling back to the first free track overall. */
	public int findNearTrackBySongLineAndColumn(int songline, int column, byte[] used) {
		for (int j = songline; j >= 0; j--) {
			if (songGo[j] >= 0) {
				continue;
			}
			int t = song[j][column];
			if (t >= 0) {
				for (int k = t + 1; k < Tracks.TRACKSNUM; k++) {
					if (used[k] == 0) {
						return k;
					}
				}
				// Because it did not find any behind it, try looking in front of it instead
				for (int k = t - 1; k >= 0; k--) {
					if (used[k] == 0) {
						return k;
					}
				}
			}
		}
		// Search for the first one usable from the beginning
		for (int k = 0; k < Tracks.TRACKSNUM; k++) {
			if (used[k] == 0) {
				return k;
			}
		}
		return -1;
	}

	/**
	 * Advances the song's play line, following a goto line if one is set.
	 * Always returns {@code true} (C++'s {@code BOOL} return is unconditional
	 * in every branch of the original).
	 *
	 * <p>Omits C++'s {@code m_pokeyStream}-consulting "song is done" check -
	 * {@link PokeyStream} isn't ported, and {@code m_pokeyStream} is always
	 * null in every existing CSong test.
	 */
	public boolean songPlayNextLine() {
		trackPlayLine = 0; // first track pattern line

		// Normal play, play from current position, or play from bookmark => shift to the next line
		if (playMode == PlayMode.PLAY_SONG || playMode == PlayMode.PLAY_FROM || playMode == PlayMode.PLAY_BOOKMARK) {
			songPlayLine++;
			if (songPlayLine > 255) {
				songPlayLine = 0;
			}
		}

		// When a goto line is encountered, jump right to the defined line and continue playback from that position
		if (songGo[songPlayLine] >= 0) {
			songPlayLine = songGo[songPlayLine];
		}

		return true;
	}

	/** Encodes {@code song}/{@code songGo} into the Atari module's song-data byte format, returning the number of bytes actually used. */
	public int songToAta(byte[] dest, int max, int adr, int tracks4_8) {
		int len = 0;
		for (int sline = 0; sline < SONGLEN; sline++) {
			int apos = sline * tracks4_8;
			if (apos + tracks4_8 > max) {
				return len; // buffer overflow
			}

			int go = songGo[sline];
			if (go >= 0) {
				// There is a goto line
				dest[apos] = (byte) 254; // go command
				dest[apos + 1] = (byte) go; // number where to jump
				int goadr = (adr + go * tracks4_8) & 0xFFFF;
				dest[apos + 2] = (byte) (goadr & 0xff); // low byte
				dest[apos + 3] = (byte) (goadr >> 8); // high byte
				if (tracks4_8 > 4) {
					for (int j = 4; j < tracks4_8; j++) {
						dest[apos + j] = (byte) 255; // to make sure this is the correct line
					}
				}
				len = sline * tracks4_8 + 4; // this is the end for now (goto has 4 bytes for 8 tracks)
			} else {
				// There are track numbers
				for (int i = 0; i < tracks4_8; i++) {
					int j = song[sline][i];
					if (j >= 0 && j < Tracks.TRACKSNUM) {
						dest[apos + i] = (byte) j;
						len = (sline + 1) * tracks4_8; // this is the end for now
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
	public boolean ataToSong(byte[] sour, int len, int adr, int tracks4_8) {
		int i = 0;
		int col = 0;
		int line = 0;
		while (i < len) {
			int b = unsignedByte(sour, i);
			// C++ also checks "b >= 0", tautological for its unsigned char b.
			if (b < Tracks.TRACKSNUM) {
				song[line][col] = b;
			} else if (b == 254 && col == 0) {
				int ptr = unsignedByte(sour, i + 2) | (unsignedByte(sour, i + 3) << 8); // goto vector
				int go = (ptr - adr) / tracks4_8;
				if (go >= 0 && go < (len / tracks4_8) && go < SONGLEN) {
					songGo[line] = go;
				} else {
					songGo[line] = 0; // place of invalid jump and jump to line 0
				}
				i += tracks4_8;
				if (i >= len) {
					return true; // this is the end of goto
				}
				line++;
				if (line >= SONGLEN) {
					return true;
				}
				continue;
			} else {
				song[line][col] = -1;
			}

			col++;
			if (col >= tracks4_8) {
				line++;
				if (line >= SONGLEN) {
					return true; // so that it does not overflow
				}
				col = 0;
			}
			i++;
		}
		return true;
	}

	public int[][] getSong() {
		return song;
	}

	public int[] getSongGo() {
		return songGo;
	}

	public Bookmark getBookmark() {
		return bookmark;
	}

	public void getSongInfoPars(SongInfo info) {
		System.arraycopy(songName, 0, info.songName, 0, SongInfo.SONG_NAME_MAX_LEN);
		info.speed = speed;
		info.mainSpeed = mainSpeed;
		info.instrumentSpeed = instrumentSpeed;
		info.songNameCursor = songNameCursor;
	}

	public void setSongInfoPars(SongInfo info) {
		System.arraycopy(info.songName, 0, songName, 0, SongInfo.SONG_NAME_MAX_LEN);
		speed = info.speed;
		mainSpeed = info.mainSpeed;
		instrumentSpeed = info.instrumentSpeed;
		songNameCursor = info.songNameCursor;
	}

	public boolean isValidSongline(int songline) {
		return songline >= 0 && songline < SONGLEN;
	}

	public boolean isSongGo(int songline) {
		return isValidSongline(songline) && songGo[songline] >= 0;
	}

	public int songGetTrack(int songline, int trackcol) {
		return isValidSongline(songline) && !isSongGo(songline) ? song[songline][trackcol] : -1;
	}

	public int songGetGo() {
		return songGo[songActiveLine];
	}

	public int songGetGo(int songline) {
		return songGo[songline];
	}

	public int songGetActiveLine() {
		return songActiveLine;
	}

	public void songSetActiveLine(int line) {
		songActiveLine = line;
	}

	public int getActiveLine() {
		return trackActiveLine;
	}

	public void setActiveLine(int line) {
		trackActiveLine = line;
	}

	/**
	 * Allocate a snapshot of the current edit cursor for the given part - the position undo/redo restores on top of the data itself.
	 */
	public int[] getUECursor(Part part) {
		switch (part) {
		case PART_TRACKS:
			return new int[] { songActiveLine, trackActiveLine, trackActiveCol, trackActiveCur };
		case PART_SONG:
			return new int[] { songActiveLine, trackActiveCol };
		case PART_INSTRUMENTS: {
			Instrument in = instruments.getInstrument(activeInstr);
			return new int[] { activeInstr, in.activeEditSection.ordinal(), in.editEnvelopeX, in.editEnvelopeY, in.editParameterNr, in.editNoteTableCursorPos };
			// =in->activenam; It omits that any change in the cursor position in the name is not a reason for undo separation
		}
		case PART_INFO:
			return new int[] { infoAct.ordinal() };
		default:
			return null;
		}
	}

	/**
	 * Restore the edit cursor for the given part - the counterpart to {@link #getUECursor}.
	 *
	 * @param undo used only to record which part is now active, matching C++'s g_activepart global
	 */
	public void setUECursor(Part part, int[] cursor, Undo undo) {
		switch (part) {
		case PART_TRACKS:
			songActiveLine = cursor[0];
			trackActiveLine = cursor[1];
			trackActiveCol = cursor[2];
			trackActiveCur = cursor[3];
			undo.setActivePart(Part.PART_TRACKS);
			break;

		case PART_SONG:
			songActiveLine = cursor[0];
			trackActiveCol = cursor[1];
			undo.setActivePart(Part.PART_SONG);
			break;

		case PART_INSTRUMENTS:
			activeInstr = cursor[0];
			// the other parameters 1-5 are within the instrument, so it is not necessary to set
			undo.setActivePart(Part.PART_INSTRUMENTS);
			break;

		case PART_INFO:
			infoAct = EditArea.values()[cursor[0]];
			undo.setActivePart(Part.PART_INFO);
			break;

		default:
			// don't change the active part!
			break;
		}
	}

	public boolean uecursorIsEqual(int[] cursor1, int[] cursor2, Part part) {
		int len;
		switch (part) {
		case PART_TRACKS:
			len = 4;
			break;
		case PART_SONG:
			len = 2;
			break;
		case PART_INSTRUMENTS:
			len = 6;
			break;
		case PART_INFO:
			len = 1;
			break;
		default:
			return false;
		}
		for (int i = 0; i < len; i++) {
			if (cursor1[i] != cursor2[i]) {
				return false;
			}
		}
		return true;
	}

	/** No-op here - see class javadoc (no CTrackClipboard ported yet). */
	public void blockDeselect() {
	}

	public PlayMode getPlayMode() {
		return playMode;
	}

	public void setPlayMode(PlayMode mode) {
		playMode = mode;
	}

	public void setPlayPressedTonesSilence() {
		for (int t = 0; t < SONGTRACKS; t++) {
			playPtNote[t] = -1;
			playPtInstr[t] = -1;
			playPtVolume[t] = 0;
		}
	}

	/**
	 * Stop playback, if playing - a no-op otherwise (see class javadoc for why {@code undo} is a parameter, and what's omitted).
	 */
	public void stop(Undo undo) {
		if (getPlayMode() != PlayMode.PLAY_STOP) {
			setPlayMode(PlayMode.PLAY_STOP);
			undo.separator();
			quantizationNote = quantizationInstr = quantizationVol = -1;
			setPlayPressedTonesSilence();
		}
	}

	private static int unsignedByte(byte[] buf, int index) {
		return buf[index] & 0xFF;
	}
}
