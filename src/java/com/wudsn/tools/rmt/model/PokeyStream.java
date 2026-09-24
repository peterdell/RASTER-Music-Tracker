package com.wudsn.tools.rmt.model;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * Ported from CPokeyStream (src/cpp/PokeyStream.h/.cpp) - the pure
 * state-machine methods {@code PokeyStreamTests.cpp} exercises
 * ({@link #switchIntoRecording}/{@link #switchIntoStop}/
 * {@link #callFromPlay}/{@link #trackSongLine}/{@link #callFromPlayBeat},
 * plus {@link #clear} and the getters), plus the two early-return guard
 * clauses of {@link #record}/{@link #writeToFile} that don't touch a real
 * driver/stream buffer.
 *
 * <p><b>{@code StartRecording}/{@code FinishedRecording} deferred
 * entirely, and {@link #record}/{@link #writeToFile}'s real bodies
 * deferred too</b>: none of these touch anything beyond the tested guard
 * clauses in {@code PokeyStreamTests.cpp} - the actual Pokey-register
 * recording pipeline (needs a real {@link AtariTrackerDriver}, a growable
 * byte buffer, and {@code CSong::DumpSongToPokeyStream()}'s wider
 * machinery) is exercised for real in {@code SongEditingTests.cpp}
 * instead, which isn't ported yet. Consequently {@code m_StreamBuffer}/
 * {@code m_BufferSize}/{@code m_FrameSize}/{@code m_AtariTrackerDriver}
 * aren't modeled here at all - nothing observable in this port depends on
 * them.
 */
public final class PokeyStream {

	private StreamState recordState = StreamState.STOP;
	private int frameCounter;
	private int songlineCounter;
	private int songLoopedCounter;
	private final int[] playCount = new int[Song.SONGLEN];
	private final int[] framesPerSongline = new int[Song.SONGLEN];
	private final int[] offsetPerSongline = new int[Song.SONGLEN];
	private int firstCountPoint;
	private int secondCountPoint;
	private int thirdCountPoint;

	/** Resets frame/line counters but not {@code recordState} - see {@code PokeyStreamTests.cpp}'s own characterization of this quirk. */
	public void clear() {
		frameCounter = 0;
		songlineCounter = 0;
		Arrays.fill(playCount, 0);
		Arrays.fill(framesPerSongline, 0);
		Arrays.fill(offsetPerSongline, 0);
		songLoopedCounter = 0;
	}

	/** True for {@link StreamState#RECORD}, {@link StreamState#WRITE}, and {@link StreamState#START} alike - "not stopped", not "actively recording". */
	public boolean isRecording() {
		return recordState != StreamState.STOP;
	}

	public boolean isWriting() {
		return recordState == StreamState.WRITE;
	}

	public void setState(StreamState newState) {
		recordState = newState;
	}

	public int getCurrentFrame() {
		return frameCounter;
	}

	public int getFirstCountPoint() {
		return firstCountPoint;
	}

	public int getSecondCountPoint() {
		return secondCountPoint;
	}

	public int getThirdCountPoint() {
		return thirdCountPoint;
	}

	public int loopCount() {
		return songLoopedCounter;
	}

	public int getSonglineCount() {
		return songlineCounter;
	}

	public int getFramesPerSongline(int songLine) {
		return framesPerSongline[songLine];
	}

	public int getOffsetPerSongline(int songLine) {
		return offsetPerSongline[songLine];
	}

	public int switchIntoRecording() {
		recordState = StreamState.RECORD;
		firstCountPoint = frameCounter;
		return firstCountPoint;
	}

	public int switchIntoStop() {
		recordState = StreamState.STOP;
		secondCountPoint = frameCounter - firstCountPoint;
		if (secondCountPoint < 0) {
			secondCountPoint = 0;
		}
		thirdCountPoint = firstCountPoint - secondCountPoint;
		if (thirdCountPoint < 0) {
			thirdCountPoint = 0;
		}
		return secondCountPoint;
	}

	public void callFromPlay(PlayMode playerState, int trackLine, int songLine) {
		// The SAP-R dumper initialisation flag was set
		if (recordState == StreamState.START) {
			Arrays.fill(playCount, 0); // Reset lines play counter first
			if (playerState == PlayMode.PLAY_BLOCK) {
				playCount[trackLine] += 1; // Increment the track line play count early, so it will be detected as the selection block loop
			} else {
				playCount[songLine] += 1; // Increment the line play count early, to ensure that same line will be detected again as the loop point
			}
			recordState = StreamState.RECORD; // Set the SAPR dumper with the "is currently recording data" flag
		}
	}

	public boolean trackSongLine(int trackedInstance) {
		if (recordState == StreamState.RECORD) { // The SAPR dumper is running with the "is currently recording data" flag
			playCount[trackedInstance] += 1; // Increment the position counter by 1

			// If the Songline is played for the first time, the current frames count will be used for its index
			if (songLoopedCounter < 1) {
				// At least 1 Songline will be played, increment the count early
				songlineCounter++;
				offsetPerSongline[songlineCounter] = frameCounter;
			}

			int count = playCount[trackedInstance]; // Fetch that line play count for the next step
			if (count > 1) {
				// A value above 1 means a full playback loop has been completed, the line play count incremented twice
				songLoopedCounter++; // Increment the dumper iteration count by 1
				recordState = StreamState.WRITE; // Set the "write SAP-R data to file" flag
				if (songLoopedCounter == 1) {
					Arrays.fill(playCount, 0); // Reset the lines play count before the next step
					playCount[trackedInstance] += 1; // Increment the line play count early, to ensure that same line will be detected again as the loop point for the next dumper iteration

					switchIntoRecording();
				}
				if (songLoopedCounter == 2) {
					switchIntoStop();
					return true;
				}
			}
		}
		return false;
	}

	public boolean callFromPlayBeat(int trackedInstance) {
		if (recordState == StreamState.RECORD) {
			// The SAPR dumper is running with the "is currently recording data" flag
			playCount[trackedInstance] += 1; // Increment the position counter by 1

			int count = playCount[trackedInstance]; // Fetch that line play count for the next step
			if (count > 1) {
				// A value above 1 means a full playback loop has been completed, the line play count incremented twice
				songLoopedCounter++; // Increment the dumper iteration count by 1
				recordState = StreamState.WRITE; // Set the "write SAP-R data to file" flag
				if (songLoopedCounter == 1) {
					Arrays.fill(playCount, 0); // Reset the lines play count before the next step
					playCount[trackedInstance] += 1; // Increment the line play count early, to ensure that same line will be detected again as the loop point for the next dumper iteration
				}
				if (songLoopedCounter == 2) {
					return true;
				}
			}
		}
		return false;
	}

	/** Only the two tested early-return guards - the real register-dumping/buffer-growing body is deferred (see class javadoc). */
	public void record() {
		if (recordState == StreamState.STOP) {
			return;
		}
		if (recordState == StreamState.START) {
			return; // Too soon, must first be initialised to get a constant rate every time
		}
		// The real data-writing path is deferred - see class javadoc.
	}

	/** Only the tested no-stream-buffer guard - the real write is deferred (see class javadoc), so this is always a no-op here. */
	public void writeToFile(OutputStream out, int frames, int offset) {
	}
}
