package org.atari.raster.rmt.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.atari.raster.rmt.model.Atari;
import org.atari.raster.rmt.model.AtariCpu;
import org.atari.raster.rmt.model.AtariTrackerDriver;
import org.atari.raster.rmt.model.ChannelControl;
import org.atari.raster.rmt.model.PlayMode;
import org.atari.raster.rmt.model.Song;

/**
 * C++'s {@code CSongTimer} + {@code CSong::TimerRoutine()} +
 * {@code CXPokey::RenderSound1_50()}: one daemon thread that, once per video
 * frame, advances playback ({@code PlayVBI}, {@code PlayPressedTones}), runs
 * the tracker driver once per instrument-speed sub-frame, copies the POKEY
 * register shadow into the POKEY pair with the muted channels zeroed
 * ({@code CopyAtariMemoryToPokey}) and renders that sub-frame's samples.
 *
 * <p>Pacing: C++ is driven by a 20 ms (PAL) / 17-17-16 ms (NTSC) multimedia
 * timer and renders more or less than a chunk to stay three chunks ahead of
 * DirectSound's play cursor. Here the sound card paces the thread: every
 * frame renders exactly one frame's worth of CPU cycles and the blocking
 * {@link SourceDataLine#write} of those samples waits for room in a
 * three-frame line buffer (C++'s {@code m_Latency = 3}). PAL/NTSC timing is
 * therefore the sample clock's; no groove is needed. Without an audio device
 * the thread sleeps a frame instead, so playback stays visible (and the
 * engine is testable headless through {@link #renderFrame}).
 *
 * <p>Output is always 16-bit signed little-endian, 2 channels, 44.1 kHz
 * (C++: 8-bit, 2 channels); a mono song's single POKEY is written to both
 * channels, as C++'s 2-channel output does.
 *
 * <p>Threading: the frame work runs under the session's lock
 * ({@link RmtSession#lock}) - the EDT holds the same lock for its input
 * handling, commands and painting and releases it around modal dialogs (see
 * {@link RmtSession#unlocked}). The engine never touches Swing.
 *
 * <p>{@code ReInitSound()} (C++: on an NTSC or mono/stereo switch, a load or
 * an import) is detected at the top of a frame: when the song's video
 * standard or channel count no longer matches the POKEY pair's, the pair is
 * re-initialized, the tuning tables regenerated and the driver reset - the
 * three things {@code CSong::ReInitSound()} does.
 */
public final class AudioEngine implements Runnable {

	/** {@code CXPokey::m_Latency}: the line buffer holds this many frames. */
	static final int LATENCY_FRAMES = 3;

	private static final int OUTPUT_CHANNELS = 2;
	private static final int OUTPUT_BLOCK_SIZE = OUTPUT_CHANNELS * 2; // 16-bit

	private final RmtSession session;
	private final byte[] renderBuffer = new byte[8192];
	private final byte[] frameBuffer = new byte[8192];
	private int frameBytes;

	private volatile boolean running;
	private Thread thread;
	private volatile boolean lineOpen;

	public AudioEngine(RmtSession session) {
		this.session = session;
	}

	/** {@code CSongTimer::SetTimer} + {@code InitSound}: starts the frame thread. */
	public synchronized void start() {
		if (thread != null) {
			return;
		}
		running = true;
		thread = new Thread(this, "RMT audio");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
	}

	/** {@code CSongTimer::StopTimer}: stops the thread and waits until it has left its callback. */
	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
	}

	/** Whether an audio line could be opened (false while headless or without a mixer, when the engine only paces the model). */
	public boolean isLineOpen() {
		return lineOpen;
	}

	@Override
	public void run() {
		SourceDataLine line = openLine();
		lineOpen = line != null;
		try {
			while (running) {
				int n;
				session.lock.lock();
				try {
					n = renderFrame();
				} finally {
					session.lock.unlock();
				}
				if (line != null) {
					line.write(frameBuffer, 0, n);
				} else {
					try {
						Thread.sleep(session.song.isNTSC() ? 17 : 20);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		} finally {
			if (line != null) {
				line.stop();
				line.close();
			}
			lineOpen = false;
		}
	}

	private static SourceDataLine openLine() {
		AudioFormat format = new AudioFormat(AtariCpu.SAMPLE_RATE, 16, OUTPUT_CHANNELS, true, false);
		try {
			SourceDataLine line = AudioSystem.getSourceDataLine(format);
			int frameBlocks = AtariCpu.SAMPLE_RATE / 50 + 1;
			line.open(format, LATENCY_FRAMES * frameBlocks * OUTPUT_BLOCK_SIZE);
			line.start();
			return line;
		} catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
			return null; // no audio device: the engine still paces the model
		}
	}

	/**
	 * {@code CSong::TimerRoutine()} + {@code RenderSound1_50()} for one video
	 * frame, into {@link #getFrameBuffer()}; returns the number of bytes
	 * rendered. Caller holds the session lock.
	 */
	int renderFrame() {
		Song song = session.song;
		AtariCpu cpu = session.atari.getCpu();
		AtariTrackerDriver driver = session.atariTrackerDriver;
		int tracks4_8 = session.tracks4_8;
		boolean stereo = song.isStereo(tracks4_8);

		// ReInitSound(): the POKEY pair follows the song's video standard and channel count
		if (cpu.isNTSC() != song.isNTSC() || cpu.isStereo() != stereo) {
			session.reInitSound();
		}

		// Things that are solved 1x for vbi
		song.playVBI(tracks4_8, driver);
		// Play tones if there are key presses
		song.playPressedTones(driver);

		// --- Rendered Sound --- one frame of cycles, split over the instrument-speed sub-frames as RenderSound1_50 splits its chunk
		int instrumentSpeed = Math.max(1, song.getInstrumentSpeed());
		int remainingCycles = Atari.getFrameCycleCount(song.isNTSC());
		int rendered = 0;
		boolean specialProveMode = session.uiState.editMode.isSpecialProveMode();
		for (int i = instrumentSpeed; i > 0; i--) {
			driver.play(specialProveMode); // one run RMT routine (instruments)
			copyAtariMemoryToPokey(cpu, stereo);
			int cycles = remainingCycles / i;
			remainingCycles -= cycles;
			rendered += cpu.render(cycles, renderBuffer, rendered * cpu.getBlockSize());
		}
		frameBytes = AtariCpu.toTwoChannels(renderBuffer, rendered, cpu.getBlockSize(), frameBuffer, 0);

		if (song.getPlayMode() != PlayMode.PLAY_STOP) {
			session.uiState.playTime++; // If the song is currently playing, increment the timer
		}
		return frameBytes;
	}

	/** {@code CXPokey::CopyAtariMemoryToPokey()}: the 9/18 register bytes, a muted channel's AUDF/AUDC as 0. */
	private void copyAtariMemoryToPokey(AtariCpu cpu, boolean stereo) {
		Atari atari = session.atari;
		ChannelControl channelControl = session.channelControl;
		for (int i = 0; i < 8; i++) {
			int channel = i / 2;
			cpu.pokeRegister(i, channelControl.isChannelOn(channel) ? atari.getByteAt(0xD200 + i) : 0);
			if (stereo) {
				cpu.pokeRegister(i + 16, channelControl.isChannelOn(channel + 4) ? atari.getByteAt(0xD210 + i) : 0);
			}
		}
		// AUDCTL
		cpu.pokeRegister(8, atari.getByteAt(0xD208));
		if (stereo) {
			cpu.pokeRegister(8 + 16, atari.getByteAt(0xD218));
		}
	}

	/** The last rendered frame's samples (16-bit LE, 2 channels); {@link #renderFrame} says how many bytes are valid. */
	byte[] getFrameBuffer() {
		return frameBuffer;
	}

	int getFrameBytes() {
		return frameBytes;
	}
}
