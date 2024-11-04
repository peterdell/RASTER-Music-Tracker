#include "StdAfx.h"
#include "GuiHelpers.h"
#include "Song.h"
#include "Instruments.h"
#include "Atari6502.h"
#include "PokeyStream.h"
#include "ChannelControl.h"


extern CInstruments	g_Instruments;
extern BOOL volatile g_rmtroutine;
extern long g_playtime;


/// <summary>
/// Get the Pokey registers to be dumped to a stream buffer.
/// GUI is disabled but MFC messages are being pumped, so the screen is updated
/// </summary>
/// <returns></returns>
void CSong::DumpSongToPokeyStream(CPokeyStream& pokeyStream, int playmode, int songline, int trackline)
{
    CString statusBarLog;

    Stop();					// Make sure RMT is stopped 
    CAtari::InitRMTRoutine();	// Reset the RMT routines 
    SetChannelOnOff(-1, 0);	// Switch all channels off 

    // Activate stream recording mode.
    m_pokeyStream = &pokeyStream;
    m_pokeyStream->StartRecording();

    // Play song using the chosen playback parameters
    // If no argument was passed, Play from start will be assumed
    m_songactiveline = songline;
    m_trackactiveline = trackline;
    Play(playmode, m_followplay);

    // Wait in a tight loop pumping messages until the playback stops
    {
        DisableEventSection section;

        // The SAP-R dumper is running during that time...
        while (m_play != MPLAY_STOP)
        {
            // 1 VBI of module playback
            PlayVBI();

            // Increment the timer shown during playback
            g_playtime++;

            // Multiple RMT routine calls will be processed if needed
            for (int i = 0; i < m_instrumentSpeed; i++)
            {
                // 1 VBI of RMT routine (for instruments)
                if (g_rmtroutine)
                {
                    CAtari::PlayRMT();
                }
                // Transfer from g_atarimem to POKEY buffer
                pokeyStream.Record();
            }

            // Update the screen only once every few frames
            // Displaying everything in real time slows things down considerably!
            if (!RefreshScreen(1)) {
                continue;
            }

            // Display the number of frames dumped so far
            statusBarLog.Format("Generating Pokey stream, playing song in quick mode... %i frames recorded", pokeyStream.GetCurrentFrame());
            SetStatusBarText(statusBarLog);
        }

        // End playback now, the SAP-R data should have been dumped successfully!
        Stop();

        // Deactivate stream recording.
        m_pokeyStream = nullptr;

        statusBarLog.Format("Done... %i frames recorded in total, Loop point found at frame %i", pokeyStream.GetCurrentFrame(), pokeyStream.GetFirstCountPoint());
        SetStatusBarText(statusBarLog);
    }
}
