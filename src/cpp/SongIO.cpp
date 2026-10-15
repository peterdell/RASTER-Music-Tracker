// The file formats of the song: the module codec (MakeModule()/
// DecodeModule() with SongToAta()/AtaToSong()) and the RMT/TXT/RMW
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
#include "RmtVersion.h"
#include "Messages.h"
#include <fstream>

extern CInstruments g_Instruments;
extern CTracks g_Tracks;
extern int g_tracks4_8;
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
                              &g_tracks4_8,                                                 \
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


int CSong::SongToAta(unsigned char* dest, int max, int adr) {
    int j;
    int apos = 0, len = 0, go = -1;
    ;
    for (int sline = 0; sline < SONGLEN; sline++) {
        apos = sline * g_tracks4_8;
        if (apos + g_tracks4_8 > max) {
            return len; //if it had a buffer overflow
        }

        if ((go = m_songgo[sline]) >= 0) {
            //there is a goto line
            dest[apos] = 254; //go command
            dest[apos + 1] = go; //number where to jump
            WORD goadr = adr + (go * g_tracks4_8);
            dest[apos + 2] = goadr & 0xff; //low byte
            dest[apos + 3] = (goadr >> 8); //high byte
            if (g_tracks4_8 > 4) {
                for (int j = 4; j < g_tracks4_8; j++) {
                    dest[apos + j] = 255; //to make sure this is the correct line
                }
            }
            len = sline * g_tracks4_8 + 4; //this is the end for now (goto has 4 bytes for 8 tracks)
        } else {
            //there are track numbers
            for (int i = 0; i < g_tracks4_8; i++) {
                j = m_song[sline][i];
                if (j >= 0 && j < TRACKSNUM) {
                    dest[apos + i] = j;
                    len = (sline + 1) * g_tracks4_8; //this is the end for now
                } else {
                    dest[apos + i] = 255; //--
                }
            }
        }
    }
    return len;
}

BOOL CSong::AtaToSong(unsigned char* sour, int len, int adr) {
    int i = 0;
    int col = 0, line = 0;
    unsigned char b;
    while (i < len) {
        b = sour[i];
        if (b >= 0 && b < TRACKSNUM) {
            m_song[line][col] = b;
        } else if (b == 254 && col == 0) { //go command only in 0 track
            //m_songgo[line]=sour[i+1];  //the driver took it by the number in channel 1
            //but more importantly, it's a vector, so it's better done that way
            int ptr = sour[i + 2] | (sour[i + 3] << 8); //goto vector
            int go = (ptr - adr) / g_tracks4_8;
            if (go >= 0 && go < (len / g_tracks4_8) && go < SONGLEN) {
                m_songgo[line] = go;
            } else {
                m_songgo[line] = 0; //place of invalid jump and jump to line 0
            }
            i += g_tracks4_8;
            if (i >= len) {
                return 1; //this is the end of goto
            }
            line++;
            if (line >= SONGLEN) {
                return 1;
            }
            continue;
        } else {
            m_song[line][col] = -1;
        }

        col++;
        if (col >= g_tracks4_8) {
            line++;
            if (line >= SONGLEN) {
                return 1; //so that it does not overflow
            }
            col = 0;
        }
        i++;
    }
    return 1;
}

int CSong::MakeModule(unsigned char* mem, int addr, SongIOType iotype, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags) {
    int i, j;
    TTrack* tr;

    // Clear the instrument and tracks used flags
    memset(instrumentSavedFlags, 0, INSTRSNUM);
    memset(trackSavedFlags, 0, TRACKSNUM);

    // Write out the RMT header (part 1)
    // 0: RMT4 or RMT8
    // 4: Track length
    // 5: Song speed
    // 6: Instrument speed
    // 7: RMT version (1 for now)
    strncpy((char*)(mem + addr), "RMT", 3);
    mem[addr + 3] = g_tracks4_8 + '0'; // 4 or 8
    mem[addr + 4] = g_Tracks.GetMaxTrackLength() & 0xff;
    mem[addr + 5] = m_mainSpeed & 0xff;
    mem[addr + 6] = m_instrumentSpeed; // 1-4 player calls per frame
    mem[addr + 7] = RMTFormatVersion::V1; // RMT format version number

    // Note:
    // When saving in RMT format ALL non-empty tracks and non-empty instruments will be stored
    // In other formats only the USED tracks and USED instruments will be stored

    MarkTF_USED(trackSavedFlags); // Mark all tracks as used
    if (iotype == SongIOType::RMT) {
        MarkTF_NOEMPTY(trackSavedFlags); // In addition to the used ones, all non-empty tracks are added to the RMT, all non-empty tracks
    }

    // Mark all used instruments in the tracks that will be saved
    for (i = 0; i < TRACKSNUM; i++) {
        if (trackSavedFlags[i] > 0) {
            tr = g_Tracks.GetTrack(i);
            for (j = 0; j < tr->len; j++) {
                if (g_Tracks.IsValidInstrument(tr->instr[j])) {
                    instrumentSavedFlags[tr->instr[j]] = IF_USED;
                }
            }
        }
    }

    if (iotype == SongIOType::RMT) {
        // In addition to the instruments used in the tracks that are in the song, all non-empty instruments are stored in the RMT
        for (i = 0; i < INSTRSNUM; i++) {
            if (g_Instruments.CalculateNotEmpty(i)) {
                instrumentSavedFlags[i] |= IF_NOEMPTY;
            }
        }
    }

    //---
    // Find how many tracks and instruments to save
    int numTracks = 0;
    for (i = TRACKSNUM - 1; i >= 0; i--) {
        if (trackSavedFlags[i] > 0) {
            numTracks = i + 1;
            break;
        }
    }

    int numInstruments = 0;
    for (i = INSTRSNUM - 1; i >= 0; i--) {
        if (instrumentSavedFlags[i] > 0) {
            numInstruments = i + 1;
            break;
        }
    }

    // Calculate the offsets for instruments, tracks (lo & hi) and song lines
    // RMT header is 16 bytes, so instrument ptrs start there
    // Each instrument ptr is 2 bytes, and the track pointers are 2 bytes
    // but split into low and high storage areas
    int ptrInstruments = addr + 16;
    int ptrTracksLoBytes = ptrInstruments + numInstruments * 2;
    int ptrTracksHiBytes = ptrTracksLoBytes + numTracks;
    int ptrInstrumentData = ptrTracksHiBytes + numTracks; // behind the track byte table

    // Saves instrument data and writes their beginnings to the table
    for (i = 0; i < numInstruments; i++) {
        if (instrumentSavedFlags[i]) {
            // Create instrument data
            int thisInstrumentLength = g_Instruments.InstrToAta(i, mem + ptrInstrumentData, ATARI_MAX_INSTR_LENGTH);

            // Save where the instrument data is to be found
            mem[ptrInstruments + i * 2] = ptrInstrumentData & 0xff; // lo byte
            mem[ptrInstruments + i * 2 + 1] = ptrInstrumentData >> 8; // hi byte

            // Move where the next instruments data is to be saved
            ptrInstrumentData += thisInstrumentLength;
        } else {
            // Oi, nothing to save here, just emit 0
            // This happens if there are unused instruments between the used ones.
            // Best to rearrange the instruments to be one continous block
            mem[ptrInstruments + i * 2] = mem[ptrInstruments + i * 2 + 1] = 0;
        }
    }

    // Just after of the instrument data we start with the track data
    int ptrTrackData = ptrInstrumentData;

    // Saves track data and write their beginnings to the table
    for (i = 0; i < numTracks; i++) {
        if (trackSavedFlags[i]) {
            // Create the track data
            int thisTrackLength = g_Tracks.TrackToAta(i, mem + ptrTrackData, ATARI_MAX_TRACK_LENGTH);

            // Check that the track data is valid
            if (thisTrackLength < 1) {
                // Track cannot be saved to RMT
                CString msg;
                msg.Format("Fatal error in track %02X.\n\nThis track contains too many events (notes and speed commands),\nthat's why it can't be coded to RMT internal code format.", i);
                SendErrorMessage("Internal format problem.", msg);
                return -1;
            }

            // Save where the track data is to be found
            mem[ptrTracksLoBytes + i] = ptrTrackData & 0xff; // lo byte
            mem[ptrTracksHiBytes + i] = ptrTrackData >> 8; // hi byte

            // Move where the next track's data is to be saved
            ptrTrackData += thisTrackLength;
        } else {
            // Oi, nothing to save here, just emit 0
            mem[ptrTracksLoBytes + i] = mem[ptrTracksHiBytes + i] = 0;
        }
    }

    // Just after the track data we store the song lines
    int ptrSongData = ptrTrackData;

    int thisSongLength = SongToAta(mem + ptrSongData, 0x10000 - ptrSongData, ptrSongData);

    int endOfModule = ptrSongData + thisSongLength;

    // Writes computed pointers to the header
    mem[addr + 8] = ptrInstruments & 0xff; // lo byte pointer to instrument table
    mem[addr + 9] = ptrInstruments >> 8; // hi byte
    //
    mem[addr + 10] = ptrTracksLoBytes & 0xff; // lo byte pointer to low bytes of track data table
    mem[addr + 11] = ptrTracksLoBytes >> 8; // hi byte
    mem[addr + 12] = ptrTracksHiBytes & 0xff; // lo byte pointer to high bytes of track data table
    mem[addr + 13] = ptrTracksHiBytes >> 8; // hi byte
    //
    mem[addr + 14] = ptrSongData & 0xff; // lo byte pointer to song data (arrangements of tracks)
    mem[addr + 15] = ptrSongData >> 8; // hi byte

    // Return the address of the first byte past the last one used
    return endOfModule;
}

int CSong::DecodeModule(unsigned char* mem, int fromAddr, int endAddr, BYTE* instrumentLoadedFlags, BYTE* trackLoadedFlags) {
    int addr = fromAddr;

    memset(instrumentLoadedFlags, 0, INSTRSNUM);
    memset(trackLoadedFlags, 0, TRACKSNUM);

    unsigned char data;
    int i, j;

    BOOL loadState;

    // Check that the header starts with "RMT"
    if (strncmp((char*)(mem + addr), "RMT", 3) != 0) {
        return 0; //there is no RMT
    }

    // 4th byte: # of channels (4 or 8)
    data = mem[addr + 3];
    if (data != '4' && data != '8') {
        return 0; //it is not RMT4 or RMT8
    }
    SetTracks(data & 0x0F); // Store how many channels this module uses

    // 5th byte: track length
    data = mem[addr + 4];
    g_Tracks.SetMaxTrackLength((data > 0) ? data : 256); //0 => 256

    // 6th byte: song speed
    data = mem[addr + 5];
    m_mainSpeed = data;
    if (data < 1) {
        return 0; // there can be no zero speed
    }

    // 7th byte: Instrument speed
    data = mem[addr + 6];
    if (data < 1 || data > 8) {
        return 0; // Instrument speed is less than 1 or greater than 8 (note: should be max 4, but allows up to 8 and will only display a warning)
    }
    m_instrumentSpeed = data;

    // 8th byte: RMT format version nr.
    int version = mem[addr + 7];
    // TODO: Make "case" and support V2
    if (version > RMTFormatVersion::V1) {
        // the byte version is above the currently supported one
        return 0;
    }

    // Now g_Tracks.m_maxTrackLength is set to the value in the RMT header,
    // so re-initialize the tracks to set all tracks to this new length
    g_Tracks.InitTracks();

    // Get various pointers
    int ptrInstruments = mem[addr + 8] + (mem[addr + 9] << 8); // Get ptr to the instuments
    int ptrTracksLow = mem[addr + 10] + (mem[addr + 11] << 8); // Get ptr to tracks table low
    int ptrTracksHigh = mem[addr + 12] + (mem[addr + 13] << 8); // Get ptr to tracks table high
    int ptrSong = mem[addr + 14] + (mem[addr + 15] << 8); // Get ptr to track list (the song)

    // Calculate how long each of the sections are
    int numInstruments = (ptrTracksLow - ptrInstruments) / 2;
    int numTracks = (ptrTracksHigh - ptrTracksLow);
    int lengthSong = endAddr - ptrSong;

    // Decoding of individual instruments
    for (int instrumentNr = 0; instrumentNr < numInstruments; instrumentNr++) {
        // Get the ptr to an instruments configuration data
        int ptrOneInstrument = mem[ptrInstruments + instrumentNr * 2] + (mem[ptrInstruments + instrumentNr * 2 + 1] << 8);

        // Skip over empty instruments
        if (ptrOneInstrument == 0) {
            continue; // Empty instruments have a NULL ptr
        }

        // Depending on the file version load the instrument data into g_Instruments
        if (version == 0) {
            loadState = g_Instruments.AtaV0ToInstr(mem + ptrOneInstrument, instrumentNr);
        } else {
            loadState = g_Instruments.AtaToInstr(mem + ptrOneInstrument, instrumentNr);
        }

        g_Instruments.Update(instrumentNr); //writes to Atari ram

        if (!loadState) {
            return 0; // some problem with the instrument => END
        }

        // Mark the instrument as loaded
        instrumentLoadedFlags[instrumentNr] = 1;
    }

    // Track data ptrs are split over two tables.  Low and high bytes, each indexed by the track number
    // Decoding individual tracks
    for (i = 0; i < numTracks; i++) {
        int trackNr = i;
        int ptrTrack = mem[ptrTracksLow + i] + (mem[ptrTracksHigh + i] << 8);
        if (ptrTrack == 0) {
            continue; // Omitted tracks have pointer of 0
        }

        // Identify the end of the track by the starting address of the next track,
        // and at the end by the starting address of the song data that follows the data of the last track
        int ptrTrackEnd = 0;
        for (j = i; j < numTracks; j++) {
            ptrTrackEnd = (j + 1 == numTracks) ? ptrSong : mem[ptrTracksLow + j + 1] + (mem[ptrTracksHigh + j + 1] << 8);
            if (ptrTrackEnd != 0) {
                break;
            }
            i++; //continue from the next and skip the omitted one
        }

        int trackLength = ptrTrackEnd - ptrTrack;
        if (!g_Tracks.AtaToTrack(mem + ptrTrack, trackLength, trackNr)) {
            return 0; //some problem with the track => END
        }

        // Mark the track as loaded
        trackLoadedFlags[trackNr] = 1;
    }

    // Decoded song
    if (!AtaToSong(mem + ptrSong, lengthSong, ptrSong)) {
        return 0; //some problem with the song => END
    }

    return version;
}

bool CSong::SaveRMW(std::ostream& ou) {
    CString version = RMT_VERSION_STRING;
    ou << (unsigned char*)(LPCSTR)version << std::endl;
    //
    ou.write((char*)m_songname, sizeof(m_songname));
    //
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
    DEFINE_MAINPARAMS;
    int p = 0;
    in.read((char*)&p, sizeof(p)); //read the number of main parameters
    for (int i = 0; i < p; i++) {
        // See the matching comment in SaveRMW() for why this is sizeof(int),
        // not sizeof(mainparams[0]).
        in.read((char*)mainparams[i], sizeof(int));
    }

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
    s.Format("[MODULE]\nRMT: %X\nNAME: %s\nMAXTRACKLEN: %02X\nMAINSPEED: %02X\nINSTRSPEED: %X\nVERSION: %02X\n", g_tracks4_8, (LPCTSTR)nambf, g_Tracks.GetMaxTrackLength(), m_mainSpeed, m_instrumentSpeed, RMTFormatVersion::V1);
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
        for (j = 0; j < g_tracks4_8; j++) {
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
        for (j = 0; j < g_tracks4_8; j++) {
            int t = m_song[i][j];
            if (t >= 0 && t < TRACKSNUM) {
                bf[0] = CharH4(t);
                bf[1] = CharL4(t);
            } else {
                bf[0] = bf[1] = '-';
            }
            bf[2] = 0;
            ou << bf;
            if (j + 1 == g_tracks4_8) {
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
                    if (v <= 4) {
                        v = 4;
                    } else {
                        v = 8;
                    }
                    SetTracks(v);
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
                    g_Tracks.InitTracks(); // Reinitialise
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
                for (i = 0; i < g_tracks4_8; i++) {
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
