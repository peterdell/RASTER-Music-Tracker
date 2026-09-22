#include "StdAfx.h"

#include "AtariIO.h"
#include "GuiHelpers.h"
#include "lzss_sap.h"
#include "LZSSFile.h"
#include "Memory.h"
#include "Messages.h"
#include "SAPFileExporter.h"
#include "VUPlayer.h"
#include <filesystem>

// CSAPFileExporter's methods have no dialog of their own - both are called
// from CSongExporter's thin dialog wrappers (SongExporter.cpp) after the
// real dialog (CSAPFileExportDialog::Show()) has already populated the
// CSAPFile they're given. ExportSAP_B_LZSS() does need a real on-disk
// resource file (resources/players/vu_player_v2.obx, loaded via
// GetResourceFilePath()) - the first such dependency in this test suite;
// see plans/SAP_LZSS_WAV_XEX_PLAN.md and test/AtariBinariesStub.cpp for
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
    if (!CAtariIO::LoadBinaryFile(binaryFilePath, memory, addressFrom, addressTo))
    {
        CString message;
        message.Format("Fatal error with RMT LZSS system routines.\nCouldn't load '%s'.", binaryFilePath);
        SendErrorMessage("Export aborted", message);
        return false;
    }

    SetStatusBarText("Compressing data ...");

    const int frameSize = CLZSSFile::GetFrameSize(songExport.GetSong());

    byte buff1[RAM_SIZE];	// LZSS buffers for each ones of the tune parts being reconstructed
    byte buff2[RAM_SIZE];	// they are used for parts labeled: full, intro, and loop
    byte buff3[RAM_SIZE];	// a LZSS export will typically make use of intro and loop only, unless specified otherwise

    CCompressLzss lzssData;

    // Now, create LZSS files using the SAP-R dump created earlier
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();
    int full = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer(), pokeyStream.GetFirstCountPoint() * frameSize, buff1);
    int intro = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer(), pokeyStream.GetThirdCountPoint() * frameSize, buff2);
    int loop = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer() + (pokeyStream.GetFirstCountPoint() * frameSize), pokeyStream.GetSecondCountPoint() * frameSize, buff3);

    // Some additional variables that will be used below
    int targetAddrOfModule = VUPlayer::SONGDATA;											// All the LZSS data will be written starting from this address
    //int lzss_offset = (intro) ? targetAddrOfModule + intro : targetAddrOfModule + full;	// Calculate the offset for the export process between the subtune parts, at the moment only 1 tune at the time can be exported
    int lzss_offset = (intro > 16) ? targetAddrOfModule + intro : targetAddrOfModule;
    int lzss_end = lzss_offset + loop;													// this sets the address that defines where the data stream has reached its end

    ClearStatusBar();

    // If the size is too big, abort the process and show an error message
    // JAC! Have same error handling
    if (lzss_end > RAM_MAX_ADDRESS)
    {
        SendErrorMessage("Buffer Overflow",
            "Error, LZSS data is too big to fit in memory!\n\n"
            "High Instrument Speed and/or Stereo greatly inflate memory usage, even when data is compressed");
        return false;
    }

    sapFile.SetInitAddress(VUPlayer::INIT_SAP);
    sapFile.SetPlayerAddress(VUPlayer::DO_PLAY_ADDR);

    sapFile.Export(ou);

    VUPlayer::PatchMemoryForSAP_B(memory, songExport.GetSong(), buff2, buff3, intro, loop, targetAddrOfModule, lzss_offset, lzss_end);


    // Reconstruct the export binary
    CAtariIO::SaveBinaryBlock(ou, memory, 0x1900, 0x1EFF, true);	// LZSS Driver, and some free bytes for later if needed
    CAtariIO::SaveBinaryBlock(ou, memory, 0x2000, 0x27FF, false);	// VUPlayer only

    // Overwrite the LZSS data region with both the pointers for subtunes index, and the actual LZSS streams until the end of file
    CAtariIO::SaveBinaryBlock(ou, memory, VUPlayer::LZSS_POINTER, lzss_end, false);

    return true;
}
