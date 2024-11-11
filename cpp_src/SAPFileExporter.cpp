#include "stdafx.h"
#include "SAPFileExporter.h"
#include "Memory.h"
#include "lzss_sap.h"
#include "LZSSFile.h"
#include "VUPlayer.h"
#include "AtariIO.h"
#include "GuiHelpers.h"

extern CString g_prgpath;

bool CSAPFileExporter::ExportSAP_B_LZSS( CSongExport& songExport, CSAPFile& sapFile, std::ofstream& ou) {

    byte memory[RAM_SIZE]{};
    MemoryAddress addressFrom;
    MemoryAddress addressTo;

    CString binaryFilePath = g_prgpath + "RMT Binaries/VUPlayer (LZSS Export).obx";
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
    int targetAddrOfModule = VU_PLAYER_SONGDATA;											// All the LZSS data will be written starting from this address
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

    sapFile.SetInitAddress(VU_PLAYER_INIT_SAP);
    sapFile.SetPlayerAddress(VU_PLAYER_DO_PLAY_ADDR);

    sapFile.Export(ou);

    // Patch: change a JMP [label] to a RTS with 2 NOPs
    byte saprtsnop[3] = { 0x60,0xEA,0xEA };
    for (int i = 0; i < 3; i++) { memory[VU_PLAYER_RTS_NOP + i] = saprtsnop[i]; }

    // Patch: change a $00 to $FF to force the LOOP flag to be infinite
    memory[VU_PLAYER_LOOP_FLAG] = 0xFF;

    // SAP initialisation patch, running from address 0x3080 in Atari executable 
    byte sapbytes[14] =
    {
        0x8D,LZSSP_SONGIDX & 0xff,LZSSP_SONGIDX >> 8,								// STA SongIdx
        0xA2,0x00,																	// LDX #0
        0x8E,LZSSP_IS_FADEING_OUT & 0xff, LZSSP_IS_FADEING_OUT >> 8,				// STX is_fadeing_out
        0x8E,LZSSP_STOP_ON_FADE_END & 0xff, LZSSP_STOP_ON_FADE_END >> 8,			// STX stop_on_fade_end
        0x4C,LZSSP_SETNEWSONGPTRSFULL & 0xff, LZSSP_SETNEWSONGPTRSFULL >> 8	// JMP SetNewSongPtrsLoopsOnly
    };
    memcpy(memory + VU_PLAYER_INIT_SAP, sapbytes, 14);

    memory[VU_PLAYER_SONG_SPEED] = songExport.GetSong().GetInstrumentSpeed();			// Song speed
    memory[VU_PLAYER_STEREO_FLAG] = songExport.GetSong().IsStereo() ? 0xFF : 0x00;		// Is the song stereo?

    // Reconstruct the export binary 
    CAtariIO::SaveBinaryBlock(ou, memory, 0x1900, 0x1EFF, 1);	// LZSS Driver, and some free bytes for later if needed
    CAtariIO::SaveBinaryBlock(ou, memory, 0x2000, 0x27FF, 0);	// VUPlayer only

    // SongStart pointers
    memory[LZSS_POINTER] = targetAddrOfModule >> 8;			// SongsSHIPtrs
    memory[LZSS_POINTER] = lzss_offset >> 8;				// SongsIndexEnd
    memory[LZSS_POINTER] = targetAddrOfModule & 0xFF;		// SongsSLOPtrs
    memory[LZSS_POINTER] = lzss_offset & 0xFF;				// SongsDummyEnd

    // SongEnd pointers
    memory[LZSS_POINTER] = lzss_offset >> 8;				// LoopsIndexStart
    memory[LZSS_POINTER] = lzss_end >> 8;					// LoopsIndexEnd
    memory[LZSS_POINTER] = lzss_offset & 0xFF;				// LoopsSLOPtrs
    memory[LZSS_POINTER] = lzss_end & 0xFF;					// LoopsDummyEnd

    if (intro > 16)
    {
        memcpy(memory + targetAddrOfModule, buff2, intro);
        memcpy(memory + lzss_offset, buff3, loop);
    }
    else
    {
        //memcpy(mem + targetAddrOfModule, buff1, full);
        memcpy(memory + lzss_offset, buff3, loop);
    }

    // Overwrite the LZSS data region with both the pointers for subtunes index, and the actual LZSS streams until the end of file
    CAtariIO::SaveBinaryBlock(ou, memory, LZSS_POINTER, lzss_end, 0);

    return true;
}

bool CSAPFileExporter::ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ofstream& ou) {

    sapFile.Export(ou);

    // Write the SAP-R stream to the output file defined in the path dialog with the data specified above
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();
    pokeyStream.WriteToFile(ou, pokeyStream.GetFirstCountPoint(), 0);

    return true;
}