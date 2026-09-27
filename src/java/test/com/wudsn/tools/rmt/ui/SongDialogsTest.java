package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.Messages;
import com.wudsn.tools.rmt.model.Song;

/** The editing-dialog flows, headless: the dialogs are the scripted stub, the message boxes a recorder. */
class SongDialogsTest {

	private static final class RecordingMessages implements Messages.Handler {
		final List<String> log = new ArrayList<>();
		MessageAnswer answer = MessageAnswer.YES;

		@Override
		public void showError(String title, String message) {
			log.add("E:" + title + ":" + message);
		}

		@Override
		public void showWarning(String title, String message) {
			log.add("W:" + title);
		}

		@Override
		public void showInformation(String title, String message) {
			log.add("I:" + title + ":" + message);
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			log.add("Q:" + title);
			return answer;
		}
	}

	private RmtSession session;
	private StubSongDialogsHost host;
	private RecordingMessages messages;
	private SongDialogs dialogs;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		host = new StubSongDialogsHost();
		messages = new RecordingMessages();
		session.messages.setHandler(messages);
		dialogs = new SongDialogs(session, host);
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(SongFilesTest.DELTA, false));
	}

	@Test
	void insertCopyOrCloneOffersTheLineAboveAndInsertsAtTheActiveLine() {
		session.song.songSetActiveLine(2);
		int[] line1 = session.song.getSong()[1].clone();
		host.nextInsertCopy = new SongDialogs.InsertCopyChoice(1, 1, false, 0, 100);
		dialogs.insertCopyOrCloneOfSongLines();
		assertEquals(List.of("insertCopy:1:1:2"), host.calls);
		assertEquals(2, session.song.songGetActiveLine());
		for (int col = 0; col < 4; col++) {
			assertEquals(line1[col], session.song.getSong()[2][col]); // the copy sits on line 2 now
		}
		assertTrue(messages.log.isEmpty(), messages.log.toString());

		host.nextInsertCopy = null; // cancelled: nothing but the active line restored
		dialogs.insertCopyOrCloneOfSongLines();
		assertEquals(2, session.song.songGetActiveLine());
	}

	@Test
	void instrInfoTextMatchesTheCppBox() {
		String text = dialogs.instrInfoText(0);
		assertTrue(text.startsWith("Instrument: 00\nName: "), text);
		assertTrue(text.contains("\nUsed in "), text);
		assertTrue(text.contains("\n\nNote listing:\n"), text);
		assertTrue(text.contains("\n\nTrack listing:\n"), text);
		assertNull(dialogs.instrInfoText(999));
		dialogs.instrInfo();
		assertEquals("I:Instrument info:" + dialogs.instrInfoText(session.song.getActiveInstr()), messages.log.get(0));
	}

	@Test
	void instrChangeRunsTheDialogWithTheActiveContextAndReportsTheChanges() {
		session.song.songSetActiveLine(1);
		Song.InstrChangeParams p = new Song.InstrChangeParams();
		p.sinstrfrom = 0;
		p.sinstrto = 0;
		p.snotefrom = 0;
		p.snoteto = 60;
		p.svolmin = 0;
		p.svolmax = 15;
		p.dinstrfrom = 5;
		p.dinstrto = 5;
		p.dnotefrom = 0;
		p.dnoteto = 60;
		p.dvolmin = 0;
		p.dvolmax = 15;
		p.onlytrack = -1;
		p.onlychannels = -1;
		p.onlysonglinefrom = -1;
		p.onlysonglineto = -1;
		host.nextInstrChange = p;
		dialogs.instrChange();
		assertEquals(List.of("instrChange:0:" + session.song.getSong()[1][0] + ":1"), host.calls);
		assertEquals(1, messages.log.size());
		assertTrue(messages.log.get(0).startsWith("I:Instrument changes:"), messages.log.get(0));
		assertTrue(session.undo.getUndoSteps() > 0);

		host.nextInstrChange = null;
		dialogs.instrChange();
		assertEquals(1, messages.log.size());
	}

	@Test
	void tracksOrderValidatesTheRangeAndAsksBeforeClearingColumns() {
		int[] line0 = session.song.getSong()[0].clone();
		host.nextTracksOrder = new SongDialogs.TracksOrderChoice("00", "ZZ", new int[] { 0, 1, 2, 3, 4, 5, 6, 7 });
		dialogs.tracksOrderChange();
		assertEquals(List.of("tracksOrder:00:FF"), host.calls); // the remembered defaults 00..FF offered
		assertEquals("E:Error:Bad songline (from-to) range.", messages.log.get(0));

		host.nextTracksOrder = new SongDialogs.TracksOrderChoice("00", "05", new int[] { 1, 0, -1, 3, 4, 5, 6, 7 });
		messages.answer = MessageAnswer.NO;
		dialogs.tracksOrderChange();
		assertEquals("Q:Warning", messages.log.get(1)); // one column will be cleared
		assertEquals(line0[0], session.song.getSong()[0][0]); // declined: nothing changed
		assertEquals(5, session.song.getTracksOrderChangeSonglineto()); // but the range is remembered

		messages.answer = MessageAnswer.YES;
		dialogs.tracksOrderChange();
		assertEquals(line0[1], session.song.getSong()[0][0]); // columns 0 and 1 swapped
		assertEquals(line0[0], session.song.getSong()[0][1]);
		assertEquals(-1, session.song.getSong()[0][2]); // column 2 cleared
	}

	@Test
	void changeMaxTrackLengthOffersTheEffectiveValueAndApplies() {
		int effective = session.song.getEffectiveMaxtracklen(session.tracks4_8);
		host.nextMaxTrackLength = -1;
		dialogs.changeMaxTrackLength();
		assertEquals("maxTrackLength:" + effective + ":Current value: " + session.tracks.getMaxTrackLength() + "|Computed effective value for current song: " + effective, host.calls.get(0));
		host.nextMaxTrackLength = 32;
		dialogs.changeMaxTrackLength();
		assertEquals(32, session.tracks.getMaxTrackLength());
		assertTrue(session.undo.getUndoSteps() > 0);
	}

	@Test
	void renumberDialogsFeedTheirRadioIntoTheModel() {
		int[] line0 = session.song.getSong()[0].clone();
		host.nextRenumberTracks = 0;
		dialogs.renumberAllTracks();
		assertEquals(line0[0], session.song.getSong()[0][0]);
		host.nextRenumberTracks = 1;
		dialogs.renumberAllTracks();
		assertEquals(0, session.song.getSong()[0][0]); // by columns first: the first used track becomes 0

		host.nextRenumberInstruments = 1;
		dialogs.renumberAllInstruments();
		assertEquals(List.of("renumberTracks", "renumberTracks", "renumberInstruments"), host.calls);
	}

	@Test
	void monoStereoSwitchGoesThroughTheModelsQuestionAndTellsTheHost() {
		assertEquals(4, session.tracks4_8);
		messages.answer = MessageAnswer.CANCEL;
		dialogs.songswitch4_8();
		assertEquals(4, session.tracks4_8);
		assertEquals(0, host.layoutChangedCount);
		messages.answer = MessageAnswer.YES;
		dialogs.songswitch4_8();
		assertEquals(8, session.tracks4_8);
		assertEquals(1, host.layoutChangedCount);
		assertFalse(messages.log.isEmpty());
	}
}
