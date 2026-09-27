package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.Atari;
import com.wudsn.tools.rmt.model.AtariTrackerDriver;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.PokeyStream;
import com.wudsn.tools.rmt.model.RmtExporter;
import com.wudsn.tools.rmt.model.SongIOType;
import com.wudsn.tools.rmt.model.TrackerDriverVersion;
import com.wudsn.tools.rmt.model.Tracks;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPArgumentException;
import net.sf.asap.ASAPFormatException;

/**
 * The tracker driver running on the session's emulated Atari - live playback's and the SAP-R dump's path, as in C++ - checked against an independent emulation: the same song exported as a module and
 * played by ASAP's own RMT player.
 */
class LivePlaybackTest {

	private static final Path DELTA = ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt");

	private static final int AUDCTL = 8;

	@Test
	void oneBeatAndOneDriverTickLeaveNotesInThePokeyShadow() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(DELTA, false));
		Atari atari = session.atari;
		AtariTrackerDriver driver = session.atariTrackerDriver;

		assertEquals(1, driver.init(), "RMT_INIT's A");
		driver.setPokey();
		for (int i = 0; i < 8; i++) {
			assertEquals(0, atari.getByteAt(0xD200 + i), "silent after init: $D20" + i);
		}
		assertEquals(3, atari.getByteAt(0xD20F), "SKCTL after init");

		assertTrue(session.song.play(PlayMode.PLAY_SONG, false, 0, session.undo, session.tracks4_8, driver, session.clipboard));
		driver.play();

		boolean anyVolume = false;
		for (int ch = 0; ch < 4; ch++) {
			anyVolume |= (atari.getByteAt(0xD201 + ch * 2) & 0x0F) != 0;
		}
		assertTrue(anyVolume, "the first beat of Delta.rmt sounds on at least one channel");
		session.song.stop(session.undo);
	}

	@Test
	void theDumpWithTheUnpatchedDriverMatchesAsapsPlayerFrameForFrame() throws IOException {
		RmtSession session = open();
		session.atariTrackerDriver.loadRMTRoutines(TrackerDriverVersion.UNPATCHED); // the classic driver, whose frequency tables ASAP's player shares
		byte[] dump = dump(session);
		byte[] asap = asapReference(session, dump.length / 9);

		for (int f = 0; f < dump.length / 9; f++) {
			byte[] e = Arrays.copyOfRange(asap, f * 9, f * 9 + 9);
			byte[] a = Arrays.copyOfRange(dump, f * 9, f * 9 + 9);
			if (!Arrays.equals(e, a)) {
				fail("frame " + f + ": ASAP " + hex(e) + " driver " + hex(a));
			}
		}
	}

	@Test
	void theDefaultPatchedDriverDiffersFromAsapsPlayerOnlyInFrequencyBytes() throws IOException {
		RmtSession session = open();
		assertEquals(TrackerDriverVersion.PATCH16, session.options.trackerDriverVersion);
		byte[] dump = dump(session);
		byte[] asap = asapReference(session, dump.length / 9);

		int audfDifferences = 0;
		for (int f = 0; f < dump.length / 9; f++) {
			for (int r = 0; r < 9; r++) {
				boolean audf = r < AUDCTL && (r & 1) == 0;
				if (audf) {
					if (dump[f * 9 + r] != asap[f * 9 + r]) {
						audfDifferences++;
					}
				} else {
					assertEquals(asap[f * 9 + r], dump[f * 9 + r], "frame " + f + " register " + r + " (AUDC/AUDCTL must agree)");
				}
			}
		}
		assertTrue(audfDifferences > 0, "the patched driver's frequency tables differ from the classic ones");
	}

	private static RmtSession open() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(DELTA, false));
		assertEquals(4, session.tracks4_8);
		return session;
	}

	/** The production SAP-R dump up to the loop point: {@code frames * 9} bytes, one AUDF1..AUDCTL frame per driver tick. */
	private static byte[] dump(RmtSession session) {
		PokeyStream stream = new PokeyStream();
		session.song.dumpSongToPokeyStream(stream, PlayMode.PLAY_SONG, 0, 0, session.tracks4_8, session.atariTrackerDriver, session.channelControl, session.clipboard, session.undo);
		int frames = stream.getFirstCountPoint();
		assertTrue(frames > 100, "frames before the loop point: " + frames);
		return stream.getFrameBytes(frames, 0);
	}

	/** The same song exported as an RMT module and played by ASAP's own RMT player, one 9-byte register frame per player call - the way the dump worked before the audio batch. */
	private static byte[] asapReference(RmtSession session, int frames) {
		byte[] mem = new byte[Atari.MEMORY_SIZE];
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int end = session.song.makeModule(mem, 0x4000, SongIOType.RMT, instrumentSavedFlags, trackSavedFlags, session.tracks4_8);
		byte[] module = RmtExporter.exportAsRMT(session.song, session.instruments, mem, 0x4000, end, instrumentSavedFlags);
		ASAP asap = new ASAP();
		try {
			asap.load("song.rmt", module, module.length);
			asap.playSong(0, -1);
		} catch (ASAPFormatException | ASAPArgumentException e) {
			throw new AssertionError(e);
		}
		byte[] result = new byte[frames * 9];
		for (int f = 0; f < frames; f++) {
			asap.stepFrame();
			for (int r = 0; r < 9; r++) {
				result[f * 9 + r] = (byte) asap.getPokeyRegisterShadow(0, r);
			}
			if (asap.getPokeyRegisterShadow(0, 15) == 0x8B) {
				result[f * 9 + 1] |= 0x10; // CPokeyStream::Record()'s Two-Tone patch
			}
		}
		return result;
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x ", b & 0xFF));
		}
		return sb.toString().trim();
	}
}
