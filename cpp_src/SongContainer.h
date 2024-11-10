#pragma once

#include "Song.h"
#include "PokeyStream.h"

// Song with lazily created dump of the Pokey stream.
class CSongContainer
{
public:
    CSongContainer(CSong& song);
    ~CSongContainer();

    CSong& GetSong();
    const CPokeyStream& GetPokeyStream();
    CPokeyStream& GetModifiablePokeyStream(); // TODO: Probably obsolete workaround for WAV generation

private:
    CSong* m_song;
    CPokeyStream m_pokeyStream;
    bool m_pokeyStreamReady;
};

