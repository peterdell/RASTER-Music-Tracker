package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.Notes;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.Track;
import com.wudsn.tools.rmt.model.Tracks;
import com.wudsn.tools.rmt.model.UndoType;

/**
 * The editing commands that open a dialog before calling the model - the
 * port of {@code CSong::SongInsertCopyOrCloneOfSongLines()},
 * {@code CSong::InstrChange()}, {@code CSong::TracksOrderChange()},
 * {@code CSong::InstrInfo()}'s message branch, and the {@code CRmtView}
 * handlers for "Change maximal length of tracks", "Renumber all tracks",
 * "Renumber all instruments" and the mono/stereo switch. As with
 * {@link SongFiles}, the dialogs stay behind {@link Host} so the flows
 * (undo bookkeeping, validation, confirmations, the model calls) are
 * tested headless with a stub.
 */
public final class SongDialogs {

	/** {@code CInsertCopyOrCloneOfSongLinesDlg}'s result. */
	public record InsertCopyChoice(int lineFrom, int lineTo, boolean cloneTracks, int tuning, int volumePercent) {
	}

	/** {@code CSongTracksOrderDlg}'s result: the two hex fields as typed (parsed by the caller, as C++ does) and the order (a negative entry clears that column). */
	public record TracksOrderChoice(String songLineFrom, String songLineTo, int[] tracksOrder) {
	}

	/** The window services the flows need. */
	public interface Host {
		/** {@code CInsertCopyOrCloneOfSongLinesDlg}: {@code null} if cancelled. */
		InsertCopyChoice showInsertCopyOrClone(int lineFrom, int lineTo, int lineInto);

		/** {@code CInstrumentChangeDlg}: {@code null} if cancelled. */
		Song.InstrChangeParams showInstrumentChange(int instr, int onlyTrack, int onlySongLine);

		/** {@code CSongTracksOrderDlg}: {@code null} if cancelled. */
		TracksOrderChoice showTracksOrder(String songLineFrom, String songLineTo);

		/** {@code CChangeMaxtracklenDlg}: the new length, or -1 if cancelled. */
		int showChangeMaxTrackLength(String info, int maxTrackLength);

		/** {@code CRenumberTracksDlg}: 1 = by song columns first, 2 = by song lines first, 0 if cancelled. */
		int showRenumberTracks();

		/** {@code CRenumberInstrumentsDlg}: 1 = remove gaps, 2 = by use in tracks, 3 = by name, 0 if cancelled. */
		int showRenumberInstruments();

		/** {@code OnGetMinMaxInfo}'s input changed (the mono/stereo switch). */
		void songLayoutChanged();
	}

	private final RmtSession session;
	private final Host host;

	public SongDialogs(RmtSession session, Host host) {
		this.session = session;
		this.host = host;
	}

	private void stop() {
		session.song.stop(session.undo);
	}

	/** {@code OnSongInsertcopyorcloneofsonglines()} + {@code CSong::SongInsertCopyOrCloneOfSongLines(line)}: the dialog defaults to copying the line above into the active line. */
	public void insertCopyOrCloneOfSongLines() {
		Song song = session.song;
		int line = song.songGetActiveLine();
		int n = (line > 0) ? line - 1 : 0;
		InsertCopyChoice dlg = host.showInsertCopyOrClone(n, n, line);
		if (dlg != null) {
			if (!song.songInsertCopyOrCloneOfSongLinesApply(line, dlg.lineFrom(), dlg.lineTo(), dlg.cloneTracks(), dlg.tuning(), dlg.volumePercent(), session.undo, session.tracks4_8, session.clipboard)) {
				// C++ names the exact guard (song-range overrun / no empty track left to clone into) - the model's boolean can't
				session.messages.sendErrorMessage("Warning", "The copy or clone could not be completed: the song line range overruns the song, or there is no empty track left to clone into.");
			}
		}
		song.songSetActiveLine(line);
	}

	/** {@code OnInstrumentInfo()} + {@code CSong::InstrInfo(instr)}'s message branch: usage statistics with the note and track listings. */
	public void instrInfo() {
		session.messages.sendInformationMessage("Instrument info", instrInfoText(session.song.getActiveInstr()));
	}

	/** The text of {@code InstrInfo}'s box (the {@code iinfo == NULL} branch), or {@code null} for an invalid instrument. */
	String instrInfoText(int instr) {
		if (!session.instruments.isValidInstrument(instr)) {
			return null;
		}
		Song.InstrInfo info = new Song.InstrInfo();
		session.song.instrInfo(info, instr);
		// the two listings the model's InstrInfo doesn't keep: which notes and which tracks
		int[] withnote = new int[Notes.NOTESNUM];
		boolean[] intrack = new boolean[Tracks.TRACKSNUM];
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			Track at = session.tracks.getTrack(i);
			int ain = -1;
			for (int j = 0; j < at.len; j++) {
				if (at.instr[j] >= 0) {
					ain = at.instr[j];
				}
				if (ain == instr) {
					intrack[i] = true;
					int note = at.note[j];
					if (note >= 0 && note < Notes.NOTESNUM) {
						withnote[note]++;
					}
				}
			}
		}
		StringBuilder s = new StringBuilder(String.format("Instrument: %02X\nName: %s\nUsed in %d tracks, globally %d times.\nFrom note: %s\nTo note: %s\nMin volume: %X\nMax volume: %X", instr, Song.nameToString(session.instruments.getName(instr)), info.usedInTracks, info.count,
				info.minNote < Notes.NOTESNUM ? Notes.getNote(info.minNote) : "-", info.maxNote >= 0 ? Notes.getNote(info.maxNote) : "-", info.minVol <= 15 ? info.minVol : 0, info.maxVol >= 0 ? info.maxVol : 0));
		if (info.count > 0) {
			s.append("\n\nNote listing:\n");
			int lc = 0;
			for (int i = 0; i < Notes.NOTESNUM; i++) {
				if (withnote[i] != 0) {
					s.append(Notes.getNote(i));
					lc++;
					if (lc < 12) {
						s.append(' ');
					} else {
						s.append('\n');
						lc = 0;
					}
				}
			}
			s.append("\n\nTrack listing:\n");
			lc = 0;
			for (int i = 0; i < Tracks.TRACKSNUM; i++) {
				if (intrack[i]) {
					s.append(String.format("%02X", i));
					lc++;
					if (lc < 16) {
						s.append(' ');
					} else {
						s.append('\n');
						lc = 0;
					}
				}
			}
		}
		return s.toString();
	}

	/** {@code OnInstrumentChange()} + {@code CSong::InstrChange(instr)}: the dialog, then {@code InstrChangeApply} and its "Instrument changes" summary box. */
	public void instrChange() {
		Song song = session.song;
		int instr = song.getActiveInstr();
		if (!session.instruments.isValidInstrument(instr)) {
			return;
		}
		Song.InstrChangeParams p = host.showInstrumentChange(instr, song.songGetActiveTrack(), song.songGetActiveLine());
		if (p == null) {
			return; // Change all the instrument occurences
		}
		String result = song.instrChangeApply(p, session.undo, session.tracks4_8);
		session.messages.sendInformationMessage("Instrument changes", result);
	}

	/** {@code OnSongTracksorderchange()} + {@code CSong::TracksOrderChange()}. */
	public void tracksOrderChange() {
		Song song = session.song;
		stop();
		TracksOrderChoice dlg = host.showTracksOrder(String.format("%02X", song.getTracksOrderChangeSonglinefrom()), String.format("%02X", song.getTracksOrderChangeSonglineto()));
		if (dlg == null) {
			return;
		}
		session.undo.changeSong(song.songGetActiveLine(), song.getActiveColumn(), UndoType.UETYPE_SONGDATA, 1);

		int f = Song.hexstr(dlg.songLineFrom(), 0, 2);
		int t = Song.hexstr(dlg.songLineTo(), 0, 2);
		if (f < 0 || f >= Song.SONGLEN || t < 0 || t >= Song.SONGLEN || t < f) {
			session.messages.sendErrorMessage("Error", "Bad songline (from-to) range.");
			return;
		}
		song.setTracksOrderChangeSonglineRange(f, t);

		int c = 0;
		for (int i = 0; i < session.tracks4_8; i++) {
			if (dlg.tracksOrder()[i] < 0) {
				c++;
			}
		}
		if (c > 0) {
			if (session.messages.sendQuestionMessage("Warning", "Warning: " + c + " song column(s) will be cleared completely.\nAre you sure to do it?", MessageButtons.YES_NO_CANCEL) != MessageAnswer.YES) {
				return;
			}
		}
		song.tracksOrderChangeApply(f, t, dlg.tracksOrder(), session.tracks4_8);
	}

	/** {@code OnSongSongswitch4_8()}: the model asks its own question; a switch re-initializes the Atari ({@code g_Atari.Init()}) and the window's minimum size. */
	public void songswitch4_8() {
		stop();
		int before = session.tracks4_8;
		session.tracks4_8 = session.song.songswitch4_8(before, before <= 4 ? 8 : 4, session.undo, session.messages);
		if (session.tracks4_8 != before) {
			session.initTuning(); // g_Atari.Init(g_Song.IsNTSC())
			host.songLayoutChanged();
		}
	}

	/** {@code OnSongSongchangemaximallengthoftracks()}. */
	public void changeMaxTrackLength() {
		stop();
		int ma = session.song.getEffectiveMaxtracklen(session.tracks4_8);
		String info = "Current value: " + session.tracks.getMaxTrackLength() + "\nComputed effective value for current song: " + ma;
		int chosen = host.showChangeMaxTrackLength(info, ma);
		if (chosen >= 0) {
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL);
			session.song.changeMaxtracklen(chosen);
		}
	}

	/** {@code OnTrackRenumberalltracks()}. */
	public void renumberAllTracks() {
		int type = host.showRenumberTracks();
		if (type != 0) {
			stop();
			// Hide the tracks and song
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, -1);
			session.undo.changeSong(0, 0, UndoType.UETYPE_SONGDATA, 1);
			session.song.renumberAllTracks(type, session.tracks4_8); // Type = 1 -> Order by songcolumns, Type = 2 -> Order by songlines
		}
	}

	/** {@code OnInstrumentRenumberallinstruments()}. */
	public void renumberAllInstruments() {
		stop();
		int type = host.showRenumberInstruments();
		if (type != 0) {
			// hide instruments and tracks
			session.undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRSALL, -1);
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, 1);
			session.song.renumberAllInstruments(type); // type=1...remove gaps, 2=order by using in tracks, type=3...order by instrument names
		}
	}

	/** {@code Instruments.INSTRSNUM} for the dialogs' combo boxes. */
	static int instrumentCount() {
		return Instruments.INSTRSNUM;
	}
}
