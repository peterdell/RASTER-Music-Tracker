package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/SongEditingTests.cpp's "CSong::ImportTMCParseHeader /
 * ImportTMCApply" section - Batch A of plans/14_JAVA_IMPORTER_PLAN.md.
 */
class TmcImporterTest {

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
	void parseHeaderFailsOnATruncatedFile() {
		byte[] data = new byte[0]; // not even a valid 4-byte header

		TmcImporter.ParseHeaderResult header = TmcImporter.parseHeader(data, song, tracks, undo);

		assertFalse(header.ok());
	}

	@Test
	void parseHeaderSetsTheSongName() {
		byte[] mem = { 'H' }; // song name field stops at the first 0 byte (from the zeroed scratch buffer)
		byte[] data = AtariIO.saveBinaryBlock(mem, 0, 0, true);

		TmcImporter.ParseHeaderResult header = TmcImporter.parseHeader(data, song, tracks, undo);

		assertTrue(header.ok());
		assertEquals(0, header.bfrom());
		assertEquals("H", song.getName());
	}

	@Test
	void applyConvertsANoteIntoTheDestinationTrack() {
		// Hand-derived minimal TMC buffer (all offsets relative to bfrom=0) -
		// mirrors the C++ test's own byte-layout derivation exactly:
		// [0] = 0xFF - doubles as the song name's first (blanked) char and
		// the "mem[adr]==0xFF" empty-track sentinel that all 127 unused
		// track pointers (defaulting to address 0) hit.
		// [30] = 5 -> mainspeed = 6
		// [31] = 1 -> instrspeed = 1
		// [32..159] = 0 -> all 64 instrument pointers undefined
		// [160] = 0xB0, [288] = 0x01 -> track_ptr[0] = 0x01B0 = 432
		// [161..287], [289..415] = 0 -> track_ptr[1..127] = 0 (sentinel)
		// [416..431] = songline 0: column 0 -> track 0, shift 0; columns
		// 1-7 skipped (track byte 0xFF, out of the valid 0-127 range)
		// [432..434] = track 0's data: note 0 (byte 0x01), volume 15/15
		// (byte 0x00), end-of-track marker (byte 0xFF, "space=64")
		byte[] mem = new byte[435];
		mem[0] = (byte) 0xFF;
		mem[30] = 5;
		mem[31] = 1;
		mem[160] = (byte) 0xB0;
		mem[288] = 0x01;
		mem[431] = 0x00;
		mem[430] = 0x00; // column 0: track 0, shift 0 (also: not a goto line)
		mem[429] = (byte) 0xFF;
		mem[428] = 0x00; // column 1: skipped
		mem[427] = (byte) 0xFF;
		mem[426] = 0x00; // column 2: skipped
		mem[425] = (byte) 0xFF;
		mem[424] = 0x00; // column 3: skipped
		mem[423] = (byte) 0xFF;
		mem[422] = 0x00; // column 4: skipped
		mem[421] = (byte) 0xFF;
		mem[420] = 0x00; // column 5: skipped
		mem[419] = (byte) 0xFF;
		mem[418] = 0x00; // column 6: skipped
		mem[417] = (byte) 0xFF;
		mem[416] = 0x00; // column 7: skipped
		mem[432] = 0x01; // note = (0x01 & 0x3f) - 1 = 0
		mem[433] = 0x00; // volume: volL = volR = 15
		mem[434] = (byte) 0xFF; // end of track (space = 64)

		byte[] data = AtariIO.saveBinaryBlock(mem, 0, 434, true);

		TmcImporter.ParseHeaderResult header = TmcImporter.parseHeader(data, song, tracks, undo);
		assertTrue(header.ok());

		TmcImporter.ApplyResult result = TmcImporter.apply(header, false, false, false, song, tracks, instruments, undo);

		assertEquals(1, result.songLines());
		assertEquals(0, result.nonEmptyInstruments()); // no instrument pointers defined
		// numOfTracks tracks the highest track *index* used (a pre-existing
		// naming quirk carried over from C++, not something this port
		// changes) - index 0 is the only one used here, so it never exceeds
		// its own initial value of 0.
		assertEquals(0, result.numOfTracks());

		assertEquals(4, result.tracks4_8()); // no stereo columns (4-7) used -> mono module
		assertEquals(0, song.getSong()[0][0]); // track 0 placed at songline 0, column 0

		Track track0 = tracks.getTrack(0);
		assertEquals(0, track0.note[0]);
		assertEquals(0, track0.instr[0]);
		// Volume gets normalized against the instrument's own max envelope
		// volume (ConvertTracks.makeOrFindTrackShiftLR()) - since instrument
		// 0 is undefined here (no instrument pointers were set up), its
		// tracked max volume defaults to 0, which floors this note's volume
		// to 0 too. This is real, faithful TMC-import behavior, not a test bug.
		assertEquals(0, track0.volume[0]);
	}
}
