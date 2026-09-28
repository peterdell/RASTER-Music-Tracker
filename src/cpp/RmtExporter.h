#pragma once

#include "Song.h"
#include "StdAfx.h"

class CRmtExporter {

public:
    // ExportAsRMT() takes std::ostream& rather than std::ofstream& - its one
    // real call site (ExportV2()) passes a genuine file stream (which
    // satisfies the wider base type), and the wider type lets tests use an
    // in-memory stream (see test/SongEditingTests.cpp).
    static bool ExportAsRMT(CSong& song, std::ostream& ou, TExportDescription* exportDesc);
    static bool ExportAsStrippedRMT(CSong& song, std::ofstream& ou, TExportDescription* exportDesc, LPCTSTR filename);
    // Extracted from ExportAsStrippedRMT(): the dialog-independent work,
    // once the confirmed target address and SFX-support flag are known.
    // Takes std::ostream& for the same reason as ExportAsRMT() above.
    static bool ExportAsStrippedRMTApply(CSong& song, std::ostream& ou, int targetAddrOfModule, BOOL sfxSupport);
};
