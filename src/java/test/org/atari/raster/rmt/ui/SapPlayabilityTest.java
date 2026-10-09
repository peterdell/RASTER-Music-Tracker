package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPSampleFormat;

/**
 * The exported SAP type B must PLAY, not merely match the other program's
 * bytes. Both programs shared the same defect for years: the export wrote
 * the memory blocks of the old VU-Player while shipping vu_player_v2.obx,
 * so ASAP stopped every exported SAP with "INIT routine didn't return" -
 * and the byte-identical cross-program comparison could never see it,
 * because agreement is not correctness
 * (plans/32_RITMO_FORK_ANALYSIS_PLAN.md). This test is the external check:
 * the vendored ASAP, an independent SAP player, loads the export, runs its
 * INIT and renders a second of audio that must not be silence.
 */
class SapPlayabilityTest {

	@TempDir
	Path dir;

	private byte[] export(String subsongs) throws IOException {
		return export(SongFilesTest.DELTA, subsongs, -1);
	}

	/**
	 * Exports {@code song} as SAP type B. A {@code loopAtSongline} >= 0
	 * plants a "goto songline 0" there first, so the register dump ends
	 * after that many songlines - the way to run a big song through the
	 * export without outgrowing the player's memory window.
	 */
	private byte[] export(Path song, String subsongs, int loopAtSongline) throws IOException {
		RmtSession session = new RmtSession();
		StubSongFilesHost host = new StubSongFilesHost();
		SongFiles files = new SongFiles(session, host);
		assertTrue(files.fileOpen(song, false));
		if (loopAtSongline >= 0) {
			session.song.getSongGo()[loopAtSongline] = 0;
		}

		host.nextSap = new SongFiles.SapChoice("Me", "Test", "01/01/2026", subsongs);
		Path out = dir.resolve("export.sap");
		Files.deleteIfExists(out);
		host.answer(out, 5); // the Export dialog's SAP filter
		files.fileExportAs();
		assertTrue(Files.exists(out), "the SAP export wrote nothing");
		return Files.readAllBytes(out);
	}

	/** One second of a subsong, rendered by ASAP; its mean absolute sample level. */
	private static long play(byte[] sap, int song) throws Exception {
		ASAP asap = new ASAP();
		asap.load("delta.sap", sap, sap.length);
		asap.playSong(song, 10_000);
		byte[] buf = new byte[44100 * 2 * 2];
		int n = asap.generate(buf, buf.length, ASAPSampleFormat.S16_L_E);
		assertTrue(n > 0, "ASAP rendered nothing");
		long energy = 0;
		for (int i = 0; i < n; i += 2) {
			energy += Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8)));
		}
		return energy / (n / 2);
	}

	@Test
	void theExportedSapPlaysInAsap() throws Exception {
		byte[] sap = export("00");
		long level = play(sap, 0);
		assertTrue(level > 100, "the SAP plays as silence (mean |sample| " + level + ")");
	}

	/** Two subsongs from different songlines: SONGS 2 in the header, and both INIT and play. */
	@Test
	void aSapWithTwoSubsongsPlaysBoth() throws Exception {
		byte[] sap = export("00 03");
		String header = new String(sap, 0, 200, java.nio.charset.StandardCharsets.US_ASCII);
		assertTrue(header.contains("SONGS 2"), header);

		assertTrue(play(sap, 0) > 100, "subsong 0 is silent");
		assertTrue(play(sap, 1) > 100, "subsong 1 is silent");
	}

	/**
	 * A stereo export: STEREO in the header, two channels in ASAP, and
	 * audible energy on BOTH of them - the AUDCTL copy-paste bug this
	 * coverage exists for fed the left POKEY's AUDCTL to the right one
	 * (plans/32_RITMO_FORK_ANALYSIS_PLAN.md). The song is the stereo
	 * cross-program reference song, cut to its first five songlines by a
	 * planted goto: the full song's LZSS streams outgrow the player's
	 * memory window, which is why a cut is needed at all.
	 */
	@Test
	void aStereoSapPlaysOnBothChannels() throws Exception {
		Path song = ReferenceScreenshot.ROOT.resolve("song2-stereo").resolve("Why_Do_You_Dance_With_Me-132-$4000.rmt");
		byte[] sap = export(song, "00", 5);
		String header = new String(sap, 0, 200, java.nio.charset.StandardCharsets.US_ASCII);
		assertTrue(header.contains("STEREO"), header);

		ASAP asap = new ASAP();
		asap.load("export.sap", sap, sap.length);
		assertEquals(2, asap.getInfo().getChannels());
		asap.playSong(0, 10_000);
		byte[] buf = new byte[44100 * 2 * 4]; // two stereo seconds: 16-bit frames of left,right
		int n = asap.generate(buf, buf.length, ASAPSampleFormat.S16_L_E);
		assertTrue(n > 0, "ASAP rendered nothing");
		long left = 0, right = 0;
		for (int i = 0; i + 3 < n; i += 4) {
			left += Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8)));
			right += Math.abs((short) ((buf[i + 2] & 0xff) | (buf[i + 3] << 8)));
		}
		int frames = n / 4;
		assertTrue(left / frames > 100, "the left channel is silent (mean |sample| " + left / frames + ")");
		assertTrue(right / frames > 100, "the right channel is silent (mean |sample| " + right / frames + ")");
	}

	/** The parser hands the type B export the songline each subsong starts from. */
	@Test
	void parseSubsongsCollectsThePositions() {
		java.util.List<Integer> positions = new java.util.ArrayList<>();
		assertEquals(3, SongFiles.parseSubsongs("00 0a,1F", positions));
		assertEquals(java.util.List.of(0, 10, 31), positions);
	}
}
