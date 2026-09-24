package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/ASMFileBuilderTests.cpp. */
class AsmFileBuilderTest {

	@Test
	void buildInstrumentDataNoLabelEmitsPlainByteList() {
		byte[] buf = { 0x01, 0x02, 0x03, 0x04 };
		int[] info = { 0, 0, 0, 0 };

		AsmFileBuilder.Result result = AsmFileBuilder.buildInstrumentData("", buf, 0, 4, info, AssemblerFormat.ATASM);

		assertEquals(4, result.size());
		assertEquals("\n\n; Instrument data\n\n    {{byte}} $01,$02,$03,$04", result.code());
	}

	@Test
	void buildInstrumentDataNonEmptyInfoEntryInsertsLabelAndRestartsRow() {
		byte[] buf = { (byte) 0xAA, (byte) 0xBB, (byte) 0xCC };
		int[] info = { 0, 5, 0 }; // info[1]=5 -> label "?Instrument_4" (5-1), row restarts

		AsmFileBuilder.Result result = AsmFileBuilder.buildInstrumentData("", buf, 0, 3, info, AssemblerFormat.ATASM);

		assertEquals(3, result.size());
		assertEquals("\n\n; Instrument data\n\n    {{byte}} $aa\n?Instrument_4\n    {{byte}} $bb,$cc", result.code());
		assertEquals(0, info[1]); // consumed
	}

	@Test
	void buildInstrumentDataNonEmptyLabelEmitsOrgLineInEachAssemblerFormat() {
		byte[] buf = { 0x00 };
		int[] info = { 0 };

		AsmFileBuilder.Result atasmResult = AsmFileBuilder.buildInstrumentData("INSTR_START", buf, 0, 1, info, AssemblerFormat.ATASM);
		assertEquals("\n\n; Instrument data\n* = INSTR_START\n\n    {{byte}} $00", atasmResult.code());

		info[0] = 0;
		AsmFileBuilder.Result xasmResult = AsmFileBuilder.buildInstrumentData("INSTR_START", buf, 0, 1, info, AssemblerFormat.XASM);
		assertEquals("\n\n; Instrument data\norg INSTR_START\n\n    {{byte}} $00", xasmResult.code());
	}

	// buildTracksData()'s final validity check scans trackPos[0..65535]
	// unconditionally (not just the [from,to) range actually processed), so
	// the array passed in must always have at least 65536 entries - a real
	// fragility in the method's contract, not something a caller would guess
	// from its signature. Characterized here, not changed.
	private static final int TRACK_POS_SIZE = 65536;

	@Test
	void buildTracksDataNoLabelEmitsPlainByteList() {
		byte[] buf = { 0x11, 0x22, 0x33, 0x44 };
		int[] trackPos = new int[TRACK_POS_SIZE];

		AsmFileBuilder.Result result = AsmFileBuilder.buildTracksData("", buf, 0, 4, trackPos, AssemblerFormat.ATASM);

		assertEquals(4, result.size());
		assertEquals("\n\n; Track data\n    {{byte}} $11,$22,$33,$44", result.code());
	}

	@Test
	void buildTracksDataNonZeroTrackPosOutsideProcessedRangeFailsValidation() {
		byte[] buf = { 0x01, 0x02 };
		int[] trackPos = new int[TRACK_POS_SIZE];
		trackPos[100] = 1; // left set outside the [0,2) range actually consumed

		AsmFileBuilder.Result result = AsmFileBuilder.buildTracksData("", buf, 0, 2, trackPos, AssemblerFormat.ATASM);

		assertEquals(0, result.size()); // signals failure: not every entry was consumed back to 0
	}

	// buildSongData() encodes song lines (numTracks bytes each) plus an
	// optional 4-byte "goto" sequence: 0xFE, an unused filler byte (the byte
	// count always matches numTracks, mirroring CSong::SongToAta()/
	// AtaToSong()'s own 4-byte goto encoding for an 8-track song halved for 4
	// tracks here), then a little-endian absolute target address (relative
	// to "start") that gets converted back to a "?line_NN" label reference.

	@Test
	void buildSongDataPlainLinesWithNoGotoEmitsByteRows() {
		byte[] buf = { 1, 2, 3, 4, 5, 6, 7, 8 }; // 2 lines of 4 tracks

		AsmFileBuilder.Result result = AsmFileBuilder.buildSongData("", buf, 0, 8, 0, 4, AssemblerFormat.ATASM);

		assertEquals(8, result.size());
		assertEquals(
				"\n\n; Song data\n?SongData"
						+ "\n?Line_00  {{byte}} $01,$02,$03,$04"
						+ "\n?Line_01  {{byte}} $05,$06,$07,$08"
						+ "\n",
				result.code());
	}

	@Test
	void buildSongDataGotoToLineZeroEmitsLineLabelReference() {
		// Line 0 (4 bytes), then a goto sequence (0xFE, unused filler, low
		// byte, high byte of target address 0) pointing back at line 0.
		byte[] buf = { 1, 2, 3, 4, (byte) 0xFE, 0x00, 0x00, 0x00 };

		AsmFileBuilder.Result result = AsmFileBuilder.buildSongData("", buf, 0, 8, 0, 4, AssemblerFormat.ATASM);

		// The goto's filler + address bytes don't all count towards size
		// (only the 0xFE marker and the filler byte do; the 2 address bytes
		// are consumed purely for the jump calculation).
		assertEquals(6, result.size());
		assertEquals(
				"\n\n; Song data\n?SongData"
						+ "\n?Line_00  {{byte}} $01,$02,$03,$04"
						+ "\n?Line_01  {{byte}} $fe,$00,<?line_00,>?line_00"
						+ "\n",
				result.code());
	}

	@Test
	void buildSongDataMisalignedGotoTargetEmitsErrorComment() {
		// Same shape as above, but the target address (3) isn't a multiple
		// of numTracks (4) relative to offsetSong, so it can't map to a line
		// number. The error message's format string uses a "% 04x"/"% x"
		// space flag in C++, silently dropped there for a hex conversion -
		// see AsmFileBuilder's own comment for why Java reproduces the exact
		// same visible output by hand instead of with a literal space flag.
		byte[] buf = { 1, 2, 3, 4, (byte) 0xFE, 0x00, 0x03, 0x00 };

		AsmFileBuilder.Result result = AsmFileBuilder.buildSongData("", buf, 0, 8, 0, 4, AssemblerFormat.ATASM);

		assertEquals(6, result.size());
		assertEquals(
				"\n\n; Song data\n?SongData"
						+ "\n?Line_00  {{byte}} $01,$02,$03,$04"
						+ "\n?Line_01  {{byte}} $fe,$00; ERROR malformed file(song jump bad $ 0003[0:8])\n,<($3+?SongData),>($3+?SongData)"
						+ "\n",
				result.code());
	}
}
