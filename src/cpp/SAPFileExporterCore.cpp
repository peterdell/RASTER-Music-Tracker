#include <vector>
#include "StdAfx.h"

#include "AtariIO.h"
#include "GuiHelpers.h"
#include "lzss_sap.h"
#include "LZSSFile.h"
#include "Memory.h"
#include "Messages.h"
#include "SAPFileExporter.h"
#include "SongExporter.h"
#include "VUPlayer.h"
#include <filesystem>

// CSAPFileExporter's methods have no dialog of their own - both are called
// from CSongExporter's thin dialog wrappers (SongExporter.cpp) after the
// real dialog (CSAPFileExportDialog::Show()) has already populated the
// CSAPFile they're given. ExportSAP_B_LZSS() does need a real on-disk
// resource file (resources/players/vu_player_v2.obx, loaded via
// GetResourceFilePath()) - the first such dependency in this test suite;
// see plans/05_SAP_LZSS_WAV_XEX_PLAN.md and test/AtariBinariesStub.cpp for
// how that's satisfied for tests.
//
// GetResourceFilePath() itself lives in the much wider Global.cpp/Global.h
// (not linked/included here) - declared directly instead, same "declare
// individual externs, avoid Global.h" pattern as elsewhere.
extern CString GetResourceFilePath(const std::filesystem::path& relativeFolderPath, const CString& fileName);

bool CSAPFileExporter::ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou) {
    sapFile.SetType("R");
    sapFile.Export(ou);

    // Write the SAP-R stream to the output file defined in the path dialog with the data specified above
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();
    pokeyStream.WriteToFile(ou, pokeyStream.GetFirstCountPoint(), 0);

    return true;
}

bool CSAPFileExporter::ExportSAP_B_LZSS(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou) {

    byte memory[RAM_SIZE]{};
    MemoryAddress addressFrom;
    MemoryAddress addressTo;

    auto binaryFilePath = GetResourceFilePath(std::filesystem::path("resources/players"), "vu_player_v2.obx");
    if (!CAtariIO::LoadBinaryFile(binaryFilePath, memory, addressFrom, addressTo)) {
        CString message;
        message.Format("Fatal error with RMT LZSS system routines.\nCouldn't load '%s'.", binaryFilePath);
        SendErrorMessage("Export aborted", message);
        return false;
    }

    // The subtunes: the songlines the SAP file's subsongs start from, else the song from its start
    std::vector<int> subtunes = sapFile.GetSubsongPositions();
    if (subtunes.empty()) {
        subtunes.push_back(0);
    }
    const int subsongs = (int)subtunes.size();

    // Dump, compress and lay out the subtunes exactly as the XEX export does
    // for the same player: the song index, the section and sequence lists and
    // the intro and loop streams from VUPlayer::SONGDATA on. The export used
    // to write the memory blocks of the old VU-Player ($1900-$27FF) while
    // vu_player_v2.obx lives at $0C1B-$1F3F: INIT jumped into bytes the file
    // did not hold, and no player could run any SAP this program exported
    // (found through the RITMO fork's b243f10, ported here;
    // plans/32_RITMO_FORK_ANALYSIS_PLAN.md).
    SetStatusBarText("Compressing data ...");
    int lzss_total = 0;
    int framescount = 0;
    if (!CSongExporter::BuildLzssSubtunes(songExport, subtunes.data(), subsongs, memory, lzss_total, framescount)) {
        ClearStatusBar();
        return false;
    }
    ClearStatusBar();

    sapFile.SetSongs(subsongs);
    sapFile.SetInitAddress(VUPlayer::INIT_SAP);
    sapFile.SetPlayerAddress(VUPlayer::DO_PLAY_ADDR);
    VUPlayer::PatchMemoryForSAP_B(memory, songExport.GetSong(), subsongs);

    sapFile.Export(ou);

    // The binary: the LZSS driver and the player, then the song index, lists and streams until the end of the data
    CAtariIO::SaveBinaryBlock(ou, memory, LZSSP_PLAYLZ16BEGIN, VUPlayer::LZSS_POINTER - 1, true);
    CAtariIO::SaveBinaryBlock(ou, memory, VUPlayer::LZSS_POINTER, lzss_total - 1, false);

    return true;
}
