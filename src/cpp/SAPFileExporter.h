#pragma once

#include "SongExport.h"
#include "SAPFile.h"

class CSAPFileExporter
{

public:
    static bool ExportSAP_B_LZSS(CSongExport& songExport, CSAPFile& sapFile, std::ofstream& ou);

    static bool ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ofstream& ou);

};

