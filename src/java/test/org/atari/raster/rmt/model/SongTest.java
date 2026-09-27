package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/SongTests.cpp's SongCoreTest - only the CSong
 * methods implemented in SongCore.cpp (see plans/13_JAVA_PORT_PLAN.md for the
 * Song.cpp/IO_Song.cpp triage this project inherited from the C++
 * characterization effort).
 */
class SongTest {

	private Song song;

	@BeforeEach
	void setUp() {
		song = new Song(new Instruments(), new Tracks());
		// Song's constructor doesn't fully reset song data - blank song
		// lines/goto slots to "empty" (-1), matching SongCoreTest's own SetUp.
		for (int line = 0; line < Song.SONGLEN; line++) {
			for (int col = 0; col < Song.SONGTRACKS; col++) {
				song.getSong()[line][col] = -1;
			}
			song.getSongGo()[line] = -1;
		}
	}

	@Test
	void newSongHasEmptyNameAndZeroedCursorState() {
		assertEquals("", song.getName());
		assertEquals(0, song.getActiveColumn());
		assertEquals(0, song.getActiveLine());
		assertEquals(0, song.getPlayLine());
		assertFalse(song.isNTSC());
	}

	@Test
	void getTracksAndIsStereoFollowGlobalTrackCount() {
		assertEquals(4, song.getTracks(4));
		assertFalse(song.isStereo(4));

		assertEquals(8, song.getTracks(8));
		assertTrue(song.isStereo(8));
	}

	@Test
	void getInstrumentSpeedReflectsSongInfoPars() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.instrumentSpeed = 3;
		song.setSongInfoPars(info);

		assertEquals(3, song.getInstrumentSpeed());
	}

	@Test
	void activeAndPlayLineSettersRoundTrip() {
		song.setActiveLine(42);
		assertEquals(42, song.getActiveLine());

		song.setPlayLine(17);
		assertEquals(17, song.getPlayLine());
	}

	@Test
	void playPressedTonesInitAndSilenceDoNotCrash() {
		// Both only write to the private playPt* arrays, with no public
		// getter to assert on directly - this just confirms neither throws
		// on a freshly-constructed song (C++'s always-true BOOL return isn't
		// preserved here - see Song.playPressedTonesInit()'s javadoc).
		song.playPressedTonesInit();
		song.setPlayPressedTonesSilence();
	}

	@Nested
	class UecursorIsEqualTest {

		@Test
		void comparesTheRightNumberOfIntsPerPart() {
			int[] a4 = { 1, 2, 3, 4 };
			int[] b4 = { 1, 2, 3, 4 };
			int[] c4 = { 1, 2, 3, 9 };
			assertTrue(song.uecursorIsEqual(a4, b4, Part.PART_TRACKS));
			assertFalse(song.uecursorIsEqual(a4, c4, Part.PART_TRACKS));

			int[] a2 = { 5, 6 };
			int[] b2 = { 5, 6 };
			assertTrue(song.uecursorIsEqual(a2, b2, Part.PART_SONG));

			int[] a6 = { 1, 2, 3, 4, 5, 6 };
			int[] b6 = { 1, 2, 3, 4, 5, 7 };
			assertFalse(song.uecursorIsEqual(a6, b6, Part.PART_INSTRUMENTS));

			int[] a1 = { 9 };
			int[] b1 = { 9 };
			assertTrue(song.uecursorIsEqual(a1, b1, Part.PART_INFO));
		}
	}

	@Test
	void songGetGoReadsCurrentAndGivenLine() {
		song.getSongGo()[0] = 3;
		song.getSongGo()[5] = -1;

		song.songSetActiveLine(0);
		assertEquals(3, song.songGetGo());
		assertEquals(-1, song.songGetGo(5));
	}

	@Test
	void songTrackGoDecAndIncWrapAtByteBoundaries() {
		song.songSetActiveLine(0);
		song.getSongGo()[0] = 0;

		song.songTrackGoDec();
		assertEquals(0xff, song.songGetGo()); // (0 - 1) & 0xff wraps to 255

		song.songTrackGoInc();
		assertEquals(0, song.songGetGo()); // back to 0

		song.songTrackGoInc();
		assertEquals(1, song.songGetGo());
	}

	@Test
	void findNearTrackBySongLineAndColumnPrefersTrackAfterDefault() {
		song.getSongGo()[0] = -1; // not a goto line
		song.getSong()[0][2] = 10; // column 2's default track is 10

		byte[] used = new byte[Tracks.TRACKSNUM];
		used[10] = TrackFlag.TF_USED; // 10 itself is taken
		used[11] = TrackFlag.TF_USED; // so is the next one

		// Should skip 10 and 11 (both used) and return the next free track after 10.
		assertEquals(12, song.findNearTrackBySongLineAndColumn(0, 2, used));
	}

	@Test
	void findNearTrackBySongLineAndColumnFallsBackToFirstFreeTrack() {
		// No song line has a default track for this column (all -1 from
		// setUp, and no goto lines either), so it falls through to "first
		// free track".
		byte[] used = new byte[Tracks.TRACKSNUM];
		used[0] = TrackFlag.TF_USED;
		used[1] = TrackFlag.TF_USED;

		assertEquals(2, song.findNearTrackBySongLineAndColumn(0, 0, used));
	}

	@Test
	void songPlayNextLineAdvancesAndFollowsGotoLine() {
		song.setPlayMode(PlayMode.PLAY_SONG);
		song.songSetPlayLine(4);
		song.getSongGo()[5] = -1; // plain advance to line 5, no goto

		assertTrue(song.songPlayNextLine());
		assertEquals(5, song.songGetPlayLine());
		assertEquals(0, song.getPlayLine()); // the *track* play line always resets

		song.getSongGo()[6] = 2; // line 6 says "goto line 2"
		assertTrue(song.songPlayNextLine());
		assertEquals(2, song.songGetPlayLine()); // advanced to 6, then jumped to 2
	}

	@Test
	void songPlayNextLineDoesNotAdvanceWhenStopped() {
		song.setPlayMode(PlayMode.PLAY_STOP);
		song.songSetPlayLine(7);
		song.getSongGo()[7] = -1;

		assertTrue(song.songPlayNextLine());
		assertEquals(7, song.songGetPlayLine()); // unchanged: PLAY_STOP doesn't advance
	}

	@Test
	void songToAtaEncodesTrackDataThenFillsRestAsUnused() {
		song.getSong()[0][0] = 5;
		song.getSong()[0][1] = 10;
		song.getSong()[0][2] = 15;
		song.getSong()[0][3] = 20;

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = song.songToAta(dest, dest.length, 0x4000, 4);

		assertEquals(4, size);
		assertEquals(5, dest[0]);
		assertEquals(10, dest[1]);
		assertEquals(15, dest[2]);
		assertEquals(20, dest[3]);
		// Every other line is empty (-1), encoded as 255 per track slot.
		assertEquals((byte) 255, dest[4]);
		assertEquals((byte) 255, dest[Song.SONGLEN * 4 - 1]);
	}

	@Test
	void songToAtaAndAtaToSongRoundTripTrackData() {
		song.getSong()[0][0] = 5;
		song.getSong()[0][1] = 10;
		song.getSong()[0][2] = 15;
		song.getSong()[0][3] = 20;

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = song.songToAta(dest, dest.length, 0x4000, 4);

		Song decoded = new Song(new Instruments(), new Tracks());
		assertTrue(decoded.ataToSong(dest, size, 0x4000, 4));
		assertEquals(5, decoded.getSong()[0][0]);
		assertEquals(10, decoded.getSong()[0][1]);
		assertEquals(15, decoded.getSong()[0][2]);
		assertEquals(20, decoded.getSong()[0][3]);
	}

	@Test
	void songToAtaAndAtaToSongRoundTripGotoLine() {
		song.getSongGo()[0] = 1; // line 0: "goto line 1"

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = song.songToAta(dest, dest.length, 0x4000, 4);

		assertEquals(4, size);
		assertEquals((byte) 254, dest[0]); // go command marker

		// Decode using a byte range covering (at least) 2 lines, so the
		// decoded goto target (line 1) is within bounds - ataToSong()'s
		// "len" parameter represents how much of the module's song section
		// is being decoded, not songToAta()'s own (smaller) "bytes actually
		// used" return value.
		Song decoded = new Song(new Instruments(), new Tracks());
		assertTrue(decoded.ataToSong(dest, 8, 0x4000, 4));
		assertEquals(1, decoded.songGetGo(0));
	}
}
