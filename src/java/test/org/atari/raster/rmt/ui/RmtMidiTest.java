package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.MessageButtons;
import org.atari.raster.rmt.model.Messages;

/** {@link RmtMidi} against {@code CRmtMidi}'s life cycle, with a fake device list instead of Java Sound. */
class RmtMidiTest {

	/** The machine's MIDI IN devices, scripted. */
	static final class FakeDevices implements MidiDevices {
		final List<String> names = new ArrayList<>();
		final List<String> opened = new ArrayList<>();
		int closed;
		boolean failOpen;
		Listener listener;

		@Override
		public List<String> names() {
			return names;
		}

		@Override
		public Connection open(String name, Listener listener) throws MidiUnavailableException {
			if (failOpen) {
				throw new MidiUnavailableException("busy");
			}
			opened.add(name);
			this.listener = listener;
			return () -> closed++;
		}
	}

	/** The message boxes, recorded as "title: message". */
	static final class RecordingMessages implements Messages.Handler {
		final List<String> warnings = new ArrayList<>();
		final List<String> errors = new ArrayList<>();

		@Override
		public void showError(String title, String message) {
			errors.add(title + ": " + message);
		}

		@Override
		public void showWarning(String title, String message) {
			warnings.add(title + ": " + message);
		}

		@Override
		public void showInformation(String title, String message) {
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			return MessageAnswer.YES;
		}
	}

	private RmtSession session;
	private FakeDevices devices;
	private RecordingMessages messages;
	private RmtMidi midi;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		devices = new FakeDevices();
		devices.names.add("Pad");
		devices.names.add("Keys");
		messages = new RecordingMessages();
		session.messages.setHandler(messages);
		midi = session.midi;
		midi.setDevices(devices);
	}

	@Test
	void withoutADeviceNameInitSucceedsAndOnDoesNothing() {
		assertTrue(midi.midiInit());
		assertFalse(midi.hasDevice());
		assertFalse(midi.midiOn());
		assertFalse(midi.isOn());
		assertTrue(devices.opened.isEmpty());
		assertTrue(messages.warnings.isEmpty() && messages.errors.isEmpty());
	}

	@Test
	void aConfiguredDeviceIsFoundOpenedAndClosed() {
		session.options.midiDevice = "Keys";
		assertTrue(midi.midiInit());
		assertTrue(midi.hasDevice());
		assertFalse(midi.isOn(), "MidiInit does not turn it on by itself");

		assertTrue(midi.midiOn());
		assertTrue(midi.isOn());
		assertEquals(List.of("Keys"), devices.opened);

		midi.midiOff();
		assertFalse(midi.isOn());
		assertEquals(1, devices.closed);
		assertTrue(midi.hasDevice(), "off keeps the device, only the toolbar toggle changes");
	}

	@Test
	void aMissingDeviceWarnsAndClearsTheName() {
		session.options.midiDevice = "Gone";
		assertFalse(midi.midiInit());
		assertFalse(midi.hasDevice());
		assertEquals("", session.options.midiDevice, "written back as none on the next WriteRMTConfig");
		assertEquals(List.of("MIDI IN error: Can't init the MIDI IN device\nGone"), messages.warnings);
	}

	@Test
	void initWhileOnReopensTheDevice() {
		session.options.midiDevice = "Keys";
		midi.midiInit();
		midi.midiOn();

		assertTrue(midi.midiInit()); // the Options dialog's OK
		assertTrue(midi.isOn(), "was on before, so on again");
		assertEquals(1, devices.closed);
		assertEquals(List.of("Keys", "Keys"), devices.opened);
	}

	@Test
	void aDeviceThatCannotBeOpenedIsAnError() {
		session.options.midiDevice = "Pad";
		midi.midiInit();
		devices.failOpen = true;

		assertFalse(midi.midiOn());
		assertFalse(midi.isOn());
		assertEquals(List.of("MidiInOpen error: Can't open selected MIDI IN device."), messages.errors);
	}

	@Test
	void onResetsTheChannelArraysEvenWithoutADevice() {
		midi.lastNoteOnChannel[3] = 20;
		midi.noteVolumeOnChannel[3] = 5;
		midi.instrumentOnChannel[3] = 7;

		midi.midiOn();

		for (int i = 0; i < 16; i++) {
			assertEquals(-1, midi.lastNoteOnChannel[i]);
			assertEquals(0, midi.noteVolumeOnChannel[i]);
			assertEquals(0, midi.instrumentOnChannel[i]);
		}
	}

	@Test
	void theDevicesMessagesReachTheListener() {
		List<String> received = new ArrayList<>();
		midi.setListener((status, data1, data2) -> received.add(status + "/" + data1 + "/" + data2));
		session.options.midiDevice = "Keys";
		midi.midiInit();
		midi.midiOn();

		devices.listener.midiMessage(0x90, 60, 100);

		assertEquals(List.of("144/60/100"), received);
	}

	@Test
	void theDeviceNamesComeFromTheDevices() {
		assertEquals(List.of("Pad", "Keys"), midi.deviceNames());
	}
}
