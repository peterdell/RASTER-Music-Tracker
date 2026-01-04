#pragma once

#include "SongExport.h"
#include "Pokey.h"

class CWaveFileExporter
{

public:
    static bool ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory);
};

