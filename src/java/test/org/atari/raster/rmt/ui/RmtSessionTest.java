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
		Path path = ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt");

		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(path, false));

		assertEquals(4, session.tracks4_8);
		assertEquals(path.toString(), session.song.getFilename());
		assertEquals(SongIOType.RMT, session.song.getIOType());
		assertEquals(session.song.getMainSpeed(), session.song.getSpeed());
	}

	@Test
	void openingAStereoRmtKeepsEightTracks() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(ReferenceScreenshot.ROOT.resolve("song2-stereo").resolve("Why_Do_You_Dance_With_Me-132-$4000.rmt"), false));
		assertEquals(8, session.tracks4_8);
	}

	@Test
	void aFileThatIsNotAnRmtModuleLeavesAClearedSong(@TempDir Path dir) throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt"), false));
		Path junk = dir.resolve("junk.rmt");
		Files.write(junk, new byte[] { 1, 2, 3, 4 });

		assertFalse(new SongFiles(session, new StubSongFilesHost()).fileOpen(junk, false));

		assertEquals("Noname song", session.song.getName());
		assertEquals("", session.song.getFilename());
	}
}
