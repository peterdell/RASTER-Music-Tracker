#pragma once

#include "Song.h"
#include "StdAfx.h"

class CRmtExporter
{

public:
    static bool ExportAsRMT(CSong& song, std::ofstream& ou, TExportDescription* exportDesc);
    static bool ExportAsStrippedRMT(CSong& song, std::ofstream& ou, TExportDescription* exportDesc, LPCTSTR filename);
};

