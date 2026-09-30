package org.atari.raster.rmt.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Locale;

import org.atari.raster.rmt.model.EditArea;
import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.Instrument;
import org.atari.raster.rmt.model.Instruments;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.TrackClipboard;
import org.atari.raster.rmt.model.Tracks;

/**
 * Ported from CSongUI (src/cpp/SongUI.h/.cpp) - draws the song-level
 * screen elements: the info area top-left, the SONG block top-right, the
 * TIME/BPM counter, and the Edit Tracks screen. The many globals the C++
 * reads ({@code g_tracks4_8}, {@code g_activepart}, {@code g_view},
 * {@code g_trackLine*Highlight}, {@code g_ChannelControl},
 * {@code g_TrackClipboard}, ...) come from the {@link RmtSession} - its
 * model objects, {@link UiState} and {@link RmtOptions}.
 *
 * <p>All six drawing methods are ported ({@link #drawInstrument} delegates
 * to {@link InstrumentsUI}, {@link #drawVolumeAnalyzer} embeds
 * {@link PokeyView}); the one piece still missing is the Pokey Explorer
 * rows of the POKEY view - see {@link PokeyView}.
 *
 * <p>Deviation: C++ keeps the BPM averaging window ({@code m_avgspeed[8]})
 * on {@code CSong}, although only {@code DrawPlayTimeCounter()} ever reads
 * or writes it; it lives here as {@link #avgSpeed} so the model stays
 * display-free. The {@code MapVirtualKeyEx(vk, MAPVK_VK_TO_CHAR)} call in
 * the debug line has no Java equivalent; {@link #vkToChar} approximates it
 * (letters, digits and space map to themselves, everything else to 0,
 * which - as in C++'s {@code CString::Format("%c", 0)} - ends the text).
 */
public final class SongUI {

	private final RmtSession session;
	private CanvasXY canvasXY;
	private TracksControl tracksControl;
	private InstrumentsUI instrumentsUI;

	/** {@code CSong::m_avgspeed} - see the class javadoc. */
	private final int[] avgSpeed = new int[8];

	public SongUI(RmtSession session) {
		this.session = session;
	}

	public void setCanvas(CanvasXY canvasXY) {
		this.canvasXY = canvasXY;
		this.tracksControl = new TracksControl(canvasXY, session.uiState, session.options);
		this.instrumentsUI = new InstrumentsUI(session, canvasXY);
	}

	private boolean isProveMode() {
		return session.uiState.editMode.isProveMode();
	}

	private boolean isStereo() {
		return session.song.isStereo(session.tracks4_8);
	}

	private boolean isPlaying() {
		return session.song.getPlayMode() != PlayMode.PLAY_STOP;
	}

	private static String hex2(int value) {
		return String.format("%02X", value & 0xFF);
	}

	/** C++'s {@code char[]} name fields as a string: everything up to the first NUL. */
	static String nameToString(char[] name) {
		int end = 0;
		while (end < name.length && name[end] != '\0') {
			end++;
		}
		return new String(name, 0, end);
	}

	/** The SONG block's x position, displaced by window width, mono/stereo and the active screen the same way in every method that draws near it. */
	private int getSongOffsetX() {
		UiState ui = session.uiState;
		boolean stereo = isStereo();
		final int MINIMAL_WIDTH_INSTRUMENTS = 1220;
		final int WINDOW_OFFSET = (ui.width < 1320 && stereo && ui.activeTi == Part.PART_TRACKS) ? -250 : 0; // test displacement with the window size
		int INSTRUMENT_OFFSET = (ui.activeTi == Part.PART_INSTRUMENTS && stereo) ? -250 : 0;
		if (!stereo && ui.activeTi == Part.PART_INSTRUMENTS && ui.width > MINIMAL_WIDTH_INSTRUMENTS - 220) {
			INSTRUMENT_OFFSET = 260;
		}
		return RmtScreenLayout.SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((!stereo) ? -200 : 310); // displace the SONG block depending on certain parameters
	}

	/** {@code DrawSong()}'s {@code WINDOW_OFFSET}: non-zero when a stereo tracks screen is narrower than 1320 logical pixels, which compacts the SONG block to 5 lines. */
	private boolean isCompactSongBlock() {
		UiState ui = session.uiState;
		return ui.width < 1320 && isStereo() && ui.activeTi == Part.PART_TRACKS;
	}

	/**
	 * {@code CRmtView::DrawAll()}: one whole frame into the canvas - the
	 * background, the secondary elements, then the primary screen (tracks or
	 * instrument) above everything. Lives here rather than on the panel so a
	 * frame can be rendered headless.
	 */
	public void drawAll() {
		UiState ui = session.uiState;
		// Clear the screen with the background color
		canvasXY.fillSolidRect(0, 0, ui.width, ui.height, RgbColor.BACKGROUND);
		// Draw the secondary screen elements
		drawInfo();
		drawSong();
		drawVolumeAnalyzer();
		drawPlayTimeCounter();

		// Draw the primary screen above everything
		if (ui.activeTi == Part.PART_TRACKS) {
			drawTracks();
		} else {
			drawInstrument();
		}
	}

	private static final int ANALYZER_S = 6;
	private static final int ANALYZER_H = 5;
	private static final int ANALYZER_HP = 8;

	/** Draw a bridge between two columns (on the tracks view) */
	private void drawTracksHook(int ANALYZER_X, int ANALYZER_Y, int g1, int g2, int yUp) {
		canvasXY.moveTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - 1);
		canvasXY.lineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - yUp);
		canvasXY.lineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y - yUp);
		canvasXY.lineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y);
	}

	/**
	 * Draw a volume analyser above each track, and the tuning/POKEY register
	 * panel ({@link PokeyView}) to the right of the tracks.
	 *
	 * <p>C++ also contains an "instrument edit mode" variant (smaller boxes
	 * above the SONG block, {@code ANALYZER2_*}, plus {@code DrawInstrumentHook})
	 * - but as an {@code else if (g_active_ti == PART_INSTRUMENTS)} nested
	 * <em>inside</em> the {@code if (g_active_ti == PART_TRACKS)} block, so it
	 * can never execute; the reference screenshots of the instrument screen
	 * confirm nothing is drawn there. Not ported. Likewise the
	 * {@code DEBUG_MEMORY} ({@link AtariView}) branch is a compile-time
	 * {@code FALSE} in C++ and is kept as such.
	 */
	public void drawVolumeAnalyzer() {
		if (!session.options.view.volumeAnalyzer) {
			return;
		} // the analyser won't be displayed without the setting enabled first

		Song song = session.song;
		UiState ui = session.uiState;
		int tracks4_8 = session.tracks4_8;
		final boolean stereo = isStereo();
		final int MINIMAL_WIDTH_TRACKS = (stereo && ui.activeTi == Part.PART_TRACKS) ? 1420 : 960;
		final int MINIMAL_WIDTH_INSTRUMENTS = 1220;
		final int SONG_OFFSET_X = getSongOffsetX();

		boolean viewPokeyRegisters = session.options.view.pokeyRegisters;
		boolean DEBUG_POKEY = viewPokeyRegisters; // registers debug display
		boolean DEBUG_MEMORY = false; // memory debug display

		// Hide if not enough space is available.
		if ((ui.width < MINIMAL_WIDTH_TRACKS && ui.activeTi == Part.PART_TRACKS) || (ui.width < MINIMAL_WIDTH_INSTRUMENTS && ui.activeTi == Part.PART_INSTRUMENTS)) {
			DEBUG_POKEY = DEBUG_MEMORY = false;
		}

		final int ANALYZER_X = RmtScreenLayout.TRACKS_X + 6 * 8 + 4; // 68
		final int ANALYZER_Y = RmtScreenLayout.TRACKS_Y - 8; // Line 8 = 128

		final int POKEY_VIEW_X = SONG_OFFSET_X + 6 * 8 - 32;
		final int POKEY_VIEW_Y = RmtScreenLayout.TRACKS_Y + 50;

		int audf;
		int audc;
		int vol;
		final int[] idx = { 0xd200, 0xd202, 0xd204, 0xd206, 0xd210, 0xd212, 0xd214, 0xd216 }; // AUDF and AUDC for mono and stereo
		int[] col = new int[8];
		int[] R = new int[8];
		int[] G = new int[8];
		int yUp = 7;
		for (int i = 0; i < song.getTracks(tracks4_8); i++) {
			col[i] = 102;
			R[i] = 44;
			G[i] = 60;
		}
		int a;
		int b;
		Color acol;

		if (ui.activeTi == Part.PART_TRACKS) { // bigger look for track edit mode
			// In tracks drawing mode
			// Draw bridge connections between channels. For each connection we move 2 pixels up.
			// Max rise is 10 pixels
			final byte[] memory = session.atari.getMemory();

			// Clear the area where the analyser is to be drawn
			canvasXY.fillSolidRect(ANALYZER_X, ANALYZER_Y - ANALYZER_HP, tracks4_8 * 16 * 8 - 34, ANALYZER_H + ANALYZER_HP, RgbColor.BACKGROUND);

			// Left/Mono Channel
			// Draw which channels are joined by highpass filters or normal channel join
			a = memory[0xd208] & 0xFF; // AUDCTL @ $D208
			if ((a & 0x04) != 0) {
				col[2] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 2, yUp);
				yUp -= 2;
			} // High pass filter on channel 1, clocked by channel 3
			if ((a & 0x02) != 0) {
				col[3] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 1, 3, yUp);
				yUp -= 2;
			} // High pass filter on channel 3, clocked by channel 4
			if ((a & 0x10) != 0) {
				col[0] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 1, yUp);
				yUp -= 2;
			} // Join channels 1 + 2 (16 bit)
			if ((a & 0x08) != 0) {
				col[2] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 2, 3, yUp);
				yUp -= 2;
			} // Join channels 3 + 4 (16 bit)

			b = memory[0xd20f] & 0xFF; // SKCTL @ $D20F
			if (b == 0x8b) {
				col[1] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 1, yUp);
				yUp -= 2;
			} // Two tone mode (join channel 1 + 2)
			yUp = 7;

			// Stereo Channel
			a = memory[0xd218] & 0xFF; // AUDCTL2 @ $D218
			if ((a & 0x04) != 0) {
				col[2 + 4] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 2 + 4, yUp);
				yUp -= 2;
			} // High pass filter on channel 5 clocked by channel 7
			if ((a & 0x02) != 0) {
				col[3 + 4] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 1 + 4, 3 + 4, yUp);
				yUp -= 2;
			} // High pass filter on channel 7, clocked by channel 8
			if ((a & 0x10) != 0) {
				col[0 + 4] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp);
				yUp -= 2;
			} // Join channels 5 + 6 (16 bit)
			if ((a & 0x08) != 0) {
				col[2 + 4] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 2 + 4, 3 + 4, yUp);
				yUp -= 2;
			} // Join channels 7 + 8 (16 bit)

			b = memory[0xd21f] & 0xFF; // SKCTL2 @ $D21F
			if (b == 0x8b) {
				col[1 + 4] = RgbColor.COL_BLOCK;
				drawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp);
				yUp -= 2;
			} // Two tone mode (join channel 5 + 6)

			for (int channelNr = 0; channelNr < song.getTracks(tracks4_8); channelNr++) {
				audf = memory[idx[channelNr]] & 0xFF; // Get the frequency
				audc = memory[idx[channelNr] + 1] & 0xFF; // Get audio control, Bits: 0-3 = volume, 4 = Volume only, 5-7 = Distortion
				int skctl1 = memory[0xd20f] & 0xFF; // Two tone mode Mono
				int skctl2 = memory[0xd21f] & 0xFF; // Two tone mode Stereo

				vol = audc & 0x0f; // Volume in lower nibble
				a = channelNr * 16 * 8; // X offset

				// Draw the background box of the volume analyser for this channel
				// 15 unit wide, each unit is 6 pixels (ANALYZER_S)
				// Default color is RGB(44, 60, 102) - Dark blue
				canvasXY.fillSolidRect(ANALYZER_X + a + 2, ANALYZER_Y, 15 * ANALYZER_S, ANALYZER_H, new Color(R[channelNr], G[channelNr], col[channelNr]));

				// Determine the color of the channels volume bar: Normal, mute or Volume only
				acol = session.channelControl.isChannelOn(channelNr) ? (((audc & 0x10) != 0) ? RgbColor.VOLUME_ONLY : RgbColor.NORMAL) : RgbColor.MUTE;

				// Check if its a two tone channel (1 or 5)
				if (session.channelControl.isChannelOn(channelNr) && ((skctl1 == 0x8b && channelNr == 0) || (skctl2 == 0x8b && channelNr == 4))) {
					acol = RgbColor.TWO_TONE;
				}

				// Draw the volume bar in the selected color
				if (vol != 0) {
					canvasXY.fillSolidRect(ANALYZER_X + a + 3 + (15 - vol) * ANALYZER_S / 2, ANALYZER_Y, vol * ANALYZER_S, ANALYZER_H, acol);
				}

				// Draw the frequency and audio control numbers for this channel
				if (viewPokeyRegisters) {
					canvasXY.numberMiniXY(audf, ANALYZER_X + 10 + a + 17, ANALYZER_Y - 8, TextMiniColor.GRAY);
					canvasXY.numberMiniXY(audc, ANALYZER_X + 36 + a + 17, ANALYZER_Y - 8, TextMiniColor.GRAY);
				}
			}
			if (viewPokeyRegisters) {
				// Draw the AUDCTL (audio control) register value
				canvasXY.numberMiniXY(memory[0xd208] & 0xFF, ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 8, TextMiniColor.GRAY); // Mono
				if (stereo) {
					canvasXY.numberMiniXY(memory[0xd218] & 0xFF, ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 8, TextMiniColor.GRAY);
				} // Stereo

				// Draw the SKCTL (Two tone control/Serial port control) register value
				canvasXY.numberMiniXY(memory[0xd20f] & 0xFF, ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 0, TextMiniColor.GRAY); // Mono
				if (stereo) {
					canvasXY.numberMiniXY(memory[0xd21f] & 0xFF, ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 0, TextMiniColor.GRAY); // Stereo
				}
			}
			// (C++'s unreachable "else if (g_active_ti == PART_INSTRUMENTS)" branch would sit here - see the javadoc)

			if (DEBUG_POKEY) {
				Canvas pokeyCanvas = new Canvas(canvasXY, POKEY_VIEW_X, POKEY_VIEW_Y);
				PokeyView pokeyView = new PokeyView(pokeyCanvas);
				pokeyView.draw(stereo, session.tuning, session.tuningSettings, session.options.notesPerOctave, session.atari, session.uiState.editMode == EditMode.POKEY_EXPLORER_MODE, session.pokeyController);
			}

			if (DEBUG_MEMORY) {
				Canvas atariCanvas = new Canvas(canvasXY, POKEY_VIEW_X, POKEY_VIEW_Y + 192);
				AtariView atariView = new AtariView(atariCanvas);
				atariView.draw(session.atari);
			}
		}
	}

	/** The instrument editor screen ({@code CInstruments::DrawInstrument} of the active instrument). */
	public void drawInstrument() {
		instrumentsUI.drawInstrument(session.song.getActiveInstr());
	}

	/**
	 * Draw a song's line information
	 *   L1 L2 L3 L4 R1 R2 R3 R4
	 *   00 01 02 03 -- -- -- --
	 * &gt; 04 05 06 07 -- -- -- --
	 *   -- -- -- -- -- -- -- --
	 * Taking into account that during playback the lines can be smooth scrolled.
	 */
	public void drawSong() {
		Song song = session.song;
		UiState ui = session.uiState;
		int tracks4_8 = session.tracks4_8;
		int[][] songLines = song.getSong();
		int[] songGo = song.getSongGo();
		int line;
		int i;
		int j;
		int k;
		int y;
		int t;
		TextColor color;

		boolean smoothScroll = session.options.view.smoothScrolling;
		boolean compact = isCompactSongBlock();
		final int SONG_OFFSET_X = getSongOffsetX();

		int activeSmooth = (smoothScroll && isPlaying() && song.getFollowPlayMode()) ? 1 : 0; // could also be used as an offset
		int patternLen = 0;
		int smoothY = 0;

		if (activeSmooth != 0) {
			// Map the current track's playline into the number range -8 -> 7
			// This gives a Y position shift to draw the song line info
			// y_offset = line * 16 / track_length
			patternLen = song.getSmallestMaxtracklen(song.songGetPlayLine(), tracks4_8);
			if (patternLen == 0) {
				patternLen = session.tracks.getMaxTrackLength(); // fallback to whatever is in memory instead if the value returned is invalid
			}
			smoothY = (song.getPlayLine() * 16 / patternLen) - 8;
		}
		y = RmtScreenLayout.SONG_Y + (1 - activeSmooth) * 16 - smoothY;

		int linesCount = compact ? 5 : 9;

		for (i = 0; i < linesCount + activeSmooth * 2; i++, y += 16) {
			int linesOffset = compact ? -2 : -4;
			line = song.songGetActiveLine() + i + linesOffset - activeSmooth;
			boolean isOutOfBounds = false;

			// roll over the songline if it is out of bounds
			if (line < 0 || line > 255) {
				line += 256;
				line %= 256;
				isOutOfBounds = true;
			}
			// Draw either "XX: -- -- -- -- ..." or "Go to line XX"

			if ((j = songGo[line]) >= 0) { // there is a GO to line
				// Draw: "Go to line"
				color = isOutOfBounds ? TextColor.DARK_GRAY : TextColor.TURQUOISE; // turquoise text, blank tiles to mask text if needed, else gray if out of bounds
				canvasXY.textXY("GO\u001fTO\u001fLINE", SONG_OFFSET_X + 16, y, color);

				// Draw: "XX"
				color = isOutOfBounds ? TextColor.DARK_GRAY : TextColor.WHITE; // white, for the number used, or gray if out of bounds
				if (line == song.songGetActiveLine()) {
					if (isProveMode()) {
						color = (ui.activePart == Part.PART_SONG) ? TextColor.SELECTED_PROVE : TextColor.BLUE;
					} else {
						color = (ui.activePart == Part.PART_SONG) ? TextColor.SELECTED : TextColor.RED;
					}
				}
				canvasXY.textXY(hex2(j), SONG_OFFSET_X + 16 + 11 * 8, y, color);
			} else {
				// Draw the line number XX: (current line = YELLOW, song line = WHITE, out of bounds = TURQUOISE)
				color = (line == song.songGetPlayLine()) ? TextColor.YELLOW : TextColor.WHITE;
				if (isOutOfBounds) {
					color = TextColor.DARK_GRAY; // darker gray, out of bounds
				}
				canvasXY.textXY(hex2(line) + ":", SONG_OFFSET_X + 16, y, color);

				// For each track that is part of the song draw its number
				for (j = 0, k = 32; j < tracks4_8; j++, k += 24) {
					String number = ((t = songLines[line][j]) >= 0) ? hex2(t) : "--"; // No track here so draw "--"

					if (line == song.songGetActiveLine() && j == song.getActiveColumn()) {
						if (isProveMode()) {
							color = (ui.activePart == Part.PART_SONG) ? TextColor.SELECTED_PROVE : TextColor.BLUE;
						} else {
							color = (ui.activePart == Part.PART_SONG) ? TextColor.SELECTED : TextColor.RED;
						}
					} else {
						color = (line == song.songGetPlayLine()) ? TextColor.YELLOW : TextColor.WHITE;
					}
					if (isOutOfBounds) {
						color = TextColor.DARK_GRAY; // darker gray, out of bounds
					}
					canvasXY.textXY(number, SONG_OFFSET_X + 16 + k, y, color);
				}
			}
		}
		// Draw an arrow pointing to the current song line
		color = isProveMode() ? TextColor.BLUE : TextColor.RED;
		int arrowPos = compact ? RmtScreenLayout.SONG_Y + 48 : RmtScreenLayout.SONG_Y + 80;
		canvasXY.textXY("\u0004\u0005", SONG_OFFSET_X, arrowPos, color);

		if (isStereo()) { // a line delimiting the boundary between left/right
			int fl = 32;
			int tl = 32 + linesCount * 16;
			int x = RmtScreenLayout.SONG_Y + 80 + 5 * 8 + 3 + SONG_OFFSET_X;

			canvasXY.moveTo(x, fl);
			canvasXY.lineTo(x, tl);
		}

		// Draw mask rectangles over the extra pixels above and below the song lines.
		// This gets rid of the pixels we dont want to see with smooth scrolling
		int width = 8 * (isStereo() ? 30 : 18);
		int height = 32;
		canvasXY.fillSolidRect(SONG_OFFSET_X, 0, width, height, RgbColor.BACKGROUND); // top
		canvasXY.fillSolidRect(SONG_OFFSET_X, linesCount * 16 + 32, width, height, RgbColor.BACKGROUND); // bottom

		canvasXY.textXY("SONG", SONG_OFFSET_X + 8, RmtScreenLayout.SONG_Y, TextColor.WHITE);

		// print L1 .. L4 R1 .. R4 with highlighted current track
		k = SONG_OFFSET_X + 6 * 8;
		for (i = 0; i < 4; i++, k += 24) {
			canvasXY.textXY("L" + (char) (i + '1'), k, RmtScreenLayout.SONG_Y, getChannelNameColor(i)); // character 1-4
		}
		for (i = 4; i < song.getTracks(tracks4_8); i++, k += 24) {
			canvasXY.textXY("R" + (char) (i + 49 - 4), k, RmtScreenLayout.SONG_Y, getChannelNameColor(i)); // character 1-4
		}
	}

	/** The L1..R4 header color: active channel highlighted, switched off channels in gray. */
	private TextColor getChannelNameColor(int channel) {
		if (session.channelControl.isChannelOn(channel)) {
			if (session.song.getActiveColumn() == channel) {
				return isProveMode() ? TextColor.BLUE : TextColor.RED; // active channel highlight
			}
			return TextColor.WHITE; // normal channel
		}
		return TextColor.GRAY; // switched off channels are in gray
	}

	/** {@code GetTracklineText()}: a track line number as two characters - hex, or the alternative "bar/beat" numbering. Empty for an out-of-range line. */
	String getTracklineText(int line) {
		if (line < 0 || line > 0xff) {
			return "";
		}
		RmtOptions options = session.options;
		if (options.trackLineAltNumbering) {
			int a = line / options.trackLinePrimaryHighlight;
			if (a >= 35) {
				a = (a - 35) % 26 + 'a' - '9' + 1;
			}
			int b = line % options.trackLinePrimaryHighlight;
			if (b >= 35) {
				b = (b - 35) % 26 + 'a' - '9' + 1;
			}
			if (a <= 8) {
				a = '1' + a;
			} else {
				a = 'A' - 9 + a;
			}
			if (b <= 8) {
				b = '1' + b;
			} else {
				b = 'A' - 9 + b;
			}
			return new String(new char[] { (char) a, (char) b });
		}
		return hex2(line);
	}

	private static int wrapSongline(int sl) {
		if (sl < 0 || sl > 255) {
			sl += 256;
			sl %= 256;
		}
		return sl;
	}

	public void drawTracks() {
		final String tnames = "L1L2L3L4R1R2R3R4";
		Song song = session.song;
		Tracks tracks = session.tracks;
		TrackClipboard clipboard = session.clipboard;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		int tracks4_8 = session.tracks4_8;
		int[][] songLines = song.getSong();
		int[] songGo = song.getSongGo();
		int i;
		int x;
		int y;
		int tr;
		int line;
		TextColor color;
		int t;

		// caching certain global variables makes sure they remain the same until the function finishes drawing the tracks
		// this appears to be related to routine timing, and might actually explain why certain bugs seem to happen randomly
		int trackActiveLine = song.getActiveLine();
		int trackPlayLine = song.getPlayLine();
		int songActiveLine = song.songGetActiveLine();
		int songPlayLine = song.songGetPlayLine();
		int speed = song.getSpeed();
		int speeda = song.getSpeeda();

		// coordinates for only the TRACKS width block rendering
		int maskX = (!isStereo()) ? RmtScreenLayout.TRACKS_X + (93 - 4 * 11) * 11 - 4 : RmtScreenLayout.TRACKS_X + (93 + 3) * 11 - 8;

		if (song.songGetGo() >= 0) { // it's a GOTO line, it won't draw tracks
			int TRACKS_OFFSET = isStereo() ? 62 : 30;
			canvasXY.textXY("GO TO LINE ", RmtScreenLayout.TRACKS_X + TRACKS_OFFSET * 8, RmtScreenLayout.TRACKS_Y + 8 * 16, TextColor.TURQUOISE);
			if (isProveMode()) {
				color = (ui.activePart == Part.PART_TRACKS) ? TextColor.SELECTED_PROVE : TextColor.BLUE;
			} else {
				color = (ui.activePart == Part.PART_TRACKS) ? TextColor.SELECTED : TextColor.RED;
			}
			canvasXY.textXY(hex2(song.songGetGo()), RmtScreenLayout.TRACKS_X + TRACKS_OFFSET * 8 + 11 * 8, RmtScreenLayout.TRACKS_Y + 8 * 16, color);
			return;
		}

		// the cursor position is alway centered regardless of the window size with this simple formula
		ui.cursorActView = trackActiveLine + 8 - ui.lineY;

		int activeSmooth = (options.view.smoothScrolling && isPlaying() && song.getFollowPlayMode() && speed > 1) ? 1 : 0; // could also be used as an offset
		int smoothY = (activeSmooth != 0) ? ((speeda * 16) / speed) - 8 : 0;
		if (smoothY > 8 || smoothY < -8) {
			activeSmooth = smoothY = 0; // prevents going out of bounds
		}
		y = (RmtScreenLayout.TRACKS_Y + (3 - activeSmooth) * 16) + smoothY;
		x = RmtScreenLayout.TRACKS_X + 5 * 8;

		char[] s = "--\u0002".toCharArray(); // 2 digits and the "|" tile on the right side

		boolean isGoto = false;

		int notation = options.getNotation();

		for (i = 0; i < ui.trackLines + activeSmooth * 2; i++, y += 16) {
			line = ui.cursorActView + i - 8 - activeSmooth; // 8 lines from above
			int oob = 0;

			int sl = songActiveLine; // offset by the oob songline counter when needed
			int ln = song.getSmallestMaxtracklen(sl, tracks4_8);

			if (line < 0) {
				do { // minusline:
					oob--;
					sl = wrapSongline(songActiveLine + oob);
					ln = song.getSmallestMaxtracklen(sl, tracks4_8);

					line += ln;
				} while (line < 0);
			}
			if (line >= ln) {
				do { // plusline:
					oob++;
					line -= ln;
					sl = wrapSongline(songActiveLine + oob);
					ln = song.getSmallestMaxtracklen(sl, tracks4_8);

					if (ln == 0) {
						isGoto = true;
						ln = tracks.getMaxTrackLength();
					}
				} while (line >= ln);
			}

			if (options.trackLineAltNumbering) {
				String stmp = getTracklineText(line);
				s[0] = (stmp.charAt(1) == '1') ? stmp.charAt(0) : ' ';
				s[1] = stmp.charAt(1);
			} else {
				s[0] = Song.charH4(line);
				s[1] = Song.charL4(line);
			}

			if (isGoto) {
				// mask out the first line
				canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X, y, maskX, 16, RgbColor.BACKGROUND);

				// get the songline that has the goto set
				sl = wrapSongline(songActiveLine + oob);

				// if the line is 2 patterns or more away, it must also be gray
				canvasXY.textXY("GO TO LINE ", RmtScreenLayout.TRACKS_X + 6 * 8, y, (oob - 1 != 0) ? TextColor.DARK_GRAY : TextColor.TURQUOISE);
				canvasXY.textXY(hex2(songGo[sl]), RmtScreenLayout.TRACKS_X + 17 * 8, y, (oob - 1 != 0) ? TextColor.DARK_GRAY : TextColor.WHITE);
				break;
			}

			color = TextColor.WHITE;
			if (line % options.trackLineSecondaryHighlight == 0) {
				color = TextColor.GREEN;
			}
			if (line % options.trackLinePrimaryHighlight == 0) {
				color = TextColor.CYAN;
			}
			if (line == trackPlayLine) {
				color = TextColor.YELLOW;
			}
			if (line == trackActiveLine) {
				color = isProveMode() ? TextColor.BLUE : TextColor.RED;
			}
			if (oob != 0) {
				color = TextColor.DARK_GRAY;
			}
			canvasXY.textXY(new String(s), RmtScreenLayout.TRACKS_X, y, color);

			for (int j = 0; j < tracks4_8; j++, x += 16 * 8) {
				// track in the current line of the song
				sl = wrapSongline(songActiveLine + oob);

				tr = songLines[sl][j];

				// is it playing?
				if (songPlayLine == songActiveLine) {
					t = trackPlayLine;
				} else {
					t = -1;
				}
				tracksControl.drawTrackLine(tracks, j, x, y, tr, line, trackActiveLine, ui.cursorActView, t, (song.getActiveColumn() == j), song.getTrackActiveCur(), oob, notation);
			}
			x = RmtScreenLayout.TRACKS_X + 5 * 8;
		}

		// mask rectangles for hiding extra rendered lines
		canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 1 * 16, maskX, 32, RgbColor.BACKGROUND);
		canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 2 * 16 + ((ui.trackLines + 1) * 16) + 1, maskX, 48, RgbColor.BACKGROUND);

		// tracks
		char[] header = "  TRACK XX   ".toCharArray();
		x = RmtScreenLayout.TRACKS_X + 5 * 8;
		y = (RmtScreenLayout.TRACKS_Y + 3 * 16) + smoothY;

		for (i = 0; i < tracks4_8; i++, x += 16 * 8) {
			header[8] = tnames.charAt(i * 2);
			header[9] = tnames.charAt(i * 2 + 1);

			color = session.channelControl.isChannelOn(i) ? TextColor.WHITE : TextColor.GRAY; // channels off are in gray
			canvasXY.textXY(new String(header), x, RmtScreenLayout.TRACKS_Y, color);

			// track in the current line of the song
			tr = songLines[songActiveLine][i];

			tracksControl.drawTrackHeader(tracks, x + 8, RmtScreenLayout.TRACKS_Y + 16, tr, color);
		}

		// lines delimiting the current line
		x = maskX;
		y = RmtScreenLayout.TRACKS_Y + 3 * 16 - 2 + ui.lineY * 16;

		canvasXY.moveTo(RmtScreenLayout.TRACKS_X, y);
		canvasXY.lineTo(x, y);
		canvasXY.moveTo(RmtScreenLayout.TRACKS_X, y + 19);
		canvasXY.lineTo(x, y + 19);

		// a line delimiting the boundary between left/right-- there is a bug with some tracks but the entire function needs to be rewritten anyway...
		if (isStereo()) {
			y = (RmtScreenLayout.TRACKS_Y + 3 * 16);
			int lineEnd = y + ui.trackLines * 16;

			if (isGoto) {
				lineEnd = y + (8 - ui.cursorActView + song.getSmallestMaxtracklen(songActiveLine, tracks4_8)) * 16 + smoothY;
			}

			canvasXY.moveTo(RmtScreenLayout.TRACKS_X + 50 * 11 - 3, y);
			canvasXY.lineTo(RmtScreenLayout.TRACKS_X + 50 * 11 - 3, lineEnd);
		}

		// mask out any extra pixels after rendering each elements
		canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 2 * 16, maskX, 16, RgbColor.BACKGROUND);
		canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 2 * 16 + (ui.trackLines + 1) * 16, maskX, 32, RgbColor.BACKGROUND);

		// selected block
		if (clipboard.isBlockSelected()) {
			x = RmtScreenLayout.TRACKS_X + 6 * 8 + clipboard.getSelCol() * 16 * 8 - 8;
			int xt = x + 14 * 8 + 8;

			y = (RmtScreenLayout.TRACKS_Y + 3 * 16) + smoothY;
			TrackClipboard.FromTo fromTo = clipboard.getFromTo();
			int bfro = fromTo.from();
			int bto = fromTo.to();

			int yf = bfro - ui.cursorActView + 8;
			int fls = 0;
			int yt = bto - ui.cursorActView + 8 + 1;
			int tls = 0;
			boolean p1 = true;
			boolean p2 = true;

			if (yf < 0) {
				yf = 0;
				p1 = false;
				fls = activeSmooth * 5;
			}
			if (yt > ui.trackLines) {
				yt = ui.trackLines;
				p2 = false;
				tls = activeSmooth * 7;
			}
			if (yf < ui.trackLines && yt > 0 && clipboard.getSelTrack() == song.songGetTrack(song.songGetActiveLine(), clipboard.getSelCol()) && clipboard.getSelSongLine() == song.songGetActiveLine()) {
				// a rectangle delimiting the selected block
				canvasXY.setLineColor(new Color(255, 255, 255)); // "redpen" (which is white)

				canvasXY.moveTo(x, y - 2 - fls + yf * 16);
				canvasXY.lineTo(x, y + 2 + tls + yt * 16);
				canvasXY.moveTo(xt, y - 2 - fls + yf * 16);
				canvasXY.lineTo(xt, y + 2 + tls + yt * 16);

				if (p1) {
					canvasXY.moveTo(x, y - 2 + yf * 16);
					canvasXY.lineTo(xt, y - 2 + yf * 16);
				}
				if (p2) {
					canvasXY.moveTo(x, y + 2 + yt * 16);
					canvasXY.lineTo(xt + 1, y + 2 + yt * 16);
				}

				canvasXY.setLineColor(RgbColor.LINES); // origpen
			}

			String s1 = getTracklineText(bfro);
			String s2 = getTracklineText(bto);

			// mask out any extra pixels after rendering the selection box before drawing the infos below
			if (activeSmooth != 0) {
				canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 2 * 16, maskX, 16, RgbColor.BACKGROUND);
				canvasXY.fillSolidRect(RmtScreenLayout.TRACKS_X - 8, RmtScreenLayout.TRACKS_Y + 2 * 16 + (ui.trackLines + 1) * 16, maskX, 16, RgbColor.BACKGROUND);
			}

			String tx = String.format("%d line(s) [%s-%s] selected in the pattern track %02X", bto - bfro + 1, s1, s2, clipboard.getSelTrack());
			canvasXY.textXY(tx, RmtScreenLayout.TRACKS_X + 4 * 8, RmtScreenLayout.TRACKS_Y + (4 + ui.trackLines) * 16, TextColor.WHITE);
			x = RmtScreenLayout.TRACKS_X + 4 * 8 + tx.length() * 8 + 8;

			if (clipboard.isAll()) {
				tx = "[edit ALL data]";
			} else {
				tx = String.format("[edit data ONLY for instrument %02X]", song.getActiveInstr());
			}
			canvasXY.textXY(tx, x, RmtScreenLayout.TRACKS_Y + (4 + ui.trackLines) * 16, TextColor.RED);
		}

		// Debug display at the bottom of the screen, this could be toggled on if needed
		if (options.view.debugDisplay) {
			final int width = (8 * 8);
			// Don't draw further more than what could fit on screen
			for (int n = 0; n < ui.width / width; n++) {
				String d;
				switch (n) {
				case 0 -> d = String.format("GW=%04d", ui.width);
				case 1 -> d = String.format("GH=%04d", ui.height);
				case 2 -> d = String.format("PX=%04d", ui.mouseX);
				case 3 -> d = String.format("PY=%04d", ui.mouseY);
				case 4 -> d = String.format("MB=%02d", ui.mouseButton);
				case 5 -> d = String.format("CA=%02d", ui.cursorActView);
				case 6 -> d = String.format("TA=%02d", song.getActiveLine());
				case 7 -> d = String.format("DY=%02d", ui.mouseY / 16);
				case 8 -> d = String.format("GTL=%02d", ui.trackLines);
				case 9 -> d = String.format("OL=%02d", ui.trackLines / 2);
				case 10 -> {
					char c = vkToChar(ui.lastKeyPressed);
					d = c == 0 ? "VK=" : String.format("VK=%c %02X", c, ui.lastKeyPressed);
				}
				case 11 -> d = "MO=" + (ui.shiftKey ? "S" : " ") + (ui.controlKey ? "C" : " ");
				case 12 -> d = String.format("WD=%02d", ui.mouseWheelDelta);
				default -> {
					continue;
				}
				}
				canvasXY.textXY(d, RmtScreenLayout.TRACKS_X + n * width, ui.height - 32, TextColor.TURQUOISE);
			}
		}
	}

	/** The debug line's {@code MapVirtualKeyEx(vk, MAPVK_VK_TO_CHAR)} approximation - see the class javadoc. */
	static char vkToChar(int vk) {
		if ((vk >= KeyEvent.VK_A && vk <= KeyEvent.VK_Z) || (vk >= KeyEvent.VK_0 && vk <= KeyEvent.VK_9) || vk == KeyEvent.VK_SPACE) {
			return (char) vk;
		}
		return 0;
	}

	/**
	 * Draw song/play information:
	 * Line 1: Time  BPM  PAL/NTSC  Highlight  FPS
	 * Line 2: Song name
	 * Line 3: Music Speed   MaxTrackLength   Mono/Stereo
	 * Line 4: Edit/Jam/Midi/Explorer mode    Octave
	 * Line 5: Instrument                     Volume
	 * Line 6: Instrument flags
	 */
	public void drawInfo() {
		Song song = session.song;
		Instruments instruments = session.instruments;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		int i;
		boolean selected;
		ui.isEditingInfos = false;

		final int INFO_X = RmtScreenLayout.INFO_X;

		// Line 1: Time  BPM  PAL/NTSC  Hightlight (XX/XX)  FPS
		canvasXY.textXY(song.isNTSC() ? "NTSC" : "PAL", INFO_X + 33 * 8, RmtScreenLayout.INFO_Y_LINE_1, TextColor.TURQUOISE);

		// 2x Line highlights XX/XX (go and override --)
		canvasXY.textXY("HIGHLIGHT: --/--", 344, RmtScreenLayout.INFO_Y_LINE_1, TextColor.WHITE);
		TextColor color = isProveMode() ? TextColor.SELECTED_PROVE : TextColor.SELECTED;

		selected = ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.FIRST_HIGHLIGHT;
		canvasXY.textXY(hex2(options.trackLinePrimaryHighlight), 344 + 11 * 8, RmtScreenLayout.INFO_Y_LINE_1, selected ? color : TextColor.TURQUOISE);

		selected = ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.SECOND_HIGHLIGHT;
		canvasXY.textXY(hex2(options.trackLineSecondaryHighlight), 344 + 14 * 8, RmtScreenLayout.INFO_Y_LINE_1, selected ? color : TextColor.TURQUOISE);

		if (options.view.debugDisplay) {
			// A poor attempt at an FPS counter
			String fps = String.format(Locale.ROOT, "%1.2f FPS", ui.lastFps);
			if (fps.length() > 15) {
				fps = fps.substring(0, 15); // snprintf(szBuffer, 16, ...)
			}
			canvasXY.textXY(fps, 560 - 9 * 8, RmtScreenLayout.INFO_Y_LINE_1, TextColor.TURQUOISE);
		}

		// Line 2: Name
		if (ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.NAME) { // info? && edit name?
			ui.isEditingInfos = true;
			i = song.getSongNameCursor();
			color = isProveMode() ? TextColor.BLUE : TextColor.RED;
		} else {
			i = -1;
			color = TextColor.TURQUOISE;
		}
		canvasXY.textXY("NAME:", INFO_X, RmtScreenLayout.INFO_Y_LINE_2, TextColor.WHITE);
		canvasXY.textXYSelN(song.getSongNameField(), i, INFO_X + 6 * 8, RmtScreenLayout.INFO_Y_LINE_2, color);

		// Line 3: Speed (XX/XX/X)  MaxTrackLength (XX)  (Mono/Stereo)
		canvasXY.textXY("MUSIC SPEED: --/--/-    MAXTRACKLENGTH: --", INFO_X, RmtScreenLayout.INFO_Y_LINE_3, TextColor.WHITE);

		// 3x Speed indicators XX/XX/X (go and override --)
		color = isProveMode() ? TextColor.SELECTED_PROVE : TextColor.SELECTED;

		selected = ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.SPEED;
		canvasXY.textXY(hex2(song.getSpeed()), INFO_X + 13 * 8, RmtScreenLayout.INFO_Y_LINE_3, selected ? color : TextColor.TURQUOISE);

		selected = ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.MAIN_SPEED;
		canvasXY.textXY(hex2(song.getMainSpeed()), INFO_X + 16 * 8, RmtScreenLayout.INFO_Y_LINE_3, selected ? color : TextColor.TURQUOISE);

		selected = ui.activePart == Part.PART_INFO && song.getInfoAct() == EditArea.INSTR_SPEED;
		canvasXY.textXY(String.format("%X", song.getInstrumentSpeed()), INFO_X + 19 * 8, RmtScreenLayout.INFO_Y_LINE_3, selected ? color : TextColor.TURQUOISE);

		// Max Track Length @ 40 chars
		canvasXY.textXY(hex2(session.tracks.getMaxTrackLength()), INFO_X + 40 * 8, RmtScreenLayout.INFO_Y_LINE_3, TextColor.TURQUOISE);

		// Mono or Stereo @ 46 chars
		canvasXY.textXY(isStereo() ? "STEREO-8-TRACKS" : "MONO-4-TRACKS", INFO_X + 46 * 8, RmtScreenLayout.INFO_Y_LINE_3, TextColor.TURQUOISE);

		// Line 4: (Mode)  Octive (X-X)
		int xpos = INFO_X;
		int ypos = RmtScreenLayout.INFO_Y_LINE_4;
		if (ui.editMode == EditMode.POKEY_EXPLORER_MODE) { // test mode exclusive to keyboard input for sound debugging, this cannot be set by accident unless I did something stupid
			canvasXY.textXY("EXPLORER MODE (PITCH CALCULATIONS)", xpos, ypos, TextColor.TURQUOISE);
		} else if (ui.editMode == EditMode.MIDI_CH15_MODE) { // test mode exclusive from MIDI CH15 inputs, this cannot be set by accident unless I did something stupid
			canvasXY.textXY("EXPLORER MODE (MIDI CH15)", xpos, ypos, TextColor.TURQUOISE);
		} else if (isProveMode()) {
			canvasXY.textXY((ui.editMode == EditMode.JAM_MONO_MODE) ? "JAM MODE (MONO)" : "JAM MODE (STEREO)", xpos, ypos, TextColor.BLUE);
		} else {
			canvasXY.textXY("EDIT MODE", xpos, ypos, TextColor.RED);
		}

		canvasXY.textXY("OCTAVE", INFO_X + 55 * 8, RmtScreenLayout.INFO_Y_LINE_4, TextColor.WHITE);
		canvasXY.textXY(String.format("%d-%d", song.getOctave() + 1, song.getOctave() + 2), INFO_X + 62 * 8, RmtScreenLayout.INFO_Y_LINE_4, TextColor.TURQUOISE);

		// Line 5: Instrument (XX): (name)
		canvasXY.textXY("INSTRUMENT", INFO_X, RmtScreenLayout.INFO_Y_LINE_5, TextColor.WHITE);
		canvasXY.textXY(hex2(song.getActiveInstr()) + ":", INFO_X + 11 * 8, RmtScreenLayout.INFO_Y_LINE_5, TextColor.WHITE);
		String instrumentName = nameToString(instruments.getName(song.getActiveInstr()));
		if (instrumentName.length() > 36) {
			instrumentName = instrumentName.substring(0, 36); // C++'s szBuffer[40] = 0 after the "XX: " prefix
		}
		canvasXY.textXY(instrumentName, INFO_X + 15 * 8, RmtScreenLayout.INFO_Y_LINE_5, TextColor.TURQUOISE);

		canvasXY.textXY((ui.respectVolume ? "*" : " ") + "VOLUME", INFO_X + 56 * 8, RmtScreenLayout.INFO_Y_LINE_5, TextColor.WHITE); // Put a * infront of Volume if the RESPECT volume mode is on
		canvasXY.textXY(String.format("%X", song.getVolume()), INFO_X + 64 * 8, RmtScreenLayout.INFO_Y_LINE_5, TextColor.TURQUOISE);

		// Line 6: Under the instrument line draw small text indicating the instrument flags
		int flag = instruments.getFlag(song.getActiveInstr()) & 0xFF;

		int x = INFO_X;
		final int y = RmtScreenLayout.INFO_Y_LINE_6;
		int activeChannel = (song.getActiveColumn() % 4) + 1; // channel 1 to 4

		if ((flag & Instrument.IF_FILTER) != 0) {
			if (activeChannel > 2) {
				canvasXY.textMiniXY("NO FILTER", x, y, TextMiniColor.GRAY);
				x += 10 * 8;
			} else {
				if (activeChannel == 1) {
					canvasXY.textMiniXY("AUTOFILTER(1+3)", x, y, TextMiniColor.BLUE);
				} else {
					canvasXY.textMiniXY("AUTOFILTER(2+4)", x, y, TextMiniColor.BLUE);
				}
				x += 16 * 8;
			}
		}

		if ((flag & Instrument.IF_BASS16) != 0) {
			if (activeChannel == 2) {
				canvasXY.textMiniXY("BASS16(2+1)", x, y, TextMiniColor.BLUE);
				x += 12 * 8;
			} else if (activeChannel == 4) {
				canvasXY.textMiniXY("BASS16(4+3)", x, y, TextMiniColor.BLUE);
				x += 12 * 8;
			} else {
				canvasXY.textMiniXY("NO BASS16", x, y, TextMiniColor.GRAY);
				x += 10 * 8;
			}
		}

		if ((flag & Instrument.IF_PORTAMENTO) != 0) {
			canvasXY.textMiniXY("PORTAMENTO", x, y, TextMiniColor.BLUE);
			x += 11 * 8;
		}

		if ((flag & Instrument.IF_AUDCTL) != 0) {
			int instr = song.getActiveInstr();
			int audctl = instruments.getParameter(instr, Instrument.PAR_AUDCTL_15KHZ) | instruments.getParameter(instr, Instrument.PAR_AUDCTL_HPF_CH2) << 1 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_HPF_CH1) << 2 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_JOIN_3_4) << 3 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_JOIN_1_2) << 4 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_179_CH3) << 5 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_179_CH1) << 6 | instruments.getParameter(instr, Instrument.PAR_AUDCTL_POLY9) << 7;
			canvasXY.textMiniXY(String.format("AUDCTL:%02X", audctl & 0xFF), x, y, TextMiniColor.BLUE);
		}
	}

	/**
	 * Draw the song play time and BPM
	 * TODO: Merge with DrawInfo(), both are displayed in the same area
	 */
	public void drawPlayTimeCounter() {
		if (!session.options.view.playTimeCounter) {
			return; // the timer won't be displayed without the setting enabled first
		}
		Song song = session.song;
		UiState ui = session.uiState;

		final int PLAYTC_X = 16;
		final int PLAYTC_Y = 16;

		int fps = song.isNTSC() ? 60 : 50;
		int ts = ui.playTime / fps; // total time in seconds
		int timesec = ts % 60; // seconds 0 to 59
		int timemin = ts / 60; // minutes 0 to ...
		int timemilisec = (ui.playTime % fps) * 100 / fps; // miliseconds 0 to 99
		double speed = 0.0;
		double bpm;

		avgSpeed[song.getPlayLine() % 8] = song.getSpeed(); // refreshed every 8 rows
		for (int i = 0; i < 8; i++) {
			speed += avgSpeed[i];
		}
		speed /= 8.0; // average speed
		bpm = ((60.0 * fps) / session.options.trackLinePrimaryHighlight) / speed; // average BPM

		boolean play = isPlaying();
		String timstr = String.format(Locale.ROOT, (timesec & 1) == 0 ? "%2d:%02d.%02d" : "%2d %02d.%02d", timemin, timesec, timemilisec);
		String bpmstr = play ? String.format(Locale.ROOT, "%1.2f", bpm) : "0.00";
		if (bpmstr.length() > 7) {
			bpmstr = bpmstr.substring(0, 7); // snprintf(bpmstr, 8, ...)
		}

		canvasXY.textXY("TIME:             BPM:", PLAYTC_X, PLAYTC_Y, TextColor.WHITE);
		canvasXY.textXY(timstr, PLAYTC_X + 8 * 6, PLAYTC_Y, play ? TextColor.WHITE : TextColor.GRAY);
		canvasXY.textXY(bpmstr, PLAYTC_X + 8 * 23, PLAYTC_Y, play ? TextColor.WHITE : TextColor.GRAY);
	}
}
