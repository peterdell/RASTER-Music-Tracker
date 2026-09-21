#include "gtest/gtest.h"

#include "General.h" // PlayMode (PLAY_SONG/PLAY_BLOCK)
#include "PokeyStream.h"

#include <fstream>

// StartRecording()/Record()'s data path/FinishedRecording() all need a real
// CAtariTrackerDriver (itself needing a real CAtari) or a real CSong, both
// blocked by CSong's g_Atari-coupled constructor (see plans/NOTES.md).
// Tests here exercise only CPokeyStream's pure state-machine methods, which
// never touch m_AtariTrackerDriver or need a CSong, plus the two early-return
// safety paths of Record()/WriteToFile() that don't touch them either.

TEST(PokeyStreamTest, NewStreamIsNotRecording) {
    CPokeyStream stream;
    EXPECT_FALSE(stream.IsRecording());
    EXPECT_FALSE(stream.IsWriting());
    EXPECT_EQ(stream.GetCurrentFrame(), 0);
}

// Characterization: Clear() resets frame/line counters but does NOT touch
// m_recordState, so a stream that was mid-recording stays "recording"
// (by IsRecording()'s definition) after Clear().
TEST(PokeyStreamTest, ClearDoesNotResetRecordingState) {
    CPokeyStream stream;
    stream.SetState(CPokeyStream::RECORD);

    stream.Clear();

    EXPECT_TRUE(stream.IsRecording());
}

TEST(PokeyStreamTest, SwitchIntoRecordingCapturesCurrentFrameAsFirstCountPoint) {
    CPokeyStream stream;
    int result = stream.SwitchIntoRecording();

    EXPECT_TRUE(stream.IsRecording());
    EXPECT_FALSE(stream.IsWriting());
    EXPECT_EQ(result, 0);
    EXPECT_EQ(stream.GetFirstCountPoint(), 0);
}

TEST(PokeyStreamTest, SwitchIntoStopComputesSecondAndThirdCountPoints) {
    CPokeyStream stream;
    stream.SwitchIntoRecording(); // m_FirstCountPoint = 0 (frame counter never advances without a driver)

    int result = stream.SwitchIntoStop();

    EXPECT_FALSE(stream.IsRecording());
    EXPECT_EQ(result, 0);
    EXPECT_EQ(stream.GetSecondCountPoint(), 0);
    EXPECT_EQ(stream.GetThirdCountPoint(), 0);
}

TEST(PokeyStreamTest, CallFromPlayOnlyFiresInStartState) {
    CPokeyStream stream;
    // Not in START state: CallFromPlay() is a no-op.
    stream.CallFromPlay(PLAY_SONG, 3, 7);
    EXPECT_FALSE(stream.IsRecording());

    stream.SetState(CPokeyStream::START);
    stream.CallFromPlay(PLAY_SONG, 3, 7);
    EXPECT_TRUE(stream.IsRecording()); // transitions to RECORD

    // Indirect proof that CallFromPlay() pre-incremented songLine 7's count:
    // a single TrackSongLine(7) call now already detects one full loop,
    // where a fresh stream would need two calls with the same line.
    stream.TrackSongLine(7);
    EXPECT_EQ(stream.LoopCount(), 1);
}

// TrackSongLine() is the loop-point detector driven by song-line playback.
// Traced by hand: lines 0,1,2 play once each (no loop yet), then 0,1,2 play
// a second time (first loop detected, self-re-arms via SwitchIntoRecording()),
// then line 0 plays a third time (second loop detected -> fully resolved).
TEST(PokeyStreamTest, TrackSongLineDetectsLoopOnSecondFullPassAndResolvesOnThird) {
    CPokeyStream stream;
    stream.SetState(CPokeyStream::RECORD);

    EXPECT_FALSE(stream.TrackSongLine(0));
    EXPECT_FALSE(stream.TrackSongLine(1));
    EXPECT_FALSE(stream.TrackSongLine(2));
    EXPECT_FALSE(stream.TrackSongLine(0)); // 1st loop detected; self-re-arms to RECORD
    EXPECT_TRUE(stream.IsRecording());
    EXPECT_FALSE(stream.TrackSongLine(1));
    EXPECT_FALSE(stream.TrackSongLine(2));
    EXPECT_TRUE(stream.TrackSongLine(0)); // 2nd loop detected -> resolved

    EXPECT_FALSE(stream.IsRecording()); // SwitchIntoStop() left it in STOP
    EXPECT_EQ(stream.LoopCount(), 2);
    EXPECT_EQ(stream.GetSonglineCount(), 4); // settled once the 1st loop closed the table
}

// CallFromPlayBeat() is CallFromPlay/TrackSongLine's simpler sibling used
// for beat-level tracking. Unlike TrackSongLine(), it does NOT call
// SwitchIntoRecording() after detecting the first loop, so it leaves the
// stream in WRITE state - and since it only acts while state==RECORD, a
// second loop can only ever be detected if the caller manually re-arms the
// state (SetState(RECORD)) in between, unlike TrackSongLine()'s self-arming.
TEST(PokeyStreamTest, CallFromPlayBeatRequiresManualRearmForSecondLoop) {
    CPokeyStream stream;
    stream.SetState(CPokeyStream::RECORD);

    EXPECT_FALSE(stream.CallFromPlayBeat(5));
    EXPECT_FALSE(stream.CallFromPlayBeat(5)); // 1st loop detected, state -> WRITE
    // IsRecording() means "not stopped" (true for RECORD, WRITE, and START
    // alike) - WRITE still counts, so this is true here, not false.
    EXPECT_TRUE(stream.IsRecording());
    EXPECT_TRUE(stream.IsWriting());
    EXPECT_EQ(stream.LoopCount(), 1);

    // Without re-arming, further calls are gated out entirely (no-ops).
    EXPECT_FALSE(stream.CallFromPlayBeat(5));
    EXPECT_EQ(stream.LoopCount(), 1);

    stream.SetState(CPokeyStream::RECORD); // manual re-arm
    EXPECT_TRUE(stream.CallFromPlayBeat(5)); // 2nd loop detected
    EXPECT_EQ(stream.LoopCount(), 2);
}

TEST(PokeyStreamTest, RecordIsANoOpBeforeStartRecording) {
    CPokeyStream stream; // m_recordState == STOP by default
    stream.Record(); // must not dereference the (null) tracker driver

    stream.SetState(CPokeyStream::START);
    stream.Record(); // also an early return, "too soon" per its own comment
}

TEST(PokeyStreamTest, WriteToFileIsANoOpWithoutAStreamBuffer) {
    CPokeyStream stream; // StartRecording() was never called, so there's no buffer
    std::ofstream neverOpened; // WriteToFile() takes std::ofstream& specifically

    stream.WriteToFile(neverOpened, 10, 0);

    // m_StreamBuffer is null, so WriteToFile() returns before ever touching
    // `neverOpened` - this test just confirms the call completes safely.
    EXPECT_FALSE(neverOpened.is_open());
}
