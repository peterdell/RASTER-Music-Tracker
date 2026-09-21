#include "StdAfx.h"

#include "AtariTrackerDriver.h"
#include "ChannelControl.h"
#include "LZSSFile.h"
#include "Song.h"

// Link-only stubs. PokeyStream.cpp's Record()/FinishedRecording()/
// StartRecording() call these, but tests here only exercise the pure state
// machine methods (SwitchIntoRecording/SwitchIntoStop/CallFromPlay/
// TrackSongLine/CallFromPlayBeat) and never call those three, so real
// bodies aren't needed - only the symbols, to satisfy the linker for this
// translation unit. GetByteAt() used to be stubbed here too, but now has a
// real body linked via AtariTrackerDriverCore.cpp (see
// plans/SONG_IO_SONG_REMAINING_PLAN.md).
int CAtariTrackerDriver::Init() { return 0; }
int CLZSSFile::GetFrameSize(const CSong&) { return 9; }

// g_ChannelControl is a real, already-tested CChannelControl (see
// ChannelControlTests.cpp) - FinishedRecording() calls SetAllChannelsOn() on
// it, but again, tests here never call FinishedRecording(), so this only
// needs to exist for linking.
CChannelControl g_ChannelControl(8);
