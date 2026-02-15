#pragma once

#include "PokeyRenderer.h"
#include "SongExport.h"

class CWaveFileExporter
{

public:
    static bool ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory);
};

