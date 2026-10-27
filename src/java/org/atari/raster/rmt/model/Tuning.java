package org.atari.raster.rmt.model;

/**
 * Ported from CTuning (src/cpp/Tuning.h/.cpp, src/cpp/TuningTables.cpp).
 *
 * <p>C++'s test-only constructor (which bypasses InitTuning()'s dependency on
 * the tuning state it does not get passed) becomes the only constructor here.
 * generateTable()/initTuning() take {@link TuningSettings}
 * as explicit parameters instead of reaching for the song that owns them, as
 * C++ does with g_Song.GetTuningSettings() - a deliberate, idiomatic
 * substitution (same judgment call as IntegerFraction's immutability), not a
 * behavior change: every value production reads there is still
 * supplied by the caller here, just explicitly. Their C++ MessageBox+exit(1)
 * guard (basetuning == 0) becomes an IllegalStateException, matching
 * IntegerFraction's own precedent for translating a fatal C++ precondition into a
 * Java exception instead of terminating the process.
 *
 * <p>Expected values for this class's tests were captured by first adding
 * the equivalent golden-master characterization tests to CTuning itself
 * (src/cpp/test/TuningTests.cpp) - the safest way to port arithmetic this
 * intricate (modulo-driven branching, the preset multipliers - on their Temperament instances since plan 59 step 4) without
 * either hand-deriving expected values or trusting an unverified transcription.
 */
public final class Tuning {

	public static final int NO_TEMPERAMENT = 0; // No temperament assumes the value of 0, as Equal Temperament
	public static final int TUNING_PRESETS = 29; // Total number of temperaments available
	public static final int TUNING_CUSTOM = TUNING_PRESETS; // Custom Temperament using Ratio is assumed otherwise

	/**
	 * The twelve semitones of the 2:1 octave - the one place the 12 is
	 * defined ({@link TuningCents#COUNT} is this constant). After plan 59
	 * retired the presets that do not fit (step 1), every temperament has
	 * this many notes, so C++'s per-preset notes-per-octave scan
	 * (g_notesperoctave) is a constant here; the preset multipliers
	 * themselves live on their {@link Temperament} instances (step 4).
	 */
	public static final int NOTES_PER_OCTAVE = 12;

	// Multiply by notes per octave to transpose the table. dist_4_buzzy/
	// dist_c_unstable exist in the C++ source (Tuning.h) but are never
	// actually used by InitTuning() there either - omitted here as dead code.
	private static final TuningTable DIST_2_BELL = new TuningTable(1, 0, 4, 2);
	private static final TuningTable DIST_4_SMOOTH = new TuningTable(1, 0, 2, 2);
	private static final TuningTable DIST_A_PURE = new TuningTable(4, 2, 9, 2);
	private static final TuningTable DIST_C_BUZZY = new TuningTable(2, 1, 7, 2);
	private static final TuningTable DIST_C_GRITTY = new TuningTable(1, 0, 6, 2);

	/**
	 * The two sawtooth tables of PAGE_EXTRA_0 ($B500 for channel 1, $B540 for
	 * channel 3), copied verbatim from {@code asm/Patch-16/rmtdata.a65}, which
	 * stays their source of truth. No formula produces them: the values are
	 * hand-picked and non-monotonic, two channels beating against each other
	 * rather than a scale. {@link #initTuning} writes them alongside the
	 * generated tables so that the whole $B000-$B5FF region is a pure function
	 * of the tuning settings - it must not depend on a driver binary having
	 * been loaded over it first
	 * (plans/37_TUNING_COMPATIBILITY_PLAN.md, T6).
	 */
	// clang-format off
	private static final int[] SAWTOOTH_TABLES = {
		0xE4, 0xDE, 0xD7, 0xD1, 0xCB, 0xC5, 0xBF, 0xBA, 0xB4, 0xAF, 0xAA, 0xA5,
		0xA0, 0x9B, 0x97, 0x92, 0x8E, 0x8A, 0x86, 0x82, 0x7E, 0x7A, 0x77, 0x73,
		0x70, 0x6D, 0x69, 0x66, 0x63, 0x60, 0x5D, 0x81, 0x58, 0x7A, 0x76, 0x50,
		0x4E, 0x6C, 0x69, 0x47, 0x45, 0x43, 0x5D, 0x5A, 0x57, 0x3B, 0x52, 0x62,
		0x4D, 0x34, 0x68, 0x31, 0x62, 0x42, 0x5C, 0x2B, 0x6A, 0x67, 0x6C, 0x58,
		0x35, 0x4A, 0x32, 0x4E,
		0xE5, 0xDF, 0xD8, 0xD2, 0xCC, 0xC6, 0xC0, 0xBB, 0xB5, 0xB0, 0xAB, 0xA6,
		0xA1, 0x9C, 0x98, 0x93, 0x8F, 0x8B, 0x87, 0x83, 0x7F, 0x7B, 0x78, 0x74,
		0x71, 0x6E, 0x6A, 0x67, 0x64, 0x61, 0x5E, 0x83, 0x59, 0x7C, 0x78, 0x51,
		0x4F, 0x6E, 0x6B, 0x48, 0x46, 0x44, 0x5F, 0x5C, 0x59, 0x3C, 0x54, 0x65,
		0x4F, 0x35, 0x6C, 0x32, 0x66, 0x44, 0x60, 0x2C, 0x70, 0x6D, 0x73, 0x5D,
		0x37, 0x4E, 0x34, 0x53};
	// clang-format on

	private final int clockFrequency;

	// Custom tuning ratio is generated into this array by initTuning(), so
	// it's not a constant.
	private final double[] custom = new double[NOTES_PER_OCTAVE + 1]; // the twelve multipliers plus the octave

	public Tuning(int clockFrequency) {
		this.clockFrequency = clockFrequency;
	}

	/** A- is nine semitones above C-; the {@link BaseNote} numbers count downward from C = 0, so the interval from the tonic up to the next A is (9 + basenote) % 12. */
	static final int A_SEMITONES_ABOVE_C = 9;

	/**
	 * The anchor to feed {@link #getTruePitch}'s {@code tuning} parameter so
	 * that the note A always sounds at the A4 reference pitch
	 * {@code tuningSettings.basetuning}, whatever the key - plan 59 step 2,
	 * the port of ivop's {@code calculate_base_frequency()}
	 * (others/Ivop/tuning_poc2.c): the temperament's multiplier for the
	 * interval from the tonic up to A divides the A4 frequency, and the power
	 * of two re-anchors the octave. For the base note A- the interval is the
	 * unison, so the anchor is the A4 frequency itself, bit-exactly - the
	 * tables of every module with the default base note are unchanged. For
	 * the custom temperament this reads the multipliers {@link #initTuning}
	 * computed from the cents, so it is only valid after that computation -
	 * which is the only caller ({@code generateTable}).
	 */
	double calculateBaseFrequency(TuningSettings tuningSettings) {
		int toA = A_SEMITONES_ABOVE_C + tuningSettings.basenote.getNumber();
		int interval = toA % NOTES_PER_OCTAVE; // semitones from the tonic up to the next A
		double multiplier;
		if (tuningSettings.temperament == Temperament.EQUAL) {
			multiplier = Math.pow(2, (double) interval / NOTES_PER_OCTAVE);
		} else if (tuningSettings.temperament.isCustom()) {
			multiplier = custom[interval];
		} else {
			multiplier = tuningSettings.temperament.getMultiplier(interval);
		}
		return tuningSettings.basetuning * Math.pow(2, 1 - toA / NOTES_PER_OCTAVE) / multiplier; // integer division: 1 - 1 = 0 octaves for the keys C#- through A-
	}

	/**
	 * Calculate the true audio pitch output using the given parameters.
	 *
	 * @param tuning the anchor pitch (in Hertz) the tonic's octave chain is placed by - {@link #calculateBaseFrequency}'s conversion of the A4 reference pitch (for the base note A- the two coincide)
	 * @param temperament the temperament used in calculations - a preset plays its own multipliers, {@link Temperament#CUSTOM} the cents {@link #initTuning} converted
	 * @param basenote base note/key from which the tuning is calculated, typically it is the key of A- or C-
	 * @param semitone semitones added to base note for calculating higher pitches
	 * @return true audio pitch for a given note (in Hertz)
	 */
	public double getTruePitch(double tuning, Temperament temperament, int basenote, int semitone) {
		int note = (semitone + basenote) % NOTES_PER_OCTAVE; // current note
		double ratio;
		double octave = 2; // an octave is the frequency of a note multiplied by 2

		// Equal temperament is generated using the 12th root of 2
		if (temperament == Temperament.EQUAL) {
			ratio = Math.pow(2.0, 1.0 / NOTES_PER_OCTAVE);
			return (tuning / 64) * Math.pow(ratio, semitone + basenote);
		}
		if (temperament.isCustom()) { // custom temperament will be used using ratio
			octave = custom[NOTES_PER_OCTAVE];
			ratio = custom[note];
		} else { // any temperament preset will be used
			ratio = temperament.getMultiplier(note);
		}
		double multi = Math.pow(octave, (semitone + basenote) / NOTES_PER_OCTAVE); // integer division, matching C++'s int/int then trunc()
		return (tuning / 64) * (multi * ratio);
	}

	/**
	 * Calculate the difference between 2 POKEY frequencies (AUDF) within the conditions intended for the timbre to be output.
	 *
	 * @param pitch reference audio pitch (in Hertz)
	 * @param audf invalid POKEY Frequency (AUDF) referenced to find the nearest compromised frequency
	 * @param coarseDivisor coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division
	 * @param divisor fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division
	 * @param cycle offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither
	 * @param timbre POKEY sound timbre output using the Distortion as well as the modulo of the Frequency
	 * @return compromised POKEY Frequency (AUDF) which is now valid within the conditions established for the generated timbre
	 */
	public int calculateDeltaAUDF(double pitch, int audf, int coarseDivisor, double divisor, int cycle, Timbre timbre) {
		int distortion = timbre.value & 0xF0;

		int tmpAudfUp = audf; // begin from the currently invalid audf
		int tmpAudfDown = audf;

		if (distortion != 0x40 && distortion != 0xC0) {
			// anything not distortion 4 or C, simplest delta method
			tmpAudfUp++;
			tmpAudfDown--;
		} else if (distortion == 0x40) {
			if (timbre == Timbre.SMOOTH_4) { // verify MOD3 integrity
				for (int o = 0; o < 6; o++) {
					if ((tmpAudfUp + cycle) % 3 != 0 || (tmpAudfUp + cycle) % 5 == 0 || (tmpAudfUp + cycle) % 31 == 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 3 != 0 || (tmpAudfDown + cycle) % 5 == 0 || (tmpAudfDown + cycle) % 31 == 0) {
						tmpAudfDown--;
					}
				}
			} else if (timbre == Timbre.BUZZY_4) {
				for (int o = 0; o < 6; o++) {
					if ((tmpAudfUp + cycle) % 3 == 0 || (tmpAudfUp + cycle) % 5 == 0 || (tmpAudfUp + cycle) % 31 == 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 3 == 0 || (tmpAudfDown + cycle) % 5 == 0 || (tmpAudfDown + cycle) % 31 == 0) {
						tmpAudfDown--;
					}
				}
			} else {
				return 0; // invalid parameter most likely
			}
		} else { // distortion == 0xC0
			if (coarseDivisor == 114) { // 15kHz mode
				for (int o = 0; o < 3; o++) { // MOD5 must be avoided!
					if ((tmpAudfUp + cycle) % 5 == 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 5 == 0) {
						tmpAudfDown--;
					}
				}
			} else if (timbre == Timbre.BUZZY_C) { // verify MOD3 integrity
				for (int o = 0; o < 6; o++) {
					if ((tmpAudfUp + cycle) % 3 != 0 || (tmpAudfUp + cycle) % 5 == 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 3 != 0 || (tmpAudfDown + cycle) % 5 == 0) {
						tmpAudfDown--;
					}
				}
			} else if (timbre == Timbre.GRITTY_C) { // verify neither MOD3 or MOD5 is used
				for (int o = 0; o < 6; o++) {
					if ((tmpAudfUp + cycle) % 3 == 0 || (tmpAudfUp + cycle) % 5 == 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 3 == 0 || (tmpAudfDown + cycle) % 5 == 0) {
						tmpAudfDown--;
					}
				}
			} else if (timbre == Timbre.UNSTABLE_C) { // verify MOD5 integrity
				for (int o = 0; o < 6; o++) {
					if ((tmpAudfUp + cycle) % 3 == 0 || (tmpAudfUp + cycle) % 5 != 0) {
						tmpAudfUp++;
					}
					if ((tmpAudfDown + cycle) % 3 == 0 || (tmpAudfDown + cycle) % 5 != 0) {
						tmpAudfDown--;
					}
				}
			} else {
				return 0; // invalid parameter most likely
			}
		}

		double pitchUp = getPitch(tmpAudfUp, coarseDivisor, divisor, cycle);
		double deltaUp = pitch - pitchUp; // first delta, up
		double pitchDown = getPitch(tmpAudfDown, coarseDivisor, divisor, cycle);
		double deltaDown = pitchDown - pitch; // second delta, down

		// positive means delta up is closer than delta down, negative means the opposite
		return (deltaDown - deltaUp) > 0 ? tmpAudfUp : tmpAudfDown;
	}

	/**
	 * Generate a POKEY Frequencies lookup table using the given parameters.
	 *
	 * @param table memory the table will be written into, typically the emulated Atari memory
	 * @param offset starting index within table to write at (C++ passes a pre-offset pointer instead)
	 * @param length length of the table in number of semitones, 16-bit tables use twice as many bytes
	 * @param semitone number of semitones above base note, useful for transposing a table to a different key/octave
	 * @param timbre POKEY sound timbre output using the Distortion as well as the modulo of the Frequency
	 * @param audctl POKEY modes used to generate the frequencies, typically, 15Khz/64Khz clock, 1.79mHz clock, 16-bit mode, etc
	 * @param tuningSettings the base tuning/temperament/basenote to generate the table from (C++ reads these from the g_tuning global instead)
	 */
	public void generateTable(byte[] table, int offset, int length, int semitone, Timbre timbre, int audctl, TuningSettings tuningSettings) {
		double divisor = 1;
		int coarseDivisor = 1;
		int cycle = 1;

		boolean clock15 = (audctl & 0x01) != 0;
		boolean join34 = (audctl & 0x08) != 0;
		boolean join12 = (audctl & 0x10) != 0;
		boolean ch3_179 = (audctl & 0x20) != 0;
		boolean ch1_179 = (audctl & 0x40) != 0;

		// combined modes for some special output... the channel number doesn't
		// actually matter for creating tables, so the parameter is omitted
		boolean join16bit = (join12 && ch1_179) || (join34 && ch3_179);
		boolean clock179 = ch1_179 || ch3_179;
		if (join16bit || clock179) {
			// override, these 2 take priority over 15khz mode if enabled at the same time
			clock15 = false;
		}

		if (join16bit) {
			cycle = 7;
		} else if (clock179) {
			cycle = 4;
		} else {
			coarseDivisor = clock15 ? 114 : 28;
		}

		// Use the modulo flags to make sure the correct timbre will be output.
		switch (timbre) {
		case PINK_NOISE:
			break;
		case BROWNIAN_NOISE:
			divisor = 36.5; // Brownian noise, not MOD31 and not MOD73
			break;
		case FUZZY_NOISE:
			divisor = 255.5; // Fuzzy noise, not MOD7, not MOD31 and not MOD73
			break;
		case BELL:
			divisor = 31; // Bell tones, not MOD31
			break;
		case BUZZY_4:
			divisor = 232.5; // Buzzy tones, neither MOD3 or MOD5 or MOD31
			break;
		case SMOOTH_4:
			divisor = 77.5; // Smooth tones, MOD3 but not MOD5 or MOD31
			break;
		case WHITE_NOISE:
			break;
		case METALLIC_NOISE:
			divisor = 36.5; // Metallic noise, not MOD73
			break;
		case BUZZY_NOISE:
			divisor = 255.5; // Buzzy noise, not MOD7 and not MOD73
			break;
		case PURE_A:
			break;
		case GRITTY_C:
			divisor = 7.5; // Gritty tones, neither MOD3 or MOD5
			break;
		case BUZZY_C:
			divisor = 2.5; // Buzzy tones, MOD3 but not MOD5
			break;
		case UNSTABLE_C:
			divisor = 1.5; // Unstable Buzzy tones, MOD5 but not MOD3
			break;
		}

		// Generate the table using all the initialized parameters. MOD7/MOD15/
		// MOD73 are computed in the C++ source but never actually read by any
		// branch below - dropped here as dead code, verified against
		// TuningTables.cpp's GenerateTable() before omitting.
		double baseFrequency = calculateBaseFrequency(tuningSettings);
		for (int i = 0; i < length; i++) {
			int note = i + semitone;
			double pitch = getTruePitch(baseFrequency, tuningSettings.temperament, tuningSettings.basenote.getNumber(), note);
			int audf = getAUDF(pitch, coarseDivisor, divisor, cycle);

			boolean mod3 = (audf + cycle) % 3 == 0;
			boolean mod5 = (audf + cycle) % 5 == 0;
			boolean mod31 = (audf + cycle) % 31 == 0;

			switch (timbre) {
			case BELL:
				if (mod31) {
					audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, divisor, cycle, timbre);
				}
				break;

			case BUZZY_4:
				if (mod3 || mod5 || mod31) {
					audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, divisor, cycle, timbre);
				}
				break;

			case SMOOTH_4:
				if (!(mod3 || clock15) || mod5) {
					audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, divisor, cycle, timbre);
				}
				if (!join16bit && audf > 0xFF) { // use the buzzy timbre on the lower range instead
					audf = getAUDF(pitch, coarseDivisor, 232.5, cycle);
					mod3 = (audf + cycle) % 3 == 0;
					mod5 = (audf + cycle) % 5 == 0;
					if (mod3 || mod5) {
						audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, 232.5, cycle, Timbre.BUZZY_4);
					}
				}
				break;

			case GRITTY_C:
				if (mod3 || mod5) {
					audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, divisor, cycle, timbre);
				}
				break;

			case BUZZY_C:
				if (!(mod3 || clock15) || mod5) {
					audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, divisor, cycle, timbre);
				}
				if (!join16bit && audf > 0xFF) { // use the gritty timbre on the lower range instead
					audf = getAUDF(pitch, coarseDivisor, 7.5, cycle);
					mod3 = (audf + cycle) % 3 == 0;
					mod5 = (audf + cycle) % 5 == 0;
					if (mod3 || mod5) {
						audf = calculateDeltaAUDF(pitch, audf, coarseDivisor, 7.5, cycle, Timbre.GRITTY_C);
					}
				}
				break;

			default:
				break;
			}

			if (audf < 0) {
				audf = 0;
			}
			if (!join16bit && audf > 0xFF) {
				audf = 0xFF;
			}
			if (join16bit && audf > 0xFFFF) {
				audf = 0xFFFF;
			}

			// Write the POKEY frequency to the table.
			if (join16bit) { // In 16-bit tables, 2 bytes have to be written contiguously
				table[offset + i * 2] = (byte) (audf & 0x0FF); // LSB
				table[offset + i * 2 + 1] = (byte) (audf >> 8); // MSB
			} else {
				table[offset + i] = (byte) audf;
			}
		}
	}

	/**
	 * Initialize the tuning variables, and generate the POKEY frequencies (AUDF) lookup tables into the given memory buffer.
	 *
	 * @param tableMemory buffer to write the lookup tables into, typically the emulated Atari memory (at least 0x600 bytes)
	 * @param tuningSettings the base tuning/temperament/basenote and the custom-temperament ratios to generate the tables from (C++ reads these from the g_tuning/g_tuningRatios globals instead)
	 */
	public void initTuning(byte[] tableMemory, TuningSettings tuningSettings) {
		if (tuningSettings.basetuning == 0) {
			// if base tuning is 0.0, the C++ original shows a MessageBox and
			// calls exit(1) to avoid crashing later - not appropriate for a
			// Java library class, so this throws instead.
			throw new IllegalStateException("An invalid tuning has been detected: basetuning is zero.");
		}

		// Calculate the custom multiplier for each semitone from its cents
		// value (plan 59): 2^(cents/1200). The octave is fixed at 2:1.
		for (int i = 0; i < TuningCents.COUNT; i++) {
			custom[i] = Math.pow(2, tuningSettings.cents.values[i] / 1200);
		}
		custom[NOTES_PER_OCTAVE] = 2;

		// Generate all lookup tables used by the RMT driver for tuning purposes.

		// Distortion 2, at 0xB000
		generateTable(tableMemory, 0x000, 64, DIST_2_BELL.table64khz() * NOTES_PER_OCTAVE, Timbre.BELL, 0x00, tuningSettings);
		generateTable(tableMemory, 0x040, 64, DIST_2_BELL.table179mhz() * NOTES_PER_OCTAVE, Timbre.BELL, 0x40, tuningSettings);
		generateTable(tableMemory, 0x080, 64, DIST_2_BELL.table16bit() * NOTES_PER_OCTAVE, Timbre.BELL, 0x50, tuningSettings);
		// no 15kHz table...

		// Distortion 4 (Smooth), at 0xB100
		generateTable(tableMemory, 0x100, 64, DIST_4_SMOOTH.table64khz() * NOTES_PER_OCTAVE, Timbre.SMOOTH_4, 0x00, tuningSettings);
		generateTable(tableMemory, 0x140, 64, DIST_4_SMOOTH.table179mhz() * NOTES_PER_OCTAVE, Timbre.SMOOTH_4, 0x40, tuningSettings);
		generateTable(tableMemory, 0x180, 64, DIST_4_SMOOTH.table16bit() * NOTES_PER_OCTAVE, Timbre.SMOOTH_4, 0x50, tuningSettings);
		// no 15kHz table...

		// Distortion A (Pure), at 0xB200
		generateTable(tableMemory, 0x200, 64, DIST_A_PURE.table64khz() * NOTES_PER_OCTAVE, Timbre.PURE_A, 0x00, tuningSettings);
		generateTable(tableMemory, 0x240, 64, DIST_A_PURE.table179mhz() * NOTES_PER_OCTAVE, Timbre.PURE_A, 0x40, tuningSettings);
		generateTable(tableMemory, 0x280, 64, DIST_A_PURE.table16bit() * NOTES_PER_OCTAVE, Timbre.PURE_A, 0x50, tuningSettings);
		generateTable(tableMemory, 0x580, 64, DIST_A_PURE.table15khz() * NOTES_PER_OCTAVE, Timbre.PURE_A, 0x01, tuningSettings);

		// Distortion C (Buzzy), at 0xB300
		generateTable(tableMemory, 0x300, 64, DIST_C_BUZZY.table64khz() * NOTES_PER_OCTAVE, Timbre.BUZZY_C, 0x00, tuningSettings);
		generateTable(tableMemory, 0x340, 64, DIST_C_BUZZY.table179mhz() * NOTES_PER_OCTAVE, Timbre.BUZZY_C, 0x40, tuningSettings);
		generateTable(tableMemory, 0x380, 64, DIST_C_BUZZY.table16bit() * NOTES_PER_OCTAVE, Timbre.BUZZY_C, 0x50, tuningSettings);
		generateTable(tableMemory, 0x5C0, 64, DIST_C_BUZZY.table15khz() * NOTES_PER_OCTAVE, Timbre.BUZZY_C, 0x01, tuningSettings);

		// Distortion C (Gritty), at 0xB400
		generateTable(tableMemory, 0x400, 64, DIST_C_GRITTY.table64khz() * NOTES_PER_OCTAVE, Timbre.GRITTY_C, 0x00, tuningSettings);
		generateTable(tableMemory, 0x440, 64, DIST_C_GRITTY.table179mhz() * NOTES_PER_OCTAVE, Timbre.GRITTY_C, 0x40, tuningSettings);
		generateTable(tableMemory, 0x480, 64, DIST_C_GRITTY.table16bit() * NOTES_PER_OCTAVE, Timbre.GRITTY_C, 0x50, tuningSettings);
		// no 15kHz table...

		// Sawtooth, at 0xB500 and 0xB540 - hand-made, not generated (see SAWTOOTH_TABLES)
		for (int i = 0; i < SAWTOOTH_TABLES.length; i++) {
			tableMemory[0x500 + i] = (byte) SAWTOOTH_TABLES[i];
		}
	}

	/**
	 * Calculate the POKEY audio pitch using the given parameters.
	 *
	 * @param audf POKEY Frequency, either 8-bit or 16-bit
	 * @param coarseDivisor coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division
	 * @param divisor fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division
	 * @param cycle offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither
	 * @return POKEY audio pitch (in Hertz)
	 */
	public double getPitch(int audf, int coarseDivisor, double divisor, int cycle) {
		return ((clockFrequency / (coarseDivisor * divisor)) / (audf + cycle)) / 2;
	}

	/**
	 * Find the nearest POKEY Frequency (AUDF) using the given parameters.
	 *
	 * @param pitch source audio pitch (in Hertz)
	 * @param coarseDivisor coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division
	 * @param divisor fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division
	 * @param cycle offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither
	 * @return POKEY Frequency (AUDF)
	 */
	public int getAUDF(double pitch, int coarseDivisor, double divisor, int cycle) {
		return (int) Math.round(((clockFrequency / (coarseDivisor * divisor)) / (2 * pitch)) - cycle);
	}

	/**
	 * Generate the POKEY audio pitch using the given parameters.
	 *
	 * @param audc POKEY Distortion and Volume output mode
	 * @param audf POKEY Frequency, either 8-bit or 16-bit
	 * @param audctl POKEY modes used to generate the frequencies, typically, 15Khz/64Khz clock, 1.79mHz clock, 16-bit mode, etc
	 * @param channel POKEY channel number between 0 and 3, multiple parameters might give different results
	 * @return POKEY audio pitch (in Hertz)
	 */
	public double getPOKEYPitch(int audc, int audf, int audctl, int channel) {
		// variables for pitch calculation, divisors must never be 0!
		double divisor = 1;
		int coarseDivisor = 1;
		int cycle = 1;

		// register variables
		int distortion = audc & 0xf0;
		boolean clock15 = (audctl & 0x01) != 0;
		boolean join34 = (audctl & 0x08) != 0;
		boolean join12 = (audctl & 0x10) != 0;
		boolean ch3_179 = (audctl & 0x20) != 0;
		boolean ch1_179 = (audctl & 0x40) != 0;
		boolean poly9 = (audctl & 0x80) != 0;

		// combined modes for some special output...
		boolean join16bit = (join12 && ch1_179 && (channel == 1)) || (join34 && ch3_179 && (channel == 3));
		boolean clock179 = (ch1_179 && (channel == 0)) || (ch3_179 && (channel == 2));
		if (join16bit || clock179) {
			// override, these 2 take priority over 15khz mode if they are enabled at the same time
			clock15 = false;
		}

		if (join16bit) {
			cycle = 7;
		} else if (clock179) {
			cycle = 4;
		} else {
			coarseDivisor = clock15 ? 114 : 28;
		}

		// Many combinations depend entirely on the Modulo of POKEY frequencies to generate different tones.
		// If a known value provide unstable results, it may be avoided on purpose.
		boolean mod3 = (audf + cycle) % 3 == 0;
		boolean mod5 = (audf + cycle) % 5 == 0;
		boolean mod7 = (audf + cycle) % 7 == 0;
		boolean mod15 = (audf + cycle) % 15 == 0;
		boolean mod31 = (audf + cycle) % 31 == 0;
		boolean mod73 = (audf + cycle) % 73 == 0;

		switch (distortion) {
		case 0x00:
			if (poly9) {
				divisor = 255.5; // Metallic Buzzy
				if (mod7 || (!clock15 && !clock179 && !join16bit)) {
					divisor = 36.5; // seems to only sound "uniform" in 64kHz mode for some reason
				}
				if (mod31 || mod73) {
					return 0; // MOD31 and MOD73 values are invalid
				}
			}
			break;

		case 0x20:
		case 0x60: // Duplicate of Distortion 2
			divisor = 31;
			if (mod31) {
				return 0;
			}
			break;

		case 0x40:
			divisor = 232.5; // Buzzy tones, neither MOD3 or MOD5 or MOD31
			if (mod3 || clock15) {
				divisor = 77.5; // Smooth tones, MOD3 but not MOD5 or MOD31
			}
			if (mod5) {
				divisor = 46.5; // Unstable tones #1, MOD5 but not MOD3 or MOD31
			}
			if (mod31) {
				divisor = (mod3 || mod5) ? 2.5 : 7.5; // Unstable Tones #2 and #3, MOD31, with MOD3 or MOD5
			}
			if (mod15 || (mod5 && clock15)) {
				return 0; // Both MOD3 and MOD5 at once are invalid
			}
			break;

		case 0x80:
			if (poly9) {
				divisor = 255.5; // Metallic Buzzy
				if (mod7 || (!clock15 && !clock179 && !join16bit)) {
					divisor = 36.5; // seems to only sound "uniform" in 64kHz mode for some reason
				}
				if (mod73) {
					return 0; // MOD73 values are invalid
				}
			}
			break;

		case 0xC0:
			divisor = 7.5; // Gritty tones, neither MOD3 or MOD5
			if (mod3 || clock15) {
				divisor = 2.5; // Buzzy tones, MOD3 but not MOD5
			}
			if (mod5) {
				divisor = 1.5; // Unstable Buzzy tones, MOD5 but not MOD3
			}
			if (mod15 || (mod5 && clock15)) {
				return 0; // Both MOD3 and MOD5 at once are invalid
			}
			break;
		default:
			break;
		}
		return getPitch(audf, coarseDivisor, divisor, cycle);
	}

	// Table construction structure, ported from the C++ struct TTuning. Only
	// used internally to hold the 5 dist_* constants above.
	private record TuningTable(int table64khz, int table15khz, int table179mhz, int table16bit) {
	}
}
