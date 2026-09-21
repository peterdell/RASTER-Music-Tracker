#pragma once

#include "StdAfx.h"

#include "Song.h"

class CSongTimer
{

public:
    /// <summary>
    /// Immediately kill the timer event
    /// </summary>
    void KillTimer();

    /// <summary>
    /// Stop the timer and make sure that the timer event is not running
    /// </summary>
    void StopTimer();

    /// <summary>
    /// Set the timing of how often the CSong::TimerRoutine is being called.
    /// Depends on PAL or NTSC timing.
    /// </summary>
    /// <param name="ms">ms between calls (17=NTSC, 20=PAL)</param>
    void SetTimer(CSong& song, int ms);


    void Callback();

    /// <summary>
    /// Wait for the Timer Routine to run at least once
    /// </summary>
    void WaitForTimerRoutineProcessed();

private:

    // Given explicit defaults (matching what the global g_SongTimer already
    // got for free from static zero-initialization) so any other instance -
    // e.g. one constructed directly in a test - is just as well-defined.
    // m_timerRoutine == 0 is also what WaitForTimerRoutineProcessed()/
    // StopTimer()/KillTimer() rely on to stay safe no-ops when no real timer
    // was ever started via SetTimer().
    CSong* m_song = nullptr;
    UINT m_timerRoutine = 0;
    bool volatile busyInCallback = false;
    bool volatile m_timerRoutineProcessed = false;
};

