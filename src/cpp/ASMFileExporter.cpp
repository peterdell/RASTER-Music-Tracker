#include "StdAfx.h"
#include "ASMFile.h"
#include "ASMFileExporter.h"
#include "ExportDlgs.h"



extern BOOL g_rmtstripped_sfx;			//sfx offshoot RMT stripped file
extern BOOL g_rmtstripped_gvf;			//gvs GlobalVolumeFade for feat
extern BOOL g_rmtstripped_nos;			//nos NoStartingSongline for feat

// g_PrefixForAllAsmLabels is defined in ASMFileExporterCore.cpp - that's
// the half linked into the test project, and ExportAsAsmApply() there
// needs it too.
extern CString g_PrefixForAllAsmLabels;	//label prefix for export ASM simple notation

CString g_AsmLabelForStartOfSong;	// Label for relocatable ASM for RMTPlayer.asm
BOOL g_AsmWantRelocatableInstruments = 0;
BOOL g_AsmWantRelocatableTracks = 0;
BOOL g_AsmWantRelocatableSongLines = 0;
CString g_AsmInstrumentsLabel;
CString g_AsmTracksLabel;
CString g_AsmSongLinesLabel;
AssemblerFormat g_AsmFormat = XASM;


// ============================================================================
// Code to export RMT song data as
// - simple notation assembler
// - fully reloctable assembler source code for RMTPlayer
// ============================================================================


bool CASMFileExporter::ExportAsAsm(const CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc)
{
    // Setup the ASM export dialog
    CExportAsmDlg dlg;
    dlg.m_prefixForAllAsmLabels = g_PrefixForAllAsmLabels;

    if (dlg.DoModal() != IDOK) return false;

    // Save for future ASM exports
    g_PrefixForAllAsmLabels = dlg.m_prefixForAllAsmLabels;

    return ExportAsAsmApply(song, ou, dlg.m_exportType, dlg.m_notesIndexOrFreq, dlg.m_durationsType);
}

// CASMFileExporter::ExportAsAsmApply() is implemented in ASMFileExporterCore.cpp.

bool CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer(CSong& song, std::ofstream& ou, TExportDescription* exportDescStripped)
{
    TExportDescription exportDescWithSFX;
    memset(&exportDescWithSFX, 0, sizeof(TExportDescription));
    exportDescWithSFX.targetAddrOfModule = 0x4000;		// Standard RMT modules are set to start @ $4000

    // Create a variant for SFX (ie. including unused instruments and tracks)
    exportDescWithSFX.firstByteAfterModule = song.MakeModule(exportDescWithSFX.mem, exportDescWithSFX.targetAddrOfModule, SongIOType::RMT, exportDescWithSFX.instrumentSavedFlags, exportDescWithSFX.trackSavedFlags);
    if (exportDescWithSFX.firstByteAfterModule < 0) return false;	// if the module could not be created

    CExportRelocatableAsmForRmtPlayer dlg;
    dlg.m_exportDescStripped = exportDescStripped;
    dlg.m_exportDescWithSFX = &exportDescWithSFX;

    dlg.m_strAsmLabelForStartOfSong = g_AsmLabelForStartOfSong;
    dlg.m_wantRelocatableInstruments = g_AsmWantRelocatableInstruments;
    dlg.m_wantRelocatableTracks = g_AsmWantRelocatableTracks;
    dlg.m_wantRelocatableSongLines = g_AsmWantRelocatableSongLines;
    dlg.m_strAsmInstrumentsLabel = g_AsmInstrumentsLabel;
    dlg.m_strAsmTracksLabel = g_AsmTracksLabel;
    dlg.m_strAsmSongLinesLabel = g_AsmSongLinesLabel;
    dlg.m_assemblerFormat = g_AsmFormat;

    dlg.m_sfxSupport = g_rmtstripped_sfx;
    dlg.m_globalVolumeFade = g_rmtstripped_gvf;
    dlg.m_noStartingSongLine = g_rmtstripped_nos;
    dlg.m_song = &song;
    dlg.m_filename = "";

    if (dlg.DoModal() != IDOK) return false;

    // Save the dialog settings for future exports
    g_AsmLabelForStartOfSong = dlg.m_strAsmLabelForStartOfSong;
    g_AsmWantRelocatableInstruments = dlg.m_wantRelocatableInstruments;
    g_AsmWantRelocatableTracks = dlg.m_wantRelocatableTracks;
    g_AsmWantRelocatableSongLines = dlg.m_wantRelocatableSongLines;
    g_AsmInstrumentsLabel = dlg.m_strAsmInstrumentsLabel;
    g_AsmTracksLabel = dlg.m_strAsmTracksLabel;
    g_AsmSongLinesLabel = dlg.m_strAsmSongLinesLabel;
    g_AsmFormat = dlg.m_assemblerFormat;

    g_rmtstripped_sfx = dlg.m_sfxSupport;
    g_rmtstripped_gvf = dlg.m_globalVolumeFade;
    g_rmtstripped_nos = dlg.m_noStartingSongLine;

    TRelocatableAsmExportParams params;
    params.strAsmLabelForStartOfSong = g_AsmLabelForStartOfSong;
    params.wantRelocatableInstruments = g_AsmWantRelocatableInstruments;
    params.wantRelocatableTracks = g_AsmWantRelocatableTracks;
    params.wantRelocatableSongLines = g_AsmWantRelocatableSongLines;
    params.strAsmInstrumentsLabel = g_AsmInstrumentsLabel;
    params.strAsmTracksLabel = g_AsmTracksLabel;
    params.strAsmSongLinesLabel = g_AsmSongLinesLabel;
    params.assemblerFormat = g_AsmFormat;
    params.sfxSupport = g_rmtstripped_sfx;
    params.globalVolumeFade = g_rmtstripped_gvf;
    params.noStartingSongLine = g_rmtstripped_nos;

    return ExportAsRelocatableAsmForRmtPlayerApply(song, ou, exportDescStripped, &exportDescWithSFX, params);
}

// CASMFileExporter::ExportAsRelocatableAsmForRmtPlayerApply() and
// BuildRelocatableAsm() are implemented in ASMFileExporterCore.cpp.

