#include "stdafx.h"
#include "SongExport.h"

CSongExport::CSongExport(CSongContainer& songContainer, CString filePath) {
    m_songContainer = &songContainer;
    m_filePath = filePath;
}

CSong& CSongExport::GetSong() {
    return m_songContainer->GetSong();
}

const CPokeyStream& CSongExport::GetPokeyStream() {
    return  m_songContainer->GetPokeyStream();
}

CString CSongExport::GetFilePath() const {
    return m_filePath;
}
