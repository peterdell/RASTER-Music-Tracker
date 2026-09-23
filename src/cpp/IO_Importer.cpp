#include "StdAfx.h"

#include "AtariIO.h"

#include "importdlgs.h"

#include "Tracks.h"
#include "Song.h"
#include "Instruments.h"
#include "Notes.h"
#include "Messages.h"

#include "Global.h"

extern CInstruments g_Instruments;

// CConvertTracks (TMC-only helper) and CSong::ImportTMCParseHeader()/
// ImportTMCApply() (the real conversion work) are implemented in
// IO_ImporterCore.cpp - see plans/IO_IMPORTER_PLAN.md. ImportTMC() below is
// now a thin wrapper around them, showing its two real dialogs.

//----------------------------------------------

int CSong::ImportTMC(std::ifstream& in) {
    auto originalg_tracks4_8 = GetTracks();

    TImportTMCHeader header;
    if (!ImportTMCParseHeader(in, header)) {
        SendErrorMessage("Open error", "Corrupted TMC file or unsupported format version.");
        return 0;
    }

    CImportTmcDlg importdlg;
    CString s = m_songname;
    s.TrimRight();
    importdlg.m_info.Format("TMC module: %s", (LPCTSTR)s);

    if (importdlg.DoModal() != IDOK) {
        return 0;
    }

    BOOL x_usetable = importdlg.m_check1;
    BOOL x_optimizeloops = importdlg.m_check6;
    BOOL x_truncateunusedparts = importdlg.m_check7;

    TImportTMCResult result;
    ImportTMCApply(header, x_usetable, x_optimizeloops, x_truncateunusedparts, result);

    //FINAL DIALOGUE AFTER IMPORT
    CImportTmcFinishedDlg imfdlg;
    imfdlg.m_info.Format("%i tracks, %i instruments, %i songlines", result.numoftracks, result.nonemptyinstruments, result.songlines);

    //OPTIMIZATIONS
    if (x_optimizeloops) {
        CString s;
        s.Format("\x0d\x0aOptimization: Loops in %i tracks (%i beats/lines)", result.optitracks, result.optibeats);
        imfdlg.m_info += s;
    }

    if (x_truncateunusedparts) {
        CString s;
        s.Format("\x0d\x0aOptimization: Cleared %i, truncated %i tracks (%i beats/lines)", result.clearedtracks, result.truncatedtracks, result.truncatedbeats);
        imfdlg.m_info += s;
    }

    if (imfdlg.DoModal() != IDOK) {
        //did not give Ok, so it deletes
        ClearSong(originalg_tracks4_8); //returns the original value
        SendInformationMessage("Import...", "Module import aborted.");
    }

    return 1;
}

/********************************************************************************************/

//September 27, 2003 8:38 PM ... I just imported aurora.mod, released it without editing and I'm amazed !!!
//							That's absolutely AMAZING! AMAZING! ABSOLUTELY AWESOME !!!

// CSong::ImportMODParseHeader()/ImportMODApply() (and their private helpers
// TMODInstrumentMark/AtariVolume) are implemented in IO_ImporterCore.cpp -
// see plans/IO_IMPORTER_PLAN.md. ImportMOD() below is now a thin wrapper
// around them, showing its two real dialogs.

int CSong::ImportMOD(std::ifstream& in) {
    int originalg_tracks4_8 = GetTracks(); //keeps the original value for Abort

    TImportMODHeader header;
    if (!ImportMODParseHeader(in, header)) {
        // ParseHeader() reports which of the three original guards tripped
        // via header.errorCode (rather than showing a MessageBox itself),
        // so the exact original, differently-worded message can still be
        // shown here - see TImportMODHeader's own comment.
        switch (header.errorCode) {
        case 1:
            SendErrorMessage("Error", "Bad file format.");
            break;
        case 2: {
            CString es;
            es.Format("There isn't ProTracker identification header bytes.\nAllowed headers are \"M.K.\" or from \"4CHN\" to \"8CHN\",\nbut there is \"%s\".", header.head + 1080);
            SendErrorMessage("Error", (LPCTSTR)es);
            break;
        }
        case 3:
            SendErrorMessage("Error", "Bad file.");
            break;
        }
        return 0;
    }

    BYTE trackorder[8] = {0, 1, 2, 3, 4, 5, 6, 7}; //track layout
    int rmttype = 0;

    CImportModDlg importdlg;
    importdlg.m_info.Format("%i channels %i samples ProTracker module detected.\n(Header bytes \"%s\".)", header.chnls, header.modsamples, header.head + 1080);
    if (header.chnls == 4) { //4 channels module
        importdlg.m_txtradio1 = "RMT4 with 1,2,3,4 tracks order";
        importdlg.m_txtradio2 = "RMT8 with 1,4 / 2,3 tracks order";
        if (importdlg.DoModal() == IDOK) {
            if (importdlg.m_txtradio1 != "") { //first choice
                rmttype = 4;
            } else { //second choice
                rmttype = 8;
                trackorder[0] = 0;
                trackorder[1] = 4;
                trackorder[2] = 5;
                trackorder[3] = 1;
            }
        }
    } else { //5-8 channels module
        importdlg.m_txtradio1 = "RMT8 with 1,4,5,8 / 2,3,6,7 tracks order";
        importdlg.m_txtradio2 = "RMT8 with 1,2,3,4 / 5,6,7,8 tracks order";
        if (importdlg.DoModal() == IDOK) {
            if (importdlg.m_txtradio1 != "") { //first choice
                rmttype = 8;
                trackorder[0] = 0;
                trackorder[1] = 4;
                trackorder[2] = 5;
                trackorder[3] = 1;
                trackorder[4] = 2;
                trackorder[5] = 6;
                trackorder[6] = 7;
                trackorder[7] = 3;
            } else { //second choice
                rmttype = 8;
            }
        }
    }

    if (rmttype != 4 && rmttype != 8) { //did not select the back option (cancel in the dialog)
        return 0;
    }

    BOOL x_shiftdownoctave = importdlg.m_check1;
    BOOL x_portamento = importdlg.m_check5;
    BOOL x_fullvolumerange = importdlg.m_check2;
    BOOL x_volumeincrease = importdlg.m_check3;
    BOOL x_decreaseinstrument = importdlg.m_check4;
    BOOL x_optimizeloops = importdlg.m_check6;
    BOOL x_truncateunusedparts = importdlg.m_check7;
    // x_fourier (importdlg.m_check8) is captured by the original but only
    // ever read inside a genuinely commented-out Fourier-transform block -
    // dead code, same category as the already-found MakeTuningBlock/
    // DecodeTuningBlock - so it's not passed through to Apply().

    TImportMODResult result;
    ImportMODApply(in, header, rmttype, trackorder, x_shiftdownoctave, x_portamento, x_fullvolumerange, x_volumeincrease, x_decreaseinstrument, x_optimizeloops, x_truncateunusedparts, result);

    //FINAL DIALOGUE AFTER IMPORT
    CImportModFinishedDlg imfdlg;
    imfdlg.m_info.Format("%i tracks, %i instruments, %i songlines", result.destnum, result.nonemptysamples, result.songlines);

    //OPTIMIZATIONS
    if (x_optimizeloops) {
        CString s;
        s.Format("\x0d\x0aOptimization: Loops in %i tracks (%i beats/lines)", result.optitracks, result.optibeats);
        imfdlg.m_info += s;
    }

    if (x_truncateunusedparts) {
        CString s;
        s.Format("\x0d\x0aOptimization: Cleared %i, truncated %i tracks (%i beats/lines)", result.clearedtracks, result.truncatedtracks, result.truncatedbeats);
        imfdlg.m_info += s;
    }

    if (imfdlg.DoModal() != IDOK) {
        //did not give Ok, so it deletes
        ClearSong(originalg_tracks4_8); //returns the original value
        SendInformationMessage("Import...", "Module import aborted.");
    }

    return 1;
}
