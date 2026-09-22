#pragma once

#include "SongExport.h"
#include "SAPFile.h"

class CSAPFileExporter
{

public:
    static bool ExportSAP_B_LZSS(CSongExport& songExport, CSAPFile& sapFile, std::ofstream& ou);

    // Takes std::ostream& rather than std::ofstream& - its one real call
    // site (CSongExporter::ExportSAP_R()) passes a genuine file stream
    // (which satisfies the wider base type), and the wider type lets tests
    // use an in-memory stream (see test/SongEditingTests.cpp).
    static bool ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou);

};

