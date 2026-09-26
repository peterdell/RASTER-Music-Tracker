package com.wudsn.tools.rmt.model;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPArgumentException;
import net.sf.asap.ASAPFormatException;
import net.sf.asap.ASAPSampleFormat;

/**
 * Ported from CWaveFileExporter (src/cpp/WaveFileExporter.h/.cpp) -
 * {@code ExportWAV} only, redesigned around {@link AsapEmulator}'s
 * underlying {@code ASAP} directly rather than porting C++'s own separate
 * software POKEY audio-synthesis engine ({@code CXPokey}/
 * {@code PokeyRenderer.h/.cpp}/{@code PokeyCore.cpp}, confirmed
 * C++-tested but never ported to Java - see
 * {@code plans/JAVA_PORT_NEXT_STEPS_PLAN.md}'s Phase A item 4).
 *
 * <p><b>Deliberate idiomatic substitution, not a faithful line-for-line
 * port</b>: C++'s {@code ExportWAV} replays an already-recorded
 * {@code PokeyStream}'s raw register bytes into {@code CXPokey}'s own
 * synthesis engine to produce PCM samples. This port instead exports the
 * song to a real RMT module (same as {@link Song#dumpSongToPokeyStream})
 * and lets ASAP's own {@code load}/{@code playSong}/{@code generate}
 * independently decode and render it - ASAP already contains a complete,
 * tested POKEY audio synthesizer for exactly this purpose, so porting a
 * second, redundant one from scratch would duplicate real engineering
 * effort for no behavioral gain (matches this port's established
 * "idiomatic substitution when it produces identical behavior" precedent,
 * e.g. {@code Fraction}'s collapsed increment operators). The observable
 * result - a valid WAV file that sounds like the song - is the same; only
 * which software POKEY emulator computes the samples differs.
 *
 * <p>Returns a complete WAV file as a {@code byte[]} (header + samples),
 * matching this port's established byte-array-over-stream idiom, rather
 * than C++'s real on-disk {@code CWaveFile}.
 */
public final class WaveFileExporter {

	private WaveFileExporter() {
	}

	/**
	 * Renders {@code durationMs} milliseconds of the current song as a WAV
	 * file (16-bit signed PCM, ASAP's default 44100 Hz sample rate). Callers
	 * typically derive {@code durationMs} from a prior
	 * {@link Song#dumpSongToPokeyStream} call's
	 * {@link PokeyStream#getFirstCountPoint} (one full, non-repeating
	 * playthrough) and {@link Song#isNTSC} (for the frames-per-second
	 * conversion) - matching the loop point C++'s {@code ExportWAV} itself
	 * replays.
	 */
	public static byte[] exportWav(Song song, Instruments instruments, int tracks4_8, int durationMs) {
		byte[] mem = new byte[Atari.MEMORY_SIZE];
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int targetAddrOfModule = 0x4000;
		int firstByteAfterModule = song.makeModule(mem, targetAddrOfModule, SongIOType.RMT, instrumentSavedFlags, trackSavedFlags, tracks4_8);
		byte[] moduleBytes = RmtExporter.exportAsRMT(song, instruments, mem, targetAddrOfModule, firstByteAfterModule, instrumentSavedFlags);

		ASAP asap = new ASAP();
		try {
			asap.load("song.rmt", moduleBytes, moduleBytes.length);
			asap.playSong(0, durationMs);
		} catch (ASAPFormatException | ASAPArgumentException e) {
			throw new IllegalArgumentException("Not a valid RMT module", e);
		}

		ASAPSampleFormat format = ASAPSampleFormat.S16_L_E;
		int channels = asap.getInfo().getChannels();
		int blockSize = channels * 2; // 16-bit samples
		int blocks = (int) Math.round(durationMs / 1000.0 * asap.getSampleRate());

		byte[] header = new byte[128];
		int headerLen = asap.getWavHeader(header, format, false);

		byte[] samples = new byte[blocks * blockSize];
		int samplesLen = asap.generate(samples, samples.length, format);

		byte[] out = new byte[headerLen + samplesLen];
		System.arraycopy(header, 0, out, 0, headerLen);
		System.arraycopy(samples, 0, out, headerLen, samplesLen);
		return out;
	}

	/** Frames per second for {@code CLZSSFile::GetFrameSize}'s NTSC/PAL video frame rate, matching ASAP's own internal per-frame cycle counts. */
	public static double getFrameRate(boolean ntsc) {
		return ntsc ? 1789772.0 / 29868 : 1773447.0 / 35568;
	}
}
