package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.Atari;
import org.atari.raster.rmt.model.AtariCpu;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.PokeyStream;
import org.atari.raster.rmt.model.TestSongs;
import org.atari.raster.rmt.model.WaveFileExporter;

/**
 * The WAV export must translate the SAP-R Two-Tone convention - AUDC1 bit 4,
 * set by {@link PokeyStream#record} while the driver's SKCTL shadow reads $8B
 * - the way the SAP/XEX exports' LZSS player does: SKCTL $8B into the POKEY
 * pair and the bit cleared from AUDC1. Passed through raw, a POKEY reads the
 * bit as volume-only and the filter is lost (plans/50 T3, issue #30).
 */
class WavExportTwoToneTest {

	@Test
	void theWavExportTranslatesTheTwoToneConventionLikeTheLzssPlayer() throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(new SongFiles(session, new StubSongFilesHost()).fileOpen(TestSongs.ISSUE_30_TWO_TONE_FILTER, false));
		PokeyStream stream = new PokeyStream();
		session.song.dumpSongToPokeyStream(stream, PlayMode.PLAY_SONG, 0, 0, session.tracks4_8, session.atariTrackerDriver, session.channelControl, session.clipboard, session.undo);
		int frames = stream.getFirstCountPoint();
		byte[] bytes = stream.getFrameBytes(frames, 0);

		int twoToneFrames = 0;
		for (int f = 0; f < frames; f++) {
			int audc1 = bytes[f * 9 + 1] & 0xFF;
			if (audc1 < 0xF0 && (audc1 & 0x10) != 0) {
				twoToneFrames++;
			}
		}
		assertTrue(twoToneFrames > 0, "the example's dump carries the convention");

		int instrumentSpeed = session.song.getInstrumentSpeed();
		byte[] wav = WaveFileExporter.exportWav(stream, false, false, instrumentSpeed);
		byte[] samples = Arrays.copyOfRange(wav, 44, wav.length);

		assertArrayEquals(replay(bytes, frames, instrumentSpeed, true), samples, "the WAV's sound is the translated replay");
		assertFalse(Arrays.equals(replay(bytes, frames, instrumentSpeed, false), samples), "the raw replay (bit 4 as volume-only, no SKCTL) must not be");
	}

	/** {@code WaveFileExporter}'s replay loop with ({@code translate}) and without the Two-Tone translation - the latter is the pre-plan-50 behavior. */
	private static byte[] replay(byte[] stream, int frames, int instrumentSpeed, boolean translate) {
		AtariCpu cpu = new AtariCpu(false, false);
		int frameCycles = Atari.getFrameCycleCount(false);
		int subFrames = Math.max(1, instrumentSpeed);
		ByteArrayOutputStream samples = new ByteArrayOutputStream();
		byte[] rendered = new byte[8192];
		byte[] output = new byte[8192];
		int remainingCycles = 0;
		int partGroup = 0;
		for (int frame = 0; frame < frames; frame++) {
			int at = frame * 9;
			int audc1 = stream[at + 1] & 0xFF;
			boolean twoTone = translate && audc1 < 0xF0 && (audc1 & 0x10) != 0;
			if (translate) {
				cpu.pokeRegister(15, twoTone ? 0x8B : 0x03);
			}
			for (int r = 0; r < 9; r++) {
				int value = stream[at + r] & 0xFF;
				if (r == 1 && twoTone) {
					value &= ~0x10;
				}
				cpu.pokeRegister(r, value);
			}
			if (partGroup == 0) {
				remainingCycles = frameCycles;
				partGroup = subFrames;
			}
			int cycles = remainingCycles / partGroup;
			remainingCycles -= cycles;
			partGroup--;
			int blocks = cpu.render(cycles, rendered, 0);
			int n = AtariCpu.toTwoChannels(rendered, blocks, cpu.getBlockSize(), output, 0);
			samples.write(output, 0, n);
		}
		return samples.toByteArray();
	}
}
