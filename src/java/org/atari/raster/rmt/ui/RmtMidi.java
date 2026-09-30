package org.atari.raster.rmt.ui;

import java.util.Arrays;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;

/**
 * The port of {@code CRmtMidi} ({@code RmtMidi.h/.cpp}, the global
 * {@code g_Midi}): the MIDI IN device's life cycle. The persisted settings
 * ({@code MIDI_IN} = the device name, touch response, volume offset, note
 * off) live in {@link RmtOptions}, where the port keeps every
 * {@code rmt.ini} value; this class reads the device name from there.
 *
 * <p>{@link #midiInit()} looks the configured name up among the devices
 * (a missing device: the C++ warning box and the name cleared),
 * {@link #midiOn()} opens it and resets the per-channel arrays the handler
 * ({@link MidiInput}) keeps, {@link #midiOff()} closes it. The window calls
 * init + on at start-up ({@code OnInitialUpdate}), off on close; the
 * Options dialog and {@code ResetRMTConfig} call init; the toolbar button
 * toggles on/off. Messages arrive on the device's thread through the
 * listener set with {@link #setListener}.
 *
 * <p>The devices come from {@link MidiDevices} - Java Sound by default,
 * a fake in tests ({@link #setDevices}).
 */
public final class RmtMidi {

	private final RmtSession session;
	private MidiDevices devices = MidiDevices.javaSound();
	private MidiDevices.Listener listener = (status, data1, data2) -> {
	};

	private boolean on;
	private MidiDevices.Connection connection;
	/** {@code m_MidiInDeviceId}: the index of the configured device in {@link MidiDevices#names()}, -1 for none. */
	private int deviceId = -1;

	/** {@code m_LastNoteOnChannel}: the last recorded note on each MIDI channel. */
	final int[] lastNoteOnChannel = new int[16];
	/** {@code m_NoteVolumeOnChannel}: the note volume on each MIDI channel. */
	final int[] noteVolumeOnChannel = new int[16];
	/** {@code m_InstrumentOnChannel}: the last set instrument number on each MIDI channel. */
	final int[] instrumentOnChannel = new int[16];

	public RmtMidi(RmtSession session) {
		this.session = session;
	}

	/** The device source; tests replace Java Sound with a fake. */
	public void setDevices(MidiDevices devices) {
		this.devices = devices;
	}

	/** The device names, for the Options dialog's combo box ("None" is the dialog's own first entry). */
	public List<String> deviceNames() {
		return devices.names();
	}

	/** Where an open device's messages go ({@code MidiInProc} -> {@code CSong::MidiEvent}); called on the device's thread. */
	public void setListener(MidiDevices.Listener listener) {
		this.listener = listener;
	}

	/** {@code IsOn()}. */
	public boolean isOn() {
		return on;
	}

	/** {@code GetMidiDevId() >= 0}: a device is configured and was found - the toolbar button's enabled state. */
	public boolean hasDevice() {
		return deviceId >= 0;
	}

	/** {@code GetMidiDevName()}: the configured device name, "" for none. */
	public String getDeviceName() {
		return session.options.midiDevice;
	}

	/**
	 * {@code MidiInit()}: turns MIDI off, looks the configured name up. No
	 * name: no device, true. Found: remembered, turned on again if it was
	 * on, true. Not found: the warning box "MIDI IN error", the name
	 * cleared, false.
	 */
	public boolean midiInit() {
		boolean wasOn = isOn();
		midiOff();

		String name = session.options.midiDevice;
		if (name == null || name.isEmpty()) {
			deviceId = -1;
			return true; // does not want a MIDI device
		}

		int index = devices.names().indexOf(name);
		if (index >= 0) {
			deviceId = index; // found midi in by configfile
			if (wasOn) {
				midiOn();
			}
			return true;
		}
		// Device was not found.
		deviceId = -1;
		session.messages.sendWarningMessage("MIDI IN error", "Can't init the MIDI IN device\n" + name);
		session.options.midiDevice = "";
		return false;
	}

	/**
	 * {@code MidiOn()}: resets the channel arrays (always, as in C++), then
	 * opens the found device and starts it; a failure is the error box
	 * "MidiInOpen error". False without a device.
	 */
	public boolean midiOn() {
		Arrays.fill(lastNoteOnChannel, -1); // last pressed keys on each channel
		Arrays.fill(noteVolumeOnChannel, 0); // volume
		Arrays.fill(instrumentOnChannel, 0); // instrument numbers

		if (deviceId >= 0) {
			if (isOn()) {
				midiOff();
			}
			try {
				connection = devices.open(session.options.midiDevice, (status, data1, data2) -> listener.midiMessage(status, data1, data2));
				on = true;
				return true;
			} catch (MidiUnavailableException | RuntimeException e) {
				session.messages.sendErrorMessage("MidiInOpen error", "Can't open selected MIDI IN device.");
				return false;
			}
		}
		return false;
	}

	/** {@code MidiOff()}: stop, reset and close the device. */
	public void midiOff() {
		if (connection != null) {
			try {
				connection.close();
			} catch (RuntimeException e) {
				// closing a vanished device: nothing to do
			}
			connection = null;
		}
		on = false;
	}

	/** {@code MidiRestart()}: off, then on. */
	public boolean midiRestart() {
		midiOff();
		return midiOn();
	}
}
