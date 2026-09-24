package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ struct TInstrument (InstrumentTypes.h) - a single
 * instrument's data. Mutable, matching how C++ code accesses these fields
 * directly by index everywhere.
 *
 * <p>{@code name} stays a fixed-width {@code char[]} (not a {@code String})
 * since it's edited character-by-character via a cursor position
 * ({@code editNameCursorPos}) in the not-yet-ported UI - matching the
 * fixed-size C++ buffer's shape rather than converting to an immutable type
 * that wouldn't fit that editing model. Unlike C++, there's no null
 * terminator to manage; the array is exactly
 * {@link #INSTRUMENT_NAME_MAX_LEN} characters, always space-padded.
 */
public final class Instrument {

	public static final int INSTRUMENT_NAME_MAX_LEN = 32; // maximum length of instrument name
	public static final int PARCOUNT = 24; // 24 instrument parameters
	public static final int ENVELOPE_MAX_COLUMNS = 48; // 48 columns in envelope (drive 32) (48 from version 1.25)
	public static final int ENVROWS = 8; // 8 line (parameter) in the envelope
	public static final int NOTE_TABLE_MAX_LEN = 32; // maximum 32 steps in the note table
	public static final int NUMBER_OF_PARAMS = 20;

	// bits in displayHintFlags
	public static final int IF_NOEMPTY = 1;
	public static final int IF_USED = 2;
	public static final int IF_FILTER = 4;
	public static final int IF_BASS16 = 8;
	public static final int IF_PORTAMENTO = 16;
	public static final int IF_AUDCTL = 32;

	// Indices into parameters[]
	public static final int PAR_TBL_LENGTH = 0;
	public static final int PAR_TBL_GOTO = 1;
	public static final int PAR_TBL_SPEED = 2;
	public static final int PAR_TBL_TYPE = 3;
	public static final int PAR_TBL_MODE = 4;

	public static final int PAR_ENV_LENGTH = 5;
	public static final int PAR_ENV_GOTO = 6;
	public static final int PAR_VOL_FADEOUT = 7;
	public static final int PAR_VOL_MIN = 8;
	public static final int PAR_DELAY = 9;
	public static final int PAR_VIBRATO = 10;
	public static final int PAR_FREQ_SHIFT = 11;

	public static final int PAR_AUDCTL_15KHZ = 12;
	public static final int PAR_AUDCTL_HPF_CH2 = 13;
	public static final int PAR_AUDCTL_HPF_CH1 = 14;
	public static final int PAR_AUDCTL_JOIN_3_4 = 15;
	public static final int PAR_AUDCTL_JOIN_1_2 = 16;
	public static final int PAR_AUDCTL_179_CH3 = 17;
	public static final int PAR_AUDCTL_179_CH1 = 18;
	public static final int PAR_AUDCTL_POLY9 = 19;

	// Which section (name, parameters, envelope, note table) is being edited
	public InstrumentSection activeEditSection;

	// Name section
	public final char[] name = new char[INSTRUMENT_NAME_MAX_LEN];
	public int editNameCursorPos; // Where is the edit cursor 0 - 31

	// Parameter section
	public final int[] parameters = new int[PARCOUNT]; // 24 parameters (20 used, 4 spare)
	public int editParameterNr; // which parameter is being edited

	// Envelope section
	public final int[][] envelope = new int[ENVELOPE_MAX_COLUMNS][ENVROWS]; // [32][8] in practice
	public int editEnvelopeX;
	public int editEnvelopeY;

	// Note table section
	public final int[] noteTable = new int[NOTE_TABLE_MAX_LEN];
	public int editNoteTableCursorPos; // Which note table entry is being edited

	public int octave; // Last used Octave and Volume
	public int volume;

	public int displayHintFlags; // Some flags that give hints to what is happening with this instrument
}
