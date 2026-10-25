#include "AtariTrackerDriver.h"
#include "AtariTrackerDriver.h"
#include "Global.h"
#include "LZSSFile.h"
#include "Messages.h"
#include "StdAfx.h"
#include "WaveFile.h"
#include "WaveFileExporter.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;

bool CWaveFileExporter::ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory) {
    CWaveFile wavefile{};

    BYTE* buffer = NULL;
    BYTE* streambuffer = NULL;
    const WAVEFORMATEX* wfm = NULL;
    int length = 0, frames = 0, offset = 0;
    const int instrumentSpeed = songExport.GetSong().GetInstrumentSpeed();
    int chunkRemaining = 0;
    int partGroup = 0;
    const int frameSize = CLZSSFile::GetFrameSize(songExport.GetSong());

    ou.close(); // hack, just to be able to actually use the filename for now...

    if (!(wfm = pokey.GetSoundFormat())) {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    if (!wavefile.OpenFile(songExport.GetFilePath().GetBuffer(), wfm->nSamplesPerSec, wfm->wBitsPerSample, wfm->nChannels)) {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    // Dump the POKEY registers from full song playback
    CPokeyStream& pokeyStream = songExport.GetSongContainer().GetModifiablePokeyStream();

    // Busy writing! The timing overlap the old note here asked about is real:
    // the timer thread would render through the same POKEY
    // (CSong::TimerRoutine -> RenderSound1_50) while this renders through
    // RenderSoundV2, and the WRITE state below does not stop it - the song's
    // m_pokeyStream is already null once DumpSongToPokeyStream() returned.
    // Reproduced with a script run (Rmt.exe /SCRIPT): the export crashed
    // mid-file with a different length each time. The timer is stopped for
    // the whole export by the caller's CExportSection (GuiHelpers.h).
    pokeyStream.SetState(CPokeyStream::WRITE);

    g_AtariTrackerDriver->Init(); // Reset the Atari memory
    g_ChannelControl.SetAllChannelsOn();

    // Create the sound buffer to copy from and to
    auto bufferSize = CXPokey::BUFFER_SIZE;
    buffer = new BYTE[CXPokey::BUFFER_SIZE];
    memset(buffer, 0x80, bufferSize);

    while (frames < pokeyStream.GetFirstCountPoint()) {
        // Copy the SAP-R bytes of this frame straight to the POKEY shadows at
        // $D200/$D210, where CopyAtariMemoryToPokey() reads them - the same
        // direct route the Java port always used. Until plan 46 the bytes
        // went into the Patch 16 driver's variables instead, and
        // RenderPartV2() ran the loaded driver's rmt_set_pokey routine to carry
        // them here: with a v1-family driver loaded, that clobbered the
        // player (TRACKN_AUDC is $3180-$3187, across the v1 dispatch table
        // at $3182) and rendered the WAV from the driver's own stale
        // variables rather than the recorded stream. A SAP-R frame is the
        // nine registers in $D200 order; in stereo the second POKEY's nine
        // bytes come first in the stream (CPokeyStream::Record()).
        streambuffer = pokeyStream.GetStreamBuffer() + frames * frameSize;

        if (frameSize == 18) {
            for (int i = 0; i < 9; i++) {
                memory[0xd200 + i] = streambuffer[0x09 + i];
                memory[0xd210 + i] = streambuffer[0x00 + i];
            }
        } else {
            for (int i = 0; i < 9; i++) {
                memory[0xd200 + i] = streambuffer[i];
            }
        }

        // One stream frame is one driver call, 1/instrumentSpeed of a video
        // frame of sound - the rounding rests are spread over the video
        // frame's group so every group still sums to a whole chunk.
        if (partGroup == 0) {
            chunkRemaining = pokey.GetChunkSize();
            partGroup = instrumentSpeed;
        }
        int partSize = (chunkRemaining / partGroup) & 0xfffe;
        chunkRemaining -= partSize;
        partGroup--;
        pokey.RenderPartV2(partSize, buffer, length);

        // Write the buffer to WAV file
        wavefile.WriteWave(buffer, length);

        // Update the PokeyStream offset for the next frame
        frames++;
    }

    // Finished doing WAV things...
    wavefile.CloseFile();

    // Also make sure to delete the buffer once it's no longer needed
    delete buffer;

    return true;
}