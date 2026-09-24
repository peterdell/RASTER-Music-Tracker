package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/InstrumentsTests.cpp's already-tested subset (see
 * Instruments's own javadoc for what's deferred and why). C++'s
 * g_tracks4_8/g_keyboard_RememberOctavesAndVolumes globals become explicit
 * {@code stereo}/{@code rememberOctavesAndVolumes} parameters here.
 */
class InstrumentsTest {

	private static final int INSTR = 0;

	private Instruments instruments;

	@BeforeEach
	void setUp() {
		instruments = new Instruments();
	}

	private static void setUpSampleInstrument(Instruments instruments) {
		Instrument ai = instruments.getInstrument(INSTR);
		int[] par = ai.parameters;
		par[Instrument.PAR_TBL_LENGTH] = 1;
		par[Instrument.PAR_TBL_GOTO] = 0;
		par[Instrument.PAR_ENV_LENGTH] = 1;
		par[Instrument.PAR_ENV_GOTO] = 0;
		par[Instrument.PAR_TBL_TYPE] = 0;
		par[Instrument.PAR_TBL_MODE] = 0;
		par[Instrument.PAR_TBL_SPEED] = 5;
		par[Instrument.PAR_AUDCTL_15KHZ] = 1;
		par[Instrument.PAR_AUDCTL_HPF_CH2] = 0;
		par[Instrument.PAR_AUDCTL_HPF_CH1] = 1;
		par[Instrument.PAR_AUDCTL_JOIN_3_4] = 0;
		par[Instrument.PAR_AUDCTL_JOIN_1_2] = 0;
		par[Instrument.PAR_AUDCTL_179_CH3] = 0;
		par[Instrument.PAR_AUDCTL_179_CH1] = 0;
		par[Instrument.PAR_AUDCTL_POLY9] = 0;
		par[Instrument.PAR_VOL_FADEOUT] = 3;
		par[Instrument.PAR_VOL_MIN] = 2;
		par[Instrument.PAR_DELAY] = 4;
		par[Instrument.PAR_VIBRATO] = 1;
		par[Instrument.PAR_FREQ_SHIFT] = 7;

		ai.noteTable[0] = 10;
		ai.noteTable[1] = 20;

		int[] env0 = ai.envelope[0];
		env0[EnvelopeParameter.VOLUMER] = 3;
		env0[EnvelopeParameter.VOLUMEL] = 5;
		env0[EnvelopeParameter.DISTORTION] = 4;
		env0[EnvelopeParameter.COMMAND] = 2;
		env0[EnvelopeParameter.X] = 6;
		env0[EnvelopeParameter.Y] = 9;
		env0[EnvelopeParameter.FILTER] = 1;
		env0[EnvelopeParameter.PORTAMENTO] = 1;

		int[] env1 = ai.envelope[1];
		env1[EnvelopeParameter.VOLUMER] = 7;
		env1[EnvelopeParameter.VOLUMEL] = 8;
		env1[EnvelopeParameter.DISTORTION] = 2;
		env1[EnvelopeParameter.COMMAND] = 1;
		env1[EnvelopeParameter.X] = 3;
		env1[EnvelopeParameter.Y] = 4;
		env1[EnvelopeParameter.FILTER] = 0;
		env1[EnvelopeParameter.PORTAMENTO] = 0;
	}

	@Nested
	class InstrumentAtaFormatTest {

		@Test
		void instrToAtaMonoEncodesExactBytes() {
			setUpSampleInstrument(instruments);

			byte[] ata = new byte[32];
			int size = instruments.instrToAta(INSTR, ata, false); // mono

			assertEquals(20, size);
			byte[] expected = {
					0x0D, 0x0C, 0x11, 0x0E, 0x05, 0x05, 0x03, 0x20, 0x04, 0x01,
					0x07, 0x00, 0x0A, 0x14, 0x55, (byte) 0xA5, 0x69, (byte) 0x88, 0x12, 0x34 };
			for (int i = 0; i < size; i++) {
				assertEquals(expected[i], ata[i], "at index " + i);
			}
		}

		// Mono packing only stores one volume nibble per envelope entry
		// ("VOLUME R = VOLUME L", per instrToAta()'s own comment), so decoding a
		// mono-encoded instrument loses the original, distinct VOLUMER value -
		// this characterizes that intentional lossiness rather than treating it
		// as a bug.
		@Test
		void ataToInstrMonoRoundTripLosesVolumeR() {
			setUpSampleInstrument(instruments);

			byte[] ata = new byte[32];
			int size = instruments.instrToAta(INSTR, ata, false); // mono

			int decodedInstr = 1;
			assertTrue(instruments.ataToInstr(ata, decodedInstr, false));
			Instrument decoded = instruments.getInstrument(decodedInstr);

			assertEquals(1, decoded.parameters[Instrument.PAR_TBL_LENGTH]);
			assertEquals(1, decoded.parameters[Instrument.PAR_ENV_LENGTH]);
			assertEquals(5, decoded.parameters[Instrument.PAR_TBL_SPEED]);
			assertEquals(1, decoded.parameters[Instrument.PAR_AUDCTL_15KHZ]);
			assertEquals(1, decoded.parameters[Instrument.PAR_AUDCTL_HPF_CH1]);
			assertEquals(3, decoded.parameters[Instrument.PAR_VOL_FADEOUT]);
			assertEquals(2, decoded.parameters[Instrument.PAR_VOL_MIN]);
			assertEquals(4, decoded.parameters[Instrument.PAR_DELAY]);
			assertEquals(1, decoded.parameters[Instrument.PAR_VIBRATO]);
			assertEquals(7, decoded.parameters[Instrument.PAR_FREQ_SHIFT]);
			assertEquals(10, decoded.noteTable[0]);
			assertEquals(20, decoded.noteTable[1]);

			int[] env0 = decoded.envelope[0];
			assertEquals(5, env0[EnvelopeParameter.VOLUMER]); // lossy: collapsed to VOLUMEL, not the original 3
			assertEquals(5, env0[EnvelopeParameter.VOLUMEL]);
			assertEquals(4, env0[EnvelopeParameter.DISTORTION]);
			assertEquals(2, env0[EnvelopeParameter.COMMAND]);
			assertEquals(6, env0[EnvelopeParameter.X]);
			assertEquals(9, env0[EnvelopeParameter.Y]);
			assertEquals(1, env0[EnvelopeParameter.FILTER]);
			assertEquals(1, env0[EnvelopeParameter.PORTAMENTO]);
		}

		// The stereo path stores both volume nibbles separately, so (unlike mono)
		// VOLUMER round-trips exactly.
		@Test
		void ataToInstrStereoRoundTripPreservesBothVolumes() {
			setUpSampleInstrument(instruments);

			byte[] ata = new byte[32];
			int size = instruments.instrToAta(INSTR, ata, true); // stereo

			assertEquals(20, size);
			byte[] expected = {
					0x0D, 0x0C, 0x11, 0x0E, 0x05, 0x05, 0x03, 0x20, 0x04, 0x01,
					0x07, 0x00, 0x0A, 0x14, 0x35, (byte) 0xA5, 0x69, 0x78, 0x12, 0x34 };
			for (int i = 0; i < size; i++) {
				assertEquals(expected[i], ata[i], "at index " + i);
			}

			int decodedInstr = 1;
			assertTrue(instruments.ataToInstr(ata, decodedInstr, true));
			Instrument decoded = instruments.getInstrument(decodedInstr);

			assertEquals(3, decoded.envelope[0][EnvelopeParameter.VOLUMER]);
			assertEquals(5, decoded.envelope[0][EnvelopeParameter.VOLUMEL]);
			assertEquals(7, decoded.envelope[1][EnvelopeParameter.VOLUMER]);
			assertEquals(8, decoded.envelope[1][EnvelopeParameter.VOLUMEL]);
		}

		@Test
		void ataToInstrRejectsOutOfBoundsEnvelope() {
			byte[] ata = new byte[32];
			ata[0] = 12; // note table length 0, fine
			ata[1] = 12;
			ata[2] = (byte) (12 + 1 + (Instrument.ENVELOPE_MAX_COLUMNS) * 3); // envelopeLength == ENVELOPE_MAX_COLUMNS -> out of bounds
			ata[3] = ata[2];

			assertFalse(instruments.ataToInstr(ata, INSTR, false));
		}

		@Test
		void ataV0ToInstrDecodesOldFormat() {
			byte[] ata = new byte[32];
			for (int i = 0; i < 8; i++) {
				ata[i] = (byte) (i + 1); // note table 1..8
			}
			ata[8] = 0x0A; // ENV_LENGTH=1, TBL_LENGTH=2
			ata[9] = 0x01; // ENV_GOTO=0, TBL_GOTO=1
			ata[10] = 0x45; // TBL_TYPE=0, TBL_MODE=1, TBL_SPEED=5
			ata[11] = 9; // VOL_FADEOUT
			ata[12] = 0x33; // VOL_MIN=3, 15KHZ=1, POLY9=1
			ata[13] = 6; // DELAY
			ata[14] = 2; // VIBRATO
			ata[15] = 11; // FREQ_SHIFT
			ata[16] = 0x37;
			ata[17] = (byte) 0x94;
			ata[18] = 0x5B; // envelope entry 0
			ata[19] = 0x2C;
			ata[20] = 0x61;
			ata[21] = (byte) 0x8F; // envelope entry 1

			assertTrue(instruments.ataV0ToInstr(ata, INSTR, false)); // mono (matches C++ test's g_tracks4_8 = 4)

			Instrument ai = instruments.getInstrument(INSTR);
			for (int i = 0; i < 8; i++) {
				assertEquals(i + 1, ai.noteTable[i]);
			}
			assertEquals(1, ai.parameters[Instrument.PAR_ENV_LENGTH]);
			assertEquals(2, ai.parameters[Instrument.PAR_TBL_LENGTH]);
			assertEquals(0, ai.parameters[Instrument.PAR_ENV_GOTO]);
			assertEquals(1, ai.parameters[Instrument.PAR_TBL_GOTO]);
			assertEquals(0, ai.parameters[Instrument.PAR_TBL_TYPE]);
			assertEquals(1, ai.parameters[Instrument.PAR_TBL_MODE]);
			assertEquals(5, ai.parameters[Instrument.PAR_TBL_SPEED]);
			assertEquals(9, ai.parameters[Instrument.PAR_VOL_FADEOUT]);
			assertEquals(3, ai.parameters[Instrument.PAR_VOL_MIN]);
			assertEquals(1, ai.parameters[Instrument.PAR_AUDCTL_15KHZ]);
			assertEquals(1, ai.parameters[Instrument.PAR_AUDCTL_POLY9]);
			assertEquals(0, ai.parameters[Instrument.PAR_AUDCTL_HPF_CH2]);
			assertEquals(6, ai.parameters[Instrument.PAR_DELAY]);
			assertEquals(2, ai.parameters[Instrument.PAR_VIBRATO]);
			assertEquals(11, ai.parameters[Instrument.PAR_FREQ_SHIFT]);

			int[] env0 = ai.envelope[0];
			assertEquals(7, env0[EnvelopeParameter.VOLUMER]); // mono: R == L
			assertEquals(7, env0[EnvelopeParameter.VOLUMEL]);
			assertEquals(1, env0[EnvelopeParameter.FILTER]);
			assertEquals(1, env0[EnvelopeParameter.COMMAND]);
			assertEquals(4, env0[EnvelopeParameter.DISTORTION]);
			assertEquals(0, env0[EnvelopeParameter.PORTAMENTO]);
			assertEquals(5, env0[EnvelopeParameter.X]);
			assertEquals(11, env0[EnvelopeParameter.Y]);

			int[] env1 = ai.envelope[1];
			assertEquals(12, env1[EnvelopeParameter.VOLUMEL]);
			assertEquals(0, env1[EnvelopeParameter.FILTER]);
			assertEquals(6, env1[EnvelopeParameter.COMMAND]);
			assertEquals(0, env1[EnvelopeParameter.DISTORTION]);
			assertEquals(1, env1[EnvelopeParameter.PORTAMENTO]);
			assertEquals(8, env1[EnvelopeParameter.X]);
			assertEquals(15, env1[EnvelopeParameter.Y]);
		}
	}

	@Nested
	class InstrumentsCoreTest {

		@Test
		void clearInstrumentResetsToStartupDefaults() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 5;
			ai.octave = 3;
			ai.volume = 2;
			ai.activeEditSection = InstrumentSection.NAME;

			instruments.clearInstrument(INSTR);

			assertEquals("Instrument 00  ", new String(ai.name, 0, 15));
			assertEquals(InstrumentSection.ENVELOPE, ai.activeEditSection);
			assertEquals(0, ai.editNameCursorPos);
			assertEquals(Instrument.PAR_ENV_LENGTH, ai.editParameterNr);
			assertEquals(0, ai.editEnvelopeX);
			assertEquals(1, ai.editEnvelopeY);
			assertEquals(0, ai.editNoteTableCursorPos);
			assertEquals(0, ai.octave);
			assertEquals(15, ai.volume); // MAXVOLUME (SongTypes.h, not otherwise needed here)
			assertEquals(0, ai.parameters[Instrument.PAR_ENV_LENGTH]);
		}

		@Test
		void clearInstrumentIgnoresOutOfRangeIndex() {
			instruments.clearInstrument(-1);
			instruments.clearInstrument(Instruments.INSTRSNUM);
			// No crash - nothing further to assert (getInstrument() guards both).
		}

		@Test
		void setEnvelopeVolumeSetsLeftChannelInMonoModeRegardlessOfRightFlag() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 1;

			instruments.setEnvelopeVolume(INSTR, true, 0, 9, false); // mono

			assertEquals(9, ai.envelope[0][EnvelopeParameter.VOLUMEL]);
			assertEquals(0, ai.envelope[0][EnvelopeParameter.VOLUMER]);
		}

		@Test
		void setEnvelopeVolumeSetsRightChannelInStereoModeWhenRequested() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 1;

			instruments.setEnvelopeVolume(INSTR, true, 0, 9, true); // stereo

			assertEquals(9, ai.envelope[0][EnvelopeParameter.VOLUMER]);
			assertEquals(0, ai.envelope[0][EnvelopeParameter.VOLUMEL]);
		}

		@Test
		void setEnvelopeVolumeIgnoresOutOfRangePosition() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 1;

			instruments.setEnvelopeVolume(INSTR, false, -1, 9, false);
			instruments.setEnvelopeVolume(INSTR, false, 2, 9, false); // > PAR_ENV_LENGTH + 1

			assertEquals(0, ai.envelope[0][EnvelopeParameter.VOLUMEL]);
		}

		@Test
		void setEnvelopeVolumeIgnoresOutOfRangeVolume() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 1;

			instruments.setEnvelopeVolume(INSTR, false, 0, -1, false);
			instruments.setEnvelopeVolume(INSTR, false, 0, 16, false);

			assertEquals(0, ai.envelope[0][EnvelopeParameter.VOLUMEL]);
		}

		@Test
		void memorizeOctaveAndVolumeStoresBothWhenEnabled() {
			Instrument ai = instruments.getInstrument(INSTR);

			instruments.memorizeOctaveAndVolume(INSTR, 3, 10, true);

			assertEquals(3, ai.octave);
			assertEquals(10, ai.volume);
		}

		@Test
		void memorizeOctaveAndVolumeIgnoresNegativeValues() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.octave = 3;
			ai.volume = 10;

			instruments.memorizeOctaveAndVolume(INSTR, -1, -1, true);

			assertEquals(3, ai.octave);
			assertEquals(10, ai.volume);
		}

		@Test
		void memorizeOctaveAndVolumeDoesNothingWhenDisabled() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.octave = 3;
			ai.volume = 10;

			instruments.memorizeOctaveAndVolume(INSTR, 5, 12, false);

			assertEquals(3, ai.octave);
			assertEquals(10, ai.volume);
		}

		@Test
		void rememberOctaveAndVolumeReadsBothWhenEnabled() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.octave = 4;
			ai.volume = 11;

			Instruments.OctaveAndVolume result = instruments.rememberOctaveAndVolume(INSTR, -99, -99, true);

			assertEquals(4, result.octave());
			assertEquals(11, result.volume());
		}

		@Test
		void rememberOctaveAndVolumeDoesNothingWhenDisabled() {
			Instrument ai = instruments.getInstrument(INSTR);
			ai.octave = 4;
			ai.volume = 11;

			Instruments.OctaveAndVolume result = instruments.rememberOctaveAndVolume(INSTR, -99, -99, false);

			assertEquals(-99, result.octave());
			assertEquals(-99, result.volume());
		}
	}
}
