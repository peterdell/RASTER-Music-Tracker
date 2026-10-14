package org.atari.raster.rmt.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
 * {@code plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md}'s Phase A item 4 for why
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

	// resources/players/vu_player_v2.obx of the program folder's rmt/ layout
	// (C++: GetResourceFilePath(), see ProgramFolder). Package-private (not
	// private): CSAPFileExporter's own on-disk std::ifstream load and
	// CSongExporter::ExportXEX_LZSS's CRmtAtariBinaries::GetVUPlayerBinary()
	// (a real MFC CFile-based load) read the exact same real file in C++
	// too - two different C++ loading mechanisms with no observable
	// difference, so SongExporter reuses this same path/loading logic
	// rather than duplicating a second one.
	static java.nio.file.Path vuPlayerPath() {
		return RmtAtariBinaries.getPath(RmtAtariBinaries.VU_PLAYER_FOLDER, RmtAtariBinaries.VU_PLAYER_FILE);
	}

	/**
	 * {@code CSAPFileExporter::ExportSAP_B_LZSS()}: the SAP text header (the
	 * caller sets the type to {@code "B"}), then the VU-Player and the song
	 * data as two binary blocks. The subtunes are dumped, compressed and laid
	 * out by {@link SongExporter#buildLzssSubtunes}, exactly as the XEX
	 * export does for the same player - the export used to write the memory
	 * blocks of the old VU-Player ($1900-$27FF) while vu_player_v2.obx lives
	 * at $0C1B-$1F3F: INIT jumped into bytes the file did not hold, and no
	 * player could run any SAP this program exported (ported from the RITMO
	 * fork's b243f10; plans/32_RITMO_FORK_ANALYSIS_PLAN.md).
	 *
	 * @throws IllegalStateException if {@code vu_player_v2.obx} can't be
	 * loaded, or the LZSS data doesn't fit in memory - both guard-only C++
	 * error paths that show a {@code MessageBox} and return {@code false}.
	 */
	public static byte[] exportSapBLzss(SapFile sapFile, Song song, int tracks4_8, AtariTrackerDriver atariTrackerDriver, ChannelControl channelControl, TrackClipboard clipboard, Undo undo) {
		byte[] memory = new byte[Atari.MEMORY_SIZE];
		byte[] vuPlayerData = RmtAtariBinaries.getVUPlayerBinary();
		if (vuPlayerData == null) { // C++'s GetVUPlayerBinary() failure box
			throw new IllegalStateException("Fatal error with RMT LZSS system routines.\nCouldn't load '" + vuPlayerPath() + "'.");
		}
		AtariIO.Result loadResult = AtariIO.loadBinaryFile(vuPlayerData, memory);
		if (loadResult.bytesRead() <= 0) {
			throw new IllegalStateException("Fatal error with RMT LZSS system routines.\nCouldn't load '" + vuPlayerPath() + "'.");
		}

		// The subtunes: the songlines the SAP file's subsongs start from, else the song from its start
		int[] subtunes = sapFile.getSubsongPositions();
		if (subtunes.length == 0) {
			subtunes = new int[] { 0 };
		}
		SongExporter.LzssSubtunes built = SongExporter.buildLzssSubtunes(song, tracks4_8, subtunes, memory, atariTrackerDriver, channelControl, clipboard, undo);

		sapFile.setSongs(subtunes.length);
		sapFile.setInitAddress(VUPlayer.INIT_SAP);
		sapFile.setPlayerAddress(VUPlayer.DO_PLAY_ADDR);
		VUPlayer.patchMemoryForSapB(memory, song, tracks4_8, subtunes.length);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(sapFile.export().getBytes(StandardCharsets.US_ASCII));

		// The binary: the LZSS driver and the player, then the song index, lists and streams until the end of the data
		out.writeBytes(AtariIO.saveBinaryBlock(memory, VUPlayer.PLAYLZ16BEGIN, VUPlayer.LZSS_POINTER - 1, true));
		out.writeBytes(AtariIO.saveBinaryBlock(memory, VUPlayer.LZSS_POINTER, built.lzssTotal() - 1, false));

		return out.toByteArray();
	}

}
