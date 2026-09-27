package org.atari.raster.rmt.ui;

import static org.atari.raster.rmt.ui.VirtualKey.*;

import org.atari.raster.rmt.model.EditArea;
import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.EnvelopeParameter;
import org.atari.raster.rmt.model.Instrument;
import org.atari.raster.rmt.model.InstrumentSection;
import org.atari.raster.rmt.model.Instruments;
import org.atari.raster.rmt.model.Keyboard2NoteMapping;
import org.atari.raster.rmt.model.Notes;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.SongInfo;
import org.atari.raster.rmt.model.TrackClipboard;
import org.atari.raster.rmt.model.Tracks;
import org.atari.raster.rmt.model.UndoType;

/**
 * Keyboard input: the port of {@code CRmtView::OnKeyDown()}/{@code OnKeyUp()}
 * (RmtView.cpp) and of the per-part key handlers {@code CSong::InfoKey()}/
 * {@code InstrKey()}/{@code ProveKey()}/{@code TrackKey()}/{@code SongKey()}
 * plus {@code CursorToSpeedColumn()}/{@code IsNotAMovementVKey()}
 * (GUI_Song.cpp). Pure dispatch over already-ported {@link Song}/
 * {@link Instruments}/{@link TrackClipboard} operations, so it lives in
 * {@code ui} taking the {@link RmtSession} - the deviation from C++ (which
 * puts the handlers on {@code CSong}) recorded in
 * {@code plans/18_JAVA_UI_PORT_PLAN.md}.
 *
 * <p>Key codes are <b>Windows virtual-key codes</b> ({@link VirtualKey}),
 * exactly what the C++ switches on and what the
 * {@link Keyboard2NoteMapping} tables are indexed by; {@link TrackerPanel}
 * translates Swing's key codes once. C++'s {@code goto}s become small
 * private methods ({@link #changeInstrumentPar}, {@link #changeInstrumentEnv},
 * ...) or booleans; every other line follows the original.
 *
 * <p>Not ported here: the
 * {@code /SCRIPT}-style {@code FlaToCha} numpad remap of
 * {@code OnKeyDown}'s first lines (a scan-code workaround for
 * Shift+numpad on Windows; Swing already reports numpad keys by their own
 * codes), the Pokey Explorer branch of {@code ProveKey}
 * ({@code CPokeyController} is unported), and the two dialogs some keys
 * open ({@code SongInsertCopyOrCloneOfSongLines} on Ctrl+O and
 * {@code BlockEffect} on Ctrl+F - B7).
 */
public final class SongInput {

	private final RmtSession session;

	public SongInput(RmtSession session) {
		this.session = session;
	}

	/** Ctrl+O's {@code SongInsertCopyOrCloneOfSongLines()} dialog, installed by the window ({@link SongDialogs#insertCopyOrCloneOfSongLines}); nothing happens without one. */
	private Runnable insertCopyOrCloneAction;

	public void setInsertCopyOrCloneAction(Runnable insertCopyOrCloneAction) {
		this.insertCopyOrCloneAction = insertCopyOrCloneAction;
	}

	/** Ctrl+F's {@code BlockEffect()} dialog with its undo bookkeeping ({@link SongDialogs#blockEffectFromKey}); nothing happens without one. */
	private Runnable blockEffectAction;

	public void setBlockEffectAction(Runnable blockEffectAction) {
		this.blockEffectAction = blockEffectAction;
	}

	// --- key tables (Keyboard2NoteMapping is indexed by the raw 0..255 VK byte) ---

	private int noteKey(int vk) {
		return vk >= 0 && vk <= 0xFF ? Keyboard2NoteMapping.noteKey(vk, session.options.keyboardLayout) : -1;
	}

	private static int numbKey(int vk) {
		return vk >= 0 && vk <= 0xFF ? Keyboard2NoteMapping.numbKey(vk) : -1;
	}

	private static int numblock09Key(int vk) {
		return vk >= 0 && vk <= 0xFF ? Keyboard2NoteMapping.numblock09Key(vk) : -1;
	}

	/** returns 1 if it is not a scroll key */
	static boolean isNotAMovementVKey(int vk) {
		return vk != VK_RIGHT && vk != VK_LEFT && vk != VK_UP && vk != VK_DOWN && vk != VK_TAB && vk != VK_RETURN && vk != VK_HOME && vk != VK_END && vk != VK_PRIOR && vk != VK_NEXT && vk != VK_CAPITAL;
	}

	// --- the C++ macros over the block selection ---

	private boolean isBlockSelected() {
		return session.clipboard.isBlockSelected();
	}

	private void blockDeselect() {
		session.song.blockDeselect(session.clipboard);
	}

	private void blockSetBegin() {
		session.song.songBlockSetBegin(session.clipboard, session.tracks);
	}

	private void blockSetEnd() {
		session.song.songBlockSetEnd(session.clipboard, session.tracks);
	}

	/** "if no block is selected, make a block at the current location" */
	private void makeBlockAtCursorIfNone() {
		Song song = session.song;
		if (!isBlockSelected()) {
			session.clipboard.blockSetBegin(song.getActiveColumn(), song.songGetActiveTrack(), song.getActiveLine(), session.tracks, song);
			session.clipboard.blockSetEnd(song.getActiveLine(), session.tracks);
		}
	}

	private boolean isPlayingAndFollowing() {
		return session.song.getPlayMode() != PlayMode.PLAY_STOP && session.song.getFollowPlayMode();
	}

	private int skipLines() {
		return session.options.skipLinesAfterNoteInsert;
	}

	/** {@code TrackUp(lines)} */
	private void trackUp(int lines) {
		session.song.trackUp(lines, session.tracks4_8, session.options.keyboardUpDownContinue, session.undo, session.clipboard);
	}

	/**
	 * {@code TrackDown(lines, stoponlastline)}. C++ returns FALSE from its
	 * two early exits (play+follow; the "stop on last line" case) and TRUE
	 * otherwise; the Java model method is void, so the same conditions are
	 * evaluated here for the one caller that needs the result (Enter).
	 */
	private boolean trackDown(int lines, boolean stopOnLastLine) {
		Song song = session.song;
		boolean moves = !isPlayingAndFollowing() && !(!session.options.keyboardUpDownContinue && stopOnLastLine && song.getActiveLine() + lines > song.trackGetLastLine());
		song.trackDown(lines, stopOnLastLine, session.tracks4_8, session.options.keyboardUpDownContinue, session.undo, session.clipboard);
		return moves;
	}

	private void trackLeft(boolean column) {
		session.song.trackLeft(column, session.tracks4_8, session.undo);
	}

	private void trackRight(boolean column) {
		session.song.trackRight(column, session.tracks4_8, session.undo);
	}

	private void songUp() {
		session.song.songUp(session.undo, session.clipboard);
	}

	private void songDown() {
		session.song.songDown(session.undo, session.clipboard);
	}

	private void activeInstrPrev() {
		session.song.activeInstrPrev(session.undo, session.options.keyboardRememberOctavesAndVolumes);
	}

	private void activeInstrNext() {
		session.song.activeInstrNext(session.undo, session.options.keyboardRememberOctavesAndVolumes);
	}

	/** {@code CSong::CursorToSpeedColumn()} */
	boolean cursorToSpeedColumn() {
		if (session.uiState.activePart != Part.PART_TRACKS || session.song.songGetActiveTrack() < 0) {
			return false;
		}
		blockDeselect();
		session.song.setTrackActiveCur(3);
		return true;
	}

	// --- CRmtView::OnKeyDown / OnKeyUp ---

	/** {@code CRmtView::OnKeyDown()}: the global keys, Shift/Ctrl/Alt tracking, then the per-part dispatch with its prove-mode and Caps Lock redirections. */
	public void keyDown(int vk) {
		UiState ui = session.uiState;
		ui.lastKeyPressed = vk;

		switch (vk) {
		case VK_F11:
			session.undo.separator(); // respect volume
			ui.respectVolume = !ui.respectVolume;
			break;

		case VK_MEDIA_PLAY_PAUSE:
			if (session.song.getPlayMode() == PlayMode.PLAY_STOP) {
				session.song.play(PlayMode.PLAY_SONG, session.song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard); // play song from start
			} else {
				session.song.stop(session.undo); // if playing, stop
			}
			break;

		case VK_MEDIA_NEXT_TRACK:
			session.song.play(PlayMode.PLAY_SEEK_NEXT, session.song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard); // seek next and play from track
			break;

		case VK_MEDIA_PREV_TRACK:
			session.song.play(PlayMode.PLAY_SEEK_PREV, session.song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard); // seek prev and play from track
			break;

		case VK_SHIFT:
			ui.shiftKey = true;
			return;

		case VK_CONTROL:
			ui.controlKey = true;
			return;

		case VK_MENU:
			ui.altKey = true;
			return;

		default:
			boolean capsLock = ui.capsLock;
			boolean shift = ui.shiftKey;
			boolean control = ui.controlKey;
			switch (ui.activePart) {
			case PART_INFO:
				if (shift && !ui.isEditingInfos && (noteKey(vk) >= 0 || numblock09Key(vk) >= 0 || vk == VK_SPACE)) {
					proveKey(vk, shift, control); // plays a note while the SHIFT key is held, except on the Song Name field, it will be ignored
				} else if (shift && !ui.isEditingInfos && (noteKey(vk) < 0)) {
					if (vk == VK_TAB || vk == VK_LEFT || vk == VK_RIGHT || vk == VK_PRIOR || vk == VK_NEXT) {
						infoKeyAnyway(vk, shift, control);
					}
					// else: prevents inputing incorrect infos by accident while testing notes holding SHIFT
				} else if (ui.isEditingInfos && capsLock && !shift) {
					infoKey(vk, true, control); // workaround: so it won't *stay* locked when CAPSLOCK isn't active
				} else if (ui.isEditingInfos && capsLock && shift) {
					infoKey(vk, false, control); // workaround: so it will *stay* locked when CAPSLOCK isn't active
				} else {
					infoKeyAnyway(vk, shift, control);
				}
				break;

			case PART_TRACKS:
				if (ui.editMode.isProveMode()) {
					proveKey(vk, shift, control);
				} else if (shift && (noteKey(vk) >= 0 || numblock09Key(vk) >= 0 || vk == VK_SPACE)) {
					proveKey(vk, shift, control);
				} else {
					trackKey(vk, shift, control);
				}
				break;

			case PART_INSTRUMENTS:
				if (shift && !ui.isEditingInstrumentName && (noteKey(vk) >= 0 || numblock09Key(vk) >= 0 || vk == VK_SPACE)) {
					proveKey(vk, shift, control); // plays a note while the SHIFT key is held, except on the Instrument Name field, it will be ignored
				} else if (shift && !ui.isEditingInstrumentName && (noteKey(vk) < 0)) {
					if (vk == VK_TAB || vk == VK_INSERT || vk == VK_DELETE || vk == VK_LEFT || vk == VK_RIGHT || vk == VK_UP || vk == VK_DOWN || vk == VK_DIVIDE || vk == VK_MULTIPLY || vk == VK_SUBTRACT || vk == VK_ADD || vk == VK_PRIOR || vk == VK_NEXT) {
						instrKeyAnyway(vk, shift, control);
					}
					// else: prevents inputing incorrect infos by accident while testing notes holding SHIFT
				} else if (ui.isEditingInstrumentName && capsLock && !shift) {
					instrKey(vk, true, control); // workaround: so it won't *stay* locked when CAPSLOCK isn't active
				} else if (ui.isEditingInstrumentName && capsLock && shift) {
					instrKey(vk, false, control); // workaround: so it will *stay* locked when CAPSLOCK isn't active
				} else {
					instrKeyAnyway(vk, shift, control);
				}
				break;

			case PART_SONG:
				if (ui.editMode.isProveMode()) {
					proveKey(vk, shift, control);
				} else if (shift && (noteKey(vk) >= 0 || numblock09Key(vk) >= 0 || vk == VK_SPACE)) {
					proveKey(vk, shift, control);
				} else {
					songKey(vk, shift, control);
				}
				break;
			}
		}
	}

	/** C++'s {@code do_infokey_anyway:} label. */
	private void infoKeyAnyway(int vk, boolean shift, boolean control) {
		if (vk == VK_PRIOR || vk == VK_NEXT) {
			proveKey(vk, shift, control);
		} else {
			infoKey(vk, shift, control);
		}
	}

	/** C++'s {@code do_instrkey_anyway:} label. */
	private void instrKeyAnyway(int vk, boolean shift, boolean control) {
		if (vk == VK_PRIOR || vk == VK_NEXT) {
			proveKey(vk, shift, control);
		} else {
			instrKey(vk, shift, control);
		}
	}

	/** {@code CRmtView::OnKeyUp()}. */
	public void keyUp(int vk) {
		UiState ui = session.uiState;
		switch (vk) {
		case VK_SHIFT -> ui.shiftKey = false;
		case VK_CONTROL -> ui.controlKey = false;
		case VK_MENU -> ui.altKey = false;
		default -> {
		}
		}
	}

	// --- CSong::InfoKey ---

	/** The value the info area's cursor is on: current speed, main speed, instrument speed, primary or secondary highlight (C++'s {@code infptab} table). */
	private int getInfoValue(EditArea area) {
		Song song = session.song;
		RmtOptions options = session.options;
		return switch (area) {
		case SPEED -> song.getSpeed();
		case MAIN_SPEED -> song.getMainSpeed();
		case INSTR_SPEED -> song.getInstrumentSpeed();
		case FIRST_HIGHLIGHT -> options.trackLinePrimaryHighlight;
		case SECOND_HIGHLIGHT -> options.trackLineSecondaryHighlight;
		default -> 0;
		};
	}

	private void setInfoValue(EditArea area, int value) {
		Song song = session.song;
		RmtOptions options = session.options;
		switch (area) {
		case SPEED -> song.setSpeed(value);
		case MAIN_SPEED -> song.setMainSpeed(value);
		case INSTR_SPEED -> song.setInstrumentSpeed(value);
		case FIRST_HIGHLIGHT -> options.trackLinePrimaryHighlight = value;
		case SECOND_HIGHLIGHT -> options.trackLineSecondaryHighlight = value;
		default -> {
		}
		}
	}

	/** The maximum of the value the info area's cursor is on (C++'s {@code infandtab}). */
	private int getInfoMax(EditArea area) {
		int half = session.tracks.getMaxTrackLength() / 2;
		return switch (area) {
		case SPEED, MAIN_SPEED -> 0xFF;
		case INSTR_SPEED -> 0x08;
		case FIRST_HIGHLIGHT, SECOND_HIGHLIGHT -> half;
		default -> 0;
		};
	}

	private static EditArea editArea(int ordinal) {
		return EditArea.values()[ordinal];
	}

	/** {@code CSong::InfoKey()} */
	boolean infoKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		UiState ui = session.uiState;
		int i;
		int num;
		EditArea infoAct = song.getInfoAct();
		int infand = getInfoMax(infoAct);
		boolean capsLock = ui.capsLock;
		boolean editOk = false;

		if (infoAct == EditArea.NAME) {
			ui.isEditingInfos = true;
			if (vk == VK_DIVIDE || vk == VK_MULTIPLY || vk == VK_ADD || vk == VK_SUBTRACT) {
				editOk = true; // a workaround so the Octave and Volume can be set anywhere
			} else if (((!capsLock && shift) || (capsLock && !shift)) && (vk == VK_LEFT || vk == VK_RIGHT)) {
				editOk = true; // a workaround so the active instrument can be set anywhere
			} else {
				if (isNotAMovementVKey(vk)) { // saves undo only if it is not cursor movement
					session.undo.changeInfo(0, UndoType.UETYPE_INFODATA);
				}
				TextFieldEditor.Result r = TextFieldEditor.editText(vk, shift, control, song.getSongNameChars(), song.getSongNameCursor(), SongInfo.SONG_NAME_MAX_LEN);
				song.setSongNameCursor(r.cursor());
				if (r.done()) {
					song.setInfoAct(EditArea.SPEED);
				}
				return true;
			}
		}

		if (!editOk && (num = numbKey(vk)) >= 0 && num <= infand) {
			int infp = getInfoValue(infoAct);
			i = 0;
			if (infoAct.compareTo(EditArea.SPEED) >= 0 && infoAct.compareTo(EditArea.INSTR_SPEED) <= 0) {
				i = infp & 0x0f; // lower digit (hex)
				if (infand < 0x0f) {
					if (num <= infand) {
						i = num;
					}
				} else {
					i = ((i << 4) | num) & infand;
				}
			}
			// couldn't quite get decimal to work yet...
			else if (infoAct == EditArea.FIRST_HIGHLIGHT || infoAct == EditArea.SECOND_HIGHLIGHT) {
				i = infp & 0x0f; // lower digit (hex)
				if (infand < 0x0f) {
					if (num <= infand) {
						i = num;
					}
				} else {
					i = (i << 4) | num;
					if (i > infand) {
						i = infand;
					}
				}
			}
			if (i <= 0) {
				i = 1; // all values must be at least 1
			}
			session.undo.changeInfo(0, UndoType.UETYPE_INFODATA);
			setInfoValue(infoAct, i);
			return true;
		}
		// edit_ok:
		switch (vk) {
		case VK_TAB:
			if (control) {
				break; // do nothing
			}
			if (shift) {
				song.setInfoAct(EditArea.NAME); // Shift+TAB => Name
				ui.isEditingInfos = true;
			} else {
				// TAB => Speed variables 1, 2 or 3, or line highlights 4 or 5
				if (infoAct.compareTo(EditArea.SECOND_HIGHLIGHT) < 0) {
					// C++: "auto value = (int)m_infoact; m_infoact = (EditArea)(value++);" - the post-increment
					// assigns the unchanged value, so TAB stays where it is (a real quirk, kept as is)
					song.setInfoAct(infoAct);
				} else {
					song.setInfoAct(EditArea.SPEED);
				}
				ui.isEditingInfos = false;
			}
			return true;

		case VK_UP:
			if (control && shift) {
				break; // do nothing
			}
			if (control) {
				incrementInfoPar(infoAct, infand);
				return true;
			}
			break;

		case VK_DOWN:
			if (control && shift) {
				break; // do nothing
			}
			if (control) {
				decrementInfoPar(infoAct, infand);
				return true;
			}
			break;

		case VK_LEFT:
			if (control && shift) {
				break; // do nothing
			}
			if (control) {
				decrementInfoPar(infoAct, infand);
			} else if (!capsLock && shift || (capsLock && !shift && ui.isEditingInfos) || (capsLock && shift && !ui.isEditingInfos)) {
				activeInstrPrev();
			} else {
				if (infoAct.compareTo(EditArea.SPEED) > 0) {
					if (infoAct == EditArea.FIRST_HIGHLIGHT) {
						song.setInfoAct(EditArea.SECOND_HIGHLIGHT);
					} else {
						song.setInfoAct(editArea(infoAct.ordinal() - 1));
					}
				} else {
					song.setInfoAct(EditArea.INSTR_SPEED);
				}
			}
			return true;

		case VK_RIGHT:
			if (control && shift) {
				break; // do nothing
			}
			if (control) {
				incrementInfoPar(infoAct, infand);
			} else if (!capsLock && shift || (capsLock && !shift && ui.isEditingInfos) || (capsLock && shift && !ui.isEditingInfos)) {
				activeInstrNext();
			} else {
				if (infoAct.compareTo(EditArea.SPEED) >= 0 && infoAct.compareTo(EditArea.FIRST_HIGHLIGHT) < 0) {
					int next = infoAct.ordinal() + 1;
					song.setInfoAct(next > EditArea.INSTR_SPEED.ordinal() ? EditArea.SPEED : editArea(next));
				} else if (infoAct.compareTo(EditArea.FIRST_HIGHLIGHT) >= 0) {
					int next = infoAct.ordinal() + 1;
					song.setInfoAct(next > EditArea.SECOND_HIGHLIGHT.ordinal() ? EditArea.FIRST_HIGHLIGHT : editArea(next));
				}
			}
			return true;

		case VK_RETURN:
			ui.activePart = ui.activeTi;
			return true;

		case VK_MULTIPLY:
			song.octaveUp();
			return true;

		case VK_DIVIDE:
			song.octaveDown();
			return true;

		case VK_ADD:
			song.volumeUp();
			return true;

		case VK_SUBTRACT:
			song.volumeDown();
			return true;

		default:
			break;
		}
		return false;
	}

	/** C++'s {@code DecrementInfoPar:} label. */
	private void decrementInfoPar(EditArea infoAct, int infand) {
		int i = getInfoValue(infoAct);
		i--;
		if (i <= 0) {
			i = infand; // value must be at least 1, roll back to the maximum defined earlier
		}
		session.undo.changeInfo(0, UndoType.UETYPE_INFODATA);
		setInfoValue(infoAct, i);
	}

	/** C++'s {@code IncrementInfoPar:} label. */
	private void incrementInfoPar(EditArea infoAct, int infand) {
		int i = getInfoValue(infoAct);
		i++;
		if (i > infand) {
			i = 1; // value must be at least 1
		}
		session.undo.changeInfo(0, UndoType.UETYPE_INFODATA);
		setInfoValue(infoAct, i);
	}

	// --- CSong::InstrKey ---

	/** C++'s {@code ChangeInstrumentPar:} label: "because there has been some change in the instrument parameter => stop this instrument in all channels". */
	private boolean changeInstrumentPar() {
		int instr = session.song.getActiveInstr();
		session.atariTrackerDriver.instrumentTurnOff(instr);
		session.instruments.checkInstrumentParameters(instr);
		session.instruments.update(instr);
		return true;
	}

	/** C++'s {@code ChangeInstrumentEnv:}/{@code ChangeInstrumentTab:} labels: "something changed => Save instrument to Atari memory". */
	private boolean changeInstrumentEnv() {
		session.instruments.update(session.song.getActiveInstr());
		return true;
	}

	private void undoChangeInstrument() {
		session.undo.changeInstrument(session.song.getActiveInstr(), 0, UndoType.UETYPE_INSTRDATA);
	}

	/** {@code CSong::InstrKey()} - "note: if returning 1, then screenupdate is done in RmtView" */
	boolean instrKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		UiState ui = session.uiState;
		Instrument ai = session.instruments.getInstrument(song.getActiveInstr());
		int i;

		boolean capsLock = ui.capsLock;

		if (!control && !shift && numbKey(vk) >= 0) {
			if (ai.activeEditSection == InstrumentSection.PARAMETERS) { // parameters
				InstrumentsUI.ShPar par = InstrumentsUI.SHPAR[ai.editParameterNr];
				int pmax = par.maxParameterValue();
				int pfrom = par.displayOffset();
				if (numbKey(vk) > pmax + pfrom) {
					return false;
				}
				i = ai.parameters[ai.editParameterNr] + pfrom;
				i &= 0x0f; // lower digit
				if (pmax + pfrom > 0x0f) {
					i = (i << 4) | numbKey(vk);
					if (i > pmax + pfrom) {
						i &= 0x0f; // leaves only the lower digit
					}
				} else {
					if (numbKey(vk) >= pfrom) {
						i = numbKey(vk);
					}
				}
				i -= pfrom;
				if (i < 0) {
					i = 0;
				}
				undoChangeInstrument();
				ai.parameters[ai.editParameterNr] = i;
				return changeInstrumentPar();
			} else if (ai.activeEditSection == InstrumentSection.ENVELOPE) { // envelope
				int eand = InstrumentsUI.SHENV[ai.editEnvelopeY].pand();
				int num = numbKey(vk);
				i = num & eand;
				if (i != num) {
					return false; // something else came out after and number pressed out of range
				}
				undoChangeInstrument();
				ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] = i;
				// shift to the right
				i = ai.editEnvelopeX;
				if (i < ai.parameters[Instrument.PAR_ENV_LENGTH]) {
					i++; // else i=0; //length of env
				}
				ai.editEnvelopeX = i;
				return changeInstrumentEnv();
			} else if (ai.activeEditSection == InstrumentSection.NOTETABLE) { // table
				int num = numbKey(vk);
				i = ((ai.noteTable[ai.editNoteTableCursorPos] << 4) | num) & 0xff;
				undoChangeInstrument();
				ai.noteTable[ai.editNoteTableCursorPos] = i;
				return changeInstrumentEnv();
			}
		}

		// For name, parameters, envelope and table
		switch (vk) {
		case VK_TAB:
			if (control) {
				break; // do nothing
			}
			if (ai.activeEditSection == InstrumentSection.NAME) {
				break; // is editing text
			}
			if (shift) {
				ai.activeEditSection = InstrumentSection.NAME; // Shift+TAB => Name
				ui.isEditingInstrumentName = true;
			} else {
				switch (ai.activeEditSection) { // TAB 1,2,3
				case PARAMETERS -> ai.activeEditSection = InstrumentSection.ENVELOPE;
				case ENVELOPE -> ai.activeEditSection = InstrumentSection.NOTETABLE;
				case NOTETABLE -> ai.activeEditSection = InstrumentSection.PARAMETERS;
				default -> {
				}
				}

				ui.isEditingInstrumentName = false;
			}
			session.undo.separator();
			return true;

		case VK_LEFT:
			if (!control && (!capsLock && shift || (capsLock && !shift && ui.isEditingInstrumentName) || (capsLock && shift && !ui.isEditingInstrumentName))) {
				activeInstrPrev();
				return true;
			}
			break;

		case VK_RIGHT:
			if (!control && (!capsLock && shift || (capsLock && !shift && ui.isEditingInstrumentName) || (capsLock && shift && !ui.isEditingInstrumentName))) {
				activeInstrNext();
				return true;
			}
			break;

		case VK_UP:
		case VK_DOWN:
			if (capsLock && shift) {
				break;
			}

			if (shift && !control) {
				return false; // the combination Shift + Control + UP / DOWN is enabled for edit ENVELOPE and TABLE
			}
			if (shift && control && ai.activeEditSection == InstrumentSection.PARAMETERS) {
				return false; // except for edit PARAM, is not allowed there
			}
			break;

		case VK_MULTIPLY:
			song.octaveUp();
			return true;

		case VK_DIVIDE:
			song.octaveDown();
			return true;

		case VK_SUBTRACT: // Numlock minus
			if (shift && control) { // S+C+numlock_minus  ...reading the whole curve with a minimum of 0
				undoChangeInstrument();
				boolean br = false;
				boolean bl = false;
				if (ai.activeEditSection == InstrumentSection.ENVELOPE && ai.editEnvelopeY == EnvelopeParameter.VOLUMER) {
					br = true;
				} else if (ai.activeEditSection == InstrumentSection.ENVELOPE && ai.editEnvelopeY == EnvelopeParameter.VOLUMEL) {
					bl = true;
				} else {
					br = bl = true;
				}
				for (int e = 0; e <= ai.parameters[Instrument.PAR_ENV_LENGTH]; e++) {
					if (br) {
						if (ai.envelope[e][EnvelopeParameter.VOLUMER] > 0) {
							ai.envelope[e][EnvelopeParameter.VOLUMER]--;
						}
					}
					if (bl) {
						if (ai.envelope[e][EnvelopeParameter.VOLUMEL] > 0) {
							ai.envelope[e][EnvelopeParameter.VOLUMEL]--;
						}
					}
				}
				return changeInstrumentEnv();
			} else {
				song.volumeDown();
			}
			return true;

		case VK_ADD: // Numlock plus
			if (shift && control) { // S+C+numlock_plus ...applying the whole curve with maximum f
				undoChangeInstrument();
				boolean br = false;
				boolean bl = false;
				if (ai.activeEditSection == InstrumentSection.ENVELOPE && ai.editEnvelopeY == EnvelopeParameter.VOLUMER) {
					br = true;
				} else if (ai.activeEditSection == InstrumentSection.ENVELOPE && ai.editEnvelopeY == EnvelopeParameter.VOLUMEL) {
					bl = true;
				} else {
					br = bl = true;
				}
				for (int e = 0; e <= ai.parameters[Instrument.PAR_ENV_LENGTH]; e++) {
					if (br) {
						if (ai.envelope[e][EnvelopeParameter.VOLUMER] < 0x0f) {
							ai.envelope[e][EnvelopeParameter.VOLUMER]++;
						}
					}
					if (bl) {
						if (ai.envelope[e][EnvelopeParameter.VOLUMEL] < 0x0f) {
							ai.envelope[e][EnvelopeParameter.VOLUMEL]++;
						}
					}
				}
				return changeInstrumentEnv();
			} else {
				song.volumeUp();
			}
			return true;

		default:
			break;
		}

		// and now only for special parts
		if (ai.activeEditSection == InstrumentSection.NAME) {
			ui.isEditingInstrumentName = true;
			// NAME
			if (isNotAMovementVKey(vk)) { // saves undo only if it is not cursor movement
				undoChangeInstrument();
			}
			TextFieldEditor.Result r = TextFieldEditor.editText(vk, shift, control, ai.name, ai.editNameCursorPos, Instrument.INSTRUMENT_NAME_MAX_LEN);
			ai.editNameCursorPos = r.cursor();
			if (r.done()) {
				ai.activeEditSection = InstrumentSection.PARAMETERS;
			}

			return true;
		} else if (ai.activeEditSection == InstrumentSection.PARAMETERS) {
			// Parameter section is active
			ui.isEditingInstrumentName = false;
			InstrumentsUI.ShPar par = InstrumentsUI.SHPAR[ai.editParameterNr];
			switch (vk) {
			case VK_UP:
				if (control) {
					return parameterInc(ai);
				}
				ai.editParameterNr = par.gotoUp();
				return true;

			case VK_DOWN:
				if (control) {
					return parameterDec(ai);
				}
				ai.editParameterNr = par.gotoDown();
				return true;

			case VK_LEFT:
				if (control) {
					// Change the parameter value
					return parameterDec(ai);
				}
				// Move to the next parameter
				ai.editParameterNr = par.gotoLeft();
				return true;

			case VK_RIGHT:
				if (control) {
					// Change the parameter value
					return parameterInc(ai);
				}
				// Move to the next parameter
				ai.editParameterNr = par.gotoRight();
				return true;

			case VK_HOME:
				ai.editParameterNr = Instrument.PAR_ENV_LENGTH;
				return true;

			case VK_SPACE:
				if (control) {
					break; // prevents inputing a SPACE while exiting PROVE mode
				}
				// falls through
			case VK_BACK: // BACKSPACE
			case VK_DELETE:
				undoChangeInstrument();
				ai.parameters[ai.editParameterNr] = 0;
				return changeInstrumentPar();

			default:
				break;
			}
		} else if (ai.activeEditSection == InstrumentSection.ENVELOPE) {
			ui.isEditingInstrumentName = false;
			// ENVELOPE
			InstrumentsUI.ShEnv env = InstrumentsUI.SHENV[ai.editEnvelopeY];
			switch (vk) {
			case VK_UP:
				if (control) { //
					if (shift) { // SHIFT+CONTROL+UP
						undoChangeInstrument();
						for (int e = 0; e <= ai.parameters[Instrument.PAR_ENV_LENGTH]; e++) {
							ai.envelope[e][ai.editEnvelopeY] = (ai.envelope[e][ai.editEnvelopeY] + env.padd()) & env.pand();
						}
						return changeInstrumentEnv();
					}
					return envelopeInc(ai);
				}
				i = ai.editEnvelopeY;
				if (i > 0) {
					i--;
					if (i == 0 && song.getTracks(session.tracks4_8) <= 4) {
						i = 7; // mono mode
					}
				} else {
					i = 7;
				}
				ai.editEnvelopeY = i;
				return true;

			case VK_DOWN:
				if (control) { //
					if (shift) { // SHIFT+CONTROL+DOWN
						undoChangeInstrument();
						for (int e = 0; e <= ai.parameters[Instrument.PAR_ENV_LENGTH]; e++) {
							ai.envelope[e][ai.editEnvelopeY] = (ai.envelope[e][ai.editEnvelopeY] + env.psub()) & env.pand();
						}
						return changeInstrumentEnv();
					}
					return envelopeDec(ai);
				}
				i = ai.editEnvelopeY;
				if (i < 7) {
					i++;
				} else {
					i = (song.getTracks(session.tracks4_8) > 4) ? 0 : 1;
				}
				ai.editEnvelopeY = i;
				return true;

			case VK_LEFT:
				if (control) {
					return envelopeDec(ai);
				}
				i = ai.editEnvelopeX;
				if (i > 0) {
					i--;
				} else {
					i = ai.parameters[Instrument.PAR_ENV_LENGTH]; // length of env
				}
				ai.editEnvelopeX = i;
				return true;

			case VK_RIGHT:
				if (control) {
					return envelopeInc(ai);
				}
				i = ai.editEnvelopeX;
				if (i < ai.parameters[Instrument.PAR_ENV_LENGTH]) {
					i++;
				} else {
					i = 0; // length of env
				}
				ai.editEnvelopeX = i;
				return true;

			case VK_HOME:
				if (control) {
					undoChangeInstrument();
					ai.parameters[Instrument.PAR_ENV_GOTO] = ai.editEnvelopeX; // sets ENVGO to this column
					return changeInstrumentPar(); // yes, that's fine, it really changed the PARAMETER, even if it's in the envelope
				}
				// goes left to column 0 or to the beginning of the GO loop
				if (ai.editEnvelopeX != 0) {
					ai.editEnvelopeX = 0;
				} else {
					ai.editEnvelopeX = ai.parameters[Instrument.PAR_ENV_GOTO];
				}
				return true;

			case VK_END:
				if (control) {
					undoChangeInstrument();
					if (ai.editEnvelopeX == ai.parameters[Instrument.PAR_ENV_LENGTH]) { // sets ENVLEN to this column or to the end
						ai.parameters[Instrument.PAR_ENV_LENGTH] = Instrument.ENVELOPE_MAX_COLUMNS - 1;
					} else {
						ai.parameters[Instrument.PAR_ENV_LENGTH] = ai.editEnvelopeX;
					}
					return changeInstrumentPar(); // yes, changed PAR from envelope
				}
				ai.editEnvelopeX = ai.parameters[Instrument.PAR_ENV_LENGTH]; // moves the cursor to the right to the end
				return true;

			case VK_BACK:
				undoChangeInstrument();
				ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] = 0;
				return changeInstrumentEnv();

			case VK_SPACE:
				if (control) {
					break; // prevents inputing a SPACE while exiting PROVE mode
				}
				undoChangeInstrument();
				for (int j = 0; j < Instrument.ENVROWS; j++) {
					ai.envelope[ai.editEnvelopeX][j] = 0;
				}
				if (ai.editEnvelopeX < ai.parameters[Instrument.PAR_ENV_LENGTH]) {
					ai.editEnvelopeX++; // shift to the right
				}
				return changeInstrumentEnv();

			case VK_INSERT:
				if (!control) { // moves the envelope from the current position to the right
					undoChangeInstrument();
					int ele = ai.parameters[Instrument.PAR_ENV_LENGTH];
					int ego = ai.parameters[Instrument.PAR_ENV_GOTO];
					if (ele < Instrument.ENVELOPE_MAX_COLUMNS - 1) {
						ele++;
					}
					if (ai.editEnvelopeX < ego && ego < Instrument.ENVELOPE_MAX_COLUMNS - 1) {
						ego++;
					}
					for (int c = Instrument.ENVELOPE_MAX_COLUMNS - 2; c >= ai.editEnvelopeX; c--) {
						for (int j = 0; j < Instrument.ENVROWS; j++) {
							ai.envelope[c + 1][j] = ai.envelope[c][j];
						}
					}
					// improvement: with shift it will leave it there (it will not erase the column)
					if (!shift) {
						for (int j = 0; j < Instrument.ENVROWS; j++) {
							ai.envelope[ai.editEnvelopeX][j] = 0;
						}
					}
					ai.parameters[Instrument.PAR_ENV_LENGTH] = ele;
					ai.parameters[Instrument.PAR_ENV_GOTO] = ego;
					return changeInstrumentPar(); // changed length and / or go parameters
				}
				return false; // without screen update

			case VK_DELETE:
				if (!control) { // moves the envelope from the current position to the left
					undoChangeInstrument();
					int ele = ai.parameters[Instrument.PAR_ENV_LENGTH];
					int ego = ai.parameters[Instrument.PAR_ENV_GOTO];
					if (ele > 0) {
						ele--;
						for (int c = ai.editEnvelopeX; c < Instrument.ENVELOPE_MAX_COLUMNS - 1; c++) {
							for (int j = 0; j < Instrument.ENVROWS; j++) {
								ai.envelope[c][j] = ai.envelope[c + 1][j];
							}
						}
						for (int j = 0; j < Instrument.ENVROWS; j++) {
							ai.envelope[Instrument.ENVELOPE_MAX_COLUMNS - 1][j] = 0;
						}
					} else {
						for (int j = 0; j < Instrument.ENVROWS; j++) {
							ai.envelope[0][j] = 0;
						}
					}
					if (ai.editEnvelopeX < ego) {
						ego--;
					}
					if (ego > ele) {
						ego = ele;
					}
					ai.parameters[Instrument.PAR_ENV_GOTO] = ego;
					ai.parameters[Instrument.PAR_ENV_LENGTH] = ele;
					if (ai.editEnvelopeX > ele) {
						ai.editEnvelopeX = ele;
					}
					return changeInstrumentPar(); // changed length and / or go parameters
				}
				return false; // without screen update

			default:
				break;
			}
		}
		if (ai.activeEditSection == InstrumentSection.NOTETABLE) {
			ui.isEditingInstrumentName = false;
			// TABLE
			switch (vk) {
			case VK_HOME:
				if (control) { // set a TABLE go loop here
					undoChangeInstrument();
					ai.parameters[Instrument.PAR_TBL_GOTO] = ai.editNoteTableCursorPos;
					if (ai.editNoteTableCursorPos > ai.parameters[Instrument.PAR_TBL_LENGTH]) {
						ai.parameters[Instrument.PAR_TBL_LENGTH] = ai.editNoteTableCursorPos;
					}
					return changeInstrumentPar();
				}
				// go to the beginning of the TABLE and to the beginning of the TABLE loop
				if (ai.editNoteTableCursorPos != 0) {
					ai.editNoteTableCursorPos = 0;
				} else {
					ai.editNoteTableCursorPos = ai.parameters[Instrument.PAR_TBL_GOTO];
				}
				return true;

			case VK_END:
				if (control) { // set TABLE only by location
					undoChangeInstrument();
					if (ai.editNoteTableCursorPos == ai.parameters[Instrument.PAR_TBL_LENGTH]) {
						ai.parameters[Instrument.PAR_TBL_LENGTH] = Instrument.NOTE_TABLE_MAX_LEN - 1;
					} else {
						ai.parameters[Instrument.PAR_TBL_LENGTH] = ai.editNoteTableCursorPos;
					}
					return changeInstrumentPar();
				}
				// goes to the last parameter in the TABLE
				ai.editNoteTableCursorPos = ai.parameters[Instrument.PAR_TBL_LENGTH];
				return true;

			case VK_UP:
				if (control) {
					if (shift) { // Shift+Control+UP
						undoChangeInstrument();
						for (int n = 0; n <= ai.parameters[Instrument.PAR_TBL_LENGTH]; n++) {
							ai.noteTable[n] = (ai.noteTable[n] + 1) & 0xff;
						}
						return changeInstrumentEnv();
					}
					return tableInc(ai);
				}
				return true;

			case VK_DOWN:
				if (control) {
					if (shift) { // Shift+Control+DOWN
						undoChangeInstrument();
						for (int n = 0; n <= ai.parameters[Instrument.PAR_TBL_LENGTH]; n++) {
							ai.noteTable[n] = (ai.noteTable[n] - 1) & 0xff;
						}
						return changeInstrumentEnv();
					}
					return tableDec(ai);
				}
				return true;

			case VK_LEFT:
				if (control) {
					return tableDec(ai);
				}
				i = ai.editNoteTableCursorPos - 1;
				if (i < 0) {
					i = ai.parameters[Instrument.PAR_TBL_LENGTH];
				}
				ai.editNoteTableCursorPos = i;
				return changeInstrumentEnv();

			case VK_RIGHT:
				if (control) {
					return tableInc(ai);
				}
				i = ai.editNoteTableCursorPos + 1;
				if (i > ai.parameters[Instrument.PAR_TBL_LENGTH]) {
					i = 0;
				}
				ai.editNoteTableCursorPos = i;
				return changeInstrumentEnv();

			case VK_SPACE: // parameter reset and shift by 1 to the right
				if (control) {
					break; // prevents inputing a SPACE while exiting PROVE mode
				}
				if (ai.editNoteTableCursorPos < ai.parameters[Instrument.PAR_TBL_LENGTH]) {
					ai.editNoteTableCursorPos++;
				}
				// and proceeds the same as VK_BACKSPACE
				// falls through
			case VK_BACK: // parameter reset
				undoChangeInstrument();
				ai.noteTable[ai.editNoteTableCursorPos] = 0;
				return changeInstrumentEnv();

			case VK_INSERT:
				if (!control) { // moves the table from the current position to the right
					undoChangeInstrument();
					int tle = ai.parameters[Instrument.PAR_TBL_LENGTH];
					int tgo = ai.parameters[Instrument.PAR_TBL_GOTO];
					if (tle < Instrument.NOTE_TABLE_MAX_LEN - 1) {
						tle++;
					}
					if (ai.editNoteTableCursorPos < tgo && tgo < Instrument.NOTE_TABLE_MAX_LEN - 1) {
						tgo++;
					}
					for (int n = Instrument.NOTE_TABLE_MAX_LEN - 2; n >= ai.editNoteTableCursorPos; n--) {
						ai.noteTable[n + 1] = ai.noteTable[n];
					}
					if (!shift) {
						ai.noteTable[ai.editNoteTableCursorPos] = 0; // with the shift it will leave there
					}
					ai.parameters[Instrument.PAR_TBL_LENGTH] = tle;
					ai.parameters[Instrument.PAR_TBL_GOTO] = tgo;
					return changeInstrumentPar(); // changed TABLE LEN or GO, must stop the instrument
				}
				return false; // without screen update

			case VK_DELETE:
				if (!control) { // moves the table from the current position to the left
					undoChangeInstrument();
					int tle = ai.parameters[Instrument.PAR_TBL_LENGTH];
					int tgo = ai.parameters[Instrument.PAR_TBL_GOTO];
					if (tle > 0) {
						tle--;
						for (int n = ai.editNoteTableCursorPos; n < Instrument.NOTE_TABLE_MAX_LEN - 1; n++) {
							ai.noteTable[n] = ai.noteTable[n + 1];
						}
						ai.noteTable[Instrument.NOTE_TABLE_MAX_LEN - 1] = 0;
					} else {
						ai.noteTable[0] = 0;
					}
					if (ai.editNoteTableCursorPos < tgo) {
						tgo--;
					}
					if (tgo > tle) {
						tgo = tle;
					}
					ai.parameters[Instrument.PAR_TBL_LENGTH] = tle;
					ai.parameters[Instrument.PAR_TBL_GOTO] = tgo;
					if (ai.editNoteTableCursorPos > tle) {
						ai.editNoteTableCursorPos = tle;
					}
					return changeInstrumentPar(); // changed TABLE LEN or GO, must stop the instrument
				}
				return false; // without screen update

			default:
				break;
			}
		}
		return false; // => SCREENUPDATE will not be performed
	}

	/** C++'s {@code ParameterInc:} label. */
	private boolean parameterInc(Instrument ai) {
		undoChangeInstrument();
		int v = ai.parameters[ai.editParameterNr] + 1;
		if (v > InstrumentsUI.SHPAR[ai.editParameterNr].maxParameterValue()) {
			v = 0;
		}
		ai.parameters[ai.editParameterNr] = v;
		return changeInstrumentPar();
	}

	/** C++'s {@code ParameterDec:} label. */
	private boolean parameterDec(Instrument ai) {
		undoChangeInstrument();
		int v = ai.parameters[ai.editParameterNr] - 1;
		if (v < 0) {
			v = InstrumentsUI.SHPAR[ai.editParameterNr].maxParameterValue();
		}
		ai.parameters[ai.editParameterNr] = v;
		return changeInstrumentPar();
	}

	/** C++'s {@code EnvelopeInc:} label. */
	private boolean envelopeInc(Instrument ai) {
		undoChangeInstrument();
		InstrumentsUI.ShEnv env = InstrumentsUI.SHENV[ai.editEnvelopeY];
		ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] = (ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] + env.padd()) & env.pand();
		return changeInstrumentEnv();
	}

	/** C++'s {@code EnvelopeDec:} label. */
	private boolean envelopeDec(Instrument ai) {
		undoChangeInstrument();
		InstrumentsUI.ShEnv env = InstrumentsUI.SHENV[ai.editEnvelopeY];
		ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] = (ai.envelope[ai.editEnvelopeX][ai.editEnvelopeY] + env.psub()) & env.pand();
		return changeInstrumentEnv();
	}

	/** C++'s {@code TableInc:} label. */
	private boolean tableInc(Instrument ai) {
		undoChangeInstrument();
		ai.noteTable[ai.editNoteTableCursorPos] = (ai.noteTable[ai.editNoteTableCursorPos] + 1) & 0xff;
		return changeInstrumentEnv();
	}

	/** C++'s {@code TableDec:} label. */
	private boolean tableDec(Instrument ai) {
		undoChangeInstrument();
		ai.noteTable[ai.editNoteTableCursorPos] = (ai.noteTable[ai.editNoteTableCursorPos] - 1) & 0xff;
		return changeInstrumentEnv();
	}

	// --- CSong::ProveKey ---

	/** {@code CSong::ProveKey()}: the jam ("prove") mode keys - notes play live, the cursor still moves. */
	boolean proveKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		UiState ui = session.uiState;
		Tracks tracks = session.tracks;
		int tracks4_8 = session.tracks4_8;

		if (ui.editMode == EditMode.POKEY_EXPLORER_MODE) { // POKEY EXPLORER MODE: FULL CONTROL OVER THE POKEY (IGNORE RMT ROUTINES EXCEPT SETPOKEY)
			return false; // B3/B8: m_PokeyController->OnKeyDown(vk, shift, control) - CPokeyController is unported
		}

		int note = noteKey(vk);

		if (note >= 0) {
			int i = note + song.getOctave() * 12;
			if (i >= 0 && i < Notes.NOTESNUM) { // only within limits
				song.setPlayPressedTonesTNIV(song.getActiveColumn(), i, song.getActiveInstr(), song.getVolume());
				if ((control || ui.editMode == EditMode.JAM_STEREO_MODE) && song.getTracks(tracks4_8) > 4) {
					// with control or in prove2 => stereo test
					song.setPlayPressedTonesTNIV((song.getActiveColumn() + 4) & 0x07, i, song.getActiveInstr(), song.getVolume());
				}
			}
			return false; // they don't have to redraw
		}

		if (song.songGetGo() >= 0) { // is active song go to line => they must not edit anything
			if (!control && (vk == VK_UP || vk == VK_PRIOR)) { // GO - key up
				song.setActiveLine(0);
				trackUp(skipLines() == 0 ? 1 : skipLines());
				return true;
			}
			if (!control && (vk == VK_DOWN || vk == VK_NEXT)) { // GO - key down
				song.setActiveLine(tracks.getMaxTrackLength() - 1);
				trackDown(1, false);
				return true;
			}
		}

		switch (vk) {
		case VK_LEFT:
			if (control) {
				break; // do nothing
			}
			if (shift) {
				activeInstrPrev();
			} else if (ui.activePart != Part.PART_TRACKS) { // anywhere but tracks
				trackLeft(true);
			} else {
				trackLeft(false);
			}
			break;

		case VK_RIGHT:
			if (control) {
				break; // do nothing
			}
			if (shift) {
				activeInstrNext();
			} else if (ui.activePart != Part.PART_TRACKS) { // anywhere but tracks
				trackRight(true);
			} else {
				trackRight(false);
			}
			break;

		case VK_UP:
			if (shift) {
				break; // do nothing
			}
			if (control || ui.activePart != Part.PART_TRACKS) { // anywhere but tracks
				songUp();
			} else {
				trackUp(skipLines() == 0 ? 1 : skipLines());
			}
			break;

		case VK_DOWN:
			if (shift) {
				break; // do nothing
			}
			if (control || ui.activePart != Part.PART_TRACKS) { // anywhere but tracks
				songDown();
			} else {
				trackDown(skipLines() == 0 ? 1 : skipLines(), false); // stoponlastline = 0 => will not stop on the last line of the track
			}
			break;

		case VK_TAB:
			if (shift) {
				trackLeft(true); // Shift+TAB
			} else if (control) {
				cursorToSpeedColumn(); // Ctrl+TAB
			} else {
				trackRight(true);
			}
			break;

		case VK_SPACE:
			// (with control: prevents inputing a SPACE while exiting PROVE mode - nothing to do either way)
			break;

		case VK_SUBTRACT:
			song.volumeDown();
			break;

		case VK_ADD:
			song.volumeUp();
			break;

		case VK_DIVIDE:
			song.octaveDown();
			break;

		case VK_MULTIPLY:
			song.octaveUp();
			break;

		case VK_PRIOR:
			if (ui.activePart != Part.PART_TRACKS) {
				if (shift) {
					song.songSubsongPrev(session.undo);
				} else {
					songUp();
				}
				break;
			} else {
				if (!shift && control) {
					songUp();
				} else if (!control && shift) {
					// move to the previous goto
					song.songSubsongPrev(session.undo);
				}
				if (isPlayingAndFollowing()) {
					break; // prevents moving at all during play+follow
				} else {
					if (song.getActiveLine() > 0) {
						song.setActiveLine(((song.getActiveLine() - 1) / session.options.trackLinePrimaryHighlight) * session.options.trackLinePrimaryHighlight);
					}
				}
			}
			break;

		case VK_NEXT:
			if (ui.activePart != Part.PART_TRACKS) {
				if (shift) {
					song.songSubsongNext(session.undo);
				} else {
					songDown();
				}
				break;
			} else {
				if (!shift && control) {
					songDown();
				} else if (!control && shift) {
					// move to the next goto
					song.songSubsongNext(session.undo);
				}
				if (isPlayingAndFollowing()) {
					break; // prevents moving at all during play+follow
				} else {
					int line = ((song.getActiveLine() + session.options.trackLinePrimaryHighlight) / session.options.trackLinePrimaryHighlight) * session.options.trackLinePrimaryHighlight;
					if (line > song.getSmallestMaxtracklen(song.songGetActiveLine(), tracks4_8) - 1) {
						line -= session.options.trackLinePrimaryHighlight;
					}
					song.setActiveLine(line);
				}
			}
			break;

		case VK_HOME:
			if (control || shift) {
				break; // do nothing
			}
			if (ui.activePart == Part.PART_TRACKS) { // tracks
				song.setActiveLine(0); // line 0
			} else if (ui.activePart == Part.PART_SONG) { // song lines
				song.songSetActiveLine(0);
			}
			break;

		case VK_END:
			if (control || shift) {
				break; // do nothing
			}
			if (ui.activePart == Part.PART_TRACKS) { // tracks
				if (song.trackGetGoLine() >= 0) {
					song.setActiveLine(tracks.getMaxTrackLength() - 1); // last line
				} else {
					song.setActiveLine(song.trackGetLastLine()); // end line
				}
				if (song.getActiveLine() < 0) {
					song.setActiveLine(tracks.getMaxTrackLength() - 1); // failsafe in case the active line is out of bounds
				}
			} else if (ui.activePart == Part.PART_SONG) { // song lines
				song.songSetActiveLine(lastUsedSongline());
			}
			break;

		case VK_RETURN:
			if (ui.activePart == Part.PART_TRACKS) {
				if (control != session.options.keyboardSwapEnter) { // control+Enter => plays a whole line (all tracks)
					// for all track columns except the active track column
					for (int i = 0; i < song.getTracks(tracks4_8); i++) {
						if (i != song.getActiveColumn()) {
							playLineColumn(i, song.songGetTrack(song.songGetActiveLine(), i));
						}
					}
				}
				// and now for that active track column
				playLineColumn(song.getActiveColumn(), song.songGetActiveTrack());
				trackDown(1, false); // move down 1 step always
			} else {
				ui.activePart = ui.activeTi;
				return true;
			}
			break;

		default:
			return false;
		}
		return true;
	}

	/** The "is there a note? ... is there a separate volume?" step shared by {@code ProveKey}'s and {@code TrackKey}'s Enter: queues track {@code track}'s current line for playing on column {@code column}. */
	private void playLineColumn(int column, int track) {
		Song song = session.song;
		Song.NoteInstrVol niv = song.trackGetLoopingNoteInstrVol(track);
		if (niv.note() >= 0) { // is there a note?
			song.setPlayPressedTonesTNIV(column, niv.note(), niv.instr(), niv.vol()); // it will lose it as it is there
		} else if (niv.vol() >= 0) { // there is no note, but is there a separate volume?
			song.setPlayPressedTonesV(column, niv.vol()); // adjust the volume as it is
		}
	}

	/** {@code SongKey}'s/{@code ProveKey}'s End: the last songline that has a track or a goto. */
	private int lastUsedSongline() {
		Song song = session.song;
		int[][] songLines = song.getSong();
		int[] songGo = song.getSongGo();
		int la = 0;
		for (int j = 0; j < Song.SONGLEN; j++) {
			for (int i = 0; i < song.getTracks(session.tracks4_8); i++) {
				if (songLines[j][i] >= 0) {
					la = j;
					break;
				}
			}
			if (songGo[j] >= 0) {
				la = j;
			}
		}
		return la;
	}

	// --- CSong::TrackKey ---

	/** {@code CSong::TrackKey()} */
	boolean trackKey(int vk, boolean shift, boolean control) {
		final int VKX_SONGINSERTLINE = VK_I;
		final int VKX_SONGDELETELINE = VK_U;
		final int VKX_SONGDUPLICATELINE = VK_O;
		final int VKX_SONGPREPARELINE = VK_P;

		Song song = session.song;
		Tracks tracks = session.tracks;
		TrackClipboard clipboard = session.clipboard;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		int tracks4_8 = session.tracks4_8;
		int note;
		int i;
		int j;

		if (clipboard.isBlockSelected() && song.songGetActiveTrack() != clipboard.getSelTrack()) {
			blockDeselect();
		}

		if (song.songGetGo() >= 0) { // is active song go to line => they must not edit anything
			if (!control && (vk == VK_UP || vk == VK_PRIOR)) { // GO - key up
				song.setActiveLine(0); // always assume it went from line 0
				trackUp(skipLines() == 0 ? 1 : skipLines());
				return true;
			}
			if (!control && (vk == VK_DOWN || vk == VK_NEXT)) { // GO - key down
				song.setActiveLine(tracks.getMaxTrackLength() - 1); // always reset to line 0
				trackDown(1, false);
				return true;
			}
			if (!control && !shift) {
				return false;
			}
			if (control && (vk == VK_BACK || vk == VK_G)) { // control+backspace or control+G
				song.songTrackGoOnOff(session.undo);
				return true;
			}
			boolean trackKeyOk = control && !shift && (vk == VKX_SONGINSERTLINE || vk == VKX_SONGDELETELINE || vk == VKX_SONGPREPARELINE || vk == VKX_SONGDUPLICATELINE || vk == VK_PRIOR || vk == VK_NEXT);
			if (!trackKeyOk && vk != VK_LEFT && vk != VK_RIGHT && vk != VK_UP && vk != VK_DOWN) {
				return false;
			}
		}
		// TrackKeyOk:

		switch (song.getTrackActiveCur()) {
		case 0: // note column
			if (control) {
				break; // with control, notes are not entered (break continues)
			}
			note = noteKey(vk);
			if (note >= 0) {
				insertNote(note);
				return true;
			} else // the numbers 1-6 on the numeral are overwritten by an octave
			if ((j = numblock09Key(vk)) >= 1 && j <= 6 && song.getActiveLine() <= song.trackGetLastLine()) {
				note = song.trackGetNote();
				if (note >= 0) { // is there a note?
					blockDeselect();
					note = (note % 12) + ((j - 1) * 12); // changes its octave according to the number pressed on the numblock
					if (note >= 0 && note < Notes.NOTESNUM) {
						int instr = song.trackGetInstr();
						int vol = song.trackGetVol();
						if (song.trackSetNoteInstrVol(note, instr, vol, ui.respectVolume, session.undo)) {
							song.setPlayPressedTonesTNIV(song.getActiveColumn(), note, instr, vol);
						}
					}
				}
				if (!isPlayingAndFollowing()) {
					trackDown(skipLines(), true);
				}
				return true;
			}
			break;

		case 1: // instrument column
			i = numbKey(vk);
			note = noteKey(vk); // workaround: the note key is known early in case it is needed
			if (i >= 0 && !shift && !control) {
				blockDeselect();
				if (song.trackGetNote() >= 0) { // the instrument number can only be changed if there is a note
					j = ((song.trackGetInstr() & 0x0f) << 4) | i;
					if (j >= Instruments.INSTRSNUM) {
						j &= 0x0f; // leaves only the lower digit
					}
					song.trackSetInstr(j, session.undo);
					return true;
				}
				// testnotevalue: attempt to catch a fail by testing the other possible condition anyway
				return testNoteValue(note);
			} else if (note >= 0 && !shift && !control) {
				return testNoteValue(note);
			}
			break;

		case 2: // volume column
			i = numbKey(vk);
			if (i >= 0 && !shift && !control) {
				blockDeselect();
				if (song.trackSetVol(i, session.undo) && !isPlayingAndFollowing()) {
					trackDown(skipLines(), true);
				}
				return true;
			}
			break;

		case 3: // speed column
			i = numbKey(vk);
			if (i >= 0 && !shift && !control) {
				blockDeselect();
				j = song.trackGetSpeed();
				if (j < 0) {
					j = 0;
				}
				j = ((j & 0x0f) << 4) | i;
				if (j >= Tracks.TRACKMAXSPEED) {
					j &= 0x0f; // leaves only the lower digit
				}
				if (j <= 0) {
					j = -1; // zero does not exist
				}
				song.trackSetSpeed(j, session.undo);
				return true;
			}
			break;

		default:
			break;
		}

		switch (vk) {
		case VK_UP:
			if (control && shift) {
				makeBlockAtCursorIfNone();
				// volume change incrementing
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockVolumeChange(song.getActiveInstr(), 1, tracks);
			} else if (shift && !control) {
				// block selection
				blockSetBegin();
				trackUp(skipLines() == 0 ? 1 : skipLines());
				blockSetEnd();
			} else if (control && !shift) {
				if (isBlockSelected()) {
					blockDeselect();
					break;
				}
				songUp();
			} else {
				blockDeselect();
				trackUp(skipLines() == 0 ? 1 : skipLines());
			}
			break;

		case VK_DOWN:
			if (control && shift) {
				makeBlockAtCursorIfNone();
				// volume change decrementing
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockVolumeChange(song.getActiveInstr(), -1, tracks);
			} else if (shift && !control) {
				// block selection
				blockSetBegin();
				trackDown(skipLines() == 0 ? 1 : skipLines(), false); // will not stop on the last line
				blockSetEnd();
			} else if (control && !shift) {
				if (isBlockSelected()) {
					blockDeselect();
					break;
				}
				songDown();
			} else {
				blockDeselect();
				trackDown(skipLines() == 0 ? 1 : skipLines(), false); // will not stop on the last line
			}
			break;

		case VK_LEFT:
			if (control && shift) {
				makeBlockAtCursorIfNone();
				// instrument changes decrementing
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockInstrumentChange(song.getActiveInstr(), -1, tracks);
			} else if (shift && !control) {
				activeInstrPrev();
			} else if (control && !shift) {
				if (isBlockSelected()) {
					blockDeselect();
					break;
				}
				song.songTrackDec(session.undo);
			} else {
				blockDeselect();
				trackLeft(false);
			}
			break;

		case VK_RIGHT:
			if (control && shift) {
				makeBlockAtCursorIfNone();
				// instrument changes incrementing
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockInstrumentChange(song.getActiveInstr(), 1, tracks);
			} else if (shift && !control) {
				activeInstrNext();
			} else if (control && !shift) {
				if (isBlockSelected()) {
					blockDeselect();
					break;
				}
				song.songTrackInc(session.undo);
			} else {
				blockDeselect();
				trackRight(false);
			}
			break;

		case VK_PRIOR:
			if (!shift && control) {
				blockDeselect();
				songUp();
			} else if (!control && shift) {
				// move to the previous goto
				blockDeselect();
				song.songSubsongPrev(session.undo);
			} else if (isPlayingAndFollowing()) {
				break; // prevents moving at all during play+follow
			} else {
				blockDeselect();
				if (song.getActiveLine() > 0) {
					song.setActiveLine(((song.getActiveLine() - 1) / options.trackLinePrimaryHighlight) * options.trackLinePrimaryHighlight);
				}
			}
			break;

		case VK_NEXT:
			if (!shift && control) {
				blockDeselect();
				songDown();
			} else if (!control && shift) {
				// move to the next goto
				blockDeselect();
				song.songSubsongNext(session.undo);
			} else if (isPlayingAndFollowing()) {
				break; // prevents moving at all during play+follow
			} else {
				blockDeselect();
				int line = ((song.getActiveLine() + options.trackLinePrimaryHighlight) / options.trackLinePrimaryHighlight) * options.trackLinePrimaryHighlight;
				if (line > song.getSmallestMaxtracklen(song.songGetActiveLine(), tracks4_8) - 1) {
					line -= options.trackLinePrimaryHighlight;
				}
				song.setActiveLine(line);
			}
			break;

		case VK_SUBTRACT:
			song.volumeDown();
			break;

		case VK_ADD:
			song.volumeUp();
			break;

		case VK_DIVIDE:
			song.octaveDown();
			break;

		case VK_MULTIPLY:
			song.octaveUp();
			break;

		case VK_TAB:
			blockDeselect();
			if (shift) {
				trackLeft(true); // Shift+TAB
			} else if (control) {
				cursorToSpeedColumn(); // Ctrl+TAB
			} else {
				trackRight(true);
			}
			break;

		case VK_ESCAPE:
			blockDeselect();
			break;

		case VK_A:
			if (isBlockSelected() && shift && control) { // Shift+control+A
				// switch ALL / no ALL
				clipboard.blockAllOnOff(tracks);
			} else if (control && !shift) {
				// control+A
				// selection of the whole track (from 0 to the length of that track)
				clipboard.blockDeselect();
				clipboard.blockSetBegin(song.getActiveColumn(), song.songGetActiveTrack(), 0, tracks, song);
				clipboard.blockSetEnd(song.getSmallestMaxtracklen(song.songGetActiveLine(), tracks4_8) - 1, tracks);
			}
			break;

		case VK_B: // restore block from backup
			if (isBlockSelected() && control && !shift) {
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 1);
				clipboard.blockRestoreFromBackup(tracks);
			}
			break;

		case VK_C:
			if (control && !shift) {
				makeBlockAtCursorIfNone();
				clipboard.blockCopyToClipboard(tracks);
			}
			break;

		case VK_E:
			if (control && !shift) { // exchange block and clipboard
				if (isBlockSelected()) {
					session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 1);
					if (clipboard.blockExchangeClipboard(tracks) == 0) {
						session.undo.dropLast();
					}
				}
			}
			break;

		case VK_M:
			if (control && !shift) {
				blockDeselect();
				song.blockPaste(1, clipboard, tracks, session.undo, tracks4_8); // paste merge
			}
			break;

		case VK_V:
			if (control && !shift) {
				blockDeselect();
				song.blockPaste(0, clipboard, tracks, session.undo, tracks4_8); // classic paste
			}
			break;

		case VK_X:
			if (control && !shift) {
				makeBlockAtCursorIfNone();
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 1);
				clipboard.blockCopyToClipboard(tracks);
				clipboard.blockClear(tracks);
			}
			break;

		case VK_F:
			if (control && !shift && isBlockSelected() && blockEffectAction != null) {
				blockEffectAction.run(); // g_Undo.ChangeTrack(...); if (!g_TrackClipboard.BlockEffect()) g_Undo.DropLast();
			}
			break;

		case VK_G: // song goto on/off
			blockDeselect();
			if (control && !shift) {
				song.songTrackGoOnOff(session.undo); // control+G => goto on/off line in the song
			}
			break;

		case VK_N:
			blockDeselect();
			if (control && !shift) {
				song.songPutnewemptyunusedtrack(session.undo, tracks4_8);
			}
			break;

		case VK_D:
			blockDeselect();
			if (control && !shift) {
				song.songMaketracksduplicate(session.undo, tracks4_8, session.messages);
			}
			break;

		case VK_HOME:
			if (control) {
				song.trackSetGo(session.undo);
			} else {
				if (shift) {
					blockSetBegin();
					song.setActiveLine(0); // line 0
					blockSetEnd();
				} else {
					if (isBlockSelected()) {
						// sets to the first line in the block
						song.setActiveLine(clipboard.getFromTo().from());
					} else {
						if (song.getActiveLine() != 0) {
							song.setActiveLine(0); // line 0
						} else {
							i = song.trackGetGoLine();
							if (i >= 0) {
								song.setActiveLine(i); // at the beginning of the GO loop
							}
						}
						blockDeselect();
					}
				}
			}
			break;

		case VK_END:
			if (control) {
				song.trackSetEnd(session.undo);
			} else {
				if (shift) {
					blockSetBegin();
					if (song.trackGetGoLine() >= 0) {
						song.setActiveLine(tracks.getMaxTrackLength() - 1); // last line
					} else {
						song.setActiveLine(song.trackGetLastLine()); // end line
					}
					blockSetEnd();
					if (song.getActiveLine() < 0) {
						song.setActiveLine(tracks.getMaxTrackLength() - 1); // failsafe in case the active line is out of bounds
						blockDeselect(); // prevents selecting invalid data
					}
				} else {
					if (isBlockSelected()) {
						// sets to the first line in the block
						song.setActiveLine(clipboard.getFromTo().to());
					} else {
						i = song.trackGetLastLine();
						if (i != song.getActiveLine()) {
							song.setActiveLine(i); // at the end of the GO loop or end line
							if (song.getActiveLine() < 0) {
								song.setActiveLine(tracks.getMaxTrackLength() - 1); // failsafe in case the active line is out of bounds
							}
						} else {
							song.setActiveLine(tracks.getMaxTrackLength() - 1); // last line
						}
						blockDeselect();
					}
				}
			}
			break;

		case VK_RETURN: { // FIXME: Channels are desynched when track End or Loops are detected, bad hack...
			if (shift && control) {
				blockDeselect();
				song.trackSetEnd(session.undo);
				break;
			}
			if (!shift && control != options.keyboardSwapEnter) { // control+Enter => plays a whole line (all tracks)
				// for all track columns except the active track column
				for (i = 0; i < song.getTracks(tracks4_8); i++) {
					if (i != song.getActiveColumn()) {
						playLineColumn(i, song.songGetTrack(song.songGetActiveLine(), i));
					}
				}
			}
			// and now for that active track column
			Song.NoteInstrVol niv = song.trackGetLoopingNoteInstrVol(song.songGetActiveTrack());
			if (niv.note() >= 0) { // is there a note?
				song.setPlayPressedTonesTNIV(song.getActiveColumn(), niv.note(), niv.instr(), niv.vol()); // it will lose it as it is there
				if (shift && !control) { // with the shift, this instrument and the volume will "pick up" as current (only if it is not 0)
					song.activeInstrSet(niv.instr(), options.keyboardRememberOctavesAndVolumes);
					if (niv.vol() > 0) {
						song.setVolume(niv.vol());
					}
				}
			} else if (niv.vol() >= 0) { // there is no note, but is there a separate volume?
				song.setPlayPressedTonesV(song.getActiveColumn(), niv.vol()); // adjust the volume
				if (shift && !control && niv.vol() > 0) {
					song.setVolume(niv.vol()); // "picks up" the volume as current (only if it is not 0)
				}
			}
			int oldline = song.getActiveLine(); // hack, force a line move even if TrackDown prevents it after Enter called it, otherwise the last line would get stuck
			if (trackDown(1, false) && oldline == song.getActiveLine()) {
				song.setActiveLine(song.getActiveLine() + 1);
			}
			if (isBlockSelected()) { // if a block is selected, it moves (and plays) only in it
				TrackClipboard.FromTo fromTo = clipboard.getFromTo();
				if (song.getActiveLine() < fromTo.from() || song.getActiveLine() > fromTo.to()) {
					song.setActiveLine(fromTo.from());
				}
			}
			break;
		}

		case VK_I:
			if (control && !shift) {
				insertLine();
			}
			break;

		case VK_U:
			if (control && !shift) {
				deleteLine();
			}
			break;

		case VK_INSERT:
			insertLine();
			break;

		case VK_DELETE:
			if (isBlockSelected()) {
				// the block is selected, so it deletes it
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 1);
				clipboard.blockClear(tracks);
			} else if (!shift) {
				deleteLine();
			}
			break;

		case VK_SPACE:
			if (control) {
				break; // fixes the "return to EDIT MODE space input" bug, by ignoring SPACE if CTRL is also detected
			}
			blockDeselect();
			if (song.trackDelNoteInstrVolSpeed(1 + 2 + 4 + 8, session.undo)) { // all
				if (!isPlayingAndFollowing()) {
					trackDown(skipLines(), true);
				}
			}
			break;

		case VK_BACK: {
			blockDeselect();
			boolean r = false;
			switch (song.getTrackActiveCur()) {
			case 0, 1 -> r = song.trackDelNoteInstrVolSpeed(1 + 2, session.undo); // delete note + instrument
			case 2 -> r = song.trackDelNoteInstrVolSpeed(1 + 2 + 4, session.undo); // delete note + instrument + volume
			case 3 -> r = song.trackSetSpeed(-1, session.undo); // delete speed
			default -> {
			}
			}
			if (r) {
				if (!isPlayingAndFollowing()) {
					trackDown(skipLines(), true);
				}
			}
			break;
		}

		case VK_F1:
			if (control) {
				makeBlockAtCursorIfNone();
				// transpose down by 1 semitone
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockNoteTransposition(song.getActiveInstr(), -1, tracks);
			}
			break;

		case VK_F2:
			if (control) {
				makeBlockAtCursorIfNone();
				// transpose up by 1 semitone
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockNoteTransposition(song.getActiveInstr(), 1, tracks);
			}
			break;

		case VK_F3:
			if (control) {
				makeBlockAtCursorIfNone();
				// transpose down by 1 octave
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockNoteTransposition(song.getActiveInstr(), -12, tracks);
			}
			break;

		case VK_F4:
			if (control) {
				makeBlockAtCursorIfNone();
				// transpose up by 1 octave
				session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				clipboard.blockNoteTransposition(song.getActiveInstr(), 12, tracks);
			}
			break;

		default:
			return false;
		}
		return true;
	}

	/** C++'s {@code insertnotes:} label: enters {@code note} (in the current octave) with the active instrument and volume, or quantizes it during follow-play. */
	private void insertNote(int note) {
		Song song = session.song;
		int i = note + song.getOctave() * 12;
		if (i >= 0 && i < Notes.NOTESNUM) { // only within limits
			blockDeselect();
			// Quantization
			if (isPlayingAndFollowing() && (song.getSpeeda() < (song.getSpeed() / 2))) {
				song.setQuantization(i, song.getActiveInstr(), song.getVolume());
				return;
			}
			// end Quantization
			if (song.trackSetNoteActualInstrVol(i, session.uiState.respectVolume, session.undo)) {
				song.setPlayPressedTonesTNIV(song.getActiveColumn(), i, song.getActiveInstr(), song.trackGetVol());
				if (!isPlayingAndFollowing()) {
					trackDown(skipLines(), true);
				}
			}
		}
	}

	/** C++'s {@code testnotevalue:} label (instrument column): a note key enters a note only where there is none yet. */
	private boolean testNoteValue(int note) {
		blockDeselect();
		if (session.song.trackGetNote() >= 0) {
			return false; // do not input a note if there is already a note!
		}
		if (note >= 0) {
			insertNote(note); // force a note insertion otherwise
		}
		return true;
	}

	/** C++'s {@code insertline:} label. */
	private void insertLine() {
		Song song = session.song;
		blockDeselect();
		session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 0);
		session.tracks.insertLine(song.songGetActiveTrack(), song.getActiveLine());
	}

	/** C++'s {@code deleteline:} label. */
	private void deleteLine() {
		Song song = session.song;
		blockDeselect();
		session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 0);
		session.tracks.deleteLine(song.songGetActiveTrack(), song.getActiveLine());
	}

	// --- CSong::SongKey ---

	/** {@code CSong::SongKey()} */
	boolean songKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		UiState ui = session.uiState;
		int tracks4_8 = session.tracks4_8;
		boolean isgo = song.songGetGo() >= 0;

		if (!control && numbKey(vk) >= 0) {
			song.songTrackSetByNum(numbKey(vk), session.undo);
			return true;
		}

		switch (vk) {
		case VK_UP:
			blockDeselect();
			songUp();
			break;

		case VK_DOWN:
			blockDeselect();
			songDown();
			break;

		case VK_LEFT:
			if (shift) {
				activeInstrPrev();
			} else if (control) {
				if (isgo) {
					song.songTrackGoDec();
				} else {
					song.songTrackDec(session.undo);
				}
			} else {
				trackLeft(true);
			}
			break;

		case VK_RIGHT:
			if (shift) {
				activeInstrNext();
			} else if (control) {
				if (isgo) {
					song.songTrackGoInc();
				} else {
					song.songTrackInc(session.undo);
				}
			} else {
				trackRight(true);
			}
			break;

		case VK_TAB:
			if (shift) {
				trackLeft(true); // SHIFT+TAB
			} else {
				trackRight(true);
			}
			break;

		case VK_U: // Control+VK_U:
			if (!control) {
				break;
			}
			// falls through
		case VK_DELETE:
			song.songDeleteLine(song.songGetActiveLine(), session.undo, tracks4_8);
			break;

		case VK_I: // Control+VK_I:
			if (!control) {
				break;
			}
			// falls through
		case VK_INSERT:
			song.songInsertLine(song.songGetActiveLine(), session.undo, tracks4_8);
			break;

		case VK_O: // Control+VK_O
			if (control && insertCopyOrCloneAction != null) {
				insertCopyOrCloneAction.run(); // SongInsertCopyOrCloneOfSongLines(m_songactiveline) - the "Insert copy or clone of song line(s)" dialog
			}
			break;

		case VK_P: // Control+VK_P
			if (control) {
				song.songPrepareNewLine(song.songGetActiveLine(), -1, true, session.undo, tracks4_8);
			}
			break;

		case VK_N: // Control+VK_N
			if (control) {
				song.songPutnewemptyunusedtrack(session.undo, tracks4_8);
			}
			break;

		case VK_D: // Control+VK_D
			blockDeselect();
			if (control) {
				song.songMaketracksduplicate(session.undo, tracks4_8, session.messages);
			}
			break;

		case VK_BACK:
			if (isgo) {
				song.songTrackGoOnOff(session.undo); // Go off
			} else {
				song.songTrackEmpty(session.undo);
			}
			break;

		case VK_G:
			if (control) {
				song.songTrackGoOnOff(session.undo); // Go on/off
			}
			break;

		case VK_RETURN:
			ui.activePart = ui.activeTi;
			break;

		case VK_HOME:
			song.songSetActiveLine(0);
			break;

		case VK_END:
			song.songSetActiveLine(lastUsedSongline());
			break;

		case VK_PRIOR:
			if (shift) {
				song.songSubsongPrev(session.undo);
			} else {
				songUp();
			}
			break;

		case VK_NEXT:
			if (shift) {
				song.songSubsongNext(session.undo);
			} else {
				songDown();
			}
			break;

		case VK_MULTIPLY:
			song.octaveUp();
			return true;

		case VK_DIVIDE:
			song.octaveDown();
			return true;

		case VK_ADD:
			song.volumeUp();
			return true;

		case VK_SUBTRACT:
			song.volumeDown();
			return true;

		default:
			return false;
		}
		return true;
	}
}
