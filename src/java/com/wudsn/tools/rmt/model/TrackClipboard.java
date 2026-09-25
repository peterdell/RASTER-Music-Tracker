package com.wudsn.tools.rmt.model;

/**
 * Ported from CTrackClipboard (src/cpp/Clipboard.h, Clipboard.cpp,
 * ClipboardCore.cpp) - the subset already exercised, indirectly, by
 * {@code SongEditingTests.cpp}'s {@code BlockSetBeginEndDeselectAndIsBlockSelectedRoundTrip}/
 * {@code BlockPastePastesTheCopiedTrackOntoTheActiveTrack} tests (via
 * {@code CSong}'s {@code BLOCKSETBEGIN}/{@code BLOCKSETEND}/
 * {@code BLOCKDESELECT}/{@code ISBLOCKSELECTED}/{@code BlockPaste} delegate
 * wrappers, ported here as {@link Song#songBlockSetBegin}/
 * {@link Song#songBlockSetEnd}/{@link Song#blockDeselect}/
 * {@link Song#isBlockSelected}/{@link Song#blockPaste}): the constructor,
 * {@link #isBlockSelected}, {@link #isTrackSelected} (an internal guard
 * inside the tested methods, never itself directly asserted on), {@link #clear},
 * {@link #blockSetBegin}, {@link #blockSetEnd}, {@link #blockDeselect},
 * {@link #blockInitBase} (an internal helper called by {@link #blockSetBegin}),
 * {@link #blockCopyToClipboard}, {@link #blockPasteToTrack}, {@link #getFromTo}.
 *
 * <p><b>{@code BlockAllOnOff}/{@code BlockExchangeClipboard}/{@code BlockClear}/
 * {@code BlockRestoreFromBackup}/{@code BlockNoteTransposition}/
 * {@code BlockInstrumentChange}/{@code BlockVolumeChange} are NOT ported</b> -
 * confirmed to have no test coverage anywhere (direct or indirect) at the
 * time of this port; porting them would need new C++ characterization
 * tests first, a decision deferred to a future session (see
 * {@code plans/JAVA_SONGEDITING_PLAN.md}). {@code BlockEffect} stays
 * deferred indefinitely (a real MFC dialog, confirmed on the C++ side to
 * have no extractable logic).
 *
 * <p><b>The "{@code g_Song}-reads-a-global" wrinkle</b>: {@link #blockSetBegin}/
 * {@link #blockPasteToTrack} take an explicit {@link Song} parameter for
 * the same reason C++'s {@code BlockSetBegin}/{@code BlockPasteToTrack}
 * read the global {@code g_Song} rather than any particular {@code CSong}
 * instance - a pre-existing coupling in {@code CTrackClipboard} itself
 * (see {@code SongEditing.cpp}'s own header comment), not introduced by
 * this port. {@link Tracks}/{@code tracks4_8} likewise become explicit
 * parameters, matching this project's established idiom for globals a
 * ported method needs.
 *
 * <p><b>{@code m_trackcopy}/copying entire tracks is NOT modeled here</b> -
 * {@link Song}'s {@code trackCopy}/{@code trackPaste}/{@code trackCut}
 * (ported earlier this effort) already model that single-track clipboard
 * slot directly as {@link Song}'s own {@code trackCopyClipboard} field,
 * needing nothing from this class.
 *
 * <p><b>{@link #blockDeselect} omits C++'s {@code ClearStatusBar()} call</b> -
 * GUI-only, no Java equivalent (matches this project's established
 * omission of status-bar calls elsewhere, e.g. {@code BlockNoteTransposition}'s
 * {@code SetStatusBarText}).
 */
public final class TrackClipboard {

	private int selCol;
	private int selTrack;
	private int selSongLine;
	private int selFrom;
	private int selTo;
	private final Track track = new Track();

	private final Track trackBase = new Track();
	private boolean all;
	private int instrBase;
	private int changeNote;
	private int changeInstr;
	private int changeVolume;

	private final Track trackBackup = new Track();

	public TrackClipboard() {
		track.len = -1; // For copying a complete track with loops, etc.
		trackBackup.len = -1; // Back up
		all = true; // true = all events / false = only for events with the same instrument as currently set
		clear();
	}

	public boolean isBlockSelected() {
		return selCol >= 0;
	}

	public boolean isTrackSelected() {
		return selTrack >= 0;
	}

	public void clear() {
		selCol = selTrack = -1;
		clearTrack();
	}

	private void clearTrack() {
		track.len = -1;
		track.go = -1;
		for (int i = 0; i < Track.TRACKLEN; i++) {
			track.note[i] = -1;
			track.instr[i] = -1;
			track.volume[i] = -1;
			track.speed[i] = -1;
		}
	}

	public boolean blockSetBegin(int col, int trackNumber, int line, Tracks tracks, Song song) {
		Track tt = tracks.getTrack(trackNumber);

		// Only process further if data is valid and within boundaries
		if (tt != null && (selCol != col || selTrack != trackNumber) && tracks.isValidChannel(col) && tracks.isValidLine(line)) {
			// Newly marked start of block
			selCol = col;
			selTrack = trackNumber;
			selSongLine = song.songGetActiveLine();
			selFrom = selTo = line;

			// Keep the track as it was now
			trackBackup.copyFrom(tt);

			// And initializes a base track
			blockInitBase(trackNumber, tracks);
			return true;
		}

		return false;
	}

	public boolean blockSetEnd(int line, Tracks tracks) {
		if (tracks.isValidLine(line)) {
			selTo = line;
			return true;
		}

		return false;
	}

	public void blockDeselect() {
		if (isBlockSelected()) {
			selCol = -1;
		}
	}

	private void blockInitBase(int trackNumber, Tracks tracks) {
		Track tt = tracks.getTrack(trackNumber);

		if (tt != null) {
			trackBase.copyFrom(tt);
			changeNote = 0;
			changeInstr = 0;
			changeVolume = 0;
			instrBase = -1;
		}
	}

	public int blockCopyToClipboard(Tracks tracks) {
		Track ts = tracks.getTrack(selTrack);

		// Process further only if these conditions are respected
		if (ts != null && isBlockSelected() && isTrackSelected()) {
			// Clear the track data first
			clearTrack();

			// Get the selection block position and length
			FromTo fromTo = getFromTo();

			// Copy data from selected lines to clipboard starting at the first line
			int i = 0;
			for (int line = fromTo.from(); line <= fromTo.to(); line++, i++) {
				int xline = (line < ts.len || ts.go < 0) ? line : ((line - ts.len) % (ts.len - ts.go)) + ts.go;
				track.note[i] = (line < ts.len || ts.go >= 0) ? ts.note[xline] : -1;
				track.instr[i] = (line < ts.len || ts.go >= 0) ? ts.instr[xline] : -1;
				track.volume[i] = (line < ts.len || ts.go >= 0) ? ts.volume[xline] : -1;
				track.speed[i] = (line < ts.len || ts.go >= 0) ? ts.speed[xline] : -1;
			}

			// Return the length of copied track data
			track.len = i;
			return i;
		}

		return 0;
	}

	public int blockPasteToTrack(int trackNumber, int line, int special, Tracks tracks, Song song, int tracks4_8) {
		Track ts = track;
		Track td = tracks.getTrack(trackNumber);

		int bfro = -1;
		int bto = -1;
		int smallmax = song.getSmallestMaxtracklen(selSongLine, tracks4_8);

		// A block is selected, so the Paste will be placed in the line position
		if (isBlockSelected() && isTrackSelected()) {
			td = tracks.getTrack(selTrack);
			FromTo fromTo = getFromTo();
			bfro = fromTo.from();
			bto = fromTo.to();
		}

		if (ts != null && td != null) {
			// To exchange data in the optimal way, wise loops must be expanded first
			tracks.trackExpandLoop(ts);
			tracks.trackExpandLoop(td);

			int linemax;
			// Block selected (continued)
			if (bfro >= 0) {
				line = bfro;
				linemax = bto + 1;
			} else {
				linemax = line + ts.len;
			}

			if (linemax > smallmax) {
				linemax = smallmax;
			}

			if (line > td.len) {
				// If it makes a paste under the --end-- line, empty the gap between --end-- and the end of the place where it pastes
				for (int i = td.len; i < linemax; i++) {
					td.note[i] = td.instr[i] = td.volume[i] = td.speed[i] = -1;
				}
			}

			for (int i = line, j = 0; i < linemax; i++, j++) {
				switch (special) {
				case 0: // Normal paste
					td.note[i] = ts.note[j];
					td.instr[i] = ts.instr[j];
					td.volume[i] = ts.volume[j];
					td.speed[i] = ts.speed[j];
					break;

				case 1: // Merge
					if (tracks.isValidNote(ts.note[j]) && tracks.isValidInstrument(ts.instr[j]) && tracks.isValidVolume(ts.volume[j])) {
						td.note[i] = ts.note[j];
						td.instr[i] = ts.instr[j];
						td.volume[i] = ts.volume[j];
					}
					if (tracks.isValidVolume(ts.volume[j])) {
						td.volume[i] = ts.volume[j];
					}
					if (tracks.isValidSpeed(ts.speed[j])) {
						td.speed[i] = ts.speed[j];
					}
					break;

				case 2: // Volumes only
					if (tracks.isValidVolume(ts.volume[j])) {
						td.volume[i] = ts.volume[j]; // If the source volume is non-negative, it writes it
					} else if (!tracks.isValidNote(td.note[i]) && !tracks.isValidInstrument(td.instr[i])) {
						td.volume[i] = -1; // Delete only on separate volumes
					}
					break;

				case 3: // Speeds only
					td.speed[i] = ts.speed[j];
					break;

				default:
					break;
				}
			}

			// If it's beyond the end of the track, extend its length
			if (linemax > td.len) {
				td.len = linemax;
			}

			// When it was a paste into a block, it returns 0
			return (bfro >= 0) ? 0 : linemax - line;
		}

		return 0;
	}

	/** {@code from}/{@code to} were C++'s {@code int&} output parameters. */
	public record FromTo(int from, int to) {
	}

	public FromTo getFromTo() {
		int from = 1;
		int to = 0;

		if (isBlockSelected()) {
			from = Math.min(selFrom, selTo);
			to = Math.max(selFrom, selTo);
		}

		return new FromTo(from, to);
	}

	/** Needed by {@link Song#play}'s {@code PLAY_BLOCK} branch, matching C++'s direct read of {@code m_selsongline}. */
	public int getSelSongLine() {
		return selSongLine;
	}
}
