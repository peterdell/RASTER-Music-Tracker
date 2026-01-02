#pragma once

#include "SongExport.h"
#include "XPokey.h"

class CWaveFileExporter
{

public:
    static bool ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory);
};

