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

    CSong* m_song;
    UINT m_timerRoutine;
    bool volatile busyInCallback;
    bool volatile m_timerRoutineProcessed;
};

