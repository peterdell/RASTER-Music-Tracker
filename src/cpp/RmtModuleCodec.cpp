#include "StdAfx.h"

#include "RmtModuleCodec.h"

#include <algorithm>
#include <cstring>

#include "Instruments.h"
#include "Song.h"
#include "Tracks.h"

extern CInstruments g_Instruments;
extern CTracks g_Tracks;

CRmtModule CRmtModuleCodec::FromSong(const CSong& song, SongIOType iotype) {
    int channels = song.GetChannels();
    BYTE instrumentSavedFlags[INSTRSNUM] = {};
    BYTE trackSavedFlags[TRACKSNUM] = {};
    song.MarkTF_USED(trackSavedFlags); // mark all tracks as used
    if (iotype == SongIOType::RMT) {
        song.MarkTF_NOEMPTY(trackSavedFlags); // in addition to the used ones, all non-empty tracks
    }
    for (int i = 0; i < TRACKSNUM; i++) {
        if (trackSavedFlags[i] > 0) {
            TTrack* track = g_Tracks.GetTrack(i);
            for (int j = 0; j < track->len; j++) {
                if (g_Tracks.IsValidInstrument(track->instr[j])) {
                    instrumentSavedFlags[track->instr[j]] = IF_USED;
                }
            }
        }
    }
    if (iotype == SongIOType::RMT) {
        for (int i = 0; i < INSTRSNUM; i++) {
            if (g_Instruments.CalculateNotEmpty(i)) {
                instrumentSavedFlags[i] |= IF_NOEMPTY;
            }
        }
    }

    BOOL stereo = song.IsStereo();
    unsigned char scratch[std::max(CRmtModule::Track::MAX_LENGTH, CRmtModule::Instrument::MAX_LENGTH)] = {};
    std::map<int, CRmtModule::Instrument> instruments;
    for (int i = 0; i < INSTRSNUM; i++) {
        if (instrumentSavedFlags[i] != 0) {
            int length = g_Instruments.InstrToAta(i, scratch, sizeof(scratch), stereo);
            instruments.emplace(i, CRmtModule::Instrument::Parse(scratch, 0, length));
        }
    }
    std::map<int, CRmtModule::Track> tracks;
    for (int i = 0; i < TRACKSNUM; i++) {
        if (trackSavedFlags[i] != 0) {
            int length = g_Tracks.TrackToAta(i, scratch, ATARI_MAX_TRACK_LENGTH);
            if (length < 1) {
                throw CRmtModule::FormatException("Track " + std::to_string(i) + " has too many events to encode.");
            }
            tracks.emplace(i, CRmtModule::Track::Of(scratch, 0, length));
        }
    }

    // The song lines up to the last one with a go to or a track.
    std::vector<CRmtModule::SongLine> lines;
    int last = -1;
    for (int line = 0; line < SONGLEN; line++) {
        if (song.m_songgo[line] >= 0) {
            lines.push_back(CRmtModule::SongLine::GoTo(song.m_songgo[line]));
            last = line;
        } else {
            std::vector<int> numbers(channels);
            for (int channel = 0; channel < channels; channel++) {
                int number = song.m_song[line][channel];
                numbers[channel] = number >= 0 && number < TRACKSNUM ? number : CRmtModule::SongLine::EMPTY;
            }
            CRmtModule::SongLine songLine = CRmtModule::SongLine::Tracks(numbers);
            lines.push_back(songLine);
            if (songLine.HasTracks()) {
                last = line;
            }
        }
    }
    lines.resize(last + 1, CRmtModule::SongLine::GoTo(0));
    return CRmtModule(channels, g_Tracks.GetMaxTrackLength(), song.m_mainSpeed, song.m_instrumentSpeed, RMTFormatVersion::V1, instruments, tracks, lines);
}

CRmtModuleFile CRmtModuleCodec::FileFromSong(const CSong& song, int loadAddress) {
    CRmtModule module = FromSong(song, SongIOType::RMT);
    std::vector<std::string> instrumentNames;
    for (const auto& entry : module.GetInstruments()) {
        CString name = g_Instruments.GetName(entry.first);
        name.TrimRight();
        instrumentNames.push_back(std::string((LPCSTR)name));
    }
    return CRmtModuleFile(module, loadAddress, std::string((LPCSTR)song.GetName()), instrumentNames);
}

void CRmtModuleCodec::ToSong(const CRmtModule& module, CSong& song) {
    song.SetChannels(module.GetChannels());
    g_Tracks.SetMaxTrackLength(module.GetTrackLength());
    song.m_mainSpeed = module.GetSongSpeed();
    song.m_instrumentSpeed = module.GetInstrumentSpeed();
    g_Tracks.InitTracks();
    BOOL stereo = song.IsStereo();
    for (const auto& entry : module.GetInstruments()) {
        CRmtModule::Bytes bytes = entry.second.ToBytes();
        if (!g_Instruments.AtaToInstr(bytes.data(), entry.first, stereo)) {
            throw CRmtModule::FormatException("Instrument " + std::to_string(entry.first) + " does not decode.");
        }
        g_Instruments.Update(entry.first);
    }
    for (const auto& entry : module.GetTracks()) {
        CRmtModule::Bytes bytes = entry.second.GetBytes();
        if (!g_Tracks.AtaToTrack(bytes.data(), entry.second.GetLength(), entry.first)) {
            throw CRmtModule::FormatException("Track " + std::to_string(entry.first) + " does not decode.");
        }
    }
    // A go to beyond the complete lines (a final stereo
    // go to line is not one) or beyond the song jumps to 0.
    const std::vector<CRmtModule::SongLine>& lines = module.GetSongLines();
    int completeLines = module.SongDataLength() / song.GetChannels();
    for (int line = 0; line < static_cast<int>(lines.size()) && line < SONGLEN; line++) {
        const CRmtModule::SongLine& songLine = lines[line];
        if (songLine.IsGoTo()) {
            int target = songLine.GoToTarget();
            song.m_songgo[line] = target < completeLines && target < SONGLEN ? target : 0;
        } else {
            for (int channel = 0; channel < song.GetChannels(); channel++) {
                int number = songLine.TrackNumber(channel);
                song.m_song[line][channel] = number < TRACKSNUM ? number : -1;
            }
        }
    }
}

namespace {

void FillName(char* target, int length, const std::string& name) {
    std::memset(target, ' ', length);
    std::memcpy(target, name.data(), std::min(name.size(), static_cast<size_t>(length)));
}

} // namespace

void CRmtModuleCodec::NamesToSong(const CRmtModuleFile& file, CSong& song) {
    if (file.IsStripped()) {
        return;
    }
    FillName(song.m_songname, SONG_NAME_MAX_LEN, *file.GetSongName());
    size_t i = 0;
    for (const auto& entry : file.GetModule().GetInstruments()) {
        FillName(g_Instruments.GetName(entry.first), INSTRUMENT_NAME_MAX_LEN, (*file.GetInstrumentNames())[i++]);
    }
}
