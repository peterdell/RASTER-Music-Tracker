package org.atari.raster.rmt.model;

import java.io.ByteArrayOutputStream;

/**
 * Ported from CWaveFileExporter (src/cpp/WaveFileExporter.h/.cpp) -
 * {@code ExportWAV}: replays the recorded {@link PokeyStream}, frame by
 * frame up to its loop point, through the POKEY pair and returns the
 * samples as a WAV file. C++ writes each frame's register bytes into the
 * tracker driver's variables and lets {@code RMT_SETPOKEY} +
 * {@code CopyAtariMemoryToPokey} carry them to the POKEY once per
 * instrument-speed sub-frame ({@code RenderSoundV2}); here the bytes are
 * poked into the POKEY pair directly (all channels on, as C++ sets them
 * for the export) - the same registers by a shorter route - and the
 * sub-frame's share of the frame's cycles is rendered.
 *
 * <p>Output is 16-bit signed PCM, 44.1 kHz, 2 channels (C++: 8-bit); a mono
 * song's single POKEY is on both channels, as C++'s 2-channel output. Until
 * the audio batch (B8) this class played an exported module through ASAP's
 * own RMT player instead, which uses the classic frequency tables rather
 * than the tracker driver's (see {@code LivePlaybackTest}).
 *
 * <p>Stereo: a stream frame is the second POKEY's 9 bytes followed by the
 * first POKEY's ({@link PokeyStream#record}'s layout). C++'s
 * {@code ExportWAV} read bytes 0-8 as the first POKEY's registers and never
 * set the second's, so a stereo WAV carried only the right-hand POKEY,
 * played on the left one - fixed in both languages on 2026-09-27.
 *
 * <p>Returns a complete WAV file as a {@code byte[]} (header + samples),
 * matching this port's established byte-array-over-stream idiom, rather
 * than C++'s real on-disk {@code CWaveFile}.
 */
public final class WaveFileExporter {

	private static final int CHANNELS = 2;
	private static final int BITS_PER_SAMPLE = 16;
	private static final int HEADER_SIZE = 44;

	private WaveFileExporter() {
	}

	/**
	 * Renders the frames up to {@code pokeyStream}'s loop point
	 * ({@link PokeyStream#getFirstCountPoint}) - one full, non-repeating
	 * playthrough, exactly what C++'s {@code ExportWAV} replays - for a song
	 * with the given video standard, channel count and instrument speed.
	 */
	public static byte[] exportWav(PokeyStream pokeyStream, boolean ntsc, boolean stereo, int instrumentSpeed) {
		AtariCpu cpu = new AtariCpu(ntsc, stereo);
		int frames = pokeyStream.getFirstCountPoint();
		int frameSize = stereo ? 18 : 9;
		byte[] stream = pokeyStream.getFrameBytes(frames, 0);
		int frameCycles = Atari.getFrameCycleCount(ntsc);
		int subFrames = Math.max(1, instrumentSpeed);

		ByteArrayOutputStream samples = new ByteArrayOutputStream(frames * 900 * CHANNELS * 2);
		byte[] rendered = new byte[8192];
		byte[] output = new byte[8192];
		for (int frame = 0; frame < frames; frame++) {
			int offset = frame * frameSize;
			int remainingCycles = frameCycles;
			for (int i = subFrames; i > 0; i--) {
				// RenderSoundV2: SetPokey + CopyAtariMemoryToPokey per sub-frame, then the sub-frame's share of the chunk
				int first = stereo ? offset + 9 : offset;
				for (int r = 0; r < 9; r++) {
					cpu.pokeRegister(r, stream[first + r] & 0xFF);
				}
				if (stereo) {
					for (int r = 0; r < 9; r++) {
						cpu.pokeRegister(16 + r, stream[offset + r] & 0xFF);
					}
				}
				int cycles = remainingCycles / i;
				remainingCycles -= cycles;
				int blocks = cpu.render(cycles, rendered, 0);
				int n = AtariCpu.toTwoChannels(rendered, blocks, cpu.getBlockSize(), output, 0);
				samples.write(output, 0, n);
			}
		}

		byte[] data = samples.toByteArray();
		byte[] wav = new byte[HEADER_SIZE + data.length];
		writeHeader(wav, data.length);
		System.arraycopy(data, 0, wav, HEADER_SIZE, data.length);
		return wav;
	}

	/** The canonical 44-byte RIFF/WAVE PCM header ({@code CWaveFile::OpenFile}'s format: 44.1 kHz, 16-bit, 2 channels). */
	private static void writeHeader(byte[] wav, int dataLength) {
		int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;
		putAscii(wav, 0, "RIFF");
		putInt(wav, 4, 36 + dataLength);
		putAscii(wav, 8, "WAVE");
		putAscii(wav, 12, "fmt ");
		putInt(wav, 16, 16); // PCM format chunk size
		putShort(wav, 20, 1); // PCM
		putShort(wav, 22, CHANNELS);
		putInt(wav, 24, AtariCpu.SAMPLE_RATE);
		putInt(wav, 28, AtariCpu.SAMPLE_RATE * blockAlign);
		putShort(wav, 32, blockAlign);
		putShort(wav, 34, BITS_PER_SAMPLE);
		putAscii(wav, 36, "data");
		putInt(wav, 40, dataLength);
	}

	private static void putAscii(byte[] b, int off, String s) {
		for (int i = 0; i < s.length(); i++) {
			b[off + i] = (byte) s.charAt(i);
		}
	}

	private static void putInt(byte[] b, int off, int v) {
		b[off] = (byte) v;
		b[off + 1] = (byte) (v >> 8);
		b[off + 2] = (byte) (v >> 16);
		b[off + 3] = (byte) (v >> 24);
	}

	private static void putShort(byte[] b, int off, int v) {
		b[off] = (byte) v;
		b[off + 1] = (byte) (v >> 8);
	}

	/** Frames per second for {@code CLZSSFile::GetFrameSize}'s NTSC/PAL video frame rate (the exact frame-cycle counts over the POKEY clock). */
	public static double getFrameRate(boolean ntsc) {
		return ntsc ? 1789772.0 / 29868 : 1773447.0 / 35568;
	}
}
