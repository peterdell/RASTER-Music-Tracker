#include "AtariTrackerDriver.h"
#include "Global.h"
#include "GuiHelpers.h"
#include "Instruments.h"
#include "PokeyStream.h"
#include "Song.h"
#include "StdAfx.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;
extern CInstruments g_Instruments;
extern BOOL volatile g_rmtroutine;
extern long g_playtime;

/// <summary>
/// Get the Pokey registers to be dumped to a stream buffer: the song is
/// played in quick mode through the tracker driver until its loop point.
/// Runs inside the caller's CExportSection (window disabled, timer stopped);
/// the screen is not redrawn meanwhile - the song is in playback state and
/// would show itself racing (plans/24_EXPORT_SCREEN_UPDATES_PLAN.md) - only
/// the status bar reports the progress, a few times per second. The cursor
/// and the play time are as before the dump afterwards.
/// </summary>
void CSong::DumpSongToPokeyStream(CPokeyStream& pokeyStream, PlayMode playMode, int songline, int trackline) {
    CString statusBarLog;
    int savedSongActiveLine = m_songactiveline;
    int savedTrackActiveLine = m_trackactiveline;
    long savedPlayTime = g_playtime;

    Stop(); // Make sure RMT is stopped
    g_AtariTrackerDriver->Init(); // Reset the RMT routines
    g_ChannelControl.SetAllChannelsOff();

    // Activate stream recording mode.
    m_pokeyStream = &pokeyStream;
    m_pokeyStream->StartRecording(*this, g_AtariTrackerDriver);

    // Play song using the chosen playback parameters
    // If no argument was passed, Play from start will be assumed
    m_songactiveline = songline;
    m_trackactiveline = trackline;
    Play(playMode, m_followplay);

    // The recording loop, until the playback stops
    {
        DWORD lastStatusTick = GetTickCount();

        // The SAP-R dumper is running during that time...
        while (m_play != PLAY_STOP) {
            // 1 VBI of module playback
            PlayVBI();

            // Increment the timer shown during playback
            g_playtime++;

            // Multiple RMT routine calls will be processed if needed
            for (int i = 0; i < m_instrumentSpeed; i++) {
                // 1 VBI of RMT routine (for instruments)
                if (g_rmtroutine) {
                    g_AtariTrackerDriver->Play();
                }
                // Transfer from memory to POKEY buffer
                pokeyStream.Record();
            }

            // The number of frames dumped so far, a few times per second
            DWORD now = GetTickCount();
            if (now - lastStatusTick >= 250) {
                lastStatusTick = now;
                statusBarLog.Format("Generating Pokey stream, playing song in quick mode... %i frames recorded", pokeyStream.GetCurrentFrame());
                SetStatusBarText(statusBarLog);
            }
        }
        g_AtariTrackerDriver->Init(); // Reset the RMT routines

        // End playback now, the SAP-R data should have been dumped successfully!
        Stop();

        // Deactivate stream recording.
        m_pokeyStream = nullptr;

        // The dump leaves no traces: the cursor and the play time as before
        m_songactiveline = savedSongActiveLine;
        m_trackactiveline = savedTrackActiveLine;
        g_playtime = savedPlayTime;

        statusBarLog.Format("Done... %i frames recorded in total, Loop point found at frame %i", pokeyStream.GetCurrentFrame(), pokeyStream.GetFirstCountPoint());
        SetStatusBarText(statusBarLog);
    }
}
