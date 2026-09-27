package com.wudsn.tools.rmt.model;

import java.util.Arrays;

/**
 * Ported from CPokeyStream (src/cpp/PokeyStream.h/.cpp) - the pure
 * state-machine methods {@code PokeyStreamTests.cpp} exercises
 * ({@link #switchIntoRecording}/{@link #switchIntoStop}/
 * {@link #callFromPlay}/{@link #trackSongLine}/{@link #callFromPlayBeat},
 * plus {@link #clear} and the getters), plus {@link #startRecording}/
 * {@link #record}/{@link #finishedRecording}'s real bodies. As in C++,
 * {@link #record} reads the POKEY register shadow at $D200/$D210 of the
 * {@link AtariTrackerDriver}'s Atari, which the driver's
 * {@code RMT_SETPOKEY} fills on every {@link AtariTrackerDriver#play}.
 *
 * <p><b>{@code WriteToFile} redesigned as {@link #getFrameBytes}</b>:
 * C++'s {@code std::ostream&}-writing method becomes a returned {@code byte[]}
 * slice, matching this port's established byte-array-over-stream idiom used
 * everywhere else ({@code saveRMW}/{@code loadRMW}/{@code exportAsRMT}, ...).
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

	private byte[] streamBuffer;
	private int frameSize;
	private AtariTrackerDriver driver;

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

	/**
	 * Prepares recording: allocates the stream buffer and resets every
	 * counter, matching C++'s {@code StartRecording()} exactly except that
	 * the growable {@code m_StreamBuffer}/{@code realloc} pair becomes a
	 * plain {@code byte[]}, doubled in place by {@link #record} when it
	 * fills up (via {@code Arrays.copyOf}).
	 */
	public void startRecording(Song song, int tracks4_8, AtariTrackerDriver driver) {
		this.driver = driver;

		recordState = StreamState.START;

		streamBuffer = new byte[0xFFFFF];

		frameSize = song.isStereo(tracks4_8) ? 18 : 9;
		frameCounter = 0;
		songlineCounter = 0;
		Arrays.fill(playCount, 0);
		Arrays.fill(framesPerSongline, 0);
		Arrays.fill(offsetPerSongline, 0);
		songLoopedCounter = 0;

		firstCountPoint = 0;
		secondCountPoint = 0;
		thirdCountPoint = 0;
	}

	/**
	 * Dumps the current POKEY register values to the stream buffer at the
	 * position defined by the frame counter - mirrors C++'s {@code Record()}
	 * exactly, including the "1st POKEY is 2nd in the stream" byte-layout
	 * quirk and the Two-Tone-mode AUDC1 bit-10 patch (driven by SKCTL at
	 * $D20F/$D21F).
	 */
	public void record() {
		if (recordState == StreamState.STOP) {
			return;
		}
		if (recordState == StreamState.START) {
			return; // Too soon, must first be initialised to get a constant rate every time, this prevents writing garbage in memory for the first few frames
		}

		int offsetIntoBuffer = frameCounter * frameSize; // 4 AUDC, 4 AUDF, 1 AUDCTL + Second POKEY if used

		if (offsetIntoBuffer > streamBuffer.length - frameSize + 1) {
			// Buffer is too small, grow it
			streamBuffer = Arrays.copyOf(streamBuffer, streamBuffer.length * 2);
		}

		// Dump Pokey sound registers to position defined by the frames counter
		// AUDF1, AUDC1, AUDF2, AUDC2, AUDF3, AUDC3, AUDF4, AUDC4, AUDCTL
		for (int i = 0; i < 9; i++) {
			int j = (frameSize == 18) ? 9 : 0; // Slight offset for i count, memory can then be aligned as it is expected

			// Copy data from the 1st Pokey
			// 0 offset in mono
			// 9 offset in stereo
			streamBuffer[offsetIntoBuffer + i + j] = (byte) driver.getByteAt(0xD200 + i);
			if (i == 1) { // AUDC1
				// Test SKCTL ($D20F), if Two-Tone is expected, set the Volume Only bit in the current AUDC1 offset
				streamBuffer[offsetIntoBuffer + i + j] |= (driver.getByteAt(0xD20F) == 0x8B) ? 0x10 : 0x00;
			}

			if (frameSize == 9) {
				continue; // No second POKEY
			}

			// Copy data from the 2nd Pokey
			streamBuffer[offsetIntoBuffer + i] = (byte) driver.getByteAt(0xD210 + i);
			if (i == 1) { // AUDC1
				// Test SKCTL ($D21F), if Two-Tone is expected, set the Volume Only bit in the current AUDC1 offset
				streamBuffer[offsetIntoBuffer + i] |= (driver.getByteAt(0xD21F) == 0x8B) ? 0x10 : 0x00;
			}
		}

		// If the end was reached, do nothing, simply ignore the last frame
		if (songLoopedCounter == 2) {
			return;
		}

		// Count the frames played in each Songline, until the first loop point is found
		if (songLoopedCounter < 1) {
			// Increment the frames counter for the current songline
			framesPerSongline[songlineCounter]++;
		}

		// Increment the frames counter for the next iteration
		frameCounter++;
	}

	/**
	 * Returns {@code frames} frames' worth of recorded register bytes
	 * starting at frame {@code offset} - mirrors C++'s
	 * {@code WriteToFile(std::ostream&, frames, offset)}, redesigned to
	 * return the bytes directly instead of writing to a stream (see class
	 * javadoc). Empty before {@link #startRecording} has ever run, matching
	 * C++'s {@code m_StreamBuffer == NULL} guard.
	 */
	public byte[] getFrameBytes(int frames, int offset) {
		if (streamBuffer == null) {
			return new byte[0];
		}
		int off = offset * frameSize;
		int len = frames * frameSize;
		return Arrays.copyOfRange(streamBuffer, off, off + len);
	}

	/**
	 * Resets recording state and releases the stream buffer - mirrors C++'s
	 * {@code FinishedRecording()} minus its {@code g_AtariTrackerDriver->Init()}/
	 * {@code g_ChannelControl.SetAllChannelsOn()} calls, which are the
	 * caller's own collaborators (see {@link Song#dumpSongToPokeyStream},
	 * which calls both explicitly itself, matching this port's established
	 * "no stored globals" idiom).
	 */
	public void finishedRecording() {
		recordState = StreamState.STOP; // Reset the SAPR dump flag now it is done
		frameCounter = 0; // Also reset the framecount once finished
		songLoopedCounter = 0; // Reset the playback counter
		streamBuffer = null;
		driver = null;
	}
}
