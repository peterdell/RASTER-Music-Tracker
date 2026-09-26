package com.wudsn.tools.rmt.model;

/**
 * Ported from CUndo (src/cpp/Undo.h/.cpp) - fully, matching
 * {@code UndoTests.cpp}'s own complete coverage (all of CUndo's real
 * dependencies "turned out already safe once the CSong split was done" -
 * see that file's own header comment).
 *
 * <p>Coordinates {@link Tracks}/{@link Instruments}/{@link Song} directly
 * (stored at construction) rather than via C++'s free-standing globals -
 * this is the natural OO shape once those globals become real objects.
 * {@link Song#stop} takes this instance as an explicit parameter instead
 * (see {@link Song}'s own javadoc), avoiding a circular constructor
 * dependency between the two classes.
 *
 * <p><b>Omitted, no Java equivalent yet</b>: C++'s
 * {@code InsertEvent()}-side {@code g_changes}/{@code SetRMTTitle()}
 * window-title bookkeeping (no window exists), and
 * {@code PerformEvent()}'s {@code g_Instruments.Update(instrnum)} calls'
 * Atari-memory write for {@code UETYPE_INSTRDATA}/{@code UETYPE_INSTRSALL}
 * ("must save to Atari" - no Atari-memory-writing subsystem wired here
 * yet); their display-hint half is done via {@link Instruments#update}.
 *
 * <p><b>A latent C++ bug found, not fixed (also flagged in Undo.cpp)</b>:
 * {@code ChangeSong}'s {@code UETYPE_SONGDATA} case never copies the
 * current bookmark into its snapshot (only {@code song}/{@code songgo}),
 * so the first undo of a whole-song-data change would restore whatever
 * indeterminate bookmark {@code new TSong} happened to leave in the
 * snapshot. Not currently characterized by any test (the existing test
 * doesn't check the bookmark after undo), and this port preserves the same
 * omission in {@link #changeSong} for parity - though Java's mandatory
 * zero-initialization means the *symptom* differs (a zeroed
 * {@link Bookmark}, not garbage), the same underlying oversight (missing
 * copy) is still there.
 */
public final class Undo {

	// Undo operation (one can consume up to 3 records)
	private static final int UNDOSTEPS = 100;
	private static final int MAXUNDO = UNDOSTEPS * 3 + 8; // 2 extra separating gap

	private static final int POSGROUPTYPE0_63SIZE = 2;
	private static final int POSGROUPTYPE64_127SIZE = 1;
	private static final int POSGROUPTYPE128_191SIZE = 3;

	private final Tracks tracks;
	private final Instruments instruments;
	private final Song song;
	private final TrackClipboard trackClipboard;

	private final UndoEvent[] uar = new UndoEvent[MAXUNDO];
	private int head, tail, headmax;
	private int undoSteps, redoSteps;

	private Part activePart = Part.PART_TRACKS; // matches g_activepart's own default

	public Undo(Tracks tracks, Instruments instruments, Song song, TrackClipboard trackClipboard) {
		this.tracks = tracks;
		this.instruments = instruments;
		this.song = song;
		this.trackClipboard = trackClipboard;
	}

	public void init() {
		clear();
	}

	public void clear() {
		head = 0;
		tail = 0;
		headmax = 0;
		undoSteps = redoSteps = 0;
		for (int i = 0; i < MAXUNDO; i++) {
			deleteEvent(i);
		}
	}

	private int deleteEvent(int i) {
		UndoEvent ue = uar[i];
		if (ue == null) {
			return 1;
		}
		int sep = ue.separator; // storage for return
		uar[i] = null;
		return sep;
	}

	public int getUndoSteps() {
		return undoSteps;
	}

	public boolean undo() {
		if (head == tail) {
			return false; // nothing to keep
		}

		song.stop(this);

		int sep;
		do {
			head = (head + MAXUNDO - 1) % MAXUNDO;
			performEvent(head);
			if (head == tail) {
				break;
			}
			int prev = (head + MAXUNDO - 1) % MAXUNDO;
			sep = uar[prev].separator;
		} while (sep == -1);

		undoSteps--;
		redoSteps++;

		return true;
	}

	public int getRedoSteps() {
		return redoSteps;
	}

	public boolean redo() {
		if (head == headmax) {
			return false; // nothing to return
		}

		song.stop(this);

		int sep;
		do {
			sep = performEvent(head);
			head = (head + 1) % MAXUNDO;
		} while (sep == -1);

		redoSteps--;
		undoSteps++;

		return true;
	}

	private void insertEvent(UndoEvent ue) {
		// add cursor
		ue.part = activePart;
		ue.cursor = song.getUECursor(activePart);
		if (uar[head] != null) {
			deleteEvent(head);
		}
		uar[head] = ue;
		// is there an event already?
		if (head != tail) {
			UndoEvent le = uar[(head + MAXUNDO - 1) % MAXUNDO];
			if (ue.part == le.part && ue.type == le.type && le.separator == 0 && ue.separator == 0
					&& song.uecursorIsEqual(ue.cursor, le.cursor, ue.part) && posIsEqual(ue.pos, le.pos, ue.type)) {
				// the last event is at the same cursor position and with the same data
				deleteEvent(head); // erases it from memory
				// and will not count it among undo events, just end the maximum undo
				headmax = head;
				redoSteps = 0;
				return;
			}
		}

		if (ue.separator != -1) {
			undoSteps++; // only complete events are included
		}
		head = (head + 1) % MAXUNDO;
		if ((undoSteps > UNDOSTEPS) || ((head + 1) % MAXUNDO == tail)) {
			int sep;
			do {
				sep = deleteEvent(tail);
				tail = (tail + 1) % MAXUNDO;
			} while (sep == -1);
			undoSteps--;
		}
		headmax = head;
		redoSteps = 0;
	}

	public void dropLast() {
		if (head == tail) {
			return;
		}
		head = (head + MAXUNDO - 1) % MAXUNDO;
		deleteEvent(head);
		undoSteps--; // will count this step
	}

	public void setActivePart(Part part) {
		activePart = part;
	}

	public void separator() {
		separator(1);
	}

	public void separator(int sep) {
		UndoEvent le = uar[(head + MAXUNDO - 1) % MAXUNDO];
		if (le == null) {
			return;
		}
		if (sep < 0 && le.separator >= 0) {
			undoSteps--; // the number of undo counted in insertEvent
		}
		le.separator = sep;
	}

	public void changeTrack(int tracknum, int trackline, int type) {
		changeTrack(tracknum, trackline, type, 0);
	}

	public void changeTrack(int tracknum, int trackline, int type, int separator) {
		if (!tracks.isValidTrack(tracknum) || !tracks.isValidLine(trackline)) {
			return;
		}

		Track tr = tracks.getTrack(tracknum);

		UndoEvent ue = new UndoEvent();
		ue.type = type;
		ue.pos = new int[] { tracknum, trackline };
		ue.separator = separator;
		Object data;

		switch (type) {
		case UndoType.UETYPE_NOTEINSTRVOL:
			data = new int[] { tr.note[trackline], tr.instr[trackline], tr.volume[trackline] };
			break;

		case UndoType.UETYPE_NOTEINSTRVOLSPEED:
			data = new int[] { tr.note[trackline], tr.instr[trackline], tr.volume[trackline], tr.speed[trackline] };
			break;

		case UndoType.UETYPE_SPEED:
			data = new int[] { tr.speed[trackline] };
			break;

		case UndoType.UETYPE_LENGO:
			data = new int[] { tr.len, tr.go };
			break;

		case UndoType.UETYPE_TRACKDATA: { // Whole track
			Track snapshot = new Track();
			snapshot.copyFrom(tr);
			data = snapshot;
			break;
		}

		case UndoType.UETYPE_TRACKSALL: { // All tracks
			TracksAll snapshot = new TracksAll();
			tracks.getTracksAll(snapshot);
			data = snapshot;
			break;
		}

		default:
			// C++ shows a "CUndo::ChangeTrack BAD!" error message here - no
			// message subsystem is ported, so this is silently characterized
			// as a no-op data event instead (matches
			// ChangeTrackWithInvalidTypeIsCharacterizedAsANoOpDataEvent).
			data = null;
		}

		ue.data = data;
		insertEvent(ue);
	}

	public void changeSong(int songline, int trackcol, int type) {
		changeSong(songline, trackcol, type, 0);
	}

	public void changeSong(int songline, int trackcol, int type, int separator) {
		if (songline < 0 || trackcol < 0) {
			return;
		}

		UndoEvent ue = new UndoEvent();
		ue.type = type;
		ue.pos = new int[] { songline, trackcol };
		ue.separator = separator;
		Object data;

		switch (type) {
		case UndoType.UETYPE_SONGTRACK:
			data = new int[] { song.songGetTrack(songline, trackcol) };
			break;

		case UndoType.UETYPE_SONGGO:
			data = new int[] { song.songGetGo(songline) };
			break;

		case UndoType.UETYPE_SONGDATA: { // Whole song
			SongData snapshot = new SongData();
			for (int line = 0; line < Song.SONGLEN; line++) {
				System.arraycopy(song.getSong()[line], 0, snapshot.song[line], 0, Song.SONGTRACKS);
			}
			System.arraycopy(song.getSongGo(), 0, snapshot.songGo, 0, Song.SONGLEN);
			// snapshot.bookmark deliberately left at its default (all zero) -
			// matches a latent C++ omission, see class javadoc.
			data = snapshot;
			break;
		}

		default:
			data = null;
		}

		ue.data = data;
		insertEvent(ue);
	}

	public void changeInstrument(int instrnum, int paridx, int type) {
		changeInstrument(instrnum, paridx, type, 0);
	}

	public void changeInstrument(int instrnum, int paridx, int type, int separator) {
		Instrument instr = instruments.getInstrument(instrnum);
		if (instr == null) {
			return;
		}

		UndoEvent ue = new UndoEvent();
		ue.type = type;
		ue.pos = new int[] { instrnum, paridx };
		ue.separator = separator;
		Object data;

		switch (type) {
		case UndoType.UETYPE_INSTRDATA: { // Whole instrument
			Instrument snapshot = new Instrument();
			snapshot.copyFrom(instr);
			data = snapshot;
			break;
		}

		case UndoType.UETYPE_INSTRSALL: { // All instruments
			InstrumentsAll snapshot = new InstrumentsAll();
			instruments.getInstrumentsAll(snapshot);
			data = snapshot;
			break;
		}

		default:
			data = null;
		}

		ue.data = data;
		insertEvent(ue);
	}

	public void changeInfo(int paridx, int type) {
		changeInfo(paridx, type, 0);
	}

	public void changeInfo(int paridx, int type, int separator) {
		UndoEvent ue = new UndoEvent();
		ue.type = type;
		ue.pos = new int[] { paridx };
		ue.separator = separator;
		Object data;

		switch (type) {
		case UndoType.UETYPE_INFODATA: { // Whole info
			SongInfo snapshot = new SongInfo();
			song.getSongInfoPars(snapshot); // fill with values taken from song
			data = snapshot;
			break;
		}

		default:
			data = null;
		}

		ue.data = data;
		insertEvent(ue);
	}

	public boolean posIsEqual(int[] pos1, int[] pos2, int type) {
		int len;
		switch (type >> 6) { // /64
		case 0:
			len = POSGROUPTYPE0_63SIZE;
			break;
		case 1:
			len = POSGROUPTYPE64_127SIZE;
			break;
		case 2:
			len = POSGROUPTYPE128_191SIZE;
			break;
		default:
			return false;
		}
		for (int i = 0; i < len; i++) {
			if (pos1[i] != pos2[i]) {
				return false;
			}
		}
		return true;
	}

	private static void exchange(int[] a, int ai, int[] b, int bi) {
		int tmp = a[ai];
		a[ai] = b[bi];
		b[bi] = tmp;
	}

	private int performEvent(int i) {
		UndoEvent ue = uar[i];
		if (ue == null) {
			return 1;
		}

		// Keeps the separator as a return value
		int sep = ue.separator;

		// Set cursor there (change and the active part)
		song.setUECursor(ue.part, ue.cursor, this);

		song.blockDeselect(trackClipboard);

		switch (ue.type) {
		case UndoType.UETYPE_NOTEINSTRVOL: {
			int tracknum = ue.pos[0];
			int trackline = ue.pos[1];
			Track tr = tracks.getTrack(tracknum);
			int[] data = (int[]) ue.data;
			exchange(tr.note, trackline, data, 0);
			exchange(tr.instr, trackline, data, 1);
			exchange(tr.volume, trackline, data, 2);
			break;
		}

		case UndoType.UETYPE_NOTEINSTRVOLSPEED: {
			int tracknum = ue.pos[0];
			int trackline = ue.pos[1];
			Track tr = tracks.getTrack(tracknum);
			int[] data = (int[]) ue.data;
			exchange(tr.note, trackline, data, 0);
			exchange(tr.instr, trackline, data, 1);
			exchange(tr.volume, trackline, data, 2);
			exchange(tr.speed, trackline, data, 3);
			break;
		}

		case UndoType.UETYPE_SPEED: {
			int tracknum = ue.pos[0];
			int trackline = ue.pos[1];
			Track tr = tracks.getTrack(tracknum);
			int[] data = (int[]) ue.data;
			exchange(tr.speed, trackline, data, 0);
			break;
		}

		case UndoType.UETYPE_LENGO: {
			int tracknum = ue.pos[0];
			int trackline = ue.pos[1];
			Track tr = tracks.getTrack(tracknum);
			int[] data = (int[]) ue.data;
			int tmp = tr.len;
			tr.len = data[0];
			data[0] = tmp;
			tmp = tr.go;
			tr.go = data[1];
			data[1] = tmp;
			break;
		}

		case UndoType.UETYPE_TRACKDATA: { // Whole track
			int tracknum = ue.pos[0];
			Track tr = tracks.getTrack(tracknum);
			Track data = (Track) ue.data;
			Track temp = new Track();
			temp.copyFrom(tr);
			tr.copyFrom(data);
			data.copyFrom(temp);
			break;
		}

		case UndoType.UETYPE_TRACKSALL: { // All tracks
			TracksAll data = (TracksAll) ue.data;
			TracksAll temp = new TracksAll();
			tracks.getTracksAll(temp); // from temp
			tracks.setTracksAll(data); // data to tracksall
			data.copyFrom(temp);
			break;
		}

		case UndoType.UETYPE_SONGTRACK: {
			int songline = ue.pos[0];
			int trackcol = ue.pos[1];
			int[] data = (int[]) ue.data;
			exchange(song.getSong()[songline], trackcol, data, 0);
			break;
		}

		case UndoType.UETYPE_SONGGO: {
			int songline = ue.pos[0];
			int[] data = (int[]) ue.data;
			exchange(song.getSongGo(), songline, data, 0);
			break;
		}

		case UndoType.UETYPE_SONGDATA: { // Whole song
			SongData data = (SongData) ue.data;
			SongData temp = new SongData();
			for (int line = 0; line < Song.SONGLEN; line++) {
				System.arraycopy(song.getSong()[line], 0, temp.song[line], 0, Song.SONGTRACKS);
			}
			System.arraycopy(song.getSongGo(), 0, temp.songGo, 0, Song.SONGLEN);
			temp.bookmark.copyFrom(song.getBookmark());

			for (int line = 0; line < Song.SONGLEN; line++) {
				System.arraycopy(data.song[line], 0, song.getSong()[line], 0, Song.SONGTRACKS);
			}
			System.arraycopy(data.songGo, 0, song.getSongGo(), 0, Song.SONGLEN);
			song.getBookmark().copyFrom(data.bookmark);

			for (int line = 0; line < Song.SONGLEN; line++) {
				System.arraycopy(temp.song[line], 0, data.song[line], 0, Song.SONGTRACKS);
			}
			System.arraycopy(temp.songGo, 0, data.songGo, 0, Song.SONGLEN);
			data.bookmark.copyFrom(temp.bookmark);
			break;
		}

		case UndoType.UETYPE_INSTRDATA: { // Whole instrument
			int instrnum = ue.pos[0];
			Instrument in = instruments.getInstrument(instrnum);
			Instrument data = (Instrument) ue.data;
			Instrument temp = new Instrument();
			temp.copyFrom(in);
			in.copyFrom(data);
			data.copyFrom(temp);
			instruments.update(instrnum); // must save to Atari (the display-hint half of it - see Instruments.update)
			break;
		}

		case UndoType.UETYPE_INSTRSALL: { // All instruments
			InstrumentsAll data = (InstrumentsAll) ue.data;
			InstrumentsAll temp = new InstrumentsAll();
			instruments.getInstrumentsAll(temp);
			instruments.setInstrumentsAll(data);
			data.copyFrom(temp);
			for (int instr = 0; instr < Instruments.INSTRSNUM; instr++) {
				instruments.update(instr); // must save to Atari (the display-hint half of it - see Instruments.update)
			}
			break;
		}

		case UndoType.UETYPE_INFODATA: {
			SongInfo data = (SongInfo) ue.data;
			SongInfo info = new SongInfo();
			song.getSongInfoPars(info); // fill "info" with values taken from song
			SongInfo temp = new SongInfo();
			temp.copyFrom(info);
			info.copyFrom(data);
			data.copyFrom(temp);
			song.setSongInfoPars(info); // set values in song with values from "data"
			break;
		}

		default:
			// C++ shows a "PerformEvent BAD!" error message here - no message
			// subsystem is ported, and this branch is unreachable via any
			// event this class itself ever creates (see the "BAD!" default
			// cases above, which always leave ue.data null and never fail to
			// insert the event).
			break;
		}

		return sep; // Returns separator
	}
}
