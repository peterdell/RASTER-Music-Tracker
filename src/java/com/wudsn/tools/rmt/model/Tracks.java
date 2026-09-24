package com.wudsn.tools.rmt.model;

/**
 * Ported from CTracks (src/cpp/Tracks.h/.cpp, src/cpp/IO_Tracks.cpp).
 * {@code TrackBuildLoop}/{@code TrackExpandLoop}/{@code ModifyTrack}/
 * {@code GetTracksAll}/{@code SetTracksAll} were originally deferred for
 * having no C++ test coverage to port against (same reasoning as
 * {@code GenerateTable}/{@code InitTuning}'s original deferral); backfilled
 * in {@code TracksTests.cpp} and ported here once that gave real
 * golden-master values to verify against.
 *
 * <p>Still deferred, matching the C++ source's own genuinely
 * globals-coupled split: {@code TracksEdit.cpp}'s
 * {@code DelNoteInstrVolSpeed}/{@code SetNoteInstrVol}/{@code SetInstr}/
 * {@code SetVol}/{@code SetSpeed}/{@code SetEnd}/{@code SetGo} (need
 * {@code g_Undo}, not yet ported) and {@code IO_Tracks.cpp}'s
 * {@code SaveTrack}/{@code LoadTrack}/{@code SaveAll}/{@code LoadAll}
 * (untested stream I/O in two on-disk formats).
 *
 * <p>C++'s {@code new TTrack[TRACKSNUM]} leaves every track's fields
 * genuinely uninitialized until {@code InitTracks()} runs (a known,
 * accepted convention - every real caller, including this class's own
 * tests, calls {@code InitTracks()} immediately after construction); Java
 * always zero-initializes fields, so the constructor here has nothing
 * unsafe to guard against either way.
 */
public final class Tracks {

	public static final int TRACKSNUM = 254; // 0-253
	public static final int TRACKMAXSPEED = 256; // Maximum speed value; the higher the slower

	// From SongTypes.h/InstrumentTypes.h (not yet ported) - duplicated here
	// with the same values pending those classes' own Java ports.
	private static final int SONGTRACKS = 8;
	private static final int MAXVOLUME = 15;
	private static final int ATARI_MAX_TRACK_LENGTH = 256;
	private static final int INSTRSNUM = 64;

	private int maxTrackLength;
	private final Track[] track;

	public Tracks() {
		maxTrackLength = 64; // Default value
		track = new Track[TRACKSNUM];
		for (int i = 0; i < TRACKSNUM; i++) {
			track[i] = new Track();
		}
	}

	public void initTracks() {
		for (int i = 0; i < TRACKSNUM; i++) {
			clearTrack(i);
		}
	}

	public void clearTrack(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return;
		}

		// Clear everything, set to -1 for empty values (matches C++'s
		// memset(tr, -1, sizeof(TTrack)), which sets every int field's every
		// byte to 0xFF - equivalent to -1 for a two's-complement int).
		tr.len = -1;
		tr.go = -1;
		java.util.Arrays.fill(tr.note, -1);
		java.util.Arrays.fill(tr.instr, -1);
		java.util.Arrays.fill(tr.volume, -1);
		java.util.Arrays.fill(tr.speed, -1);

		// Except for Maxtracklength, set to the last known parameter
		tr.len = maxTrackLength;
	}

	public boolean isEmptyTrack(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}

		// If the track length doesn't match Maxtracklength, it is not empty
		if (tr.len != maxTrackLength) {
			return false;
		}

		// Test for values in track, if it is equal or above 0, it is not empty
		for (int i = 0; i < maxTrackLength; i++) {
			if (tr.volume[i] >= 0 || tr.speed[i] >= 0 || tr.note[i] >= 0) {
				return false;
			}
		}

		// If everything failed, the track is definitely empty
		return true;
	}

	public int getLastLine(int trackNumber) {
		Track tr = getTrack(trackNumber);
		return (tr != null) ? tr.len - 1 : -1;
	}

	public int getLength(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return -1;
		}
		return tr.go >= 0 ? maxTrackLength : tr.len;
	}

	public int getGoLine(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return 0;
		}
		return tr.go;
	}

	public boolean insertLine(int trackNumber, int line) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}
		if (tr.len < 0) {
			return false;
		}

		for (int i = tr.len - 2; i >= line; i--) {
			tr.note[i + 1] = tr.note[i];
			tr.instr[i + 1] = tr.instr[i];
			tr.volume[i + 1] = tr.volume[i];
			tr.speed[i + 1] = tr.speed[i];
		}

		tr.note[line] = tr.instr[line] = tr.volume[line] = tr.speed[line] = -1;
		return true;
	}

	public boolean deleteLine(int trackNumber, int line) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}
		if (tr.len < 0) {
			return false;
		}

		for (int i = line; i < tr.len - 1; i++) {
			tr.note[i] = tr.note[i + 1];
			tr.instr[i] = tr.instr[i + 1];
			tr.volume[i] = tr.volume[i + 1];
			tr.speed[i] = tr.speed[i + 1];
		}

		line = tr.len - 1;
		tr.note[line] = tr.instr[line] = tr.volume[line] = tr.speed[line] = -1;
		return true;
	}

	/**
	 * Check if a specific track has any valid information set. Checks track length, notes, and volume and speed changes.
	 *
	 * @param trackNumber which track is being checked
	 * @return true if the track is NOT empty, false if there is nothing set on it
	 */
	public boolean calculateNotEmpty(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}

		if (tr.len != maxTrackLength) { // If the length is anything but the maximum track length?
			return true; // Yes, it's NOT EMPTY
		}

		for (int i = 0; i < tr.len; i++) {
			if (tr.note[i] >= 0 || tr.volume[i] >= 0 || tr.speed[i] >= 0) {
				return true; // Not empty
			}
		}

		return false; // Is empty
	}

	public boolean compareTracks(int track1, int track2) {
		Track t1 = getTrack(track1);
		Track t2 = getTrack(track2);
		if (t1 == null || t2 == null) {
			return false;
		}

		if (t1.len != t2.len || t1.go != t2.go) {
			return false;
		}

		for (int i = 0; i < t1.len; i++) {
			if (t1.note[i] != t2.note[i] || t1.instr[i] != t2.instr[i] || t1.volume[i] != t2.volume[i] || t1.speed[i] != t2.speed[i]) {
				return false; // Found a difference => they are not the same
			}
		}

		return true; // Did not find a difference => they are the same
	}

	public boolean trackOptimizeVol0(int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}

		int lastzline = -1;
		int kline = -1; // Candidate for deletion including note

		for (int i = 0; i < tr.len; i++) {
			if (tr.volume[i] == 0) {
				if (lastzline >= 0) {
					if (kline >= 0) { // Any candidate to delete? (note + vol0 in the middle between zero volumes)
						tr.note[kline] = tr.instr[kline] = tr.volume[kline] = -1;
					}
					if (tr.note[i] < 0 && tr.instr[i] < 0) {
						tr.volume[i] = -1; // Cancel this volume
					} else {
						kline = i;
					}
				} else {
					// This is currently the last line with zero volume
					lastzline = i;
				}
			} else if (tr.volume[i] > 0) {
				lastzline = kline = -1;
			}
		}
		return true;
	}

	/**
	 * Searches for the earliest/shortest repeating suffix (at least 2 lines, matching an earlier segment, with more than 1 non-empty line inside the matched region) and, if found, truncates the track into a loop instead.
	 *
	 * @param trackNumber which track to search
	 * @return the length of the loop found, or 0 if none was found (or the track isn't eligible - empty, already looped, or not full length)
	 */
	public int trackBuildLoop(int trackNumber) {
		if (isEmptyTrack(trackNumber)) {
			return 0; // Empty track
		}

		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return 0;
		}

		if (tr.go >= 0) {
			return 0; // There is a loop
		}
		if (tr.len != maxTrackLength) {
			return 0; // It is not full length => it cannot make a loop there
		}

		for (int i = 1; i < tr.len; i++) {
			for (int j = 0; j < i; j++) {
				int k;
				for (k = 0; i + k < tr.len; k++) {
					if (tr.note[i + k] == tr.note[j + k] && tr.instr[i + k] == tr.instr[j + k] && tr.volume[i + k] == tr.volume[j + k] && tr.speed[i + k] == tr.speed[j + k]) {
						continue;
					}
					break;
				}
				if (k > 1 && i + k == tr.len) {
					// It managed to find a loop at least 2 bars long lasting until the end
					// Check to see if it's not empty in that loop
					int p = 0;
					for (int m = 0; i + m < tr.len; m++) {
						if (tr.note[j + m] >= 0 || tr.instr[j + m] >= 0 || tr.volume[j + m] >= 0 || tr.speed[j + m] >= 0) {
							p++;
							if (p > 1) { // Yes, it found at least two nonzero lines inside the loop
								tr.len = i;
								tr.go = j;
								return k; // Returns the length of the loop found
							}
						}
					}
				}
			}
		}
		return 0;
	}

	/**
	 * The inverse of {@link #trackBuildLoop}: expands a track with a go-loop back out to full length by cyclically repeating the [go, len) segment.
	 *
	 * @param trackNumber which track to expand
	 * @return the number of lines the loop was expanded by, or 0 if the track is empty or invalid
	 */
	public int trackExpandLoop(int trackNumber) {
		if (isEmptyTrack(trackNumber)) {
			return 0; // Empty track
		}

		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return 0;
		}

		// Length of the expanded loop
		return trackExpandLoop(tr);
	}

	/**
	 * Overload taking a {@link Track} directly (matches C++'s {@code TrackExpandLoop(TTrack*)}).
	 *
	 * @param track the track to expand
	 * @return the number of lines the loop was expanded by, or 0 if track is null or has no loop
	 */
	public int trackExpandLoop(Track track) {
		if (track == null) {
			return 0;
		}
		if (track.go < 0) {
			return 0; // There is no loop
		}

		int i;
		for (i = 0; track.len + i < maxTrackLength; i++) {
			int j = track.len + i;
			int k = track.go + i;
			track.note[j] = track.note[k];
			track.instr[j] = track.instr[k];
			track.volume[j] = track.volume[k];
			track.speed[j] = track.speed[k];
		}
		track.len = maxTrackLength; // Full length
		track.go = -1; // No loop

		return i; // Length of the expanded loop
	}

	/**
	 * A plain deep-copy snapshot of every track plus the current max track length - used by CUndo (not yet ported) to save/restore all track state at once.
	 */
	public void getTracksAll(TracksAll toTracks) {
		toTracks.maxTrackLength = maxTrackLength;
		for (int i = 0; i < TRACKSNUM; i++) {
			copyTrack(track[i], toTracks.tracks[i]);
		}
	}

	public void setTracksAll(TracksAll fromTracks) {
		maxTrackLength = fromTracks.maxTrackLength;
		for (int i = 0; i < TRACKSNUM; i++) {
			copyTrack(fromTracks.tracks[i], track[i]);
		}
	}

	private static void copyTrack(Track from, Track to) {
		to.len = from.len;
		to.go = from.go;
		System.arraycopy(from.note, 0, to.note, 0, Track.TRACKLEN);
		System.arraycopy(from.instr, 0, to.instr, 0, Track.TRACKLEN);
		System.arraycopy(from.volume, 0, to.volume, 0, Track.TRACKLEN);
		System.arraycopy(from.speed, 0, to.speed, 0, Track.TRACKLEN);
	}

	/**
	 * Applies a transposition/instrument-shift/volume-percentage change across a line range, optionally filtered to only lines carrying a specific instrument.
	 *
	 * @param track the track to modify directly (not looked up by number - matches C++'s TTrack* parameter)
	 * @param from first line, inclusive
	 * @param to last line, inclusive (clamped to Track.TRACKLEN - 1 if past it)
	 * @param instrnumonly filters by the *active* instrument at each line (the most recent instr[] value seen at or after {@code from}, not necessarily set on the exact line being modified) - a negative value applies to all instruments
	 * @param tuning semitones to transpose by (see {@link #getModifiedNote})
	 * @param instradd instrument number to shift by, wrapping (see {@link #getModifiedInstr})
	 * @param volumep volume percentage to scale by, clamped (see {@link #getModifiedVolumeP})
	 * @return false only if track is null
	 */
	public boolean modifyTrack(Track track, int from, int to, int instrnumonly, int tuning, int instradd, int volumep) {
		// instruments < 0 => all instruments
		//              >= 0 => only that one instrument
		if (track == null) {
			return false;
		}
		if (to >= Track.TRACKLEN) {
			to = Track.TRACKLEN - 1;
		}
		int ainstr = -1;
		for (int i = from; i <= to; i++) {
			int instr = track.instr[i];
			if (instr >= 0) {
				ainstr = instr;
			}
			if (instrnumonly >= 0 && instrnumonly != ainstr) {
				continue;
			}
			track.note[i] = getModifiedNote(track.note[i], tuning);
			track.instr[i] = getModifiedInstr(instr, instradd);
			track.volume[i] = getModifiedVolumeP(track.volume[i], volumep);
		}
		return true;
	}

	public int getModifiedNote(int note, int tuning) {
		if (!isValidNote(note)) {
			return -1;
		}

		int n = note + tuning;

		if (n < 0) {
			n += ((-n - 1) / 12 + 1) * 12;
		} else if (n >= Notes.NOTESNUM) {
			n -= ((n - Notes.NOTESNUM) / 12 + 1) * 12;
		}
		return n;
	}

	public int getModifiedInstr(int instr, int instradd) {
		if (!isValidInstrument(instr)) {
			return -1;
		}

		int i = instr + instradd;
		while (i < 0) {
			i += INSTRSNUM;
		}
		while (i >= INSTRSNUM) {
			i -= INSTRSNUM;
		}
		return i;
	}

	public int getModifiedVolumeP(int volume, int percentage) {
		if (volume < 0) {
			return -1;
		}
		if (percentage <= 0) {
			return 0;
		}
		int v = (int) ((float) percentage / 100 * volume + 0.5);
		return (v > MAXVOLUME) ? MAXVOLUME : v;
	}

	/**
	 * Encode a track into its compact on-Atari byte representation.
	 *
	 * @param trackNumber which track to encode
	 * @param dest buffer to write into (C++ additionally takes a separate "max" length - Java uses dest.length instead, since every real caller already passes the buffer's own size)
	 * @return number of bytes written, or -1 if dest was too small
	 */
	public int trackToAta(int trackNumber, byte[] dest) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return 0;
		}

		// A single-element array used purely as a mutable "output parameter"
		// for the idx cursor, since writeAt()/writePause() need to both
		// report success/failure and advance the cursor - the Java analogue
		// of C++'s WRITEATIDX macro directly returning -1 from TrackToAta.
		int[] idx = { 0 };
		int goidx = -1;
		int pause = 0;

		for (int i = 0; i < tr.len; i++) {
			int note = tr.note[i];
			int instr = tr.instr[i];
			int volume = tr.volume[i];
			int speed = tr.speed[i];

			if (volume >= 0 || speed >= 0 || tr.go == i) {
				if (pause > 0) {
					if (!writePause(dest, idx, pause)) {
						return -1;
					}
					pause = 0;
				}

				if (tr.go == i) {
					goidx = idx[0];
				}

				if (speed >= 0) {
					if (!writeAt(dest, idx, 63) || !writeAt(dest, idx, speed & 0xff)) {
						return -1;
					}
					pause = 0;
				}
			}

			if (note >= 0 && instr >= 0 && volume >= 0) {
				if (!writeAt(dest, idx, ((volume & 0x03) << 6) | (note & 0x3f))
						|| !writeAt(dest, idx, ((instr & 0x3f) << 2) | ((volume & 0x0c) >> 2))) {
					return -1;
				}
				pause = 0;
			} else if (volume >= 0) {
				if (!writeAt(dest, idx, ((volume & 0x03) << 6) | 61)
						|| !writeAt(dest, idx, (volume & 0x0c) >> 2)) {
					return -1;
				}
				pause = 0;
			} else {
				pause++;
			}
		}

		if (tr.len < maxTrackLength) {
			if (pause > 0 && !writePause(dest, idx, pause)) {
				return -1;
			}
			if (tr.go >= 0 && goidx >= 0) {
				if (!writeAt(dest, idx, 0x80 | 63) || !writeAt(dest, idx, goidx)) {
					return -1;
				}
			} else if (!writeAt(dest, idx, 255)) {
				return -1;
			}
		} else if (pause > 0 && !writePause(dest, idx, pause)) {
			return -1;
		}
		return idx[0];
	}

	private static boolean writeAt(byte[] dest, int[] idx, int value) {
		if (idx[0] < dest.length) {
			dest[idx[0]] = (byte) value;
			idx[0]++;
			return true;
		}
		return false;
	}

	private static boolean writePause(byte[] dest, int[] idx, int pause) {
		if (pause >= 1 && pause <= 3) {
			return writeAt(dest, idx, 62 | (pause << 6));
		}
		return writeAt(dest, idx, 62) && writeAt(dest, idx, pause);
	}

	/**
	 * Decode a track's compact on-Atari byte representation into this instance.
	 *
	 * @param mem buffer containing the track description
	 * @param trackLength length of the encoded track, in bytes
	 * @param trackNumber which track to decode into
	 * @return true on success
	 */
	public boolean ataToTrack(byte[] mem, int trackLength, int trackNumber) {
		Track tr = getTrack(trackNumber);
		if (tr == null) {
			return false;
		}

		int gotoIndex = -1;
		if (trackLength >= 2) {
			// There is a go loop at the end of the track
			if (unsignedByte(mem, trackLength - 2) == 128 + 63) {
				gotoIndex = unsignedByte(mem, trackLength - 1); // Store its index
			}
		}

		int line = 0;
		int src = 0;

		while (src < trackLength) {
			// Jump to gotoIndex => set go to this line
			if (src == gotoIndex) {
				tr.go = line;
			}

			int data = unsignedByte(mem, src) & 0x3f;

			if (data <= 60) {
				// Note, instrument and volume data on this line
				tr.note[line] = data;
				tr.instr[line] = (unsignedByte(mem, src + 1) & 0xfc) >> 2;
				tr.volume[line] = ((unsignedByte(mem, src + 1) & 0x03) << 2) | ((unsignedByte(mem, src) & 0xc0) >> 6);
				src += 2;
				line++;
			} else if (data == 61) {
				// Volume only on this line
				tr.volume[line] = ((unsignedByte(mem, src + 1) & 0x03) << 2) | ((unsignedByte(mem, src) & 0xc0) >> 6);
				src += 2;
				line++;
			} else if (data == 62) {
				// Pause / empty line
				int count = unsignedByte(mem, src) & 0xc0;
				if (count == 0) {
					// Pause is 0 then the number of lines to skip is in the next byte
					if (unsignedByte(mem, src + 1) == 0) {
						break; // Infinite pause => end
					}
					line += unsignedByte(mem, src + 1); // Shift line
					src += 2;
				} else {
					line += (count >> 6); // Upper 2 bits directly specify a pause 1-3
					src++;
				}
			} else { // data == 63: speed, go loop, or end
				int count = unsignedByte(mem, src) & 0xc0;
				if (count == 0) {
					// Speed
					tr.speed[line] = unsignedByte(mem, src + 1);
					src += 2;
					// Without line shift
				} else if (count == 0x80 || count == 0xc0) {
					// Go to loop, or end - either way, that's the end of the track
					tr.len = line;
					break;
				}
				// count == 0x40 matches no branch here (as in the C++ original -
				// never produced by trackToAta()'s own encoder, only reachable
				// from a malformed/corrupted byte stream), which would loop here
				// without advancing src - preserved as-is, not hardened against,
				// matching the C++ source's own behavior exactly.
			}
		}
		return true;
	}

	private static int unsignedByte(byte[] mem, int index) {
		return mem[index] & 0xFF;
	}

	public boolean isValidChannel(int channel) {
		return channel >= 0 && channel < SONGTRACKS;
	}

	public boolean isValidTrack(int trackNumber) {
		return trackNumber >= 0 && trackNumber < TRACKSNUM;
	}

	public boolean isValidLine(int line) {
		return line >= 0 && line < ATARI_MAX_TRACK_LENGTH;
	}

	public boolean isValidNote(int note) {
		return Notes.isValidNote(note);
	}

	public boolean isValidInstrument(int instr) {
		return instr >= 0 && instr < INSTRSNUM;
	}

	public boolean isValidVolume(int vol) {
		return vol >= 0 && vol <= MAXVOLUME;
	}

	public boolean isValidSpeed(int speed) {
		return speed >= 0 && speed < TRACKMAXSPEED;
	}

	public boolean isValidLength(int len) {
		return len > 0 && len <= ATARI_MAX_TRACK_LENGTH;
	}

	public boolean isValidGo(int go) {
		return isValidLine(go);
	}

	public int getNote(int trackNumber, int line) {
		return isValidTrack(trackNumber) && isValidLine(line) ? track[trackNumber].note[line] : -1;
	}

	public int getInstr(int trackNumber, int line) {
		return isValidTrack(trackNumber) && isValidLine(line) ? track[trackNumber].instr[line] : -1;
	}

	public int getVol(int trackNumber, int line) {
		return isValidTrack(trackNumber) && isValidLine(line) ? track[trackNumber].volume[line] : -1;
	}

	public int getSpeed(int trackNumber, int line) {
		return isValidTrack(trackNumber) && isValidLine(line) ? track[trackNumber].speed[line] : -1;
	}

	public void getNoteInstrVolSpeed(int[] buff, int trackNumber, int line) {
		if (!(isValidTrack(trackNumber) && isValidLine(line))) {
			return;
		}
		buff[0] = track[trackNumber].note[line];
		buff[1] = track[trackNumber].instr[line];
		buff[2] = track[trackNumber].volume[line];
		buff[3] = track[trackNumber].speed[line];
	}

	public Track getTrack(int trackNumber) {
		return isValidTrack(trackNumber) ? track[trackNumber] : null;
	}

	public int getMaxTrackLength() {
		return maxTrackLength;
	}

	public void setMaxTrackLength(int length) {
		if (isValidLength(length)) {
			maxTrackLength = length;
		}
	}
}
