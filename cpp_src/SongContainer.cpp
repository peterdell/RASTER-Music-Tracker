#include "stdafx.h"
#include "SongContainer.h"

#include "GuiHelpers.h"

CSongContainer::CSongContainer(CSong& song) {
    m_song = &song;
    m_pokeyStreamReady = false;

    if (m_song->GetPlayMode() != MPLAY_STOP) {
        AfxThrowInvalidArgException();
    }
}

CSongContainer::~CSongContainer() {
    m_pokeyStream.FinishedRecording();
}

CSong& CSongContainer::GetSong() {
    return *m_song;
}

const CPokeyStream& CSongContainer::GetPokeyStream() {
    if (!m_pokeyStreamReady) {
        SetStatusBarText("Generating stream data ...");
        m_song->DumpSongToPokeyStream(m_pokeyStream);
        m_pokeyStreamReady = true;
    }
    return m_pokeyStream;
}