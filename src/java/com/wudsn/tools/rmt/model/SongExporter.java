package com.wudsn.tools.rmt.model;

/**
 * Ported from CSongExporter (src/cpp/SongExporter.h, SongExporterCore.cpp) -
 * {@link #exportLzss} only. {@code ExportCompactLZSS} is deliberately not
 * ported: its own C++ source already self-describes as "TODO: What is
 * this? Currently unused?" (see {@code SongExporter.h}) and its body
 * contains genuinely dead/confused logic (an empty conditional branch
 * whose own comment reads "I don't know anymore, at this point...") and
 * writes a diagnostic text-log dump, not a real compressed export -
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
}
