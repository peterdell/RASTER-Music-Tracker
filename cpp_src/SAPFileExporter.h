#pragma once

#include "Song.h"
#include "PokeyStream.h"
#include "SAPFile.h"

class CSAPFileExporter
{

public:
    static bool ExportSAP_B_LZSS(const CSong& song, const CPokeyStream& pokeyStream, CSAPFile& sapFile, std::ofstream& ou);

    static bool ExportSAP_R(const CSong& song, const CPokeyStream& pokeyStream, CSAPFile& sapFile, std::ofstream& ou);

};

