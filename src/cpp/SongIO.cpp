// The file formats of the song: the module codec (MakeModule()/
// DecodeModule(), wrappers over CRmtModuleCodec) and the RMT/TXT/RMW
// load/save methods, all still CSong members - C++ spreads the class over
// translation units. They moved here from SongEditing.cpp and SongCore.cpp
// with plan 45, mirroring the Java port's SongIO class, so the two
// programs' file maps stay in sync. IO_Song.cpp keeps the MFC file-dialog
// wrappers around these methods; this file stays free of Global.h's wider
// dependency graph so the test binary can link it, which is why the
// characterization effort had parked the codec in SongEditing.cpp.
//
// SongIO.h and this file existed before the move, as a reservation: the
// header holds the file open/save/import/export dialog filter classes and
// an (empty) SongIO class, and this file was a one-line include of it.

#include "StdAfx.h"

#include "SongIO.h" // the file-dialog filter classes that had this file's name reserved

#include "Song.h"

#include "AtariIO.h"
#include "IOHelpers.h"
#include "RmtModuleCodec.h"
#include "RmtVersion.h"
#include "Messages.h"
#include <fstream>

extern CInstruments g_Instruments;
extern CTracks g_Tracks;
extern WORD g_rmtstripped_adr_module;
extern BOOL volatile g_rmtroutine;
extern BOOL g_changes;

// DEFINE_MAINPARAMS (below, used by SaveRMW()/LoadRMW()) takes the address
// of each of these - all plain ints/enums/bools with no constructor or
// hazard of their own. The macro is the C++ twin of the Java port's
// RMW_UI_PARAMS block in SongIO.java.
extern Part g_activepart;
extern Part g_active_ti;
extern EditMode volatile g_prove;
extern BOOL volatile g_respectvolume;
extern int g_trackLinePrimaryHighlight;
extern BOOL g_tracklinealtnumbering;
extern BOOL g_displayflatnotes;
extern BOOL g_usegermannotation;
extern int g_cursoractview;
extern KeyboardLayout g_keyboard_layout;
extern BOOL g_keyboard_escresetatarisound;
extern BOOL g_keyboard_swapenter;
extern BOOL g_keyboard_playautofollow;
extern BOOL g_keyboard_updowncontinue;
extern BOOL g_keyboard_RememberOctavesAndVolumes;

#define RMWMAINPARAMSCOUNT 31 //
#define DEFINE_MAINPARAMS int* mainparams[RMWMAINPARAMSCOUNT] = {                           \
                              &channels,                                                    \
                              (int*)&m_speed, (int*)&m_mainSpeed, (int*)&m_instrumentSpeed, \
                              (int*)&m_songactiveline, (int*)&m_songplayline,               \
                              (int*)&m_trackactiveline, (int*)&m_trackplayline,             \
                              (int*)&g_activepart, (int*)&g_active_ti,                      \
                              (int*)&g_prove, (int*)&g_respectvolume,                       \
                              &g_trackLinePrimaryHighlight,                                 \
                              &g_tracklinealtnumbering,                                     \
                              &g_displayflatnotes,                                          \
                              &g_usegermannotation,                                         \
                              &g_cursoractview,                                             \
                              (int*)&g_keyboard_layout,                                     \
                              &g_keyboard_escresetatarisound,                               \
                              &g_keyboard_swapenter,                                        \
                              &g_keyboard_playautofollow,                                   \
                              &g_keyboard_updowncontinue,                                   \
                              &g_keyboard_RememberOctavesAndVolumes,                        \
                              &g_keyboard_escresetatarisound,                               \
                              &m_trackactivecol, &m_trackactivecur,                         \
                              &m_activeinstr, &m_volume, &m_octave,                         \
                              (int*)&m_infoact, &m_songnamecur}


// Encodes the song into the RMT module format at mem[addr...] (per iotype's
// save-all-vs-save-used rule) and returns the address of the first byte past
// the module, or -1 if the song does not make a module - a track too
// event-dense to encode, a speed outside the header's range, or a module that
// does not fit at addr. Since plans/60_RMT_MODULE_CONTAINER_PLAN.md M6 a
// wrapper over the container: CRmtModuleCodec::FromSong() decides what is
// saved and CRmtModule::Write() lays it out. The flags mark the instruments
// and tracks the module holds.
int CSong::MakeModule(unsigned char* mem, int addr, SongIOType iotype, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags) {
    memset(instrumentSavedFlags, 0, INSTRSNUM);
    memset(trackSavedFlags, 0, TRACKSNUM);
    try {
        CRmtModule module = CRmtModuleCodec::FromSong(*this, iotype);
        int endOfModule = module.Write(mem, CMemory::SIZE, addr);
        for (const auto& entry : module.GetInstruments()) {
            instrumentSavedFlags[entry.first] = 1;
        }
        for (const auto& entry : module.GetTracks()) {
            trackSavedFlags[entry.first] = 1;
        }
        return endOfModule; // the address of the first byte past the last one used
    } catch (const CRmtModule::FormatException& e) {
        SendErrorMessage("Internal format problem.", CString("The song cannot be coded to the RMT module format.\n\n") + e.what());
        return -1;
    }
}

// Decodes the RMT module at mem[fromAddr..endAddr) into the song and returns
// its format version, 0 on failure. Since plans/60_RMT_MODULE_CONTAINER_PLAN.md
// M6 a wrapper over the container - CRmtModule::Parse(), then
// CRmtModuleCodec::ToSong() - so a module the container rejects, format
// version 0 included, leaves the song as it was.
int CSong::DecodeModule(unsigned char* mem, int fromAddr, int endAddr, BYTE* instrumentLoadedFlags, BYTE* trackLoadedFlags) {
    memset(instrumentLoadedFlags, 0, INSTRSNUM);
    memset(trackLoadedFlags, 0, TRACKSNUM);
    try {
        CRmtModule module = CRmtModule::Parse(mem, fromAddr, endAddr);
        CRmtModuleCodec::ToSong(module, *this);
        for (const auto& entry : module.GetInstruments()) {
            instrumentLoadedFlags[entry.first] = 1;
        }
        for (const auto& entry : module.GetTracks()) {
            trackLoadedFlags[entry.first] = 1;
        }
        return module.GetFormatVersion();
    } catch (const CRmtModule::FormatException&) {
        return 0;
    }
}

bool CSong::SaveRMW(std::ostream& ou) {
    CString version = RMT_VERSION_STRING;
    ou << (unsigned char*)(LPCSTR)version << std::endl;
    //
    ou.write((char*)m_songname, sizeof(m_songname));
    //
    int channels = m_channels;
    DEFINE_MAINPARAMS;

    int p = RMWMAINPARAMSCOUNT; // Number of stored parameters
    ou.write((char*)&p, sizeof(p)); // Write the number of main parameters
    for (int i = 0; i < p; i++) {
        // Each parameter is a 4-byte int (or an int-backed enum/BOOL of the
        // same size) - sizeof(mainparams[0]) is wrong here, since
        // mainparams[i]'s own type is int*, not int: on the 32-bit builds
        // this project originally shipped as, sizeof(int*) and sizeof(int)
        // were both 4, masking the bug; on this 64-bit build sizeof(int*)
        // is 8, silently writing (and, in LoadRMW, reading) 4 bytes of
        // adjacent memory past each parameter.
        ou.write((char*)mainparams[i], sizeof(int));
    }

    // Write a complete song and songgo
    ou.write((char*)m_song, sizeof(m_song));
    ou.write((char*)m_songgo, sizeof(m_songgo));

    g_Instruments.SaveAll(ou, InstrumentIOType::RMW);
    g_Tracks.SaveAll(ou, SongIOType::RMW);

    return true;
}

bool CSong::LoadRMW(std::istream& in) {
    ClearSong(8); // Always clear 8 tracks

    CString version = RMT_VERSION_STRING;
    char filever[256];
    in.getline(filever, 255);
    if (strcmp((char*)(LPCTSTR)version, filever) != 0) {
        SendErrorMessage("Load error", CString("Incorrect version: ") + filever);
        return false;
    }
    //
    in.read((char*)m_songname, sizeof(m_songname));
    //
    int channels = m_channels;
    DEFINE_MAINPARAMS;
    int p = 0;
    in.read((char*)&p, sizeof(p)); //read the number of main parameters
    for (int i = 0; i < p; i++) {
        // See the matching comment in SaveRMW() for why this is sizeof(int),
        // not sizeof(mainparams[0]).
        in.read((char*)mainparams[i], sizeof(int));
    }
    if (channels != Channels::MONO && channels != Channels::STEREO) {
        return false; // a channel count no tracker driver plays
    }
    SetChannels(channels);

    // Read the complete song and songgo
    in.read((char*)m_song, sizeof(m_song));
    in.read((char*)m_songgo, sizeof(m_songgo));

    g_Instruments.LoadAll(in, InstrumentIOType::RMW);
    g_Tracks.LoadAll(in, SongIOType::RMW);

    return true;
}

bool CSong::SaveTxt(std::ostream& ou) {
    CString s, nambf;
    char bf[16];
    nambf = m_songname;
    nambf.TrimRight();
    s.Format("[MODULE]\nRMT: %X\nNAME: %s\nMAXTRACKLEN: %02X\nMAINSPEED: %02X\nINSTRSPEED: %X\nVERSION: %02X\n", m_channels, (LPCTSTR)nambf, g_Tracks.GetMaxTrackLength(), m_mainSpeed, m_instrumentSpeed, RMTFormatVersion::V1);
    ou << s << "\n"; //gap
    ou << "[SONG]\n";
    int i, j;
    // Looking for the length of the song
    int songLength = -1;
    for (i = 0; i < SONGLEN; i++) {
        if (m_songgo[i] >= 0) {
            songLength = i;
            continue;
        }
        for (j = 0; j < m_channels; j++) {
            if (m_song[i][j] >= 0 && m_song[i][j] < TRACKSNUM) {
                songLength = i;
                break;
            }
        }
    }

    // Write the song
    for (i = 0; i <= songLength; i++) {
        if (m_songgo[i] >= 0) {
            s.Format("Go to line %02X\n", m_songgo[i]);
            ou << s;
            continue;
        }
        for (j = 0; j < m_channels; j++) {
            int t = m_song[i][j];
            if (t >= 0 && t < TRACKSNUM) {
                bf[0] = CharH4(t);
                bf[1] = CharL4(t);
            } else {
                bf[0] = bf[1] = '-';
            }
            bf[2] = 0;
            ou << bf;
            if (j + 1 == m_channels) {
                ou << "\n"; //for the last end of the line
            } else {
                ou << " "; //between them
            }
        }
    }

    ou << "\n"; // gap

    // Now save the instruments and tracks to the output
    g_Instruments.SaveAll(ou, InstrumentIOType::TXT);
    g_Tracks.SaveAll(ou, SongIOType::TXT);

    return true;
}

bool CSong::LoadTxt(std::istream& in) {
    ClearSong(8); // Always clear 8 tracks

    g_Tracks.InitTracks();

    char b;
    char line[1025];
    // Read until the first "[" is found. This indicates a segment [.....]
    NextSegment(in);

    while (!in.eof()) {
        in.getline(line, 1024); // Read the rest of the line
        Trimstr(line); // Get rid of /r/n at the end

        if (strcmp(line, "MODULE]") == 0) {
            // [MODULE]
            while (!in.eof()) {
                // Check for next segment start '['
                in.read((char*)&b, 1);
                if (b == '[') {
                    break;
                }
                if (b == '\n') {
                    // A blank line (SaveTxt() writes one as a "gap" before
                    // the next segment) - not real content, so it must not
                    // be handed to getline() below: doing so would swallow
                    // the following line whole, silently absorbing the '['
                    // that starts the next segment instead of recognizing
                    // it as a boundary (see GitHub issue #21).
                    continue;
                }
                // Not a segment start so save the read character and get the rest of the line
                line[0] = b;
                in.getline(line + 1, 1024);

                // Split on the ": " (COLON + SPACE) point
                char* value = strstr(line, ": ");
                if (value) {
                    value[1] = 0; // Zero terminate the string on the left
                    value += 2; // move to the first character after the space
                } else {
                    continue;
                }

                // Process each of the possible commands in a [MODULE]
                if (strcmp(line, "RMT:") == 0) {
                    // RMT version indicator: 4 or 8
                    int v = Hexstr(value, 2);
                    if (v <= Channels::MONO) {
                        v = Channels::MONO;
                    } else {
                        v = Channels::STEREO;
                    }
                    SetChannels(v);
                } else if (strcmp(line, "NAME:") == 0) {
                    // Set the name of the song.
                    Trimstr(value);
                    memset(m_songname, ' ', SONG_NAME_MAX_LEN);
                    int lname = SONG_NAME_MAX_LEN;
                    if (strlen(value) <= SONG_NAME_MAX_LEN) {
                        lname = (int)strlen(value);
                    }
                    strncpy(m_songname, value, lname);
                } else if (strcmp(line, "MAXTRACKLEN:") == 0) {
                    // Set how long a track is: MAXTRACKLEN: 00-FF
                    int v = Hexstr(value, 2);
                    if (v == 0) {
                        v = 256;
                    }
                    g_Tracks.SetMaxTrackLength(v);
                    g_Tracks.InitTracks(); // Reinitialize
                } else if (strcmp(line, "MAINSPEED:") == 0) {
                    // Set the play speed: MAINSPEED: 01-FF
                    int v = Hexstr(value, 2);
                    if (v > 0) {
                        m_mainSpeed = v;
                    }
                } else if (strcmp(line, "INSTRSPEED:") == 0) {
                    // Set the instrument speed: INSTRSPEED: 01-FF
                    int v = Hexstr(value, 1);
                    if (v > 0) {
                        m_instrumentSpeed = v;
                    }
                } else if (strcmp(line, "VERSION:") == 0) {
                    // The version number is not needed for TXT yet, because it only selects the parameters it knows
                }
            }
        } else if (strcmp(line, "SONG]") == 0) {
            // [SONG]
            int idx, i;
            for (idx = 0; !in.eof() && idx < SONGLEN; idx++) {
                // Read the song line. Dump out if its the next section
                memset(line, 0, 32);
                in.read((char*)&b, 1);
                if (b == '[') {
                    break;
                }
                if (b == '\n') {
                    // A blank line (SaveTxt() writes one as a "gap" before
                    // the next segment) - see the [MODULE] loop above for
                    // why this must not be handed to getline(). idx-- so
                    // the for loop's own idx++ leaves it unchanged, since
                    // this iteration didn't consume a real song line.
                    idx--;
                    continue;
                }
                line[0] = b;
                in.getline(line + 1, 1024);

                // The line go be one of two types
                // "Go to line XX"
                // "-- -- -- --" or "-- -- -- -- -- -- -- --"
                if (strncmp(line, "Go to line ", 11) == 0) {
                    int go = Hexstr(line + 11, 2);
                    if (go >= 0 && go < SONGLEN) {
                        m_songgo[idx] = go;
                    }
                    continue;
                }
                for (i = 0; i < m_channels; i++) {
                    // Parse the track
                    int track = Hexstr(line + i * 3, 2);
                    if (track >= 0 && track < TRACKSNUM) {
                        m_song[idx][i] = track;
                    }
                }
            }
        } else if (strcmp(line, "INSTRUMENT]") == 0) {
            // [INSTRUMNENT]
            // Pass the instrument loading to the CInstruments class
            g_Instruments.LoadInstrument(-1, in, InstrumentIOType::TXT); //-1 => retrieve the instrument number from the TXT source
        } else if (strcmp(line, "TRACK]") == 0) {
            // [TRACK]
            // Pass the track loading to the CTracks class
            g_Tracks.LoadTrack(-1, in, SongIOType::TXT); //-1 => retrieve the track number from TXT source
        } else {
            NextSegment(in); // Look for the beginning of the next segment
        }
    }

    return true;
}

bool CSong::LoadRMT(std::istream& in) {
    byte mem[RAM_SIZE]{};
    WORD fromAddr, toAddr;
    WORD bto_mainblock;

    BYTE instrumentLoadedFlags[INSTRSNUM];
    BYTE trackLoadedFlags[TRACKSNUM];

    int len, i, idx, k;
    int loadResult;

    // RMT header+song data is the first main block of an RMT song
    // There has to be 1 binary block with the header, song, instrument and track data
    // Optional block with instrument and song name information
    len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);

    if (len > 0) {
        loadResult = DecodeModule(mem, fromAddr, toAddr + 1, instrumentLoadedFlags, trackLoadedFlags);
        if (loadResult == 0) {
            SendErrorMessage("Open error", "Bad RMT data format or old tracker version.");
            return false;
        }
        // The main block of the module is OK => take its boot address
        g_rmtstripped_adr_module = fromAddr;
        bto_mainblock = toAddr;
    } else {
        SendErrorMessage("Open error", "Corrupted file or unsupported format version.");
        return false; // Did not retrieve any data in the first block
    }

    // RMT - now read the second block with names
    len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);
    if (len < 1) {
        CString msg;
        msg.Format("This file appears to be a stripped RMT module.\nThe song and instruments names are missing.\n\nMemory addresses: $%04X - $%04X.", g_rmtstripped_adr_module, bto_mainblock);
        SendInformationMessage("Info", (LPCTSTR)msg);
        return true;
    }

    char ch;
    // Parse the song name (until we hit the terminating zero)
    for (idx = 0; idx < SONG_NAME_MAX_LEN && (ch = mem[fromAddr + idx]); idx++) {
        m_songname[idx] = ch;
    }

    for (k = idx; k < SONG_NAME_MAX_LEN; k++) {
        m_songname[k] = ' '; // fill in the gaps
    }

    int addrInstrumentNames = fromAddr + idx + 1; // +1 that's the zero behind the name
    for (i = 0; i < INSTRSNUM; i++) {
        // Check if this instrument has been loaded
        if (instrumentLoadedFlags[i]) {
            // Yes its loaded, parse its name
            for (idx = 0; idx < INSTRUMENT_NAME_MAX_LEN && (ch = mem[addrInstrumentNames + idx]); idx++) {
                //g_Instruments.m_instr[i].name[idx] = ch;
                g_Instruments.GetName(i)[idx] = ch;
            }

            for (k = idx; k < INSTRUMENT_NAME_MAX_LEN; k++) { //g_Instruments.m_instr[i].name[k] = ' '; //fill in the gaps
                g_Instruments.GetName(i)[k] = ' '; // Fill in the gaps
            }

            // Move to source of the next instrument's name
            addrInstrumentNames += idx + 1; //+1 is zero behind the name
        }
    }

    return true;
}
