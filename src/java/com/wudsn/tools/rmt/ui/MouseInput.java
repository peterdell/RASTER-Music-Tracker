package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.rmt.model.EditArea;
import com.wudsn.tools.rmt.model.Instrument;
import com.wudsn.tools.rmt.model.Part;
import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.SongInfo;
import com.wudsn.tools.rmt.model.UndoType;

/**
 * Mouse input: the port of {@code CRmtView::MouseAction()} and its button/
 * move/wheel handlers (RmtView.cpp) together with the hit-position helpers
 * {@code CSong::TrackCursorGoto()}/{@code SongCursorGoto()}/
 * {@code InfoCursorGotoSongname()}/{@code InfoCursorGotoSpeed()}/
 * {@code InfoCursorGotoHighlight()} (GUI_Song.cpp); the instrument
 * screen's zones come from {@link InstrumentsUI}. Coordinates are logical
 * canvas pixels (the caller has already applied {@code INVERSE_SCALE}).
 *
 * <p>Everything that opens a window - the three click-positioned popups
 * ({@code InfoCursorGoto*Select}) and the three commands behind info-area
 * fields - goes through {@link Callbacks}, so this class stays headless
 * and testable; {@link TrackerPanel} supplies the Swing implementation.
 * The cursor shape C++ sets with {@code SetCursor} is recorded in
 * {@link UiState#cursor} for the panel to apply.
 */
public final class MouseInput {

	/** {@code MK_LBUTTON}/{@code MK_RBUTTON} - the {@code mousebutt} bits. */
	public static final int MK_LBUTTON = 0x0001;
	public static final int MK_RBUTTON = 0x0002;

	/** The result of the volume select popup ({@code CVolumeSelectDlg}: a volume plus the "respect vol." check box). */
	public record VolumeSelection(int volume, boolean respectVolume) {
	}

	/** What the mouse can trigger that needs a window. Each {@code select*} returns the chosen value, or -1/{@code null} when the popup was cancelled; {@code x}/{@code y} are the logical click position. */
	public interface Callbacks {
		int selectOctave(int x, int y, int octave);

		VolumeSelection selectVolume(int x, int y, int volume, boolean respectVolume);

		int selectInstrument(int x, int y, int instrument);

		/** {@code OnSongSongchangemaximallengthoftracks()} - the "Change maximal length of tracks" dialog. */
		void changeMaxTrackLength();

		/** {@code OnSongSongswitch4_8()}. */
		void switchMonoStereo();

		/** {@code OnSongToggleNTSC()}. */
		void toggleNTSC();
	}

	private final RmtSession session;
	private final SongInput songInput;
	private final InstrumentsUI instrumentsUI;
	private final Callbacks callbacks;

	public MouseInput(RmtSession session, SongInput songInput, Callbacks callbacks) {
		this.session = session;
		this.songInput = songInput;
		this.instrumentsUI = new InstrumentsUI(session);
		this.callbacks = callbacks;
	}

	// --- the button handlers ---

	/** {@code OnLButtonDown}/{@code OnRButtonDown}: {@code button} is {@link #MK_LBUTTON} or {@link #MK_RBUTTON}. */
	public void buttonDown(int x, int y, int button) {
		session.undo.separator();
		session.uiState.mouseButtonsHeld |= button;
		mouseAction(x, y, button, 0);
	}

	/** {@code OnLButtonUp}/{@code OnRButtonUp}. */
	public void buttonUp(int button) {
		session.undo.separator();
		session.uiState.mouseButtonsHeld &= ~button;
	}

	/** {@code OnMouseMove}. */
	public void mouseMove(int x, int y) {
		mouseAction(x, y, 0, 0);
	}

	/** {@code OnMouseWheel}: {@code wheelDelta} in Windows units (a positive multiple of 120 = wheel up/away). */
	public void mouseWheel(int x, int y, int wheelDelta) {
		mouseAction(x, y, 0, wheelDelta);
	}

	/** C++'s {@code CRect::PtInRect} over {@code CRect(l, t, r, b)}: right and bottom exclusive. */
	private static boolean ptInRect(int left, int top, int right, int bottom, int x, int y) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static boolean ptInRect(java.awt.Rectangle rect, int x, int y) {
		return rect != null && rect.contains(x, y);
	}

	/**
	 * {@code CRmtView::MouseAction()}: hit-tests {@code (x, y)} against the
	 * screen layout, sets the cursor shape, and performs the click/wheel
	 * action for the area hit. Returns C++'s area code (0 = nothing).
	 */
	public int mouseAction(int x, int y, int mousebutt, int wheelDelta) {
		Song song = session.song;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		int tracks4_8 = session.tracks4_8;
		int i;
		int px;
		int py;

		// Store the last known mouse XY coordinates and buttons used
		ui.mouseX = x;
		ui.mouseY = y;
		ui.mouseButton = mousebutt;
		ui.mouseWheelDelta = wheelDelta;

		final boolean stereo = song.isStereo(tracks4_8);
		final int MINIMAL_WIDTH_INSTRUMENTS = 1220;
		final int WINDOW_OFFSET = (ui.width < 1320 && stereo && ui.activeTi == Part.PART_TRACKS) ? -250 : 0; // test displacement with the window size
		int INSTRUMENT_OFFSET = (ui.activeTi == Part.PART_INSTRUMENTS && stereo) ? -250 : 0;
		if (tracks4_8 == 4 && ui.activeTi == Part.PART_INSTRUMENTS && ui.width > MINIMAL_WIDTH_INSTRUMENTS - 220) {
			INSTRUMENT_OFFSET = 260;
		}
		final int SONG_OFFSET = RmtScreenLayout.SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((tracks4_8 == 4) ? -200 : 310); // displace the SONG block depending on certain parameters

		final int linescount = (WINDOW_OFFSET != 0) ? 5 : 9; // songlines displayed depend on the window offset

		// SONG PARTS
		if (ptInRect(SONG_OFFSET + 6 * 8, RmtScreenLayout.SONG_Y + 16, SONG_OFFSET + 6 * 8 + tracks4_8 * 3 * 8 - 8, RmtScreenLayout.SONG_Y + 16 + linescount * 16, x, y)) {
			// Song
			ui.cursor = RmtCursor.GOTO;

			if ((mousebutt & MK_LBUTTON) != 0) {
				int lineoffset = (WINDOW_OFFSET != 0) ? RmtScreenLayout.SONG_Y + 16 : RmtScreenLayout.SONG_Y + 48;
				songCursorGoto(x - (SONG_OFFSET + 6 * 8), y - lineoffset);
			}
			if (wheelDelta != 0) {
				if (wheelDelta > 0) {
					songInput.songKey(VirtualKey.VK_UP, false, false);
				}
				if (wheelDelta < 0) {
					songInput.songKey(VirtualKey.VK_DOWN, false, false);
				}
			}
			return 5;
		}

		if (ptInRect(SONG_OFFSET + 6 * 8, RmtScreenLayout.SONG_Y, SONG_OFFSET + 6 * 8 + tracks4_8 * 3 * 8 - 8, RmtScreenLayout.SONG_Y + 16, x, y)) {
			// over Song L1-R4 for channel on/off/solo/inversion
			i = (x + 4 - (SONG_OFFSET + 6 * 8)) / (8 * 3);
			if (i < 0) {
				i = 0;
			} else {
				if (i >= tracks4_8) {
					i = tracks4_8 - 1;
				}
			}
			px = i;
			ui.cursor = RmtCursor.CHANNEL_ON_OFF;
			if ((mousebutt & MK_LBUTTON) != 0) {
				session.channelControl.toggleChannelOnOff(px); // inversion
			}
			if ((mousebutt & MK_RBUTTON) != 0) {
				session.channelControl.setChannelSolo(px); // solo/mute on off
			}
			return 1;
		}

		// INFO PARTS
		if (ptInRect(64, 32, 64 + SongInfo.SONG_NAME_MAX_LEN * 8, 32 + 16, x, y)) {
			// Song name
			ui.cursor = RmtCursor.GOTO;
			if ((mousebutt & MK_LBUTTON) != 0) {
				infoCursorGotoSongname(x - 64);
			}
			return 6;
		}

		if (ptInRect(120, 48, 120 + 7 * 8, 48 + 16, x, y)) {
			// Song speed
			ui.cursor = RmtCursor.GOTO;
			if ((mousebutt & MK_LBUTTON) != 0) {
				infoCursorGotoSpeed(x - 120);
			}
			return 6;
		}

		if (ptInRect(336, 48, 336 + 2 * 8, 48 + 16, x, y)) {
			// MAXTRACKLENGTH
			ui.cursor = RmtCursor.DIALOG;
			if ((mousebutt & MK_LBUTTON) != 0) {
				callbacks.changeMaxTrackLength();
			}
			return 6;
		}

		if (ptInRect(384, 48, 384 + 8 * ((tracks4_8 == 8) ? 15 : 13), 48 + 16, x, y)) {
			// MONO-4-TRACKS or STEREO-8-TRACKS
			ui.cursor = RmtCursor.DIALOG;
			if ((mousebutt & MK_LBUTTON) != 0) {
				callbacks.switchMonoStereo();
			}
			return 6;
		}

		final boolean ntsc = song.isNTSC();
		if (ptInRect(280, 16, 280 + 8 * (ntsc ? 4 : 3), 16 + 16, x, y)) {
			ui.cursor = RmtCursor.GOTO;
			if ((mousebutt & MK_LBUTTON) != 0) {
				callbacks.toggleNTSC();
			}
			return 6;
		}

		if (ptInRect(432, 16, 432 + 8 * 5, 16 + 16, x, y)) {
			// track line highlights
			int ma = session.tracks.getMaxTrackLength() / 2;
			int hx = (x - 432 - 4) / 8;
			ui.cursor = RmtCursor.GOTO;
			if ((mousebutt & MK_LBUTTON) != 0) {
				infoCursorGotoHighlight(x - 432);
			}
			if (wheelDelta != 0) {
				if (hx < 2) { // primary line highlight
					if (wheelDelta < 0) {
						options.trackLinePrimaryHighlight++;
						if (options.trackLinePrimaryHighlight > ma) {
							options.trackLinePrimaryHighlight = ma;
						}
					} else if (wheelDelta > 0) {
						options.trackLinePrimaryHighlight--;
						if (options.trackLinePrimaryHighlight < 1) {
							options.trackLinePrimaryHighlight = 1;
						}
					}
				} else { // secondary line highlight
					if (wheelDelta < 0) {
						options.trackLineSecondaryHighlight++;
						if (options.trackLineSecondaryHighlight > ma) {
							options.trackLineSecondaryHighlight = ma;
						}
					} else if (wheelDelta > 0) {
						options.trackLineSecondaryHighlight--;
						if (options.trackLineSecondaryHighlight < 1) {
							options.trackLineSecondaryHighlight = 1;
						}
					}
				}
			}
			return 6;
		}

		if (ptInRect(456, 64, 456 + 8 * 10, 64 + 16, x, y)) {
			// Octave Select Dialog
			ui.cursor = RmtCursor.DIALOG;
			if ((mousebutt & MK_LBUTTON) != 0) {
				infoCursorGotoOctaveSelect(x, y);
			}
			if (wheelDelta != 0) {
				if (wheelDelta < 0) {
					song.octaveUp();
				} else if (wheelDelta > 0) {
					song.octaveDown();
				}
			}
			return 6;
		}

		if (ptInRect(472, 80, 472 + 8 * 8, 80 + 16, x, y)) {
			// Volume Select Dialog
			ui.cursor = RmtCursor.DIALOG;
			if ((mousebutt & MK_LBUTTON) != 0) {
				infoCursorGotoVolumeSelect(x, y);
			}
			if (wheelDelta != 0) {
				if (wheelDelta < 0) {
					song.volumeUp();
				} else if (wheelDelta > 0) {
					song.volumeDown();
				}
			}
			return 6;
		}

		if (ptInRect(136, 80, 136 + Instrument.INSTRUMENT_NAME_MAX_LEN * 8, 80 + 16, x, y)) {
			// Instrument Select Dialog
			return instrumentSelectDialog(x, y, mousebutt, wheelDelta);
		}

		// LOWER PARTS
		if (ui.activeTi == Part.PART_TRACKS) {
			if (ptInRect(RmtScreenLayout.TRACKS_X + 3 * 16, RmtScreenLayout.TRACKS_Y - 12, RmtScreenLayout.TRACKS_X + 3 * 8 + tracks4_8 * 8 * 16, RmtScreenLayout.TRACKS_Y + 32, x, y)) {
				i = (x - (RmtScreenLayout.TRACKS_X + 5 * 8)) / (8 * 16);
				if (i < 0) {
					i = 0;
				} else if (i >= tracks4_8) {
					i = tracks4_8 - 1;
				}
				px = i;

				ui.cursor = RmtCursor.CHANNEL_ON_OFF;

				if ((mousebutt & MK_LBUTTON) != 0) {
					session.channelControl.toggleChannelOnOff(px); // inversion
				}
				if ((mousebutt & MK_RBUTTON) != 0) {
					session.channelControl.setChannelSolo(px); // solo/mute/on/off
				}
				return 1;
			}
			// the number of tracklines is adjusted based on the window height
			if (ptInRect(RmtScreenLayout.TRACKS_X + 6 * 8, RmtScreenLayout.TRACKS_Y + 48, RmtScreenLayout.TRACKS_X + 3 * 8 + tracks4_8 * 8 * 16, RmtScreenLayout.TRACKS_Y + 48 + ui.trackLines * 16, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					trackCursorGoto(x - (RmtScreenLayout.TRACKS_X + 6 * 8), y - (RmtScreenLayout.TRACKS_Y + 48));
				}
				if (wheelDelta != 0) {
					if (wheelDelta > 0) {
						songInput.trackKey(VirtualKey.VK_UP, false, false);
					} else if (wheelDelta < 0) {
						songInput.trackKey(VirtualKey.VK_DOWN, false, false);
					}
				}
				return 4;
			}
		} else if (ui.activeTi == Part.PART_INSTRUMENTS) {
			// Detect bounding box hits on various parts of the instrument display
			// Algo:
			// 1. Find the area that a specific GUI part (zone) will cover.
			//		Size depends on instrument data
			// 2. Check if the action point is within the zone
			// 3. Do zone specific action

			int activeInstrNum = song.getActiveInstr();
			java.awt.Rectangle rec;

			// VOLUME LEFT (bottom)
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.ENVELOPE_LEFT_ENVELOPE);
			if (ptInRect(rec, x, y)) {
				// In the left volume envelope area
				px = (x - rec.x) / 8; // px is how far into the envelop the cursor is
				py = 15 - ((y - rec.y) / 4); // py is the volume at the cursor position
				ui.cursor = RmtCursor.ENVELOPE_VOLUME;
				if ((ui.mouseButtonsHeld & MK_LBUTTON) != 0) { // compares g_mousebutt to make it work while moving
					// Set the volume
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					session.instruments.setEnvelopeVolume(activeInstrNum, false, px, py, stereo);
				}
				if ((ui.mouseButtonsHeld & MK_RBUTTON) != 0) { // compares g_mousebutt to make it work while moving
					// Clear the volume
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					session.instruments.setEnvelopeVolume(activeInstrNum, false, px, 0, stereo);
				}
				return 2;
			}

			// VOLUME RIGHT (upper)
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.ENVELOPE_RIGHT_ENVELOPE);
			if (ptInRect(rec, x, y)) {
				// In the right volume envelope area
				px = (x - rec.x) / 8;
				py = 15 - ((y - rec.y) / 4);
				ui.cursor = RmtCursor.ENVELOPE_VOLUME;
				if ((ui.mouseButtonsHeld & MK_LBUTTON) != 0) { // compares g_mousebutt to make it work while moving
					// Set the volume
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					session.instruments.setEnvelopeVolume(activeInstrNum, true, px, py, stereo);
				}
				if ((ui.mouseButtonsHeld & MK_RBUTTON) != 0) { // compares g_mousebutt to make it work while moving
					// Clear the volume
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					session.instruments.setEnvelopeVolume(activeInstrNum, true, px, 0, stereo);
				}
				return 3;
			}

			// ENVELOPE PARAMETERS large table
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.ENVELOPE_PARAM_TABLE);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 0);
				}
				return 3;
			}

			// ENVELOPE PARAMETERS series of numbers for the right channel volume
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.ENVELOPE_RIGHT_VOL_NUMS);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 1);
				}
				return 3;
			}

			// INSTRUMENT NOTE TABLE
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.NOTE_TABLE);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 2);
				}
				return 3;
			}

			// INSTRUMENT NAME
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.INSTRUMENT_NAME);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 3);
				}
				return 3;
			}

			// INSTRUMENT PARAMETERS
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.PARAMETERS);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.GOTO;
				if ((mousebutt & MK_LBUTTON) != 0) {
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 4);
				}
				return 3;
			}

			// INSTRUMENT SELECT DIALOG
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.INSTRUMENT_NUMBER_DLG);
			if (ptInRect(rec, x, y)) {
				return instrumentSelectDialog(x + 64, y - (4 * 16 + 8), mousebutt, wheelDelta); // C++'s "goto Instrument_Select_Dialog" with the point shifted
			}

			// ENVELOPE LEN a GO PARAMETER - length and loop to help the mouse
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.LEN_AND_GOTO_ARROWS);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.SET_POSITION;
				if ((mousebutt & MK_LBUTTON) != 0) {
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 5);
				}
				if ((mousebutt & MK_RBUTTON) != 0) {
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 6);
				}
				return 7;
			}

			// TABLE LEN a GO PARAMETER - length and loop to help the mouse
			rec = instrumentsUI.getGUIArea(activeInstrNum, InstrumentsUI.Zone.NOTE_TBL_LEN_AND_GOTO);
			if (ptInRect(rec, x, y)) {
				ui.cursor = RmtCursor.SET_POSITION;
				if ((mousebutt & MK_LBUTTON) != 0) {
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 7);
				}
				if ((mousebutt & MK_RBUTTON) != 0) {
					session.undo.changeInstrument(activeInstrNum, 0, UndoType.UETYPE_INSTRDATA);
					instrumentsUI.cursorGoto(activeInstrNum, x - rec.x, y - rec.y, 8);
				}
				return 7;
			}
		}
		ui.cursor = RmtCursor.ARROW;
		return 0;
	}

	/** C++'s {@code Instrument_Select_Dialog:} label, reached from the info area's instrument field and from the instrument screen's "INSTRUMENT XX" title. */
	private int instrumentSelectDialog(int x, int y, int mousebutt, int wheelDelta) {
		Song song = session.song;
		session.uiState.cursor = RmtCursor.DIALOG;
		if ((mousebutt & MK_LBUTTON) != 0) {
			infoCursorGotoInstrumentSelect(x, y);
		}
		if (wheelDelta != 0) {
			if (wheelDelta > 0) {
				song.activeInstrPrev(session.undo, session.options.keyboardRememberOctavesAndVolumes);
			} else if (wheelDelta < 0) {
				song.activeInstrNext(session.undo, session.options.keyboardRememberOctavesAndVolumes);
			}
		}
		return 6;
	}

	// --- CSong::*CursorGoto ---

	/** {@code CSong::InfoCursorGotoSongname()} */
	boolean infoCursorGotoSongname(int x) {
		x = x / 8;
		if (x >= 0 && x < SongInfo.SONG_NAME_MAX_LEN) {
			session.song.setSongNameCursor(x);
			session.uiState.activePart = Part.PART_INFO;
			session.song.setInfoAct(EditArea.NAME);
			session.uiState.isEditingInfos = true; // Song Name is being edited
			return true;
		}
		return false;
	}

	/** {@code CSong::InfoCursorGotoSpeed()} */
	boolean infoCursorGotoSpeed(int x) {
		x = (x - 4) / 8;
		if (x < 2) {
			session.song.setInfoAct(EditArea.SPEED);
		} else if (x < 5) {
			session.song.setInfoAct(EditArea.MAIN_SPEED);
		} else {
			session.song.setInfoAct(EditArea.INSTR_SPEED);
		}
		session.uiState.activePart = Part.PART_INFO;
		session.uiState.isEditingInfos = false; // Song Speed is being edited
		return true;
	}

	/** {@code CSong::InfoCursorGotoHighlight()} */
	boolean infoCursorGotoHighlight(int x) {
		x = (x - 4) / 8;
		if (x < 2) {
			session.song.setInfoAct(EditArea.FIRST_HIGHLIGHT);
		} else {
			session.song.setInfoAct(EditArea.SECOND_HIGHLIGHT);
		}
		session.uiState.activePart = Part.PART_INFO;
		session.uiState.isEditingInfos = false; // Song Highlight is being edited
		return true;
	}

	/** {@code CSong::InfoCursorGotoOctaveSelect()}: the popup, then {@code m_octave = dlg.m_octave} on OK. C++ zeroes {@code g_mousebutt} first "because the dialog swallows the OnLButtonUp event". */
	boolean infoCursorGotoOctaveSelect(int x, int y) {
		session.uiState.mouseButtonsHeld = 0;
		int octave = callbacks.selectOctave(x, y, session.song.getOctave());
		if (octave >= 0) {
			session.song.setOctave(octave);
			return true;
		}
		return false;
	}

	/** {@code CSong::InfoCursorGotoVolumeSelect()} */
	boolean infoCursorGotoVolumeSelect(int x, int y) {
		session.uiState.mouseButtonsHeld = 0;
		VolumeSelection selection = callbacks.selectVolume(x, y, session.song.getVolume(), session.uiState.respectVolume);
		if (selection != null) {
			session.song.setVolume(selection.volume());
			session.uiState.respectVolume = selection.respectVolume();
			return true;
		}
		return false;
	}

	/** {@code CSong::InfoCursorGotoInstrumentSelect()} */
	boolean infoCursorGotoInstrumentSelect(int x, int y) {
		session.uiState.isEditingInstrumentName = false;
		session.uiState.mouseButtonsHeld = 0;
		int selected = callbacks.selectInstrument(x, y, session.song.getActiveInstr());
		if (selected >= 0) {
			session.song.activeInstrSet(selected, session.options.keyboardRememberOctavesAndVolumes);
			return true;
		}
		return false;
	}

	/** {@code CSong::TrackCursorGoto()}: {@code (x, y)} relative to the first track line's top-left. */
	boolean trackCursorGoto(int x, int y) {
		Song song = session.song;
		UiState ui = session.uiState;
		int xch = x / (16 * 8);
		int xx = (x - (xch * 16 * 8)) / 8;

		int line = (y + 0) / 16 - 8 + ui.cursorActView;

		if (line >= 0 && line < song.getSmallestMaxtracklen(song.songGetActiveLine(), session.tracks4_8)) { // variable pattern size, to prevent clicking "out of bounds" with the new tracks display
			if (xch >= 0 && xch < song.getTracks(session.tracks4_8)) {
				song.setActiveColumn(xch);
			}
			if (!(song.getPlayMode() != PlayMode.PLAY_STOP && song.getFollowPlayMode())) { // prevents moving at all during play+follow
				song.setActiveLine(line);
			}
		} else {
			return false;
		}
		// notracklinechange:
		switch (xx) {
		case 0, 1, 2, 3 -> song.setTrackActiveCur(0);
		case 4, 5, 6 -> song.setTrackActiveCur(1);
		case 7, 8, 9 -> song.setTrackActiveCur(2);
		case 10, 11, 12, 13, 14, 15, 16 -> song.setTrackActiveCur(3); // filling more area avoids jumping all over the place, between the speed column and the next channel's note column
		default -> {
		}
		}
		ui.activePart = Part.PART_TRACKS;
		return true;
	}

	/** {@code CSong::SongCursorGoto()}: {@code (x, y)} relative to the song block's track columns, with the row of the active line at {@code y / 16 == 2}. */
	boolean songCursorGoto(int x, int y) {
		Song song = session.song;
		UiState ui = session.uiState;
		int xch = (x + 4) / (3 * 8);
		int line = (y + 0) / 16 - 2 + song.songGetActiveLine();
		if (line >= 0 && line < Song.SONGLEN) {
			if (xch >= 0 && xch < song.getTracks(session.tracks4_8)) {
				song.setActiveColumn(xch);
			}
			if (line != song.songGetActiveLine()) {
				ui.activePart = Part.PART_SONG;
				if (song.getPlayMode() != PlayMode.PLAY_STOP && song.getFollowPlayMode()) {
					PlayMode mode = (song.getPlayMode() == PlayMode.PLAY_TRACK) ? PlayMode.PLAY_TRACK : PlayMode.PLAY_FROM; // play track in loop, else, play from cursor position
					song.stop(session.undo);
					song.songSetPlayLine(line);
					song.songSetActiveLine(line);
					song.setPlayLine(0);
					song.setActiveLine(0);
					song.play(mode, song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard); // continue playing using the correct parameters
				} else {
					song.songSetActiveLine(line);
				}
			}
		} else {
			return false;
		}
		song.setActiveColumn(xch);
		ui.activePart = Part.PART_SONG;
		return true;
	}
}
