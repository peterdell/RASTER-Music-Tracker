package com.wudsn.tools.rmt.model;

/**
 * Ported from CSong (src/cpp/Song.h, SongCore.cpp, SongEditing.cpp) - a
 * deliberately minimal slice, just what {@link Undo} needs (see
 * {@code plans/JAVA_PORT_PLAN.md} for the full scoping rationale). CSong
 * itself is a much larger class ("the God Object" per this project's own
 * characterization-testing notes); everything else - playback, file I/O,
 * the GUI-adjacent editing surface - stays deferred.
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
	private int trackActiveLine;
	private int trackActiveCol;
	private int trackActiveCur;

	private int activeInstr;
	private EditArea infoAct = EditArea.NAME;

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
}
