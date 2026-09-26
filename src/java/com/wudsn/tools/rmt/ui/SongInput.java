package com.wudsn.tools.rmt.ui;

import java.awt.event.KeyEvent;

import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.TrackClipboard;
import com.wudsn.tools.rmt.model.Tracks;

/**
 * Keyboard input: the port of {@code CRmtView::OnKeyDown()}/{@code OnKeyUp()}
 * (RmtView.cpp) and of the per-part key handlers {@code CSong::TrackKey()}/
 * {@code SongKey()} (GUI_Song.cpp). Pure dispatch over already-ported
 * {@link Song} operations, so it lives in {@code ui} taking the
 * {@link RmtSession} - the deviation from C++ (which puts the handlers on
 * {@code CSong}) recorded in {@code plans/JAVA_UI_PORT_PLAN.md}.
 *
 * <p><b>Scope so far: cursor navigation only</b> (batch B1) - the plain and
 * Ctrl arms of Up/Down/Left/Right/PgUp/PgDn/Home/End/Tab/Esc in the tracks
 * part and the song part, the modifier-key tracking, and Enter in the song
 * part. Every arm that enters or changes data, selects blocks, changes the
 * active instrument/octave/volume, or belongs to {@code InfoKey}/
 * {@code InstrKey}/{@code ProveKey} is marked {@code // B3} and comes with
 * the full keyboard batch.
 *
 * <p>Key codes are {@link KeyEvent} {@code VK_*} constants. Most equal the
 * Windows virtual-key codes C++ switches on, but not all ({@code VK_ENTER}
 * is 0x0A here, {@code VK_RETURN} is 0x0D there), so the constants are
 * always used by name.
 */
public final class SongInput {

	private final RmtSession session;

	public SongInput(RmtSession session) {
		this.session = session;
	}

	/** {@code CRmtView::OnKeyDown()}: tracks Shift/Ctrl/Alt, then dispatches on the active part. */
	public void keyDown(int keyCode) {
		UiState ui = session.uiState;
		ui.lastKeyPressed = keyCode;

		switch (keyCode) {
		case KeyEvent.VK_SHIFT -> ui.shiftKey = true;
		case KeyEvent.VK_CONTROL -> ui.controlKey = true;
		case KeyEvent.VK_ALT -> ui.altKey = true;
		default -> {
			switch (ui.activePart) {
			case PART_TRACKS -> trackKey(keyCode, ui.shiftKey, ui.controlKey);
			case PART_SONG -> songKey(keyCode, ui.shiftKey, ui.controlKey);
			default -> {
				// B3: PART_INFO -> InfoKey/ProveKey, PART_INSTRUMENTS -> InstrKey/ProveKey (and the prove-mode redirections of the two parts above)
			}
			}
		}
		}
	}

	/** {@code CRmtView::OnKeyUp()}. */
	public void keyUp(int keyCode) {
		UiState ui = session.uiState;
		switch (keyCode) {
		case KeyEvent.VK_SHIFT -> ui.shiftKey = false;
		case KeyEvent.VK_CONTROL -> ui.controlKey = false;
		case KeyEvent.VK_ALT -> ui.altKey = false;
		default -> {
		}
		}
	}

	private boolean isPlayingAndFollowing() {
		return session.song.getPlayMode() != PlayMode.PLAY_STOP && session.song.getFollowPlayMode();
	}

	/** {@code CSong::TrackKey()} - navigation arms. */
	void trackKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		Tracks tracks = session.tracks;
		TrackClipboard clipboard = session.clipboard;
		RmtOptions options = session.options;
		int tracks4_8 = session.tracks4_8;
		int step = options.skipLinesAfterNoteInsert == 0 ? 1 : options.skipLinesAfterNoteInsert;

		switch (vk) {
		case KeyEvent.VK_UP:
			if (control && shift) {
				// B3: block volume change incrementing
			} else if (shift && !control) {
				// B3: block selection upward
			} else if (control && !shift) {
				if (clipboard.isBlockSelected()) {
					song.blockDeselect(clipboard);
					break;
				}
				song.songUp(session.undo, clipboard);
			} else {
				song.blockDeselect(clipboard);
				song.trackUp(step, tracks4_8, options.keyboardUpDownContinue, session.undo, clipboard);
			}
			break;

		case KeyEvent.VK_DOWN:
			if (control && shift) {
				// B3: block volume change decrementing
			} else if (shift && !control) {
				// B3: block selection downward
			} else if (control && !shift) {
				if (clipboard.isBlockSelected()) {
					song.blockDeselect(clipboard);
					break;
				}
				song.songDown(session.undo, clipboard);
			} else {
				song.blockDeselect(clipboard);
				song.trackDown(step, false, tracks4_8, options.keyboardUpDownContinue, session.undo, clipboard); // will not stop on the last line
			}
			break;

		case KeyEvent.VK_LEFT:
			if (control && shift) {
				// B3: block instrument change decrementing
			} else if (shift && !control) {
				// B3: ActiveInstrPrev
			} else if (control && !shift) {
				// B3: SongTrackDec (deselects a block instead when one is selected)
			} else {
				song.blockDeselect(clipboard);
				song.trackLeft(false, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_RIGHT:
			if (control && shift) {
				// B3: block instrument change incrementing
			} else if (shift && !control) {
				// B3: ActiveInstrNext
			} else if (control && !shift) {
				// B3: SongTrackInc (deselects a block instead when one is selected)
			} else {
				song.blockDeselect(clipboard);
				song.trackRight(false, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_PAGE_UP:
			if (!shift && control) {
				song.blockDeselect(clipboard);
				song.songUp(session.undo, clipboard);
			} else if (!control && shift) {
				// move to the previous goto
				song.blockDeselect(clipboard);
				song.songSubsongPrev(session.undo);
			} else if (isPlayingAndFollowing()) {
				break; // prevents moving at all during play+follow
			} else {
				song.blockDeselect(clipboard);
				int line = song.getActiveLine();
				if (line > 0) {
					song.setActiveLine(((line - 1) / options.trackLinePrimaryHighlight) * options.trackLinePrimaryHighlight);
				}
			}
			break;

		case KeyEvent.VK_PAGE_DOWN:
			if (!shift && control) {
				song.blockDeselect(clipboard);
				song.songDown(session.undo, clipboard);
			} else if (!control && shift) {
				// move to the next goto
				song.blockDeselect(clipboard);
				song.songSubsongNext(session.undo);
			} else if (isPlayingAndFollowing()) {
				break; // prevents moving at all during play+follow
			} else {
				song.blockDeselect(clipboard);
				int line = ((song.getActiveLine() + options.trackLinePrimaryHighlight) / options.trackLinePrimaryHighlight) * options.trackLinePrimaryHighlight;
				if (line > song.getSmallestMaxtracklen(song.songGetActiveLine(), tracks4_8) - 1) {
					line -= options.trackLinePrimaryHighlight;
				}
				song.setActiveLine(line);
			}
			break;

		case KeyEvent.VK_TAB:
			song.blockDeselect(clipboard);
			if (shift) {
				song.trackLeft(true, tracks4_8, session.undo); // Shift+TAB
			} else if (control) {
				// B3: CursorToSpeedColumn (Ctrl+TAB)
			} else {
				song.trackRight(true, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_ESCAPE:
			song.blockDeselect(clipboard);
			break;

		case KeyEvent.VK_HOME:
			if (control) {
				// B3: TrackSetGo
			} else if (shift) {
				// B3: block selection to line 0
			} else {
				if (clipboard.isBlockSelected()) {
					// sets to the first line in the block
					song.setActiveLine(clipboard.getFromTo().from());
				} else {
					if (song.getActiveLine() != 0) {
						song.setActiveLine(0); // line 0
					} else {
						int i = tracks.getGoLine(song.songGetActiveTrack());
						if (i >= 0) {
							song.setActiveLine(i); // at the beginning of the GO loop
						}
					}
					song.blockDeselect(clipboard);
				}
			}
			break;

		case KeyEvent.VK_END:
			if (control) {
				// B3: TrackSetEnd
			} else if (shift) {
				// B3: block selection to the last line
			} else {
				if (clipboard.isBlockSelected()) {
					// sets to the last line in the block
					song.setActiveLine(clipboard.getFromTo().to());
				} else {
					int i = song.trackGetLastLine();
					if (i != song.getActiveLine()) {
						song.setActiveLine(i); // at the end of the GO loop or end line
						if (song.getActiveLine() < 0) {
							song.setActiveLine(tracks.getMaxTrackLength() - 1); // failsafe in case the active line is out of bounds
						}
					} else {
						song.setActiveLine(tracks.getMaxTrackLength() - 1); // last line
					}
					song.blockDeselect(clipboard);
				}
			}
			break;

		default:
			// B3: note/instrument/volume/speed entry, block commands, Enter (play line), Insert/Delete/Backspace, +/-/*//, letters with Ctrl
			break;
		}
	}

	/** {@code CSong::SongKey()} - navigation arms. */
	void songKey(int vk, boolean shift, boolean control) {
		Song song = session.song;
		TrackClipboard clipboard = session.clipboard;
		UiState ui = session.uiState;
		int tracks4_8 = session.tracks4_8;

		// B3: a digit key without Ctrl sets the track number (SongTrackSetByNum)

		switch (vk) {
		case KeyEvent.VK_UP:
			song.blockDeselect(clipboard);
			song.songUp(session.undo, clipboard);
			break;

		case KeyEvent.VK_DOWN:
			song.blockDeselect(clipboard);
			song.songDown(session.undo, clipboard);
			break;

		case KeyEvent.VK_LEFT:
			if (shift) {
				// B3: ActiveInstrPrev
			} else if (control) {
				// B3: SongTrackGoDec / SongTrackDec
			} else {
				song.trackLeft(true, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_RIGHT:
			if (shift) {
				// B3: ActiveInstrNext
			} else if (control) {
				// B3: SongTrackGoInc / SongTrackInc
			} else {
				song.trackRight(true, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_TAB:
			if (shift) {
				song.trackLeft(true, tracks4_8, session.undo); // SHIFT+TAB
			} else {
				song.trackRight(true, tracks4_8, session.undo);
			}
			break;

		case KeyEvent.VK_ENTER:
			ui.activePart = ui.activeTi;
			break;

		case KeyEvent.VK_HOME:
			song.songSetActiveLine(0);
			break;

		case KeyEvent.VK_END: {
			int la = 0;
			int[][] songLines = song.getSong();
			int[] songGo = song.getSongGo();
			for (int j = 0; j < Song.SONGLEN; j++) {
				for (int i = 0; i < tracks4_8; i++) {
					if (songLines[j][i] >= 0) {
						la = j;
						break;
					}
				}
				if (songGo[j] >= 0) {
					la = j;
				}
			}
			song.songSetActiveLine(la);
			break;
		}

		case KeyEvent.VK_PAGE_UP:
			if (shift) {
				song.songSubsongPrev(session.undo);
			} else {
				song.songUp(session.undo, clipboard);
			}
			break;

		case KeyEvent.VK_PAGE_DOWN:
			if (shift) {
				song.songSubsongNext(session.undo);
			} else {
				song.songDown(session.undo, clipboard);
			}
			break;

		default:
			// B3: Insert/Delete/Backspace, Ctrl+U/I/O/P/N/D/G, +/-/*//
			break;
		}
	}
}
