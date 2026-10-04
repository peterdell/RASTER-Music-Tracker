package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.TrackerDriverVersion;

/** The command handlers and their enable/check logic, headless: the host is a recorder, the message boxes answer through Messages' test hook. */
class RmtCommandsTest {

	private static final class RecordingHost implements RmtCommands.Host {
		final List<String> calls = new ArrayList<>();

		@Override
		public void applyViewElements() {
			calls.add("applyViewElements");
		}

		@Override
		public void updateMinimumSize() {
			calls.add("updateMinimumSize");
		}

		@Override
		public void exit() {
			calls.add("exit");
		}

		@Override
		public void notAvailable(String feature) {
			calls.add("notAvailable:" + feature);
		}

		@Override
		public void skipLinesChanged() {
			calls.add("skipLinesChanged");
		}

		OptionsValues editedValues;
		boolean okPressed;

		@Override
		public boolean editOptions(OptionsValues values) {
			calls.add("editOptions");
			editedValues = values;
			return okPressed;
		}

		@Override
		public void rescale() {
			calls.add("rescale");
		}

		@Override
		public void showAbout() {
			calls.add("showAbout");
		}

		java.nio.file.Path nextScript;

		@Override
		public java.nio.file.Path chooseScriptFile() {
			calls.add("chooseScriptFile");
			return nextScript;
		}
	}

	private RmtSession session;
	private RecordingHost host;
	private RmtCommands commands;
	private SongInput input;
	private StubSongFilesHost filesHost;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		for (int col = 0; col < 8; col++) {
			session.song.getSong()[0][col] = col;
		}
		host = new RecordingHost();
		input = new SongInput(session);
		filesHost = new StubSongFilesHost();
		commands = new RmtCommands(session, input, host, new SongFiles(session, filesHost), new SongDialogs(session, new StubSongDialogsHost()));
	}

	@Test
	void thePokeyMenuWorksInTheExplorerModeOnly() {
		byte[] memory = session.atari.getMemory();
		memory[PokeyController.AUDF + 2] = 0x40;
		assertFalse(commands.isEnabled(RmtCommandId.POKEY_AUDF2_INCREASE_BY_10));
		commands.execute(RmtCommandId.POKEY_AUDF2_INCREASE_BY_10); // disabled: nothing
		assertEquals(0x40, memory[PokeyController.AUDF + 2]);

		commands.execute(RmtCommandId.EDIT_ACTIVATE_POKEY_EXPLORER_MODE);
		assertTrue(commands.isEnabled(RmtCommandId.POKEY_AUDF2_INCREASE_BY_10));
		commands.execute(RmtCommandId.POKEY_AUDF2_INCREASE_BY_10);
		assertEquals(0x50, memory[PokeyController.AUDF + 2]);
		commands.execute(RmtCommandId.POKEY_AUDC1_DECREASE_BY_01);
		assertEquals((byte) 0xFF, memory[PokeyController.AUDC + 1]);
		commands.execute(RmtCommandId.POKEY_AUDCTL_BIT4);
		assertEquals(0x10, memory[PokeyController.AUDCTL]);
		commands.execute(RmtCommandId.POKEY_SKCTL_TWO_TONE_MODE);
		assertEquals((byte) 0x8B, memory[PokeyController.SKCTL]); // the driver's SKCTL $03 with the two-tone bits
		commands.execute(RmtCommandId.POKEY_PREVIOUSCHANNEL);
		assertEquals(3, session.pokeyController.getChannelIndex());
		commands.execute(RmtCommandId.POKEY_DIVISOR_INCREASE_BY_1);
		assertEquals(2.0, session.pokeyController.getDivisor(), 1e-9);
	}

	@Test
	void theMidiToggleFollowsTheDevice() {
		RmtMidiTest.FakeDevices devices = new RmtMidiTest.FakeDevices();
		devices.names.add("Keys");
		session.midi.setDevices(devices);
		assertFalse(commands.isEnabled(RmtCommandId.MIDIONOFF), "OnUpdateMidiOnOff: no device");
		assertFalse(commands.isChecked(RmtCommandId.MIDIONOFF));
		commands.execute(RmtCommandId.MIDIONOFF); // disabled: nothing
		assertFalse(session.midi.isOn());

		session.options.midiDevice = "Keys";
		session.midi.midiInit();
		assertTrue(commands.isEnabled(RmtCommandId.MIDIONOFF));
		commands.execute(RmtCommandId.MIDIONOFF); // OnMidiOnOff
		assertTrue(session.midi.isOn());
		assertTrue(commands.isChecked(RmtCommandId.MIDIONOFF));
		commands.execute(RmtCommandId.MIDIONOFF);
		assertFalse(session.midi.isOn());
	}

	@Test
	void everyCommandHasALabelAndCanBeAskedForItsState() {
		for (RmtCommandId id : RmtCommandId.values()) {
			assertFalse(id.action.getLabel().isEmpty(), id + " has no label");
			assertTrue(id.action.getLabel().contains("&"), id + " has no mnemonic");
			commands.isEnabled(id);
			commands.isChecked(id);
			commands.getLabel(id);
		}
	}

	@Test
	void partCommandsMoveTheFocusAndAreCheckedAccordingly() {
		commands.execute(RmtCommandId.PART_INSTRUMENTS);
		assertEquals(Part.PART_INSTRUMENTS, session.uiState.activePart);
		assertEquals(Part.PART_INSTRUMENTS, session.uiState.activeTi);
		assertTrue(commands.isChecked(RmtCommandId.PART_INSTRUMENTS));
		assertFalse(commands.isChecked(RmtCommandId.PART_TRACKS));

		commands.execute(RmtCommandId.PART_INFO);
		assertEquals(Part.PART_INFO, session.uiState.activePart);
		assertEquals(Part.PART_INSTRUMENTS, session.uiState.activeTi); // the screen below stays
		assertFalse(commands.isChecked(RmtCommandId.PART_INSTRUMENTS)); // needs both

		commands.execute(RmtCommandId.PART_TRACKS);
		assertTrue(commands.isChecked(RmtCommandId.PART_TRACKS));
	}

	@Test
	void channelCommandsToggleSoloAndReflectTheState() {
		commands.execute(RmtCommandId.CHANNELS_CHANNEL3);
		assertFalse(session.channelControl.isChannelOn(2));
		assertFalse(commands.isChecked(RmtCommandId.CHANNELS_CHANNEL3));
		commands.execute(RmtCommandId.CHANNELS_TOGGLE_ACTIVE_CHANNEL_SOLO);
		assertTrue(session.channelControl.isChannelOn(0));
		assertFalse(session.channelControl.isChannelOn(1));
		commands.execute(RmtCommandId.CHANNELS_TOGGLE_ALL_CHANNELS_ON_OFF);
		assertFalse(session.channelControl.isChannelOn(0));
		assertTrue(session.channelControl.isChannelOn(1));
	}

	@Test
	void undoRedoStatesAndLabelsFollowTheUndoHistory() {
		assertFalse(commands.isEnabled(RmtCommandId.EDIT_UNDO));
		assertEquals("&Undo", commands.getLabel(RmtCommandId.EDIT_UNDO));
		input.keyDown(VirtualKey.VK_Z); // enters a note = one undo step
		assertTrue(commands.isEnabled(RmtCommandId.EDIT_UNDO));
		assertEquals("&Undo (1)", commands.getLabel(RmtCommandId.EDIT_UNDO));
		assertTrue(commands.isEnabled(RmtCommandId.EDIT_CLEAR_UNDO_REDO_HISTORY));

		commands.execute(RmtCommandId.EDIT_UNDO);
		assertEquals(-1, session.tracks.getNote(0, 0));
		assertTrue(commands.isEnabled(RmtCommandId.EDIT_REDO));
		commands.execute(RmtCommandId.EDIT_REDO);
		assertEquals(0, session.tracks.getNote(0, 0));

		commands.execute(RmtCommandId.EDIT_CLEAR_UNDO_REDO_HISTORY);
		assertFalse(commands.isEnabled(RmtCommandId.EDIT_UNDO));
	}

	@Test
	void stepSizeCommandsWrapAndTellTheHost() {
		session.options.skipLinesAfterNoteInsert = 8;
		commands.execute(RmtCommandId.SONG_INCREASE_PATTERN_STEP_SIZE);
		assertEquals(0, session.options.skipLinesAfterNoteInsert);
		commands.execute(RmtCommandId.SONG_DECREASE_PATTERN_STEP_SIZE);
		assertEquals(8, session.options.skipLinesAfterNoteInsert);
		assertEquals(List.of("skipLinesChanged", "skipLinesChanged"), host.calls);
	}

	@Test
	void viewTogglesFlipTheOptionsAndOnlyTheBarsReachTheHost() {
		commands.execute(RmtCommandId.VIEW_VOLUMEANALYZER);
		assertFalse(session.options.view.volumeAnalyzer);
		assertFalse(commands.isChecked(RmtCommandId.VIEW_VOLUMEANALYZER));
		assertFalse(commands.isEnabled(RmtCommandId.VIEW_POKEYREGS)); // needs the analyzer
		assertTrue(host.calls.isEmpty());
		commands.execute(RmtCommandId.VIEW_STATUS_BAR);
		assertEquals(List.of("applyViewElements"), host.calls);
	}

	@Test
	void playCommandsSetThePlayModeAndStopClearsIt() {
		commands.execute(RmtCommandId.SONG_PLAY_FROM_START);
		assertEquals(PlayMode.PLAY_SONG, session.song.getPlayMode());
		assertTrue(commands.isChecked(RmtCommandId.SONG_PLAY_FROM_START));
		assertTrue(commands.isEnabled(RmtCommandId.SONG_STOP));
		commands.execute(RmtCommandId.SONG_STOP);
		assertEquals(PlayMode.PLAY_STOP, session.song.getPlayMode());
		assertFalse(commands.isEnabled(RmtCommandId.SONG_STOP));
		commands.execute(RmtCommandId.SONG_PLAY_FOLLOW);
		assertFalse(session.song.getFollowPlayMode());
	}

	@Test
	void blockCommandsAreEnabledOnlyWithASelectionAndActOnIt() {
		assertFalse(commands.isEnabled(RmtCommandId.BLOCK_CUT));
		input.keyDown(VirtualKey.VK_Z); // a note on line 0
		commands.execute(RmtCommandId.BLOCK_SELECTALL);
		assertTrue(session.clipboard.isBlockSelected());
		assertTrue(commands.isEnabled(RmtCommandId.BLOCK_CUT));
		commands.execute(RmtCommandId.BLOCK_TRANSPOSE_NOTES_UP);
		assertEquals(1, session.tracks.getNote(0, 0));
		commands.execute(RmtCommandId.BLOCK_DELETE);
		assertEquals(-1, session.tracks.getNote(0, 0));
	}

	@Test
	void bookmarkAndSongLineCommands() {
		assertFalse(commands.isEnabled(RmtCommandId.SONG_CLEAR_BOOKMARK));
		commands.execute(RmtCommandId.SONG_SET_BOOKMARK);
		assertTrue(session.song.isBookmark());
		assertTrue(commands.isEnabled(RmtCommandId.SONG_PLAY_FROM_BOOKMARK));
		commands.execute(RmtCommandId.SONG_CLEAR_BOOKMARK);
		assertFalse(session.song.isBookmark());

		commands.execute(RmtCommandId.SONG_COPY_LINE);
		session.song.songSetActiveLine(3);
		commands.execute(RmtCommandId.SONG_PASTE_LINE);
		assertEquals(5, session.song.getSong()[3][5]);
		commands.execute(RmtCommandId.SONG_CLEAR_LINE);
		assertEquals(-1, session.song.getSong()[3][5]);
	}

	@Test
	void confirmationsGoThroughMessagesAndCancelLeavesTheDataAlone() {
		input.keyDown(VirtualKey.VK_Z);
		session.messages.setTestQuestionAnswer(MessageAnswer.CANCEL);
		commands.execute(RmtCommandId.TRACK_ALLTRACKSCLEANUP);
		assertEquals(0, session.tracks.getNote(0, 0));
		session.messages.setTestQuestionAnswer(MessageAnswer.YES);
		commands.execute(RmtCommandId.TRACK_ALLTRACKSCLEANUP);
		assertEquals(-1, session.tracks.getNote(0, 0));
	}

	@Test
	void switchingEditModeAndTheExplorerCommand() {
		commands.execute(RmtCommandId.EDIT_SWITCH_EDIT_MODE);
		assertEquals(EditMode.JAM_MONO_MODE, session.uiState.editMode);
		assertTrue(commands.isChecked(RmtCommandId.TOOLBAR_SWITCH_EDIT_MODE));
		commands.execute(RmtCommandId.EDIT_ACTIVATE_POKEY_EXPLORER_MODE);
		assertEquals(EditMode.POKEY_EXPLORER_MODE, session.uiState.editMode);
		assertFalse(commands.isEnabled(RmtCommandId.EDIT_ACTIVATE_POKEY_EXPLORER_MODE));
		assertTrue(commands.isEnabled(RmtCommandId.POKEY_AUDCTL_BIT0)); // the Pokey menu works in the explorer mode
	}

	@Test
	void localHelpReportsAMissingHelpFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
		java.nio.file.Path original = org.atari.raster.rmt.model.ProgramFolder.get();
		java.nio.file.Files.createDirectories(dir.resolve("resources")); // a resource root of its own (else the checkout's rmt/ would serve), without docs/
		List<String> errors = new ArrayList<>();
		session.messages.setHandler(new org.atari.raster.rmt.model.Messages.Handler() {
			@Override
			public void showError(String title, String message) {
				errors.add(title + ": " + message);
			}

			@Override
			public void showWarning(String title, String message) {
			}

			@Override
			public void showInformation(String title, String message) {
			}

			@Override
			public MessageAnswer askQuestion(String title, String message, org.atari.raster.rmt.model.MessageButtons buttons) {
				return MessageAnswer.CANCEL;
			}
		});
		try {
			org.atari.raster.rmt.model.ProgramFolder.set(dir); // no docs/ here
			assertEquals(dir.resolve("docs").resolve("rmt_en.html"), RmtCommands.localHelpFile());
			commands.execute(RmtCommandId.HELP);
			assertEquals(1, errors.size());
			assertTrue(errors.get(0).startsWith("Help: The help file '"), errors.get(0));
		} finally {
			org.atari.raster.rmt.model.ProgramFolder.set(original);
		}
	}

	@Test
	void runScriptRunsTheChosenScriptOnTheLiveSessionAndReportsOnce(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
		java.nio.file.Path script = dir.resolve("open.rmtscript");
		java.nio.file.Files.write(script, List.of("open \"" + ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt").toAbsolutePath().toString().replace("\\", "\\\\") + "\"", "echo hello"));
		List<String> boxes = new ArrayList<>();
		session.messages.setHandler(new org.atari.raster.rmt.model.Messages.Handler() {
			@Override
			public void showError(String title, String message) {
				boxes.add("E:" + title + ":" + message);
			}

			@Override
			public void showWarning(String title, String message) {
				boxes.add("W:" + title + ":" + message);
			}

			@Override
			public void showInformation(String title, String message) {
				boxes.add("I:" + title + ":" + message);
			}

			@Override
			public MessageAnswer askQuestion(String title, String message, org.atari.raster.rmt.model.MessageButtons buttons) {
				return MessageAnswer.CANCEL;
			}
		});

		host.nextScript = script;
		commands.execute(RmtCommandId.TOOLS_RUN_SCRIPT);
		assertEquals(4, session.tracks4_8, "Delta.rmt (mono) was opened");
		assertTrue(host.calls.contains("chooseScriptFile") && host.calls.contains("updateMinimumSize"), host.calls.toString());
		assertEquals(1, boxes.size(), boxes.toString());
		assertTrue(boxes.get(0).startsWith("I:Script:Script 'open.rmtscript' finished.") && boxes.get(0).contains("hello"), boxes.get(0));
		assertTrue(session.messages.getHandler() != null, "the window's handler is back in place");

		boxes.clear();
		java.nio.file.Files.write(script, List.of("export mp3 x.mp3"));
		commands.execute(RmtCommandId.TOOLS_RUN_SCRIPT);
		assertEquals(1, boxes.size(), boxes.toString());
		assertTrue(boxes.get(0).startsWith("E:Script:Script 'open.rmtscript' failed:") && boxes.get(0).contains("line 1"), boxes.get(0));

		host.nextScript = null; // cancelled
		boxes.clear();
		commands.execute(RmtCommandId.TOOLS_RUN_SCRIPT);
		assertTrue(boxes.isEmpty());
	}

	@Test
	void openAsapFileIsDisabledLikeItsHandlerlessMenuItemInMfc() {
		assertFalse(commands.isEnabled(RmtCommandId.TOOLS_OPEN_ASAP_FILE));
		commands.execute(RmtCommandId.TOOLS_OPEN_ASAP_FILE); // does nothing, reports nothing
		commands.execute(RmtCommandId.FILE_EXIT);
		assertEquals(List.of("exit"), host.calls);
	}

	@Test
	void optionsCommandEditsACopyAndAppliesItOnlyOnOK() {
		session.options.scalingPercentage = 150;
		host.okPressed = false;
		commands.execute(RmtCommandId.TOOLS_OPTIONS);
		assertEquals(List.of("editOptions"), host.calls);
		assertEquals(150, host.editedValues.scalingPercentage);
		host.editedValues.scalingPercentage = 200; // a cancelled dialog leaves the options alone
		assertEquals(150, session.options.scalingPercentage);

		host.calls.clear();
		host.okPressed = true;
		commands.execute(RmtCommandId.TOOLS_OPTIONS);
		assertEquals(List.of("editOptions"), host.calls); // unchanged values: no rescale
		assertEquals(150, session.options.scalingPercentage);
	}

	@Test
	void applyOptionsHasTheCppSideEffectsOnlyForChangedValues() {
		OptionsValues v = OptionsValues.from(session);
		v.scalingPercentage = 250; // must differ from the default, which is 200
		v.ntsc = true;
		v.trackerDriverVersion = TrackerDriverVersion.PATCH8;
		v.trackLinePrimaryHighlight = 16;
		v.useGermanNotation = true;
		v.keyboardLayout = KeyboardLayout.AZERTY;
		v.midiDevice = "Some device";
		v.midiVolumeOffset = 7;
		RmtMidiTest.FakeDevices devices = new RmtMidiTest.FakeDevices(); // applyOptions runs MidiInit, which clears an unknown device name
		devices.names.add("Some device");
		session.midi.setDevices(devices);
		double basetuning = session.tuningSettings.basetuning;

		commands.applyOptions(v);

		assertEquals(List.of("rescale"), host.calls);
		assertEquals(250, session.options.scalingPercentage);
		assertTrue(session.song.isNTSC());
		assertEquals(basetuning * org.atari.raster.rmt.model.Atari.FREQ_17_NTSC / org.atari.raster.rmt.model.Atari.FREQ_17_PAL, session.tuningSettings.basetuning, 1e-9); // SetNTSC rescaled the tuning
		assertEquals(TrackerDriverVersion.PATCH8, session.options.trackerDriverVersion);
		assertEquals(16, session.options.trackLinePrimaryHighlight);
		assertTrue(session.options.useGermanNotation);
		assertEquals(KeyboardLayout.AZERTY, session.options.keyboardLayout);
		assertEquals("Some device", session.options.midiDevice);
		assertEquals(7, session.options.midiVolumeOffset);

		host.calls.clear();
		commands.applyOptions(v); // applying the same values again changes nothing
		assertTrue(host.calls.isEmpty());
		assertTrue(session.song.isNTSC());
	}

	@Test
	void trackInfoTextMatchesTheCppMessageBox() {
		session.song.getSong()[1][2] = 5;
		session.song.getSong()[4][2] = 5;
		assertEquals("Track: 05\nUsing in song:\nL1: 0   L2: 0   L3: 2   L4: 0   R1: 0   R2: 1   R3: 0   R4: 0   \nUsed in 3 songlines, globally 3 times.", commands.trackInfoText(5));
		assertNull(commands.getLabel(RmtCommandId.TRACK_INFOABOUTUSINGOFACTUALTRACK));
	}

	/**
	 * File > Export now asks for the file name and then hands the slow part to
	 * the host (plans/24_EXPORT_SCREEN_UPDATES_PLAN.md, batch E3). A host
	 * without a worker thread, like this one, runs it where it stands, so the
	 * command still writes the file by the time it returns.
	 */
	@Test
	void theExportCommandAsksForTheFileAndThenRunsTheExport() throws java.io.IOException {
		java.nio.file.Path out = java.nio.file.Files.createTempDirectory("rmt-export").resolve("song.rmt");
		filesHost.answer(out, 1); // stripped RMT, whose dialog the stub answers
		filesHost.nextStrippedRmt = new SongFiles.StrippedRmtChoice(0x4000, org.atari.raster.rmt.model.AssemblerFormat.XASM, false, false, false);

		commands.execute(RmtCommandId.FILE_EXPORT);

		assertTrue(java.nio.file.Files.exists(out), "the export command wrote nothing");
		assertTrue(java.nio.file.Files.size(out) > 0);
	}
}
