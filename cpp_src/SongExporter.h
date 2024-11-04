#pragma once

#include "Song.h"
#include <fstream>
#include "XPokey.h"

extern CString g_rmtmsxtext;

class CSongExporter {

public:

    /// <summary>
    /// Generate a SAP-R data stream and compress it with LZSS
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <param name="filename">Filename to output additional files, required for splitting the Intro and Loop sections of a song</param>
    /// <returns></returns>
    static bool ExportLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename);


    /// <summary>
    /// Generate a SAP-R data stream, compress it and optimise the compression further by removing redundancy.
    /// Ideally, this will be used to find and merge duplicated songline buffers, for a compromise between speed and memory.
    /// The output streams may get considerably smaller, at the cost of having an index to keep track of, in order to reconstruct the stream properly.
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <param name="filename">Filename to use for saving the data output</param>
    /// <returns></returns>
    static bool ExportCompactLZSS(CSong& song, std::ofstream& ou, LPCTSTR filename); // TODO: What is this? Currently unsued?

    /// <summary>
    /// Export the Pokey registers to the SAP Type R format (data stream)
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <returns>true if the file was written</returns>
    static bool ExportSAP_R(CSong& song, std::ofstream& ou);

    /// <summary>
    /// Export the Pokey registers to a SAP TYPE B format
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <returns>true if the file was written</returns>
    static bool ExportSAP_B_LZSS(CSong& song, std::ofstream& ou);

    static bool ExportWAV(CSong& song, std::ofstream& ou, LPCTSTR filename, CXPokey& pokey, byte* memory);

    /// <summary>
    /// Generate a SAP-R data stream, compress it and export to VUPlayer xex
    /// </summary>
    /// <param name="ou">File to output the compressed data to</param>
    /// <returns></returns>
    static bool ExportXEX_LZSS(CSong& song, std::ofstream& ou);

    // TODO: Private

private:
    static void StrToAtariVideo(char* txt, int count);

    static void CreateExportMetadata(const CSong& song, TExportMetadata& metadata);

    // A dumb SAP-R LZSS optimisations bruteforcer, returns the optimal value and buffer
    static int BruteforceOptimalLZSS(unsigned char* src, int srclen, unsigned char* dst);
    static bool ExportXEX_LZSS(TExportMetadata& metadata);
};

