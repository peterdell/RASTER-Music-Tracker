package com.wudsn.tools.rmt.model;

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
}
