#pragma once

#include "SongContainer.h"

// Song with lazily created dump of the Pokey stream.
class CSongExport
{
public:
    CSongExport(CSongContainer& songContainer, CString filePath);

    CSongContainer& GetSongContainer();

    CSong& GetSong();
    const CPokeyStream& GetPokeyStream();
    CString GetFilePath() const;


private:
    CSongContainer* m_songContainer;
    CString m_filePath;
};

