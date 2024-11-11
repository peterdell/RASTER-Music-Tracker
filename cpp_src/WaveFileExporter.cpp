#include "stdafx.h"
#include "WaveFileExporter.h"
#include "WaveFile.h"
#include "GuiHelpers.h"
#include "LZSSFile.h"
#include "Atari6502.h"
#include "ChannelControl.h"



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

    CAtari::InitRMTRoutine();	// Reset the Atari memory 
    SetChannelOnOff(-1, 1);	// Unmute all channels

    // Create the sound buffer to copy from and to
    buffer = new BYTE[BUFFER_SIZE];
    memset(buffer, 0x80, BUFFER_SIZE);

    while (frames < pokeyStream.GetFirstCountPoint())
    {
        // Copy the SAP-R bytes to g_atarimem for this frame
        streambuffer = pokeyStream.GetStreamBuffer() + frames * frameSize;

        //for (int i = 0; i < frameSize; i++)
        //{
        //	g_atarimem[0xd200 + i] = streambuffer[i];
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

    // Finished doing WAV things...
    wavefile.CloseFile();

    // Also make sure to delete the buffer once it's no longer needed
    delete buffer;

    return true;
}