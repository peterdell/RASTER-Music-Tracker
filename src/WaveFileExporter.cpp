#include "StdAfx.h"
#include "WaveFileExporter.h"
#include "WaveFile.h"
#include "GuiHelpers.h"
#include "LZSSFile.h"
#include "AtariTrackerDriver.h"
#include "ChannelControl.h"
#include "AtariTrackerDriver.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;

bool CWaveFileExporter::ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory)
{
    CWaveFile wavefile{};

    BYTE* buffer = NULL;
    BYTE* streambuffer = NULL;
    const WAVEFORMATEX* wfm = NULL;
    int length = 0, frames = 0, offset = 0;
    const int frameSize = CLZSSFile::GetFrameSize(songExport.GetSong());

    ou.close();	// hack, just to be able to actually use the filename for now...

    if (!(wfm = pokey.GetSoundFormat()))
    {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    if (!wavefile.OpenFile(songExport.GetFilePath().GetBuffer(), wfm->nSamplesPerSec, wfm->wBitsPerSample, wfm->nChannels))
    {
        SendErrorMessage("Wave Export Failed", "Could not get sound format!");
        return false;
    }

    // Dump the POKEY registers from full song playback
    CPokeyStream& pokeyStream = songExport.GetSongContainer().GetModifiablePokeyStream();

    // Busy writing! TODO: Fix the timing overlap causing conflicts
    // JAC! Does this problem really still exist?
    pokeyStream.SetState(CPokeyStream::WRITE);

    g_AtariTrackerDriver->Init();	// Reset the Atari memory 
    SetAllChannelsOn();

    // Create the sound buffer to copy from and to
    auto bufferSize = CXPokey::BUFFER_SIZE;
    buffer = new BYTE[CXPokey::BUFFER_SIZE];
    memset(buffer, 0x80, bufferSize);

    while (frames < pokeyStream.GetFirstCountPoint())
    {
        // Copy the SAP-R bytes to memory for this frame
        streambuffer = pokeyStream.GetStreamBuffer() + frames * frameSize;

        //for (int i = 0; i < frameSize; i++)
        //{
        //	memory[0xd200 + i] = streambuffer[i];
        //}

        memory[RMTPLAYR_TRACKN_AUDF + 0] = streambuffer[0x00];
        memory[RMTPLAYR_TRACKN_AUDF + 1] = streambuffer[0x02];
        memory[RMTPLAYR_TRACKN_AUDF + 2] = streambuffer[0x04];
        memory[RMTPLAYR_TRACKN_AUDF + 3] = streambuffer[0x06];
        memory[RMTPLAYR_TRACKN_AUDC + 0] = streambuffer[0x01];
        memory[RMTPLAYR_TRACKN_AUDC + 1] = streambuffer[0x03];
        memory[RMTPLAYR_TRACKN_AUDC + 2] = streambuffer[0x05];
        memory[RMTPLAYR_TRACKN_AUDC + 3] = streambuffer[0x07];
        memory[RMTPLAYR_V_AUDCTL] = streambuffer[0x08];

        // Fill the POKEY buffer with 1 rendered chunk
        pokey.RenderSoundV2(songExport.GetSong().GetInstrumentSpeed(), buffer, length);

        // Write the buffer to WAV file
        wavefile.WriteWave(buffer, length);

        // Update the PokeyStream offset for the next frame
        frames++;
    }

    SetAllChannelsOff();

    // Finished doing WAV things...
    wavefile.CloseFile();

    // Also make sure to delete the buffer once it's no longer needed
    delete buffer;

    // TODO: Set channels on again?
    return true;
}