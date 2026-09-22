#include "StdAfx.h"

#include "ASMFileExporter.h"
#include "Atari.h"
#include "PokeyRenderer.h"
#include "RmtExporter.h"
#include "Song.h"
#include "SongExporter.h"

// CSong::ExportV2() is a safe dispatcher, even though it unconditionally
// constructs CSongContainer/CSongExporter/CSongExport regardless of
// iotype: CSongContainer's ctor just stores a pointer and validates the
// song is stopped (a real but simple guard, not a dialog/hazard), and
// CSongExport's ctor just stores pointers - neither touches the real Atari
// rendering pipeline, which only runs lazily inside
// CSongContainer::GetPokeyStream() (confirmed while triaging ExportV2, see
// plans/EXPORTV2_PLAN.md).
//
// Kept as its own file (rather than staying in IO_Song.cpp) because the
// rest of that file is the FileXxx family (Batch 7, real CFileDialog/
// confirm-prompt orchestration - recommended deferred indefinitely) -
// same "split along the coupling seam" pattern as elsewhere.
//
// NOT linked into the test project, and NOT widened to std::ostream&,
// unlike ExportAsRMT()/ExportAsStrippedRMTApply()/etc.: this function's own
// switch statement references every iotype branch syntactically (even ones
// a given test would never take), so the linker needs ALL of them to
// resolve regardless of which case actually runs. That means even testing
// just the RMT case here would require linking or stubbing
// CSongContainer/CSongExport/CSongExporter's constructors, all 5 of
// CSongExporter's Tier-2 export methods (plans/EXPORTV2_PLAN.md - real
// Atari audio-rendering pipeline, deferred), the two real-dialog wrappers
// CRmtExporter::ExportAsStrippedRMT()/CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer()
// (not their already-tested *Apply() siblings), and a real g_Pokey global -
// confirmed by actually attempting the link, not assumed. That's a lot of
// new surface just to characterize a thin, already-covered dispatch layer:
// every branch ExportV2() reaches (RMT/RMTSTRIPPED/ASM/ASM_RMTPLAYER) is
// already directly tested via ExportAsRMT()/ExportAsStrippedRMTApply()/
// ExportAsAsmApply()/ExportAsRelocatableAsmForRmtPlayerApply() (Batches
// A-C), so this file stays production-only - moved out of IO_Song.cpp for
// clarity, not for testability.

extern CXPokey g_Pokey;
extern CAtari g_Atari;

/// <summary>
/// Export dispatcher.
/// First build a module to make sure the data is consistent.
/// Then dispatch to the appropriate export handler
/// </summary>
/// <param name="ou">output fream</param>
/// <param name="iotype">requested output format</param>
/// <param name="filename">filename of the output</param>
/// <returns>0 if the export failed, 1 if the export is ok</returns>
bool CSong::ExportV2(CSong& song, std::ofstream& ou, SongIOType iotype, LPCTSTR filename)
{
    // Init the export data container
    TExportDescription exportDesc{};
    exportDesc.targetAddrOfModule = 0x4000;		// Standard RMT modules are set to start @ $4000

    // Create a module, if it fails stop the export
    int maxAddr = song.MakeModule(exportDesc.mem, exportDesc.targetAddrOfModule, iotype, exportDesc.instrumentSavedFlags, exportDesc.trackSavedFlags);
    if (maxAddr < 0)
    {
        return false;								// If the module could not be created, the export process is immediately aborted
    }
    exportDesc.firstByteAfterModule = maxAddr;

    CSongContainer songContainer(song);
    CSongExporter songExporter;
    CSongExport songExport(songContainer, filename);
    switch (iotype)
    {
    case SongIOType::RMT: return CRmtExporter::ExportAsRMT(song, ou, &exportDesc);
    case SongIOType::RMTSTRIPPED: return CRmtExporter::ExportAsStrippedRMT(song, ou, &exportDesc, filename);
    case SongIOType::ASM: return CASMFileExporter::ExportAsAsm(song, ou, &exportDesc);
    case SongIOType::ASM_RMTPLAYER: return CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer(song, ou, &exportDesc);
    case SongIOType::SAPR: return songExporter.ExportSAP_R(songExport, ou);
    case SongIOType::LZSS: return songExporter.ExportLZSS(songExport, ou);
    case SongIOType::LZSS_SAP: return songExporter.ExportSAP_B_LZSS(songExport, ou);
    case SongIOType::LZSS_XEX: return songExporter.ExportXEX_LZSS(songExport, ou);
    case SongIOType::WAV: return songExporter.ExportWAV(songExport, ou, g_Pokey, g_Atari.GetMemoryAt(0));
    }

    return false;	// Failed
}
