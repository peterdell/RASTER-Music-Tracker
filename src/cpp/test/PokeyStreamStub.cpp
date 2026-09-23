#include "StdAfx.h"

#include "AtariTrackerDriver.h"
#include "ChannelControl.h"
#include "Song.h"

// Link-only stubs. PokeyStream.cpp's Record()/FinishedRecording()/
// StartRecording() call these, but tests here only exercise the pure state
// machine methods (SwitchIntoRecording/SwitchIntoStop/CallFromPlay/
// TrackSongLine/CallFromPlayBeat) and never call those three, so real
// bodies aren't needed - only the symbols, to satisfy the linker for this
// translation unit. GetByteAt() used to be stubbed here too, but now has a
// real body linked via AtariTrackerDriverCore.cpp (see
// plans/SONG_IO_SONG_REMAINING_PLAN.md). CLZSSFile::GetFrameSize() used to be
// stubbed here too (always returning 9); it now has a real body linked via
// LZSSFile.cpp (see plans/SAP_LZSS_WAV_XEX_PLAN.md).
int CAtariTrackerDriver::Init() {
	return 0;
}

// Link-only no-op stub: CSong::DumpSongToPokeyStream() (Song_DumpSong.cpp,
// now linked - see Song_DumpSongStub.cpp) calls this only when g_rmtroutine
// is true, which none of its tests ever set (defaults FALSE - see
// SongEditingStub.cpp). Its real body lives in the still-unlinked
// AtariTrackerDriver.cpp (needs Global.h's IsSpecialProveMode()) - same
// "link-only, never really called" treatment as Init() above.
void CAtariTrackerDriver::Play() {
}

// g_ChannelControl is a real, already-tested CChannelControl (see
// ChannelControlTests.cpp) - FinishedRecording() calls SetAllChannelsOn() on
// it, but again, tests here never call FinishedRecording(), so this only
// needs to exist for linking.
CChannelControl g_ChannelControl(8);
