package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.AtariCpu;
import org.atari.raster.rmt.model.PlayMode;

/** The frame step of {@link AudioEngine} driven by hand (no audio line): what C++'s {@code TimerRoutine()} does per tick. */
class AudioEngineTest {

	private static final Path DELTA = ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt");

	private static RmtSession openDelta() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(DELTA, false));
		return session;
	}

	@Test
	void aFrameAdvancesPlaybackCountsTimeAndRendersOneFrameOfStereoSamples() throws IOException {
		RmtSession session = openDelta();
		AudioEngine engine = new AudioEngine(session);
		session.song.play(PlayMode.PLAY_SONG, false, 0, session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
		assertEquals(0, session.uiState.playTime, "Play() resets g_playtime");

		int speed = session.song.getSpeed();
		int bytes = engine.renderFrame();
		// one PAL frame: 44100 / 50 = 882 blocks of 2 channels x 16 bit
		assertTrue(bytes >= 880 * 4 && bytes <= 884 * 4, "bytes per frame: " + bytes);
		assertEquals(1, session.uiState.playTime);
		boolean nonZero = false;
		for (int i = 0; i < bytes; i++) {
			nonZero |= engine.getFrameBuffer()[i] != 0;
		}
		assertTrue(nonZero, "audible output");
		// a mono song: both output channels carry the same sample
		byte[] b = engine.getFrameBuffer();
		for (int i = 0; i < bytes; i += 4) {
			assertEquals(b[i], b[i + 2]);
			assertEquals(b[i + 1], b[i + 3]);
		}

		// the play position advances one track line per 'speed' frames (Play() itself starts one beat and adds a frame: "m_speeda++")
		int line = session.song.getPlayLine();
		for (int i = 1; i <= speed; i++) {
			engine.renderFrame();
		}
		assertEquals(line + 1, session.song.getPlayLine());
		assertEquals(speed + 1, session.uiState.playTime);

		session.song.stop(session.undo);
		engine.renderFrame();
		assertEquals(speed + 1, session.uiState.playTime, "the counter stops with the song");
	}

	@Test
	void aMutedChannelReachesThePokeyAsSilence() throws IOException {
		RmtSession session = openDelta();
		AudioEngine engine = new AudioEngine(session);
		AtariCpu cpu = session.atari.getCpu();
		session.song.play(PlayMode.PLAY_SONG, false, 0, session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
		engine.renderFrame();
		int sounding = -1;
		for (int ch = 0; ch < 4; ch++) {
			if (cpu.getChannelVolume(ch) != 0) {
				sounding = ch;
			}
		}
		assertTrue(sounding >= 0, "Delta.rmt's first beat sounds");

		session.channelControl.toggleChannelOnOff(sounding);
		engine.renderFrame();
		assertEquals(0, cpu.getChannelVolume(sounding), "muted channel " + sounding);
		assertNotEquals(0, session.atari.getByteAt(0xD201 + sounding * 2) & 15, "the driver itself keeps playing it (the shadow is untouched)");

		session.channelControl.toggleChannelOnOff(sounding);
		engine.renderFrame();
		assertNotEquals(0, cpu.getChannelVolume(sounding));
	}

	@Test
	void aPressedToneSoundsWithPlaybackStopped() throws IOException {
		RmtSession session = openDelta();
		AudioEngine engine = new AudioEngine(session);
		assertEquals(PlayMode.PLAY_STOP, session.song.getPlayMode());
		session.song.setPlayPressedTonesTNIV(2, 24, 0, 15); // the keyboard preview: channel 2, C-3, instrument 0, full volume
		engine.renderFrame();
		assertNotEquals(0, session.atari.getCpu().getChannelVolume(2));
		assertEquals(0, session.uiState.playTime, "not playing");
	}

	@Test
	void anNtscOrStereoSwitchReInitializesThePokeyPairAtTheNextFrame() throws IOException {
		RmtSession session = openDelta();
		AudioEngine engine = new AudioEngine(session);
		AtariCpu cpu = session.atari.getCpu();
		assertFalse(cpu.isNTSC());
		assertFalse(cpu.isStereo(), "Delta.rmt is mono");

		session.setNTSC(true); // SetNTSC() -> ReInitSound() at once
		assertTrue(cpu.isNTSC());
		int bytes = engine.renderFrame();
		assertTrue(bytes >= 733 * 4 && bytes <= 737 * 4, "one NTSC frame is ~735 blocks, was " + bytes / 4);

		session.tracks4_8 = 8; // a writer that doesn't call reInitSound(): the engine catches up at the top of the frame
		bytes = engine.renderFrame();
		assertTrue(cpu.isStereo());
		assertTrue(bytes >= 733 * 4 && bytes <= 737 * 4, "still one frame of 2-channel output, was " + bytes / 4);
	}

	@Test
	void startAndStopWithoutAnAudioDeviceKeepPacingTheModel() throws IOException, InterruptedException {
		RmtSession session = openDelta();
		AudioEngine engine = new AudioEngine(session);
		session.song.play(PlayMode.PLAY_SONG, false, 0, session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
		engine.start();
		long deadline = System.currentTimeMillis() + 3000;
		while (session.locked(() -> session.uiState.playTime) < 3 && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		engine.stop();
		assertTrue(session.uiState.playTime >= 3, "frames were played: " + session.uiState.playTime);
	}
}
