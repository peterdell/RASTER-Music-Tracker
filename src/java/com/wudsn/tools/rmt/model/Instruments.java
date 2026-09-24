package com.wudsn.tools.rmt.model;

import java.util.Arrays;

/**
 * Ported from CInstruments (src/cpp/Instruments.h, InstrumentsCore.cpp,
 * Instruments.cpp, InstrumentsAtaFormat.cpp) - the already-tested subset
 * only, matching the scoping discipline already established for
 * {@link Tuning} and {@link Tracks}: port what {@code InstrumentsTests.cpp}
 * already characterizes, defer the rest.
 *
 * <p><b>Deferred, untested in C++</b>: {@code CheckInstrumentParameters}/
 * {@code RecalculateFlag}/{@code CalculateNotEmpty}/{@code GetNote}
 * (InstrumentsCore.cpp - simple, but no existing test coverage to port
 * against) and {@code GetFrequency} (also needs the not-yet-ported
 * {@code CAtari}'s memory buffer). <b>Deferred, needs I/O infrastructure not
 * yet ported</b>: {@code Update}/{@code SaveAll}/{@code LoadAll}/
 * {@code SaveInstrument}/{@code LoadInstrument} (IO_Instruments.cpp -
 * untested stream I/O; {@code Update()} needs {@code CAtari}). <b>Deferred,
 * GUI</b>: {@code SetCanvas}/{@code DrawInstrument}/{@code DrawName}/
 * {@code DrawParameter}/{@code DrawEnv}/{@code DrawNoteTableValue}/
 * {@code GetGUIArea}/{@code CursorGoto} - not part of the model layer.
 * {@code GetInstrumentsAll()} is also skipped: it's a zero-copy
 * reinterpret-cast view in C++ (unlike {@code GetTracksAll}/
 * {@code SetTracksAll}'s real deep copy), untested, and Java has no
 * equivalent aliasing mechanism to design around without inventing new,
 * uncharacterized behavior.
 *
 * <p><b>Explicit parameters instead of C++ globals</b>, matching the
 * pattern already established for {@link Tuning}: {@code g_tracks4_8}
 * (mono/stereo envelope-volume packing) becomes an explicit {@code stereo}
 * parameter on {@link #setEnvelopeVolume}/{@link #instrToAta}/
 * {@link #ataToInstr}/{@link #ataV0ToInstr}; {@code g_keyboard_
 * RememberOctavesAndVolumes} becomes an explicit parameter on
 * {@link #memorizeOctaveAndVolume}/{@link #rememberOctaveAndVolume}.
 *
 * <p><b>No hardware/Atari-memory side effects</b>: C++'s
 * {@code ClearInstrument()} calls {@code g_AtariTrackerDriver->
 * InstrumentTurnOff()} (silences any channel currently playing this
 * instrument) and both it and {@code SetEnvelopeVolume()} call
 * {@code Update()} (writes the instrument into "the emulated Atari
 * memory"). Neither has a Java equivalent yet, since no live-playback
 * subsystem has been ported - not a behavior difference to characterize,
 * since the concept these calls act on doesn't exist here yet either.
 */
public final class Instruments {

	public static final int INSTRSNUM = 64;

	// From SongTypes.h (not yet ported) - duplicated here with the same
	// value pending that class's own Java port.
	private static final int MAXVOLUME = 15;

	private final Instrument[] instrument;

	public Instruments() {
		instrument = new Instrument[INSTRSNUM];
		for (int i = 0; i < INSTRSNUM; i++) {
			instrument[i] = new Instrument();
		}
	}

	public void initInstruments() {
		for (int i = 0; i < INSTRSNUM; i++) {
			clearInstrument(i);
		}
	}

	/**
	 * Reset an instrument to startup defaults.
	 *
	 * @param instrNr index of the instrument, 0-63
	 */
	public void clearInstrument(int instrNr) {
		Instrument ai = getInstrument(instrNr);
		if (ai == null) {
			return;
		}

		// Clear everything (matches C++'s memset(instrument, 0, sizeof(TInstrument)))
		ai.activeEditSection = InstrumentSection.NONE;
		Arrays.fill(ai.name, '\0');
		ai.editNameCursorPos = 0;
		Arrays.fill(ai.parameters, 0);
		ai.editParameterNr = 0;
		for (int[] row : ai.envelope) {
			Arrays.fill(row, 0);
		}
		ai.editEnvelopeX = 0;
		ai.editEnvelopeY = 0;
		Arrays.fill(ai.noteTable, 0);
		ai.editNoteTableCursorPos = 0;
		ai.octave = 0;
		ai.volume = 0;
		ai.displayHintFlags = 0;

		// Init the name "Instrument XX"
		String initialName = String.format("Instrument %02X", instrNr);
		for (int i = 0; i < ai.name.length; i++) {
			ai.name[i] = i < initialName.length() ? initialName.charAt(i) : ' ';
		}

		// Set some initial values
		ai.activeEditSection = InstrumentSection.ENVELOPE; // Activate on the Envelope, so testing instruments wouldn't cause accidental rename
		ai.editNameCursorPos = 0; // 0 character name
		ai.editParameterNr = Instrument.PAR_ENV_LENGTH; // Envelope length is the default parameter to edit
		ai.editEnvelopeX = 0;
		ai.editEnvelopeY = 1; // Volume left
		ai.editNoteTableCursorPos = 0; // 0 element in the table
		ai.octave = 0;
		ai.volume = MAXVOLUME;

		// No hardware side effect / Atari-memory update here - see class javadoc.
	}

	/**
	 * Set the volume level for a channel.
	 *
	 * @param instr instrument number
	 * @param right true - then use the stereo/right channel
	 * @param px X position in the envelope
	 * @param newVolume volume level to set
	 * @param stereo whether the current track layout is stereo (C++ reads this from the g_tracks4_8 global)
	 */
	public void setEnvelopeVolume(int instr, boolean right, int px, int newVolume, boolean stereo) {
		Instrument ti = getInstrument(instr);
		if (ti == null) {
			return;
		}

		// Validate
		if (px < 0 || px >= ti.parameters[Instrument.PAR_ENV_LENGTH] + 1) {
			return;
		}
		if (newVolume < 0 || newVolume > 15) {
			return;
		}

		int ep = (right && stereo) ? EnvelopeParameter.VOLUMER : EnvelopeParameter.VOLUMEL;
		ti.envelope[px][ep] = newVolume;

		// No Atari-memory update here - see class javadoc.
	}

	/**
	 * Save octave and volume info in the instrument.
	 *
	 * @param instr instrument number
	 * @param oct last used octave (ignored if negative)
	 * @param vol last used volume (ignored if negative)
	 * @param rememberOctavesAndVolumes C++ reads this from the g_keyboard_RememberOctavesAndVolumes global
	 */
	public void memorizeOctaveAndVolume(int instr, int oct, int vol, boolean rememberOctavesAndVolumes) {
		Instrument ti = getInstrument(instr);
		if (ti == null) {
			return;
		}

		if (rememberOctavesAndVolumes) {
			if (oct >= 0) {
				ti.octave = oct;
			}
			if (vol >= 0) {
				ti.volume = vol;
			}
		}
	}

	/**
	 * Load octave and volume info from the instrument. C++'s {@code int& oct, int& vol} output parameters become the returned {@link OctaveAndVolume}; when disabled, the given {@code octave}/{@code volume} are returned unchanged (matching C++ leaving the caller's variables untouched).
	 *
	 * @param instr instrument number
	 * @param octave the caller's current octave, returned unchanged if disabled or the instrument is invalid
	 * @param volume the caller's current volume, returned unchanged if disabled or the instrument is invalid
	 * @param rememberOctavesAndVolumes C++ reads this from the g_keyboard_RememberOctavesAndVolumes global
	 */
	public OctaveAndVolume rememberOctaveAndVolume(int instr, int octave, int volume, boolean rememberOctavesAndVolumes) {
		Instrument ti = getInstrument(instr);
		if (ti != null && rememberOctavesAndVolumes) {
			return new OctaveAndVolume(ti.octave, ti.volume);
		}
		return new OctaveAndVolume(octave, volume);
	}

	/** C++'s {@code int& oct, int& vol} output parameters for {@link #rememberOctaveAndVolume}. */
	public record OctaveAndVolume(int octave, int volume) {
	}

	/**
	 * Encode an instrument into its compact on-Atari byte representation.
	 *
	 * @param instr instrument number
	 * @param ata buffer to write into
	 * @param stereo whether the current track layout is stereo (C++ reads this from the g_tracks4_8 global)
	 * @return the data length of the encoded instrument
	 */
	public int instrToAta(int instr, byte[] ata, boolean stereo) {
		Instrument ai = getInstrument(instr);
		int[] par = ai.parameters;

		final int INSTRPAR = 12; // 12th byte starts the table

		int tablelast = par[Instrument.PAR_TBL_LENGTH] + INSTRPAR;
		ata[0] = (byte) tablelast;
		ata[1] = (byte) (par[Instrument.PAR_TBL_GOTO] + INSTRPAR);
		ata[2] = (byte) (par[Instrument.PAR_ENV_LENGTH] * 3 + tablelast + 1); // behind the table is the envelope
		ata[3] = (byte) (par[Instrument.PAR_ENV_GOTO] * 3 + tablelast + 1);

		ata[4] = (byte) ((par[Instrument.PAR_TBL_TYPE] << 7) | (par[Instrument.PAR_TBL_MODE] << 6) | (par[Instrument.PAR_TBL_SPEED]));
		ata[5] = (byte) (par[Instrument.PAR_AUDCTL_15KHZ] | (par[Instrument.PAR_AUDCTL_HPF_CH2] << 1) | (par[Instrument.PAR_AUDCTL_HPF_CH1] << 2)
				| (par[Instrument.PAR_AUDCTL_JOIN_3_4] << 3) | (par[Instrument.PAR_AUDCTL_JOIN_1_2] << 4) | (par[Instrument.PAR_AUDCTL_179_CH3] << 5)
				| (par[Instrument.PAR_AUDCTL_179_CH1] << 6) | (par[Instrument.PAR_AUDCTL_POLY9] << 7));
		ata[6] = (byte) par[Instrument.PAR_VOL_FADEOUT];
		ata[7] = (byte) (par[Instrument.PAR_VOL_MIN] << 4);
		ata[8] = (byte) par[Instrument.PAR_DELAY];
		ata[9] = (byte) (par[Instrument.PAR_VIBRATO] & 0x03);
		ata[10] = (byte) par[Instrument.PAR_FREQ_SHIFT];
		ata[11] = 0; // unused, for now

		// the entire table length gets the data copied
		for (int i = 0; i <= par[Instrument.PAR_TBL_LENGTH]; i++) {
			ata[INSTRPAR + i] = (byte) ai.noteTable[i];
		}

		// envelope is behind the table
		int len = par[Instrument.PAR_ENV_LENGTH];
		for (int i = 0, j = tablelast + 1; i <= len; i++, j += 3) {
			int[] env = ai.envelope[i];
			ata[j] = (byte) (stereo ? (env[EnvelopeParameter.VOLUMER] << 4) | (env[EnvelopeParameter.VOLUMEL]) // stereo
					: (env[EnvelopeParameter.VOLUMEL] << 4) | (env[EnvelopeParameter.VOLUMEL])); // mono, VOLUME R = VOLUME L

			ata[j + 1] = (byte) ((env[EnvelopeParameter.FILTER] << 7) | (env[EnvelopeParameter.COMMAND] << 4) // 0-7
					| (env[EnvelopeParameter.DISTORTION]) // 0,2,4,6,8,A,C,E
					| (env[EnvelopeParameter.PORTAMENTO]));
			ata[j + 2] = (byte) ((env[EnvelopeParameter.X] << 4) | (env[EnvelopeParameter.Y]));
		}
		return tablelast + 1 + (len + 1) * 3; // returns the data length of the instrument
	}

	/** Decode an instrument using the old (pre-envelope-flags) on-Atari format. */
	public boolean ataV0ToInstr(byte[] ata, int instr, boolean stereo) {
		Instrument ai = getInstrument(instr);

		// 0-7 table
		for (int i = 0; i <= 7; i++) {
			ai.noteTable[i] = unsignedByte(ata, i);
		}

		// 8 ;instr len 0-31 *8, table len 0-7 (iiii ittt)
		int[] par = ai.parameters;
		int len = par[Instrument.PAR_ENV_LENGTH] = unsignedByte(ata, 8) >> 3;
		par[Instrument.PAR_TBL_LENGTH] = unsignedByte(ata, 8) & 0x07;
		par[Instrument.PAR_ENV_GOTO] = unsignedByte(ata, 9) >> 3;
		par[Instrument.PAR_TBL_GOTO] = unsignedByte(ata, 9) & 0x07;
		par[Instrument.PAR_TBL_TYPE] = unsignedByte(ata, 10) >> 7;
		par[Instrument.PAR_TBL_MODE] = (unsignedByte(ata, 10) >> 6) & 0x01;
		par[Instrument.PAR_TBL_SPEED] = unsignedByte(ata, 10) & 0x3f;
		par[Instrument.PAR_VOL_FADEOUT] = unsignedByte(ata, 11);
		par[Instrument.PAR_VOL_MIN] = unsignedByte(ata, 12) >> 4;
		par[Instrument.PAR_AUDCTL_15KHZ] = unsignedByte(ata, 12) & 0x01;
		par[Instrument.PAR_AUDCTL_HPF_CH2] = 0;
		par[Instrument.PAR_AUDCTL_HPF_CH1] = 0;
		par[Instrument.PAR_AUDCTL_JOIN_3_4] = 0;
		par[Instrument.PAR_AUDCTL_JOIN_1_2] = 0;
		par[Instrument.PAR_AUDCTL_179_CH3] = 0;
		par[Instrument.PAR_AUDCTL_179_CH1] = 0;
		par[Instrument.PAR_AUDCTL_POLY9] = (unsignedByte(ata, 12) >> 1) & 0x01;

		par[Instrument.PAR_DELAY] = unsignedByte(ata, 13);
		par[Instrument.PAR_VIBRATO] = unsignedByte(ata, 14) & 0x03;
		par[Instrument.PAR_FREQ_SHIFT] = unsignedByte(ata, 15);

		for (int i = 0, j = 16; i <= len; i++, j += 3) {
			int[] env = ai.envelope[i];
			env[EnvelopeParameter.VOLUMER] = stereo ? (unsignedByte(ata, j) >> 4) : (unsignedByte(ata, j) & 0x0f); // if mono, then VOLUME R = VOLUME L
			env[EnvelopeParameter.VOLUMEL] = unsignedByte(ata, j) & 0x0f;
			env[EnvelopeParameter.FILTER] = unsignedByte(ata, j + 1) >> 7;
			env[EnvelopeParameter.COMMAND] = (unsignedByte(ata, j + 1) >> 4) & 0x07;
			env[EnvelopeParameter.DISTORTION] = unsignedByte(ata, j + 1) & 0x0e; // even numbers 0,2,4, .., 14
			env[EnvelopeParameter.PORTAMENTO] = unsignedByte(ata, j + 1) & 0x01;
			env[EnvelopeParameter.X] = unsignedByte(ata, j + 2) >> 4;
			env[EnvelopeParameter.Y] = unsignedByte(ata, j + 2) & 0x0f;
		}
		return true;
	}

	/**
	 * Load an instrument from a binary location and parse the data.
	 *
	 * @param mem start of the instrument definition structure
	 * @param instrumentNr which instrument # is this
	 * @param stereo whether the current track layout is stereo (C++ reads this from the g_tracks4_8 global)
	 */
	public boolean ataToInstr(byte[] mem, int instrumentNr, boolean stereo) {
		Instrument ai = getInstrument(instrumentNr);

		int noteTableLength = unsignedByte(mem, 0) - 12;
		int noteTableGoto = unsignedByte(mem, 1) - 12;
		int envelopeLength = (unsignedByte(mem, 2) - (unsignedByte(mem, 0) + 1)) / 3;
		int envelopeGoto = (unsignedByte(mem, 3) - (unsignedByte(mem, 0) + 1)) / 3;

		// Check the scope of the tables and envelope
		if (noteTableLength >= Instrument.NOTE_TABLE_MAX_LEN || noteTableGoto > noteTableLength
				|| envelopeLength >= Instrument.ENVELOPE_MAX_COLUMNS || envelopeGoto > envelopeLength) {
			// Note table and envelope parameters are out of bounds
			return false;
		}

		// Transfer the Atari memory data into the instrument structures
		int[] par = ai.parameters;
		par[Instrument.PAR_TBL_LENGTH] = noteTableLength;
		par[Instrument.PAR_TBL_GOTO] = noteTableGoto;
		par[Instrument.PAR_ENV_LENGTH] = envelopeLength;
		par[Instrument.PAR_ENV_GOTO] = envelopeGoto;
		// Set the Note table speed, type and mode. 0 <= speed <= 63, type
		par[Instrument.PAR_TBL_TYPE] = unsignedByte(mem, 4) >> 7; // 0 = notes, 1 = frequencies
		par[Instrument.PAR_TBL_MODE] = (unsignedByte(mem, 4) >> 6) & 0x01; // 0 = set, 1 = add
		par[Instrument.PAR_TBL_SPEED] = unsignedByte(mem, 4) & 0x3f; // play speed
		// Set the AUDCTL register
		par[Instrument.PAR_AUDCTL_15KHZ] = unsignedByte(mem, 5) & 0x01;
		par[Instrument.PAR_AUDCTL_HPF_CH2] = (unsignedByte(mem, 5) >> 1) & 0x01;
		par[Instrument.PAR_AUDCTL_HPF_CH1] = (unsignedByte(mem, 5) >> 2) & 0x01;
		par[Instrument.PAR_AUDCTL_JOIN_3_4] = (unsignedByte(mem, 5) >> 3) & 0x01;
		par[Instrument.PAR_AUDCTL_JOIN_1_2] = (unsignedByte(mem, 5) >> 4) & 0x01;
		par[Instrument.PAR_AUDCTL_179_CH3] = (unsignedByte(mem, 5) >> 5) & 0x01;
		par[Instrument.PAR_AUDCTL_179_CH1] = (unsignedByte(mem, 5) >> 6) & 0x01;
		par[Instrument.PAR_AUDCTL_POLY9] = (unsignedByte(mem, 5) >> 7) & 0x01;

		par[Instrument.PAR_VOL_FADEOUT] = unsignedByte(mem, 6);
		par[Instrument.PAR_VOL_MIN] = unsignedByte(mem, 7) >> 4;
		par[Instrument.PAR_DELAY] = unsignedByte(mem, 8);
		par[Instrument.PAR_VIBRATO] = unsignedByte(mem, 9) & 0x03;
		par[Instrument.PAR_FREQ_SHIFT] = unsignedByte(mem, 10);

		// 0-31 table
		for (int i = 0; i <= par[Instrument.PAR_TBL_LENGTH]; i++) {
			ai.noteTable[i] = unsignedByte(mem, 12 + i);
		}

		// Envelope
		int ptrEnvelopeEntry = unsignedByte(mem, 0) + 1; // location in Atari memory where envelope data is parsed from

		for (int i = 0; i <= par[Instrument.PAR_ENV_LENGTH]; i++, ptrEnvelopeEntry += 3) {
			// Take the 3 bytes of envelope data and parse them into the 8 data fields
			int[] env = ai.envelope[i];
			env[EnvelopeParameter.VOLUMER] = stereo ? (unsignedByte(mem, ptrEnvelopeEntry) >> 4) : (unsignedByte(mem, ptrEnvelopeEntry) & 0x0f); // if mono, then VOLUME R = VOLUME L
			env[EnvelopeParameter.VOLUMEL] = unsignedByte(mem, ptrEnvelopeEntry) & 0x0f;

			env[EnvelopeParameter.FILTER] = unsignedByte(mem, ptrEnvelopeEntry + 1) >> 7;
			env[EnvelopeParameter.COMMAND] = (unsignedByte(mem, ptrEnvelopeEntry + 1) >> 4) & 0x07;
			env[EnvelopeParameter.DISTORTION] = unsignedByte(mem, ptrEnvelopeEntry + 1) & 0x0e; // even numbers 0,2,4,...E
			env[EnvelopeParameter.PORTAMENTO] = unsignedByte(mem, ptrEnvelopeEntry + 1) & 0x01;

			env[EnvelopeParameter.X] = unsignedByte(mem, ptrEnvelopeEntry + 2) >> 4;
			env[EnvelopeParameter.Y] = unsignedByte(mem, ptrEnvelopeEntry + 2) & 0x0f;
		}
		return true;
	}

	private static int unsignedByte(byte[] mem, int index) {
		return mem[index] & 0xFF;
	}

	public boolean isValidInstrument(int instr) {
		return instr >= 0 && instr < INSTRSNUM;
	}

	public byte getFlag(int instr) {
		return isValidInstrument(instr) ? (byte) instrument[instr].displayHintFlags : (byte) -1;
	}

	public byte getParameter(int instr, int param) {
		return isValidInstrument(instr) ? (byte) instrument[instr].parameters[param] : (byte) -1;
	}

	public int getParameterNumber(int instr) {
		return isValidInstrument(instr) ? instrument[instr].editParameterNr : -1;
	}

	public InstrumentSection getActiveEditSection(int instr) {
		return isValidInstrument(instr) ? instrument[instr].activeEditSection : InstrumentSection.NONE;
	}

	public int getNameCursorPosition(int instr) {
		return isValidInstrument(instr) ? instrument[instr].editNameCursorPos : -1;
	}

	public char[] getName(int instr) {
		return isValidInstrument(instr) ? instrument[instr].name : null;
	}

	public Instrument getInstrument(int instr) {
		return isValidInstrument(instr) ? instrument[instr] : null;
	}
}
