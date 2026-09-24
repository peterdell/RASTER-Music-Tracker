package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/LzssTests.cpp. */
class CompressLzssTest {

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	@Nested
	class OptimiseAudcTest {

		@Test
		void zeroVolumeClearsDistortionBits() {
			// AUDC0: dist=0x30, vol=0
			byte[] buf = bytes(0, 0x30, 0, 0, 0, 0, 0, 0, 0);
			new CompressLzss().optimiseAudc(buf);
			assertEquals((byte) 0x00, buf[1]);
		}

		@Test
		void noiseTypeBitClearedWhenBit5DistortionSet() {
			// AUDC0: dist=0xE0 (bit5 set), vol=5
			byte[] buf = bytes(0, 0xE5, 0, 0, 0, 0, 0, 0, 0);
			new CompressLzss().optimiseAudc(buf);
			assertEquals((byte) 0xA5, buf[1]); // bit 0x40 cleared
		}

		@Test
		void distortionAtOrAboveF0IsLeftUntouched() {
			// dist=0xF0, vol=5 (two-tone filter range)
			byte[] buf = bytes(0, 0xF5, 0, 0, 0, 0, 0, 0, 0);
			new CompressLzss().optimiseAudc(buf);
			assertEquals((byte) 0xF5, buf[1]);
		}
	}

	@Nested
	class OptimiseAudctlTest {

		@Test
		void mutingAllChannelsClearsFilterAndClockBits() {
			// AUDC0/1/3 muted (volume nibble 0), AUDC2 has volume 5, AUDCTL all bits set.
			byte[] buf = bytes(0, 0x00, 0, 0x00, 0, 0x05, 0, 0x00, 0xFF);
			new CompressLzss().optimiseAudctl(buf);
			assertEquals((byte) 0xA9, buf[8]);
		}

		@Test
		void noMutedChannelsLeavesAudctlUntouched() {
			byte[] buf = bytes(0, 0x01, 0, 0x02, 0, 0x03, 0, 0x04, 0xFF);
			new CompressLzss().optimiseAudctl(buf);
			assertEquals((byte) 0xFF, buf[8]);
		}
	}

	@Nested
	class OptimiseAudfTest {

		@Test
		void mutedChannelsWithNoConflictingAudctlBitsAreZeroed() {
			byte[] buf = bytes(0xAB, 0x00, 0xCD, 0x00, 0xEF, 0x00, 0x12, 0x00, 0x00);
			new CompressLzss().optimiseAudf(buf);
			assertEquals((byte) 0, buf[0]);
			assertEquals((byte) 0, buf[2]);
			assertEquals((byte) 0, buf[4]);
			assertEquals((byte) 0, buf[6]);
		}

		@Test
		void mutedChannelsPreserveAudfWhenAudctlNeedsThem() {
			// AUDCTL bit 0x04 keeps AUDF0 (case 0) and AUDF2 (case 2), but not
			// AUDF1 (case 1, needs 0x02/0x10) or AUDF3 (case 3, needs 0x02/0x08).
			byte[] buf = bytes(0xAB, 0x00, 0xCD, 0x00, 0xEF, 0x00, 0x12, 0x00, 0x04);
			new CompressLzss().optimiseAudf(buf);
			assertEquals((byte) 0xAB, buf[0]);
			assertEquals((byte) 0, buf[2]);
			assertEquals((byte) 0xEF, buf[4]);
			assertEquals((byte) 0, buf[6]);
		}

		@Test
		void nonZeroVolumeLeavesAudfUntouched() {
			byte[] buf = bytes(0xAB, 0x01, 0xCD, 0x02, 0xEF, 0x03, 0x12, 0x04, 0x00);
			new CompressLzss().optimiseAudf(buf);
			assertEquals((byte) 0xAB, buf[0]);
			assertEquals((byte) 0xCD, buf[2]);
			assertEquals((byte) 0xEF, buf[4]);
			assertEquals((byte) 0x12, buf[6]);
		}
	}

	// CompressLzss.compress() is the only entry point that exercises the LZSS
	// matching/bit-packing itself. Expected byte arrays below were captured
	// by running the actual implementation once ("golden master"), not
	// hand-derived, since that logic is too intricate to safely hand-verify.

	@Test
	void allSilenceFrames() {
		// 3 identical all-zero 9-byte SAP-R register frames.
		byte[] src = new byte[27];

		byte[] compressed = new CompressLzss().compress(src, SapROptimization.NONE);

		byte[] expected = bytes(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFE, 0x02);
		assertArrayEquals(expected, compressed);
	}

	@Test
	void repeatingAndVaryingFrames() {
		// 4 frames: the 1st, 2nd and 4th are identical, the 3rd differs slightly,
		// giving the LZSS matcher a real (short) match to find.
		byte[] src = bytes(
				10, 0x81, 20, 0x82, 30, 0x83, 40, 0x84, 0x00,
				10, 0x81, 20, 0x82, 30, 0x83, 40, 0x84, 0x00,
				11, 0x81, 21, 0x82, 31, 0x83, 41, 0x84, 0x00,
				10, 0x81, 20, 0x82, 30, 0x83, 40, 0x84, 0x00);

		byte[] compressed = new CompressLzss().compress(src, SapROptimization.NONE);

		byte[] expected = bytes(
				0xAB, 0x00, 0x84, 0x28, 0x83, 0x1E, 0x82, 0x14, 0x81, 0x0A, 0xFF, 0x28,
				0x1E, 0x14, 0x0A, 0x29, 0x1F, 0x15, 0x0B, 0x0F, 0x28, 0x1E, 0x14, 0x0A);
		assertArrayEquals(expected, compressed);
	}

	@Test
	void allOptimizationsAppliedToMutedChannels() {
		// All 4 channels muted (volume nibble 0) and AUDCTL all-bits-set: the
		// AUDC/AUDCTL/AUDF optimisation passes should all fire before
		// compression, so the compressed output differs from the equivalent
		// input run through SapROptimization.NONE.
		byte[] src = bytes(
				0xAB, 0xE0, 0xCD, 0x30, 0xEF, 0x00, 0x12, 0x00, 0xFF,
				0xAB, 0xE0, 0xCD, 0x30, 0xEF, 0x00, 0x12, 0x00, 0xFF);

		byte[] compressed = new CompressLzss().compress(src, SapROptimization.ALL);

		byte[] expected = bytes(0xFF, 0xA1, 0x00, 0x00, 0x00, 0xEF, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00);
		assertArrayEquals(expected, compressed);
	}
}
