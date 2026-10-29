package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from CRmtExporter (src/cpp/RmtExporter.h/.cpp, RmtExporterCore.cpp) -
 * {@link #exportAsRMT}/{@link #exportAsStrippedRMTApply} only, the
 * dialog-independent halves {@code RmtExporterCore.cpp}'s own header
 * comment already confirms are hazard-free. {@code ExportAsStrippedRMT()}
 * (RmtExporter.cpp) instantiates a real {@code CExportStrippedRMTDialog}
 * MFC dialog to gather its parameters before delegating to
 * {@link #exportAsStrippedRMTApply} - not ported, matching this project's
 * established "real dialog stays deferred" pattern.
 *
 * <p>Static methods taking {@link Song}/{@link Instruments} explicitly
 * (matching C++'s own {@code CRmtExporter::ExportAsRMT(CSong&, ...)} shape -
 * a free-standing exporter class, not a {@link Song} method) rather than
 * being folded onto {@link Song} itself.
 *
 * <p>C++'s {@code std::ostream&} becomes a returned {@code byte[]} (the
 * format is binary) - matches {@code saveRMW}/{@code loadRMW}'s established
 * byte-array-over-stream idiom; {@code null} signals
 * {@link #exportAsStrippedRMTApply}'s one guard-only failure (a track too
 * event-dense to encode).
 */
public final class RmtExporter {

	private RmtExporter() {
	}

	/**
	 * The {@code .rmt} file of the module RMT built at
	 * {@code mem[targetAddrOfModule..firstByteAfterModule)} (by
	 * {@link SongIO#makeModule}): the module's segment, then the segment
	 * with the song name and each saved instrument's name (null-terminated,
	 * in number order) - C++'s two {@code CAtariIO::SaveBinaryBlock} calls.
	 * Since plan 60 M2 written by {@link RmtModuleFile}; the saved
	 * instruments are the ones the module holds.
	 */
	public static byte[] exportAsRMT(Song song, byte[] mem, int targetAddrOfModule, int firstByteAfterModule) {
		RmtModule module = RmtModule.parse(mem, targetAddrOfModule, firstByteAfterModule);
		List<String> instrumentNames = new ArrayList<>();
		for (int number : module.instruments().keySet()) {
			instrumentNames.add(Song.nameToString(song.instruments.getName(number)).stripTrailing());
		}
		return new RmtModuleFile(module, targetAddrOfModule, song.getName(), instrumentNames).write();
	}

	/**
	 * The stripped module of the song at {@code targetAddrOfModule} as a
	 * single binary block - the dialog-independent work
	 * {@code ExportAsStrippedRMT()} delegates to once its target
	 * address/SFX-support parameters are confirmed. Returns {@code null} if
	 * the song does not make a module (see {@link SongIO#makeModule}).
	 */
	public static byte[] exportAsStrippedRMTApply(Song song, int targetAddrOfModule, boolean sfxSupport) {
		// SFX support keeps all non-empty instruments (sound effects trigger
		// instruments directly, without any track referencing them); without
		// it, unused instruments are stripped. The two iotypes were swapped
		// here since 1.34, so an SFX-only module (instruments, no tracks)
		// exported to a 30-byte husk (issue #5).
		try {
			RmtModule module = RmtModuleCodec.fromSong(song, sfxSupport ? SongIOType.RMT : SongIOType.RMTSTRIPPED);
			return RmtModuleFile.stripped(module, targetAddrOfModule).write();
		} catch (RmtModule.FormatException e) {
			return null;
		}
	}
}
