package com.wudsn.tools.rmt.model;

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
 * event-dense for {@link Song#makeModule} to encode).
 */
public final class RmtExporter {

	private RmtExporter() {
	}

	/**
	 * Encodes {@code mem[targetAddrOfModule..firstByteAfterModule)} (already
	 * built by {@link Song#makeModule}) as the RMT module's first data
	 * block, followed by a second block holding the song name and each
	 * saved instrument's name (null-terminated, in index order) - mirrors
	 * C++'s two {@code CAtariIO::SaveBinaryBlock} calls exactly. Reuses
	 * {@code mem} as scratch space past {@code firstByteAfterModule} for the
	 * names block, matching C++'s own in-place reuse of the same buffer.
	 */
	public static byte[] exportAsRMT(Song song, Instruments instruments, byte[] mem, int targetAddrOfModule, int firstByteAfterModule, byte[] instrumentSavedFlags) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		out.writeBytes(AtariIO.saveBinaryBlock(mem, targetAddrOfModule, firstByteAfterModule - 1, true));

		int addrOfSongName = firstByteAfterModule;
		byte[] nameBytes = song.getName().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(nameBytes, 0, mem, addrOfSongName, nameBytes.length);
		mem[addrOfSongName + nameBytes.length] = 0;
		int addrInstrumentNames = addrOfSongName + nameBytes.length + 1;

		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			if (instrumentSavedFlags[i] != 0) {
				String name = Song.nameToString(instruments.getName(i)).stripTrailing();
				byte[] instrNameBytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
				System.arraycopy(instrNameBytes, 0, mem, addrInstrumentNames, instrNameBytes.length);
				mem[addrInstrumentNames + instrNameBytes.length] = 0;
				addrInstrumentNames += instrNameBytes.length + 1;
			}
		}

		out.writeBytes(AtariIO.saveBinaryBlock(mem, addrOfSongName, addrInstrumentNames - 1, false));
		return out.toByteArray();
	}

	/**
	 * Builds a module (via {@link Song#makeModule}, freshly, into its own
	 * 64K scratch buffer) and encodes it as a single binary block - the
	 * dialog-independent work {@code ExportAsStrippedRMT()} delegates to
	 * once its target address/SFX-support parameters are confirmed.
	 * Returns {@code null} if {@link Song#makeModule} fails.
	 */
	public static byte[] exportAsStrippedRMTApply(Song song, int targetAddrOfModule, boolean sfxSupport, int tracks4_8) {
		byte[] mem = new byte[Atari.MEMORY_SIZE];
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int firstByteAfterModule = song.makeModule(mem, targetAddrOfModule, sfxSupport ? SongIOType.RMTSTRIPPED : SongIOType.RMT, instrumentSavedFlags, trackSavedFlags, tracks4_8);
		if (firstByteAfterModule < 0) {
			return null;
		}
		return AtariIO.saveBinaryBlock(mem, targetAddrOfModule, firstByteAfterModule - 1, true);
	}
}
