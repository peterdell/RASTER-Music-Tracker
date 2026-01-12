#pragma once
#include "StdAfx.h"
#include <fstream>

#include "SongTypes.h"
#include "TrackTypes.h"
#include "Tracks.h"

#include "Notes.h"

class CTracks
{
public:

    typedef int TrackNumber; // Starting with 0
    typedef int LineNumber; // Starting with 0

    CTracks();
    ~CTracks();
    void InitTracks();
    void ClearTrack(TrackNumber track);
    BOOL IsEmptyTrack(TrackNumber track) const;
    BOOL DelNoteInstrVolSpeed(int noteinstrvolspeed, TrackNumber track, int line);
    BOOL SetNoteInstrVol(int note, int instr, int vol, TrackNumber track, int line);
    BOOL SetInstr(int instr, TrackNumber track, int line);
    BOOL SetVol(int vol, TrackNumber track, int line);
    BOOL SetSpeed(int speed, TrackNumber track, int line);

    BOOL IsValidChannel(int channel) const { return channel >= 0 && channel < SONGTRACKS; };
    BOOL IsValidTrack(TrackNumber track) const { return track >= 0 && track < TRACKSNUM; };
    BOOL IsValidLine(int line) const { return line >= 0 && line < ATARI_MAX_TRACK_LENGTH; };
    BOOL IsValidNote(int note) { return CNotes::IsValidNote(note); };
    BOOL IsValidInstrument(int instr) const { return instr >= 0 && instr < INSTRSNUM; };
    BOOL IsValidVolume(int vol) const { return vol >= 0 && vol <= MAXVOLUME; };
    BOOL IsValidSpeed(int speed) const { return speed >= 0 && speed < TRACKMAXSPEED; };
    BOOL IsValidLength(int len) const { return len > 0 && len <= ATARI_MAX_TRACK_LENGTH; };
    BOOL IsValidGo(int go) const { return IsValidLine(go); };

    int GetNote(TrackNumber track, int line) const { return IsValidTrack(track) && IsValidLine(line) ? m_track[track].note[line] : -1; };
    int GetInstr(TrackNumber track, int line) const { return IsValidTrack(track) && IsValidLine(line) ? m_track[track].instr[line] : -1; };
    int GetVol(TrackNumber track, int line) { return IsValidTrack(track) && IsValidLine(line) ? m_track[track].volume[line] : -1; };
    int GetSpeed(TrackNumber track, int line) const { return IsValidTrack(track) && IsValidLine(line) ? m_track[track].speed[line] : -1; };
    void GetNoteInstrVolSpeed(int* buff, TrackNumber track, int line) const { if (!(IsValidTrack(track) && IsValidLine(line))) return; buff[0] = m_track[track].note[line]; buff[1] = m_track[track].instr[line]; buff[2] = m_track[track].volume[line]; buff[3] = m_track[track].speed[line]; };
    BOOL SetEnd(TrackNumber track, int line);
    int GetLastLine(TrackNumber track) const;
    int GetLength(TrackNumber track) const;
    BOOL SetGo(TrackNumber track, int line);
    int GetGoLine(TrackNumber track) const;

    BOOL InsertLine(TrackNumber track, int line);
    BOOL DeleteLine(TrackNumber track, int line);

    TTrack* GetTrack(TrackNumber track) { return IsValidTrack(track) ? &m_track[track] : NULL; };

    const TTrack* GetConstTrack(TrackNumber track) const {
        return  IsValidTrack(track) ? &m_track[track] : NULL;
    };

    void GetTracksAll(TTracksAll* toTracks) const;
    void SetTracksAll(TTracksAll* fromTracks);

    TrackNumber TrackToAta(TrackNumber trackNr, unsigned char* dest, int max) const;
    TrackNumber TrackToAtaRMF(TrackNumber trackNr, unsigned char* dest, int max) const;
    BOOL AtaToTrack(unsigned char* mem, int trackLength, TrackNumber trackNr);

    int SaveAll(std::ofstream& ou, SongIOType iotype);
    int LoadAll(std::ifstream& in, SongIOType iotype);

    int SaveTrack(TrackNumber track, std::ofstream& ou, SongIOType iotype);
    int LoadTrack(TrackNumber track, std::ifstream& in, SongIOType iotype);

    BOOL CalculateNotEmpty(TrackNumber track);
    BOOL CompareTracks(TrackNumber track1, TrackNumber track2) const;

    TrackNumber TrackOptimizeVol0(TrackNumber track);
    TrackNumber TrackBuildLoop(TrackNumber track);
    TrackNumber TrackExpandLoop(TrackNumber track);
    TrackNumber TrackExpandLoop(TTrack* ttrack);

    int GetModifiedNote(int note, int tuning);
    int GetModifiedInstr(int instr, int instradd);
    int GetModifiedVolumeP(int volume, int percentage);
    BOOL ModifyTrack(TTrack* track, int from, int to, int instrnumonly, int tuning, int instradd, int volumep);

    //int m_maxTrackLength;
    int GetMaxTrackLength() const { return m_maxTrackLength; };
    void SetMaxTrackLength(int length) { if (IsValidLength(length)) m_maxTrackLength = length; };

private:
    int m_maxTrackLength;
    TTrack* m_track;
};

extern CTracks g_Tracks;
