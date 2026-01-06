#pragma once

#include "SongExport.h"
#include "PokeyRederer.h"

class CWaveFileExporter
{

public:
    static bool ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory);
};

