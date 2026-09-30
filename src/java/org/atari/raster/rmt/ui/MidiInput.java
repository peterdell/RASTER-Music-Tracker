package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.Instruments;
import org.atari.raster.rmt.model.Notes;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.Tracks;

/**
 * The port of {@code CSong::MidiEvent()} ({@code Midi_Song.cpp}): what a
 * MIDI message does. One entry point, {@link #midiEvent}, with the C++
 * globals as session state or explicit parameters: {@code g_RmtHasFocus} is
 * the {@code hasFocus} parameter (the window's focus state, {@code true}
 * from a script), {@code g_shiftkey}/{@code g_controlkey}/{@code g_activepart}/
 * {@code g_prove} are the {@link UiState}, {@code g_tracks4_8},
 * {@code g_respectvolume}, {@code g_SkipLinesAfterNoteInsert} the session's,
 * {@code g_Midi}'s channel arrays are {@link RmtMidi}'s. The {@code CSong}
 * members that only the MIDI code uses ({@code m_mod_wheel},
 * {@code m_vol_slider}, {@code m_heldkeys}, {@code m_midi_distortion},
 * {@code m_ch_offset}) are fields here.
 *
 * <p>The channels, in the order the C++ code tests them:
 * <ol>
 * <li>a note off (0x80) on any channel but 16 and 10 is a note on with
 * velocity 0; of the system messages only the system reset (0xFF) does
 * anything;</li>
 * <li>channels 2-9 play live on the Atari tracks (multitimbral, one
 * instrument per channel from program changes), without recording and
 * without focus;</li>
 * <li>everything below needs the window's focus or a jam mode;</li>
 * <li>channels 16 and 10 are the C++ author's controller: transport and
 * mode keys, mod wheel and volume slider, and in {@code MIDI_CH15_MODE} the
 * knobs, drumpads and direct POKEY notes ("test code", as the C++ comments
 * say);</li>
 * <li>channel 1 records notes at the cursor - the de facto MIDI input;
 * a program change on channels 11-15 sets the active instrument.</li>
 * </ol>
 * The recording follows the keyboard's ({@code TrackKey}): quantization
 * during follow-play, {@code respectVolume}, the cursor moving down
 * {@code skipLinesAfterNoteInsert} lines, the note played.
 *
 * <p>Runs on the MIDI device's thread, under {@link RmtSession#lock} (the
 * window's listener takes it), never touching Swing.
 */
public final class MidiInput {

	private final RmtSession session;
	private final SongInput songInput;
	private final RmtMidi midi;

	// "MIDI input variables, used for tests through MIDI CH15" (Song.h)
	int modWheel;
	int volSlider;
	int heldKeys;
	int midiDistortion;
	boolean chOffset;

	public MidiInput(RmtSession session, SongInput songInput, RmtMidi midi) {
		this.session = session;
		this.songInput = songInput;
		this.midi = midi;
	}

	/**
	 * {@code CSong::MidiEvent(dwParam, hasFocus)}: {@code status} is the
	 * status byte (command in the upper, channel in the lower nibble),
	 * {@code data1}/{@code data2} the data bytes.
	 */
	public void midiEvent(int status, int data1, int data2, boolean hasFocus) {
		Song song = session.song;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		int tracks4_8 = session.tracks4_8;
		byte[] memory = session.atari.getMemory();

		int cmd = status & 0xF0;
		int chn = status & 0x0F;
		int pr1 = data1 & 0xFF;
		int pr2 = data2 & 0xFF;
		if (cmd == 0x80 && chn != 15 && chn != 9) {
			cmd = 0x90;
			pr2 = 0;
		} // key off, as long as the MIDI channel isn't 15 or 10 to avoid conflicts
		else if (cmd == 0xF0) {
			if (status == 0xFF) {
				systemReset(); // System Reset
			}
			return; // END
		}

		if (chn > 0 && chn < 9) {
			// 2-10 channels (chn = 1-9) are used for multitimbral L1-L4, R1-R4
			int atc = (chn - 1) % tracks4_8; // atari track 0-7 (resp. 0-3 in mono)
			if (cmd == 0x90) {
				// (C++ tests "chn == 9 ... drums channel" here - unreachable inside chn < 9, not ported)
				// channel 2-9 (chn=1-8)
				int note = pr1 - 36;
				if (note >= 0 && note < Notes.NOTESNUM) {
					if (pr2 != 0 || note == midi.lastNoteOnChannel[1 + atc]) {
						multitimbralNote(atc, chn, note, pr2 / 8);
					}
				}
			} else if (cmd == 0xC0) {
				if (pr1 >= 0 && pr1 < Instruments.INSTRSNUM) {
					midi.instrumentOnChannel[1 + atc] = pr1;
				}
			} else if (cmd == 0xB0) {
				if (pr1 == 123) {
					// All notes OFF
					multitimbralNote(atc, chn, -1, 0);
				} else if (pr1 == 121) {
					// Reset All Controls
					systemReset();
				}
			}
			return; // END
		}

		if (!hasFocus && !ui.editMode.isProveMode()) {
			return; // when it has no focus and is not in prove mode, the MIDI input will be ignored, to avoid overwriting patterns accidentally
		}

		// test input from my own MIDI controller. CH15 for most events input, and CH9 specifically for the drumpad buttons, used for certain shortcuts triggered with MIDI NOTE ON events
		if (chn == 15 || chn == 9) {
			// command buttons, while this would technically work from any MIDI channel, it is specifically mapped for CH15 in order to avoid conflicing code, as a temporary workaround
			if (cmd == 0xB0 && chn == 15) { // control change and key pressed
				controlChangeCh15(pr1, pr2, memory);
				return; // finished, everything else will be ignored, unless it's using a different MIDI channel
			}

			if (cmd == 0x90 && chn == 9 && ui.editMode == EditMode.MIDI_CH15_MODE) { // drumpads used to control the POKEY registers
				drumpad(pr1, pr2, memory);
				return;
			}

			if (chn == 9) {
				return; // we do not want any of those MIDI events outside of the drumpads!!!
			}

			// default notes input event, which is mostly copied from the CH0 code. This is a very terrible approach, and will eventually be replaced (see above)
			if (chn == 15 && ui.editMode != EditMode.MIDI_CH15_MODE) {
				int atc = heldKeys % tracks4_8; // atari track 0-7 (resp. 0-3 in mono)

				if (cmd == 0x80) { // key off
					heldKeys--;
					midi.noteVolumeOnChannel[atc] = 0; // volume
					midi.lastNoteOnChannel[atc] = -1;
					midi.instrumentOnChannel[atc] = song.getActiveInstr(); // instrument numbers
					if (heldKeys < 0) {
						heldKeys = 0;
					}
					return;
				}

				if (cmd == 0x90) {
					// key on
					int note = pr1 - 36 + modWheel; // from the 3rd octave + modulation wheel offset
					heldKeys++;
					int vol = noteOnVolume(pr2);
					if (vol == VOLUME_IGNORED) {
						return; // note off is not recognized
					}
					if (note >= 0 && note < Notes.NOTESNUM) { // only within this range
						recordOrPlay(note, vol, atc, midi.lastNoteOnChannel, atc, false);
					}
				}

			} ////
			else { // notes (soon...)
				directPokeyNote(cmd, pr1, memory);
				return; // C++ returns from inside the block for a program change and falls out of it otherwise - with nothing left below for channel 16 in this mode
			}
		}

		// The following is performed only on channel 0 (chn = 0), and is the defacto notes input in tracks.
		// This is also the only mode that is specifically using the old code, and is technically legacy for compatibility reasons.
		if (chn == 0) {

			if (cmd == 0x90) {
				// key on/off
				int note = pr1 - 36; // from the 3rd octave
				int vol = noteOnVolume(pr2);
				if (vol == VOLUME_IGNORED) {
					return; // note off is not recognized
				}
				if (note >= 0 && note < Notes.NOTESNUM) { // only within this range
					recordOrPlay(note, vol, song.getActiveColumn(), midi.lastNoteOnChannel, chn, true);
				}
			}

		} else if (cmd == 0xC0) {
			// prg change
			if (pr1 >= 0 && pr1 < Instruments.INSTRSNUM) {
				song.activeInstrSet(pr1, options.keyboardRememberOctavesAndVolumes);
			}
		}
	}

	private static final int VOLUME_IGNORED = Integer.MIN_VALUE;

	/**
	 * The volume of a channel 1/16 note on: velocity 0 is a note off, only
	 * recognized with "Record note off" (else {@link #VOLUME_IGNORED}); with
	 * touch response {@code offset + velocity / 8} clamped to 1-15 and made
	 * the current volume; else the current volume.
	 */
	private int noteOnVolume(int pr2) {
		Song song = session.song;
		RmtOptions options = session.options;
		if (pr2 == 0) {
			if (!options.midiNoteOff) {
				return VOLUME_IGNORED; // note off is not recognized
			}
			return 0; // keyoff
		} else if (options.midiTouchResponse) {
			int vol = options.midiVolumeOffset + pr2 / 8; // dynamics
			if (vol == 0) {
				vol++; // vol=1
			} else if (vol > 15) {
				vol = 15;
			}
			song.setVolume(vol);
			return vol;
		} else {
			return song.getVolume();
		}
	}

	/**
	 * The recording part shared by channel 1 ({@code Prove_midi}/
	 * {@code NextLine_midi}) and channel 16 outside {@code MIDI_CH15_MODE}
	 * ({@code Prove_midi_test}/{@code NextLine_midi_test}): not in the TRACKS
	 * part, in a prove mode or with Shift/Ctrl held the note is only played;
	 * else a note is recorded at the cursor (or quantized during follow-play
	 * in the first half of a line), a note off deletes the last recorded
	 * note; the cursor moves down unless following; the note is played on
	 * {@code playTrack} (channel 1: the active column, with the stereo twin
	 * in {@code JAM_STEREO_MODE}/Ctrl; channel 16: the held-keys track).
	 * {@code last[lastIndex]} is the channel's "last note" slot.
	 */
	private void recordOrPlay(int note, int vol, int playTrack, int[] last, int lastIndex, boolean channel1) {
		Song song = session.song;
		UiState ui = session.uiState;
		int activeInstr = song.getActiveInstr();
		boolean play;
		if (ui.activePart != Part.PART_TRACKS || ui.editMode.isProveMode() || ui.shiftKey || ui.controlKey) {
			play = true; // play notes but do not record them if the active screen is not TRACKS, or if any other PROVE combo is detected
		} else {
			play = false;
			if (vol > 0) {
				// volume > 0 => write note
				// Quantization
				if (isPlayingAndFollowing() && (song.getSpeeda() < (song.getSpeed() / 2))) {
					song.setQuantization(note, activeInstr, vol);
					last[lastIndex] = note; // see below
					if (!channel1) {
						midi.noteVolumeOnChannel[lastIndex] = vol; // volume
						midi.instrumentOnChannel[lastIndex] = activeInstr; // instrument numbers
					}
				} // end Q
				else if (song.trackSetNoteInstrVol(note, activeInstr, vol, ui.respectVolume, session.undo)) {
					songInput.blockDeselect();
					last[lastIndex] = note; // last key pressed on this midi channel
					if (!channel1) {
						midi.noteVolumeOnChannel[lastIndex] = vol; // volume
						midi.instrumentOnChannel[lastIndex] = activeInstr; // instrument numbers
					}
					if (ui.respectVolume) {
						int v = song.trackGetVol();
						if (v >= 0 && v <= Tracks.MAXVOLUME) {
							vol = v;
						}
					}
					nextLine();
					play = true;
				}
			} else {
				// volume = 0 => noteOff => delete note and write only volume 0
				if (last[lastIndex] == note) { // is it really the last one pressed?
					if (isPlayingAndFollowing() && (song.getSpeeda() < (song.getSpeed() / 2))) { // speeda - "m_speed < m_speed / 2" was always false in C++ until 2026-09-29
						song.setQuantization(-2, -1, -1);
					} else if (song.trackSetNoteActualInstrVol(-1, ui.respectVolume, session.undo) && song.trackSetVol(0, session.undo)) {
						nextLine();
						play = true;
					}
				}
			}
		}
		if (play) {
			song.setPlayPressedTonesTNIV(playTrack, note, activeInstr, vol);
			if (channel1 && (ui.editMode == EditMode.JAM_STEREO_MODE || ui.controlKey) && song.isStereo(session.tracks4_8)) { // with control or in prove2 => stereo test
				song.setPlayPressedTonesTNIV((playTrack + 4) & 0x07, note, activeInstr, vol);
			}
		}
	}

	/** {@code NextLine_midi}: scrolls only when there is no followplay. */
	private void nextLine() {
		if (!isPlayingAndFollowing()) {
			songInput.trackDown(songInput.skipLines(), true);
		}
	}

	private boolean isPlayingAndFollowing() {
		return songInput.isPlayingAndFollowing();
	}

	/** The {@code NoteOFF:} label of channels 2-9: remember and play the note on the channel's Atari track with the channel's instrument. */
	private void multitimbralNote(int atc, int chn, int note, int vol) {
		midi.lastNoteOnChannel[1 + atc] = note;
		midi.noteVolumeOnChannel[1 + atc] = vol;
		int ins = midi.instrumentOnChannel[chn];
		session.song.setPlayPressedTonesTNIV(atc, note, ins, vol);
	}

	/** {@code MIDISystemReset:} reinit RMT routines and forget the notes of channels 2-16 (1-15: "from 1, because it is MULTITIMBRAL 2-16"). */
	private void systemReset() {
		session.atariTrackerDriver.init(); // reinit RMT routines
		for (int i = 1; i < 16; i++) {
			midi.lastNoteOnChannel[i] = -1; // last pressed keys on each channel
			midi.noteVolumeOnChannel[i] = 0; // volume
			midi.instrumentOnChannel[i] = 0; // instrument numbers
		}
	}

	// ---- channel 16 (chn 15) control changes: the controller's keys, wheel, slider and knobs ----

	private static final int AUDF = PokeyController.AUDF; // the driver's shadow registers, shared with the Pokey Explorer
	private static final int AUDC = PokeyController.AUDC;
	private static final int AUDCTL = PokeyController.AUDCTL;
	private static final int SKCTL = PokeyController.SKCTL;

	private void controlChangeCh15(int pr1, int pr2, byte[] memory) {
		Song song = session.song;
		int o = chOffset ? 2 : 0;
		// The knobs 71-78 are the "SPECIAL MIDI CH15 MODE": in C++ their cases sat inside an "if (IsEditMode(MIDI_CH15_MODE))" that a
		// switch jump never evaluates, so they worked in every mode - made effective in both programs on 2026-09-29.
		if (pr1 >= 71 && pr1 <= 78 && session.uiState.editMode != EditMode.MIDI_CH15_MODE) {
			return;
		}
		switch (pr1) {
		case 1: // Modulation wheel
			modWheel = (pr2 - 64) / 8;
			break;

		case 7: // volume slider
			volSlider = pr2 / 8;
			if (volSlider == 0) {
				volSlider++;
			}
			if (volSlider > 15) {
				volSlider = 15;
			}
			song.setVolume(volSlider);
			break;

		case 115: // LOOP key
			if (pr2 == 0) {
				break; // no key press
			}
			song.play(PlayMode.PLAY_TRACK, song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
			break;

		case 116: // STOP key
			if (pr2 == 0) {
				break; // no key press
			}
			song.stop(session.undo);
			break;

		case 117: // PLAY key
			if (pr2 == 0) {
				break; // no key press
			}
			song.play(PlayMode.PLAY_SONG, song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
			break;

		case 118: // REC key
			if (pr2 == 0) {
				break; // no key press
			}
			session.uiState.switchEditMode(EditMode.MIDI_CH15_MODE, song.isStereo(session.tracks4_8));
			break;

		case 123:
			if (pr2 == 0) {
				break; // no key press
			}
			song.stop(session.undo);
			systemReset();
			break;

		// SPECIAL MIDI CH15 MODE (see the check above)
		case 71: // Knob C1, AUDF0/AUDF2 upper 4 bits
			memory[AUDF + o] = (byte) ((memory[AUDF + o] & 0x0F) | (pr2 << 4));
			break;

		case 72: // Knob C2, AUDF1/AUDF3 upper 4 bits
			memory[AUDF + 1 + o] = (byte) ((memory[AUDF + 1 + o] & 0x0F) | (pr2 << 4));
			break;

		case 73: // Knob C3, AUDC0/AUDC2 volume
			memory[AUDC + o] = (byte) ((memory[AUDC + o] & 0xF0) | pr2);
			break;

		case 74: // Knob C4, AUDC1/AUDC3 volume
			memory[AUDC + 1 + o] = (byte) ((memory[AUDC + 1 + o] & 0xF0) | pr2);
			break;

		case 75: // Knob C5, AUDF0/AUDF2 lower 4 bits
			memory[AUDF + o] = (byte) ((memory[AUDF + o] & 0xF0) | pr2);
			break;

		case 76: // Knob C6, AUDF1/AUDF3 lower 4 bits
			memory[AUDF + 1 + o] = (byte) ((memory[AUDF + 1 + o] & 0xF0) | pr2);
			break;

		case 77: // Knob C7, AUDC0/AUDC2 distortion
			memory[AUDC + o] = (byte) ((memory[AUDC + o] & 0x0F) | ((pr2 * 2) << 4));
			break;

		case 78: // Knob C8, AUDC1/AUDC3 distortion
			memory[AUDC + 1 + o] = (byte) ((memory[AUDC + 1 + o] & 0x0F) | ((pr2 * 2) << 4));
			break;

		default:
			// do nothing
			break;
		}
	}

	// ---- channel 10 (chn 9) note on in MIDI_CH15_MODE: the drumpads toggle AUDCTL/SKCTL bits ----

	private void drumpad(int pr1, int pr2, byte[] memory) {
		if (pr2 == 0) {
			return; // no key press (every pad checks it)
		}
		switch (pr1) {
		case 60 -> memory[AUDCTL] ^= 0x04; // drumpad 1, toggle High Pass Filter in ch1+3
		case 62 -> memory[AUDCTL] ^= 0x02; // drumpad 2, toggle High Pass Filter in ch2+4
		case 66 -> memory[AUDCTL] ^= chOffset ? 0x20 : 0x40; // drumpad 3, toggle 1.79mHz mode in the respective channels
		case 70 -> memory[AUDCTL] ^= chOffset ? 0x08 : 0x10; // drumpad 4, toggle Join 16-bit mode in the respective channels
		case 74 -> chOffset = !chOffset; // drumpad 5, select the POKEY channels 1 and 2 or 3 and 4
		case 69 -> { // drumpad 6, reset all AUDCTL and SKCTL bits
			memory[SKCTL] = 0x03;
			memory[AUDCTL] = 0x00;
		}
		case 75 -> memory[SKCTL] = (byte) ((memory[SKCTL] & 0xFF) == 0x03 ? 0x8B : 0x03); // drumpad 7, toggle Two-Tone filter
		case 73 -> memory[AUDCTL] ^= 0x01; // drumpad 8, toggle 15kHz mode
		default -> {
			// do nothing
		}
		}
	}

	// ---- channel 16 (chn 15) notes in MIDI_CH15_MODE: direct POKEY notes from the driver's note tables ("TESTING HARDCODED DATA" in C++) ----

	private void directPokeyNote(int cmd, int pr1, byte[] memory) {
		Song song = session.song;
		int note = pr1; // direct MIDI note mapping, for easier tests
		int vol = song.getVolume(); // direct volume value taken from the one of active instrument in memory, controlled by the volume slider
		int track = 0;

		int midiAudctl; // AUDCTL without any special effect, default 64khz clock
		int midiAudc = 0x00; // AUDC, for the Distortion and Volume
		int midiAudf; // AUDF, for the frequency

		if (note > 63) {
			return; // crossing the boundary of the older table, so let's ignore it for now
		}

		if (cmd == 0xC0) {
			midiDistortion = (pr1 % 8) * 2;
			return;
		}

		// MIDI NOTE OFF events
		if (cmd == 0x80) {
			heldKeys--;
			if (heldKeys < 0) {
				heldKeys = 0; // if by any mean the count is desynced, force it to be 0
			}
			track = (song.getActiveColumn() + heldKeys) % 4; // offset to the previous channel
			for (int i = 0; i < 4; i++) {
				if (note == midi.lastNoteOnChannel[i]) {
					track = i; // if there is a match the correct channel will be used
					midi.lastNoteOnChannel[track] = -1; // note
					midi.noteVolumeOnChannel[track] = 0; // volume
					midi.instrumentOnChannel[track] = 0; // instrument numbers
					break;
				}
			}
		}

		// MIDI NOTE ON events
		if (cmd == 0x90) {
			track = (song.getActiveColumn() + heldKeys) % 4;
			heldKeys++;
			for (int i = 0; i < 4; i++) {
				if (midi.lastNoteOnChannel[i] == -1) {
					track = i; // if there is a match the first empty channel found will be used
					midi.lastNoteOnChannel[track] = note; // note
					midi.noteVolumeOnChannel[track] = vol; // volume
					midi.instrumentOnChannel[track] = midiDistortion; // instrument numbers to set the Distortion lol
					break;
				}
			}
		}

		midiAudc |= (midi.instrumentOnChannel[track] << 4) & 0xFF; // force Distortion based on instrument to AUDC
		midiAudctl = memory[AUDCTL] & 0xFF;

		boolean clock15 = (midiAudctl & 0x01) != 0;
		boolean join34 = (midiAudctl & 0x08) != 0;
		boolean join12 = (midiAudctl & 0x10) != 0;
		boolean ch3_179 = (midiAudctl & 0x20) != 0;
		boolean ch1_179 = (midiAudctl & 0x40) != 0;

		// combined modes for some special output...
		boolean join16bit = (join12 && ch1_179 && (track == 1 || track == 5)) || (join34 && ch3_179 && (track == 3 || track == 7));
		boolean clock179 = (ch1_179 && (track == 0 || track == 4)) || (ch3_179 && (track == 2 || track == 6));
		if (join16bit || clock179) {
			clock15 = false; // override, these 2 take priority over 15khz mode
		}

		if (ch1_179 && ch3_179) {
			// force only valid 1.79mhz channels even if the current track doesn't support it, if both are enabled but not in the right channel
			if (track > 0 && track < 2) {
				track = 2;
			} else if (track > 2) {
				track = 0;
			}
			clock179 = true;
		}

		// what is the distortion? must be known to set the right note table
		// (the 64 kHz tables of the driver: $B000 distortion 2, $B100 distortion 4 (and the default), $B200 distortion A/C, $B300 distortion C bass;
		// +$40 the 1.79 MHz tables; $B480/$B4C0 the 15 kHz tables - PAGE_EXTRA_0)
		switch (midiAudc & 0xF0) {
		case 0x20:
		case 0x60:
			if (clock179) {
				midiAudf = memory[0xB040 + note];
			} else if (clock15) {
				midiAudf = defaultTableNote(memory, note, clock179, clock15);
			} else {
				midiAudf = memory[0xB000 + note];
			}
			break;

		case 0xC0:
			if (clock179) {
				midiAudf = memory[0xB240 + note];
			} else if (clock15) {
				midiAudf = memory[0xB4C0 + note];
			} else {
				midiAudf = memory[0xB200 + note];
			}
			break;

		case 0xE0:
			midiAudc = 0xC0; // Distortion C bass E
			if (clock179) {
				midiAudf = memory[0xB340 + note];
			} else if (clock15) {
				midiAudf = memory[0xB4C0 + note];
			} else {
				midiAudf = memory[0xB300 + note];
			}
			break;

		case 0x00:
		case 0x40:
		case 0x80:
		case 0xA0:
		default:
			midiAudf = defaultTableNote(memory, note, clock179, clock15);
			break;
		}

		midiAudc |= midi.noteVolumeOnChannel[track]; // also merge the volume into it

		// DIRECT MEMORY WRITE
		memory[AUDF + track] = (byte) midiAudf; // AUDF address + offset used by SetPokey
		memory[AUDC + track] = (byte) midiAudc; // AUDC address + offset used by SetPokey
	}

	/** The {@code case_default:} table (distortion 4 and every unlisted one): $B140 at 1.79 MHz, $B480 at 15 kHz, else $B100. */
	private static int defaultTableNote(byte[] memory, int note, boolean clock179, boolean clock15) {
		if (clock179) {
			return memory[0xB140 + note];
		} else if (clock15) {
			return memory[0xB480 + note];
		} else {
			return memory[0xB100 + note];
		}
	}
}
