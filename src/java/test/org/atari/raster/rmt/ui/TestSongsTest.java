package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The test songs exist, and the Captain Future Theme's committed copies stay
 * byte-identical: the test fixture, the example song in {@code rmt/songs} and
 * the Simple RMT Player's bundle {@code asm/Patch-16/music.rmt} are the same
 * module, so a change to one of them must be a deliberate change to all.
 */
class TestSongsTest {

	@Test
	void everyTestSongExists() {
		assertTrue(Files.isRegularFile(TestSongs.DELTA));
		assertTrue(Files.isRegularFile(TestSongs.WHY_DO_YOU_DANCE_WITH_ME));
		assertTrue(Files.isRegularFile(TestSongs.CAPTAIN_FUTURE_THEME));
	}

	@Test
	void captainFutureThemeCopiesAreByteIdentical() throws IOException {
		byte[] master = Files.readAllBytes(TestSongs.CAPTAIN_FUTURE_THEME);
		assertArrayEquals(master, Files.readAllBytes(Path.of("rmt", "songs", "rmt134", "buddy", "Captain_Future_Theme.rmt")));
		assertArrayEquals(master, Files.readAllBytes(Path.of("asm", "Patch-16", "music.rmt")));
	}
}
