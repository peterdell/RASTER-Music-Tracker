package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every export must leave the channels on. The register dump switches all
 * of them off while it records, and whether they come back depended on the
 * export format's own cleanup - the RITMO fork found exports after which
 * its tracker stayed muted until a restart (its dcd62bd;
 * plans/32_RITMO_FORK_ANALYSIS_PLAN.md's open check). This asserts the
 * state for every format, sound-rendering and data alike.
 */
class ChannelsAfterExportTest {

	@TempDir
	Path dir;

	private void exportAndCheck(int filterIndex, String name) throws IOException {
		RmtSession session = new RmtSession();
		StubSongFilesHost host = new StubSongFilesHost();
		SongFiles files = new SongFiles(session, host);
		assertTrue(files.fileOpen(SongFilesTest.DELTA, false));

		host.nextSap = new SongFiles.SapChoice("Me", "Delta", "01/01/2026", "00");
		host.answerDialogDefaults = true; // the XEX dialog answers its defaults
		Path out = dir.resolve(name);
		host.answer(out, filterIndex);
		files.fileExportAs();
		assertTrue(Files.exists(out), name + ": nothing was exported");

		for (int channel = 0; channel < 8; channel++) {
			assertTrue(session.channelControl.isChannelOn(channel), name + " left channel " + channel + " off");
		}
	}

	@Test
	void saprLeavesTheChannelsOn() throws IOException {
		exportAndCheck(3, "d.sapr");
	}

	@Test
	void lzssLeavesTheChannelsOn() throws IOException {
		exportAndCheck(4, "d.lzss");
	}

	@Test
	void sapLeavesTheChannelsOn() throws IOException {
		exportAndCheck(5, "d.sap");
	}

	@Test
	void xexLeavesTheChannelsOn() throws IOException {
		exportAndCheck(6, "d.xex");
	}

	@Test
	void wavLeavesTheChannelsOn() throws IOException {
		exportAndCheck(8, "d.wav");
	}
}
