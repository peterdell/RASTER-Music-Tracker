#pragma once

#include "Song.h"
#include <fstream>

class CSongExporter {

public:
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
};

