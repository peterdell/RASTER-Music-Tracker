#pragma once
#include "stdafx.h"

#include "General.h"

#include "Instruments.h"
#include "PokeyStream.h"
#include "Tracks.h"
#include "Undo.h"

#include "SongTypes.h"

class CASMFileExporter;
class CPokeyController;

class CSongUI;

class CSong
{
public:
    friend CASMFileExporter; // JAC! TODO Remove
    friend CSongUI;

    CSong();
    ~CSong();

    CString GetName() const;
    int GetTracks() const;
    bool IsStereo() const;
    void SetTracks(const int tracksNum);
    BOOL IsNTSC() const;
    void SetNTSC(const BOOL ntsc);

    void ReInitSound();

    int GetInstrumentSpeed() const;

    void StopTimer();
    void ChangeTimer(int ms);

    void ClearSong(int numoftracks);

    // Extracted from ClearSong(): pushes g_SkipLinesAfterNoteInsert into the
    // main frame's combo box, if the app's main window exists. A real MFC
    // AfxGetMainWnd()/CMainFrame call, so it stays in Song.cpp rather than
    // moving with the rest of ClearSong() into SongEditing.cpp.
    void SyncSkipLinesAfterNoteInsertComboBox();

    void MidiEvent(DWORD dwParam);

    BOOL InfoKey(int vk, int shift, int control);
    BOOL InfoCursorGotoSongname(int x);
    BOOL InfoCursorGotoSpeed(int x);
    BOOL InfoCursorGotoHighlight(int x);
    BOOL InfoCursorGotoOctaveSelect(int x, int y);
    BOOL InfoCursorGotoVolumeSelect(int x, int y);
    BOOL InfoCursorGotoInstrumentSelect(int x, int y);

    BOOL InstrKey(int vk, int shift, int control);
    void ActiveInstrSet(int instr);
    void ActiveInstrPrev();
    void ActiveInstrNext();

    int GetActiveInstr() const;
    int GetActiveColumn() const;
    int GetActiveLine() const;
    int GetPlayLine() const;
    void SetActiveLine(int line);
    void SetPlayLine(int line);

    BOOL CursorToSpeedColumn();
    BOOL ProveKey(int vk, int shift, int control);
    BOOL TrackKey(int vk, int shift, int control);
    BOOL TrackCursorGoto(CPoint point);
    BOOL TrackUp(int lines);
    BOOL TrackDown(int lines, BOOL stoponlastline = 1);
    BOOL TrackLeft(BOOL column = 0);
    BOOL TrackRight(BOOL column = 0);
    BOOL TrackDelNoteInstrVolSpeed(int noteinstrvolspeed) { return g_Tracks.DelNoteInstrVolSpeed(noteinstrvolspeed, SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetNoteActualInstrVol(int note) { return g_Tracks.SetNoteInstrVol(note, m_activeinstr, m_volume, SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetNoteInstrVol(int note, int instr, int vol) { return g_Tracks.SetNoteInstrVol(note, instr, vol, SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetInstr(int instr) { return g_Tracks.SetInstr(instr, SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetVol(int vol) { return g_Tracks.SetVol(vol, SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetSpeed(int speed) { return g_Tracks.SetSpeed(speed, SongGetActiveTrack(), m_trackactiveline); };
    int TrackGetNote() { return g_Tracks.GetNote(SongGetActiveTrack(), m_trackactiveline); };
    int TrackGetInstr() { return g_Tracks.GetInstr(SongGetActiveTrack(), m_trackactiveline); };
    int TrackGetVol() { return g_Tracks.GetVol(SongGetActiveTrack(), m_trackactiveline); };
    int TrackGetSpeed() { return g_Tracks.GetSpeed(SongGetActiveTrack(), m_trackactiveline); };
    BOOL TrackSetEnd() { return g_Tracks.SetEnd(SongGetActiveTrack(), m_trackactiveline + 1); };
    int TrackGetLastLine() { return g_Tracks.GetLastLine(SongGetActiveTrack()); };
    BOOL TrackSetGo() { return g_Tracks.SetGo(SongGetActiveTrack(), m_trackactiveline); };
    int TrackGetGoLine() { return g_Tracks.GetGoLine(SongGetActiveTrack()); };
    void RespectBoundaries();
    void TrackGetLoopingNoteInstrVol(int track, int& note, int& instr, int& vol) const;

    int* GetUECursor(Part part);
    void SetUECursor(Part part, int* cursor);
    BOOL UECursorIsEqual(int* cursor1, int* cursor2, Part part);
    BOOL Undo() { return g_Undo.Undo(); };
    int	 UndoGetUndoSteps() { return g_Undo.GetUndoSteps(); };
    BOOL Redo() { return g_Undo.Redo(); };
    int  UndoGetRedoSteps() { return g_Undo.GetRedoSteps(); };

    BOOL SongKey(int vk, int shift, int control);
    BOOL SongCursorGoto(CPoint point);
    BOOL SongUp();
    BOOL SongDown();
    BOOL SongSubsongPrev();
    BOOL SongSubsongNext();
    BOOL SongTrackSet(int t);
    BOOL SongTrackSetByNum(int num);
    BOOL SongTrackDec();
    BOOL SongTrackInc();
    BOOL SongTrackEmpty();
    int SongGetActiveTrack() { return (m_songgo[m_songactiveline] >= 0) ? -1 : m_song[m_songactiveline][m_trackactivecol]; };
    int SongGetTrack(int songline, int trackcol) { return IsValidSongline(songline) && !IsSongGo(songline) ? m_song[songline][trackcol] : -1; };
    int SongGetActiveTrackInColumn(int column) { return m_song[m_songactiveline][column]; };
    int SongGetActiveLine() { return m_songactiveline; };
    int SongGetPlayLine() { return m_songplayline; };
    void SongSetActiveLine(int line) { m_songactiveline = line; };
    void SongSetPlayLine(int line) { m_songplayline = line; };

    BOOL SongTrackGoOnOff();
    int SongGetGo() const;
    int SongGetGo(int songline) const;
    void SongTrackGoDec();
    void SongTrackGoInc();

    BOOL SongInsertLine(int line);
    BOOL SongDeleteLine(int line);
    BOOL SongInsertCopyOrCloneOfSongLines(int& line);
    BOOL SongPrepareNewLine(int& line, int sourceline = -1, BOOL alsoemptycolumns = 1);
    int FindNearTrackBySongLineAndColumn(int songline, int column, BYTE* arrayTRACKSNUM);
    BOOL SongPutnewemptyunusedtrack();
    BOOL SongMaketracksduplicate();

    BOOL OctaveUp() { if (m_octave < 4) { m_octave++; return 1; } else return 0; };
    BOOL OctaveDown() { if (m_octave > 0) { m_octave--; return 1; } else return 0; };

    BOOL VolumeUp() { if (m_volume < MAXVOLUME) { m_volume++; return 1; } else return 0; };
    BOOL VolumeDown() { if (m_volume > 0) { m_volume--; return 1; } else return 0; };

    void ClearBookmark() { m_bookmark.songline = m_bookmark.trackline = m_bookmark.speed = -1; };
    BOOL IsBookmark() { return (m_bookmark.speed > 0 && m_bookmark.trackline < g_Tracks.GetMaxTrackLength()); };
    BOOL SetBookmark();

    BOOL Play(PlayMode mode, BOOL follow, int special = 0);
    void Stop();
    BOOL SongPlayNextLine();

    BOOL PlayBeat();
    BOOL PlayVBI();

    BOOL PlayPressedTonesInit();
    BOOL SetPlayPressedTonesTNIV(int t, int n, int i, int v) { m_playptnote[t] = n; m_playptinstr[t] = i; m_playptvolume[t] = v; return 1; }
    BOOL SetPlayPressedTonesV(int t, int v) { m_playptvolume[t] = v; return 1; };
    BOOL SetPlayPressedTonesSilence();
    BOOL PlayPressedTones();

    void TimerRoutine();

    void SetRMTTitle();

    BOOL FileOpen(const char* filename = NULL, BOOL warnOfUnsavedChanges = TRUE);
    void FileReload();
    BOOL FileCanBeReloaded() { return (m_filename != "") /*&& (!m_fileunsaved)*/ /*&& g_changes*/; };
    int WarnUnsavedChanges();

    void FileSave();
    void FileSaveAs();
    void FileNew();
    void FileImport();
    void FileExportAs();

    void FileInstrumentSave();
    void FileInstrumentLoad();
    void FileTrackSave();
    void FileTrackLoad();

    int SongToAta(unsigned char* dest, int max, int adr);
    BOOL AtaToSong(unsigned char* sour, int len, int adr);

    // SaveTxt()/SaveRMW()/LoadRMT() take std::ostream&/std::istream& rather
    // than std::ofstream&/std::ifstream& - every real call site passes a
    // genuine file stream (which satisfies the wider base type), and the
    // wider type lets tests use an in-memory stream (see SongEditingTests.cpp).
    bool SaveTxt(std::ostream& ou);
    bool SaveRMW(std::ostream& ou);
    bool LoadRMT(std::istream& in);

    bool LoadTxt(std::ifstream& in);
    bool LoadRMW(std::ifstream& in);

    int ImportTMC(std::ifstream& in);
    int ImportMOD(std::ifstream& in);

    // Export methods shall be separeated from song itself
    // CSong argument is not yet const, because the DumpPokey... methods change its state
    static bool ExportV2(CSong& song, std::ofstream& ou, SongIOType iotype, LPCTSTR filename = NULL);

    void DumpSongToPokeyStream(CPokeyStream& pokeyStream, PlayMode playMode, int songline, int trackline);


    bool TestBeforeFileSave();
    int GetSubsongParts(CString& resultstr) const;

    void MarkTF_USED(BYTE* arrayTRACKSNUM) const;
    void MarkTF_NOEMPTY(BYTE* arrayTRACKSNUM) const;

    // int MakeTuningBlock(unsigned char* mem, int addr); // TODO: Unused
    // int DecodeTuningBlock(unsigned char* mem, int fromAddr, int endAddr); // TODO: Unused
    void ResetTuningVariables();

    int MakeModule(unsigned char* mem, int adr, SongIOType iotype, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags);
    int DecodeModule(unsigned char* mem, int adrfrom, int adrend, BYTE* instrumentLoadedFlags, BYTE* trackLoadedFlags);

    void TrackCopy();
    void TrackPaste();
    void TrackCut();
    void TrackDelete();
    void TrackCopyFromTo(int fromtrack, int totrack);
    void TrackSwapFromTo(int fromtrack, int totrack);

    void BlockPaste(int special = 0);

    void InstrCopy();
    void InstrPaste(int special = 0);
    void InstrCut();
    void InstrDelete();

    void InstrInfo(int instr, TInstrInfo* iinfo = NULL, int instrto = -1);
    void InstrChange(int instr);
    void TrackInfo(int track, TTrackInfo* tinfo = NULL);

    void SongCopyLine();
    void SongPasteLine();
    void SongClearLine();

    void TracksOrderChange();
    void Songswitch4_8(int tracks4_8);
    int GetEffectiveMaxtracklen();
    int GetSmallestMaxtracklen(int songline);
    void ChangeMaxtracklen(int maxtracklen);
    void TracksAllBuildLoops(int& tracksmodified, int& beatsreduced);
    void TracksAllExpandLoops(int& tracksmodified, int& loopsexpanded);
    void SongClearUnusedTracksAndParts(int& clearedtracks, int& truncatedtracks, int& truncatedbeats);

    int SongClearDuplicatedTracks();
    int SongClearUnusedTracks();
    int ClearAllInstrumentsUnusedInAnyTrack();

    void RenumberAllTracks(int type);
    void RenumberAllInstruments(int type);

    CString GetFilename() const { return m_filename; };
    SongIOType GetIOType() const { return m_ioType; };

    int(*GetSong())[SONGLEN][SONGTRACKS]{ return &m_song; };
    int(*GetSongGo())[SONGLEN] { return &m_songgo; };
    TBookmark* GetBookmark() { return &m_bookmark; };

    PlayMode GetPlayMode() const { return m_play; };
    void SetPlayMode(PlayMode mode) { m_play = mode; };
    BOOL GetFollowPlayMode() const { return m_followplay; };
    void SetFollowPlayMode(BOOL follow) { m_followplay = follow; };

    void GetSongInfoPars(TInfo* info) { memcpy(info->songname, m_songname, SONG_NAME_MAX_LEN); info->speed = m_speed; info->mainspeed = m_mainSpeed; info->instrspeed = m_instrumentSpeed; info->songnamecur = m_songnamecur; };
    void SetSongInfoPars(TInfo* info) { memcpy(m_songname, info->songname, SONG_NAME_MAX_LEN); m_speed = info->speed; m_mainSpeed = info->mainspeed; m_instrumentSpeed = info->instrspeed; m_songnamecur = info->songnamecur; };

    BOOL IsValidSongline(int songline) const { return songline >= 0 && songline < SONGLEN; };
    BOOL IsSongGo(int songline) const { return IsValidSongline(songline) ? m_songgo[songline] >= 0 : 0; };

    void SongJump(int lines);


    void BLOCKSETBEGIN();
    void BLOCKSETEND();
    void BLOCKDESELECT();
    BOOL ISBLOCKSELECTED();

private:
    // Members below are given explicit defaults (matching what the global
    // g_Song already got for free from static zero-initialization) so any
    // other CSong instance - e.g. one constructed directly in a test - is
    // just as well-defined. Several of these are used as array indices
    // elsewhere (m_songactiveline/m_songplayline into m_songgo[SONGLEN],
    // m_trackactivecol into m_song[][SONGTRACKS], m_activeinstr into the
    // instruments table), where an indeterminate value would be an
    // out-of-bounds read, and m_pokeyStream is a pointer dereferenced by
    // SongPlayNextLine() whenever it's non-null.
    int m_song[SONGLEN][SONGTRACKS] = {};
    int m_songgo[SONGLEN] = {};					// If >= 0, then GO applies

    CPokeyStream* volatile m_pokeyStream = nullptr;       // NULL or the stream to which we are currently recording
    BOOL volatile m_followplay = FALSE;
    PlayMode volatile m_play = PLAY_STOP;
    int m_songactiveline = 0;
    int volatile m_songplayline = 0;				// Which line of the song is currently being played

    int m_trackactiveline = 0;
    int volatile m_trackplayline = 0;				// Which line of a track is currenyly being played
    int m_trackactivecol = 0;						// 0-7
    int m_trackactivecur = 0;						// 0-2

    int m_trackplayblockstart = 0;
    int m_trackplayblockend = 0;

    int m_activeinstr = 0;
    int m_volume = 0;
    int m_octave = 0;

    //MIDI input variables, used for tests through MIDI CH15 
    int m_mod_wheel = 0;
    int m_vol_slider = 0;
    int m_heldkeys = 0;
    int m_midi_distortion = 0;
    BOOL m_ch_offset = 0;

    //POKEY EXPLORER variables, used for tests involving pitch calculations and sound debugging displayed on screen
public:// TODO
    CPokeyController* m_PokeyController;
private:

    EditArea m_infoact = EditArea::NAME;					// Which part of the info area is active for editing: 0 = name,
    char m_songname[SONG_NAME_MAX_LEN + 1] = {};
    BOOL m_ntsc = FALSE;
    int m_songnamecur = 0;

    TBookmark m_bookmark = {};

    double m_avgspeed[8] = { 0 };		// Use for calculating average BPM

    int volatile m_mainSpeed = 0;
    int volatile m_speed = 0;
    int volatile m_speeda = 0;

    int volatile m_instrumentSpeed = 0;

    int volatile m_quantization_note = -1;
    int volatile m_quantization_instr = -1;
    int volatile m_quantization_vol = -1;

    int m_playptnote[SONGTRACKS] = {};
    int m_playptinstr[SONGTRACKS] = {};
    int m_playptvolume[SONGTRACKS] = {};

    TInstrument m_instrclipboard = {};
    int m_songlineclipboard[SONGTRACKS] = {};
    int m_songgoclipboard = 0;

    bool volatile m_timerRoutineProcessed = false;
    const BYTE m_timerRoutineTick[3] = { 17, 17, 16 };

    CString m_filename;
    SongIOType m_ioType = SongIOType::NONE;
    SongIOType m_lastExportIOType = SongIOType::NONE;      // Which data format was used to export a file the last time?

    int m_TracksOrderChange_songlinefrom = 0; //is defined as a member variable to keep in use
    int m_TracksOrderChange_songlineto = 0;	  //the last values used remain
};

