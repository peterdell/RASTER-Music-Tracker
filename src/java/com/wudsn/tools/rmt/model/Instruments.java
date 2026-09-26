package com.wudsn.tools.rmt.model;

import java.util.Arrays;

/**
 * Ported from CInstruments (src/cpp/Instruments.h, InstrumentsCore.cpp,
 * Instruments.cpp, InstrumentsAtaFormat.cpp) - the already-tested subset
 * only, matching the scoping discipline already established for
 * {@link Tuning} and {@link Tracks}. {@code CheckInstrumentParameters}/
 * {@code RecalculateFlag}/{@code CalculateNotEmpty}/{@code GetNote}/
 * {@code GetFrequency} were originally deferred for having no C++ test
 * coverage to port against; backfilled in {@code InstrumentsTests.cpp} and
 * ported here once that gave real characterization values to verify
 * against.
 *
 * <p>{@link #getFrequency} takes the emulated Atari memory as an explicit
 * {@code byte[]} parameter instead of reading it from a {@code CAtari}
 * instance - {@code CAtari} itself isn't ported yet, and (matching
 * {@link Tuning#generateTable}'s own precedent) a raw buffer is all this
 * method actually needs.
 *
 * <p><b>Deferred, needs I/O infrastructure not
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
 * {@code Update()}. {@code InstrumentTurnOff()} has no Java equivalent
 * yet, since no live-playback subsystem has been ported - not a behavior
 * difference to characterize, since the concept it acts on doesn't exist
 * here yet either. {@link #update} exists, but only for the half of
 * {@code Update()} the UI can observe (the display-hint flags) - see its
 * javadoc.
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
	 * Check the instrument parameters and adjust them to fit boundaries if needed.
	 *
	 * @param instr instrument number
	 */
	public void checkInstrumentParameters(int instr) {
		Instrument ai = getInstrument(instr);
		if (ai == null) {
			return;
		}

		// ENVELOPE len-go loop control
		if (ai.parameters[Instrument.PAR_ENV_GOTO] > ai.parameters[Instrument.PAR_ENV_LENGTH]) {
			ai.parameters[Instrument.PAR_ENV_GOTO] = ai.parameters[Instrument.PAR_ENV_LENGTH];
		}

		// TABLE len-go loop control
		if (ai.parameters[Instrument.PAR_TBL_GOTO] > ai.parameters[Instrument.PAR_TBL_LENGTH]) {
			ai.parameters[Instrument.PAR_TBL_GOTO] = ai.parameters[Instrument.PAR_TBL_LENGTH];
		}

		// check the cursor in the envelope
		if (ai.editEnvelopeX > ai.parameters[Instrument.PAR_ENV_LENGTH]) {
			ai.editEnvelopeX = ai.parameters[Instrument.PAR_ENV_LENGTH];
		}

		// check the cursor in the table
		if (ai.editNoteTableCursorPos > ai.parameters[Instrument.PAR_TBL_LENGTH]) {
			ai.editNoteTableCursorPos = ai.parameters[Instrument.PAR_TBL_LENGTH];
		}

		// something changed => Save instrument "to Atari" - NOTE: done from the outside
	}

	/**
	 * Ported from {@code CInstruments::Update()} (IO_Instruments.cpp): "the
	 * instrument was modified in some way" - C++ pushes the instrument's
	 * Atari-format bytes into the emulated Atari's memory
	 * ({@code InstrToAta} at {@code $4000 + instr * 256}) <em>and</em>
	 * {@link #recalculateFlag recalculates its display-hint flags}. Only the
	 * second half exists here: the memory write belongs to the real-time
	 * playback path, which the UI port's audio batch (B8) adds; until then
	 * the UI's info area only needs the flags, and the many callers
	 * ({@code decodeModule}, the importers, undo, paste, ...) call this
	 * exactly where C++ calls {@code Update()}.
	 */
	public void update(int instr) {
		recalculateFlag(instr);
	}

	/**
	 * Calculate some text hints for this instrument. When the instrument name is rendered there will be some hints below it.
	 *
	 * @param instr instrument number
	 */
	public void recalculateFlag(int instr) {
		Instrument ti = getInstrument(instr);
		if (ti == null) {
			return;
		}

		int flags = 0;

		// Analyse the instrument envelope for the Autofilter, Bass16 and Portamento flags
		for (int i = 0; i <= ti.parameters[Instrument.PAR_ENV_LENGTH]; i++) {
			// Autofilter?
			if (ti.envelope[i][EnvelopeParameter.FILTER] != 0) {
				flags |= Instrument.IF_FILTER;
			}

			// Bass16?
			if (ti.envelope[i][EnvelopeParameter.DISTORTION] == 6) {
				flags |= Instrument.IF_BASS16;
			}

			// Portamento?
			if (ti.envelope[i][EnvelopeParameter.PORTAMENTO] != 0) {
				flags |= Instrument.IF_PORTAMENTO;
			}
		}

		// Analyse the instrument parameters for the AUDCTL flag
		for (int i = Instrument.PAR_AUDCTL_15KHZ; i <= Instrument.PAR_AUDCTL_POLY9; i++) {
			// AUDCTL?
			if (ti.parameters[i] != 0) {
				flags |= Instrument.IF_AUDCTL;
			}
		}

		// Autofilter takes priority over Bass16 (RMT 1.28 driver only)
		if ((flags & Instrument.IF_FILTER) != 0 && (flags & Instrument.IF_BASS16) != 0) {
			flags ^= Instrument.IF_BASS16;
		}

		// Update the instrument hint flag to the new value
		ti.displayHintFlags = flags;
	}

	/**
	 * Check if an instrument is empty. Empty is defined as NO volume and all parameters are 0.
	 *
	 * @param instr instrument number
	 * @return true if the instrument has values, false if it is in default state
	 */
	public boolean calculateNotEmpty(int instr) {
		Instrument ti = getInstrument(instr);
		if (ti == null) {
			return false;
		}

		for (int i = 0; i <= ti.parameters[Instrument.PAR_ENV_LENGTH]; i++) {
			for (int j = 0; j < Instrument.ENVROWS; j++) {
				if (ti.envelope[i][j] != 0) {
					return true;
				}
			}
		}
		for (int i = 0; i < Instrument.PARCOUNT; i++) {
			if (ti.parameters[i] != 0) {
				return true;
			}
		}
		return false; // Is empty
	}

	/**
	 * Calculate the note according to distortion in the first entry in the note table.
	 *
	 * @param instr instrument number
	 * @param note which note
	 * @return the note, or -1 if instr/the resulting note is invalid
	 */
	public int getNote(int instr, int note) {
		Instrument tt = getInstrument(instr);
		if (tt == null) {
			return -1;
		}

		// Only for NOTES table
		if (tt.parameters[Instrument.PAR_TBL_TYPE] == 0) {
			// Shift notes according to table 0
			note = (note + tt.noteTable[0]) & 0xff;
		}

		// The note must be within valid boundaries
		if (!Notes.isValidNote(note)) {
			return -1;
		}
		return note;
	}

	/**
	 * Convert the note to a frequency according to distortion in first envelope column or first entry in the note table.
	 *
	 * @param instr instrument number
	 * @param note which note
	 * @param atariMemory the emulated Atari memory to read the frequency table from (C++ reads this from the g_Atari global instead)
	 * @return the frequency, or -1 if instr/the resulting note is invalid
	 */
	public int getFrequency(int instr, int note, byte[] atariMemory) {
		Instrument tt = getInstrument(instr);
		if (tt == null) {
			return -1;
		}

		// Only for NOTES table
		if (tt.parameters[Instrument.PAR_TBL_TYPE] == 0) {
			// Shift notes according to table 0
			note = (note + tt.noteTable[0]) & 0xff;
		}

		// The note must be within valid boundaries
		if (note < 0 || note >= Notes.NOTESNUM) {
			return -1;
		}

		// IMPORTANT NOTE: Tables are not set to a constant location!
		// The function technically returns valid data, otherwise
		switch (tt.envelope[0][EnvelopeParameter.DISTORTION]) {
		case 0x0C:
			return unsignedByte(atariMemory, Atari.RMT_FRQTABLES + 64 + note);
		case 0x06:
		case 0x0E:
			return unsignedByte(atariMemory, Atari.RMT_FRQTABLES + 128 + note);
		default:
			return unsignedByte(atariMemory, Atari.RMT_FRQTABLES + 192 + note);
		}
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

		// Recalc some info about the updated instrument
		update(instr);
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

	/**
	 * A real deep-copy snapshot of every instrument - see {@link InstrumentsAll}'s own javadoc for why this exists alongside (not instead of) the skipped C++ {@code GetInstrumentsAll()}/{@code SetInstrumentsAll()}.
	 */
	public void getInstrumentsAll(InstrumentsAll toInstruments) {
		for (int i = 0; i < INSTRSNUM; i++) {
			toInstruments.instruments[i].copyFrom(instrument[i]);
		}
	}

	public void setInstrumentsAll(InstrumentsAll fromInstruments) {
		for (int i = 0; i < INSTRSNUM; i++) {
			instrument[i].copyFrom(fromInstruments.instruments[i]);
		}
	}

	// --- SaveAll/LoadAll/SaveInstrument/LoadInstrument (IO_Instruments.cpp) ---
	//
	// Ported from CInstruments::SaveAll/LoadAll/SaveInstrument/LoadInstrument -
	// the TXT and RMW iotypes only (RTI - single-instrument file import/export -
	// is a separate, out-of-scope feature; see plans/JAVA_SONGEDITING_PLAN.md's
	// "IO_Instruments.cpp/IO_Tracks.cpp" write-up). {@code Update(instr)}
	// is called where C++ calls it - see update()'s javadoc for the half of
	// it that exists here.
	//
	// RMW's per-instrument fields (parameters/envelope/noteTable) are C++
	// `char` (signed byte) truncations of this port's `int` fields - writing
	// just takes the low 8 bits (ByteArrayOutputStream.write(int) already
	// does this), and reading sign-extends a raw Java `byte` back to `int`
	// (assigning a byte to an int variable does this automatically) -
	// together reproducing C++'s int<->char narrowing/widening exactly,
	// including for values >= 128 that come back negative after a round
	// trip (a real, pre-existing property of this file format, not
	// something to "fix"). The name field is the one exception - it's
	// stored as an *unsigned* 0-255 value per this port's own char[]
	// convention (matching TmcImporter/ModImporter's established treatment
	// of raw name bytes), so it's explicitly masked with `& 0xFF` on read.

	/** Mirrors C++'s explicit {@code enum class InstrumentSection : int} backing values (NONE=-1, NAME=0, PARAMETERS=1, ENVELOPE=2, NOTETABLE=3) for RMW's byte-exact serialization - {@link InstrumentSection} itself doesn't preserve them (see its own javadoc), so this mapping exists solely for this file format. */
	private static int instrumentSectionToRmw(InstrumentSection s) {
		return switch (s) {
		case NONE -> -1;
		case NAME -> 0;
		case PARAMETERS -> 1;
		case ENVELOPE -> 2;
		case NOTETABLE -> 3;
		};
	}

	private static InstrumentSection instrumentSectionFromRmw(int v) {
		return switch (v) {
		case 0 -> InstrumentSection.NAME;
		case 1 -> InstrumentSection.PARAMETERS;
		case 2 -> InstrumentSection.ENVELOPE;
		case 3 -> InstrumentSection.NOTETABLE;
		default -> InstrumentSection.NONE;
		};
	}

	/** Mirrors C++'s {@code Tshpar} (InstrumentTypes.h) - just the fields {@link #loadInstrumentTxt}/{@code saveInstrumentTxt} actually need (not the GUI display position/cursor-navigation fields, irrelevant to serialization). */
	private record ShPar(int paramIndex, String fieldName, int parameterAND, int maxParameterValue, int displayOffset) {
	}

	/** Mirrors C++'s {@code shpar[]} (InstrumentsAtaFormat.cpp) - order and values copied directly. */
	private static final ShPar[] SHPAR = { //
			new ShPar(Instrument.PAR_TBL_LENGTH, "LENGTH:", 0x1f, 0x1f, 1), //
			new ShPar(Instrument.PAR_TBL_GOTO, "GOTO:", 0x1f, 0x1f, 0), //
			new ShPar(Instrument.PAR_TBL_SPEED, "SPEED:", 0x3f, 0x3f, 1), //
			new ShPar(Instrument.PAR_TBL_TYPE, "TYPE:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_TBL_MODE, "MODE:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_ENV_LENGTH, "ENV_LENGTH:", 0x3f, 0x2f, 1), //
			new ShPar(Instrument.PAR_ENV_GOTO, "ENV_GOTO:", 0x3f, 0x2f, 0), //
			new ShPar(Instrument.PAR_VOL_FADEOUT, "FADEOUT:", 0xff, 0xff, 0), //
			new ShPar(Instrument.PAR_VOL_MIN, "VOL_MIN:", 0x0f, 0x0f, 0), //
			new ShPar(Instrument.PAR_DELAY, "EFF_DELAY:", 0xff, 0xff, 0), //
			new ShPar(Instrument.PAR_VIBRATO, "EFF_VIBRATO:", 0x03, 0x03, 0), //
			new ShPar(Instrument.PAR_FREQ_SHIFT, "EFF_FREQSHIFT:", 0xff, 0xff, 0), //
			new ShPar(Instrument.PAR_AUDCTL_15KHZ, "AUD_15KHZ:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH2, "AUD_HPF_CH2:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH1, "AUD_HPF_CH1:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_3_4, "AUD_JOIN34:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_1_2, "AUD_JOIN12:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH3, "AUD_179_CH3:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH1, "AUD_179_CH1:", 0x01, 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_POLY9, "AUD_POLY9:", 0x01, 0x01, 0), //
	};

	/** Mirrors C++'s {@code shenv[]} field names (InstrumentsAtaFormat.cpp) - index matches {@link EnvelopeParameter}'s row constants (0=VOLUMER..7=PORTAMENTO). */
	private static final String[] SHENV_FIELD_NAME = { "ENV_VOLUME_R:", "ENV_VOLUME_L:", "ENV_DISTORTION:", "ENV_COMMNAND:", "ENV_X:", "ENV_Y:", "ENV_AUTOFILTER:", "ENV_PORTAMENTO:" };

	/** Mirrors C++'s {@code shenv[].pand} (the mask applied to a parsed envelope hex digit). */
	private static final int[] SHENV_PAND = { 0x0f, 0x0f, 0x0e, 0x07, 0x0f, 0x0f, 0x01, 0x01 };

	/** Encodes every non-empty instrument's TXT representation, concatenated - the {@link #saveAllTxt} counterpart's per-instrument step. */
	private String saveInstrumentTxt(int instr) {
		Instrument ai = getInstrument(instr);
		StringBuilder s = new StringBuilder();

		String name = Song.nameToString(ai.name).stripTrailing();
		s.append(String.format("[INSTRUMENT]\n%02X: %s\n", instr, name));

		for (ShPar p : SHPAR) {
			s.append(String.format("%s %X\n", p.fieldName(), ai.parameters[p.paramIndex()] + p.displayOffset()));
		}

		s.append("TABLE: ");
		for (int j = 0; j <= ai.parameters[Instrument.PAR_TBL_LENGTH]; j++) {
			s.append(String.format("%02X ", ai.noteTable[j]));
		}
		s.append("\n"); // std::endl

		for (int k = 0; k < Instrument.ENVROWS; k++) {
			StringBuilder bf = new StringBuilder();
			for (int j = 0; j <= ai.parameters[Instrument.PAR_ENV_LENGTH]; j++) {
				bf.append(Song.charL4(ai.envelope[j][k]));
			}
			s.append(String.format("%s %s\n", SHENV_FIELD_NAME[k], bf));
		}
		s.append("\n"); // gap

		return s.toString();
	}

	/** Encodes every non-empty instrument as TXT, concatenated (the {@code [INSTRUMENT]} sections {@link Song#saveTxt} appends). */
	public String saveAllTxt() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < INSTRSNUM; i++) {
			if (calculateNotEmpty(i)) {
				s.append(saveInstrumentTxt(i));
			}
		}
		return s.toString();
	}

	/**
	 * Decodes one {@code [INSTRUMENT]} segment's TXT content (the instrument
	 * number is parsed from the segment's own first line, matching C++'s
	 * {@code LoadInstrument(-1, ...)} convention - the only way
	 * {@link Song#loadTxt} calls this), returning the position right after
	 * the next {@code '['} (matching {@link Song#nextSegment}'s convention,
	 * ready for the caller's own segment-name read).
	 */
	public int loadInstrumentTxt(String text, int pos) {
		Song.Line firstLine = Song.readLine(text, pos);
		pos = firstLine.nextPos();
		int instr = Song.hexstr(firstLine.content(), 0, 2);

		if (instr < 0 || instr >= INSTRSNUM) {
			return Song.nextSegment(text, pos);
		}

		clearInstrument(instr);
		Instrument ai = getInstrument(instr);

		String value = firstLine.content().length() > 4 ? firstLine.content().substring(4) : "";
		value = Song.trimstr(value);
		java.util.Arrays.fill(ai.name, ' ');
		int lname = Math.min(value.length(), Instrument.INSTRUMENT_NAME_MAX_LEN);
		for (int c = 0; c < lname; c++) {
			ai.name[c] = value.charAt(c);
		}

		while (pos < text.length()) {
			char b = text.charAt(pos);
			pos++;
			if (b == '[') {
				update(instr); // C++'s InstrEnd: label
				return pos; // end of instrument (beginning of something else)
			}
			if (b == '\n') {
				// A blank line (saveInstrumentTxt() writes one as a "gap"
				// before the next segment) - matches the fix already
				// applied on the C++ side (see IO_Instruments.cpp).
				continue;
			}

			Song.Line rest = Song.readLine(text, pos);
			pos = rest.nextPos();
			String kvLine = b + rest.content();

			int colonSpace = kvLine.indexOf(": ");
			if (colonSpace < 0) {
				continue;
			}
			String key = kvLine.substring(0, colonSpace + 1);
			String kvValue = kvLine.substring(colonSpace + 2);

			boolean matchedParam = false;
			for (ShPar p : SHPAR) {
				if (key.equals(p.fieldName())) {
					int v = Song.hexstr(kvValue, 0, 2) - p.displayOffset();
					if (v >= 0) {
						v &= p.parameterAND();
						if (v > p.maxParameterValue()) {
							v = 0;
						}
						ai.parameters[p.paramIndex()] = v;
					}
					matchedParam = true;
					break;
				}
			}
			if (matchedParam) {
				continue;
			}

			if (key.equals("TABLE:")) {
				String tableValue = Song.trimstr(kvValue);
				for (int j = 0; j < tableValue.length(); j += 3) {
					int v = Song.hexstr(tableValue, j, 2);
					if (v < 0) {
						break;
					}
					ai.noteTable[j / 3] = v;
				}
				continue;
			}

			for (int j = 0; j < Instrument.ENVROWS; j++) {
				if (key.equals(SHENV_FIELD_NAME[j])) {
					for (int k = 0; k < kvValue.length() && k < Instrument.ENVELOPE_MAX_COLUMNS; k++) {
						int v = Song.hexstr(kvValue, k, 1);
						if (v < 0) {
							break;
						}
						v &= SHENV_PAND[j];
						ai.envelope[k][j] = v;
					}
					break;
				}
			}
		}

		update(instr); // C++'s InstrEnd: label
		return text.length();
	}

	private byte[] saveInstrumentRmw(int instr) {
		Instrument ai = getInstrument(instr);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

		for (int c = 0; c < Instrument.INSTRUMENT_NAME_MAX_LEN; c++) {
			out.write(ai.name[c]);
		}
		for (int j = 0; j < Instrument.PARCOUNT; j++) {
			out.write(ai.parameters[j]);
		}
		for (int j = 0; j < Instrument.ENVELOPE_MAX_COLUMNS; j++) {
			for (int k = 0; k < Instrument.ENVROWS; k++) {
				out.write(ai.envelope[j][k]);
			}
		}
		for (int j = 0; j < Instrument.NOTE_TABLE_MAX_LEN; j++) {
			out.write(ai.noteTable[j]);
		}

		writeIntLE(out, instrumentSectionToRmw(ai.activeEditSection));
		writeIntLE(out, ai.editNameCursorPos);
		writeIntLE(out, ai.editParameterNr);
		writeIntLE(out, ai.editEnvelopeX);
		writeIntLE(out, ai.editEnvelopeY);
		writeIntLE(out, ai.editNoteTableCursorPos);
		writeIntLE(out, ai.octave);
		writeIntLE(out, ai.volume);

		return out.toByteArray();
	}

	/** Encodes every instrument (unconditionally, unlike {@link #saveAllTxt}) in RMW's binary format, concatenated. */
	public byte[] saveAllRmw() {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (int i = 0; i < INSTRSNUM; i++) {
			out.writeBytes(saveInstrumentRmw(i));
		}
		return out.toByteArray();
	}

	private int loadInstrumentRmw(int instr, byte[] data, int pos) {
		clearInstrument(instr);
		Instrument ai = getInstrument(instr);

		for (int c = 0; c < Instrument.INSTRUMENT_NAME_MAX_LEN; c++) {
			ai.name[c] = (char) (data[pos] & 0xFF);
			pos++;
		}
		for (int j = 0; j < Instrument.PARCOUNT; j++) {
			ai.parameters[j] = data[pos];
			pos++;
		}
		for (int j = 0; j < Instrument.ENVELOPE_MAX_COLUMNS; j++) {
			for (int k = 0; k < Instrument.ENVROWS; k++) {
				ai.envelope[j][k] = data[pos];
				pos++;
			}
		}
		for (int j = 0; j < Instrument.NOTE_TABLE_MAX_LEN; j++) {
			ai.noteTable[j] = data[pos];
			pos++;
		}

		update(instr);

		ai.activeEditSection = instrumentSectionFromRmw(readIntLE(data, pos));
		pos += 4;
		ai.editNameCursorPos = readIntLE(data, pos);
		pos += 4;
		ai.editParameterNr = readIntLE(data, pos);
		pos += 4;
		ai.editEnvelopeX = readIntLE(data, pos);
		pos += 4;
		ai.editEnvelopeY = readIntLE(data, pos);
		pos += 4;
		ai.editNoteTableCursorPos = readIntLE(data, pos);
		pos += 4;
		ai.octave = readIntLE(data, pos);
		pos += 4;
		ai.volume = readIntLE(data, pos);
		pos += 4;

		return pos;
	}

	/** Decodes every instrument's RMW binary representation ({@link #saveAllRmw}'s counterpart), returning the position right after the last one. */
	public int loadAllRmw(byte[] data, int pos) {
		for (int i = 0; i < INSTRSNUM; i++) {
			pos = loadInstrumentRmw(i, data, pos);
		}
		return pos;
	}

	private static void writeIntLE(java.io.ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 24) & 0xFF);
	}

	private static int readIntLE(byte[] data, int pos) {
		return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8) | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
	}
}
