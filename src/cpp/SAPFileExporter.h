#pragma once

#include "SongExport.h"
#include "SAPFile.h"

class CSAPFileExporter {

public:
    // Both take std::ostream& rather than std::ofstream& - their one real
    // call site each (CSongExporter::ExportSAP_B_LZSS()/ExportSAP_R())
    // passes a genuine file stream (which satisfies the wider base type),
    // and the wider type lets tests use an in-memory stream (see
    // test/SongEditingTests.cpp).
    static bool ExportSAP_B_LZSS(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou);

    static bool ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou);
};
