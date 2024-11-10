#include "stdafx.h"
#include "SongExporter.h"
#include <iomanip>

#include "Atari6502.h"
#include "AtariIO.h"

#include "ChannelControl.h" // TODO: Is still global
#include "GuiHelpers.h"

#include "ExportDlgs.h"
#include "SAPFileExportDialog.h"

#include "lzss_sap.h"
#include "LZSSFile.h"

#include "SAPFile.h"
#include "SAPFileExporter.h"

#include "VUPlayer.h"

#include "WaveFile.h"

// TODO: Find a better place and name
CString g_rmtmsxtext;

CSongExporter::CSongExporter() {

}

void CSongExporter::StrToAtariVideo(char* txt, int count)
{
    char a;
    for (int i = 0; i < count; i++)
    {
        a = txt[i] & 0x7f;
        if (a < 32) { a = 0; }
        else {
            if (a < 96) { a -= 32; }
        }
        txt[i] = a;
    }
}

void CSongExporter::CreateExportMetadata(const CSong& song, TExportMetadata& metadata) {
    memcpy(metadata.songname, song.GetName(), SONG_NAME_MAX_LEN);
    metadata.songname[SONG_NAME_MAX_LEN] = 0;
    metadata.currentTime = CTime::GetCurrentTime();
    metadata.isNTSC = song.IsNTSC();
    metadata.isStereo = song.IsStereo();
    metadata.instrspeed = song.GetInstrumentSpeed();
}

int CSongExporter::BruteforceOptimalLZSS(unsigned char* src, int srclen, unsigned char* dst)
{
    CString message;
    CCompressLzss lzssData;

    // Start from a high value to force the first pattern to be the best one
    int bestScore = 0xFFFFFF;
    int optimal = 0;
    int result;
    {
        DisableEventSection section;

        for (int i = 0; i < SAPR_OPTIMISATIONS_COUNT; i++)
        {
            int bruteforced = lzssData.LZSS_SAP(src, srclen, dst, i);

            if (bruteforced < bestScore)
            {
                bestScore = bruteforced;
                optimal = i;
            }

            // Always refresh the screen after each iteration
            RefreshScreen();

            message.Format("Compressing %i bytes, bruteforcing optimisation pattern %i... Current best: %i bytes with optimisation pattern %i", srclen, i, bestScore, optimal);
            SetStatusBarText(message);
        }

        result = lzssData.LZSS_SAP(src, srclen, dst, optimal);

        // Bruteforcing was completed, display some stats
        message.Format("Done... %i bytes were shrunk to %i bytes using the optimisation pattern %i", srclen, bestScore, optimal);
        SetStatusBarText(message);
    }

    return result;
}

bool CSongExporter::ExportLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename)
{
    SetStatusBarText("Generating data ...");

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    SetStatusBarText("Compressing data ...");

    const int frameSize = CLZSSFile::GetFrameSize(song);


    // Now, create LZSS files using the SAP-R dump created earlier
    byte compressedData[RAM_SIZE]{};

    CCompressLzss lzssData;

    // TODO: add a Dialog box for proper standalone LZSS exports
    // This is a hacked up method that was added only out of necessity for a project making use of song sections separately
    // I refuse to touch RMT2LZSS ever again
    CString fn = filename;
    fn = fn.Left(fn.GetLength() - 5);	// In order to keep the filename without the extention 

    // Full tune playback up to its loop point
    int full = lzssData.LZSS_SAP(pokeyStream.GetStreamBuffer(), pokeyStream.GetFirstCountPoint() * frameSize, compressedData);
    if (full > 16)
    {
        //ou.open(fn + "_FULL.lzss", ios::binary);	// Create a new file for the Full section
        ou.write((char*)compressedData, full);	// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    // Intro section playback, up to the start of the detected loop point
    int intro = lzssData.LZSS_SAP(pokeyStream.GetStreamBuffer(), pokeyStream.GetThirdCountPoint() * frameSize, compressedData);
    if (intro > 16)
    {
        ou.open(fn + "_INTRO.lzss", std::ios::binary);	// Create a new file for the Intro section
        ou.write((char*)compressedData, intro);		// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    // Looped section playback, this part is virtually seamless to itself
    int loop = lzssData.LZSS_SAP(pokeyStream.GetStreamBuffer() + (pokeyStream.GetFirstCountPoint() * frameSize), pokeyStream.GetSecondCountPoint() * frameSize, compressedData);
    if (loop > 16)
    {
        ou.open(fn + "_LOOP.lzss", std::ios::binary);	// Create a new file for the Loop section
        ou.write((char*)compressedData, loop);		// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    pokeyStream.FinishedRecording();	// Clear the SAP-R dumper memory and reset RMT routines

    ClearStatusBar();

    return true;
}

bool CSongExporter::ExportCompactLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename)
{
    // TODO: everything related to exporting the stream buffer into small files and compress them to LZSS

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    SetStatusBarText("Compressing data ...");

    const int frameSize = CLZSSFile::GetFrameSize(song);

    int indexToSongline = 0;
    int songlineCount = pokeyStream.GetSonglineCount();

    // Since 0 is also a valid offset, the initial values are set to -1 to prevent conflicts
    int listOfMatches[256];
    memset(listOfMatches, -1, sizeof(listOfMatches));

    // Now, create LZSS files using the SAP-R dump created earlier
    unsigned char compressedData[RAM_SIZE];

    CCompressLzss lzssData;

    // TODO: add a Dialog box for proper standalone LZSS exports
    CString fn = filename;
    fn = fn.Left(fn.GetLength() - 5);	// In order to keep the filename without the extention 
    ou.close();

    // For all songlines to index, process with comparisons and find duplicates 
    while (indexToSongline < songlineCount)
    {
        int bytesCount = pokeyStream.GetFramesPerSongline(indexToSongline) * frameSize;

        int index1 = pokeyStream.GetOffsetPerSongline(indexToSongline);
        unsigned char* buff1 = pokeyStream.GetStreamBuffer() + (index1 * frameSize);

        // If there is no index already, assume the Index1 to be the first occurence 
        if (listOfMatches[indexToSongline] == -1)
            listOfMatches[indexToSongline] = index1;

        // Compare all indexes available and overwrite matching songline streams with index1's offset
        for (int i = 0; i < songlineCount; i++)
        {
            // If the bytes count between 2 songlines isn't matching, don't even bother trying
            if (bytesCount != pokeyStream.GetFramesPerSongline(i) * frameSize)
                continue;

            int index2 = pokeyStream.GetOffsetPerSongline(i);
            unsigned char* buff2 = pokeyStream.GetStreamBuffer() + (index2 * frameSize);

            // If there is a match, the second index will adopt the offset of the first index
            if (!memcmp(buff1, buff2, bytesCount))
                listOfMatches[i] = index1;
        }

        // Process to the next songline index until they are all processed
        indexToSongline++;
    }
    indexToSongline = 0;

    // From here, data blocs based on the Songline index and offset will be written to file
    // This should strip away every duplicated chunks, but save just enough data for reconstructing everything 
    while (indexToSongline < songlineCount)
    {
        // Get the current index to Songline offset from the current position
        int index = listOfMatches[indexToSongline];

        // Find if this offset was already processed from a previous Songline
        for (int i = 0; i < songlineCount; i++)
        {
            // As soon as a match is found, increment the counter for how many times the index was referenced
            if (index == listOfMatches[i] && indexToSongline > i)
            {
                // I don't know anymore, at this point...
            }
        }

        // Process to the next songline index until they are all processed
        indexToSongline++;
    }

    // Create a new file for logging everything related to the procedure
    ou.open(fn + ".txt", std::ios::binary);
    ou << "This is a test that displays all duplicated SAP-R bytes from m_StreamBuffer." << std::endl;
    ou << "Each ones of the Buffer Chunks are indexed into memory using Songlines.\n" << std::endl;

    for (int i = 0; i < songlineCount; i++)
    {
        ou << "Index: " << PADHEX(2, i);
        ou << ",\t Offset (real): " << PADHEX(4, pokeyStream.GetOffsetPerSongline(i));
        ou << ",\t Offset (dupe): " << PADHEX(4, listOfMatches[i]);
        ou << ",\t Bytes (uncompressed): " << PADDEC(1, pokeyStream.GetFramesPerSongline(i) * frameSize);
        ou << ",\t Bytes (LZ16 compressed): " << PADDEC(1, lzssData.LZSS_SAP(pokeyStream.GetStreamBuffer() + (pokeyStream.GetOffsetPerSongline(i) * frameSize), pokeyStream.GetFramesPerSongline(i) * frameSize, compressedData));
        ou << std::endl;
    }

    ou.close();

    pokeyStream.FinishedRecording();	// Clear the SAP-R dumper memory and reset RMT routines

    ClearStatusBar();

    return true;
}

bool CSongExporter::ExportSAP_R(CSong& song, std::ofstream& ou)
{

    CSAPFile sapFile;
    sapFile.m_type = "R";
    if (!CSAPFileExportDialog::Show(song, sapFile)) {
        return false;
    }

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    bool result = CSAPFileExporter::ExportSAP_R(song, pokeyStream, sapFile, ou);

    // Clear the memory and reset the dumper to its initial setup for the next time it will be called
    pokeyStream.FinishedRecording();

    return result;
}


bool CSongExporter::ExportSAP_B_LZSS(CSong& song, std::ofstream& ou)
{
    CSAPFile sapFile;
    sapFile.m_type = "B";
    if (!CSAPFileExportDialog::Show(song, sapFile)) {
        return false;
    }

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    bool result = CSAPFileExporter::ExportSAP_B_LZSS(song, pokeyStream, sapFile, ou);

    pokeyStream.FinishedRecording();	// Clear the SAP-R dumper memory and reset RMT routines
    return result;
}


bool CSongExporter::ExportWAV(CSong& song, std::ofstream& ou, LPCTSTR filename, CXPokey& pokey, byte* memory)
{
    CWaveFile wavefile{};

    BYTE* buffer = NULL;
    BYTE* streambuffer = NULL;
    const WAVEFORMATEX* wfm = NULL;
    int length = 0, frames = 0, offset = 0;
    const int frameSize = CLZSSFile::GetFrameSize(song);

    ou.close();	// hack, just to be able to actually use the filename for now...

    if (!(wfm = pokey.GetSoundFormat()))
    {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    if (!wavefile.OpenFile((LPTSTR)filename, wfm->nSamplesPerSec, wfm->wBitsPerSample, wfm->nChannels))
    {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    // Dump the POKEY registers from full song playback
    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    // Busy writing! TODO: Fix the timing overlap causing conflicts
    pokeyStream.SetState(CPokeyStream::WRITE);

    CAtari::InitRMTRoutine();	// Reset the Atari memory 
    SetChannelOnOff(-1, 1);	// Unmute all channels

    // Create the sound buffer to copy from and to
    buffer = new BYTE[BUFFER_SIZE];
    memset(buffer, 0x80, BUFFER_SIZE);

    while (frames < pokeyStream.GetFirstCountPoint())
    {
        // Copy the SAP-R bytes to g_atarimem for this frame
        streambuffer = pokeyStream.GetStreamBuffer() + frames * frameSize;

        //for (int i = 0; i < frameSize; i++)
        //{
        //	g_atarimem[0xd200 + i] = streambuffer[i];
        //}

        memory[RMTPLAYR_TRACKN_AUDF + 0] = streambuffer[0x00];
        memory[RMTPLAYR_TRACKN_AUDF + 1] = streambuffer[0x02];
        memory[RMTPLAYR_TRACKN_AUDF + 2] = streambuffer[0x04];
        memory[RMTPLAYR_TRACKN_AUDF + 3] = streambuffer[0x06];
        memory[RMTPLAYR_TRACKN_AUDC + 0] = streambuffer[0x01];
        memory[RMTPLAYR_TRACKN_AUDC + 1] = streambuffer[0x03];
        memory[RMTPLAYR_TRACKN_AUDC + 2] = streambuffer[0x05];
        memory[RMTPLAYR_TRACKN_AUDC + 3] = streambuffer[0x07];
        memory[RMTPLAYR_V_AUDCTL] = streambuffer[0x08];

        // Fill the POKEY buffer with 1 rendered chunk
        pokey.RenderSoundV2(song.GetInstrumentSpeed(), buffer, length);

        // Write the buffer to WAV file
        wavefile.WriteWave(buffer, length);

        // Update the PokeyStream offset for the next frame
        frames++;
    }

    // Clear the SAP-R dumper memory and reset RMT routines
    pokeyStream.FinishedRecording();

    // Finished doing WAV things...
    wavefile.CloseFile();

    // Also make sure to delete the buffer once it's no longer needed
    delete buffer;

    return true;
}

bool CSongExporter::ExportXEX_LZSS(CSong& song, std::ofstream& ou)
{

    CString s, t;

    MemoryAddress addressFrom, addressTo;

    int subsongs = song.GetSubsongParts(t);
    int count = 0;

    int subtune[256]{};

    int lzss_chunk = 0;	// Subtune size will be added to be used as the offset to the next one
    int lzss_total = 0;	// Final offset for LZSS bytes to export
    int framescount = 0;

    const int frameSize = CLZSSFile::GetFrameSize(song);
    int section = VU_PLAYER_SECTION;
    int sequence = VU_PLAYER_SEQUENCE;

    byte mem[RAM_SIZE]{};					// Default RAM size for most 800xl/xe machines

    // GetSubsongParts returns a CString, so the values must be converted back to int first, FIXME
    for (int i = 0; i < subsongs; i++)
    {
        char c[3]{ t[i * 3], t[i * 3 + 1], '\0' };
        subtune[i] = strtoul(c, NULL, 16);
    }

    // Load VUPlayerLZSS to memory
    CAtari::LoadOBX(IOTYPE_LZSS_XEX, mem, addressFrom, addressTo);

    // Create the export metadata for songname, Atari text, parameters, etc
    TExportMetadata metadata;
    CreateExportMetadata(song, metadata);

    if (!ExportXEX_LZSS(metadata))
    {
        return false;
    }

    // LZSS buffers for each ones of the tune parts being reconstructed.
    // Because the buffers are large, they are allocated on hte heap instead of the stack.
    const size_t LZSS_BUFFER_SIZE = 0xFFFFF;
    byte* buff2 = new byte[LZSS_BUFFER_SIZE]{};
    byte* buff3 = new byte[LZSS_BUFFER_SIZE]{};

    while (count < subsongs)
    {
        // a LZSS export will typically make use of intro and loop only, unless specified otherwise
        int intro = 0, loop = 0;

        // LZSS buffers for each ones of the tune parts being reconstructed
        CPokeyStream pokeyStream;
        song.DumpSongToPokeyStream(pokeyStream, MPLAY_FROM, subtune[count], 0);

        //SetStatusBarText("Compressing data ...");

        // There is an Intro section 
        if (pokeyStream.GetThirdCountPoint())
        {
            intro = BruteforceOptimalLZSS(pokeyStream.GetStreamBuffer(), pokeyStream.GetThirdCountPoint() * frameSize, buff2);
        }

        // There is a Loop section
        if (pokeyStream.GetFirstCountPoint())
        {
            loop = BruteforceOptimalLZSS(pokeyStream.GetStreamBuffer() + (pokeyStream.GetFirstCountPoint() * frameSize), pokeyStream.GetSecondCountPoint() * frameSize, buff3);
        }

        // Add the number of frames recorded to the total count
        framescount += pokeyStream.GetFirstCountPoint();

        // Clear the SAP-R dumper memory and reset RMT routines
        pokeyStream.FinishedRecording();

        // Some additional variables that will be used below
        int targetAddrOfModule = VU_PLAYER_SONGDATA + lzss_chunk;	// All the LZSS data will be written starting from this address
        int lzss_startAddress = targetAddrOfModule + intro;
        int lzss_endAddress = lzss_startAddress + loop;				// this sets the address that defines where the data stream has reached its end

        SetStatusBarText("");

        // If the size is too big, abort the process and show an error message
        if (lzss_endAddress > RAM_MAX_ADDRESS)
        {
            CString message;
            message.Format("Error, LZSS data ($%04X - $%04X) is too big to fit in memory!\n\nHigh Instrument Speed and/or Stereo greatly inflate memory usage, even when data is compressed", lzss_startAddress, lzss_endAddress);
            SendErrorMessage("Buffer Overflow", message);
            return false;
        }

        // Set the song section and timer index 
        int index = LZSS_POINTER + count * 4;
        int timerindex = VU_PLAYER_SOUNGTIMER + count * 4;
        int subtunetimetotal = 0xFFFFFF / pokeyStream.GetFirstCountPoint();
        int subtunelooppoint = subtunetimetotal * pokeyStream.GetThirdCountPoint();
        int chunk = 0;

        mem[index + 0] = section & 0xFF;
        mem[index + 1] = section >> 8;
        mem[index + 2] = sequence & 0xFF;
        mem[index + 3] = sequence >> 8;
        mem[timerindex + 0] = subtunetimetotal >> 16;
        mem[timerindex + 1] = subtunetimetotal >> 8;
        mem[timerindex + 2] = subtunetimetotal & 0xFF;
        mem[timerindex + 3] = subtunelooppoint >> 16;

        // If there is an Intro section...
        if (intro)
        {
            memcpy(mem + targetAddrOfModule, buff2, intro);
            lzss_chunk += intro;
            mem[section + 0] = targetAddrOfModule & 0xFF;
            mem[section + 1] = targetAddrOfModule >> 8;
            mem[sequence] = chunk;
            section += 2;
            sequence += 1;
            chunk += 1;
        }

        // If there is a Loop section...
        if (loop)
        {
            memcpy(mem + lzss_startAddress, buff3, loop);
            lzss_chunk += loop;
            mem[section + 0] = lzss_startAddress & 0xFF;
            mem[section + 1] = lzss_startAddress >> 8;
            mem[sequence] = chunk;
            section += 2;
            sequence += 1;
            chunk += 1;
        }

        // End of data, will be overwritten if there is more data to export
        mem[section + 0] = lzss_endAddress & 0xFF;
        mem[section + 1] = lzss_endAddress >> 8;
        section += 2;
        mem[sequence] = (chunk | 0x80) - 1;
        sequence += 1;

        // Update the subtune offsets to export the next one
        lzss_total = lzss_endAddress;
        count++;
    }

    // Delete buffers on heap
    delete buff2;
    delete buff3;

    // Write the Atari Video text to memory, for 5 lines of 40 characters
    memcpy(&mem[LZSSP_LINE_1], metadata.atariText, TExportMetadata::ATARI_TEXT_SIZE);

    // Write the total framescount on the top line, next to the Region and VBI speed, for 28 characters
    memset(&mem[ LZSSP_LINE_0 + 0x0B], 32, 28);
    char framesdisplay[28] = { 0 };
    sprintf(framesdisplay, "(%i frames total)", framescount);
    for (int i = 0; i < 28; i++) mem[LZSSP_LINE_0 + 0x0B + i] = framesdisplay[i];
    CSongExporter::StrToAtariVideo((char*)mem + LZSSP_LINE_0 + 0x0B, 28);

    // I know the binary I have is currently set to NTSC, so I'll just convert to PAL and keep this going for now...
    if (!metadata.isNTSC)
    {
        unsigned char regionbytes[] =
        {
            0xB9,(LZSSP_TABPPPAL - 1) & 0xff,(LZSSP_TABPPPAL - 1) >> 8,			// LDA tabppPAL-1,y
            0x8D,LZSSP_ACPAPX2 & 0xFF,LZSSP_ACPAPX2 >> 8,						// STA acpapx2
            0xE0,0x9B,															// CPX #$9B
            0x30,0x05,															// BMI set_ntsc
            0xB9,(LZSSP_TABPPPALFIX - 1) & 0xff,(LZSSP_TABPPPALFIX - 1) >> 8,	// LDA tabppPALfix-1,y
            0xD0,0x03,															// BNE region_done
            0xB9,(LZSSP_TABPPNTSCFIX - 1) & 0xFF,(LZSSP_TABPPNTSCFIX - 1) >> 8	// LDA tabppNTSCfix-1,y
        };
        memcpy(&mem[VU_PLAYER_REGION], regionbytes, sizeof(regionbytes));
    }

    // Additional patches from the Export Dialog...
    mem[VU_PLAYER_SONG_SPEED] = metadata.instrspeed;						// Song speed
    mem[VU_PLAYER_RASTER_BAR] = metadata.displayRasterbar ? 0x80 : 0x00;	// Display the rasterbar for CPU level
    mem[VU_PLAYER_COLOR] = metadata.rasterbarColor;						    // Rasterbar colur 
    mem[VU_PLAYER_STEREO_FLAG] = metadata.isStereo ? 0xFF : 0x00;			// Is the song stereo?
    mem[VU_PLAYER_SONGTOTAL] = subsongs;									// Total number of subtunes
    if (!metadata.autoRegion) {												// Automatically adjust speed between regions?
        for (int i = 0; i < 4; i++) mem[VU_PLAYER_REGION + 6 + i] = 0xEA;	// set the 4 bytes to NOPs to disable it
    }

    // Reconstruct the export binary for the LZSS Driver, VUPlayer, and all the included data
    CAtariIO::SaveBinaryBlock(ou, mem, LZSSP_PLAYLZ16BEGIN, LZSSP_SONGINDEX, 1);

    // Set the run address to VUPlayer 
    mem[0x2e0] = LZSSP_VUPLAYER & 0xff;
    mem[0x2e1] = LZSSP_VUPLAYER >> 8;
    CAtariIO::SaveBinaryBlock(ou, mem, 0x2e0, 0x2e1, 0);

    // Overwrite the LZSS data region with both the pointers for subtunes index, and the actual LZSS streams until the end of file
    CAtariIO::SaveBinaryBlock(ou, mem, LZSS_POINTER, lzss_total, 0);

    return true;
}

// The XEX dialog process was split to a different function for clarity, same will be done for SAP later...
bool CSongExporter::ExportXEX_LZSS(TExportMetadata& metadata)
{
    CString EOL = "\r\n";

    CExpMSXDlg dlg;
    CString str;

    str = metadata.songname;

    if (g_rmtmsxtext != "")
    {
        dlg.m_txt = g_rmtmsxtext;	// same from last time, making repeated exports faster
    }
    else
    {
        dlg.m_txt = str + EOL;
        if (metadata.isStereo) { dlg.m_txt += "STEREO"; }
        dlg.m_txt += EOL;
        dlg.m_txt += metadata.currentTime.Format("%d/%m/%Y") + EOL;
        dlg.m_txt += "Author: (press SHIFT key)" + EOL;
        dlg.m_txt += "Author: ???";
    }

    str.Format("Playback speed will be adjusted to %s Hz on both PAL and NTSC systems.", (metadata.isNTSC ? "60" : "50"));
    dlg.m_speedinfo = str;

    if (dlg.DoModal() != IDOK)
    {
        return false;
    }
    g_rmtmsxtext = dlg.m_txt;
    g_rmtmsxtext.Replace("\x0d\x0d", "\x0d");	//13, 13 => 13

    // This block of code will handle all the user input text that will be inserted in the binary during the export process
    memset(metadata.atariText, ' ', TExportMetadata::ATARI_TEXT_SIZE);
    int p = 0, q = 0;
    char a;
    for (int i = 0; i < dlg.m_txt.GetLength(); i++)
    {
        a = dlg.m_txt.GetAt(i);
        if (a == '\n') { p += 40; q = 0; }
        else
        {
            metadata.atariText[p + q] = a;
            q++;
        }
        if (p + q >= 5 * 40) break;
    }
    StrToAtariVideo((char*)metadata.atariText, TExportMetadata::ATARI_TEXT_SIZE);

    metadata.rasterbarColor = dlg.m_metercolor;
    metadata.displayRasterbar = dlg.m_meter;
    metadata.autoRegion = dlg.m_region_auto;

    return true;
}