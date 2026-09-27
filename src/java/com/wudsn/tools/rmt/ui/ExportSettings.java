package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.rmt.model.AssemblerFormat;

/**
 * The import/export "remember what I chose last time" state C++ keeps in
 * globals ({@code g_rmtstripped_*}, {@code g_AsmFormat},
 * {@code g_PrefixForAllAsmLabels}, {@code g_AsmLabelForStartOfSong} and
 * friends, {@code g_rmtmsxtext}/{@code g_msxcheck}/{@code g_msx_shuffle}/
 * {@code g_region_auto}/{@code g_msxcol}, {@code g_importmodyesokok}/
 * {@code g_importtmcyesokok}, {@code FileImport()}'s static
 * {@code l_lastImportTypeIndex}) - one plain holder on the session, per this
 * port's "C++ global -> explicit parameter" idiom. Defaults are the C++
 * initializers; {@link #resetOnClearSong()} is the subset
 * {@code CSong::ClearSong()} resets (and {@code LoadRMT} then sets the
 * stripped address from the loaded module).
 */
public final class ExportSettings {

	// Stripped RMT / relocatable ASM (Global.cpp, ASMFileExporter.cpp)
	/** {@code g_rmtstripped_adr_module}: the stripped module's target address, 0x4000 by default and the loaded module's own address after a load. */
	public int rmtStrippedAddress = 0x4000;
	/** {@code g_rmtstripped_sfx}. */
	public boolean rmtStrippedSfx;
	/** {@code g_rmtstripped_gvf}. */
	public boolean rmtStrippedGlobalVolumeFade;
	/** {@code g_rmtstripped_nos}. */
	public boolean rmtStrippedNoStartingSongLine;
	/** {@code g_AsmFormat}, XASM by default. */
	public AssemblerFormat asmFormat = AssemblerFormat.XASM;

	// Simple ASM notation
	/** {@code g_PrefixForAllAsmLabels}, "MUSIC" after {@code ClearSong()}. */
	public String prefixForAllAsmLabels = "MUSIC";

	// Relocatable ASM for RmtPlayer.asm
	public String asmLabelForStartOfSong = "";
	public boolean asmWantRelocatableInstruments;
	public boolean asmWantRelocatableTracks;
	public boolean asmWantRelocatableSongLines;
	public String asmInstrumentsLabel = "";
	public String asmTracksLabel = "";
	public String asmSongLinesLabel = "";

	// XEX ("MSX") export
	/** {@code g_rmtmsxtext}: the on-screen text of the last XEX export, "" = build the default from the song. */
	public String msxText = "";
	/** {@code g_msxcheck}: display the rasterbar. */
	public boolean msxRasterbar = true;
	/** {@code g_msx_shuffle}. */
	public boolean msxShuffle = true;
	/** {@code g_region_auto}. */
	public boolean msxRegionAuto = true;
	/** {@code g_msxcol}: the rasterbar color, 2..254 even. */
	public int msxColor = 6;

	// Import
	/** {@code g_importmodyesokok}: the "Yes... OK, OK... I understand." box stays checked for the session. */
	public boolean importModUnderstood;
	/** {@code g_importtmcyesokok}. */
	public boolean importTmcUnderstood;
	/** {@code FileImport()}'s {@code l_lastImportTypeIndex}: the filter to preselect next time, 0 for none. */
	public int lastImportTypeIndex;

	/** What {@code CSong::ClearSong()} resets of these. */
	public void resetOnClearSong() {
		rmtStrippedAddress = 0x4000; // Default standard address for stripped RMT modules
		rmtStrippedSfx = false; // Is not a standard sfx variety stripped RMT
		rmtStrippedGlobalVolumeFade = false; // Default does not use Feat GlobalVolumeFade
		msxText = ""; // Clear the text for XEX export
		prefixForAllAsmLabels = "MUSIC"; // Default label prefix for exporting simple ASM notation
	}
}
