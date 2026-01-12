#pragma once

#include "StdAfx.h"

// ---------------------
// Analyser and other RGB colors
// TODO: Make also RGB
#define COL_BLOCK		56

class CRGBColor {

public:
    static constexpr COLORREF MUTE = RGB(120, 160, 240);        // Channel is muted
    static constexpr COLORREF NORMAL = RGB(255, 255, 255);      // Volume bar in white
    static constexpr COLORREF VOLUME_ONLY = RGB(128, 255, 255); // Turquoise for volume only channel
    static constexpr COLORREF TWO_TONE = RGB(128, 255, 0);      // Green for two tone channel
    static constexpr COLORREF BACKGROUND = RGB(34, 50, 80);     // Dark blue
    static constexpr COLORREF LINES = RGB(149, 194, 240);       // Blue gray
    static constexpr COLORREF BLACK = RGB(0, 0, 0);             // Black
};
// ----------------------------------------------------------------------------
// GUI edit modes
class EditMode {
public:

    static constexpr int EDIT_MODE = 0;				// Hit the Jam mode button to switch between
    static constexpr int JAM_MONO_MODE = 1;			// the first three modes
    static constexpr int JAM_STEREO_MODE = 2;		// Can only get here in stereo mode
    static constexpr int EDIT_AND_JAM_MODES = 3;	// < this is edit and jam
    // TODO How can this also be 3?!?!
    static constexpr int MIDI_CH15_MODE = 3;		// Hit RECORD key in Midi channel 15 to cycle to this mode
    static constexpr int POKEY_EXPLORER_MODE = 4;	// Ctrl + Shift + F5
};

// ----------------------------------------------------------------------------
// Keyboard layouts that may be used with RMT for Notes input
enum class KeyboardLayout : int {
    QWERTY = 0,
    AZERTY = 1
};

// ----------------------------------------------------------------------------
// TODO: add more keys definition to simplify things
#define VK_BACKSPACE	8
#define VK_ENTER		13
#define VK_PAGE_UP		33
#define VK_PAGE_DOWN	34

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

//bits in TRACKFLAG
#define TF_NOEMPTY		1
#define TF_USED			2

#define INSTR_GUI_ZONE_ENVELOPE_LEFT_ENVELOPE	0		// 368,220	8x64 -> 384x64
#define INSTR_GUI_ZONE_ENVELOPE_RIGHT_ENVELOPE	1		// 368,140	8x64 -> 384x64
#define INSTR_GUI_ZONE_ENVELOPE_PARAM_TABLE		2		// 368,296	8x64 -> 384x64
#define INSTR_GUI_ZONE_ENVELOPE_RIGHT_VOL_NUMS	3		// 368,200	8x64 -> 384x64
#define INSTR_GUI_ZONE_NOTE_TABLE				4		// 16,424	16x16 -> 760x16
#define INSTR_GUI_ZONE_INSTRUMENT_NAME			5		// 16,152	304x16
#define INSTR_GUI_ZONE_PARAMETERS				6		// 16,200	208x192
#define INSTR_GUI_ZONE_INSTRUMENT_NUMBER_DLG	7		// 16,136	104x16
#define INSTR_GUI_ZONE_LEN_AND_GOTO_ARROWS		8		// 368,280	384x16
#define INSTR_GUI_ZONE_NOTE_TBL_LEN_AND_GOTO	9		// 16,440	760x16


// GUI instrument definitions
#define INSTRS_X			2*8
#define INSTRS_Y			8*16+8
#define INSTRS_PARAM_X		INSTRS_X			// parameter X
#define INSTRS_PARAM_Y		INSTRS_Y+2*16		// parametry Y
#define INSTRS_ENV_X		INSTRS_X+32*8		// envelope X  (29)
#define INSTRS_ENV_Y		INSTRS_Y+2*16		// envelope Y
#define INSTRS_TABLE_X		INSTRS_X+0*8		// table X	(16)(37)
#define INSTRS_TABLE_Y		INSTRS_Y+18*16-8	// table Y
#define INSTRS_HELP_X		INSTRS_X			// active help X
#define INSTRS_HELP_Y		INSTRS_Y+21*16		// active help Y


#define	ENV_VOLUMER		0
#define	ENV_VOLUMEL		1
#define	ENV_DISTORTION	2
#define ENV_COMMAND		3
#define	ENV_X			4
#define	ENV_Y			5
#define	ENV_FILTER		6
#define	ENV_PORTAMENTO	7


// ----------------------------------------------------------------------------
// Timbre definitions, used for tuning calculations
// The values also define which is the appropriate Distortion (AUDC) to use
// Example: "audc = TIMBRE_BUZZY_C & 0xF0" 
// The value of audc is then 0xC0, which corresponds to Distortion C 
//
#define TIMBRE_PINK_NOISE		0x00	// Distortion 0, by default
#define TIMBRE_BROWNIAN_NOISE	0x01	// (MOD7 && POLY9), Distortion 0
#define TIMBRE_FUZZY_NOISE		0x02	// (!MOD7 && POLY9), Distortion 0
#define TIMBRE_BELL				0x20	// (!MOD31), Distortion 2, by default
#define TIMBRE_BUZZY_4			0x40	// (!MOD3 && !MOD5),  used by Distortion 4, this mode is actually identical to Distortion C (Gritty) 
#define TIMBRE_SMOOTH_4			0x41	// (MOD3 && !MOD5), used by Distortion 4, this mode is actually identical to Distortion C (Buzzy) 
#define TIMBRE_WHITE_NOISE		0x80	// Distortion 8, by default
#define TIMBRE_METALLIC_NOISE	0x81	// (MOD7 && POLY9), Distortion 8 
#define TIMBRE_BUZZY_NOISE		0x82	// (!MOD7 && POLY9), Distortion 8
#define TIMBRE_PURE				0xA0	// Distortion A, by default 
#define TIMBRE_GRITTY_C			0xC0	// (!MOD3 && !MOD5), also known as RMT Distortion E
#define TIMBRE_BUZZY_C			0xC1	// (MOD3 && !MOD5), also known as RMT Distortion C
#define TIMBRE_UNSTABLE_C		0xC2	// (!MOD3 && MOD5), must be avoided unless there is a purpose for it

// ----------------------------------------------------------------------------
// File open/save dialog format selections
// .rmt / .txt / .rmw
#define FILE_LOADSAVE_FILTERS "RMT song file (*.rmt)|*.rmt|TXT song file (*.txt)|*.txt|RMW song work file (*.rmw)|*.rmw||"
#define FILE_LOADSAVE_FILTER_IDX_RMT 1
#define FILE_LOADSAVE_FILTER_IDX_TXT 2
#define FILE_LOADSAVE_FILTER_IDX_RMW 3
#define FILE_LOADSAVE_FILTER_IDX_MIN FILE_LOADSAVE_FILTER_IDX_RMT
#define FILE_LOADSAVE_FILTER_IDX_MAX FILE_LOADSAVE_FILTER_IDX_RMW
#define FILE_LOADSAVE_EXTENSIONS_ARRAY { ".rmt",".txt",".rmw" }

// ----------------------------------------------------------------------------
// File import dialog format selections
#define FILE_IMPORT_FILTERS "ProTracker Modules (*.mod)|*.mod|TMC song files (*.tmc,*.tm8)|*.tmc;*.tm8||"
#define FILE_IMPORT_FILTER_IDX_MOD 1
#define FILE_IMPORT_FILTER_IDX_TMC 2
#define FILE_IMPORT_FILTER_IDX_MIN FILE_IMPORT_FILTER_IDX_MOD
#define FILE_IMPORT_FILTER_IDX_MAX FILE_IMPORT_FILTER_IDX_TMC

// ----------------------------------------------------------------------------
// File export dialog format selections
#define FILE_EXPORT_FILTERS \
		"RMT stripped song file (*.rmt)|*.rmt|" \
		"ASM simple notation source (*.asm)|*.asm|" \
		"SAP-R data stream (*.sapr)|*.sapr|" \
		"Compressed SAP-R data stream (*.lzss)|*.lzss|" \
		"SAP file + LZSS driver (*.sap)|*.sap|" \
		"XEX Atari executable + LZSS driver (*.xex)|*.xex|" \
		"Relocatable ASM for RMTPlayer (*.asm)|*.asm|" \
		"WAV audio file (*.wav)|*.wav|" \
		"|"
#define FILE_EXPORT_FILTER_IDX_STRIPPED_RMT 1
#define FILE_EXPORT_FILTER_IDX_SIMPLE_ASM 2
#define FILE_EXPORT_FILTER_IDX_SAPR 3
#define FILE_EXPORT_FILTER_IDX_LZSS 4
#define FILE_EXPORT_FILTER_IDX_SAP 5
#define FILE_EXPORT_FILTER_IDX_XEX 6
//#define FILE_EXPORT_FILTER_IDX_RELOC_ASM 7
//#define FILE_EXPORT_FILTER_IDX_MIN FILE_EXPORT_FILTER_IDX_STRIPPED_RMT
//#define FILE_EXPORT_FILTER_IDX_MAX FILE_EXPORT_FILTER_IDX_RELOC_ASM
//#define FILE_EXPORT_EXTENSIONS_ARRAY { ".rmt",".asm",".sapr",".lzss",".sap",".xex",".asm" };
//#define FILE_EXPORT_EXTENSIONS_LENGTH_ARRAY { 4, 4, 5, 5, 4, 4, 4}
#define FILE_EXPORT_FILTER_IDX_RELOC_ASM 7
#define FILE_EXPORT_FILTER_IDX_WAV 8
#define FILE_EXPORT_FILTER_IDX_MIN FILE_EXPORT_FILTER_IDX_STRIPPED_RMT
#define FILE_EXPORT_FILTER_IDX_MAX FILE_EXPORT_FILTER_IDX_WAV
#define FILE_EXPORT_EXTENSIONS_ARRAY { ".rmt",".asm",".sapr",".lzss",".sap",".xex",".asm",".wav" };
#define FILE_EXPORT_EXTENSIONS_LENGTH_ARRAY { 4, 4, 5, 5, 4, 4, 4, 4}

// ----------------------------------------------------------------------------
// Pokey play to buffer
// TODO: Unused => Remove
// 
#define POKEY2BUFFER_STOP		0
#define POKEY2BUFFER_RECORD		1
#define POKEY2BUFFER_WRITE		2
#define POKEY2BUFFER_START		3		// Start the Pokey 2 buffer recording process

// ----------------------------------------------------------------------------
// SAP-R optimisations pattern, for optimal data compression to LZSS 
// This is a set of combinations that may or may not provide better compression ratios
// Results vary wildly between any given stream of bytes, due to many variables at play 
// Bruteforcing each pattern is more or less a requirement for optimal results
// Ideally, the resulting compressed data should be as small as possible
// If several patterns gave identical results, the first optimal pattern will be used
// 
#define SAPR_OPTIMISATIONS_NONE				0
#define SAPR_OPTIMISATIONS_AUDC				1
#define SAPR_OPTIMISATIONS_AUDCTL			2
#define SAPR_OPTIMISATIONS_AUDF				3
#define SAPR_OPTIMISATIONS_AUDC_AUDF		4
#define SAPR_OPTIMISATIONS_AUDCTL_AUDC		5
#define SAPR_OPTIMISATIONS_AUDCTL_AUDF		6
#define SAPR_OPTIMISATIONS_ALL				7
#define SAPR_OPTIMISATIONS_COUNT			8

