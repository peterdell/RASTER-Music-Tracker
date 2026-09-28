package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.MessageButtons;
import org.atari.raster.rmt.model.Messages;
import org.atari.raster.rmt.model.SongIOType;

/** The file flows of IO_Song.cpp, headless: the dialogs are a scripted stub, the message boxes a recorder. */
class SongFilesTest {

	static final Path DELTA = ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt");

	private static final class RecordingMessages implements Messages.Handler {
		final List<String> log = new ArrayList<>();
		MessageAnswer answer = MessageAnswer.CANCEL;

		@Override
		public void showError(String title, String message) {
			log.add("E:" + title);
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

	@TempDir
	Path dir;
	private RmtSession session;
	private StubSongFilesHost host;
	private RecordingMessages messages;
	private SongFiles files;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		host = new StubSongFilesHost();
		messages = new RecordingMessages();
		session.messages.setHandler(messages);
		files = new SongFiles(session, host);
	}

	/** A note on line 0 of the active track (which needs a track in song line 0 first on an empty song). */
	private void enterANote() {
		if (session.song.getSong()[0][0] < 0) {
			session.song.getSong()[0][0] = 0;
		}
		new SongInput(session).keyDown(VirtualKey.VK_Z);
	}

	@Test
	void editingSetsTheChangesFlagThroughTheUndoListener() {
		assertFalse(session.uiState.changes);
		enterANote();
		assertTrue(session.uiState.changes);
	}

	@Test
	void openByFileNameFollowsTheExtensionAndTheDialogTheFilterIndex() {
		assertTrue(files.fileOpen(DELTA, false));
		assertEquals(4, session.tracks4_8);
		assertEquals(SongIOType.RMT, session.song.getIOType());
		assertEquals(DELTA.toString(), session.song.getFilename());
		assertEquals(DELTA.toAbsolutePath().getParent().toString(), session.options.lastSongsPath);
		assertEquals(1, host.songChangedCount);
		assertTrue(host.calls.isEmpty());

		// through the dialog: the last folder and the current format preselected
		host.answer(DELTA, SongFiles.FILTER_RMT);
		assertTrue(files.fileOpen(null, false));
		assertEquals(List.of("open:Load song file"), host.calls);
		assertEquals(session.options.lastSongsPath, host.lastInitialDir);
		assertEquals(SongFiles.FILTER_RMT, host.lastInitialFilterIndex);

		host.nextChoice = null; // cancelled
		assertFalse(files.fileOpen(null, false));
	}

	@Test
	void openFailuresReportAndLeaveAnEmptySong() throws IOException {
		Path missing = dir.resolve("missing.rmt");
		assertFalse(files.fileOpen(missing, false));
		assertEquals(List.of("E:Open error"), messages.log);

		Path junk = dir.resolve("junk.rmt");
		Files.write(junk, new byte[] { 1, 2, 3 });
		assertFalse(files.fileOpen(junk, false));
		assertEquals("", session.song.getFilename());
		assertEquals(8, session.tracks4_8);

		assertFalse(files.fileOpen(dir.resolve("song.xyz"), false)); // no filter for the extension
	}

	@Test
	void openWarnsAboutUnsavedChangesFirst() {
		enterANote();
		messages.answer = MessageAnswer.CANCEL;
		assertFalse(files.fileOpen(DELTA, true));
		assertEquals(List.of("Q:Current song has been changed"), messages.log);
		assertTrue(session.uiState.changes);

		messages.answer = MessageAnswer.NO;
		assertTrue(files.fileOpen(DELTA, true));
		assertFalse(session.uiState.changes); // ClearSong
	}

	@Test
	void saveWithoutANameGoesThroughSaveAsAndTheThreeFormatsRoundTrip() throws IOException {
		assertTrue(files.fileOpen(DELTA, false));

		for (int filter = SongFiles.FILTER_RMT; filter <= SongFiles.FILTER_RMW; filter++) {
			session.song.setLoadedFile("", SongIOType.NONE);
			host.answer(dir.resolve("copy"), filter);
			files.fileSave();
			assertEquals("save:Save song as...", host.calls.get(host.calls.size() - 1));
			String ext = SongFiles.SONG_FILTERS.get(filter - 1).extensions().get(0);
			Path saved = dir.resolve("copy" + ext);
			assertTrue(Files.exists(saved), saved.toString());
			assertEquals(saved.toString(), session.song.getFilename());
			assertFalse(session.uiState.changes);

			RmtSession reread = new RmtSession();
			reread.messages.setHandler(new RecordingMessages());
			assertTrue(new SongFiles(reread, new StubSongFilesHost()).fileOpen(saved, false));
			assertEquals(4, reread.tracks4_8);
			assertEquals(session.song.getName(), reread.song.getName());
			for (int t = 0; t < 8; t++) {
				for (int line = 0; line < 64; line++) {
					assertEquals(session.tracks.getNote(t, line), reread.tracks.getNote(t, line), "track " + t + " line " + line + " via " + ext);
				}
			}
			// saving what was just read back reproduces the file byte for byte
			Path again = dir.resolve("again" + ext);
			reread.song.setLoadedFile(again.toString(), reread.song.getIOType());
			new SongFiles(reread, new StubSongFilesHost()).fileSave();
			assertArrayEquals(Files.readAllBytes(saved), Files.readAllBytes(again), "re-save via " + ext);
		}
		assertTrue(messages.log.isEmpty(), messages.log.toString());
	}

	@Test
	void savingAnEmptySongAsRmtIsRefusedAndTheFileNotCreated() {
		host.answer(dir.resolve("empty"), SongFiles.FILTER_RMT);
		files.fileSaveAs();
		assertEquals(List.of("E:Errors", "W:Warning"), messages.log); // "Song is empty." then "No data has been saved!"
		assertFalse(Files.exists(dir.resolve("empty.rmt")));
	}

	@Test
	void savingASongWithoutAFinalGotoGetsOneAfterAnInformationBox() {
		for (int col = 0; col < 8; col++) {
			session.song.getSong()[0][col] = col;
		}
		enterANote();
		host.answer(dir.resolve("noend"), SongFiles.FILTER_RMT);
		files.fileSaveAs();
		assertEquals(1, messages.log.size());
		assertTrue(messages.log.get(0).startsWith("I:Warning:Song line[01]: Unexpected end of song."));
		assertEquals(0, session.song.getSongGo()[1]);
		assertTrue(Files.exists(dir.resolve("noend.rmt")));
		assertFalse(session.uiState.changes);
	}

	@Test
	void warnUnsavedChangesSavesOnYesAndReportsCancel() {
		assertFalse(files.warnUnsavedChanges()); // nothing changed
		assertTrue(files.fileOpen(DELTA, false));
		enterANote();
		messages.answer = MessageAnswer.CANCEL;
		assertTrue(files.warnUnsavedChanges());
		messages.answer = MessageAnswer.YES;
		session.song.setLoadedFile(dir.resolve("saved.rmt").toString(), SongIOType.RMT);
		assertFalse(files.warnUnsavedChanges());
		assertFalse(session.uiState.changes);
		assertTrue(Files.exists(dir.resolve("saved.rmt")));
	}

	@Test
	void reloadAsksAndReopensTheSameFile() {
		assertFalse(files.fileCanBeReloaded());
		files.fileReload();
		assertTrue(messages.log.isEmpty());
		assertTrue(files.fileOpen(DELTA, false));
		enterANote();
		messages.answer = MessageAnswer.YES;
		files.fileReload();
		assertEquals(List.of("Q:Reload"), messages.log);
		assertFalse(session.uiState.changes);
		assertEquals(0, session.undo.getUndoSteps()); // ClearSong's Undo init: the edit is gone
	}

	@Test
	void fileNewBuildsTheOneLineSong() {
		enterANote();
		host.nextFileNew = new SongFiles.FileNewChoice(32, false);
		messages.answer = MessageAnswer.NO; // don't save the changes
		files.fileNew();
		assertEquals(4, session.tracks4_8);
		assertEquals(32, session.tracks.getMaxTrackLength());
		for (int i = 0; i < 4; i++) {
			assertEquals(i, session.song.getSong()[0][i]);
		}
		assertEquals(0, session.song.getSongGo()[1]);
		assertFalse(session.uiState.changes);
		assertEquals(0, session.undo.getUndoSteps());

		host.nextFileNew = null; // cancelled
		files.fileNew();
		assertEquals(4, session.tracks4_8);
	}

	@Test
	void instrumentRtiRoundTripAndBadData() throws IOException {
		assertTrue(files.fileOpen(DELTA, false));
		session.song.activeInstrSet(3, false);
		host.answer(dir.resolve("instr"), 1);
		files.fileInstrumentSave();
		Path rti = dir.resolve("instr.rti");
		assertTrue(Files.exists(rti));
		byte[] data = Files.readAllBytes(rti);
		assertEquals('R', data[0]);
		assertEquals(1, data[3]);
		String name = org.atari.raster.rmt.model.Song.nameToString(session.instruments.getName(3));

		session.song.activeInstrSet(7, false);
		host.answer(rti, 1);
		files.fileInstrumentLoad();
		assertEquals(name, org.atari.raster.rmt.model.Song.nameToString(session.instruments.getName(7)));
		assertEquals(1, session.undo.getUndoSteps());
		assertTrue(messages.log.isEmpty());

		Files.write(rti, new byte[] { 'X', 'Y', 'Z', 0 });
		host.answer(rti, 1);
		files.fileInstrumentLoad();
		assertEquals(List.of("E:Data error"), messages.log);
	}

	@Test
	void trackTxtSaveAndLoadSingleAndMultiple() throws IOException {
		assertTrue(files.fileOpen(DELTA, false));
		session.song.getSong()[0][0] = 5; // the active track (line 0, column 0)
		session.song.getSong()[0][1] = 6;
		host.answer(dir.resolve("track"), 1);
		files.fileTrackSave();
		Path txt = dir.resolve("track.txt");
		String text = Files.readString(txt, SongFiles.TEXT_CHARSET);
		assertTrue(text.startsWith("[TRACK]" + System.lineSeparator() + "05  "), text);

		// one track: loaded into the active track without a dialog
		session.song.getSong()[0][0] = 9;
		host.answer(txt, 1);
		files.fileTrackLoad();
		assertTrue(host.calls.stream().noneMatch(c -> c.startsWith("tracksLoad")));
		for (int line = 0; line < 64; line++) {
			assertEquals(session.tracks.getNote(5, line), session.tracks.getNote(9, line));
		}

		// several tracks: the dialog decides, consecutive from the active track on
		Files.writeString(txt, session.tracks.saveTrackTxt(5) + session.tracks.saveTrackTxt(6), SongFiles.TEXT_CHARSET);
		session.song.getSong()[0][0] = 20;
		host.nextTracksLoad = 0;
		host.answer(txt, 1);
		files.fileTrackLoad();
		assertTrue(host.calls.contains("tracksLoad:20:2"));
		assertEquals("I:Track(s) loading finished.:2 track(s) loaded.", messages.log.get(messages.log.size() - 1));
		for (int line = 0; line < 64; line++) {
			assertEquals(session.tracks.getNote(6, line), session.tracks.getNote(21, line));
		}

		// cancelled
		host.nextTracksLoad = -1;
		host.answer(txt, 1);
		session.song.getSong()[0][0] = 30;
		files.fileTrackLoad();
		assertEquals(-1, session.tracks.getNote(30, 0));

		// no track at all
		Files.writeString(txt, "nothing here", SongFiles.TEXT_CHARSET);
		host.answer(txt, 1);
		files.fileTrackLoad();
		assertEquals("E:Data error", messages.log.get(messages.log.size() - 1));
	}

	@Test
	void ensureFileExtensionOnlyAppendsWhatIsMissing() {
		assertEquals(Path.of("a.rmt"), SongFiles.ensureFileExtension(Path.of("a"), SongFiles.SONG_FILTERS, 1));
		assertEquals(Path.of("a.RMT"), SongFiles.ensureFileExtension(Path.of("a.RMT"), SongFiles.SONG_FILTERS, 1));
		assertEquals(Path.of("a.rmt.txt"), SongFiles.ensureFileExtension(Path.of("a.rmt"), SongFiles.SONG_FILTERS, 2));
		assertEquals(Path.of("a"), SongFiles.ensureFileExtension(Path.of("a"), SongFiles.SONG_FILTERS, 0));
	}
}
