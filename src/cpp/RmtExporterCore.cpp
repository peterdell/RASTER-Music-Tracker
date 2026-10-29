#include "Instruments.h"
#include "RmtExporter.h"
#include "RmtModuleCodec.h"
#include "RmtModuleFile.h"

// CRmtExporter::ExportAsRMT() has no dialog/hazard of its own - only the
// module container and CSong::GetName()/CInstruments::GetName() (both
// already safe). Kept separate from RmtExporter.cpp's ExportAsStrippedRMT(), which
// instantiates a real MFC dialog - same pattern as Song.cpp/SongEditing.cpp
// etc.

extern CInstruments g_Instruments;

/// <summary>
/// Export the song data as an RMT module with full instrument and song names.
/// Writes two data blocks: the module MakeModule() built into exportDesc, and
/// the song name and the names of the instruments the module holds. Since
/// plans/60_RMT_MODULE_CONTAINER_PLAN.md M6 written by CRmtModuleFile.
/// </summary>
/// <param name="ou">Output stream</param>
/// <param name="exportDesc">Data about the packed RMT module</param>
/// <returns>true = saved ok</returns>
bool CRmtExporter::ExportAsRMT(CSong& song, std::ostream& ou, TExportDescription* exportDesc) {
    try {
        CRmtModule module = CRmtModule::Parse(exportDesc->mem, exportDesc->targetAddrOfModule, exportDesc->firstByteAfterModule);
        std::vector<std::string> instrumentNames;
        for (const auto& entry : module.GetInstruments()) {
            CString name = g_Instruments.GetName(entry.first);
            name.TrimRight();
            instrumentNames.push_back(std::string((LPCSTR)name));
        }
        CRmtModule::Bytes bytes = CRmtModuleFile(module, exportDesc->targetAddrOfModule, std::string((LPCSTR)song.GetName()), instrumentNames).Write();
        ou.write(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        return true;
    } catch (const CRmtModule::FormatException&) {
        return false;
    }
}

// Extracted from CRmtExporter::ExportAsStrippedRMT() (RmtExporter.cpp): the
// dialog-independent work, once the confirmed target address and
// SFX-support flag are known. Since plans/60_RMT_MODULE_CONTAINER_PLAN.md M6
// the stripped module of the song, written by CRmtModuleFile.
bool CRmtExporter::ExportAsStrippedRMTApply(CSong& song, std::ostream& ou, int targetAddrOfModule, BOOL sfxSupport) {
    // SFX support keeps all non-empty instruments (sound effects trigger
    // instruments directly, without any track referencing them); without it,
    // unused instruments are stripped. The two iotypes were swapped here
    // since 1.34, so an SFX-only module (instruments, no tracks) exported to
    // a 30-byte husk (issue #5).
    try {
        CRmtModule module = CRmtModuleCodec::FromSong(song, sfxSupport ? SongIOType::RMT : SongIOType::RMTSTRIPPED);
        CRmtModule::Bytes bytes = CRmtModuleFile::Stripped(module, targetAddrOfModule).Write();
        ou.write(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        return true; // Indicate that data was saved
    } catch (const CRmtModule::FormatException&) {
        return false; // if the module could not be created
    }
}
