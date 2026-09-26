package com.wudsn.tools.rmt.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ported from CSAPFileExporter (src/cpp/SAPFileExporter.h,
 * SAPFileExporterCore.cpp) - {@link #exportSapR} only, the dialog-independent
 * half {@code SAPFileExporterCore.cpp}'s own header comment already confirms
 * is hazard-free ({@code ExportSAP_B_LZSS} needs a real on-disk resource
 * file and {@link CompressLzss}'s LZSS engine - not yet ported).
 *
 * <p>Takes an already-recorded {@link PokeyStream} directly rather than
 * C++'s {@code CSongExport} (which lazily triggers
 * {@link Song#dumpSongToPokeyStream} through the unported
 * {@code CSongContainer}/{@code CSongExport} caching pair - see
 * {@code plans/JAVA_PORT_NEXT_STEPS_PLAN.md}'s Phase A item 4 for why
 * that pair was skipped) - the caller runs {@link Song#dumpSongToPokeyStream}
 * itself first.
 *
 * <p>C++'s {@code std::ostream&} becomes a returned {@code byte[]} (the SAP
 * header is ASCII text, but the appended PokeyStream data is raw binary),
 * matching this port's established byte-array-over-stream idiom.
 */
public final class SapFileExporter {

	private SapFileExporter() {
	}

	/**
	 * Writes the SAP-R header (type {@code "R"}) followed by the raw POKEY
	 * register bytes recorded up to {@code pokeyStream}'s loop point -
	 * mirrors {@code CSAPFileExporter::ExportSAP_R} exactly.
	 */
	public static byte[] exportSapR(SapFile sapFile, PokeyStream pokeyStream) {
		sapFile.setType("R");

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(sapFile.export().getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(pokeyStream.getFrameBytes(pokeyStream.getFirstCountPoint(), 0));
		return out.toByteArray();
	}

	// resources/players/vu_player_v2.obx, checked into the repo's own rmt/
	// folder - relative to the working directory, matching this repo's own
	// C++ test convention (AtariBinariesStub.cpp's g_prgpath, resolved to
	// this same rmt/ folder). Maven always runs with the repository root as
	// the working directory, so no further path resolution is needed here.
	private static final Path VU_PLAYER_PATH = Path.of("rmt", "resources", "players", "vu_player_v2.obx");

	/**
	 * Compresses the recorded PokeyStream's intro/loop sections, patches
	 * them into a loaded VUPlayer binary image (see {@link VUPlayer}), and
	 * writes the SAP-B header (caller must have already set {@code sapFile}'s
	 * type to {@code "B"}, matching the real dialog's own responsibility)
	 * followed by the patched memory's three binary blocks - mirrors
	 * {@code CSAPFileExporter::ExportSAP_B_LZSS} exactly, including its own
	 * dead {@code full} computation being dropped (the C++ source computes
	 * it but never reads the result - see {@code PatchMemoryForSAP_B}'s own
	 * commented-out {@code buff1} use).
	 *
	 * @throws IllegalStateException if {@code vu_player_v2.obx} can't be
	 * loaded, or the patched LZSS data doesn't fit in memory - both
	 * guard-only C++ error paths that show a {@code MessageBox} and return
	 * {@code false}, matching this port's established "fatal precondition
	 * becomes an exception" idiom (see e.g. {@link SapFile#export}).
	 */
	public static byte[] exportSapBLzss(SapFile sapFile, Song song, int tracks4_8, PokeyStream pokeyStream) {
		byte[] memory = new byte[Atari.MEMORY_SIZE];
		byte[] vuPlayerData;
		try {
			vuPlayerData = Files.readAllBytes(VU_PLAYER_PATH);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		AtariIO.Result loadResult = AtariIO.loadBinaryFile(vuPlayerData, memory);
		if (loadResult.bytesRead() <= 0) {
			throw new IllegalStateException("Fatal error with RMT LZSS system routines.\nCouldn't load '" + VU_PLAYER_PATH + "'.");
		}

		CompressLzss lzssData = new CompressLzss();
		byte[] buf2 = compressOrEmpty(lzssData, pokeyStream.getFrameBytes(pokeyStream.getThirdCountPoint(), 0));
		byte[] buf3 = compressOrEmpty(lzssData, pokeyStream.getFrameBytes(pokeyStream.getSecondCountPoint(), pokeyStream.getFirstCountPoint()));
		int intro = buf2.length;
		int loop = buf3.length;

		int targetAddrOfModule = VUPlayer.SONGDATA;
		int lzssOffset = (intro > 16) ? targetAddrOfModule + intro : targetAddrOfModule;
		int lzssEnd = lzssOffset + loop;

		if (lzssEnd > 0xBFFF) { // RAM_MAX_ADDRESS
			throw new IllegalStateException("Error, LZSS data is too big to fit in memory!\n\n"
					+ "High Instrument Speed and/or Stereo greatly inflate memory usage, even when data is compressed");
		}

		sapFile.setInitAddress(VUPlayer.INIT_SAP);
		sapFile.setPlayerAddress(VUPlayer.DO_PLAY_ADDR);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(sapFile.export().getBytes(StandardCharsets.US_ASCII));

		VUPlayer.patchMemoryForSapB(memory, song, tracks4_8, buf2, buf3, intro, loop, targetAddrOfModule, lzssOffset, lzssEnd);

		out.writeBytes(AtariIO.saveBinaryBlock(memory, 0x1900, 0x1EFF, true)); // LZSS Driver, and some free bytes for later if needed
		out.writeBytes(AtariIO.saveBinaryBlock(memory, 0x2000, 0x27FF, false)); // VUPlayer only
		out.writeBytes(AtariIO.saveBinaryBlock(memory, VUPlayer.LZSS_POINTER, lzssEnd, false)); // subtunes index + the actual LZSS streams

		return out.toByteArray();
	}

	/** {@code src.length == 0} (a section with zero frames) is skipped rather than handed to {@link CompressLzss#compress} - see {@code SongExporter#compressSection}'s own javadoc for why. Unlike that method, no {@code > 16} clamping here: C++'s {@code ExportSAP_B_LZSS} always uses whatever {@code LZSS_SAP} returns, never dropping a small section. */
	private static byte[] compressOrEmpty(CompressLzss lzssData, byte[] src) {
		return src.length == 0 ? new byte[0] : lzssData.compress(src, SapROptimization.AUDC);
	}
}
