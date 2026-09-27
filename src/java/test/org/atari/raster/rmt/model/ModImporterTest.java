package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/SongEditingTests.cpp's "CSong::ImportMODParseHeader /
 * ImportMODApply" section - Batch B of plans/JAVA_IMPORTER_PLAN.md.
 */
class ModImporterTest {

	private Tracks tracks;
	private Instruments instruments;
	private Song song;
	private Undo undo;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();

		instruments = new Instruments();
		instruments.initInstruments();
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			java.util.Arrays.fill(instruments.getInstrument(i).name, '\0');
		}

		song = new Song(instruments, tracks);
		undo = new Undo(tracks, instruments, song, new TrackClipboard());
	}

	@Test
	void parseHeaderFailsOnATruncatedHeader() {
		byte[] data = new byte[0]; // shorter than the required 1084-byte header

		ModImporter.ParseHeaderResult header = ModImporter.parseHeader(data, song, tracks, undo);

		assertFalse(header.ok());
		assertEquals(1, header.errorCode());
	}

	@Test
	void parseHeaderFailsOnUnrecognizedIdentification() {
		// "2CHN" parses as a 2-channel module (chnls = '2' - '0') - out of the
		// supported 4-8 range. An all-zero identification doesn't trigger this
		// guard: bytes outside the printable " ".."Z" range are treated as an
		// older, un-identified 15-sample module instead (chnls hardcoded to 4,
		// always valid) - see ModImporter.parseHeader()'s fallback branch.
		byte[] data = new byte[1084];
		data[1080] = '2';
		data[1081] = 'C';
		data[1082] = 'H';
		data[1083] = 'N';

		ModImporter.ParseHeaderResult header = ModImporter.parseHeader(data, song, tracks, undo);

		assertFalse(header.ok());
		assertEquals(2, header.errorCode());
	}

	@Test
	void applyConvertsANoteIntoTheDestinationTrack() {
		// Hand-derived minimal standard ProTracker ("M.K.", 31-sample,
		// 4-channel) module buffer. Total size (2116 bytes) is exact -
		// ImportMODApply()'s sample offsets are computed from this exact
		// layout, so it must add up precisely:
		// [0..19] song name (blank)
		// [20..49] sample #1's 30-byte header: [42..43] length word (BE, in
		// 16-bit words) = 4 -> 8 bytes of real sample data; [45] volume =
		// 0x40; repeat point/length left at 0 (no loop)
		// [50..949] samples #2-31's headers, all zero (length 0 -> skipped)
		// [950] songlen = 1 (one song order position)
		// [951] restart position = 0
		// [952] song order[0] = pattern 0
		// [1080..1083] "M.K." identification (standard 4-channel module)
		// [1084..2107] pattern 0's 1024 bytes (4 channels * 256), all empty
		// cells except row 0/channel 0: period 0x06B0 (the lowest note,
		// "C3"), sample #1, no effect
		// [2108..2115] sample #1's 8 bytes of real (non-silent) data
		byte[] buf = new byte[2116];
		buf[42] = 0x00;
		buf[43] = 0x04; // sample #1 length = 4 words = 8 bytes
		buf[45] = 0x40; // sample #1 volume
		buf[950] = 1; // songlen
		buf[951] = 0; // restartpos
		buf[952] = 0; // song order[0] -> pattern 0
		buf[1080] = 'M';
		buf[1081] = '.';
		buf[1082] = 'K';
		buf[1083] = '.';
		buf[1084] = 0x06;
		buf[1085] = (byte) 0xB0;
		buf[1086] = 0x10;
		buf[1087] = 0x00; // row0/ch0: period 0x6B0, sample 1
		buf[2108] = 0;
		buf[2109] = 50;
		buf[2110] = 0;
		buf[2111] = 50;
		buf[2112] = 0;
		buf[2113] = 50;
		buf[2114] = 0;
		buf[2115] = 50;

		ModImporter.ParseHeaderResult header = ModImporter.parseHeader(buf, song, tracks, undo);
		assertTrue(header.ok());
		assertEquals(4, header.chnls());
		assertEquals(31, header.modSamples());
		assertEquals(1, header.songLen());

		int[] trackOrder = { 0, 1, 2, 3, 4, 5, 6, 7 };
		ModImporter.ApplyResult result = ModImporter.apply(header, /* rmttype= */ 4, trackOrder, /* shiftdownoctave= */ false, /* portamento= */ false, /* fullvolumerange= */ false,
				/* volumeincrease= */ false, /* decreaseinstrument= */ false, /* optimizeloops= */ false, /* truncateunusedparts= */ false, song, tracks, instruments, undo);

		assertEquals(1, result.destNum()); // only channel 0 produced a non-empty track
		assertEquals(1, result.nonEmptySamples()); // only sample #1 has real length
		assertEquals(4, result.tracks4_8()); // rmttype=4

		assertEquals(0, song.getSong()[0][0]); // track 0 placed at songline 0, column 0
		assertEquals(-1, song.getSong()[0][1]);

		Track track0 = tracks.getTrack(0);
		assertEquals(0, track0.note[0]);
		assertEquals(1, track0.instr[0]);
		assertEquals(15, track0.volume[0]); // AtariVolume(0x40) = 15 (max)

		Instrument instr1 = instruments.getInstrument(1);
		assertEquals(1, instr1.parameters[Instrument.PAR_ENV_LENGTH]);
		assertEquals(1, instr1.parameters[Instrument.PAR_ENV_GOTO]);
	}
}
