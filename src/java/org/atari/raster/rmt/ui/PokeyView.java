package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.Atari;
import org.atari.raster.rmt.model.Notes;
import org.atari.raster.rmt.model.Tuning;
import org.atari.raster.rmt.model.TuningSettings;

/**
 * Ported from CPokeyView (src/cpp/PokeyView.h/.cpp) - the mini-font
 * tuning line and per-POKEY register dump ("POKEY REGISTERS") drawn to the
 * right of the tracks screen. Reads the register shadow at
 * {@code $D200}/{@code $D210} from the emulated Atari's memory and the
 * tuning globals ({@code g_tuning.basetuning/basenote},
 * {@code g_notesperoctave}) from the explicitly passed objects.
 *
 * <p>In the Pokey Explorer mode ({@code explorerMode}) three rows below the
 * register dumps detail the {@link PokeyController}'s channel: the
 * register bytes, the pitch formula's coarse divisor, free divisor and
 * modulo offset ({@link #explorerValues}) and the pitch
 * ({@link Tuning#getPitch}).
 */
public final class PokeyView {

	private static final int[] POKEY_ADDRESS = { 0xd200, 0xd210 };

	/** The explorer rows' numbers for a channel: the pitch formula's coarse divisor and modulo offset from the AUDCTL bits, and the "modulo" (the first divisor 3..255 of {@code audf + modoffset}, 255 when none). */
	record ExplorerValues(int coarseDivisor, int modoffset, int modulo) {
	}

	static ExplorerValues explorerValues(int audf, boolean join16bit, boolean clock179, boolean clock15) {
		// Always initialised to 1 to avoid a division by 0 error
		int modoffset = 1;
		int coarseDivisor = 1;

		// Set the divisor and modoffset variables based on the AUDCTL bits currently set
		if (join16bit) {
			modoffset = 7;
		} else if (clock179) {
			modoffset = 4;
		} else {
			coarseDivisor = clock15 ? 114 : 28;
		}

		// Identify the first modulo value that results to 0 when used
		int modulo = 0; // Does not matter right now, used in tandem with e_valid
		for (int i = 3; i < 256; i++) {
			modulo = i;
			if ((audf + modoffset) % i == 0) {
				break;
			}
		}
		return new ExplorerValues(coarseDivisor, modoffset, modulo);
	}

	private final Canvas canvas;

	public PokeyView(Canvas canvas) {
		this.canvas = canvas;
	}

	/** C's {@code round()}: half away from zero (Java's {@code Math.round} rounds half up). */
	private static double cRound(double x) {
		return x < 0 ? -Math.floor(-x + 0.5) : Math.floor(x + 0.5);
	}

	public void draw(boolean stereo, Tuning tuning, TuningSettings tuningSettings, int notesPerOctave, Atari atari, boolean explorerMode, PokeyController pokeyController) {
		final int tuningRow = 0;
		final int pokeyRows = 9;

		final int pokey1Row = tuningRow + 3;
		final int pokey2Row = pokey1Row + pokeyRows;
		final int explorerRow = pokey2Row + pokeyRows;

		// Tuning
		final double basetuning = tuningSettings.basetuning;
		final int basenote = tuningSettings.basenote;
		final int reverseBasenote = (24 - basenote) % notesPerOctave;
		canvas.colorMini(TextMiniColor.GRAY).at(0, tuningRow).printMini("A- TUNING:       HZ, PAL  , FREQ:        HZ, CYCLES: ");
		canvas.printfMini(2, "%s", Notes.getNote(reverseBasenote)); // overwrite A- with the given basenote
		canvas.colorMini(TextMiniColor.WHITE).atColumn(11).printfMini(10, "%3.2f", basetuning);
		canvas.colorMini(TextMiniColor.BLUE).atColumn(21).printMini(atari.isNTSC() ? "NTSC" : "PAL");
		canvas.colorMini(TextMiniColor.WHITE);
		canvas.atColumn(34).printfMini(7, "%d", atari.getClockFrequency());
		canvas.atColumn(53).printfMini(7, "%d", atari.getFrameCycleCount());

		// Pokeys
		canvas.colorMini(TextMiniColor.GRAY);
		int pokeyCount;
		if (stereo) {
			pokeyCount = 2;
			canvas.at(0, pokey1Row).printMini("POKEY REGISTERS (LEFT)");
			canvas.at(0, pokey2Row).printMini("POKEY REGISTERS (RIGHT)");
		} else {
			pokeyCount = 1;
			canvas.at(0, pokey1Row).printMini("POKEY REGISTERS");
		}

		final byte[] memory = atari.getMemory();
		for (int pokey = 0; pokey < pokeyCount; pokey++) {

			// Addresses
			final int pokeyAddress = POKEY_ADDRESS[pokey];
			final int audf3Address = pokeyAddress + 0x4;

			final int audctlAddress = pokeyAddress + 0x08;
			final int skctlAddress = pokeyAddress + 0x0f;

			// Values
			final int skctl = memory[skctlAddress] & 0xFF;
			final int audctl = memory[audctlAddress] & 0xFF;

			// Bits
			final boolean CLOCK_15 = (audctl & 0x01) != 0;
			final boolean HPF_CH24 = (audctl & 0x02) != 0;
			final boolean HPF_CH13 = (audctl & 0x04) != 0;
			final boolean JOIN_34 = (audctl & 0x08) != 0;
			final boolean JOIN_12 = (audctl & 0x10) != 0;
			final boolean CH3_179 = (audctl & 0x20) != 0;
			final boolean CH1_179 = (audctl & 0x40) != 0;
			final boolean POLY9 = (audctl & 0x80) != 0;
			final boolean TWO_TONE = skctl == 0x8B;

			// Rows
			final int pokeyBaseRow = (pokey == 0) ? pokey1Row : pokey2Row;
			final int channelBaseRow = pokeyBaseRow + 2;
			final int audctlRow = channelBaseRow + 4;
			final int skctlRow = audctlRow + 1;

			// Print
			canvas.colorMini(TextMiniColor.GRAY).at(0, audctlRow).printfMini(8, "$%X: $", audctlAddress).colorMini(TextMiniColor.WHITE).atColumn(8).printByte(audctl).nextRow(); // C++'s "%0hX" (a zero flag without a width does nothing)
			if (POLY9) {
				canvas.atColumn(11).printMini("POLY9 ENABLED");
			}

			canvas.colorMini(TextMiniColor.GRAY).at(0, skctlRow).printfMini(8, "$%X: $", skctlAddress).colorMini(TextMiniColor.WHITE).atColumn(8).printByte(skctl);

			canvas.colorMini(TextMiniColor.BLUE);

			if (TWO_TONE) {
				canvas.atColumn(11).printMini("CH1: TWO TONE FILTER");
			}
			if (HPF_CH24) {
				canvas.atColumn(32).printMini("CH2: HIGH PASS FILTER");
			}

			final int POKEY_CHANNELS = 4;
			for (int pokeyChannel = 0; pokeyChannel < POKEY_CHANNELS; pokeyChannel++) {

				// Addresses
				final int audfAddress = pokeyAddress + pokeyChannel * 2;

				// Values
				final int audf = memory[audfAddress] & 0xFF;
				final int audc = memory[audfAddress + 1] & 0xFF;

				final int vol = audc & 0x0f;
				final int dist = audc & 0xf0;

				// Combined values
				int audfLow;
				int audcLow;
				int volLow;
				int audf16;

				if (pokeyChannel % 2 == 1) { // only in valid 16-bit channels
					final int audfLowAddress = audfAddress - 2;
					audfLow = memory[audfLowAddress] & 0xFF;
					audcLow = memory[audfLowAddress + 1] & 0xFF;
					volLow = audcLow & 0x0f;
					audf16 = audf << 9 | audfLow;
				} else {
					audfLow = 0;
					volLow = 0;
					audf16 = 0;
				}

				// Compute combined modes for some special output.
				final boolean SAWTOOTH = CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (pokeyChannel == 0);
				final boolean SAWTOOTH_INVERTED = false;
				final boolean JOIN_16BIT = (JOIN_12 && CH1_179 && (pokeyChannel == 1)) || (JOIN_34 && CH3_179 && (pokeyChannel == 3));
				final boolean JOIN_64KHZ = (JOIN_12 && !CH1_179 && !CLOCK_15 && (pokeyChannel == 1)) || (JOIN_34 && !CH3_179 && !CLOCK_15 && (pokeyChannel == 3));
				final boolean JOIN_15KHZ = (JOIN_12 && !CH1_179 && CLOCK_15 && (pokeyChannel == 1)) || (JOIN_34 && !CH3_179 && CLOCK_15 && (pokeyChannel == 3));
				final boolean JOIN_WRONG = ((JOIN_12 && (pokeyChannel == 0)) || (JOIN_34 && (pokeyChannel == 2))) && (vol == 0x00); // 16-bit, invalid channel, no volume
				final boolean REVERSE_16 = ((JOIN_12 && (pokeyChannel == 0)) || (JOIN_34 && (pokeyChannel == 2))) && (vol > 0x00); // 16-bit, invalid channel, with volume (Reverse-16)
				final boolean CLOCK_179 = (CH1_179 && (pokeyChannel == 0)) || (CH3_179 && (pokeyChannel == 2));
				boolean EFFECTIVE_CLOCK_15 = CLOCK_15;
				if (JOIN_16BIT || CLOCK_179) {
					EFFECTIVE_CLOCK_15 = false;
				} // Override, these 2 take priority over 15khz mode

				final int iAudf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
				final double PITCH = tuning.getPOKEYPitch(audc, iAudf, audctl, pokeyChannel);

				// Rows
				final int channelRow = channelBaseRow + pokeyChannel;

				// Print
				canvas.colorMini(TextMiniColor.GRAY);
				canvas.at(0, channelRow).printfMini(5, "$%04X", audfAddress).atColumn(5).printMini(": $   $     PITCH = $     (         HZ ---  +  ), VOL = $ , DIST = $ ,");

				canvas.colorMini(TextMiniColor.WHITE);
				canvas.atColumn(8).printByte(audf);
				canvas.atColumn(12).printByte(audc);
				canvas.atColumn(26).printByte(audf);

				if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && volLow == 0) { // 16-bit without Reverse-16 output
					canvas.atColumn(28).printByte(audfLow);
				}
				canvas.atColumn(32).printfMini(10, "%9.2f", PITCH);
				canvas.atColumn(62).printNibble(vol);

				// Do not display distortion in volume-only mode
				if (dist != 0x10) { // C++'s "(dist == 0x10) == 0x00"
					canvas.atColumn(73).printNibble(dist >> 4);
				}

				// Channel suffix
				String text;
				if (EFFECTIVE_CLOCK_15) { // 15khz
					text = "15KHZ";
				} else {
					text = "64KHZ";
				}

				if (CLOCK_179) {
					text = "1.79MHZ";
				}

				if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) {
					text = "16-BIT";
				}

				canvas.colorMini(TextMiniColor.BLUE).atColumn(76).printMini(text);

				// AUDCTL row, channel-specific additions
				if (HPF_CH13) {
					canvas.colorMini(TextMiniColor.BLUE).at(32, audctlRow);
					if (SAWTOOTH && !SAWTOOTH_INVERTED) {
						canvas.printMini("CH1: HIGH PASS FILTER, SAWTOOTH");
					} else {
						if (SAWTOOTH && SAWTOOTH_INVERTED) {
							canvas.printMini("CH1: HIGH PASS FILTER, SAWTOOTH (INVERTED)");
						} else {
							canvas.printMini("CH1: HIGH PASS FILTER");
						}
					}
				}

				// SKCTL row, channel-specific additions
				canvas.colorMini(TextMiniColor.BLUE);
				if (HPF_CH24) {
					canvas.at(32, skctlRow).printMini("CH2: HIGH PASS FILTER");
				}

				if (REVERSE_16) {
					if (pokeyChannel == 0) {
						canvas.at(54, audctlRow).printMini("CH1: REVERSE - 16 OUTPUT");
					} else if (pokeyChannel == 2) {
						canvas.at(54, audctlRow).printMini("CH3: REVERSE - 16 OUTPUT");
					}
				}

				// Pokey Explorer Mode
				if (explorerMode) {
					final int eChannelIndex = pokey * POKEY_CHANNELS + pokeyChannel;
					if (pokeyController.getChannelIndex() == eChannelIndex) {
						final int row = explorerRow;

						canvas.colorMini(TextMiniColor.GRAY).at(0, row);
						canvas.printMini("CH_IDX:   , AUDF: $     , AUDC: $   , MODULO:    ").nextRow();
						canvas.printMini("COARSE_DIVISOR:    , DIVISOR:       , MODOFFSET:  ").nextRow();
						canvas.printMini("         HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2");

						final ExplorerValues e = explorerValues(audf, JOIN_16BIT, CLOCK_179, CLOCK_15);
						final double eDivisor = pokeyController.getDivisor();
						final double ePitch = tuning.getPitch(iAudf, e.coarseDivisor(), eDivisor, e.modoffset());

						canvas.colorMini(TextMiniColor.WHITE).at(0, row);
						canvas.atColumn(8).printfMini(1, "%d", eChannelIndex);
						canvas.atColumn(19).printByte(audf);
						if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) {
							canvas.atColumn(61).printByte(audfLow);
						}
						canvas.atColumn(33).printByte(audc);
						canvas.atColumn(46).printfMini(3, "%d", e.modulo()).nextRow();

						canvas.atColumn(16).printfMini(3, "%d", e.coarseDivisor());
						canvas.atColumn(30).printfMini(9, "%6.1f", eDivisor);
						canvas.atColumn(49).printfMini(3, "%d", e.modoffset()).nextRow();
						canvas.atColumn(0).printfMini(9, "%9.2f", ePitch);
					}
				}

				if (PITCH != 0) { // If 0.0 is read, there is nothing to show. The volume-only mode or invalid parameters may result in this.
					if (JOIN_WRONG) { // 16-bit, but wrong channels, and the volume is 0
						// Masking parts of the line which are invalid
						canvas.at(17, channelRow).printMini("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
					} else {
						// Most of the lines below could get some improvements...
						final double centnum = 1200 * (Math.log(PITCH / basetuning) / Math.log(2));
						final int notenum = (int) cRound(centnum * 0.01) + 60;
						final int octave = (((notenum + 96) - basenote) / notesPerOctave) - 8;
						final int cents = (int) cRound(centnum - (notenum - 60) * 100);

						canvas.colorMini(TextMiniColor.WHITE).at(49, channelRow).printfMini(3, "%03d", cents);
						canvas.colorMini(TextMiniColor.GRAY).printMini((cents >= 0) ? "+" : "-");

						int note = ((notenum + 96) - basenote) % notesPerOctave;
						if (note < 0) {
							note *= -1; // Invert the negative to prevent going out of bounds
						}

						canvas.colorMini(TextMiniColor.WHITE).at(44, channelRow);
						canvas.printfMini(2, "%s", Notes.getNote(note)).atColumn(46).printfMini(1, "%1d", octave);
					}
				}
			}
		}
	}
}
