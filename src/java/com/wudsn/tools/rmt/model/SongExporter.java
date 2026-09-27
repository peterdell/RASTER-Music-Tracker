package com.wudsn.tools.rmt.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Ported from CSongExporter (src/cpp/SongExporter.h, SongExporterCore.cpp) -
 * {@link #exportLzss}/{@link #exportXexLzss} only. {@code ExportCompactLZSS}
 * is deliberately not ported: its own C++ source already self-describes as
 * "TODO: What is this? Currently unused?" (see {@code SongExporter.h}) and
 * its body contains genuinely dead/confused logic (an empty conditional
 * branch whose own comment reads "I don't know anymore, at this point...")
 * and writes a diagnostic text-log dump, not a real compressed export -
 * matching {@code plans/EXPORTLZSS_PLAN.md}'s own "low priority, hacked
 * up" characterization. Not real, meaningful behavior worth preserving.
 *
 * <p>C++'s three real on-disk {@code .lzss} files (full/intro/loop
 * sections) become a {@link LzssExportResult} of three {@code byte[]}
 * fields, matching this port's byte-array-over-stream idiom - each is
 * empty (matching C++'s own {@code if (... > 16)} "too small to bother"
 * gating, not an error) rather than a file simply not being written.
 */
public final class SongExporter {

	private SongExporter() {
	}

	public record LzssExportResult(byte[] full, byte[] intro, byte[] loop) {
	}

	/**
	 * Compresses the full tune (up to the loop point), the intro section
	 * (up to the start of the detected loop), and the looped section (the
	 * loop's own body) separately - mirrors {@code CSongExporter::ExportLZSS}
	 * exactly, including its default {@link SapROptimization#AUDC}
	 * optimisation (C++'s {@code LZSS_SAP}'s own default argument).
	 */
	public static LzssExportResult exportLzss(PokeyStream pokeyStream) {
		CompressLzss lzssData = new CompressLzss();

		byte[] full = compressSection(lzssData, pokeyStream.getFrameBytes(pokeyStream.getFirstCountPoint(), 0));
		byte[] intro = compressSection(lzssData, pokeyStream.getFrameBytes(pokeyStream.getThirdCountPoint(), 0));
		byte[] loop = compressSection(lzssData, pokeyStream.getFrameBytes(pokeyStream.getSecondCountPoint(), pokeyStream.getFirstCountPoint()));

		return new LzssExportResult(full, intro, loop);
	}

	/**
	 * {@code src.length == 0} (a section with zero frames, e.g. a very short
	 * loop's {@code thirdCountPoint}) is skipped rather than handed to
	 * {@link CompressLzss#compress} - not a hazard C++'s own
	 * {@code LZSS_SAP(buf, 0, dst)} call needs guarding against (its loops
	 * simply don't execute for a zero length), but {@link CompressLzss}'s
	 * own already-documented "malformed length" gap throws
	 * {@code ArrayIndexOutOfBoundsException} for it instead (see that
	 * class's own javadoc) - naturally reached here for the first time,
	 * since every prior caller only ever fed it real, non-empty data.
	 * Below the {@code > 16} threshold either way, so the observable result
	 * (an empty section) is identical to C++'s own "too small to bother"
	 * gating.
	 */
	private static byte[] compressSection(CompressLzss lzssData, byte[] src) {
		if (src.length == 0) {
			return new byte[0];
		}
		byte[] compressed = lzssData.compress(src, SapROptimization.AUDC);
		return compressed.length > 16 ? compressed : new byte[0];
	}

	/**
	 * Tries every {@link SapROptimization} and keeps the smallest result -
	 * mirrors {@code CSongExporter::BruteforceOptimalLZSS} exactly (which
	 * recomputes the winning optimisation a second time into a shared
	 * output buffer; this port just keeps the already-computed {@code byte[]}
	 * from the loop itself, since {@link CompressLzss#compress} returns a
	 * fresh array per call rather than writing into a caller-supplied one).
	 * Omits C++'s {@code RefreshScreen()}/{@code SetStatusBarText()} progress
	 * notices - real UI, no Java equivalent, no effect on the result.
	 */
	private static byte[] bruteforceOptimalLzss(CompressLzss lzssData, byte[] src) {
		byte[] best = null;
		for (SapROptimization optimisation : SapROptimization.values()) {
			byte[] candidate = lzssData.compress(src, optimisation);
			if (best == null || candidate.length < best.length) {
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * Converts ASCII text in place to Atari internal screen-code bytes -
	 * mirrors {@code CSongExporter::StrToAtariVideo} exactly, operating on
	 * {@code mem[offset..offset+count)} instead of a raw {@code char*}
	 * (this port's established "explicit array + offset" idiom for an
	 * in-place buffer mutation).
	 */
	static void strToAtariVideo(byte[] mem, int offset, int count) {
		for (int i = 0; i < count; i++) {
			int a = mem[offset + i] & 0x7f;
			if (a < 32) {
				a = 0;
			} else if (a < 96) {
				a -= 32;
			}
			mem[offset + i] = (byte) a;
		}
	}

	/**
	 * Compresses each subsong's SAP-R data (bruteforcing the best
	 * {@link SapROptimization} per section) and reconstructs a complete
	 * VUPlayer XEX binary from it - mirrors
	 * {@code CSongExporter::ExportXEX_LZSS(CSongExport&, CXEXFile, std::ostream&)}
	 * exactly. Reads the same real, checked-in
	 * {@code rmt/resources/players/vu_player_v2.obx} as
	 * {@link SapFileExporter#exportSapBLzss} (see that class's own
	 * {@code VU_PLAYER_PATH} javadoc for why one file, two different C++
	 * loading mechanisms, needs only one Java load here). A fresh
	 * {@link PokeyStream} is recorded per subsong via
	 * {@link Song#dumpSongToPokeyStream} (mode {@link PlayMode#PLAY_FROM},
	 * not {@link PlayMode#PLAY_SONG} - each subsong starts partway through
	 * the song, at its own songline).
	 *
	 * @throws IllegalStateException if {@code vu_player_v2.obx} can't be
	 * loaded, or the patched LZSS data doesn't fit in memory - both
	 * guard-only C++ error paths that show a {@code MessageBox} and return
	 * {@code false}, matching this port's established "fatal precondition
	 * becomes an exception" idiom.
	 */
	public static byte[] exportXexLzss(Song song, int tracks4_8, XexFile xexFile, AtariTrackerDriver atariTrackerDriver, ChannelControl channelControl, TrackClipboard clipboard, Undo undo) {
		Song.SubsongParts subsongParts = song.getSubsongParts(tracks4_8);
		int subsongs = subsongParts.count();
		String parts = subsongParts.parts();
		int[] subtune = new int[subsongs];
		for (int i = 0; i < subsongs; i++) {
			subtune[i] = Integer.parseInt(parts.substring(i * 3, i * 3 + 2), 16);
		}

		int lzssChunk = 0; // Subtune size will be added to be used as the offset to the next one
		int lzssTotal = 0; // Final offset for LZSS bytes to export
		int framesCount = 0;

		int section = VUPlayer.SECTION;
		int sequence = VUPlayer.SEQUENCE;

		byte[] mem = new byte[Atari.MEMORY_SIZE];

		// Load VUPlayerLZSS to memory
		byte[] vuPlayerData;
		try {
			vuPlayerData = Files.readAllBytes(SapFileExporter.VU_PLAYER_PATH);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (AtariIO.loadBinaryFile(vuPlayerData, mem).bytesRead() <= 0) {
			throw new IllegalStateException("Fatal error with RMT LZSS system routines.\nCouldn't load '" + SapFileExporter.VU_PLAYER_PATH + "'.");
		}

		CompressLzss lzssData = new CompressLzss();

		for (int count = 0; count < subsongs; count++) {
			// a LZSS export will typically make use of intro and loop only, unless specified otherwise
			byte[] buf2 = new byte[0];
			byte[] buf3 = new byte[0];

			PokeyStream pokeyStream = new PokeyStream();
			song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_FROM, subtune[count], 0, tracks4_8, atariTrackerDriver, channelControl, clipboard, undo);

			// There is an Intro section
			if (pokeyStream.getThirdCountPoint() > 0) {
				buf2 = bruteforceOptimalLzss(lzssData, pokeyStream.getFrameBytes(pokeyStream.getThirdCountPoint(), 0));
			}
			// There is a Loop section
			if (pokeyStream.getFirstCountPoint() > 0) {
				buf3 = bruteforceOptimalLzss(lzssData, pokeyStream.getFrameBytes(pokeyStream.getSecondCountPoint(), pokeyStream.getFirstCountPoint()));
			}
			int intro = buf2.length;
			int loop = buf3.length;

			// Add the number of frames recorded to the total count
			framesCount += pokeyStream.getFirstCountPoint();

			// Some additional variables that will be used below
			int targetAddrOfModule = VUPlayer.SONGDATA + lzssChunk; // All the LZSS data will be written starting from this address
			int lzssStartAddress = targetAddrOfModule + intro;
			int lzssEndAddress = lzssStartAddress + loop; // this sets the address that defines where the data stream has reached its end

			// If the size is too big, abort the process
			if (lzssEndAddress > 0xBFFF) { // RAM_MAX_ADDRESS
				throw new IllegalStateException(String.format(
						"Error, LZSS data ($%04X - $%04X) is too big to fit in memory!\n\n"
								+ "High Instrument Speed and/or Stereo greatly inflate memory usage, even when data is compressed",
						lzssStartAddress, lzssEndAddress));
			}

			// Set the song section and timer index
			int index = VUPlayer.LZSS_POINTER + count * 4;
			int timerIndex = VUPlayer.SOUNGTIMER + count * 4;
			int subtuneTimeTotal = 0xFFFFFF / pokeyStream.getFirstCountPoint();
			int subtuneLoopPoint = subtuneTimeTotal * pokeyStream.getThirdCountPoint();
			int chunk = 0;

			mem[index] = (byte) (section & 0xFF);
			mem[index + 1] = (byte) (section >> 8);
			mem[index + 2] = (byte) (sequence & 0xFF);
			mem[index + 3] = (byte) (sequence >> 8);
			mem[timerIndex] = (byte) (subtuneTimeTotal >> 16);
			mem[timerIndex + 1] = (byte) (subtuneTimeTotal >> 8);
			mem[timerIndex + 2] = (byte) (subtuneTimeTotal & 0xFF);
			mem[timerIndex + 3] = (byte) (subtuneLoopPoint >> 16);

			// If there is an Intro section...
			if (intro > 0) {
				System.arraycopy(buf2, 0, mem, targetAddrOfModule, intro);
				lzssChunk += intro;
				mem[section] = (byte) (targetAddrOfModule & 0xFF);
				mem[section + 1] = (byte) (targetAddrOfModule >> 8);
				mem[sequence] = (byte) chunk;
				section += 2;
				sequence += 1;
				chunk += 1;
			}

			// If there is a Loop section...
			if (loop > 0) {
				System.arraycopy(buf3, 0, mem, lzssStartAddress, loop);
				lzssChunk += loop;
				mem[section] = (byte) (lzssStartAddress & 0xFF);
				mem[section + 1] = (byte) (lzssStartAddress >> 8);
				mem[sequence] = (byte) chunk;
				section += 2;
				sequence += 1;
				chunk += 1;
			}

			// End of data, will be overwritten if there is more data to export
			mem[section] = (byte) (lzssEndAddress & 0xFF);
			mem[section + 1] = (byte) (lzssEndAddress >> 8);
			section += 2;
			mem[sequence] = (byte) ((chunk | 0x80) - 1);
			sequence += 1;

			// Update the subtune offsets to export the next one
			lzssTotal = lzssEndAddress;
		}

		// Write the Atari Video text to memory, for 5 lines of 40 characters
		System.arraycopy(xexFile.atariText, 0, mem, VUPlayer.LINE_1, XexFile.ATARI_TEXT_SIZE);

		// Write the total framescount on the top line, next to the Region and VBI speed, for 28 characters
		byte[] framesDisplay = new byte[28];
		byte[] framesText = String.format("(%d frames total)", framesCount).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(framesText, 0, framesDisplay, 0, Math.min(framesText.length, 28));
		System.arraycopy(framesDisplay, 0, mem, VUPlayer.LINE_0 + 0x0B, 28);
		strToAtariVideo(mem, VUPlayer.LINE_0 + 0x0B, 28);

		// I know the binary I have is currently set to NTSC, so I'll just convert to PAL and keep this going for now...
		if (!xexFile.ntsc) {
			byte[] regionBytes = {
					(byte) 0xB9, (byte) ((VUPlayer.TABPPPAL - 1) & 0xff), (byte) ((VUPlayer.TABPPPAL - 1) >> 8), // LDA tabppPAL-1,y
					(byte) 0x8D, (byte) (VUPlayer.ACPAPX2 & 0xFF), (byte) (VUPlayer.ACPAPX2 >> 8), // STA acpapx2
					(byte) 0xE0, (byte) 0x9B, // CPX #$9B
					(byte) 0x30, (byte) 0x05, // BMI set_ntsc
					(byte) 0xB9, (byte) ((VUPlayer.TABPPPALFIX - 1) & 0xff), (byte) ((VUPlayer.TABPPPALFIX - 1) >> 8), // LDA tabppPALfix-1,y
					(byte) 0xD0, (byte) 0x03, // BNE region_done
					(byte) 0xB9, (byte) ((VUPlayer.TABPPNTSCFIX - 1) & 0xFF), (byte) ((VUPlayer.TABPPNTSCFIX - 1) >> 8) // LDA tabppNTSCfix-1,y
			};
			System.arraycopy(regionBytes, 0, mem, VUPlayer.REGION, regionBytes.length);
		}

		// Additional patches from the Export Dialog...
		mem[VUPlayer.SONG_SPEED] = (byte) xexFile.instrumentSpeed; // Song speed
		mem[VUPlayer.RASTER_BAR] = (byte) (xexFile.displayRasterbar ? 0x80 : 0x00); // Display the rasterbar for CPU level
		mem[VUPlayer.COLOR] = (byte) xexFile.rasterbarColor; // Rasterbar colour
		mem[VUPlayer.STEREO_FLAG] = (byte) (xexFile.stereo ? 0xFF : 0x00); // Is the song stereo?
		mem[VUPlayer.SONGTOTAL] = (byte) subsongs; // Total number of subtunes
		if (!xexFile.autoRegion) { // Automatically adjust speed between regions?
			for (int i = 0; i < 4; i++) {
				mem[VUPlayer.REGION + 6 + i] = (byte) 0xEA; // set the 4 bytes to NOPs to disable it
			}
		}

		// Reconstruct the export binary for the LZSS Driver, VUPlayer, and all the included data
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(AtariIO.saveBinaryBlock(mem, VUPlayer.PLAYLZ16BEGIN, VUPlayer.LZSS_POINTER, true));

		// Set the run address to VUPlayer
		mem[0x2e0] = (byte) (VUPlayer.VUPLAYER_START & 0xff);
		mem[0x2e1] = (byte) (VUPlayer.VUPLAYER_START >> 8);
		out.writeBytes(AtariIO.saveBinaryBlock(mem, 0x2e0, 0x2e1, false));

		// Overwrite the LZSS data region with both the pointers for subtunes index, and the actual LZSS streams until the end of file
		out.writeBytes(AtariIO.saveBinaryBlock(mem, VUPlayer.LZSS_POINTER, lzssTotal, false));

		return out.toByteArray();
	}
}
