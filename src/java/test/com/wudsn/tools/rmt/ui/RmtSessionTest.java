package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wudsn.tools.rmt.model.SongIOType;

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

		assertTrue(session.openRmtFile(path));

		assertEquals(4, session.tracks4_8);
		assertEquals(path.toString(), session.song.getFilename());
		assertEquals(SongIOType.RMT, session.song.getIOType());
		assertEquals(session.song.getMainSpeed(), session.song.getSpeed());
	}

	@Test
	void openingAStereoRmtKeepsEightTracks() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(session.openRmtFile(ReferenceScreenshot.ROOT.resolve("song2-stereo").resolve("Why_Do_You_Dance_With_Me-132-$4000.rmt")));
		assertEquals(8, session.tracks4_8);
	}

	@Test
	void aFileThatIsNotAnRmtModuleLeavesAClearedSong(@TempDir Path dir) throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(session.openRmtFile(ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt")));
		Path junk = dir.resolve("junk.rmt");
		Files.write(junk, new byte[] { 1, 2, 3, 4 });

		assertFalse(session.openRmtFile(junk));

		assertEquals("Noname song", session.song.getName());
		assertEquals("", session.song.getFilename());
	}
}
