#include "StdAfx.h"

#include "AtariTrackerDriver.h"
#include "ChannelControl.h"
#include "LZSSFile.h"
#include "Song.h"

// Link-only stubs. PokeyStream.cpp's Record()/FinishedRecording()/
// StartRecording() call these, but tests here only exercise the pure state
// machine methods (SwitchIntoRecording/SwitchIntoStop/CallFromPlay/
// TrackSongLine/CallFromPlayBeat) and never call those three, so real
// bodies (which would need an actual CAtari-backed CAtariTrackerDriver, or a
// real CSong - both blocked by CSong's g_Atari-coupled constructor, see
// plans/NOTES.md) aren't needed - only the symbols, to satisfy the linker
// for this translation unit.
byte CAtariTrackerDriver::GetByteAt(const MemoryAddress) { return 0; }
int CAtariTrackerDriver::Init() { return 0; }
int CLZSSFile::GetFrameSize(const CSong&) { return 9; }

// g_ChannelControl is a real, already-tested CChannelControl (see
// ChannelControlTests.cpp) - FinishedRecording() calls SetAllChannelsOn() on
// it, but again, tests here never call FinishedRecording(), so this only
// needs to exist for linking.
CChannelControl g_ChannelControl(8);
