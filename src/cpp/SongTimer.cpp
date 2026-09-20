#include "SongTimer.h"


extern BOOL g_closeApplication;	// Set when the application is busy shutting down


void CALLBACK TimerCallback(UINT uTimerID, UINT uMsg, DWORD_PTR dwUser, DWORD_PTR dw1, DWORD_PTR dw2)
{
    ((CSongTimer*)dwUser)->Callback();

}

void CSongTimer::KillTimer()
{
    if (m_timerRoutine)
    {
        timeKillEvent(m_timerRoutine);
        m_timerRoutine = 0;
        m_song = nullptr;
    }
}

void CSongTimer::StopTimer()
{
    while (busyInCallback) {};			// Wait until not in timer handler
    KillTimer();					// Kill the timer
    while (busyInCallback) {};			// Make sure not in the timer handler
}

void CSongTimer::SetTimer(CSong& song, int ms)
{
    KillTimer();
    this->m_song = &song;
    m_timerRoutine = timeSetEvent(ms, 0, TimerCallback, (DWORD_PTR)(this), TIME_PERIODIC);
}

void CSongTimer::Callback() {
    busyInCallback = true;
    m_song->TimerRoutine();
    m_timerRoutineProcessed = true;	// TimerRoutine took place
    busyInCallback = false;
}
void CSongTimer::WaitForTimerRoutineProcessed()
{
    // If there is any timer at all
    if (m_timerRoutine)
    {
        m_timerRoutineProcessed = false;
        while (!m_timerRoutineProcessed && !g_closeApplication) {
            // Busy Waiting
        };
    }
}
