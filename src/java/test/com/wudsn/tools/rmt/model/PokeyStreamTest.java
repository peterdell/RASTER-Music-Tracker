package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/PokeyStreamTests.cpp. */
class PokeyStreamTest {

	@Test
	void newStreamIsNotRecording() {
		PokeyStream stream = new PokeyStream();
		assertFalse(stream.isRecording());
		assertFalse(stream.isWriting());
		assertEquals(0, stream.getCurrentFrame());
	}

	// Characterization: clear() resets frame/line counters but does NOT
	// touch recordState, so a stream that was mid-recording stays
	// "recording" (by isRecording()'s definition) after clear().
	@Test
	void clearDoesNotResetRecordingState() {
		PokeyStream stream = new PokeyStream();
		stream.setState(StreamState.RECORD);

		stream.clear();

		assertTrue(stream.isRecording());
	}

	@Test
	void switchIntoRecordingCapturesCurrentFrameAsFirstCountPoint() {
		PokeyStream stream = new PokeyStream();
		int result = stream.switchIntoRecording();

		assertTrue(stream.isRecording());
		assertFalse(stream.isWriting());
		assertEquals(0, result);
		assertEquals(0, stream.getFirstCountPoint());
	}

	@Test
	void switchIntoStopComputesSecondAndThirdCountPoints() {
		PokeyStream stream = new PokeyStream();
		stream.switchIntoRecording(); // firstCountPoint = 0 (frame counter never advances without a driver)

		int result = stream.switchIntoStop();

		assertFalse(stream.isRecording());
		assertEquals(0, result);
		assertEquals(0, stream.getSecondCountPoint());
		assertEquals(0, stream.getThirdCountPoint());
	}

	@Test
	void callFromPlayOnlyFiresInStartState() {
		PokeyStream stream = new PokeyStream();
		// Not in START state: callFromPlay() is a no-op.
		stream.callFromPlay(PlayMode.PLAY_SONG, 3, 7);
		assertFalse(stream.isRecording());

		stream.setState(StreamState.START);
		stream.callFromPlay(PlayMode.PLAY_SONG, 3, 7);
		assertTrue(stream.isRecording()); // transitions to RECORD

		// Indirect proof that callFromPlay() pre-incremented songLine 7's count:
		// a single trackSongLine(7) call now already detects one full loop,
		// where a fresh stream would need two calls with the same line.
		stream.trackSongLine(7);
		assertEquals(1, stream.loopCount());
	}

	// trackSongLine() is the loop-point detector driven by song-line playback.
	// Traced by hand: lines 0,1,2 play once each (no loop yet), then 0,1,2 play
	// a second time (first loop detected, self-re-arms via switchIntoRecording()),
	// then line 0 plays a third time (second loop detected -> fully resolved).
	@Test
	void trackSongLineDetectsLoopOnSecondFullPassAndResolvesOnThird() {
		PokeyStream stream = new PokeyStream();
		stream.setState(StreamState.RECORD);

		assertFalse(stream.trackSongLine(0));
		assertFalse(stream.trackSongLine(1));
		assertFalse(stream.trackSongLine(2));
		assertFalse(stream.trackSongLine(0)); // 1st loop detected; self-re-arms to RECORD
		assertTrue(stream.isRecording());
		assertFalse(stream.trackSongLine(1));
		assertFalse(stream.trackSongLine(2));
		assertTrue(stream.trackSongLine(0)); // 2nd loop detected -> resolved

		assertFalse(stream.isRecording()); // switchIntoStop() left it in STOP
		assertEquals(2, stream.loopCount());
		assertEquals(4, stream.getSonglineCount()); // settled once the 1st loop closed the table
	}

	// callFromPlayBeat() is callFromPlay/trackSongLine's simpler sibling used
	// for beat-level tracking. Unlike trackSongLine(), it does NOT call
	// switchIntoRecording() after detecting the first loop, so it leaves the
	// stream in WRITE state - and since it only acts while state==RECORD, a
	// second loop can only ever be detected if the caller manually re-arms
	// the state (setState(RECORD)) in between, unlike trackSongLine()'s
	// self-arming.
	@Test
	void callFromPlayBeatRequiresManualRearmForSecondLoop() {
		PokeyStream stream = new PokeyStream();
		stream.setState(StreamState.RECORD);

		assertFalse(stream.callFromPlayBeat(5));
		assertFalse(stream.callFromPlayBeat(5)); // 1st loop detected, state -> WRITE
		// isRecording() means "not stopped" (true for RECORD, WRITE, and START
		// alike) - WRITE still counts, so this is true here, not false.
		assertTrue(stream.isRecording());
		assertTrue(stream.isWriting());
		assertEquals(1, stream.loopCount());

		// Without re-arming, further calls are gated out entirely (no-ops).
		assertFalse(stream.callFromPlayBeat(5));
		assertEquals(1, stream.loopCount());

		stream.setState(StreamState.RECORD); // manual re-arm
		assertTrue(stream.callFromPlayBeat(5)); // 2nd loop detected
		assertEquals(2, stream.loopCount());
	}

	@Test
	void recordIsANoOpBeforeStartRecording() {
		PokeyStream stream = new PokeyStream(); // recordState == STOP by default
		stream.record(); // must not touch a (nonexistent) tracker driver

		stream.setState(StreamState.START);
		stream.record(); // also an early return, "too soon" per its own comment
	}

	@Test
	void getFrameBytesReturnsEmptyWithoutAStreamBuffer() {
		PokeyStream stream = new PokeyStream(); // startRecording() was never called, so there's no buffer

		byte[] bytes = stream.getFrameBytes(10, 0);

		assertEquals(0, bytes.length);
	}
}
