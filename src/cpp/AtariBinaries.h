#pragma once

#include "StdAfx.h"

#include "TrackerDriverVersion.h"

class CRmtAtariBinaries {

public:
    // TODO Return CByteArray*
    // Have instance and free at end of application
    static bool GetTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion, byte*& binary, WORD& size);
    static bool GetVUPlayerBinary(byte*& binary, WORD& size);

    // The file a driver version is read from - named when it cannot be
    // loaded, and where a build to be tested goes (doc/rmt_en.md's "Files
    // and Folders").
    static CString GetTrackerDriverFilePath(TrackerDriverVersion trackerDriverVersion);

    // What to say when that file is not there. Without it the emulated Atari
    // has no player routines, so it plays silence and every export is quiet;
    // saying so beats leaving that to be noticed. Same wording as the Java
    // port's RmtSession.loadTrackerDriver().
    static CString GetMissingTrackerDriverMessage(TrackerDriverVersion trackerDriverVersion);
};
