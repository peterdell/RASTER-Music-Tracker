#pragma once

#include "RmtModule.h"
#include "RmtModuleFile.h"
#include "SongTypes.h"

class CSong;

// The conversions between a CSong and a CRmtModule (plans/60_RMT_MODULE_CONTAINER_PLAN.md,
// M6 - RmtModuleCodec.java in the Java port), kept out of the container so it
// stays reusable without RMT's editing model. They reuse the per-entity
// codecs RMT has always used - CInstruments::InstrToAta()/AtaToInstr(),
// CTracks::TrackToAta()/AtaToTrack() - and encode the song lines themselves.
// The selection rule of MakeModule() is decided here: SongIOType::RMT keeps
// every non-empty track and instrument, any other type only the used ones.
class CRmtModuleCodec {
public:
    // The module of the song. Throws CRmtModule::FormatException for a track
    // too event-dense to encode in 256 bytes, or a song speed/instrument
    // speed outside the header's range.
    static CRmtModule FromSong(const CSong& song, SongIOType iotype);
    // The .rmt file of the song as RMT saves it: the module with every
    // non-empty track and instrument, and the names segment.
    static CRmtModuleFile FileFromSong(const CSong& song, int loadAddress);
    // The module into the song - the channel count, track length and speeds,
    // every instrument and track decoded in place, the song lines set; the
    // lines beyond the module's are left as they are. Throws
    // CRmtModule::FormatException if an instrument's or a track's bytes do
    // not decode.
    static void ToSong(const CRmtModule& module, CSong& song);
    // The names of a non-stripped file into the song, space-padded as
    // LoadRMT() has always stored them; a stripped file leaves them alone.
    static void NamesToSong(const CRmtModuleFile& file, CSong& song);
};
