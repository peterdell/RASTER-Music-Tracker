package org.atari.raster.rmt.ui;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

/**
 * The MIDI IN devices of the machine - what {@code midiInGetNumDevs()}/
 * {@code midiInGetDevCaps()}/{@code midiInOpen()} are to {@code CRmtMidi}:
 * their names in device order, and opening one by name so its messages
 * reach a listener. The real implementation is Java Sound
 * ({@link #javaSound()}); tests plug in a fake, so {@link RmtMidi} and the
 * Options dialog's device list run headless.
 */
public interface MidiDevices {

	/** Receives the short messages of an open device: the status byte (command and channel), data1, data2 - the three bytes of {@code MidiInProc}'s {@code dwParam1}. */
	interface Listener {
		void midiMessage(int status, int data1, int data2);
	}

	/** An open device ({@code HMIDIIN}); {@link #close()} is {@code midiInStop/Reset/Close}. */
	interface Connection {
		void close();
	}

	/** The device names in device order ({@code MIDIINCAPS.szPname} of each device index). */
	List<String> names();

	/**
	 * Opens the device of that name and starts delivering its messages to
	 * {@code listener}, on the device's own thread.
	 *
	 * @throws MidiUnavailableException when the device cannot be opened
	 *                                  ({@code midiInOpen} failing).
	 */
	Connection open(String name, Listener listener) throws MidiUnavailableException;

	/** Java Sound's MIDI input devices: the ones that transmit and are neither a sequencer nor a synthesizer. */
	static MidiDevices javaSound() {
		return new JavaSound();
	}

	/** The Java Sound implementation. */
	final class JavaSound implements MidiDevices {

		private static boolean isInput(MidiDevice device) {
			return device.getMaxTransmitters() != 0 && !(device instanceof Sequencer) && !(device instanceof Synthesizer);
		}

		@Override
		public List<String> names() {
			List<String> names = new ArrayList<>();
			try {
				for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
					try {
						if (isInput(MidiSystem.getMidiDevice(info))) {
							names.add(info.getName());
						}
					} catch (Exception ex) {
						// a device that can't be queried is not offered
					}
				}
			} catch (Exception ex) {
				// no MIDI subsystem at all: no devices
			}
			return names;
		}

		@Override
		public Connection open(String name, Listener listener) throws MidiUnavailableException {
			for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
				if (!info.getName().equals(name)) {
					continue;
				}
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (!isInput(device)) {
					continue;
				}
				device.open();
				Transmitter transmitter;
				try {
					transmitter = device.getTransmitter();
				} catch (MidiUnavailableException e) {
					device.close();
					throw e;
				}
				transmitter.setReceiver(new Receiver() {
					@Override
					public void send(MidiMessage message, long timeStamp) {
						if (message instanceof ShortMessage sm) {
							listener.midiMessage(sm.getStatus(), sm.getData1(), sm.getData2());
						}
						// system exclusive and meta messages: ignored, as MidiInProc ignores everything but MIM_DATA/MIM_ERROR
					}

					@Override
					public void close() {
					}
				});
				return () -> {
					transmitter.close();
					device.close();
				};
			}
			throw new MidiUnavailableException("No MIDI IN device '" + name + "'");
		}
	}
}
