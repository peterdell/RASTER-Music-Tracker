#pragma once

#include "InstrumentTypes.h"

// ----------------------------------------------------------------------------
// RMT file format
// TODO Make constexpr
//
#define RMTFORMATVERSION	1	//the version number that is saved into modules, highest means more recent
#define TRACKLEN	256			//drive 128
#define TRACKSNUM	254			//0-253
#define SONGLEN		256
#define SONGTRACKS	8
#define MAXVOLUME	15			//maximum volume

#define TRACKMAXSPEED	256		//maximum speed values, highest the slowest

#define MAXATAINSTRLEN	256		//16+(ENVCOLS*3)	//atari instrument has a maximum of 16 parameters + 32 * 3 bytes envelope
#define MAXATATRACKLEN	256		//atari track has maximum 256 bytes (track index is 0-255)
#define MAXATASONGLEN	SONGTRACKS*SONGLEN	//maximum data size atari song part

static constexpr int SONG_NAME_MAX_LEN = 64;	// maximum length of song name
typedef char SongName[SONG_NAME_MAX_LEN + 1];

struct TBookmark
{
    int songline;
    int trackline;
    int speed;
};

struct TSong	//due to Undo
{
    int song[SONGLEN][SONGTRACKS];
    int songgo[SONGLEN];					//if> = 0, then GO applies
    TBookmark bookmark;
};

struct TInfo
{
    SongName songname;
    int speed;
    int mainspeed;
    int instrspeed;
    int songnamecur; //to return the cursor to the appropriate position when undo changes in the song name
};

struct TExportDescription
{
    unsigned char mem[65536];				// default RAM size for most 800xl/xe machines

    int targetAddrOfModule;					// Start of RMT module in memory [$4000]
    int firstByteAfterModule;				// Hmm, 1st byte after the RMT module

    BYTE instrumentSavedFlags[INSTRSNUM];
    BYTE trackSavedFlags[TRACKSNUM];

};


enum class SongIOType : int {
    IOTYPE_NONE = 0,			// No export has been done yet
    IOTYPE_RMT = 1,
    IOTYPE_RMW = 2,
    IOTYPE_RMTSTRIPPED = 3,
    IOTYPE_SAP = 4,
    IOTYPE_XEX = 5,		// Not used anymore? Old RMT 1.28 XEX export is disabled?
    IOTYPE_TXT = 6,
    IOTYPE_ASM = 7,
    IOTYPE_RMF = 8,
    IOTYPE_ASM_RMTPLAYER = 9,

    IOTYPE_SAPR = 10,
    IOTYPE_LZSS = 11,
    IOTYPE_LZSS_SAP = 12,
    IOTYPE_LZSS_XEX = 13,

    IOTYPE_WAV = 20,

    IOTYPE_TMC = 101		// import TMC
};
