#pragma once

#include "StdAfx.h"

// ---------------------
// Analyser and other RGB colors
class CRGBColor {

public:
    static constexpr COLORREF MUTE = RGB(120, 160, 240);        // Channel is muted
    static constexpr COLORREF NORMAL = RGB(255, 255, 255);      // Volume bar in white
    static constexpr COLORREF VOLUME_ONLY = RGB(128, 255, 255); // Turquoise for volume only channel
    static constexpr COLORREF TWO_TONE = RGB(128, 255, 0);      // Green for two tone channel
    static constexpr COLORREF BACKGROUND = RGB(34, 50, 80);     // Dark blue
    static constexpr COLORREF LINES = RGB(149, 194, 240);       // Blue gray
    static constexpr COLORREF BLACK = RGB(0, 0, 0);             // Black
    static constexpr BYTE COL_BLOCK = 56;                       // Blue portion of analyzer background block

};
// ----------------------------------------------------------------------------
// GUI edit modes
enum class EditMode : int {
    EDIT_MODE = 0,			// Hit the Jam mode button to switch between
    JAM_MONO_MODE = 1,		// the first three modes
    JAM_STEREO_MODE = 2,	// Can only get here in stereo mode
    MIDI_CH15_MODE = 3,		// Hit RECORD key in Midi channel 15 to cycle to this mode
    POKEY_EXPLORER_MODE = 4	// 
};

// ----------------------------------------------------------------------------
// Keyboard layouts that may be used with RMT for Notes input
enum class KeyboardLayout : int {
    QWERTY = 0,
    AZERTY = 1
};

#define CONFIG_FILENAME "rmt.ini"
#define TUNING_FILENAME "tuning.ini"

// This macro was shamelessly stolen from this stackoverflow post: https://stackoverflow.com/a/42450151 
// Requires #include <iomanip>
#define PADHEX(width, val) "0x"  << std::setfill('0') << std::setw(width) << std::hex << std::uppercase << (unsigned)val
#define PADDEC(width, val) std::setfill('0') << std::setw(width) << std::dec << (unsigned)val


class CSongScreenLayout {
public:
    static constexpr int CHARACTER_WIDTH = 8;
    static constexpr int HEIGHT_WIDTH = 16;

    static constexpr int TRACKS_X = 2 * 8;
    static constexpr int TRACKS_Y = 8 * 16 + 8;
    static constexpr int SONG_X = 768;
    static constexpr int SONG_Y = 16;

    // Info area
    // Shown at top-left
    // 6 lines of text
    static constexpr int INFO_X = 2 * 8;
    static constexpr int INFO_Y = 1 * 16;

    static constexpr int INFO_Y_LINE_1 = INFO_Y;
    static constexpr int INFO_Y_LINE_2 = INFO_Y + 1 * 16;
    static constexpr int INFO_Y_LINE_3 = INFO_Y + 2 * 16;
    static constexpr int INFO_Y_LINE_4 = INFO_Y + 3 * 16;
    static constexpr int INFO_Y_LINE_5 = INFO_Y + 4 * 16;
    static constexpr int INFO_Y_LINE_6 = INFO_Y + 5 * 16;

};

// Which part of the info area is active for editing (drawn in red)
enum class EditArea : int {
    NAME = 0,			// Song name can be edited
    SPEED = 1,			// Song speed can be changed
    MAIN_SPEED = 2,		// Over all song speed can be edited
    INSTR_SPEED = 3,	// Speed, i, e. how many times per frame is the instrument code called (1-8), can be edited
    FIRST_HIGHLIGHT = 4,	// Primary line highlight can be edited
    SECOND_HIGHLIGHT = 5	// Secondary line highlight can be edited
};

// Which part of the data is currently active/visible/primary
enum class Part : int {
    PART_INFO = 0,
    PART_TRACKS = 1,
    PART_INSTRUMENTS = 2,
    PART_SONG = 3
};


enum PlayMode : int {

    PLAY_STOP = 0,
    PLAY_SONG = 1,
    PLAY_FROM = 2,
    PLAY_TRACK = 3,
    PLAY_BLOCK = 4,
    PLAY_BOOKMARK = 5,
    PLAY_SEEK_NEXT = 6,	//added for Media keys
    PLAY_SEEK_PREV = 7,	//added for Media keys

    PLAY_SAPR_SONG = 255,	// SAPR dump from song start
    PLAY_SAPR_FROM = 254,	// SAPR dump from song cursor position
    PLAY_SAPR_TRACK = 253,	// SAPR dump from track (loop optional)
    PLAY_SAPR_BLOCK = 252,	// SAPR dump from selection block (loop optional)
    PLAY_SAPR_BOOKMARK = 251	// SAPR dump from bookmak position

};

// bits in TRACKFLAG
class TrackFlag {
public:
    static constexpr BYTE TF_NOEMPTY = 1;
    static constexpr BYTE TF_USED = 2;
};

enum class InstrumentGUIZone : int {
    ENVELOPE_LEFT_ENVELOPE = 0,		// 368,220	8x64 -> 384x64
    ENVELOPE_RIGHT_ENVELOPE = 1,		// 368,140	8x64 -> 384x64
    ENVELOPE_PARAM_TABLE = 2,		// 368,296	8x64 -> 384x64
    ENVELOPE_RIGHT_VOL_NUMS = 3,		// 368,200	8x64 -> 384x64
    NOTE_TABLE = 4,		// 16,424	16x16 -> 760x16
    INSTRUMENT_NAME = 5,		// 16,152	304x16
    PARAMETERS = 6,		// 16,200	208x192
    INSTRUMENT_NUMBER_DLG = 7,		// 16,136	104x16
    LEN_AND_GOTO_ARROWS = 8,		// 368,280	384x16
    NOTE_TBL_LEN_AND_GOTO = 9		// 16,440	760x16
};


// GUI instrument definitions
class InstrumentGUIPosition {
public:
    static constexpr int X = 2 * 8;
    static constexpr int Y = 8 * 16 + 8;
    static constexpr int PARAM_X = X;			    // parameter X
    static constexpr int PARAM_Y = Y + 2 * 16;		// parametry Y
    static constexpr int ENV_X = X + 32 * 8;		// envelope X  (29)
    static constexpr int ENV_Y = Y + 2 * 16;		// envelope Y
    static constexpr int TABLE_X = X + 0 * 8;		// table X	(16)(37)
    static constexpr int TABLE_Y = Y + 18 * 16 - 8;	// table Y
    static constexpr int HELP_X = X;			    // active help X
    static constexpr int HELP_Y = Y + 21 * 16;		// active help Y
};


class EnvelopeParameter {

public:

    static constexpr int VOLUMER = 0;
    static constexpr int VOLUMEL = 1;
    static constexpr int DISTORTION = 2;
    static constexpr int COMMAND = 3;
    static constexpr int X = 4;
    static constexpr int Y = 5;
    static constexpr int FILTER = 6;
    static constexpr int PORTAMENTO = 7;
};
