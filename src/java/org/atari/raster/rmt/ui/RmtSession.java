package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.Atari;
import org.atari.raster.rmt.model.AtariCpu;
import org.atari.raster.rmt.model.AtariTrackerDriver;
import org.atari.raster.rmt.model.ChannelControl;
import org.atari.raster.rmt.model.Instruments;
import org.atari.raster.rmt.model.Messages;
import org.atari.raster.rmt.model.Song;
import org.atari.raster.rmt.model.TrackClipboard;
import org.atari.raster.rmt.model.TrackerDriverVersion;
import org.atari.raster.rmt.model.Tracks;
import org.atari.raster.rmt.model.Tuning;
import org.atari.raster.rmt.model.Undo;

/**
 * The model composition root plus the UI state objects - everything C++
 * keeps as the {@code g_Song}/{@code g_Tracks}/{@code g_Instruments}/
 * {@code g_Undo}/{@code g_TrackClipboard}/{@code g_ChannelControl}/
 * {@code g_AtariTrackerDriver} globals, wired the way
 * {@code CRmtApp::InitInstance()} wires them (and exactly as every model
 * test's {@code setUp()} does). It is an ordinary object handed to the
 * window, the panel and the drawing/input classes - not a static singleton.
 *
 * <p>C++'s {@code g_tracks4_8} is the song's channel count
 * ({@link Song#getChannels()}, plan 61); the session only keeps the POKEY
 * pair in step with it, through {@link #syncSound()}.
 */
public final class RmtSession {

	public final Tracks tracks;
	public final Instruments instruments;
	public final Song song;
	public final TrackClipboard clipboard;
	public final Undo undo;
	public final Messages messages;
	/** {@code g_Atari} - the emulated Atari's memory, holding the POKEY register shadow the analyzer and POKEY view display. */
	public final Atari atari;
	public final AtariTrackerDriver atariTrackerDriver;
	public final ChannelControl channelControl;
	/** {@code g_Tuning}: the table generator. The tuning settings it reads are the song's ({@link Song#getTuningSettings()}), as C++'s are since plan 44. */
	public final Tuning tuning;

	public final UiState uiState = new UiState();
	public final RmtOptions options = new RmtOptions();
	/** The import/export dialogs' remembered choices ({@code g_rmtstripped_*}, {@code g_rmtmsxtext}, ...). */
	public final ExportSettings exportSettings = new ExportSettings();
	/** {@code g_Midi}: the MIDI IN device (its settings are in {@link #options}); the handler is {@link MidiInput}, created by the window or the script runner. */
	public final RmtMidi midi = new RmtMidi(this);
	/** {@code CSong::m_PokeyController}: the Pokey Explorer mode's controller over the Atari's memory. */
	public final PokeyController pokeyController;
	/** The Effects/tools dialog's remembered effect and parameters ({@code g_effai}, {@code eff_ed}). */
	public final org.atari.raster.rmt.model.BlockEffects.Settings blockEffectSettings = new org.atari.raster.rmt.model.BlockEffects.Settings();

	/**
	 * The one lock between the EDT and the {@link AudioEngine} thread (C++
	 * mutates the model from both its UI and its timer thread unguarded,
	 * apart from {@code busyInCallback} spins). The engine holds it for a
	 * frame's model step; the EDT holds it for a key/mouse event, a command
	 * and a paint ({@link #locked}), and releases it around modal dialogs
	 * and message boxes ({@link #unlocked}) so the sound keeps running while
	 * they are open, as in C++. Reentrant, so the wrappers may nest freely.
	 */
	public final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

	/** Runs {@code action} holding {@link #lock}. */
	public void locked(Runnable action) {
		lock.lock();
		try {
			action.run();
		} finally {
			lock.unlock();
		}
	}

	/** Runs {@code action} holding {@link #lock} and returns its result. */
	public <T> T locked(java.util.function.Supplier<T> action) {
		lock.lock();
		try {
			return action.get();
		} finally {
			lock.unlock();
		}
	}

	/** Runs {@code action} (a modal dialog) with every hold of {@link #lock} this thread has released, then re-acquires them. */
	public <T> T unlocked(java.util.function.Supplier<T> action) {
		int holds = lock.isHeldByCurrentThread() ? lock.getHoldCount() : 0;
		for (int i = 0; i < holds; i++) {
			lock.unlock();
		}
		try {
			return action.get();
		} finally {
			for (int i = 0; i < holds; i++) {
				lock.lock();
			}
		}
	}

	/** {@link #unlocked(java.util.function.Supplier)} for an action without a result. */
	public void unlocked(Runnable action) {
		unlocked(() -> {
			action.run();
			return null;
		});
	}

	/**
	 * Builds the empty stereo song {@code Rmt.exe} starts with, in
	 * {@code CRmtApp::InitInstance()}'s order: tuning, Atari, tracker driver
	 * routines, {@code ClearSong(8)}; then {@code OnInitialUpdate()}'s "all
	 * channels on".
	 */
	public RmtSession() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();

		instruments = new Instruments();
		instruments.initInstruments();

		song = new Song(instruments, tracks);
		clipboard = new TrackClipboard();
		undo = new Undo(tracks, instruments, song, clipboard);
		messages = new Messages();
		channelControl = new ChannelControl(Song.SONGTRACKS);

		song.getTuningSettings().initialize(song.isNTSC());
		// The emulated 6502 + POKEY pair the tracker driver runs on (C++'s
		// C6502/CAtari + the POKEY DLL); the Atari's 64K is the CPU's memory.
		// ClearSong(8) below makes the song stereo, so the POKEY pair starts
		// stereo and follows the song's channel count from then on (syncSound).
		atari = new Atari(new AtariCpu(song.isNTSC(), true));
		atari.init(song.isNTSC(), song.getTuningSettings());
		tuning = new Tuning(atari.getClockFrequency());
		pokeyController = new PokeyController(atari);
		atariTrackerDriver = new AtariTrackerDriver(atari);
		loadTrackerDriver(song.getTrackerDriverVersion()); // the song's default; InitInstance runs before rmt.ini is read
		atariTrackerDriver.init();
		// CInstruments::Update() also writes each instrument's Atari bytes
		// to $4000 + instr * 256 for the driver.
		instruments.attachAtari(atari, song::isStereo);

		song.clearSong(Song.Channels.STEREO, undo);
		channelControl.setAllChannelsOn();
		undo.setChangeListener(() -> uiState.changes = true); // CUndo::InsertEvent's g_changes = 1
		song.setPlayTimeResetListener(() -> uiState.playTime = 0); // g_playtime = 0 in Play() and ClearSong()
	}

	/** {@code CRmtView::SetNTSC()}: rescales the A4 frequency between the two clocks and switches the song ("TODO code... well 3 times.." in C++); {@code CSong::SetNTSC()} re-initializes the sound on a change. */
	public void setNTSC(boolean ntsc) {
		song.getTuningSettings().a4Frequency = ntsc ? (song.getTuningSettings().a4Frequency * Atari.FREQ_17_NTSC) / Atari.FREQ_17_PAL : (song.getTuningSettings().a4Frequency * Atari.FREQ_17_PAL) / Atari.FREQ_17_NTSC;
		song.setNTSC(ntsc);
		syncSound();
	}

	/**
	 * {@code ReInitSound()} when the POKEY pair is out of step with the
	 * song's video standard or channel count. C++ re-initialized inside
	 * {@code SetTracks()}/{@code SetNTSC()}; here the song changes first and
	 * the session follows - synchronously after a load, new, import or
	 * mono/stereo switch, and at the top of every {@link AudioEngine} frame,
	 * so the exports that render through the driver never see a pair that
	 * does not match the song.
	 */
	public void syncSound() {
		AtariCpu cpu = atari.getCpu();
		if (cpu.isNTSC() != song.isNTSC() || cpu.isStereo() != song.isStereo()) {
			reInitSound();
		}
	}

	/**
	 * {@code CSong::ReInitSound()}: "Force a systematic Sound Reset to
	 * correctly handle Stereo and/or NTSC switch" - the POKEY pair
	 * re-initialized for the song's video standard and channel count
	 * ({@code CXPokey::ReInitSound}), the tuning tables regenerated
	 * ({@code g_Atari.Init}), the driver reset ({@code g_AtariTrackerDriver->Init}).
	 * Called from {@link #syncSound()} on a change, and directly by the
	 * import and the options dialog, as in C++.
	 */
	public void reInitSound() {
		atari.getCpu().initialize(song.isNTSC(), song.isStereo());
		atari.init(song.isNTSC(), song.getTuningSettings());
		atariTrackerDriver.init();
	}

	/** {@code g_Tuning.InitTuning()}: regenerates the POKEY frequency tables in the Atari's memory from the song's current {@link Song#getTuningSettings()}. */
	public void initTuning() {
		atari.init(song.isNTSC(), song.getTuningSettings());
	}

	/**
	 * {@code CRmtView::OnToolsOptions()}'s driver-version branch: stores the
	 * new version, re-initializes the Atari ("TODO: This is done several
	 * times") and loads that version's player routines.
	 */
	public void setTrackerDriverVersion(TrackerDriverVersion version) {
		song.setTrackerDriverVersion(version);
		atari.init(song.isNTSC(), song.getTuningSettings());
		loadTrackerDriver(version);
	}

	/**
	 * Loads the player routines and says so when they are not there. Both
	 * programs return 0 from {@code LoadRMTRoutines} and carry on, which
	 * leaves the emulated Atari playing silence and every export quiet with
	 * no indication why - a file name typed wrong while testing a driver
	 * build is enough to cause it (plans/30_DISTRIBUTION_LAYOUT_PLAN.md).
	 */
	private void loadTrackerDriver(TrackerDriverVersion version) {
		if (atariTrackerDriver.loadRMTRoutines(version) <= 0) {
			messages.sendWarningMessage("RMT", "Could not load the player routines:\n'"
					+ org.atari.raster.rmt.model.RmtAtariBinaries.getTrackerDriverPath(version) + "'\n\nRMT stays silent and exports no sound until that file is back.\n");
		} else {
			// The driver binary ships a static copy of the frequency tables
			// in its RMT_FRQTABLES segment, which the load just wrote over
			// the generated ones - and the two differ in the bell-1.79 and
			// 15 kHz tables. Regenerate so the tuning settings always win;
			// RMT 1.34.00 did the same in Atari_InitRMTRoutine() after every
			// load, and C++ does it inside LoadRMTRoutines()
			// (plans/37_TUNING_COMPATIBILITY_PLAN.md, T2).
			atari.initTuning(song.getTuningSettings());
		}
	}
}
