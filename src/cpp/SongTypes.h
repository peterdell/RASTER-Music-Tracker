#pragma once

#include "InstrumentTypes.h"
#include "TracksTypes.h"
#include "TrackTypes.h"



// ----------------------------------------------------------------------------
// RMT file format
// TODO Make constexpr
//

// The version number that is saved into modules, highest means more recent
enum RMTFormatVersion : byte {
    V1 = 1,
    V2 = 2
};

#define SONGLEN		256
#define SONGTRACKS	8
#define MAXVOLUME	15			//maximum volume

static constexpr int ATARI_MAX_INSTR_LENGTH = 256;		// 16+(ENVCOLS*3)	//atari instrument has a maximum of 16 parameters + 32 * 3 bytes envelope
static constexpr int ATARI_MAX_TRACK_LENGTH = 256;		// Atari track has maximum 256 bytes (track index is 0-255)

static constexpr int SONG_NAME_MAX_LEN = 64;	// maximum length of song name
typedef char SongName[SONG_NAME_MAX_LEN + 1];

struct TBookmark
{
    int songline;
    int trackline;
    int speed;
};

// Populated by CSong::TrackInfo() - the track/song analogue of TInstrInfo
// (InstrumentTypes.h), which CSong::InstrInfo() populates.
struct TTrackInfo
{
    int count;                     // total occurrences of the track across all columns/songlines
    int lines;                     // songlines the track appears in (goto lines excluded)
    int usedincolumn[SONGTRACKS];  // per-column occurrence count (only columns < g_tracks4_8 are ever incremented)
};

// Input parameters for CSong::InstrChangeApply() - mirrors CInstrumentChangeDlg's
// fields 1:1 (see EffectsDlg.h), letting InstrChange()'s actual instrument-remap
// logic run independently of the dialog that normally supplies these values.
// Field names match the local variables InstrChangeApply() has always used
// internally.
struct TInstrChangeParams
{
    int snotefrom, snoteto, svolmin, svolmax;   // source note/volume range to match
    int sinstrfrom, sinstrto;                   // source instrument range to match
    int dnotefrom, dnoteto, dvolmin, dvolmax;   // destination note/volume range to map to
    int dinstrfrom, dinstrto;                   // destination instrument range to map to
    int onlytrack;                              // >=0: restrict to a single track number
    int onlychannels;                           // bitmask of song columns to restrict to
    int onlysonglinefrom, onlysonglineto;       // songline range to restrict to
};

// Populated by CSong::ImportTMCParseHeader() (IO_ImporterCore.cpp) - the
// unconditional real work ImportTMC() does before its options dialog can be
// shown (the dialog's own text needs the parsed song name), carried forward
// into CSong::ImportTMCApply() for the rest of the real conversion. See
// plans/IO_IMPORTER_PLAN.md.
struct TImportTMCHeader
{
    bool ok;                    // false = corrupted/unsupported file (see ImportTMCParseHeader())
    unsigned char mem[65536];   // raw decoded TMC file bytes
    WORD bfrom;                 // offset of the binary block within mem; all addressing below is relative to this
};

// Populated by CSong::ImportTMCApply() - the stats needed to build the
// post-import confirmation dialog's text (see CImportTmcFinishedDlg).
struct TImportTMCResult
{
    int numoftracks;
    int nonemptyinstruments;
    int songlines;
    int optitracks, optibeats;                         // only set if optimizeloops was requested
    int clearedtracks, truncatedtracks, truncatedbeats; // only set if truncateunusedparts was requested
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
    NONE = 0,           // None.
    RMT = 1,            // For load and save, TODO: Make this RMT_V1
    RMW = 2,            // For load and save. TODO: How to represent V2 later?
    RMTSTRIPPED = 3,    // Only for export, but resul can also be imported again as RMT
    SAP = 4,            // Only for export, TODO Enable import
    XEX = 5,            // Only for export, TODO: Not used anymore? Old RMT 1.28 XEX export is disabled?
    TXT = 6,            // TODO: For import and export?
    ASM = 7,            // Only for export
    // RMF = 8,         // Obsolete
    ASM_RMTPLAYER = 9,  // Only for export

    SAPR = 10,          // Only for export
    LZSS = 11,          // Only for export
    LZSS_SAP = 12,      // Only for export
    LZSS_XEX = 13,      // Only for export

    WAV = 20,           // Only for export

    TMC = 101           // Onyl for import
};
