package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.model.SongIOType;

class RmtSessionTest {

	/**
	 * The driver binary ships its own copy of the frequency tables in its
	 * RMT_FRQTABLES segment, and loading it writes that copy into the Atari's
	 * memory; the session regenerates the tables afterwards so the tuning
	 * settings win (plan 37, T2). Both copies are deliberately equal at the
	 * default settings (T7), so a changed setting is the only tell: doubling
	 * the base tuning halves every frequency, taking the bell-1.79 table's
	 * first byte from the shipped $D6 to $69. Mirrors the C++ test in
	 * AtariTrackerDriverTests.cpp, where the regeneration sits inside
	 * LoadRMTRoutines itself.
	 */
	@Test
	void driverLoadRegeneratesTheTuningTablesOverTheDriversStaticCopy() {
		RmtSession session = new RmtSession();
		assertEquals(0xD6, session.atari.getByteAt(org.atari.raster.rmt.model.Atari.RMT_FRQTABLES + 0x040));

		session.tuningSettings.basetuning *= 2;
		// The driver switch is the Options dialog's path and the rmtscript
		// "set driver" command; it loads the binary again, so the
		// regeneration has to run again after it.
		session.setTrackerDriverVersion(session.song.getTrackerDriverVersion());
		assertEquals(0x69, session.atari.getByteAt(org.atari.raster.rmt.model.Atari.RMT_FRQTABLES + 0x040));
	}

	@Test
	void startsLikeRmtExeWithAnEmptyStereoSongAndAllChannelsOn() {
		RmtSession session = new RmtSession();
		assertEquals(8, session.tracks4_8);
		assertEquals("Noname song", session.song.getName());
		assertEquals(64, session.tracks.getMaxTrackLength());
		for (int i = 0; i < 8; i++) {
			assertTrue(session.channelControl.isChannelOn(i));
		}
	}

	@Test
	void openingAMonoRmtSwitchesToFourTracks() throws IOException {
		RmtSession session = new RmtSession();
		Path path = TestSongs.DELTA;

		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(path, false));

		assertEquals(4, session.tracks4_8);
		assertEquals(path.toString(), session.song.getFilename());
		assertEquals(SongIOType.RMT, session.song.getIOType());
		assertEquals(session.song.getMainSpeed(), session.song.getSpeed());
	}

	@Test
	void openingAStereoRmtKeepsEightTracks() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(TestSongs.WHY_DO_YOU_DANCE_WITH_ME, false));
		assertEquals(8, session.tracks4_8);
	}

	@Test
	void aFileThatIsNotAnRmtModuleLeavesAClearedSong(@TempDir Path dir) throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(TestSongs.DELTA, false));
		Path junk = dir.resolve("junk.rmt");
		Files.write(junk, new byte[] { 1, 2, 3, 4 });

		assertFalse(new SongFiles(session, new StubSongFilesHost()).fileOpen(junk, false));

		assertEquals("Noname song", session.song.getName());
		assertEquals("", session.song.getFilename());
	}
}
