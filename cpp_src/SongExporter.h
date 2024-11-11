#pragma once

#include "Song.h"
#include <iosfwd>
#include "XPokey.h"
#include "SongExport.h"

extern CString g_rmtmsxtext;

class CXEXFile
{
public:
    static const size_t ATARI_TEXT_SIZE = 5 * 40;
    char songname[SONG_NAME_MAX_LEN + 1];
    CTime currentTime;
    int instrspeed;
    bool isStereo;
    bool isNTSC;
    bool autoRegion;
    bool displayRasterbar;
    int rasterbarColor;
    char atariText[ATARI_TEXT_SIZE];

    void InitFromSong(const CSong& song);
};

class CSongExporter {

public:
    CSongExporter();

    /// <summary>
    /// Generate a SAP-R data stream and compress it with LZSS
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <param name="filename">Filename to output additional files, required for splitting the Intro and Loop sections of a song</param>
    /// <returns></returns>
    bool ExportLZSS(CSongExport& songExport, std::ofstream& ou);


    /// <summary>
    /// Generate a SAP-R data stream, compress it and optimise the compression further by removing redundancy.
    /// Ideally, this will be used to find and merge duplicated songline buffers, for a compromise between speed and memory.
    /// The output streams may get considerably smaller, at the cost of having an index to keep track of, in order to reconstruct the stream properly.
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <param name="filename">Filename to use for saving the data output</param>
    /// <returns></returns>
    bool ExportCompactLZSS(CSongExport& songExport, std::ofstream& ou); // TODO: What is this? Currently unsued?

    /// <summary>
    /// Export the Pokey registers to the SAP Type R format (data stream)
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <returns>true if the file was written</returns>
    bool ExportSAP_R(CSongExport& songExport, std::ofstream& ou);

    /// <summary>
    /// Export the Pokey registers to a SAP TYPE B format
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <returns>true if the file was written</returns>
    bool ExportSAP_B_LZSS(CSongExport& songExport, std::ofstream& ou);

    // Pokey and memory are modified
    bool ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory);

    /// <summary>
    /// Generate a SAP-R data stream, compress it and export to VUPlayer xex
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <returns></returns>
    bool ExportXEX_LZSS(CSongExport& songExport, std::ofstream& ou);



private:
    static void StrToAtariVideo(char* txt, int count);

    // A dumb SAP-R LZSS optimisations bruteforcer, returns the optimal value and buffer
    static int BruteforceOptimalLZSS(unsigned char* src, int srclen, unsigned char* dst);
    static bool ShowXEXExportDialog(const CSong& song, CXEXFile& xexFile);
};

