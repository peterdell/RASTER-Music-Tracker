#include "StdAfx.h"

#include "Song.h"

#include "Atari.h"
#include "PokeyController.h"
#include "PokeyStream.h"

// These CSong methods have no dependency on Global.h (unlike the rest of
// Song.cpp/IO_Song.cpp) - the constructor needs only g_Atari (cheap to
// construct/stub, see AtariTests.cpp), and everything else here only reads/
// writes CSong's own member state. Kept separate to keep this half
// testable without linking Song.cpp's much larger dependency graph - same
// pattern as Tuning.cpp/TuningTables.cpp, Tracks.cpp/TracksEdit.cpp, and
// Instruments.cpp/InstrumentsCore.cpp+InstrumentsAtaFormat.cpp.

extern CAtari g_Atari;

CSong::CSong() {
    // Attributes
    memset(m_songname, 0, SONG_NAME_MAX_LEN);

    // Initialize Timer
    m_quantization_note = -1; // init
    m_quantization_instr = -1;
    m_quantization_vol = -1;

    m_PokeyController = new CPokeyController(&g_Atari);
}

CSong::~CSong() {
    //KillTimer();
}

CString CSong::GetName() const {
    CString result;
    result = m_songname;
    result.TrimRight();
    return result;
}

int CSong::GetChannels() const {
    return m_channels;
}

bool CSong::IsMono() const {
    return m_channels == Channels::MONO;
}

bool CSong::IsStereo() const {
    return m_channels == Channels::STEREO;
}

BOOL CSong::IsNTSC() const {
    return m_ntsc;
}

TrackerDriverVersion CSong::GetTrackerDriverVersion() const {
    return m_trackerDriverVersion;
}

// A plain accessor, unlike SetNTSC(), which calls ReInitSound(): loading the
// new version's routines needs g_AtariTrackerDriver and the missing-file
// report around it, which is the Global.h dependency this file exists
// without - and both callers that change the version already do that.
void CSong::SetTrackerDriverVersion(const TrackerDriverVersion trackerDriverVersion) {
    m_trackerDriverVersion = trackerDriverVersion;
}

TTuningSettings& CSong::GetTuningSettings() {
    return m_tuning;
}

const TTuningSettings& CSong::GetTuningSettings() const {
    return m_tuning;
}

int CSong::GetInstrumentSpeed() const {
    return m_instrumentSpeed;
}

BOOL CSong::PlayPressedTonesInit() {
    for (int t = 0; t < SONGTRACKS; t++) {
        SetPlayPressedTonesTNIV(t, -1, -1, -1);
    }
    return TRUE;
}

BOOL CSong::SetPlayPressedTonesSilence() {
    for (int t = 0; t < SONGTRACKS; t++) {
        SetPlayPressedTonesTNIV(t, -1, -1, 0);
    }
    return TRUE;
}

int CSong::GetActiveInstr() const {
    return m_activeinstr;
};

int CSong::GetActiveColumn() const {
    return m_trackactivecol;
};

int CSong::GetActiveLine() const {
    return m_trackactiveline;
};

int CSong::GetPlayLine() const {
    return m_trackplayline;
};
void CSong::SetActiveLine(int line) {
    m_trackactiveline = line;
};
void CSong::SetPlayLine(int line) {
    m_trackplayline = line;
};

BOOL CSong::UECursorIsEqual(int* cursor1, int* cursor2, Part part) {
    int len;
    switch (part) {
    case Part::PART_TRACKS:
        len = 4;
        break;
    case Part::PART_SONG:
        len = 2;
        break;
    case Part::PART_INSTRUMENTS:
        len = 6;
        break;
    case Part::PART_INFO:
        len = 1;
        break;
    default:
        return 0;
    }
    for (int i = 0; i < len; i++) {
        if (cursor1[i] != cursor2[i]) {
            return 0;
        }
    }
    return 1;
}

int CSong::SongGetGo() const {
    return m_songgo[m_songactiveline];
};

int CSong::SongGetGo(int songline) const {
    return m_songgo[songline];
};

void CSong::SongTrackGoDec() {
    m_songgo[m_songactiveline] = (m_songgo[m_songactiveline] - 1) & 0xff;
};

void CSong::SongTrackGoInc() {
    m_songgo[m_songactiveline] = (m_songgo[m_songactiveline] + 1) & 0xff;
};

int CSong::FindNearTrackBySongLineAndColumn(int songline, int column, BYTE* arrayTRACKSNUM) {
    int j, k, t;
    for (j = songline; j >= 0; j--) {
        if (m_songgo[j] >= 0) {
            continue;
        }
        if ((t = m_song[j][column]) >= 0) {
            //found the default track t
            for (k = t + 1; k < TRACKSNUM; k++) {
                if (arrayTRACKSNUM[k] == 0) {
                    return k;
                }
            }
            //because it did not find any behind it, it will try to look in front of it instead
            for (k = t - 1; k >= 0; k--) {
                if (arrayTRACKSNUM[k] == 0) {
                    return k;
                }
            }
        }
    }
    //will search for the first one usable from the beginning
    for (k = 0; k < TRACKSNUM; k++) {
        if (arrayTRACKSNUM[k] == 0) {
            return k;
        }
    }
    return -1;
}

BOOL CSong::SongPlayNextLine() {
    m_trackplayline = 0; //first track pattern line

    // Normal play, play from current position, or play from bookmark => shift to the next line
    if (m_play == PLAY_SONG || m_play == PLAY_FROM || m_play == PLAY_BOOKMARK) {
        m_songplayline++; // Increment the song line by 1
        if (m_songplayline > 255) {
            m_songplayline = 0; // Above 255, roll over to 0
        }
    }

    // When a goto line is encountered, the player will jump right to the defined line and continue playback from that position
    if (m_songgo[m_songplayline] >= 0) { // If a goto line is set here...
        m_songplayline = m_songgo[m_songplayline]; // goto line xy
    }

    if (m_pokeyStream && m_pokeyStream->TrackSongLine(m_songplayline) == true) {
        // Song is done, so stop the play back
        m_play = PLAY_STOP; // Stop the player
    }
    return 1;
}

