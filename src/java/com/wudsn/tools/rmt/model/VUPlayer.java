package com.wudsn.tools.rmt.model;

/**
 * Ported from VUPlayer (src/cpp/VUPlayer.h/.cpp) - {@link #patchMemoryForSapB}
 * plus only the memory-address constants it actually uses (a small subset
 * of {@code src/cpp/lzssp.h}'s several hundred addresses - the rest are
 * for VUPlayer's own UI/debug-overlay/keyboard-handling code, entirely
 * unrelated to the SAP-B export path this class exists for).
 *
 * <p><b>A pre-existing C++ oddity preserved as-is, not fixed</b>:
 * {@link #patchMemoryForSapB} writes to {@code memory[LZSS_POINTER]} eight
 * times in a row (four for the "song start" pointers, four for the
 * "song end" pointers) - only the last write actually survives, since it's
 * the same single address every time. The original C++ source already
 * flags this itself with two {@code // TODO: Why same address?} comments,
 * so this is a known, already-acknowledged-but-unresolved oddity in
 * already-C++-characterized, low-priority ("hacked up") code - not a
 * provable bug this port should unilaterally fix.
 */
public final class VUPlayer {

	private VUPlayer() {
	}

	public static final int LOOP_FLAG = 0x0D3E; // LZSSP_LOOP_COUNT
	public static final int STEREO_FLAG = 0x0E0C; // LZSSP_IS_STEREO_FLAG
	public static final int SONG_SPEED = 0x183D; // LZSSP_PLAYER_SONG_SPEED
	public static final int DO_PLAY_ADDR = 0x193C; // LZSSP_DO_PLAY
	public static final int RTS_NOP = 0x1990; // LZSSP_VU_PLAYER_RTS_NOP
	public static final int INIT_SAP = 0x1E9B;

	public static final int LZSS_POINTER = 0x1F40; // LZSSP_SONGINDEX
	public static final int SEQUENCE = 0x1F80; // LZSSP_SONGSEQUENCE
	public static final int SECTION = 0x2000; // LZSSP_SONGSECTION
	public static final int SONGDATA = 0x2040; // LZSSP_LZ_DTA

	public static final int SOUNGTIMER = 0x1FC0; // LZSSP_SONGTIMERCOUNT

	private static final int IS_FADEING_OUT = 0x0D69; // LZSSP_IS_FADEING_OUT
	private static final int STOP_ON_FADE_END = 0x197A; // LZSSP_STOP_ON_FADE_END
	private static final int SETNEWSONGPTRSFULL = 0x0CDB; // LZSSP_SETNEWSONGPTRSFULL
	private static final int SONGIDX = 0x0CF0; // LZSSP_SONGIDX

	/**
	 * Patches a loaded VUPlayer binary's memory image for a SAP-B-style
	 * export: forces an infinite loop, installs the LZSS song-start/
	 * song-end pointers and the SAP-mode init hack, and copies the
	 * compressed intro/loop sections into place - mirrors
	 * {@code VUPlayer::PatchMemoryForSAP_B} exactly, including the
	 * repeated-write oddity documented in this class's own javadoc.
	 */
	public static void patchMemoryForSapB(byte[] memory, Song song, int tracks4_8, byte[] buf2, byte[] buf3, int intro, int loop, int targetAddrOfModule, int lzssOffset, int lzssEnd) {
		// Patch: change a JMP [label] to a RTS with 2 NOPs
		memory[RTS_NOP] = (byte) 0x60;
		memory[RTS_NOP + 1] = (byte) 0xEA;
		memory[RTS_NOP + 2] = (byte) 0xEA;

		// Patch: change a $00 to $FF to force the LOOP flag to be infinite
		memory[LOOP_FLAG] = (byte) 0xFF;

		// SAP initialisation patch, running from address 0x3080 in Atari executable
		byte[] sapBytes = {
				(byte) 0x8D, (byte) (SONGIDX & 0xff), (byte) (SONGIDX >> 8), // STA SongIdx
				(byte) 0xA2, (byte) 0x00, // LDX #0
				(byte) 0x8E, (byte) (IS_FADEING_OUT & 0xff), (byte) (IS_FADEING_OUT >> 8), // STX is_fadeing_out
				(byte) 0x8E, (byte) (STOP_ON_FADE_END & 0xff), (byte) (STOP_ON_FADE_END >> 8), // STX stop_on_fade_end
				(byte) 0x4C, (byte) (SETNEWSONGPTRSFULL & 0xff), (byte) (SETNEWSONGPTRSFULL >> 8) // JMP SetNewSongPtrsLoopsOnly
		};
		System.arraycopy(sapBytes, 0, memory, INIT_SAP, sapBytes.length);

		memory[SONG_SPEED] = (byte) song.getInstrumentSpeed(); // Song speed
		memory[STEREO_FLAG] = (byte) (song.isStereo(tracks4_8) ? 0xFF : 0x00); // Is the song stereo?

		// SongStart pointers
		// TODO: Why same address? (see class javadoc)
		memory[LZSS_POINTER] = (byte) (targetAddrOfModule >> 8); // SongsSHIPtrs
		memory[LZSS_POINTER] = (byte) (lzssOffset >> 8); // SongsIndexEnd
		memory[LZSS_POINTER] = (byte) (targetAddrOfModule & 0xFF); // SongsSLOPtrs
		memory[LZSS_POINTER] = (byte) (lzssOffset & 0xFF); // SongsDummyEnd

		// SongEnd pointers
		// TODO: Why same address? (see class javadoc)
		memory[LZSS_POINTER] = (byte) (lzssOffset >> 8); // LoopsIndexStart
		memory[LZSS_POINTER] = (byte) (lzssEnd >> 8); // LoopsIndexEnd
		memory[LZSS_POINTER] = (byte) (lzssOffset & 0xFF); // LoopsSLOPtrs
		memory[LZSS_POINTER] = (byte) (lzssEnd & 0xFF); // LoopsDummyEnd

		if (intro > 16) {
			System.arraycopy(buf2, 0, memory, targetAddrOfModule, intro);
			System.arraycopy(buf3, 0, memory, lzssOffset, loop);
		} else {
			System.arraycopy(buf3, 0, memory, lzssOffset, loop);
		}
	}
}
