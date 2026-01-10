//
// Global.h
// originally made by Raster, 2002-2009
// experimental changes and additions by VinsCool, 2021-2022
//

#pragma once

#include "General.h"
#include "TuningTypes.h"

#include "SongTypes.h"
#include "Atari.h"
#include "AtariTrackerDriver.h"


void SetProgramFolderPath(const CString& folderPath);
CString GetResourceFolderPath(const CString& folderName);
CString GetResourceFilePath(const CString& folderName, const CString& fileName);

constexpr size_t ATARI_RAM_SIZE = 0x10000;
extern byte g_atarimem[ATARI_RAM_SIZE];
extern char g_debugmem[ATARI_RAM_SIZE];	//debug display of g_atarimem bytes directly, slow and terrible, do not use unless there is a purpose for it 

extern BOOL g_closeApplication;
extern CDC* g_mem_dc;
extern CDC* g_gfx_dc;

extern int g_width;
extern int g_height;
extern int g_tracklines;
extern int g_scaling_percentage;

extern int g_notesperoctave;


extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;

extern HWND g_hwnd;
extern HWND g_viewhwnd;

extern HINSTANCE g_c6502_dll;
extern BOOL volatile g_is6502;
extern CString g_about6502;
extern CAtari g_Atari;
extern CAtariTrackerDriver* g_AtariTrackerDriver;

extern BOOL g_changes;	//have there been any changes in the module?

extern int g_RmtHasFocus;
extern BOOL g_shiftkey;
extern BOOL g_controlkey;
extern BOOL g_altkey;	//unfinished implementation, doesn't work yet for some reason

extern int g_tracks4_8;
bool IsStereo();

extern BOOL volatile g_screenupdate;
extern BOOL volatile g_rmtroutine;

extern int volatile g_prove;			//test notes without editing (0 = off, 1 = mono, 2 = stereo)
extern int volatile g_respectvolume;	//does not change the volume if it is already there

extern WORD g_rmtstripped_adr_module;	//address for export RMT stripped file
extern BOOL g_rmtstripped_sfx;			//sfx offshoot RMT stripped file
extern BOOL g_rmtstripped_gvf;			//gvs GlobalVolumeFade for feat
extern BOOL g_rmtstripped_nos;			//nos NoStartingSongline for feat

extern Part last_activepart;		    //if equal to g_activepart, no block clear necessary
extern Part last_active_ti;			    //if equal to g_active_ti, no screen clear necessary
extern uint64_t last_ms;
extern uint64_t last_sec;
extern int real_fps;
extern double last_fps;
extern double avg_fps[120];

extern Part g_activepart;			    // 0 info, 1 edittracks, 2 editinstruments, 3 song
extern Part g_active_ti;			    // 1 tracks, 2 instrs

extern BOOL g_isEditingInstrumentName;		//0 no, 1 instrument name is edited
extern BOOL is_editing_infos;		    //0 no, 1 song name is edited

extern int g_line_y;			    //active line coordinate, used to reference g_cursoractview to the correct position

extern int g_trackLinePrimaryHighlight;	//primary line highlighted every x lines
extern int g_trackLineSecondaryHighlight;	//secondary line highlighted every x lines
extern BOOL g_tracklinealtnumbering; //alternative way of line numbering in tracks
extern int g_linesafter;			//number of lines to scroll after inserting a note (initializes in CSong :: Clear)

extern BOOL g_nohwsoundbuffer;	//Don't use hardware soundbuffer
extern int g_cursoractview;		//default position, line 0


extern BOOL g_displayflatnotes;	//flats instead of sharps
extern BOOL g_usegermannotation;	//H notes instead of B

extern int g_channelon[SONGTRACKS];
extern int g_rmtinstr[SONGTRACKS];

struct TViewState {
    BOOL mainToolbar;
    BOOL blockToolbar;
    BOOL statusBar;
    BOOL playTimeCounter;
    BOOL volumeAnalyzer;
    BOOL pokeyRegisters;
    BOOL instrumentEditHelp;
    BOOL smoothScrolling;	// if TRUE, then the track and note data is smooth scrolled during playback 
    BOOL debugDisplay;		// Display Debug informations on screen if enabled 
};

extern TViewState g_view;

extern TrackerDriverVersion g_trackerDriverVersion;
extern int g_timerGlobalCount;		// Initialised once, ticking forever
extern long g_playtime;				//1 yes, 0 no

extern UINT g_mousebutt;			//mouse button

// Mouse Information
struct TMouseInfomation {
    int pointX;
    int pointY;
    int button;
    int wheelDelta;
};
extern TMouseInfomation g_mouse;

extern int g_lastKeyPressed;		//for debugging vk input

extern CString g_lastLoadPath_Songs;		//the path of the last song loaded
extern CString g_lastLoadPath_Instruments; //the path of the last instrument loaded
extern CString g_lastLoadPath_Tracks;		//the path of the last track loaded

extern CString g_defaultSongsPath;		//default path for songs
extern CString g_defaultInstrumentsPath;	//default path for instruments
extern CString g_defaultTracksPath;		//default path for tracks

extern KeyboardLayout g_keyboard_layout;			//Keyboard layout is used by RMT. eg: QWERTY, AZERTY, etc
extern BOOL g_keyboard_swapenter;// probably not needed anymore but will be kept for now
extern BOOL g_keyboard_playautofollow;
extern BOOL g_keyboard_updowncontinue;
extern BOOL g_keyboard_RememberOctavesAndVolumes;
extern BOOL g_keyboard_escresetatarisound;
extern BOOL g_keyboard_askwhencontrol_s;
