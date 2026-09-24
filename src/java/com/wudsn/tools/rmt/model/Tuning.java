package com.wudsn.tools.rmt.model;

/**
 * Ported from CTuning (src/cpp/Tuning.h/.cpp) - the pure pitch-math subset
 * only: getPitch()/getAUDF()/getPOKEYPitch(), matching the class's own C++
 * split. CTuning::GenerateTable()/InitTuning() (implemented in
 * src/cpp/TuningTables.cpp) read C++ global tuning state and have no
 * existing characterization tests to port against, so they're deferred to a
 * follow-up batch rather than guessed at here.
 *
 * <p>C++'s test-only constructor (which bypasses InitTuning()'s global-state
 * dependency) becomes the only constructor here, since this class doesn't
 * carry the table-generation half that the two-argument C++ InitTuning()
 * would otherwise need to set the clock frequency for.
 */
public final class Tuning {

	private final int clockFrequency;

	public Tuning(int clockFrequency) {
		this.clockFrequency = clockFrequency;
	}

	/**
	 * Calculate the true audio pitch output using the given parameters.
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
}
