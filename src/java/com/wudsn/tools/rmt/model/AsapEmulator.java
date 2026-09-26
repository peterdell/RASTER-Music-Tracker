package com.wudsn.tools.rmt.model;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPArgumentException;
import net.sf.asap.ASAPFormatException;

/**
 * Wraps {@code net.sf.asap.ASAP} (see {@code lib/java/README.md}) as the
 * Java port's real Atari 6502 CPU/dual-POKEY emulator for
 * {@link Song#dumpSongToPokeyStream}'s per-frame register capture, backing
 * {@link PokeyStream#record}. {@link AtariTrackerDriver} cannot provide this
 * - its {@code play}/{@code setPokey}/{@code silence} are permanently
 * no-ops, since C++'s real CPU wraps a native DLL ({@code sa_c6502.dll})
 * this port has no equivalent of (see {@link AtariTrackerDriver}'s own
 * class javadoc). ASAP's {@code Cpu6502}/{@code Pokey} classes are a
 * complete, portable, pure-software reimplementation of the same hardware -
 * no native dependency at all.
 *
 * <p>Deliberately a separate class from {@link AtariTrackerDriver}, not a
 * replacement for it: {@link AtariTrackerDriver} models live, note-by-note
 * keyboard preview with no whole exported module involved, while this class
 * always plays back a fully-exported module byte array from the very start.
 * These are two different operations that only share one real CPU in the
 * C++ original because C++ has a single universal CPU either way; this port
 * only needs the CPU for the export path, so only that path gets one.
 *
 * <p>{@link #stepFrame} and {@link #getRegisterShadow} are RMT-specific
 * additions to the vendored {@code ASAP} class ({@code src/java/net/sf/asap}) -
 * see that class's own header comment and the two methods' own javadoc for
 * why the unmodified public API (built around "load a module, generate
 * audio") cannot support frame-accurate register capture on its own.
 */
public final class AsapEmulator {

	private final ASAP asap = new ASAP();

	/**
	 * Loads the given RMT module bytes (as produced by
	 * {@link Song#makeModule}/{@link RmtExporter#exportAsRMT}) and starts
	 * playing its first (only) song from the beginning. Call
	 * {@link #stepFrame} repeatedly afterward to advance playback one video
	 * frame at a time.
	 */
	public void startRecording(byte[] moduleBytes) {
		try {
			asap.load("song.rmt", moduleBytes, moduleBytes.length);
			asap.playSong(0, -1);
		} catch (ASAPFormatException | ASAPArgumentException e) {
			throw new IllegalArgumentException("Not a valid RMT module", e);
		}
	}

	/** Advances the emulation by exactly one video frame, producing no audio - see {@code ASAP.stepFrame}'s own javadoc. */
	public void stepFrame() {
		asap.stepFrame();
	}

	/**
	 * Returns the raw byte most recently written to a POKEY audio register -
	 * see {@code ASAP.getPokeyRegisterShadow}'s own javadoc for the exact
	 * {@code chip}/{@code offset} encoding, which matches
	 * {@code CPokeyStream::Record()}'s own addressing exactly.
	 */
	public int getRegisterShadow(int chip, int offset) {
		return asap.getPokeyRegisterShadow(chip, offset);
	}
}
