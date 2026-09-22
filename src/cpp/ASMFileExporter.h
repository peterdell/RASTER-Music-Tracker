#pragma once

#include "Song.h"
#include "ASMFile.h"

// Input parameters for CASMFileExporter::ExportAsRelocatableAsmForRmtPlayerApply() -
// mirrors CExportRelocatableAsmForRmtPlayer's fields 1:1 (see ExportDlgs.h),
// letting the actual export work (a thin wrapper around the already-pure
// BuildRelocatableAsm()) run independently of the dialog that normally
// supplies these values.
struct TRelocatableAsmExportParams
{
    CString strAsmLabelForStartOfSong;
    BOOL wantRelocatableInstruments;
    BOOL wantRelocatableTracks;
    BOOL wantRelocatableSongLines;
    CString strAsmInstrumentsLabel;
    CString strAsmTracksLabel;
    CString strAsmSongLinesLabel;
    AssemblerFormat assemblerFormat;
    BOOL sfxSupport;
    BOOL globalVolumeFade;
    BOOL noStartingSongLine;
};

class CASMFileExporter
{

public:

    /// <summary>
    /// Export the RMT module as assembler
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <param name="exportStrippedDesc">Data about the packed RMT module</param>
    /// <returns>true if the save went OK</returns>
    static bool ExportAsAsm(const CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc);
    // Extracted from ExportAsAsm(): the dialog-independent work, once its 3
    // dialog-derived parameters are known (m_prefixForAllAsmLabels is read
    // directly from g_PrefixForAllAsmLabels instead - the wrapper already
    // writes the confirmed value back to that global before calling this).
    // Takes std::ostream& rather than std::ofstream& for the same reason as
    // CRmtExporter::ExportAsRMT() (see test/SongEditingTests.cpp).
    static bool ExportAsAsmApply(const CSong& song, std::ostream& ou, int exportType, int notesIndexOrFreq, int durationsType);

    /// <summary>
    /// Export the RMT data for the RMTPlayer assembler player.
    /// All data is exported: instruments, tracks, song lines
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <param name="exportDescStripped">Data about the packed RMT module</param>
    /// <returns>true if the save went OK</returns>
    static bool ExportAsRelocatableAsmForRmtPlayer(CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc);
    // Extracted from ExportAsRelocatableAsmForRmtPlayer(): the dialog-
    // independent work, once its confirmed parameters are known. Mostly a
    // thin wrapper around the already dialog-independent BuildRelocatableAsm()
    // below, plus the exportDescWithSFX/exportDescStripped selection and the
    // final stream write that live outside of it.
    static bool ExportAsRelocatableAsmForRmtPlayerApply(CSong& song, std::ostream& ou, TExportDescription* exportDescStripped, TExportDescription* exportDescWithSFX, const TRelocatableAsmExportParams& params);


    // TODO: Used by export dialog
    void static ComposeRMTFEATstring(const CSong& song, CString& dest, const char* filename, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags, BOOL sfx, BOOL gvf, BOOL nos, AssemblerFormat assemblerFormat);

    static BOOL BuildRelocatableAsm(
        const CSong& song, CString& dest,
        TExportDescription* exportDesc,
        CString strAsmStartLabel,
        CString strTracksLabel,
        CString strSongLinesLabel,
        CString strInstrumentsLabel,
        AssemblerFormat assemblerFormat,
        BOOL sfx,
        BOOL gvf,
        BOOL nos,
        bool bWantSizeInfoOnly);
private:

};

