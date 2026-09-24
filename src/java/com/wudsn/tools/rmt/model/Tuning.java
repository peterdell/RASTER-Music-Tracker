package com.wudsn.tools.rmt.model;

/**
 * Ported from CTuning (src/cpp/Tuning.h/.cpp, src/cpp/TuningTables.cpp).
 *
 * <p>C++'s test-only constructor (which bypasses InitTuning()'s dependency on
 * global tuning state) becomes the only constructor here. generateTable()/
 * initTuning() take {@link TuningSettings}/{@link TuningRatios} as explicit
 * parameters instead of reading C++'s g_tuning/g_tuningRatios globals, since
 * no Java global-state architecture exists yet - a deliberate, idiomatic
 * substitution (same judgment call as Fraction's immutability), not a
 * behavior change: every value production reads from those globals is still
 * supplied by the caller here, just explicitly. Their C++ MessageBox+exit(1)
 * guard (basetuning == 0) becomes an IllegalStateException, matching
 * Fraction's own precedent for translating a fatal C++ precondition into a
 * Java exception instead of terminating the process.
 *
 * <p>Expected values for this class's tests were captured by first adding
 * the equivalent golden-master characterization tests to CTuning itself
 * (src/cpp/test/TuningTests.cpp) - the safest way to port arithmetic this
 * intricate (modulo-driven branching, a ragged 29-row preset table) without
 * either hand-deriving expected values or trusting an unverified transcription.
 */
public final class Tuning {

	public static final int NO_TEMPERAMENT = 0; // No temperament assumes the value of 0, as Equal Temperament
	public static final int TUNING_PRESETS = 29; // Total number of temperaments available
	public static final int TUNING_CUSTOM = TUNING_PRESETS; // Custom Temperament using Ratio is assumed otherwise
	public static final int PRESETS_LENGTH = 21; // Length of the largest preset table

	// Multiply by notes per octave to transpose the table. dist_4_buzzy/
	// dist_c_unstable exist in the C++ source (Tuning.h) but are never
	// actually used by InitTuning() there either - omitted here as dead code.
	private static final TuningTable DIST_2_BELL = new TuningTable(1, 0, 4, 2);
	private static final TuningTable DIST_4_SMOOTH = new TuningTable(1, 0, 2, 2);
	private static final TuningTable DIST_A_PURE = new TuningTable(4, 2, 9, 2);
	private static final TuningTable DIST_C_BUZZY = new TuningTable(2, 1, 7, 2);
	private static final TuningTable DIST_C_GRITTY = new TuningTable(1, 0, 6, 2);

	// clang-format off
	private static final double[][] TEMPERAMENT_PRESETS = {
		// No Temperament for the first slot, leave it empty
		row(0),

		// Thomas Young 1799's Well Temperament no.1
		row(1, 1.055709, 1.119770, 1.187690, 1.253887, 1.334739, 1.407637, 1.496513, 1.583581, 1.675715, 1.781546, 1.878851, 2),

		// Thomas Young 1799's Well Temperament no.2
		row(1, 1.055880, 1.119929, 1.187865, 1.254242, 1.334839, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.879240, 2),

		// Thomas Young 1807's Well Temperament
		row(1, 1.053497, 1.119929, 1.185185, 1.254242, 1.333333, 1.404663, 1.496616, 1.580246, 1.676105, 1.777777, 1.877119, 2),

		// Andreas Werckmeister's temperament III (the most famous one, 1681)
		row(1, 1.053497, 1.117403, 1.185185, 1.252827, 1.333333, 1.404663, 1.494927, 1.580246, 1.670436, 1.777777, 1.879240, 2),

		// Temperament Egal a Quintes Justes
		row(1, 1.059634, 1.122824, 1.189782, 1.260734, 1.335916, 1.415582, 1.5, 1.589451, 1.684236, 1.784674, 1.891101, 2.003875),

		// Alembert's and Rousseau's Temperament Ordinaire (1752/1767)
		row(1, 1.051120, 1.118034, 1.181176, 1.250000, 1.331828, 1.403077, 1.495348, 1.574901, 1.671850, 1.773766, 1.872883, 2),

		// Aron - Neidhardt equal beating well temperament
		row(1, 1.053497, 1.118144, 1.185185, 1.250031, 1.333333, 1.404663, 1.496112, 1.580246, 1.672002, 1.777777, 1.872885, 2),

		// Atom Schisma Scale
		row(1, 1.059459, 1.122463, 1.189204, 1.259924, 1.334838, 1.414207, 1.498308, 1.587396, 1.681796, 1.781794, 1.887755, 2),

		// 12-tET approximation with minimal order 17 beats
		row(1, 1.058823, 1.125000, 1.187500, 1.250000, 1.333333, 1.416666, 1.500000, 1.588235, 1.666666, 1.777777, 1.888888, 2),

		// Paul Bailey's modern well temperament (2002)
		row(1, 1.054892, 1.119671, 1.186774, 1.254242, 1.335183, 1.406531, 1.498302, 1.582329, 1.676250, 1.780202, 1.881407, 2),

		// John Barnes' temperament (1977) made after analysis of Wohltemperierte Klavier, 1/6 P
		row(1, 1.055880, 1.119929, 1.187865, 1.254242, 1.336348, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.881364, 2),

		// Bethisy temperament ordinaire, see Pierre-Yves Asselin: Musique et temperament
		row(1, 1.051418, 1.118034, 1.181509, 1.250000, 1.331953, 1.403475, 1.495348, 1.575346, 1.671850, 1.774101, 1.872884, 2),

		// Big Gulp
		row(1, 1.031250, 1.125000, 1.166666, 1.250000, 1.312500, 1.375000, 1.500000, 1.546875, 1.687500, 1.750000, 1.875000, 2),

		// 12-tone scale by Bohlen generated from the 4:7:10 triad, Acustica 39/2, 1978
		row(1, 1.100000, 1.200000, 1.304347, 1.428571, 1.571428, 1.750000, 1.909090, 2.100000, 2.300000, 2.500000, 2.750000, 3),

		// This scale may also be called the "Wedding Cake"
		row(1, 1.125000, 1.171875, 1.250000, 1.333333, 1.406250, 1.500000, 1.562500, 1.666666, 1.687500, 1.777777, 1.875000, 2),

		// Upside-Down Wedding Cake (divorce cake)
		row(1, 1.066666, 1.125000, 1.200000, 1.280000, 1.333333, 1.500000, 1.600000, 1.687500, 1.777777, 1.800000, 1.920000, 2),

		// 12-tone Pythagorean scale
		row(1, 1.067871, 1.125000, 1.185185, 1.265625, 1.333333, 1.423828, 1.500000, 1.601806, 1.687500, 1.777777, 1.898437, 2),

		// Robert Schneider, scale of log(4)..log(16), 1/1 = 264Hz
		row(1, 1.160964, 1.292481, 1.403677, 1.500000, 1.584962, 1.660964, 1.729715, 1.792481, 1.850219, 1.903677, 1.953445, 2),

		// Zarlino's Temperament Extraordinaire, 1024-tET mapping
		row(1, 1.041450, 1.116652, 1.180385, 1.247756, 1.337855, 1.393309, 1.494930, 1.567469, 1.669316, 1.777781, 1.865308, 2),

		// Fokker's 7-limit 12-tone just scale
		row(1, 1.071428, 1.125000, 1.166666, 1.250000, 1.333333, 1.406250, 1.500000, 1.607142, 1.666666, 1.750000, 1.875000, 2),

		// Bach temperament, a'=400 Hz
		row(1, 1.052192, 1.118997, 1.183716, 1.250521, 1.334029, 1.402922, 1.498956, 1.578288, 1.670146, 1.776618, 1.874739, 2),

		// Vallotti & Young scale (Vallotti version) also known as Tartini-Vallotti (1754)
		row(1, 1.055880, 1.119929, 1.187865, 1.254242, 1.336348, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.877119, 2),

		// Vallotti-Young and Werckmeister III, 10 cents 5-limit lesfip scale
		row(1, 1.051637, 1.117298, 1.187060, 1.248356, 1.336846, 1.399836, 1.495457, 1.580099, 1.669531, 1.783574, 1.867613, 2),

		// Optimally consonant major pentatonic, John deLaubenfels (2001)
		row(1, 1.118042, 1.250019, 1.496879, 1.670166, 2),

		// Ancient Greek Aeolic, also tritriadic scale of the 54:64:81 triad
		row(1, 1.125000, 1.185185, 1.333333, 1.500000, 1.580246, 1.777777, 2),

		// African Bapare xylophone (idiophone; loose log)
		row(1, 1.076737, 1.200942, 1.336382, 1.497441, 1.670175, 1.932988, 2.174725, 2.285484, 2.525670, 2.738400),

		// African Yaswa xylophones (idiophone; calabash resonators with membrane)
		row(1, 1.128312, 1.271619, 1.486239, 1.707240, 1.936341, 2.015074, 2.215296, 2.419988, 2.871225, 3.220980),

		// 19-EDO generated using Scale Workshop
		row(1, 1.037155, 1.075690, 1.115657, 1.157110, 1.200102, 1.244692, 1.290939, 1.338904, 1.388651, 1.440246, 1.493759, 1.549259, 1.606822, 1.666524, 1.728443, 1.792664, 1.859270, 1.928352, 2),
	};
	// clang-format on

	private static double[] row(double... values) {
		double[] result = new double[PRESETS_LENGTH];
		System.arraycopy(values, 0, result, 0, values.length);
		return result;
	}

	private final int clockFrequency;

	// Custom tuning ratio is generated into this array by initTuning(), so
	// it's not a constant.
	private final double[] custom = new double[13];

	public Tuning(int clockFrequency) {
		this.clockFrequency = clockFrequency;
	}

	/**
	 * Calculate the true audio pitch output using the given parameters.
	 *
	 * @param tuning tuning base pitch (in Hertz), usually the A-4 note
	 * @param temperament temperament used in calculations, 0 for Equal Temperament, 1 to 29 (inclusive) for presets, otherwise Custom Ratio is assumed
	 * @param basenote base note/key from which the tuning is calculated, typically it is the key of A- or C-
	 * @param semitone semitones added to base note for calculating higher pitches
	 * @return true audio pitch for a given note (in Hertz)
	 */
	public double getTruePitch(double tuning, int temperament, int basenote, int semitone) {
		int notesnum = 12; // unless specified otherwise
		int note = (semitone + basenote) % notesnum; // current note
		double ratio;
		double octave = 2; // an octave is usually the frequency of a note multiplied by 2
		double multi;

		// Equal temperament is generated using the 12th root of 2
		if (temperament == NO_TEMPERAMENT) {
			ratio = Math.pow(2.0, 1.0 / 12.0);
			return (tuning / 64) * Math.pow(ratio, semitone + basenote);
		}
		if (temperament >= TUNING_CUSTOM) { // custom temperament will be used using ratio
			octave = custom[notesnum];
			ratio = custom[note];
		} else { // any temperament preset will be used
			notesnum = computeNotesPerOctave(temperament);
			octave = TEMPERAMENT_PRESETS[temperament][notesnum];
			note = (semitone + basenote) % notesnum;
			ratio = TEMPERAMENT_PRESETS[temperament][note];
		}
		multi = Math.pow(octave, (semitone + basenote) / notesnum); // integer division, matching C++'s int/int then trunc()
		return (tuning / 64) * (multi * ratio);
	}

	// Shared by getTruePitch()'s preset branch and initTuning() - both scan
	// for the first zero/padding entry in a temperament_preset row to find
	// how many notes per octave that preset actually defines (C++ duplicates
	// this loop in both CTuning::GetTruePitch() and CTuning::InitTuning();
	// unified here since nothing depends on keeping them separate).
	private int computeNotesPerOctave(int temperament) {
		int notesPerOctave = 12;
		for (int i = 0; i < PRESETS_LENGTH; i++) {
			if (TEMPERAMENT_PRESETS[temperament][i] != 0) {
				continue;
			}
			notesPerOctave = i - 1;
			break;
		}
		return notesPerOctave;
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
		for (int i = 0; i < length; i++) {
			int note = i + semitone;
			double pitch = getTruePitch(tuningSettings.basetuning, tuningSettings.temperament, tuningSettings.basenote, note);
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
	 * @param tuningSettings the base tuning/temperament/basenote to generate the tables from (C++ reads these from the g_tuning global instead)
	 * @param tuningRatios the custom-temperament ratios to generate the tables from (C++ reads these from the g_tuningRatios global instead)
	 */
	public void initTuning(byte[] tableMemory, TuningSettings tuningSettings, TuningRatios tuningRatios) {
		if (tuningSettings.basetuning == 0) {
			// if base tuning is 0.0, the C++ original shows a MessageBox and
			// calls exit(1) to avoid crashing later - not appropriate for a
			// Java library class, so this throws instead.
			throw new IllegalStateException("An invalid tuning has been detected: basetuning is zero.");
		}

		int notesPerOctave = 12; // by default, an octave uses 12 semitones...
		if (tuningSettings.temperament > NO_TEMPERAMENT && tuningSettings.temperament < TUNING_CUSTOM) {
			// ...unless it is specified otherwise in the Temperament presets
			notesPerOctave = computeNotesPerOctave(tuningSettings.temperament);
		}

		// calculate the custom ratio used for each semitone
		custom[0] = tuningRatios.unison.doubleValue();
		custom[1] = tuningRatios.min2nd.doubleValue();
		custom[2] = tuningRatios.maj2nd.doubleValue();
		custom[3] = tuningRatios.min3rd.doubleValue();
		custom[4] = tuningRatios.maj3rd.doubleValue();
		custom[5] = tuningRatios.perf4th.doubleValue();
		custom[6] = tuningRatios.tritone.doubleValue();
		custom[7] = tuningRatios.perf5th.doubleValue();
		custom[8] = tuningRatios.min6th.doubleValue();
		custom[9] = tuningRatios.maj6th.doubleValue();
		custom[10] = tuningRatios.min7th.doubleValue();
		custom[11] = tuningRatios.maj7th.doubleValue();
		custom[12] = tuningRatios.octave.doubleValue();

		// Generate all lookup tables used by the RMT driver for tuning purposes.

		// Distortion 2, at 0xB000
		generateTable(tableMemory, 0x000, 64, DIST_2_BELL.table64khz() * notesPerOctave, Timbre.BELL, 0x00, tuningSettings);
		generateTable(tableMemory, 0x040, 64, DIST_2_BELL.table179mhz() * notesPerOctave, Timbre.BELL, 0x40, tuningSettings);
		generateTable(tableMemory, 0x080, 64, DIST_2_BELL.table16bit() * notesPerOctave, Timbre.BELL, 0x50, tuningSettings);
		// no 15kHz table...

		// Distortion 4 (Smooth), at 0xB100
		generateTable(tableMemory, 0x100, 64, DIST_4_SMOOTH.table64khz() * notesPerOctave, Timbre.SMOOTH_4, 0x00, tuningSettings);
		generateTable(tableMemory, 0x140, 64, DIST_4_SMOOTH.table179mhz() * notesPerOctave, Timbre.SMOOTH_4, 0x40, tuningSettings);
		generateTable(tableMemory, 0x180, 64, DIST_4_SMOOTH.table16bit() * notesPerOctave, Timbre.SMOOTH_4, 0x50, tuningSettings);
		// no 15kHz table...

		// Distortion A (Pure), at 0xB200
		generateTable(tableMemory, 0x200, 64, DIST_A_PURE.table64khz() * notesPerOctave, Timbre.PURE_A, 0x00, tuningSettings);
		generateTable(tableMemory, 0x240, 64, DIST_A_PURE.table179mhz() * notesPerOctave, Timbre.PURE_A, 0x40, tuningSettings);
		generateTable(tableMemory, 0x280, 64, DIST_A_PURE.table16bit() * notesPerOctave, Timbre.PURE_A, 0x50, tuningSettings);
		generateTable(tableMemory, 0x580, 64, DIST_A_PURE.table15khz() * notesPerOctave, Timbre.PURE_A, 0x01, tuningSettings);

		// Distortion C (Buzzy), at 0xB300
		generateTable(tableMemory, 0x300, 64, DIST_C_BUZZY.table64khz() * notesPerOctave, Timbre.BUZZY_C, 0x00, tuningSettings);
		generateTable(tableMemory, 0x340, 64, DIST_C_BUZZY.table179mhz() * notesPerOctave, Timbre.BUZZY_C, 0x40, tuningSettings);
		generateTable(tableMemory, 0x380, 64, DIST_C_BUZZY.table16bit() * notesPerOctave, Timbre.BUZZY_C, 0x50, tuningSettings);
		generateTable(tableMemory, 0x5C0, 64, DIST_C_BUZZY.table15khz() * notesPerOctave, Timbre.BUZZY_C, 0x01, tuningSettings);

		// Distortion C (Gritty), at 0xB400
		generateTable(tableMemory, 0x400, 64, DIST_C_GRITTY.table64khz() * notesPerOctave, Timbre.GRITTY_C, 0x00, tuningSettings);
		generateTable(tableMemory, 0x440, 64, DIST_C_GRITTY.table179mhz() * notesPerOctave, Timbre.GRITTY_C, 0x40, tuningSettings);
		generateTable(tableMemory, 0x480, 64, DIST_C_GRITTY.table16bit() * notesPerOctave, Timbre.GRITTY_C, 0x50, tuningSettings);
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
