#include "StdAfx.h"

#include "SAPFileExporter.h"

// CSAPFileExporter::ExportSAP_R() has no dialog/hazard of its own - only
// CSAPFile::Export() (already tested, see test/SAPFileTests.cpp) and
// CPokeyStream::WriteToFile() (a plain buffer write). Kept separate from
// SAPFileExporter.cpp's ExportSAP_B_LZSS(), which loads a real resource
// file from disk (resources/players/vu_player_v2.obx via
// GetResourceFilePath()) - a genuinely different kind of dependency,
// not yet investigated - same "split along the coupling seam" pattern as
// elsewhere.
bool CSAPFileExporter::ExportSAP_R(CSongExport& songExport, CSAPFile& sapFile, std::ostream& ou) {
    sapFile.SetType("R");
    sapFile.Export(ou);

    // Write the SAP-R stream to the output file defined in the path dialog with the data specified above
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();
    pokeyStream.WriteToFile(ou, pokeyStream.GetFirstCountPoint(), 0);

    return true;
}
