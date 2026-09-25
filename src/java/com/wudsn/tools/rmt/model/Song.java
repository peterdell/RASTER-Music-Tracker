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
 * {@link #songToAta}, {@link #ataToSong}, {@link #getSubsongParts},
 * {@link #markTfUsed}, {@link #trackLeft}, {@link #trackRight},
 * {@link #respectBoundaries}, {@link #getSmallestMaxtracklen}), matching
 * this project's established idiom for globals a ported method actually
 * needs. Likewise {@code g_keyboard_RememberOctavesAndVolumes} becomes an
 * explicit parameter on {@link #activeInstrSet}/{@link #activeInstrPrev}/
 * {@link #activeInstrNext}, matching {@link Instruments}'s own treatment
 * of the same setting.
 *
 * <p><b>{@code Tracks} joins {@code Instruments} as a stored
 * collaborator</b> (constructor parameter), needed by the first
 * {@code SongEditing.cpp} methods ported here
 * ({@link #markTfNoEmpty}/{@link #trackGetLoopingNoteInstrVol}/
 * {@link #getSmallestMaxtracklen}).
 *
 * <p><b>No {@code CPokeyStream} yet</b>: {@link #songPlayNextLine} omits
 * C++'s {@code m_pokeyStream}-consulting "song is done" check - it's always
 * null in every existing CSong test, and {@code PokeyStream} isn't ported.
 *
 * <p><b>No full {@code CTrackClipboard} yet</b>: {@link #blockDeselect} is a
 * documented no-op - C++'s version delegates to {@code g_TrackClipboard},
 * whose block-selection surface isn't ported. This only affects cosmetic
 * block-selection state after an undo/redo cursor jump, not the actual
 * data being restored. {@link #trackCopy}/{@link #trackPaste}/
 * {@link #trackCut} are the one exception - they only ever needed
 * {@code CTrackClipboard}'s single-track {@code m_trackcopy} slot, which
 * this class models directly as its own {@code trackCopyClipboard} field
 * rather than waiting on a full clipboard port (see that field's own
 * comment, and {@code plans/JAVA_SONGEDITING_PLAN.md}).
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
	private final Tracks tracks;

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
	private int octave;
	private int volume;

	private PlayMode playMode = PlayMode.PLAY_STOP;
	private int quantizationNote = -1;
	private int quantizationInstr = -1;
	private int quantizationVol = -1;

	private final int[] playPtNote = new int[SONGTRACKS];
	private final int[] playPtInstr = new int[SONGTRACKS];
	private final int[] playPtVolume = new int[SONGTRACKS];

	private final int[] songLineClipboard = new int[SONGTRACKS];
	private int songGoClipboard; // matches Song.h's default member initializer (0)

	private final Instrument instrClipboard = new Instrument();

	// A deliberately minimal slice of C++'s CTrackClipboard - just its
	// single-track m_trackcopy slot, needed by trackCopy/trackPaste/trackCut.
	// The rest of CTrackClipboard (block selection, BlockPaste, etc.) is a
	// separate, not-yet-ported class - see plans/JAVA_SONGEDITING_PLAN.md.
	private final Track trackCopyClipboard = new Track();

	public Song(Instruments instruments, Tracks tracks) {
		this.instruments = instruments;
		this.tracks = tracks;
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

	public int songGetActiveTrack() {
		return songGo[songActiveLine] >= 0 ? -1 : song[songActiveLine][trackActiveCol];
	}

	/** {@code count} is the method's own {@code int} return value; {@code parts} was C++'s {@code CString&} output parameter. {@code count} always equals the number of space-separated tokens in {@code parts}. */
	public record SubsongParts(int count, String parts) {
	}

	/** Finds each subsong (a run of songlines reachable via goto chains) that contains at least one used track, returning the songline each one starts at. */
	public SubsongParts getSubsongParts(int tracks4_8) {
		int[] songp = new int[SONGLEN];
		int lastgo = -1;
		for (int i = 0; i < SONGLEN; i++) {
			songp[i] = -1;
			if (songGo[i] >= 0) {
				lastgo = i;
			}
		}

		StringBuilder result = new StringBuilder();
		int asub = 0;
		boolean ok = false;

		for (int i = 0; i <= lastgo; i++) {
			if (songp[i] < 0) {
				int apos = i;
				while (songp[apos] < 0) {
					int n = songGo[apos];
					songp[apos] = asub;
					if (n >= 0) {
						apos = n;
					} else {
						if (!ok) {
							for (int j = 0; j < tracks4_8; j++) {
								if (song[apos][j] >= 0) {
									result.append(String.format("%02X ", apos));
									ok = true;
									break;
								}
							}
						}
						apos++;
						if (apos >= SONGLEN) {
							break;
						}
					}
				}
				if (ok) {
					asub++;
				}
				ok = false;
			}
		}
		return new SubsongParts(asub, result.toString());
	}

	/** Marks every track referenced by a non-goto songline as {@link TrackFlag#TF_USED}. */
	public void markTfUsed(byte[] used, int tracks4_8) {
		for (int i = 0; i < SONGLEN; i++) {
			if (songGo[i] < 0) {
				for (int channelNr = 0; channelNr < tracks4_8; channelNr++) {
					int tr = song[i][channelNr];
					if (tr >= 0 && tr < Tracks.TRACKSNUM) {
						used[tr] = TrackFlag.TF_USED;
					}
				}
			}
		}
	}

	/** ORs {@link TrackFlag#TF_NOEMPTY} onto every track with real data, regardless of whether it's referenced by the song. */
	public void markTfNoEmpty(byte[] used) {
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (tracks.calculateNotEmpty(i)) {
				used[i] |= TrackFlag.TF_NOEMPTY;
			}
		}
	}

	public void activeInstrSet(int instr, boolean rememberOctavesAndVolumes) {
		instruments.memorizeOctaveAndVolume(activeInstr, octave, volume, rememberOctavesAndVolumes);
		activeInstr = instr;
		Instruments.OctaveAndVolume ov = instruments.rememberOctaveAndVolume(activeInstr, octave, volume, rememberOctavesAndVolumes);
		octave = ov.octave();
		volume = ov.volume();
	}

	public void activeInstrPrev(Undo undo, boolean rememberOctavesAndVolumes) {
		undo.separator();
		activeInstrSet((activeInstr - 1) & 0x3f, rememberOctavesAndVolumes);
	}

	public void activeInstrNext(Undo undo, boolean rememberOctavesAndVolumes) {
		undo.separator();
		activeInstrSet((activeInstr + 1) & 0x3f, rememberOctavesAndVolumes);
	}

	/**
	 * Moves the track cursor left, wrapping the column at 0. C++'s always-true
	 * {@code BOOL} return is dropped - no test depends on it.
	 *
	 * @param column when true, skip the sub-column cursor and move the column directly (matching C++'s goto-driven fallthrough)
	 */
	public void trackLeft(boolean column, int tracks4_8, Undo undo) {
		undo.separator();
		boolean wrapColumn;
		if (column) {
			wrapColumn = true;
		} else {
			trackActiveCur--;
			if (trackActiveCur < 0) {
				trackActiveCur = 3; // previous speed column
				wrapColumn = true;
			} else {
				wrapColumn = false;
			}
		}
		if (wrapColumn) {
			trackActiveCol--;
			if (trackActiveCol < 0) {
				trackActiveCol = tracks4_8 - 1;
			}
		}
	}

	/** Moves the track cursor right, wrapping the column at {@code tracks4_8}. See {@link #trackLeft} for the {@code column} parameter and the dropped return value. */
	public void trackRight(boolean column, int tracks4_8, Undo undo) {
		undo.separator();
		boolean wrapColumn;
		if (column) {
			wrapColumn = true;
		} else {
			trackActiveCur++;
			if (trackActiveCur > 3) { // speed column
				trackActiveCur = 0;
				wrapColumn = true;
			} else {
				wrapColumn = false;
			}
		}
		if (wrapColumn) {
			trackActiveCol++;
			if (trackActiveCol >= tracks4_8) {
				trackActiveCol = 0;
			}
		}
	}

	/** Clamps the active song/track line back into bounds - e.g. after {@code tracks4_8} shrinks a track's effective length. */
	public void respectBoundaries(int tracks4_8) {
		int songline = songGetActiveLine();
		if (songline > SONGLEN) {
			songline = SONGLEN - 1;
		}
		if (songline < 0) {
			songline = 0;
		}

		int length = getSmallestMaxtracklen(songline, tracks4_8);
		int line = getActiveLine();
		if (line > length) {
			line = length - 1;
		}
		if (line < 0) {
			line = 0;
		}

		setActiveLine(line);
		songSetActiveLine(songline);
	}

	/** The shortest length among the tracks used on {@code songline} (0 for a goto line). Pulled forward from the not-yet-ported {@code GetEffectiveMaxtracklen}/{@code ChangeMaxtracklen} batch since {@link #respectBoundaries} needs it. */
	public int getSmallestMaxtracklen(int songline, int tracks4_8) {
		int max = 256;
		int min = tracks.getMaxTrackLength();
		int p = 0;

		if (songGo[songline] >= 0) {
			return 0; // goto line is ignored
		}

		for (int i = 0; i < tracks4_8; i++) {
			int t = song[songline][i];
			int m = tracks.getLength(t);
			if (m < 0) {
				continue;
			}
			if (m < max) {
				max = m;
			}
			p++;
		}
		if (p == 0) {
			return min; // cannot be from empty tracks
		}

		if (min < max) {
			max = min;
		}

		return max;
	}

	/** C++'s {@code int& note, int& instr, int& vol} output parameters. */
	public record NoteInstrVol(int note, int instr, int vol) {
	}

	/** The note/instrument/volume the track cursor would see at {@code track}'s current playback position, following a loop point if the cursor is past the track's own length. */
	public NoteInstrVol trackGetLoopingNoteInstrVol(int track) {
		int len = tracks.getLastLine(track) + 1;
		int go = tracks.getGoLine(track);
		int line;
		if (trackActiveLine < len) {
			line = trackActiveLine;
		} else {
			int loop = (go - len) + go;
			if (go >= 0 && loop != 0) {
				line = (trackActiveLine - len) % loop;
			} else {
				return new NoteInstrVol(-1, -1, -1);
			}
		}
		return new NoteInstrVol(tracks.getNote(track, line), tracks.getInstr(track, line), tracks.getVol(track, line));
	}

	public void songTrackSet(int t, Undo undo) {
		if (t >= -1 && t < Tracks.TRACKSNUM) {
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGTRACK);
			song[songActiveLine][trackActiveCol] = t;
		}
	}

	public void songTrackSetByNum(int num, Undo undo) {
		if (songGo[songActiveLine] < 0) {
			// Changes track
			int i = songGetActiveTrack();
			if (i < 0) {
				i = 0;
			}
			i &= 0x0f; // just the lower digit
			i = (i << 4) | num;
			if (i >= Tracks.TRACKSNUM) {
				i &= 0x0f;
			}
			songTrackSet(i, undo);
		} else {
			// Changes GO parameter
			int i = songGo[songActiveLine];
			if (i < 0) {
				i = 0;
			}
			i &= 0x0f; // just the lower digit
			i = (i << 4) | num;
			if (i >= SONGLEN) {
				i &= 0x0f;
			}
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGGO);
			songGo[songActiveLine] = i;
		}
	}

	public void songTrackDec(Undo undo) {
		if (songGo[songActiveLine] < 0) {
			int t = song[songActiveLine][trackActiveCol] - 1;
			if (t < -1) {
				t = Tracks.TRACKSNUM - 1;
			}
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGTRACK);
			song[songActiveLine][trackActiveCol] = t;
		} else {
			int g = songGo[songActiveLine] - 1;
			if (g < 0) {
				g = SONGLEN - 1;
			}
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGGO);
			songGo[songActiveLine] = g;
		}
	}

	public void songTrackInc(Undo undo) {
		if (songGo[songActiveLine] < 0) {
			int t = song[songActiveLine][trackActiveCol] + 1;
			if (t >= Tracks.TRACKSNUM) {
				t = -1;
			}
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGTRACK);
			song[songActiveLine][trackActiveCol] = t;
		} else {
			int g = songGo[songActiveLine] + 1;
			if (g >= SONGLEN) {
				g = 0;
			}
			undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGGO);
			songGo[songActiveLine] = g;
		}
	}

	public void songTrackEmpty(Undo undo) {
		undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGTRACK);
		song[songActiveLine][trackActiveCol] = -1;
	}

	public void songTrackGoOnOff(Undo undo) {
		undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGGO);
		songGo[songActiveLine] = songGo[songActiveLine] < 0 ? 0 : -1;
	}

	/**
	 * C++'s output-parameter struct {@code TInstrInfo}. Mutable and populated
	 * in place (like {@link SongInfo}) rather than an immutable record,
	 * matching {@link #instrInfo}'s "leave it untouched for invalid input"
	 * contract - the same reason {@link TrackInfo} is shaped this way.
	 */
	public static final class InstrInfo {
		public int count;
		public int usedInTracks;
		public int instrFrom;
		public int instrTo;
		public int minNote;
		public int maxNote;
		public int minVol;
		public int maxVol;
	}

	public void instrInfo(InstrInfo info, int instr) {
		instrInfo(info, instr, -1);
	}

	/**
	 * Populates {@code info} with usage statistics for {@code instr}. A
	 * no-op (leaving {@code info} untouched) if {@code instr} isn't a valid
	 * instrument number.
	 *
	 * <p>Only the {@code iinfo != NULL} branch of C++'s dual-mode
	 * {@code InstrInfo} is ported - the {@code iinfo == NULL} branch builds
	 * and shows a {@code MessageBox} summary, untested and with no Java UI
	 * to show it in (see {@code plans/DUAL_MODE_PATTERN_PLAN.md}).
	 */
	public void instrInfo(InstrInfo info, int instr, int instrto) {
		if (!instruments.isValidInstrument(instr)) {
			return;
		}

		if (instrto < instr) {
			instrto = instr;
		}

		int noftrack = 0;
		int globallytimes = 0;
		int minnote = Notes.NOTESNUM;
		int maxnote = -1;
		int minvol = 16;
		int maxvol = -1;
		int infrom = Instruments.INSTRSNUM;
		int into = -1;

		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			boolean inttrack = false;
			Track at = tracks.getTrack(i);
			int ain = -1;
			for (int j = 0; j < at.len; j++) {
				if (at.instr[j] >= 0) {
					ain = at.instr[j];
				}
				if (ain >= instr && ain <= instrto) {
					inttrack = true;
					if (ain > into) {
						into = ain;
					}
					if (ain < infrom) {
						infrom = ain;
					}
					int note = at.note[j];
					if (note >= 0 && note < Notes.NOTESNUM) {
						globallytimes++; // some note with this instrument => started
						if (note > maxnote) {
							maxnote = note;
						}
						if (note < minnote) {
							minnote = note;
						}
					}
					int vol = at.volume[j];
					if (vol >= 0 && vol <= 15) {
						if (vol > maxvol) {
							maxvol = vol;
						}
						if (vol < minvol) {
							minvol = vol;
						}
					}
				}
			}
			if (inttrack) {
				noftrack++;
			}
		}

		info.count = globallytimes;
		info.usedInTracks = noftrack;
		info.instrFrom = infrom;
		info.instrTo = into;
		info.minNote = minnote;
		info.maxNote = maxnote;
		info.minVol = minvol;
		info.maxVol = maxvol;
	}

	/** C++'s input struct {@code TInstrChangeParams} - mirrors {@code CInstrumentChangeDlg}'s fields 1:1. */
	public static final class InstrChangeParams {
		public int snotefrom, snoteto, svolmin, svolmax;
		public int sinstrfrom, sinstrto;
		public int dnotefrom, dnoteto, dvolmin, dvolmax;
		public int dinstrfrom, dinstrto;
		public int onlytrack;
		public int onlychannels;
		public int onlysonglinefrom, onlysonglineto;
	}

	/**
	 * Extracted from C++'s {@code CSong::InstrChange()} (the real
	 * {@code CInstrumentChangeDlg} wrapper, not ported): the
	 * dialog-independent instrument-remap work, once its 16 dialog-derived
	 * parameters are known. Dual-mode like {@link #instrInfo}/
	 * {@link #trackInfo}, except C++'s {@code CString* resultMsg} output
	 * parameter (non-null in every test) simply becomes this method's
	 * return value, since the {@code resultMsg == NULL} branch (show a
	 * {@code MessageBox}) isn't ported for the same reason as
	 * {@link #instrInfo}'s.
	 *
	 * @return a human-readable summary of what changed
	 */
	public String instrChangeApply(InstrChangeParams p, Undo undo, int tracks4_8) {
		StringBuilder s = new StringBuilder();

		stop(undo); // Stop playing before processing further

		// Hide all tracks and the whole song
		undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, -1);
		undo.changeSong(0, 0, UndoType.UETYPE_SONGDATA, 1);

		int snotefrom = p.snotefrom;
		int snoteto = p.snoteto;
		int svolmin = p.svolmin;
		int svolmax = p.svolmax;
		int sinstrfrom = p.sinstrfrom;
		int sinstrto = p.sinstrto;
		int dnotefrom = p.dnotefrom;
		int dnoteto = p.dnoteto;
		int dvolmin = p.dvolmin;
		int dvolmax = p.dvolmax;
		int dinstrfrom = p.dinstrfrom;
		int dinstrto = p.dinstrto;
		int onlytrack = p.onlytrack;
		int onlychannels = p.onlychannels;
		int onlysonglinefrom = p.onlysonglinefrom;
		int onlysonglineto = p.onlysonglineto;

		byte[] trackYn = new byte[Tracks.TRACKSNUM]; // 1 = yes, 2 = no, 3 = yesno (copy)
		int[] trackColumn = new int[Tracks.TRACKSNUM]; // The first occurrence in the selected area of the song
		int[] trackLine = new int[Tracks.TRACKSNUM]; // The first occurrence in the selected area of the song
		int[] trackChangeTo = new int[Tracks.TRACKSNUM]; // Changed tracks to replace in song
		java.util.Arrays.fill(trackColumn, -1);
		java.util.Arrays.fill(trackLine, -1);

		boolean onlysomething = false; // Only apply changes to specific things
		int trackcreated = 0; // Number of newly created tracks
		int songchanges = 0; // Number of changes in the song
		boolean error = false;

		if (onlychannels >= 0 || (onlysonglinefrom >= 0 && onlysonglineto >= 0)) {
			if (onlychannels <= 0) {
				onlychannels = 0xff; // All channels
			}
			if (onlysonglinefrom < 0) {
				onlysonglinefrom = 0; // From the beginning
			}
			if (onlysonglineto < 0) {
				onlysonglineto = SONGLEN - 1; // To the end
			}
			onlysomething = true; // Something specific to change

			for (int j = 0; j < SONGLEN; j++) {
				if (isSongGo(j)) {
					continue;
				}
				for (int i = 0; i < tracks4_8; i++) {
					int t = song[j][i];
					if (!tracks.isValidTrack(t)) {
						continue;
					}
					boolean r = (onlychannels & (1 << i)) != 0 && j >= onlysonglinefrom && j <= onlysonglineto;
					trackYn[t] |= (byte) (r ? 1 : 2);

					// The first occurrence in the selected area of the song
					if (r && trackColumn[t] < 0) {
						trackColumn[t] = i;
						trackLine[t] = j;
					}
				}
			}
		} else if (onlytrack >= 0) {
			trackYn[onlytrack] = 1; // 1 = yes
			onlysomething = true;
		}

		if (!tracks.isValidNote(dnoteto)) {
			dnoteto = dnotefrom + (snoteto - snotefrom);
		}
		if (!tracks.isValidVolume(dvolmax)) {
			dvolmax = dvolmin + (svolmax - svolmin);
		}
		if (!tracks.isValidInstrument(dinstrto)) {
			dinstrto = dinstrfrom + (sinstrto - sinstrfrom);
		}

		double notecoef = (snoteto - snotefrom > 0) ? (double) (dnoteto - dnotefrom) / (snoteto - snotefrom) : 0;
		double volcoef = (svolmax - svolmin > 0) ? (double) (dvolmax - dvolmin) / (svolmax - svolmin) : 0;
		double instrcoef = (sinstrto - sinstrfrom > 0) ? (double) (dinstrto - dinstrfrom) / (sinstrto - sinstrfrom) : 0;

		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			trackChangeTo[i] = -1; // initialise

			// It wants to change only some and this one is not
			if (onlysomething && (trackYn[i] & 1) != 1) {
				continue;
			}

			// Copy the original track to a temporary track
			Track st = tracks.getTrack(i);
			Track at = new Track();
			at.copyFrom(st);

			boolean changes = false;
			int lasti = -1;
			int lastn = -1;

			for (int j = 0; j < at.len; j++) {
				if (tracks.isValidInstrument(at.instr[j])) {
					lasti = at.instr[j];
				}
				if (tracks.isValidNote(at.note[j])) {
					lastn = at.note[j];
				}

				if (lasti >= sinstrfrom && lasti <= sinstrto && lastn >= snotefrom && lastn <= snoteto && at.volume[j] >= svolmin && at.volume[j] <= svolmax) {
					if (tracks.isValidNote(at.note[j])) {
						int note = dnotefrom + (int) ((double) (at.note[j] - snotefrom) * notecoef + 0.5);
						while (!tracks.isValidNote(note)) {
							note -= 12;
						}
						if (note != at.note[j]) {
							at.note[j] = note;
							changes = true;
						}
					}

					if (tracks.isValidInstrument(at.instr[j])) {
						int ins = dinstrfrom + (int) ((double) (at.instr[j] - sinstrfrom) * instrcoef + 0.5);
						if (!tracks.isValidInstrument(ins)) {
							ins = Instruments.INSTRSNUM - 1;
						}
						if (ins != at.instr[j]) {
							at.instr[j] = ins;
							changes = true;
						}
					}

					if (tracks.isValidVolume(at.volume[j])) {
						int vol = dvolmin + (int) ((double) (at.volume[j] - svolmin) * volcoef + 0.5);
						if (!tracks.isValidVolume(vol)) {
							vol = Tracks.MAXVOLUME;
						}
						if (vol != at.volume[j]) {
							at.volume[j] = vol;
							changes = true;
						}
					}
				}
			}

			// There was something changed
			if (changes) {
				// Create a new track if the track occurs both inside and outside the area
				if ((trackYn[i] & 2) != 0) {
					byte[] used = new byte[Tracks.TRACKSNUM];
					markTfUsed(used, tracks4_8);
					markTfNoEmpty(used);
					int k = findNearTrackBySongLineAndColumn(trackLine[i], trackColumn[i], used);

					// The process is aborted if there is no unused track available
					if (k < 0) {
						error = true;
						s.append("There aren't any more empty unused tracks in song, further changes could not be applied!\n\n");
						s.append(String.format("Process halted in Track %02X, in Channel %d\n\n", trackLine[i], trackColumn[i]));
						break; // matches C++'s "goto abortchanges" - also skips the "subsequent changes" loop below
					}

					// Copy the changed track (at) to the new track
					Track nt = tracks.getTrack(k);
					nt.copyFrom(at);

					trackcreated++;

					// Put it in the song at least once (due to the search in the song used tracks)
					song[trackLine[i]][trackColumn[i]] = k;
					songchanges++;

					// Will change all occurrences
					trackChangeTo[i] = k;
				} else {
					// Copy the changed track (at) back to the original track
					st.copyFrom(at);
				}
			}
		}

		// Subsequent changes in the song - skipped entirely if the loop above aborted on an error
		if (onlysomething && !error) {
			for (int j = 0; j < SONGLEN; j++) {
				if (isSongGo(j)) {
					continue;
				}
				for (int i = 0; i < tracks4_8; i++) {
					int t = song[j][i];
					if (!tracks.isValidTrack(t)) {
						continue;
					}
					boolean r = (onlychannels & (1 << i)) != 0 && j >= onlysonglinefrom && j <= onlysonglineto;
					if (r && trackChangeTo[t] >= 0) {
						song[j][i] = trackChangeTo[t];
						songchanges++;
					}
				}
			}
		}

		s.append("Instrument changes were applied ");
		s.append(error ? "with errors, beware of data loss!\n\n" : "successfully!\n\n");

		if (trackcreated > 0 || songchanges > 0) {
			s.append("Additional actions were also performed to accommodate the chosen parameters:\n\n");
			s.append(String.format("New tracks created: %d\n", trackcreated));
			s.append(String.format("Total changes in song: %d\n", songchanges));
		}

		return s.toString();
	}

	/** C++'s output-parameter struct {@code TTrackInfo}. See {@link InstrInfo} for why this is a mutable class rather than a record. */
	public static final class TrackInfo {
		public int count;
		public int lines;
		public final int[] usedInColumn = new int[SONGTRACKS];
	}

	/**
	 * Populates {@code info} with usage statistics for {@code track}. A
	 * no-op (leaving {@code info} untouched) if {@code track} is out of
	 * range. Only the {@code tinfo != NULL} branch of C++'s dual-mode
	 * {@code TrackInfo} is ported - see {@link #instrInfo}'s javadoc for why.
	 */
	public void trackInfo(int track, TrackInfo info, int tracks4_8) {
		if (track < 0 || track >= Tracks.TRACKSNUM) {
			return;
		}

		int[] trackUsedInColumn = new int[SONGTRACKS];
		int lines = 0;
		int total = 0;

		for (int sline = 0; sline < SONGLEN; sline++) {
			if (songGo[sline] >= 0) {
				continue; // goto line is ignored
			}

			boolean thisline = false;
			for (int ch = 0; ch < tracks4_8; ch++) {
				int n = song[sline][ch];
				if (n == track) {
					trackUsedInColumn[ch]++;
					total++;
					thisline = true;
				}
			}

			if (thisline) {
				lines++;
			}
		}

		info.count = total;
		info.lines = lines;
		System.arraycopy(trackUsedInColumn, 0, info.usedInColumn, 0, SONGTRACKS);
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

	public void clearBookmark() {
		bookmark.songline = bookmark.trackline = bookmark.speed = -1;
	}

	public boolean isBookmark() {
		return bookmark.speed > 0 && bookmark.trackline < tracks.getMaxTrackLength();
	}

	/** Inserts a blank songline at {@code line}, shifting {@code line} and everything after it down by one (the last line falls off the end). */
	public void songInsertLine(int line, Undo undo, int tracks4_8) {
		undo.changeSong(line, trackActiveCol, UndoType.UETYPE_SONGDATA, 0);

		for (int i = SONGLEN - 2; i >= line; i--) {
			for (int j = 0; j < tracks4_8; j++) {
				song[i + 1][j] = song[i][j];
			}
			int go = songGo[i];
			if (go > 0 && go >= line) {
				go++;
			}
			songGo[i + 1] = go;
		}
		for (int j = 0; j < tracks4_8; j++) {
			song[line][j] = -1;
		}
		songGo[line] = -1;
		for (int i = 0; i < line; i++) {
			if (songGo[i] >= line) {
				songGo[i]++;
			}
		}
		if (isBookmark() && bookmark.songline >= line) {
			bookmark.songline++;
			if (bookmark.songline >= SONGLEN) {
				clearBookmark(); // just pushed the bookmark out of the song => cancel the bookmark
			}
		}
	}

	/** Removes songline {@code line}, shifting everything after it up by one (a fresh blank line appears at the end). */
	public void songDeleteLine(int line, Undo undo, int tracks4_8) {
		undo.changeSong(line, trackActiveCol, UndoType.UETYPE_SONGDATA, 0);

		for (int i = line; i < SONGLEN - 1; i++) {
			for (int j = 0; j < tracks4_8; j++) {
				song[i][j] = song[i + 1][j];
			}
			int go = songGo[i + 1];
			if (go > 0 && go > line) {
				go--;
			}
			songGo[i] = go;
		}
		for (int i = 0; i < line; i++) {
			if (songGo[i] > line) {
				songGo[i]--;
			}
		}
		for (int j = 0; j < tracks4_8; j++) {
			song[SONGLEN - 1][j] = -1;
		}
		songGo[SONGLEN - 1] = -1;
		if (isBookmark() && bookmark.songline >= line) {
			bookmark.songline--;
			if (bookmark.songline < line) {
				clearBookmark(); // just deleted the songline with the bookmark
			}
		}
	}

	public void songCopyLine(int tracks4_8) {
		for (int i = 0; i < tracks4_8; i++) {
			songLineClipboard[i] = song[songActiveLine][i];
		}
		songGoClipboard = songGo[songActiveLine];
	}

	public void songPasteLine(Undo undo, int tracks4_8) {
		if (songGoClipboard < -1) {
			return;
		}
		undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGDATA);
		for (int i = 0; i < tracks4_8; i++) {
			song[songActiveLine][i] = songLineClipboard[i];
		}
		songGo[songActiveLine] = songGoClipboard;
	}

	public void songClearLine(Undo undo, int tracks4_8) {
		undo.changeSong(songActiveLine, trackActiveCol, UndoType.UETYPE_SONGDATA);
		for (int i = 0; i < tracks4_8; i++) {
			song[songActiveLine][i] = -1;
		}
		songGo[songActiveLine] = -1;
	}

	/**
	 * Extracted from C++'s {@code CSong::SongInsertCopyOrCloneOfSongLines()}
	 * (the real dialog wrapper, not ported): inserts copies (or, if
	 * {@code clone} is set, cloned tracks) of songlines {@code [linefrom,
	 * lineto]} starting at {@code line}.
	 *
	 * <p>C++'s two guard-only error paths (song-range overrun; ran out of
	 * unused tracks to clone into) return {@code false} here too, but the
	 * {@code SendErrorMessage} call itself isn't reproduced - {@link Song}
	 * holds no {@link Messages} reference, and no test reaches either path.
	 * C++'s {@code int& line} parameter is never actually reassigned in the
	 * method body, so it's a plain {@code int} here.
	 */
	public boolean songInsertCopyOrCloneOfSongLinesApply(int line, int linefrom, int lineto, boolean clone, int tuning, int volumep, Undo undo, int tracks4_8) {
		blockDeselect(); // the block is deselected only if it is OK

		byte[] used = new byte[Tracks.TRACKSNUM];
		markTfUsed(used, tracks4_8);
		markTfNoEmpty(used);
		int[] clonedTo = new int[Tracks.TRACKSNUM];
		java.util.Arrays.fill(clonedTo, -1);

		for (int i = linefrom; i <= lineto; i++) {
			int n = i - linefrom;
			int sou = i;
			int des = line + n;
			boolean diss = des <= sou;
			if (diss) {
				sou += n;
			}
			boolean sngo = false;
			if (sou < SONGLEN) {
				sngo = songGo[sou] >= 0;
			}

			if (diss) {
				sou++;
			}
			if (sou < 0 || sou >= SONGLEN || des < 0 || des >= SONGLEN) {
				return false; // guard-only: song-range overrun
			}

			songInsertLine(des, undo, tracks4_8); // inserted blank line

			if (clone && !sngo) {
				undo.separator(-1); // associates the previous insert lines to the next change
				undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, 1); // with separator

				for (int j = 0; j < tracks4_8; j++) {
					int k = song[sou][j]; // original track
					int d;
					if (k < 0) {
						continue; // is there --
					}
					if (clonedTo[k] >= 0) {
						d = clonedTo[k]; // this one has already been cloned, so it will also use it
					} else {
						d = findNearTrackBySongLineAndColumn(sou, j, used);
						if (d >= 0) {
							used[d] = TrackFlag.TF_USED;
							clonedTo[k] = d;
							trackCopyFromTo(k, d);
							// Edit cloned track according to tuning and volumep
							tracks.modifyTrack(tracks.getTrack(d), 0, Track.TRACKLEN - 1, -1, tuning, 0, volumep);
						} else {
							return false; // guard-only: out of unused empty tracks
						}
					}
					song[des][j] = d;
				}
			} else {
				// Copies
				songGo[des] = songGo[sou];
				for (int j = 0; j < tracks4_8; j++) {
					song[des][j] = song[sou][j];
				}
			}
		}

		return true;
	}

	public void trackCopy() {
		Track at = tracks.getTrack(songGetActiveTrack());
		if (at != null) {
			trackCopyClipboard.copyFrom(at);
		}
	}

	/**
	 * Reads from the deliberately minimal {@code trackCopyClipboard} slot
	 * (see the field's own comment) - a separate mechanism from
	 * {@code BlockPaste}'s block-selection clipboard, which isn't ported.
	 */
	public void trackPaste() {
		Track at = tracks.getTrack(songGetActiveTrack());
		if (at != null && tracks.isValidLength(trackCopyClipboard.len)) {
			at.copyFrom(trackCopyClipboard);
		}
	}

	public void trackDelete() {
		tracks.clearTrack(songGetActiveTrack());
	}

	public void trackCut() {
		trackCopy();
		trackDelete();
	}

	public void trackCopyFromTo(int fromtrack, int totrack) {
		Track at = tracks.getTrack(fromtrack);
		Track tot = tracks.getTrack(totrack);
		if (at != null && tot != null) {
			tot.copyFrom(at);
		}
	}

	public void trackSwapFromTo(int fromtrack, int totrack) {
		Track at = tracks.getTrack(fromtrack);
		Track tot = tracks.getTrack(totrack);
		if (at != null && tot != null) {
			Track buf = new Track();
			buf.copyFrom(tot);
			tot.copyFrom(at);
			at.copyFrom(buf);
		}
	}

	public void instrCopy() {
		instrClipboard.copyFrom(instruments.getInstrument(getActiveInstr()));
	}

	public void instrCut() {
		instrCopy();
		instrDelete();
	}

	public void instrDelete() {
		instruments.clearInstrument(getActiveInstr());
	}

	/** The largest "shortest track length on a songline" across the whole song (ignoring goto lines and songlines with no valid tracks). */
	public int getEffectiveMaxtracklen(int tracks4_8) {
		int max = 1;
		for (int so = 0; so < SONGLEN; so++) {
			if (songGo[so] >= 0) {
				continue; // goto line is ignored
			}
			int min = tracks.getMaxTrackLength();
			int p = 0;
			for (int i = 0; i < tracks4_8; i++) {
				int t = song[so][i];
				int m = tracks.getLength(t);
				if (m < 0) {
					continue;
				}
				p++;
				if (m < min) {
					min = m;
				}
			}
			// min = the shortest track length on this songline
			if (p > 0 && min > max) {
				max = min;
			}
		}
		return max;
	}

	/** Shortens every track at or beyond {@code maxtracklen}, cancelling its loop if it had one, then lowers the shared max track length. A no-op if {@code maxtracklen} isn't a valid track length. */
	public void changeMaxtracklen(int maxtracklen) {
		if (!tracks.isValidLength(maxtracklen)) {
			return;
		}

		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			Track tt = tracks.getTrack(i);
			// Clear
			for (int j = tt.len; j < Track.TRACKLEN; j++) {
				tt.note[j] = tt.instr[j] = tt.volume[j] = tt.speed[j] = -1;
			}
			if (tt.len >= maxtracklen) {
				tt.go = -1; // cancel GO
				tt.len = maxtracklen; // adjust length
			}
		}

		tracks.setMaxTrackLength(maxtracklen);
	}

	/** C++'s {@code int& clearedtracks, int& truncatedtracks, int& truncatedbeats} output parameters. */
	public record ClearUnusedResult(int clearedTracks, int truncatedTracks, int truncatedBeats) {
	}

	/** Truncates every track down to the length it's actually used at across the song, then deletes any track not referenced by the song at all. */
	public ClearUnusedResult songClearUnusedTracksAndParts(int tracks4_8) {
		int ttracks = 0;
		int tbeats = 0;
		int ctracks = 0;
		int[] tracklen = new int[Tracks.TRACKSNUM];
		boolean[] trackused = new boolean[Tracks.TRACKSNUM];
		java.util.Arrays.fill(tracklen, -1);

		for (int sline = 0; sline < SONGLEN; sline++) {
			if (isSongGo(sline)) {
				continue; // goto line is ignored
			}

			int nejkratsi = tracks.getMaxTrackLength();

			for (int ch = 0; ch < tracks4_8; ch++) {
				int n = song[sline][ch];
				if (!tracks.isValidTrack(n)) {
					continue; // invalid track is ignored
				}
				trackused[n] = true;
				Track tr = tracks.getTrack(n);
				if (tracks.isValidGo(tr.go)) {
					continue; // there is a loop => it has a maximum length
				}
				if (tr.len < nejkratsi) {
					nejkratsi = tr.len;
				}
			}

			// "nejkratsi" is the shortest track in this song line
			for (int ch = 0; ch < tracks4_8; ch++) {
				int n = song[sline][ch];
				if (!tracks.isValidTrack(n)) {
					continue;
				}
				if (tracklen[n] < nejkratsi) {
					tracklen[n] = nejkratsi; // if it needs a longer size, it will expand to the length it needs
				}
			}
		}

		// And now it cuts those tracks
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			int nlen = tracklen[i];
			if (nlen < 1) {
				continue; // if they don't have the length of at least 1 they are skipped
			}

			Track tr = tracks.getTrack(i);

			if (!tracks.isValidGo(tr.go)) {
				// There is no loop
				if (nlen < tr.len) {
					for (int j = nlen; j < tr.len; j++) {
						if (tracks.isValidNote(tr.note[j]) || tracks.isValidInstrument(tr.instr[j]) || tracks.isValidVolume(tr.volume[j]) || tracks.isValidSpeed(tr.speed[j])) {
							ttracks++;
							tbeats += tr.len - nlen;
							tr.len = nlen; // cut what is not needed
							break;
						}
					}
				}
			} else {
				// There is a loop; the beginning of the loop is further than the required track length
				if (tr.len >= nlen) {
					ttracks++;
					tbeats += tr.len - nlen;
					tr.len = nlen; // cut the track
					tr.go = -1; // disable loop
				}
			}
		}

		// Delete empty tracks not used in the song
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (!trackused[i] && !tracks.isEmptyTrack(i)) {
				tracks.clearTrack(i);
				ctracks++;
			}
		}

		return new ClearUnusedResult(ctracks, ttracks, tbeats);
	}

	/** Merges every pair of byte-identical tracks (keeping the lower-numbered one), remapping the song to point at the survivor. Returns the number of tracks merged away. */
	public int songClearDuplicatedTracks(int tracks4_8) {
		int[] trackto = new int[Tracks.TRACKSNUM];
		java.util.Arrays.fill(trackto, -1);

		int clearedtracks = 0;
		for (int i = 0; i < Tracks.TRACKSNUM - 1; i++) {
			if (tracks.isEmptyTrack(i)) {
				continue; // does not compare empty
			}
			for (int j = i + 1; j < Tracks.TRACKSNUM; j++) {
				if (tracks.isEmptyTrack(j)) {
					continue;
				}
				if (tracks.compareTracks(i, j)) {
					tracks.clearTrack(j); // j is the same as i, so j is deleted
					trackto[j] = i; // these tracks have to be replaced by track i
					clearedtracks++;
				}
			}
		}

		// Analyse the song and make changes to the deleted tracks
		for (int sline = 0; sline < SONGLEN; sline++) {
			for (int ch = 0; ch < tracks4_8; ch++) {
				int n = song[sline][ch];
				if (n < 0 || n >= Tracks.TRACKSNUM) {
					continue;
				}
				if (trackto[n] >= 0) {
					song[sline][ch] = trackto[n];
				}
			}
		}

		return clearedtracks;
	}

	/** Deletes every track not referenced anywhere in the song (goto lines excluded). Returns the number of tracks deleted. */
	public int songClearUnusedTracks(int tracks4_8) {
		boolean[] trackused = new boolean[Tracks.TRACKSNUM];

		for (int sline = 0; sline < SONGLEN; sline++) {
			if (songGo[sline] >= 0) {
				continue; // goto line is ignored
			}
			for (int ch = 0; ch < tracks4_8; ch++) {
				int n = song[sline][ch];
				if (n < 0 || n >= Tracks.TRACKSNUM) {
					continue;
				}
				trackused[n] = true;
			}
		}

		int clearedtracks = 0;
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (!trackused[i]) {
				if (!tracks.isEmptyTrack(i)) {
					clearedtracks++;
				}
				tracks.clearTrack(i);
			}
		}

		return clearedtracks;
	}

	/** C++'s {@code int& tracksmodified, int& beatsreduced}/{@code int& loopsexpanded} output parameters, shared by {@link #tracksAllBuildLoops}/{@link #tracksAllExpandLoops}. */
	public record TracksAllLoopResult(int tracksModified, int beatsOrLoops) {
	}

	/**
	 * Runs {@link Tracks#trackBuildLoop} over every track. Calls
	 * {@link #stop} first - a no-op as long as {@code Play()} was never
	 * called on this instance first, the only way this is exercised in
	 * tests (see {@code plans/SONG_IO_SONG_REMAINING_PLAN.md}).
	 */
	public TracksAllLoopResult tracksAllBuildLoops(Undo undo) {
		stop(undo);
		int p = 0;
		int u = 0;
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			int r = tracks.trackBuildLoop(i);
			if (r > 0) {
				p++;
				u += r;
			}
		}
		return new TracksAllLoopResult(p, u);
	}

	/** Runs {@link Tracks#trackExpandLoop} over every track. See {@link #tracksAllBuildLoops} for the {@link #stop} precondition. */
	public TracksAllLoopResult tracksAllExpandLoops(Undo undo) {
		stop(undo);
		int p = 0;
		int u = 0;
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			int r = tracks.trackExpandLoop(i);
			if (r > 0) {
				p++;
				u += r;
			}
		}
		return new TracksAllLoopResult(p, u);
	}

	/** Renumbers every track referenced by the song into a compact range starting at 0 - {@code type=1} orders by column-then-line, {@code type=2} by line-then-column. A no-op for any other {@code type}. */
	public void renumberAllTracks(int type, int tracks4_8) {
		int[] movetrackfrom = new int[Tracks.TRACKSNUM];
		int[] movetrackto = new int[Tracks.TRACKSNUM];
		java.util.Arrays.fill(movetrackfrom, -1);
		java.util.Arrays.fill(movetrackto, -1);

		int order = 0;

		if (type == 2) {
			// Horizontally along the lines
			for (int sline = 0; sline < SONGLEN; sline++) {
				if (songGo[sline] >= 0) {
					continue;
				}
				for (int i = 0; i < tracks4_8; i++) {
					int n = song[sline][i];
					if (n < 0 || n >= Tracks.TRACKSNUM) {
						continue;
					}
					if (movetrackfrom[n] < 0) {
						movetrackfrom[n] = order;
						movetrackto[order] = n;
						order++;
					}
				}
			}
		} else if (type == 1) {
			// Vertically in columns
			for (int i = 0; i < tracks4_8; i++) {
				for (int sline = 0; sline < SONGLEN; sline++) {
					if (songGo[sline] >= 0) {
						continue;
					}
					int n = song[sline][i];
					if (n < 0 || n >= Tracks.TRACKSNUM) {
						continue;
					}
					if (movetrackfrom[n] < 0) {
						movetrackfrom[n] = order;
						movetrackto[order] = n;
						order++;
					}
				}
			}
		} else {
			return; // unknown type
		}

		// Then add empty tracks not used in the song
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (movetrackfrom[i] < 0 && !tracks.isEmptyTrack(i)) {
				movetrackfrom[i] = order;
				movetrackto[order] = i;
				order++;
			}
		}

		// Precisely numbered in the song
		for (int sline = 0; sline < SONGLEN; sline++) {
			for (int i = 0; i < tracks4_8; i++) {
				int n = song[sline][i];
				if (n < 0 || n >= Tracks.TRACKSNUM) {
					continue;
				}
				song[sline][i] = movetrackfrom[n];
			}
		}

		// Physical data transfer in tracks
		for (int i = 0; i < order; i++) {
			int n = movetrackto[i]; // swap i <--> n
			if (n == i) {
				continue;
			}
			trackSwapFromTo(i, n);
			for (int j = i; j < order; j++) {
				if (movetrackto[j] == i) {
					movetrackto[j] = n;
				}
			}
		}
	}

	/** Deletes every instrument not referenced by any track's data, returning how many were actually deleted (i.e. weren't already empty). */
	public int clearAllInstrumentsUnusedInAnyTrack() {
		boolean[] instrused = new boolean[Instruments.INSTRSNUM];

		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			Track tr = tracks.getTrack(i);
			for (int j = 0; j < tr.len; j++) {
				int t = tr.instr[j];
				if (t >= 0 && t < Instruments.INSTRSNUM) {
					instrused[t] = true;
				}
			}
		}

		int clearedinstruments = 0;
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			if (!instrused[i]) {
				if (instruments.calculateNotEmpty(i)) {
					clearedinstruments++;
				}
				instruments.clearInstrument(i);
			}
		}

		return clearedinstruments;
	}

	/**
	 * Renumbers every instrument: {@code type=1} removes gaps (keeps
	 * relative order), {@code type=2} orders by first use in tracks
	 * (deleting anything left over), {@code type=3} orders alphabetically
	 * by name (bubblesort, used-before-unused). A no-op for any other
	 * {@code type}.
	 *
	 * <p>Omits C++'s final {@code g_Instruments.Update(i)} loop ("writes to
	 * Atari") - matches {@link Instruments}'s own prior omission of the
	 * same call (no Java {@code Atari} dependency exists on {@link Song}
	 * yet, and no test observes Atari memory here).
	 *
	 * <p><b>{@code type=3} has no direct test coverage</b> in
	 * {@code SongEditingTests.cpp} either (only {@code type=1}/{@code 2}
	 * are exercised) - ported as a faithful, mechanical translation, not
	 * independently verified against a golden master.
	 */
	public void renumberAllInstruments(int type) {
		int[] moveinstrfrom = new int[Instruments.INSTRSNUM];
		int[] moveinstrto = new int[Instruments.INSTRSNUM];
		java.util.Arrays.fill(moveinstrfrom, -1);
		java.util.Arrays.fill(moveinstrto, -1);

		int order = 0;

		// Analyse all tracks
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			Track tr = tracks.getTrack(i);
			for (int j = 0; j < tr.len; j++) {
				int ins = tr.instr[j];
				if (ins < 0 || ins >= Instruments.INSTRSNUM) {
					continue;
				}
				if (moveinstrfrom[ins] < 0) {
					moveinstrfrom[ins] = order;
					moveinstrto[order] = ins;
					order++;
				}
			}
		}

		// And now it adds even those that are not used in any track
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			if (moveinstrfrom[i] < 0 && instruments.calculateNotEmpty(i)) {
				moveinstrfrom[i] = order;
				moveinstrto[order] = i;
				order++;
			}
		}

		if (type == 1) {
			// Remove gaps
			int di = 0;
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				if (moveinstrfrom[i] >= 0) { // this instrument is used somewhere or is empty
					if (i != di) {
						instruments.getInstrument(di).copyFrom(instruments.getInstrument(i));
						instruments.clearInstrument(i);
					}
					moveinstrfrom[i] = di;
					moveinstrto[di] = i;
					di++;
				}
			}
		} else if (type == 2) {
			// Order by using in tracks - moveinstrfrom[instr]/moveinstrto[order] are ready, so it can physically switch straight away
			for (int i = 0; i < order; i++) {
				int n = moveinstrto[i]; // swap i <--> n
				if (n == i) {
					continue;
				}
				Instrument bufi = new Instrument();
				bufi.copyFrom(instruments.getInstrument(i));
				instruments.getInstrument(i).copyFrom(instruments.getInstrument(n));
				instruments.getInstrument(n).copyFrom(bufi);
				for (int j = i; j < order; j++) {
					if (moveinstrto[j] == i) {
						moveinstrto[j] = n;
					}
				}
			}
			// And now delete the others (due to the corresponding names of unused empty instruments)
			for (int i = order; i < Instruments.INSTRSNUM; i++) {
				instruments.clearInstrument(i);
			}
		} else if (type == 3) {
			// Order by instrument name
			boolean[] iused = new boolean[Instruments.INSTRSNUM];
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				iused[i] = moveinstrfrom[i] >= 0;
				moveinstrfrom[i] = i; // the default is to keep the same order
			}
			// Bubblesort arrange those that are iused[i]
			for (int i = Instruments.INSTRSNUM - 1; i > 0; i--) {
				for (int j = 0; j < i; j++) {
					int k = j + 1;
					boolean swap = false;

					if (iused[j] != iused[k]) {
						// one is used and one is unused
						if (iused[k]) {
							swap = true; // the second is used (=> the first is the one used), so swap
						}
					} else {
						// both are used or both are not used
						if (compareInstrumentNamesIgnoreCase(instruments.getInstrument(j).name, instruments.getInstrument(k).name) > 0) {
							swap = true; // they are the other way around, so they are swapped
						}
					}

					if (swap) {
						Instrument bufi = new Instrument();
						bufi.copyFrom(instruments.getInstrument(j));
						instruments.getInstrument(j).copyFrom(instruments.getInstrument(k));
						instruments.getInstrument(k).copyFrom(bufi);

						for (int p = 0; p < Instruments.INSTRSNUM; p++) {
							if (moveinstrfrom[p] == k) {
								moveinstrfrom[p] = j;
							} else if (moveinstrfrom[p] == j) {
								moveinstrfrom[p] = k;
							}
						}

						boolean b = iused[j];
						iused[j] = iused[k];
						iused[k] = b;
					}
				}
			}
			// Still-unused empty instruments (due to their shift, so their number-based name didn't match)
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				if (!iused[i]) {
					instruments.clearInstrument(i);
				}
			}
		} else {
			return;
		}

		// And now it has to be renumbered in all tracks according to the moveinstrfrom[instr] table
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			Track tr = tracks.getTrack(i);
			for (int j = 0; j < tr.len; j++) {
				int ins = tr.instr[j];
				if (ins < 0 || ins >= Instruments.INSTRSNUM) {
					continue;
				}
				tr.instr[j] = moveinstrfrom[ins];
			}
		}
	}

	private static int compareInstrumentNamesIgnoreCase(char[] name1, char[] name2) {
		return nameToString(name1).compareToIgnoreCase(nameToString(name2));
	}

	private static String nameToString(char[] name) {
		int end = 0;
		while (end < name.length && name[end] != '\0') {
			end++;
		}
		return new String(name, 0, end);
	}

	/**
	 * Extracted from C++'s {@code CSong::TracksOrderChange()} (the real
	 * dialog wrapper, not ported): reorders/clears song columns
	 * {@code [fromline, toline]} per {@code tracksorder} (a negative entry
	 * clears that column).
	 */
	public void tracksOrderChangeApply(int fromline, int toline, int[] tracksorder, int tracks4_8) {
		int[] buff = new int[SONGTRACKS];

		for (int i = fromline; i <= toline; i++) {
			for (int j = 0; j < tracks4_8; j++) {
				buff[j] = song[i][j];
				song[i][j] = -1;
			}
			for (int j = 0; j < tracks4_8; j++) {
				int z = tracksorder[j];
				song[i][j] = z >= 0 ? buff[z] : -1;
			}
		}
	}

	public boolean setBookmark() {
		if (songActiveLine >= 0 && songActiveLine < SONGLEN && trackActiveLine >= 0 && trackActiveLine < tracks.getMaxTrackLength() && speed >= 0) {
			bookmark.songline = songActiveLine;
			bookmark.trackline = trackActiveLine;
			bookmark.speed = speed;
			return true;
		}
		return false;
	}

	/**
	 * Returns the new {@code tracks4_8} value (the caller is responsible for
	 * storing it, matching every other {@code tracks4_8}-touching method's
	 * explicit-parameter treatment). C++'s conditional {@code ReInitSound()}
	 * call (real {@code g_AtariTrackerDriver}/{@code g_Pokey} hardware
	 * reinit, no-op-stubbed in every test) is dropped entirely, leaving this
	 * an identity transform.
	 */
	public int setTracks(int tracksNum) {
		return tracksNum;
	}

	/** Drops C++'s conditional {@code ReInitSound()} call - see {@link #setTracks}'s javadoc for why. */
	public void setNTSC(boolean ntsc) {
		if (ntsc != this.ntsc) {
			this.ntsc = ntsc;
		}
	}

	public void resetTuningVariables(TuningSettings tuning, TuningRatios tuningRatios) {
		tuning.initialize(isNTSC());
		tuningRatios.initialize();
	}

	private static final int ATARI_MAX_INSTR_OR_TRACK_LENGTH = 256; // matches C++'s ATARI_MAX_INSTR_LENGTH/ATARI_MAX_TRACK_LENGTH (SongTypes.h) - both happen to be 256

	/**
	 * Encodes the song/tracks/instruments (per {@code iotype}'s save-all-vs-
	 * save-used-only rule) into the Atari RMT module byte format at
	 * {@code mem[addr...]}, returning the address of the first byte past the
	 * last one used (or {@code -1} on the one guard-only failure: a track too
	 * event-dense to encode - C++'s {@code SendErrorMessage} isn't reproduced,
	 * matching {@link #instrChangeApply}'s established reasoning).
	 *
	 * <p>C++ writes {@code InstrToAta}/{@code TrackToAta}/{@link #songToAta}'s
	 * output directly into {@code mem} at a pointer offset; since Java arrays
	 * can't be sliced without copying, this writes each into a reusable
	 * scratch buffer first, then {@code arraycopy}s the actual encoded length
	 * into {@code mem} at the right offset.
	 */
	public int makeModule(byte[] mem, int addr, SongIOType iotype, byte[] instrumentSavedFlags, byte[] trackSavedFlags, int tracks4_8) {
		java.util.Arrays.fill(instrumentSavedFlags, (byte) 0);
		java.util.Arrays.fill(trackSavedFlags, (byte) 0);

		// Write out the RMT header (part 1)
		// 0: RMT4 or RMT8
		// 4: Track length
		// 5: Song speed
		// 6: Instrument speed
		// 7: RMT version (1 for now)
		mem[addr] = 'R';
		mem[addr + 1] = 'M';
		mem[addr + 2] = 'T';
		mem[addr + 3] = (byte) (tracks4_8 + '0'); // 4 or 8
		mem[addr + 4] = (byte) (tracks.getMaxTrackLength() & 0xff);
		mem[addr + 5] = (byte) (mainSpeed & 0xff);
		mem[addr + 6] = (byte) instrumentSpeed; // 1-4 player calls per frame
		mem[addr + 7] = (byte) RmtFormatVersion.V1;

		// Note: when saving in RMT format ALL non-empty tracks and non-empty
		// instruments will be stored; in other formats only the USED ones will be.
		markTfUsed(trackSavedFlags, tracks4_8); // mark all tracks as used
		if (iotype == SongIOType.RMT) {
			markTfNoEmpty(trackSavedFlags); // in addition to the used ones, all non-empty tracks
		}

		// Mark all used instruments in the tracks that will be saved
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (trackSavedFlags[i] > 0) {
				Track tr = tracks.getTrack(i);
				for (int j = 0; j < tr.len; j++) {
					if (tracks.isValidInstrument(tr.instr[j])) {
						instrumentSavedFlags[tr.instr[j]] = Instrument.IF_USED;
					}
				}
			}
		}

		if (iotype == SongIOType.RMT) {
			// In addition to the instruments used in the tracks in the song, all non-empty instruments are stored in the RMT
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				if (instruments.calculateNotEmpty(i)) {
					instrumentSavedFlags[i] |= Instrument.IF_NOEMPTY;
				}
			}
		}

		// Find how many tracks and instruments to save
		int numTracks = 0;
		for (int i = Tracks.TRACKSNUM - 1; i >= 0; i--) {
			if (trackSavedFlags[i] > 0) {
				numTracks = i + 1;
				break;
			}
		}

		int numInstruments = 0;
		for (int i = Instruments.INSTRSNUM - 1; i >= 0; i--) {
			if (instrumentSavedFlags[i] > 0) {
				numInstruments = i + 1;
				break;
			}
		}

		// Calculate the offsets for instruments, tracks (lo & hi) and song lines.
		// RMT header is 16 bytes, so instrument ptrs start there; each
		// instrument ptr is 2 bytes, and the track pointers are 2 bytes but
		// split into low and high storage areas.
		int ptrInstruments = addr + 16;
		int ptrTracksLoBytes = ptrInstruments + numInstruments * 2;
		int ptrTracksHiBytes = ptrTracksLoBytes + numTracks;
		int ptrInstrumentData = ptrTracksHiBytes + numTracks; // behind the track byte table

		byte[] scratch = new byte[ATARI_MAX_INSTR_OR_TRACK_LENGTH];

		// Saves instrument data and writes their beginnings to the table
		for (int i = 0; i < numInstruments; i++) {
			if (instrumentSavedFlags[i] != 0) {
				int thisInstrumentLength = instruments.instrToAta(i, scratch, isStereo(tracks4_8));
				System.arraycopy(scratch, 0, mem, ptrInstrumentData, thisInstrumentLength);

				mem[ptrInstruments + i * 2] = (byte) (ptrInstrumentData & 0xff); // lo byte
				mem[ptrInstruments + i * 2 + 1] = (byte) (ptrInstrumentData >> 8); // hi byte

				ptrInstrumentData += thisInstrumentLength;
			} else {
				// Nothing to save here, just emit 0 - happens if there are
				// unused instruments between the used ones.
				mem[ptrInstruments + i * 2] = mem[ptrInstruments + i * 2 + 1] = 0;
			}
		}

		// Just after the instrument data we start with the track data
		int ptrTrackData = ptrInstrumentData;

		// Saves track data and writes their beginnings to the table
		for (int i = 0; i < numTracks; i++) {
			if (trackSavedFlags[i] != 0) {
				int thisTrackLength = tracks.trackToAta(i, scratch);

				if (thisTrackLength < 1) {
					// Guard-only: track has too many events (notes/speed
					// commands) to encode - see this method's own javadoc.
					return -1;
				}

				System.arraycopy(scratch, 0, mem, ptrTrackData, thisTrackLength);

				mem[ptrTracksLoBytes + i] = (byte) (ptrTrackData & 0xff); // lo byte
				mem[ptrTracksHiBytes + i] = (byte) (ptrTrackData >> 8); // hi byte

				ptrTrackData += thisTrackLength;
			} else {
				mem[ptrTracksLoBytes + i] = mem[ptrTracksHiBytes + i] = 0;
			}
		}

		// Just after the track data we store the song lines
		int ptrSongData = ptrTrackData;
		byte[] songScratch = new byte[mem.length - ptrSongData];
		int thisSongLength = songToAta(songScratch, songScratch.length, ptrSongData, tracks4_8);
		System.arraycopy(songScratch, 0, mem, ptrSongData, thisSongLength);

		int endOfModule = ptrSongData + thisSongLength;

		// Writes computed pointers to the header
		mem[addr + 8] = (byte) (ptrInstruments & 0xff); // lo byte pointer to instrument table
		mem[addr + 9] = (byte) (ptrInstruments >> 8); // hi byte
		mem[addr + 10] = (byte) (ptrTracksLoBytes & 0xff); // lo byte pointer to low bytes of track data table
		mem[addr + 11] = (byte) (ptrTracksLoBytes >> 8); // hi byte
		mem[addr + 12] = (byte) (ptrTracksHiBytes & 0xff); // lo byte pointer to high bytes of track data table
		mem[addr + 13] = (byte) (ptrTracksHiBytes >> 8); // hi byte
		mem[addr + 14] = (byte) (ptrSongData & 0xff); // lo byte pointer to song data (arrangements of tracks)
		mem[addr + 15] = (byte) (ptrSongData >> 8); // hi byte

		return endOfModule; // address of the first byte past the last one used
	}

	/** {@code version} is the method's own {@code int} return value (0 on failure - ambiguous with a genuinely-decoded version-0 file, an existing C++ design wart, not introduced here); {@code tracks4_8} is C++'s {@code g_tracks4_8} global, mutated as a side effect of a successful header parse. */
	public record DecodeModuleResult(int version, int tracks4_8) {
	}

	/**
	 * Decodes an Atari RMT module byte format at {@code mem[fromAddr..endAddr)}
	 * back into this {@link Song} (plus the shared {@code Tracks}/
	 * {@code Instruments} collaborators). Omits C++'s
	 * {@code g_Instruments.Update(instrumentNr)} call ("writes to Atari ram")
	 * for each decoded instrument - matches {@code Instruments}'s own prior
	 * omission of the same call; no {@code Atari} dependency is modeled on
	 * {@link Song}, and no test observes it.
	 *
	 * <p>C++ reads {@code InstrToAta}/{@code TrackToAta}/{@link #ataToSong}'s
	 * input directly from {@code mem} at a pointer offset; since Java arrays
	 * can't be sliced without copying, each gets a
	 * {@code Arrays.copyOfRange} view instead.
	 */
	public DecodeModuleResult decodeModule(byte[] mem, int fromAddr, int endAddr, byte[] instrumentLoadedFlags, byte[] trackLoadedFlags) {
		int addr = fromAddr;

		java.util.Arrays.fill(instrumentLoadedFlags, (byte) 0);
		java.util.Arrays.fill(trackLoadedFlags, (byte) 0);

		// Check that the header starts with "RMT"
		if (mem[addr] != 'R' || mem[addr + 1] != 'M' || mem[addr + 2] != 'T') {
			return new DecodeModuleResult(0, -1); // there is no RMT
		}

		// 4th byte: # of channels (4 or 8)
		int channelByte = unsignedByte(mem, addr + 3);
		if (channelByte != '4' && channelByte != '8') {
			return new DecodeModuleResult(0, -1); // it is not RMT4 or RMT8
		}
		int tracks4_8 = setTracks(channelByte & 0x0F); // store how many channels this module uses

		// 5th byte: track length
		int trackLenByte = unsignedByte(mem, addr + 4);
		tracks.setMaxTrackLength(trackLenByte > 0 ? trackLenByte : 256); // 0 => 256

		// 6th byte: song speed
		int mainSpeedByte = unsignedByte(mem, addr + 5);
		mainSpeed = mainSpeedByte;
		if (mainSpeedByte < 1) {
			return new DecodeModuleResult(0, tracks4_8); // there can be no zero speed
		}

		// 7th byte: instrument speed
		int instrSpeedByte = unsignedByte(mem, addr + 6);
		if (instrSpeedByte < 1 || instrSpeedByte > 8) {
			return new DecodeModuleResult(0, tracks4_8); // less than 1 or greater than 8
		}
		instrumentSpeed = instrSpeedByte;

		// 8th byte: RMT format version nr.
		int version = unsignedByte(mem, addr + 7);
		if (version > RmtFormatVersion.V1) {
			return new DecodeModuleResult(0, tracks4_8); // above the currently supported one
		}

		// Now tracks.getMaxTrackLength() is set to the value in the RMT
		// header, so re-initialize the tracks to set all tracks to this new length
		tracks.initTracks();

		// Get various pointers
		int ptrInstruments = unsignedByte(mem, addr + 8) + (unsignedByte(mem, addr + 9) << 8);
		int ptrTracksLow = unsignedByte(mem, addr + 10) + (unsignedByte(mem, addr + 11) << 8);
		int ptrTracksHigh = unsignedByte(mem, addr + 12) + (unsignedByte(mem, addr + 13) << 8);
		int ptrSong = unsignedByte(mem, addr + 14) + (unsignedByte(mem, addr + 15) << 8);

		// Calculate how long each of the sections are
		int numInstruments = (ptrTracksLow - ptrInstruments) / 2;
		int numTracks = ptrTracksHigh - ptrTracksLow;
		int lengthSong = endAddr - ptrSong;

		boolean stereo = isStereo(tracks4_8);

		// Decoding of individual instruments
		for (int instrumentNr = 0; instrumentNr < numInstruments; instrumentNr++) {
			int ptrOneInstrument = unsignedByte(mem, ptrInstruments + instrumentNr * 2) + (unsignedByte(mem, ptrInstruments + instrumentNr * 2 + 1) << 8);
			if (ptrOneInstrument == 0) {
				continue; // empty instruments have a NULL ptr
			}

			byte[] instrumentData = java.util.Arrays.copyOfRange(mem, ptrOneInstrument, mem.length);
			boolean loadState = version == 0
					? instruments.ataV0ToInstr(instrumentData, instrumentNr, stereo)
					: instruments.ataToInstr(instrumentData, instrumentNr, stereo);

			if (!loadState) {
				return new DecodeModuleResult(0, tracks4_8); // some problem with the instrument => END
			}

			instrumentLoadedFlags[instrumentNr] = 1;
		}

		// Track data ptrs are split over two tables (low and high bytes, each indexed by track number)
		for (int i = 0; i < numTracks; i++) {
			int trackNr = i;
			int ptrTrack = unsignedByte(mem, ptrTracksLow + i) + (unsignedByte(mem, ptrTracksHigh + i) << 8);
			if (ptrTrack == 0) {
				continue; // omitted tracks have pointer of 0
			}

			// Identify the end of the track by the starting address of the
			// next track, and at the end by the starting address of the song
			// data that follows the data of the last track
			int ptrTrackEnd = 0;
			for (int j = i; j < numTracks; j++) {
				ptrTrackEnd = (j + 1 == numTracks) ? ptrSong : unsignedByte(mem, ptrTracksLow + j + 1) + (unsignedByte(mem, ptrTracksHigh + j + 1) << 8);
				if (ptrTrackEnd != 0) {
					break;
				}
				i++; // continue from the next and skip the omitted one
			}

			int trackLength = ptrTrackEnd - ptrTrack;
			byte[] trackData = java.util.Arrays.copyOfRange(mem, ptrTrack, mem.length);
			if (!tracks.ataToTrack(trackData, trackLength, trackNr)) {
				return new DecodeModuleResult(0, tracks4_8); // some problem with the track => END
			}

			trackLoadedFlags[trackNr] = 1;
		}

		// Decode song
		byte[] songData = java.util.Arrays.copyOfRange(mem, ptrSong, mem.length);
		if (!ataToSong(songData, lengthSong, ptrSong, tracks4_8)) {
			return new DecodeModuleResult(0, tracks4_8); // some problem with the song => END
		}

		return new DecodeModuleResult(version, tracks4_8);
	}

	private static int unsignedByte(byte[] buf, int index) {
		return buf[index] & 0xFF;
	}
}
