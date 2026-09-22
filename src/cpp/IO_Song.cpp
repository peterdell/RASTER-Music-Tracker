#include "StdAfx.h"

#include "Memory.h"
#include "Song.h"
#include "SongIO.h"

#include "FileNewDlg.h"

#include "ImportDlgs.h"

#include "AtariIO.h"
#include "AtariTrackerDriver.h"
#include "PokeyRenderer.h"

#include "IOHelpers.h"

#include "Clipboard.h"
#include "Instruments.h"

#include "Global.h"

#include "ChannelControl.h"
#include "RmtMidi.h"

#include "ASMFileExporter.h"
#include "SongExporter.h"

#include "RmtExporter.h"

extern CInstruments	g_Instruments;
extern CTrackClipboard g_TrackClipboard;
extern CXPokey g_Pokey;
extern CRmtMidi g_Midi;
extern CAtariTrackerDriver* g_AtariTrackerDriver;

// SongToAta() and AtaToSong() are implemented in SongCore.cpp (no Global.h
// dependency).

/// <summary>
/// Reload the currently loaded file
/// </summary>
void CSong::FileReload()
{
    if (!FileCanBeReloaded())
    {
        return;
    }

    // Stop the music first
    Stop();
    auto answer = MessageBox(g_hwnd, "Discard all changes since your last save?\n\nWarning: Undo operation won't be possible!!!", "Reload", MB_YESNOCANCEL | MB_ICONQUESTION);
    if (answer == IDYES)
    {
        CString filename = m_filename;
        FileOpen((LPCTSTR)filename, FALSE); // Without warning for unsaved changes
    }
}

/// <summary>
/// Open a file for loading
/// </summary>
/// <param name="filename">path to song file to load</param>
/// <param name="warnOfUnsavedChanges">TRUE if the GUI should warn on unsaved changes</param>
BOOL CSong::FileOpen(const char* filename, BOOL warnOfUnsavedChanges)
{
    // Stop the music first
    Stop();

    if (warnOfUnsavedChanges && WarnUnsavedChanges()) {
        return FALSE;
    }

    FILE_LOADSAVE fileDialogParameters;
    // Open the file open dialog with *.rmt, *.txt and *.rmw options
    CFileDialog dlg(TRUE,
        NULL,
        NULL,
        OFN_HIDEREADONLY,
        fileDialogParameters.GetFilters()
    );
    dlg.m_ofn.lpstrTitle = "Load song file";

    if (!g_lastLoadPath_Songs.IsEmpty()) {
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Songs;
    }
    else {
        if (!g_defaultSongsPath.IsEmpty()) { dlg.m_ofn.lpstrInitialDir = g_defaultSongsPath; }
    }

    if (GetIOType() == SongIOType::RMT) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::RMT; }
    if (GetIOType() == SongIOType::TXT) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::TXT; }
    if (GetIOType() == SongIOType::RMW) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::RMW; }

    CString fileToLoad = "";
    int filterIndex = 0;
    if (filename)
    {
        fileToLoad = filename;
        CString ext = fileToLoad.Right(4).MakeLower();
        if (ext == ".rmt") filterIndex = FILE_LOADSAVE::RMT;
        else
            if (ext == ".txt") filterIndex = FILE_LOADSAVE::TXT;
            else
                if (ext == ".rmw") filterIndex = FILE_LOADSAVE::RMW;
    }
    else
    {
        // If not ok, it's over
        if (dlg.DoModal() != IDOK) {
            return FALSE;
        }

        fileToLoad = dlg.GetPathName();
        filterIndex = dlg.m_ofn.nFilterIndex;
    }

    // Continue wnly when a file was selected in the FileDialog or specified at startup
    if (fileToLoad.IsEmpty() || !filterIndex) {
        return FALSE;
    }

    // Use filename from the FileDialog or from the command line
    g_lastLoadPath_Songs = GetFilePath(fileToLoad);

    // Make sure .rmt, .txt or .rmw file was selected
    if (!fileDialogParameters.IsValidFilterIndex(filterIndex))
    {
        return FALSE;
    }

    // Open the input file in binary format (even the text file)
    std::ifstream in(fileToLoad, std::ios::binary);
    if (!in)
    {
        MessageBox(g_hwnd, "Can't open this file: " + fileToLoad, "Open error", MB_ICONERROR);
        return FALSE;
    }

    // Deletes the current song
    ClearSong(g_tracks4_8);

    auto loadedOk = false;
    switch (filterIndex)
    {
    case FILE_LOADSAVE::RMT: // RMT choice in Dialog
        loadedOk = LoadRMT(in);
        m_ioType = SongIOType::RMT; // TODO: Move into Load...
        break;

    case FILE_LOADSAVE::TXT: // TXT choice in Dialog
        loadedOk = LoadTxt(in);
        m_ioType = SongIOType::TXT;
        break;

    case FILE_LOADSAVE::RMW: // RMW choice in Dialog
        loadedOk = LoadRMW(in);
        m_ioType = SongIOType::RMW;
        break;
    }
    in.close();

    if (!loadedOk)
    {
        // Something in the Load... function failed
        ClearSong(GetTracks());		// Erases everything
        SetRMTTitle();
        return FALSE;
    }

    m_filename = fileToLoad;
    m_speed = m_mainSpeed;			// Init speed
    SetRMTTitle();					// Window name
    // TODO: Check what's different after loading Buddy 15kHzs examples
    g_ChannelControl.SetAllChannelsOn();
    return TRUE;

}

void CSong::FileSave()
{
    // Stop the music first
    Stop();

    // If the song has no filename, prompt the "save as" dialog first
    if (m_filename.IsEmpty() || GetIOType() == SongIOType::NONE)
    {
        FileSaveAs();
        return;
    }

    // If the RMT module hasn't met the conditions required to be valid, it won't be saved/overwritten
    if (GetIOType() == SongIOType::RMT && !TestBeforeFileSave())
    {
        MessageBox(g_hwnd, "Warning!\nNo data has been saved!", "Warning", MB_ICONEXCLAMATION);
        SetRMTTitle();
        return;
    }

    // Create the file to save, ios::binary will be assumed if the format isn't TXT
    // TODO: Sould that be out | binary?
    std::ofstream out(m_filename, (GetIOType() == SongIOType::TXT) ? std::ios::out : std::ios::binary);
    if (!out)
    {
        MessageBox(g_hwnd, "Can't create this file", "Write error", MB_ICONERROR);
        return;
    }

    bool saveResult = false;
    switch (GetIOType())
    {
    case SongIOType::RMT:
        saveResult = ExportV2(*this, out, SongIOType::RMT);
        break;

    case SongIOType::TXT:
        saveResult = SaveTxt(out);
        break;

    case SongIOType::RMW:
        // NOTE:
        // Remembers the current octave and volume for the active instrument (for saving to RMW) 
        // It is only saved when the instrument is changed and could change the octave or volume before saving without subsequently changing the current instrument
        g_Instruments.MemorizeOctaveAndVolume(m_activeinstr, m_octave, m_volume);
        saveResult = SaveRMW(out);
        break;
    }

    // Closing only when "out" is open (because with RMT it can be closed earlier)
    if (out.is_open()) out.close();

    // TODO: add a method to prevent deleting a valid .rmt by accident when a stripped .rmt export was aborted
    if (!saveResult) //failed to save
    {
        DeleteFile(m_filename);
        MessageBox(g_hwnd, "RMT save aborted.\nFile was deleted, beware of data loss!", "Save aborted", MB_ICONEXCLAMATION);
    }
    else	//saved successfully
        g_changes = 0;	//changes have been saved

    SetRMTTitle();
}

void CSong::FileSaveAs()
{
    // Stop the music first
    Stop();
    FILE_LOADSAVE fileDialogParameters;

    CFileDialog dlg(FALSE,
        NULL,
        NULL,
        OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,
        fileDialogParameters.GetFilters()
    );
    dlg.m_ofn.lpstrTitle = "Save song as...";

    if (!g_lastLoadPath_Songs.IsEmpty()) {
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Songs;
    }
    else
    {
        if (!g_defaultSongsPath.IsEmpty()) { dlg.m_ofn.lpstrInitialDir = g_defaultSongsPath; }
    }

    // Specifies the name of the file according to the last saved one
    char filenamebuff[1024];
    if (!m_filename.IsEmpty())
    {
        int pos = m_filename.ReverseFind('\\');
        if (pos < 0) pos = m_filename.ReverseFind('/');
        if (pos >= 0)
        {
            CString s = m_filename.Mid(pos + 1);
            memset(filenamebuff, 0, 1024);
            strcpy(filenamebuff, (char*)(LPCTSTR)s);
            dlg.m_ofn.lpstrFile = filenamebuff;
            dlg.m_ofn.nMaxFile = 1020;	// 4 bytes less, just to make sure ;-)
        }
    }

    // Set the type according to the last save
    if (GetIOType() == SongIOType::RMT) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::RMT; }
    if (GetIOType() == SongIOType::TXT) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::TXT; }
    if (GetIOType() == SongIOType::RMW) { dlg.m_ofn.nFilterIndex = FILE_LOADSAVE::RMW; }

    //if not ok, nothing will be saved
    if (dlg.DoModal() == IDOK)
    {
        // Validate that the file type selection is valid
        auto filterIndex = dlg.m_ofn.nFilterIndex;
        if (!fileDialogParameters.IsValidFilterIndex(filterIndex))
        {
            return;
        }

        m_filename = dlg.GetPathName();
        fileDialogParameters.EnsureFileExtension(m_filename, filterIndex);

        g_lastLoadPath_Songs = GetFilePath(m_filename);

        switch (filterIndex)
        {
        case FILE_LOADSAVE::RMT: // RMT choice
            m_ioType = SongIOType::RMT;
            break;

        case FILE_LOADSAVE::TXT: // TXT choice
            m_ioType = SongIOType::TXT;
            break;

        case FILE_LOADSAVE::RMW: // RWM choice
            m_ioType = SongIOType::RMW;
            break;

        default:
            return;	// Nothing will be saved if no option was chosen
        }

        // If everything went well, the file will now be saved
        FileSave();
    }
}

/// <summary>
/// Popup dialog asking for parameters to init a new song.
/// </summary>
void CSong::FileNew()
{
    // Stop the music first
    Stop();

    // If the last changes were not saved, nothing will be created
    if (WarnUnsavedChanges()) { return; }

    CFileNewDlg dlg;
    if (dlg.DoModal() != IDOK) {
        return;
    }

    // Apply the settings and reset the song data
    g_Tracks.SetMaxTrackLength(dlg.m_maxTrackLength);

    ClearSong((dlg.m_comboMonoOrStereo == 0) ? 4 : 8);
    SetRMTTitle();

    // Automatically create 1 songline of empty patterns
    for (int i = 0; i < g_tracks4_8; i++) m_song[0][i] = i;

    // Set the goto to the first line 
    m_songgo[1] = 0;

    // All channels ON (unmute all)
    g_ChannelControl.SetAllChannelsOn();

    // Delete undo history
    g_Undo.Clear();
}

/// <summary>
/// Import Protracker modules or TMC song files
/// </summary>
void CSong::FileImport()
{
    static int l_lastImportTypeIndex = -1;		// Save the import setting for the next import so that the pre-selected type is preselected

    // Stop the music first
    Stop();

    if (WarnUnsavedChanges()) return;

    FILE_IMPORT fileDialogParamerters;
    CFileDialog dlg(TRUE,
        NULL,
        NULL,
        OFN_HIDEREADONLY,
        fileDialogParamerters.GetFilters()
    );
    dlg.m_ofn.lpstrTitle = "Import Song File";

    if (!g_lastLoadPath_Songs.IsEmpty()) {
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Songs;
    }
    else if (!g_defaultSongsPath.IsEmpty()) {
        dlg.m_ofn.lpstrInitialDir = g_defaultSongsPath;
    }

    if (l_lastImportTypeIndex >= 0) { dlg.m_ofn.nFilterIndex = l_lastImportTypeIndex; }	// Restore the last imported file type

    // If not ok, nothing will be imported
    if (dlg.DoModal() != IDOK) {
        return;
    }

    CString fn = dlg.GetPathName();
    g_lastLoadPath_Songs = GetFilePath(fn);	//direct way

    int filterIndex = dlg.m_ofn.nFilterIndex;
    if (!fileDialogParamerters.IsValidFilterIndex(filterIndex)) {
        return;
    }

    l_lastImportTypeIndex = filterIndex;

    std::ifstream in(fn, std::ios::binary);
    if (!in)
    {
        MessageBox(g_hwnd, "Can't open this file: " + fn, "Open error", MB_ICONERROR);
        return;
    }

    int importResult = 0;
    switch (filterIndex)
    {
    case FILE_IMPORT::MOD: // MOD choice in Dialog
        importResult = ImportMOD(in);
        break;
    case FILE_IMPORT::TMC: // TMC choice in Dialog
        importResult = ImportTMC(in);
        break;
    }

    in.close();
    m_filename = "";

    if (importResult == FALSE)				// Import failed?
        ClearSong(g_tracks4_8);			// Delete everything
    else
    {
        m_speed = m_mainSpeed;			// Init speed

        //window name
        AfxGetApp()->GetMainWnd()->SetWindowText("Imported " + fn);
        //SetRMTTitle();
    }
    // All channels ON (unmute all)
    g_ChannelControl.SetAllChannelsOn();

    // Initialise RMT routine
    ReInitSound();
}

/// <summary>
/// Export song to one of various formats
/// </summary>
void CSong::FileExportAs()
{
    // Stop the music first
    Stop();

    // Verify the integrity of the .rmt module to save first, so it won't be saved if it's not meeting the conditions for it
    if (!TestBeforeFileSave())
    {
        MessageBox(g_hwnd, "Warning!\nNo data has been saved!", "Warning", MB_ICONEXCLAMATION);
        return;
    }

    FILE_EXPORT fileDialogParameters;
    CFileDialog dlg(FALSE,
        NULL,
        NULL,
        OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,
        fileDialogParameters.GetFilters()
    );

    dlg.m_ofn.lpstrTitle = "Export song as...";

    if (!g_lastLoadPath_Songs.IsEmpty())
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Songs;
    else
        if (!g_defaultSongsPath.IsEmpty()) dlg.m_ofn.lpstrInitialDir = g_defaultSongsPath;

    if (m_lastExportIOType == SongIOType::RMTSTRIPPED) dlg.m_ofn.nFilterIndex = FILE_EXPORT::STRIPPED_RMT;
    if (m_lastExportIOType == SongIOType::ASM) dlg.m_ofn.nFilterIndex = FILE_EXPORT::SIMPLE_ASM;
    if (m_lastExportIOType == SongIOType::SAPR) dlg.m_ofn.nFilterIndex = FILE_EXPORT::SAPR;
    if (m_lastExportIOType == SongIOType::LZSS) dlg.m_ofn.nFilterIndex = FILE_EXPORT::LZSS;
    if (m_lastExportIOType == SongIOType::LZSS_SAP) dlg.m_ofn.nFilterIndex = FILE_EXPORT::SAP;
    if (m_lastExportIOType == SongIOType::LZSS_XEX) dlg.m_ofn.nFilterIndex = FILE_EXPORT::XEX;
    if (m_lastExportIOType == SongIOType::ASM_RMTPLAYER) dlg.m_ofn.nFilterIndex = FILE_EXPORT::RELOC_ASM;
    if (m_lastExportIOType == SongIOType::WAV) dlg.m_ofn.nFilterIndex = FILE_EXPORT::FILTER_IDX_WAV;

    // If not ok, nothing will be saved
    if (dlg.DoModal() == IDOK)
    {
        CString fn = dlg.GetPathName();
        int formatChoiceIndexFromDialog = dlg.m_ofn.nFilterIndex;

        if (!fileDialogParameters.IsValidFilterIndex(formatChoiceIndexFromDialog))
        {
            return;
        }

        fileDialogParameters.EnsureFileExtension(fn, formatChoiceIndexFromDialog);

        g_lastLoadPath_Songs = GetFilePath(fn);

        // Try and create the output file
        std::ofstream out(fn, std::ios::binary);
        if (!out)
        {
            MessageBox(g_hwnd, "Can't create this file: " + fn, "Export error", MB_ICONERROR);
            return;
        }

        bool exportResult = false;
        switch (formatChoiceIndexFromDialog)
        {
        case FILE_EXPORT::STRIPPED_RMT:
            m_lastExportIOType = SongIOType::RMTSTRIPPED;
            break;

        case FILE_EXPORT::SIMPLE_ASM:
            m_lastExportIOType = SongIOType::ASM;
            break;

        case FILE_EXPORT::SAPR:
            m_lastExportIOType = SongIOType::SAPR;
            break;

        case FILE_EXPORT::LZSS:
            m_lastExportIOType = SongIOType::LZSS;
            break;

        case FILE_EXPORT::SAP:
            m_lastExportIOType = SongIOType::LZSS_SAP;
            break;

        case FILE_EXPORT::XEX:
            m_lastExportIOType = SongIOType::LZSS_XEX;
            break;

        case FILE_EXPORT::RELOC_ASM:	// Relocatable ASM for RMTPlayer
            m_lastExportIOType = SongIOType::ASM_RMTPLAYER;
            break;

        case FILE_EXPORT::FILTER_IDX_WAV:
            m_lastExportIOType = SongIOType::WAV;
            break;

        }

        // Save the file using the set parameters 
        exportResult = ExportV2(*this, out, m_lastExportIOType, (LPCTSTR)fn);

        // File should have been successfully saved, make sure to close it
        out.close();

        // TODO: add a method to prevent accidental deletion of valid files
        if (!exportResult)
        {
            DeleteFile(fn);
            CString message;
            message.Format("Incomplete export file '%s' was deleted.", fn);
            MessageBox(g_hwnd, message, "Export aborted", MB_ICONEXCLAMATION);
        }
    }
}

/// <summary>
/// Save an instrument as RTI (binary format)
/// </summary>
void CSong::FileInstrumentSave()
{
    // Stop the music first
    Stop();

    CFileDialog dlg(FALSE,
        NULL,
        NULL,
        OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,
        "RMT instrument file (*.rti)|*.rti||");
    dlg.m_ofn.lpstrTitle = "Save RMT instrument file";

    if (!g_lastLoadPath_Instruments.IsEmpty())
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Instruments;
    else
        if (!g_defaultInstrumentsPath.IsEmpty()) dlg.m_ofn.lpstrInitialDir = g_defaultInstrumentsPath;

    // If it's not ok, nothing is saved
    if (dlg.DoModal() == IDOK)
    {
        CString fn = dlg.GetPathName();
        CString ext = fn.Right(4).MakeLower();
        if (ext != ".rti") fn += ".rti";

        g_lastLoadPath_Instruments = GetFilePath(fn);

        std::ofstream ou(fn, std::ios::binary);
        if (!ou)
        {
            MessageBox(g_hwnd, "Can't create the instrument file: " + fn, "Write error", MB_ICONERROR);
            return;
        }

        g_Instruments.SaveInstrument(m_activeinstr, ou, InstrumentIOType::RTI);

        ou.close();
    }
}

/// <summary>
/// Load instrument definition RTI (binary format)
/// </summary>
void CSong::FileInstrumentLoad()
{
    // Stop the music first
    Stop();

    CFileDialog dlg(TRUE,
        NULL,
        NULL,
        OFN_HIDEREADONLY,
        "RMT instrument files (*.rti)|*.rti||");
    dlg.m_ofn.lpstrTitle = "Load RMT instrument file";

    if (!g_lastLoadPath_Instruments.IsEmpty())
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Instruments;
    else
        if (!g_defaultInstrumentsPath.IsEmpty()) dlg.m_ofn.lpstrInitialDir = g_defaultInstrumentsPath;

    // If it's not ok, nothing will be loaded
    if (dlg.DoModal() == IDOK)
    {
        g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA, 1);

        CString fn = dlg.GetPathName();
        g_lastLoadPath_Instruments = GetFilePath(fn);	//direct way

        std::ifstream in(fn, std::ios::binary);
        if (!in)
        {
            MessageBox(g_hwnd, "Can't open this file: " + fn, "Open error", MB_ICONERROR);
            return;
        }

        int loadState = g_Instruments.LoadInstrument(m_activeinstr, in, InstrumentIOType::RTI);
        in.close();

        if (!loadState)
        {
            MessageBox(g_hwnd, "Failed to load RTI format (standard version 0)", "Data error", MB_ICONERROR);
            return;
        }
    }
}

/// <summary>
/// Save the active track as a text file
/// </summary>
void CSong::FileTrackSave()
{
    int track = SongGetActiveTrack();
    if (track < 0 || track >= TRACKSNUM) return;

    // Stop the music first
    Stop();

    CFileDialog dlg(FALSE,
        NULL,
        NULL,
        OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,
        "TXT track file (*.txt)|*.txt||");
    dlg.m_ofn.lpstrTitle = "Save TXT track file";

    if (!g_lastLoadPath_Tracks.IsEmpty())
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Tracks;
    else
        if (!g_defaultTracksPath.IsEmpty()) dlg.m_ofn.lpstrInitialDir = g_defaultTracksPath;

    //if not ok, nothing will be saved
    if (dlg.DoModal() == IDOK)
    {
        CString fn = dlg.GetPathName();
        CString ext = fn.Right(4).MakeLower();
        if (ext != ".txt") fn += ".txt";

        g_lastLoadPath_Tracks = GetFilePath(fn);

        std::ofstream ou(fn);	// text mode by default
        if (!ou)
        {
            MessageBox(g_hwnd, "Can't create this file: " + fn, "Write error", MB_ICONERROR);
            return;
        }

        g_Tracks.SaveTrack(track, ou, SongIOType::TXT);

        ou.close();
    }
}

/// <summary>
/// Load a text track
/// </summary>
void CSong::FileTrackLoad()
{
    int track = SongGetActiveTrack();
    if (track < 0 || track >= TRACKSNUM) return;

    // Stop the music first
    Stop();

    CFileDialog dlg(TRUE,
        NULL,
        NULL,
        OFN_HIDEREADONLY,
        "TXT track files (*.txt)|*.txt||");
    dlg.m_ofn.lpstrTitle = "Load TXT track file";

    if (!g_lastLoadPath_Tracks.IsEmpty())
        dlg.m_ofn.lpstrInitialDir = g_lastLoadPath_Tracks;
    else
        if (!g_defaultTracksPath.IsEmpty())	dlg.m_ofn.lpstrInitialDir = g_defaultTracksPath;

    // If not ok, nothing will be loaded
    if (dlg.DoModal() == IDOK)
    {
        g_Undo.ChangeTrack(0, 0, UETYPE_TRACKSALL, 1);

        CString fn = dlg.GetPathName();
        g_lastLoadPath_Tracks = GetFilePath(fn);

        std::ifstream in(fn);	// text mode by default
        if (!in)
        {
            MessageBox(g_hwnd, "Can't open this file: " + fn, "Open error", MB_ICONERROR);
            return;
        }

        char line[1025];
        int nt = 0;				// number of tracks
        int type = 0;			// type when loading multiple tracks
        while (NextSegment(in)) // will therefore look for the beginning of the next segment "["
        {
            in.getline(line, 1024);
            Trimstr(line);
            if (strcmp(line, "TRACK]") == 0) nt++;
        }

        if (nt == 0)
        {
            MessageBox(g_hwnd, "Sorry, this file doesn't contain any track in TXT format", "Data error", MB_ICONERROR);
            return;
        }
        else if (nt > 1)
        {
            CTracksLoadDlg dlg;
            dlg.m_trackfrom = track;
            dlg.m_tracknum = nt;
            if (dlg.DoModal() != IDOK) return;
            type = dlg.m_radio;
        }

        in.clear();			// Reset the flag from the end
        in.seekg(0);		// Again at the beginning
        int nr = 0;
        NextSegment(in);	// Move after the first "["
        while (!in.eof())
        {
            in.getline(line, 1024);
            Trimstr(line);
            if (strcmp(line, "TRACK]") == 0)
            {
                int tt = (type == 0) ? track : -1;
                if (g_Tracks.LoadTrack(tt, in, SongIOType::TXT))
                {
                    nr++;	//number of tracks loaded
                    if (type == 0)
                    {
                        track++;	//shift by 1 to load the next track
                        if (track >= TRACKSNUM)
                        {
                            MessageBox(g_hwnd, "Track's maximum number reached.\nLoading aborted.", "Error", MB_ICONERROR);
                            break;
                        }
                    }
                }
            }
            else
                NextSegment(in);	//move to the next "["
        }
        in.close();

        if (nr == 0 || nr > 1) // if it has not found any or more than 1
        {
            CString s;
            s.Format("%i track(s) loaded.", nr);
            MessageBox(g_hwnd, s, "Track(s) loading finished.", MB_ICONINFORMATION);
        }
    }
}

// CSong::SaveRMW() is implemented in SongEditing.cpp.

// CSong::LoadRMW() is implemented in SongEditing.cpp.

/// <summary>
/// Save the song as a text file
/// </summary>
/// <param name="ou">Output stream</param>
/// <returns></returns>
// CSong::SaveTxt() is implemented in SongEditing.cpp.

/// <summary>
/// Load a text RMT file
/// </summary>
/// <param name="in">Input stream</param>
/// <returns>Returns true if the file was loaded, does not mean the resultant data is valid</returns>
// CSong::LoadTxt() is implemented in SongEditing.cpp.



/// <summary>
/// Validate the song data to make sure that
/// - a RMT module could be created [Error]
/// - the song is not empty [Error]
/// - a goto statement does not go past the end of the song [Error]
/// - there is no recursive goto [Error]
/// - a goto follows another goto (which would be a waste) [Warning]
/// - Check that there is not more then one continuous blank song line [Warning]
/// - there is a goto at the end of the song, goto 0 is used by default [Warning]
/// </summary>
/// <returns>true if the song data is valid</returns>
bool CSong::TestBeforeFileSave()
{
    // It is performed on Export (everything except RMW) before the target file is overwritten
    // So if it returns 0, the export is terminated and the file is not overwritten

    // Try to create a module
    unsigned char mem[65536];
    int adr_module = 0x4000;
    BYTE instrumentSavedFlags[INSTRSNUM];
    BYTE trackSavedFlags[TRACKSNUM];

    if (MakeModule(mem, adr_module, SongIOType::RMT, instrumentSavedFlags, trackSavedFlags) < 0)
        return false;	// Dump out if the module could not be created

    // and now it will be checked whether the song ends with GOTO line and if there is no GOTO on GOTO line
    CString errmsg, wrnmsg, s;
    int trx[SONGLEN];
    int i, j, r, go, last = -1, tr = 0, empty = 0;

    for (i = 0; i < SONGLEN; i++)
    {
        if (m_songgo[i] >= 0)
        {
            trx[i] = 2;
            last = i;
        }
        else
        {
            trx[i] = 0;
            for (j = 0; j < g_tracks4_8; j++)
            {
                if (m_song[i][j] >= 0 && m_song[i][j] < TRACKSNUM)
                {
                    trx[i] = 1;
                    last = i; //tracks
                    tr++;
                    break;
                }
            }
        }
    }

    if (last < 0)
    {
        errmsg += "Error: Song is empty.\n";
    }

    for (i = 0; i <= last; i++)
    {
        if (m_songgo[i] >= 0)
        {
            //there is a goto line
            go = m_songgo[i];	//where is goto set to?
            if (go > last)
            {
                s.Format("Error: Song line [%02X]: Go to line over last used song line.\n", i);
                errmsg += s;
            }
            if (m_songgo[go] >= 0)
            {
                s.Format("Error: Song line [%02X]: Recursive \"go to line\" to \"go to line\".\n", i);
                errmsg += s;
            }
            if (i > 0 && m_songgo[i - 1] >= 0)
            {
                s.Format("Warning: Song line [%02X]: More \"go to line\" on subsequent lines.\n", i);
                wrnmsg += s;
            }
            goto TestTooManyEmptyLines;
        }
        else
        {
            //are there tracks or empty lines?
            if (trx[i] == 0)
                empty++;
            else
            {
            TestTooManyEmptyLines:
                if (empty > 1)
                {
                    s.Format("Warning: Song lines [%02X-%02X]: Too many empty song lines (%i) waste memory.\n", i - empty, i - 1, empty);
                    wrnmsg += s;
                }
                empty = 0;
            }
        }
    }

    if (trx[last] == 1)
    {
        char gotoline[140];
        sprintf(gotoline, "Song line[%02X]: Unexpected end of song.\nYou have to use \"go to line\" at the end of song.\n\nSong line [00] will be used by default.", last + 1);
        MessageBox(g_hwnd, gotoline, "Warning", MB_ICONINFORMATION);
        m_songgo[last + 1] = 0;	//force a goto line to the first track line
    }

    // If the warning or error messages aren't empty, something did happen
    if (!errmsg.IsEmpty() || !wrnmsg.IsEmpty())
    {
        // If there are warnings without errors, the choice is left to ignore them
        if (errmsg.IsEmpty())
        {
            wrnmsg += "\nIgnore warnings and save anyway?";
            r = MessageBox(g_hwnd, wrnmsg, "Warnings", MB_YESNO | MB_ICONQUESTION);
            if (r == IDYES) return true;
            return false;
        }
        // Otherwise, if there are any errors, always return failure
        MessageBox(g_hwnd, errmsg + wrnmsg, "Errors", MB_ICONERROR);
        return false;
    }

    return true;
}

// CSong::ExportV2() is implemented in SongExportV2.cpp.




/// <summary>
/// Load a RMT file.
/// Parse the header, tracks, songs lines and instruments
/// </summary>
/// <param name="in">Input stream</param>
/// <returns>true if the load went ok</returns>
// CSong::LoadRMT() is implemented in SongEditing.cpp.

