package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.Notes;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.Track;
import org.atari.raster.rmt.model.Tracks;

/**
 * Ported from CTracksControl (src/cpp/TracksControl.h/.cpp) - draws one
 * track column's header and one track line of the Edit Tracks screen.
 * C++'s constructor takes a {@code CCanvas} it never uses; only the
 * {@code CCanvasXY} set afterward is drawn with, so only that is kept.
 * The globals it reads ({@code g_activepart}, {@code IsProveMode()} via
 * {@code CanvasXY}, the highlight/notation options) come from the
 * {@link UiState}/{@link RmtOptions} passed in.
 */
public final class TracksControl {

	private final CanvasXY canvasXY;
	private final UiState uiState;
	private final RmtOptions options;

	public TracksControl(CanvasXY canvasXY, UiState uiState, RmtOptions options) {
		this.canvasXY = canvasXY;
		this.uiState = uiState;
		this.options = options;
	}

	/** The "XX: LL-GG <>" header line (plus the mini "FX1" above its right end) of the track {@code tr} in the column at {@code x}. */
	public void drawTrackHeader(Tracks tracks, int x, int y, int tr, TextColor col) {
		Track tt = tracks.getTrack(tr);
		String s = "--  -----";

		if (tt != null) {
			s = tracks.isValidTrack(tr) ? String.format("%02X: ", tr) : "--  ";
			if (tracks.isEmptyTrack(tr)) {
				s += "EMPTY";
			} else {
				s += tracks.isValidLength(tt.len) ? String.format("%02X-", tt.len) : "---";
				s += tracks.isValidGo(tt.go) ? String.format("%02X", tt.go) : "--";
			}
		}

		canvasXY.textXY(s, x, y, col);
		canvasXY.textXYSelN("<>", -1, x + 8 * 11, y, col);
		canvasXY.textMiniXY("FX1", x + 8 * 10, y - 8, TextMiniColor.GRAY); // C++'s default color parameter
	}

	/**
	 * One line of one track column: {@code " NNN II vV FXX"} with the loop
	 * arrows in column 0, colored by highlight/play/active/out-of-bounds
	 * priority, with the cursor's sub-column {@code acu} selected when this
	 * is the active line of the active column and the tracks part has focus.
	 */
	public void drawTrackLine(Tracks tracks, int col, int x, int y, int tr, int line, int aline, int cactview, int pline, boolean isactive, int acu, int oob, int notation) {
		char[] s = " \b\b\b \b\b \b\b \b\b\b".toCharArray(); // glyph 8 = the "no track" dot
		int len = -1;
		int last = -1;
		int go = -1;
		TextColor color = TextColor.WHITE;
		int n;
		int xline;

		Track tt = tracks.getTrack(tr);
		if (tt != null) {
			s = " --- -- -- ---".toCharArray();

			len = tt.len;
			go = tt.go;
			last = (go >= 0) ? tracks.getMaxTrackLength() : len;
			xline = (line < len || go < 0) ? line : ((line - len) % (len - go)) + go;

			if (go >= 0) {
				s[0] = (line == len - 1) ? '\u0010' : ' ';
			} // Left-up arrow or nothing
			if (line == go) {
				s[0] = (line == len - 1) ? '\u0011' : '\u000F';
			} // Left-up-right or up-right arrow

			if ((n = tt.note[xline]) >= 0) {
				int octave = (n / options.notesPerOctave) + 1 + 0x30; // Due to ASCII characters
				int note = n % options.notesPerOctave;

				String noteAndScale = Notes.getNoteAndScale(notation, note);
				s[1] = noteAndScale.charAt(0); // B
				s[2] = noteAndScale.charAt(1); // -
				s[3] = (char) octave; // 1
			}

			// Instrument
			if ((n = tt.instr[xline]) >= 0) {
				s[5] = Song.charH4(n);
				s[6] = Song.charL4(n);
			}

			// Volume
			if ((n = tt.volume[xline]) >= 0) {
				s[8] = 'v';
				s[9] = Song.charL4(n);
			}

			// Speed
			if ((n = tt.speed[xline]) >= 0) {
				s[11] = 'F'; // Fxx is for speed commands, but eventually, more commands could be used...
				s[12] = Song.charH4(n);
				s[13] = Song.charL4(n);
			}

			// Display the line highlight colors only in valid patterns
			if (line % options.trackLineSecondaryHighlight == 0) {
				color = TextColor.GREEN;
			}
			if (line % options.trackLinePrimaryHighlight == 0) {
				color = TextColor.CYAN;
			}
		}

		// The displayed colors are set from lowest to highest priority, depending on the matching conditions
		if (line >= len) {
			color = TextColor.GRAY;
		}
		if (line == pline) {
			color = TextColor.YELLOW;
		}
		if (line == aline) {
			color = uiState.editMode.isProveMode() ? TextColor.BLUE : TextColor.RED;
		}
		if (oob != 0) {
			color = TextColor.DARK_GRAY;
		}

		// Output the constructed row once it's ready, using the cursor position for highlighted column
		canvasXY.textXYCol(new String(s), x, y, uiState.activePart == Part.PART_TRACKS && (isactive && line == aline && oob == 0) ? acu : -1, color);

		// Mark the end of a pattern here, if it ends on the next line
		if (line + 1 == last && len > 0 && last != tracks.getMaxTrackLength()) {
			canvasXY.textXY("\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B", x + 7, y + 13, (oob != 0) ? TextColor.DARK_GRAY : TextColor.WHITE);
		}
	}
}
