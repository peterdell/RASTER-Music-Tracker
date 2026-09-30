package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.Track;

/**
 * {@link MidiInput} against {@code CSong::MidiEvent}: the messages are
 * pushed in directly (the fixture is headless), the tracks and the session
 * state are checked afterwards. Song line 0 maps column {@code c} to track
 * {@code c}; the cursor starts at column 0, line 0; the active instrument
 * is 3 and the volume 12.
 */
class MidiInputTest {

	private static final int AUDF = 0x3178;
	private static final int AUDC = 0x3180;
	private static final int AUDCTL = 0x3C69;

	private RmtSession session;
	private Song song;
	private MidiInput midi;
	private Track track0;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		song = session.song;
		for (int col = 0; col < 8; col++) {
			song.getSong()[0][col] = col;
			session.tracks.getTrack(col).len = 64;
		}
		track0 = session.tracks.getTrack(0);
		session.options.skipLinesAfterNoteInsert = 1;
		song.activeInstrSet(3, false);
		song.setVolume(12);
		session.midi.midiOn(); // OnInitialUpdate: the channel arrays reset (no device)
		midi = new MidiInput(session, new SongInput(session), session.midi);
	}

	private void event(int status, int data1, int data2) {
		midi.midiEvent(status, data1, data2, true);
	}

	// ---- channel 1: recording ----

	@Test
	void aChannel1NoteIsRecordedAtTheCursorWithTheActiveInstrumentAndVolumeAndTheCursorMovesDown() {
		event(0x90, 60, 100);

		assertEquals(24, track0.note[0]); // MIDI 60 - 36
		assertEquals(3, track0.instr[0]);
		assertEquals(12, track0.volume[0]); // no touch response: the current volume
		assertEquals(1, song.getActiveLine());
		assertEquals(24, session.midi.lastNoteOnChannel[0]);
	}

	@Test
	void aReleaseIsIgnoredWithoutRecordNoteOff() {
		event(0x90, 60, 100);
		event(0x80, 60, 64); // note off = note on with velocity 0

		assertEquals(-1, track0.note[1]);
		assertEquals(1, song.getActiveLine());
	}

	@Test
	void aReleaseOfTheLastNoteDeletesTheNoteAtTheCursorAndWritesVolumeZeroWithRecordNoteOff() {
		session.options.midiNoteOff = true;
		event(0x90, 60, 100); // recorded on line 0, cursor on line 1
		track0.note[1] = 30;
		track0.instr[1] = 2;
		track0.volume[1] = 9;

		event(0x80, 60, 0);

		assertEquals(-1, track0.note[1]);
		assertEquals(-1, track0.instr[1]);
		assertEquals(0, track0.volume[1]);
		assertEquals(2, song.getActiveLine());

		event(0x80, 62, 0); // not the last note: nothing
		assertEquals(2, song.getActiveLine());
	}

	@Test
	void touchResponseMakesTheVelocityTheVolumeWithTheOffsetClampedTo1To15() {
		session.options.midiTouchResponse = true;
		session.options.midiVolumeOffset = 2;

		event(0x90, 60, 64);
		assertEquals(10, track0.volume[0]); // 2 + 64 / 8
		assertEquals(10, song.getVolume(), "the current volume follows");

		event(0x90, 62, 127);
		assertEquals(15, track0.volume[1]); // 2 + 15, clamped

		session.options.midiVolumeOffset = 0;
		event(0x90, 64, 1);
		assertEquals(1, track0.volume[2]); // 0 + 0 -> 1
	}

	@Test
	void outsideTheTracksAreaOrWithShiftOrInJamModeTheNoteIsOnlyPlayed() {
		session.uiState.activePart = Part.PART_INSTRUMENTS;
		event(0x90, 60, 100);
		session.uiState.activePart = Part.PART_TRACKS;

		session.uiState.shiftKey = true;
		event(0x90, 62, 100);
		session.uiState.shiftKey = false;

		session.uiState.editMode = EditMode.JAM_MONO_MODE;
		event(0x90, 64, 100);

		assertEquals(-1, track0.note[0]);
		assertEquals(0, song.getActiveLine());
	}

	@Test
	void withoutFocusNothingIsRecordedUnlessInAJamMode() {
		midi.midiEvent(0x90, 60, 100, false);
		assertEquals(-1, track0.note[0]);

		session.uiState.editMode = EditMode.JAM_MONO_MODE;
		midi.midiEvent(0x90, 60, 100, false); // passes the gate, plays only (jam mode)
		assertEquals(-1, track0.note[0]);
	}

	@Test
	void duringFollowPlayInTheFirstHalfOfALineTheNoteIsQuantizedToTheNextLine() {
		song.songSetPlayLine(0);
		song.setPlayMode(PlayMode.PLAY_TRACK);
		song.setFollowPlayMode(true);
		song.setSpeed(8);
		song.setSpeeda(2); // first half of the line
		song.setPlayLine(4);
		song.setActiveLine(4);

		event(0x90, 60, 100);
		assertEquals(-1, track0.note[4], "held back");
		assertEquals(4, song.getActiveLine(), "no cursor move while following");

		song.setSpeeda(1);
		assertTrue(song.playVBI(session.tracks4_8, session.atariTrackerDriver, false, session.undo));
		assertEquals(24, track0.note[5]);
		assertEquals(3, track0.instr[5]);
		assertEquals(12, track0.volume[5]);
	}

	@Test
	void aProgramChangeOnChannel11SelectsTheInstrumentOnChannel1ItIsSwallowed() {
		event(0xCA, 5, 0);
		assertEquals(5, song.getActiveInstr());

		event(0xC0, 7, 0);
		assertEquals(5, song.getActiveInstr(), "the original's if (chn == 0) swallows it");
	}

	// ---- channels 2-9: live play ----

	@Test
	void aChannel2NotePlaysOnTrack0WithoutRecordingAndWithoutFocus() {
		event(0xC1, 7, 0); // the channel's instrument
		midi.midiEvent(0x91, 60, 100, false);

		assertEquals(-1, track0.note[0]);
		assertEquals(24, session.midi.lastNoteOnChannel[1]);
		assertEquals(12, session.midi.noteVolumeOnChannel[1]); // 100 / 8
		assertEquals(7, session.midi.instrumentOnChannel[1]);
	}

	@Test
	void aChannel2ReleaseCountsOnlyForItsLastNote() {
		event(0x91, 60, 100);
		event(0x91, 62, 0); // another note's release: ignored
		assertEquals(24, session.midi.lastNoteOnChannel[1]);
		assertEquals(12, session.midi.noteVolumeOnChannel[1]);

		event(0x81, 60, 0);
		assertEquals(24, session.midi.lastNoteOnChannel[1]);
		assertEquals(0, session.midi.noteVolumeOnChannel[1]);
	}

	@Test
	void allNotesOffOnChannel2ClearsItsNote() {
		event(0x91, 60, 100);
		event(0xB1, 123, 0);
		assertEquals(-1, session.midi.lastNoteOnChannel[1]);
		assertEquals(0, session.midi.noteVolumeOnChannel[1]);
	}

	@Test
	void aSystemResetForgetsChannels2To16() {
		event(0x90, 60, 100);
		event(0x91, 62, 100);
		event(0xC1, 7, 0);

		event(0xFF, 0, 0);

		assertEquals(24, session.midi.lastNoteOnChannel[0], "channel 1 is kept (\"from 1, because it is MULTITIMBRAL 2-16\")");
		assertEquals(-1, session.midi.lastNoteOnChannel[1]);
		assertEquals(0, session.midi.instrumentOnChannel[1]);
	}

	// ---- channels 16 and 10: the controller ----

	@Test
	void theRecKeyCyclesTheEditModeToTheExplorerModeAndTheTransportKeysPlayAndStop() {
		// SwitchEditMode(MIDI_CH15_MODE): edit -> mono jam -> stereo jam (a stereo song) -> the target -> edit, as the Edit/Jam toggle does
		event(0xBF, 118, 127);
		assertEquals(EditMode.JAM_MONO_MODE, session.uiState.editMode);
		event(0xBF, 118, 0); // no key press
		assertEquals(EditMode.JAM_MONO_MODE, session.uiState.editMode);
		event(0xBF, 118, 127);
		assertEquals(EditMode.JAM_STEREO_MODE, session.uiState.editMode);
		event(0xBF, 118, 127);
		assertEquals(EditMode.MIDI_CH15_MODE, session.uiState.editMode);
		event(0xBF, 118, 127);
		assertEquals(EditMode.EDIT_MODE, session.uiState.editMode);

		event(0xBF, 117, 127);
		assertEquals(PlayMode.PLAY_SONG, song.getPlayMode());
		event(0xBF, 116, 127);
		assertEquals(PlayMode.PLAY_STOP, song.getPlayMode());
		event(0xBF, 115, 127);
		assertEquals(PlayMode.PLAY_TRACK, song.getPlayMode());
		event(0xBF, 123, 127);
		assertEquals(PlayMode.PLAY_STOP, song.getPlayMode());
	}

	@Test
	void theVolumeSliderAndTheModulationWheel() {
		event(0xBF, 7, 0);
		assertEquals(1, song.getVolume()); // 0 -> 1
		event(0xBF, 7, 127);
		assertEquals(15, song.getVolume());
		event(0xBF, 7, 40);
		assertEquals(5, song.getVolume());

		event(0xBF, 1, 72); // (72 - 64) / 8 = +1 semitone
		event(0x9F, 60, 100); // a channel 16 note records like channel 1, shifted
		assertEquals(25, track0.note[0]);
		assertEquals(5, track0.volume[0]);
		assertEquals(1, song.getActiveLine());
	}

	@Test
	void theKnobsWriteThePokeyShadowRegistersInTheExplorerModeOnly() {
		byte[] memory = session.atari.getMemory();
		memory[AUDF] = 0x05;
		memory[AUDC] = 0x00;

		event(0xBF, 71, 0x0F); // edit mode: the knobs are the explorer mode's (fixed 2026-09-29)
		assertEquals(0x05, memory[AUDF]);

		session.uiState.editMode = EditMode.MIDI_CH15_MODE;
		event(0xBF, 71, 0x0F); // AUDF0 upper nibble
		assertEquals((byte) 0xF5, memory[AUDF]);
		event(0xBF, 75, 0x0A); // AUDF0 lower nibble
		assertEquals((byte) 0xFA, memory[AUDF]);
		event(0xBF, 73, 0x0C); // AUDC0 volume
		assertEquals(0x0C, memory[AUDC]);
		event(0xBF, 77, 0x05); // AUDC0 distortion (value * 2 in the upper nibble)
		assertEquals((byte) 0xAC, memory[AUDC]);
		event(0xBF, 72, 0x01); // AUDF1 upper nibble
		assertEquals(0x10, memory[AUDF + 1]);
	}

	@Test
	void theDrumpadsToggleAudctlBitsInTheExplorerModeOnly() {
		byte[] memory = session.atari.getMemory();
		memory[AUDCTL] = 0;

		event(0x99, 60, 100); // edit mode: nothing
		assertEquals(0, memory[AUDCTL]);

		session.uiState.editMode = EditMode.MIDI_CH15_MODE;
		event(0x99, 60, 100);
		assertEquals(0x04, memory[AUDCTL]);
		event(0x99, 60, 0); // no key press
		assertEquals(0x04, memory[AUDCTL]);
		event(0x99, 73, 100);
		assertEquals(0x05, memory[AUDCTL]);
		event(0x99, 66, 100); // 1.79 MHz of channel 1 (channels 1+2 selected)
		assertEquals(0x45, memory[AUDCTL]);
		event(0x99, 74, 100); // select channels 3+4
		event(0x99, 66, 100);
		assertEquals(0x65, memory[AUDCTL]);
		event(0x99, 69, 100); // reset
		assertEquals(0, memory[AUDCTL]);
		assertEquals(0x03, memory[0x3CD3]);
		event(0x99, 75, 100); // two-tone
		assertEquals((byte) 0x8B, memory[0x3CD3]);
		assertFalse(track0.note[0] >= 0, "drumpads record nothing");
	}

	@Test
	void aChannel16NoteInTheExplorerModeSoundsDirectlyFromTheDriversNoteTable() {
		byte[] memory = session.atari.getMemory();
		memory[AUDCTL] = 0;
		session.uiState.editMode = EditMode.MIDI_CH15_MODE;
		song.setActiveColumn(0);

		event(0x9F, 40, 100);

		assertEquals(memory[0xB100 + 40], memory[AUDF], "distortion 4 (the default table), 64 kHz");
		assertEquals(12, memory[AUDC], "no distortion set, the current volume");
		assertEquals(40, session.midi.lastNoteOnChannel[0]);
		assertEquals(-1, track0.note[0], "nothing recorded");

		event(0xCF, 6, 0); // program change: distortion 6 * 2 = C
		event(0x9F, 42, 100); // the next free track slot is 1
		assertEquals(memory[0xB200 + 42], memory[AUDF + 1]);
		assertEquals((byte) 0xCC, memory[AUDC + 1]);

		event(0x8F, 40, 0); // release of the first note frees slot 0
		assertEquals(-1, session.midi.lastNoteOnChannel[0]);
		assertEquals(42, session.midi.lastNoteOnChannel[1]);
	}
}
