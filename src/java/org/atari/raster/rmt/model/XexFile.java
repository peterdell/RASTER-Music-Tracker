package org.atari.raster.rmt.model;

/**
 * Ported from CXEXFile (src/cpp/SongExporter.h) - the fields
 * {@code SongExporter#exportXexLzss} actually reads. {@code songname}/
 * {@code currentTime} are dropped: C++'s own {@code InitFromSong} sets
 * them, but {@code ExportXEX_LZSS}'s body never reads either back -
 * dead fields for this export path (likely used only by the real export
 * dialog's own display, not ported here).
 *
 * <p>A plain mutable "settings struct", matching {@link Track}/
 * {@link TuningSettings}'s own established treatment - callers (and
 * {@link #fromSong}) set its fields directly.
 */
public final class XexFile {

	public static final int ATARI_TEXT_SIZE = 5 * 40;

	public int instrumentSpeed;
	public boolean stereo;
	public boolean ntsc;
	public boolean autoRegion;
	public boolean displayRasterbar;
	public int rasterbarColor;
	public final byte[] atariText = new byte[ATARI_TEXT_SIZE];

	/**
	 * The tail of {@code CSongExporter::ShowXEXExportDialog()}: the dialog's
	 * text (up to 5 lines, newline-separated, 40 characters each) laid out
	 * into the 5x40 {@link #atariText} screen block, space-padded, and
	 * converted to Atari screen codes.
	 */
	public void setDisplayedText(String txt) {
		java.util.Arrays.fill(atariText, (byte) ' ');
		int p = 0;
		int q = 0;
		for (int i = 0; i < txt.length(); i++) {
			char a = txt.charAt(i);
			if (a == '\n') {
				p += 40;
				q = 0;
			} else if (a != '\r') { // C++ works on CR/LF text (after collapsing CR CR); a lone CR would otherwise land on screen
				atariText[p + q] = (byte) a;
				q++;
			}
			if (p + q >= ATARI_TEXT_SIZE) {
				break;
			}
		}
		SongExporter.strToAtariVideo(atariText, 0, ATARI_TEXT_SIZE);
	}

	/** Mirrors {@code CXEXFile::InitFromSong} (minus the dropped dead fields - see class javadoc). */
	public static XexFile fromSong(Song song, int tracks4_8) {
		XexFile xexFile = new XexFile();
		xexFile.instrumentSpeed = song.getInstrumentSpeed();
		xexFile.stereo = song.isStereo(tracks4_8);
		xexFile.ntsc = song.isNTSC();
		return xexFile;
	}
}
