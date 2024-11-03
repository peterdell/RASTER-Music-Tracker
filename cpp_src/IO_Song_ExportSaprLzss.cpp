#include "StdAfx.h"
#include <fstream>
#include <memory.h>

#include "GuiHelpers.h"
#include "Song.h"
#include "Instruments.h"

#include "ExportDlgs.h"

#include "Atari6502.h"
#include "PokeyStream.h"

#include "Global.h"

#include "ChannelControl.h"

#include "lzssp.h"
#include "lzss_sap.h"

extern CInstruments	g_Instruments;

#include "VUPlayer.h"


/// <summary>
/// Generate a SAP-R data stream and compress it with LZSS
/// </summary>
/// <param name="ou">File to output the compressed data to</param>
/// <param name="filename">Filename to output additional files, required for splitting the Intro and Loop sections of a song</param>
/// <returns></returns>
bool CSong::ExportLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename)
{
    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    SetStatusBarText("Compressing data ...");

    int frameSize = (g_tracks4_8 == 8) ? 18 : 9;	//SAP-R bytes to copy, Stereo doubles the number

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

/// <summary>
/// Generate a SAP-R data stream, compress it and optimise the compression further by removing redundancy.
/// Ideally, this will be used to find and merge duplicated songline buffers, for a compromise between speed and memory.
/// The output streams may get considerably smaller, at the cost of having an index to keep track of, in order to reconstruct the stream properly.
/// </summary>
/// <param name="ou">File to output the compressed data to</param>
/// <param name="filename">Filename to use for saving the data output</param>
/// <returns></returns>
bool CSong::ExportCompactLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename)
{
    // TODO: everything related to exporting the stream buffer into small files and compress them to LZSS

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    SetStatusBarText("Compressing data ...");

    int frameSize = (g_tracks4_8 == 8) ? 18 : 9;	//SAP-R bytes to copy, Stereo doubles the number

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


/// <summary>
/// Generate a SAP-R data stream, compress it and export to VUPlayer xex
/// </summary>
/// <param name="ou">File to output the compressed data to</param>
/// <returns></returns>
bool CSong::ExportLZSS_XEX(CSong& song, std::ofstream& ou)
{
    CString s, t;

    MemoryAddress addressFrom, addressTo;

    int subsongs = song.GetSubsongParts(t);
    int count = 0;

    int subtune[256]{};

    int lzss_chunk = 0;	// Subtune size will be added to be used as the offset to the next one
    int lzss_total = 0;	// Final offset for LZSS bytes to export
    int framescount = 0;

    int frameSize = (g_tracks4_8 == 8) ? 18 : 9;	// SAP-R bytes to copy, Stereo doubles the number
    int section = VU_PLAYER_SECTION;
    int sequence = VU_PLAYER_SEQUENCE;

    byte mem[RAM_SIZE]{};					// Default RAM size for most 800xl/xe machines

    // GetSubsongParts returns a CString, so the values must be converted back to int first, FIXME
    for (int i = 0; i < subsongs; i++)
    {
        char c[3];
        c[0] = t[i * 3];
        c[1] = t[i * 3 + 1];
        c[2] = '\0';
        subtune[i] = strtoul(c, NULL, 16);
    }

    // Load VUPlayerLZSS to memory
    Atari_LoadOBX(IOTYPE_LZSS_XEX, mem, addressFrom, addressTo);

    // Create the export metadata for songname, Atari text, parameters, etc
    TExportMetadata metadata;
    if (!CreateExportMetadata(song, IOTYPE_LZSS_XEX, &metadata))
        return false;

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
        int lzss_endAddress = lzss_startAddress + loop;							// this sets the address that defines where the data stream has reached its end

        SetStatusBarText("");

        // If the size is too big, abort the process and show an error message
        // JAC! Output memory boundaries
        if (lzss_endAddress > RAM_MAX_ADDRESS)
        {
            CString message;
            message.Format("Error, LZSS data ($%04X - $%04X) is too big to fit in memory!\n\nHigh Instrument Speed and/or Stereo greatly inflate memory usage, even when data is compressed", lzss_startAddress, lzss_endAddress);
            MessageBox(g_hwnd,
                message,
                "Error, Buffer Overflow!", MB_ICONERROR);
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
    memcpy(mem + LZSSP_LINE_1, metadata.atariText, 40 * 5);

    // Write the total framescount on the top line, next to the Region and VBI speed, for 28 characters
    memset(mem + LZSSP_LINE_0 + 0x0B, 32, 28);
    char framesdisplay[28] = { 0 };
    sprintf(framesdisplay, "(%i frames total)", framescount);
    for (int i = 0; i < 28; i++) mem[LZSSP_LINE_0 + 0x0B + i] = framesdisplay[i];
    StrToAtariVideo((char*)mem + LZSSP_LINE_0 + 0x0B, 28);

    // I know the binary I have is currently set to NTSC, so I'll just convert to PAL and keep this going for now...
    if (!metadata.isNTSC)
    {
        unsigned char regionbytes[18] =
        {
            0xB9,(LZSSP_TABPPPAL - 1) & 0xff,(LZSSP_TABPPPAL - 1) >> 8,			// LDA tabppPAL-1,y
            0x8D,LZSSP_ACPAPX2 & 0xFF,LZSSP_ACPAPX2 >> 8,						// STA acpapx2
            0xE0,0x9B,															// CPX #$9B
            0x30,0x05,															// BMI set_ntsc
            0xB9,(LZSSP_TABPPPALFIX - 1) & 0xff,(LZSSP_TABPPPALFIX - 1) >> 8,	// LDA tabppPALfix-1,y
            0xD0,0x03,															// BNE region_done
            0xB9,(LZSSP_TABPPNTSCFIX - 1) & 0xFF,(LZSSP_TABPPNTSCFIX - 1) >> 8	// LDA tabppNTSCfix-1,y
        };
        for (int i = 0; i < 18; i++) mem[VU_PLAYER_REGION + i] = regionbytes[i];
    }

    // Additional patches from the Export Dialog...
    mem[VU_PLAYER_SONG_SPEED] = metadata.instrspeed;						// Song speed
    mem[VU_PLAYER_RASTER_BAR] = metadata.displayRasterbar ? 0x80 : 0x00;	// Display the rasterbar for CPU level
    mem[VU_PLAYER_COLOUR] = metadata.rasterbarColour;						// Rasterbar colour 
    mem[VU_PLAYER_STEREO_FLAG] = metadata.isStereo ? 0xFF : 0x00;			// Is the song stereo?
    mem[VU_PLAYER_SONGTOTAL] = subsongs;									// Total number of subtunes
    if (!metadata.autoRegion) {											// Automatically adjust speed between regions?
        for (int i = 0; i < 4; i++) mem[VU_PLAYER_REGION + 6 + i] = 0xEA;	// set the 4 bytes to NOPs to disable it
    }

    // Reconstruct the export binary for the LZSS Driver, VUPlayer, and all the included data
    SaveBinaryBlock(ou, mem, LZSSP_PLAYLZ16BEGIN, LZSSP_SONGINDEX, 1);

    // Set the run address to VUPlayer 
    mem[0x2e0] = LZSSP_VUPLAYER & 0xff;
    mem[0x2e1] = LZSSP_VUPLAYER >> 8;
    SaveBinaryBlock(ou, mem, 0x2e0, 0x2e1, 0);

    // Overwrite the LZSS data region with both the pointers for subtunes index, and the actual LZSS streams until the end of file
    SaveBinaryBlock(ou, mem, LZSS_POINTER, lzss_total, 0);

    return true;
}

/// <summary>
/// Get the Pokey registers to be dumped to a stream buffer.
/// GUI is disabled but MFC messages are being pumped, so the screen is updated
/// </summary>
/// <returns></returns>
void CSong::DumpSongToPokeyStream(CPokeyStream& pokeyStream, int playmode, int songline, int trackline)
{
    CString statusBarLog;

    Stop();					// Make sure RMT is stopped 
    Atari_InitRMTRoutine();	// Reset the RMT routines 
    SetChannelOnOff(-1, 0);	// Switch all channels off 

    // Activate stream recording mode.
    m_pokeyStream = &pokeyStream;
    m_pokeyStream->StartRecording();

    // Play song using the chosen playback parameters
    // If no argument was passed, Play from start will be assumed
    m_songactiveline = songline;
    m_trackactiveline = trackline;
    Play(playmode, m_followplay);

    // Wait in a tight loop pumping messages until the playback stops
    EnableWindow(g_hwnd, FALSE);
    HCURSOR oldCursor = SetCursor(LoadCursor(0, IDC_WAIT)); // JAC! Use everyhwere

    // The SAP-R dumper is running during that time...
    while (m_play != MPLAY_STOP)
    {
        // 1 VBI of module playback
        PlayVBI();

        // Increment the timer shown during playback
        g_playtime++;

        // Multiple RMT routine calls will be processed if needed
        for (int i = 0; i < m_instrumentSpeed; i++)
        {
            // 1 VBI of RMT routine (for instruments)
            if (g_rmtroutine)
            {
                Atari_PlayRMT();
            }
            // Transfer from g_atarimem to POKEY buffer
            pokeyStream.Record();
        }

        // Update the screen only once every few frames
        // Displaying everything in real time slows things down considerably!
        if (!RefreshScreen(1)) {
            continue;
        }

        // Display the number of frames dumped so far
        statusBarLog.Format("Generating Pokey stream, playing song in quick mode... %i frames recorded", pokeyStream.GetCurrentFrame());
        SetStatusBarText(statusBarLog);
    }

    // End playback now, the SAP-R data should have been dumped successfully!
    Stop();

    // Deactivate stream recording.
    m_pokeyStream = nullptr;

    statusBarLog.Format("Done... %i frames recorded in total, Loop point found at frame %i", pokeyStream.GetCurrentFrame(), pokeyStream.GetFirstCountPoint());
    SetStatusBarText(statusBarLog);

    SetCursor(oldCursor);
    EnableWindow(g_hwnd, TRUE);
}

// A dumb SAP-R LZSS optimisations bruteforcer, returns the optimal value and buffer
int CSong::BruteforceOptimalLZSS(unsigned char* src, int srclen, unsigned char* dst)
{
    CString statusBarLog;
    CCompressLzss lzssData;

    // Start from a high value to force the first pattern to be the best one
    int bestScore = 0xFFFFFF;
    int optimal = 0;

    EnableWindow(g_hwnd, FALSE);

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

        statusBarLog.Format("Compressing %i bytes, bruteforcing optimisation pattern %i... Current best: %i bytes with optimisation pattern %i", srclen, i, bestScore, optimal);
        SetStatusBarText(statusBarLog);
    }

    // Bruteforcing was completed, display some stats
    statusBarLog.Format("Done... %i bytes were shrunk to %i bytes using the optimisation pattern %i", srclen, bestScore, optimal);
    SetStatusBarText(statusBarLog);

    EnableWindow(g_hwnd, TRUE);

    return lzssData.LZSS_SAP(src, srclen, dst, optimal);
}
