package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.rmt.model.Atari;
import com.wudsn.tools.rmt.model.AtariCpu;
import com.wudsn.tools.rmt.model.AtariTrackerDriver;
import com.wudsn.tools.rmt.model.ChannelControl;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.Messages;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.TrackClipboard;
import com.wudsn.tools.rmt.model.TrackerDriverVersion;
import com.wudsn.tools.rmt.model.Tracks;
import com.wudsn.tools.rmt.model.Tuning;
import com.wudsn.tools.rmt.model.TuningRatios;
import com.wudsn.tools.rmt.model.TuningSettings;
import com.wudsn.tools.rmt.model.Undo;

/**
 * The model composition root plus the UI state objects - everything C++
 * keeps as the {@code g_Song}/{@code g_Tracks}/{@code g_Instruments}/
 * {@code g_Undo}/{@code g_TrackClipboard}/{@code g_ChannelControl}/
 * {@code g_AtariTrackerDriver}/{@code g_tracks4_8} globals, wired the way
 * {@code CRmtApp::InitInstance()} wires them (and exactly as every model
 * test's {@code setUp()} does). It is an ordinary object handed to the
 * window, the panel and the drawing/input classes - not a static singleton.
 *
 * <p>{@link #tracks4_8} is the one mutable model-wide value: the model
 * never stores it (every method takes it as a parameter), so the UI keeps
 * it here and updates it whenever a load or a mono/stereo switch returns a
 * new one.
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
	/** {@code g_tuning}/{@code g_tuningRatios}/{@code g_Tuning}. */
	public final TuningSettings tuningSettings = new TuningSettings();
	public final TuningRatios tuningRatios = new TuningRatios();
	public final Tuning tuning;

	public final UiState uiState = new UiState();
	public final RmtOptions options = new RmtOptions();
	/** The import/export dialogs' remembered choices ({@code g_rmtstripped_*}, {@code g_rmtmsxtext}, ...). */
	public final ExportSettings exportSettings = new ExportSettings();
	/** The Effects/tools dialog's remembered effect and parameters ({@code g_effai}, {@code eff_ed}). */
	public final com.wudsn.tools.rmt.model.BlockEffects.Settings blockEffectSettings = new com.wudsn.tools.rmt.model.BlockEffects.Settings();

	/** {@code g_tracks4_8}: 4 (mono) or 8 (stereo). Starts at 8 as in C++ ("hardcoded to 8 to prevent ReInitSound() to run before RMT finished being initialised"); written through {@link #setTracks4_8}. */
	public int tracks4_8 = 8;

	/** {@code CSong::SetTracks()}: a change of the channel count re-initializes the sound. */
	public void setTracks4_8(int tracksNum) {
		if (tracksNum != tracks4_8) {
			tracks4_8 = tracksNum;
			reInitSound();
		}
	}

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

		tuningSettings.initialize(song.isNTSC());
		tuningRatios.initialize();
		// The emulated 6502 + POKEY pair the tracker driver runs on (C++'s
		// C6502/CAtari + the POKEY DLL); the Atari's 64K is the CPU's memory.
		// ClearSong(8) below makes the song stereo, so the POKEY pair starts
		// stereo and follows g_tracks4_8 from then on (AudioEngine, B8b).
		atari = new Atari(new AtariCpu(song.isNTSC(), true));
		atari.init(song.isNTSC(), tuningSettings, tuningRatios);
		tuning = new Tuning(atari.getClockFrequency());
		atariTrackerDriver = new AtariTrackerDriver(atari);
		atariTrackerDriver.loadRMTRoutines(options.trackerDriverVersion); // g_trackerDriverVersion's default; InitInstance runs before rmt.ini is read
		atariTrackerDriver.init();
		// CInstruments::Update() also writes each instrument's Atari bytes
		// to $4000 + instr * 256 for the driver (stereo = g_tracks4_8 == 8).
		instruments.attachAtari(atari, () -> tracks4_8 == 8);

		setTracks4_8(song.clearSong(8, undo));
		channelControl.setAllChannelsOn();
		undo.setChangeListener(() -> uiState.changes = true); // CUndo::InsertEvent's g_changes = 1
		song.setPlayTimeResetListener(() -> uiState.playTime = 0); // g_playtime = 0 in Play() and ClearSong()
	}

	/** {@code CRmtView::SetNTSC()}: rescales the base tuning between the two clocks and switches the song ("TODO code... well 3 times.." in C++); {@code CSong::SetNTSC()} re-initializes the sound on a change. */
	public void setNTSC(boolean ntsc) {
		tuningSettings.basetuning = ntsc ? (tuningSettings.basetuning * Atari.FREQ_17_NTSC) / Atari.FREQ_17_PAL : (tuningSettings.basetuning * Atari.FREQ_17_PAL) / Atari.FREQ_17_NTSC;
		boolean changed = song.isNTSC() != ntsc;
		song.setNTSC(ntsc);
		if (changed) {
			reInitSound();
		}
	}

	/**
	 * {@code CSong::ReInitSound()}: "Force a systematic Sound Reset to
	 * correctly handle Stereo and/or NTSC switch" - the POKEY pair
	 * re-initialized for the song's video standard and channel count
	 * ({@code CXPokey::ReInitSound}), the tuning tables regenerated
	 * ({@code g_Atari.Init}), the driver reset ({@code g_AtariTrackerDriver->Init}).
	 * Called, as in C++, from {@link #setTracks4_8}/{@link #setNTSC}/the
	 * import and the options dialog; {@link AudioEngine} also calls it when
	 * it finds the pair out of step at the top of a frame (a safety net for a
	 * direct {@link #tracks4_8} write).
	 */
	public void reInitSound() {
		atari.getCpu().initialize(song.isNTSC(), song.isStereo(tracks4_8));
		atari.init(song.isNTSC(), tuningSettings, tuningRatios);
		atariTrackerDriver.init();
	}

	/** {@code g_Tuning.InitTuning()}: regenerates the POKEY frequency tables in the Atari's memory from the current {@link #tuningSettings}/{@link #tuningRatios}. */
	public void initTuning() {
		atari.init(song.isNTSC(), tuningSettings, tuningRatios);
	}

	/**
	 * {@code CRmtView::OnToolsOptions()}'s driver-version branch: stores the
	 * new version, re-initializes the Atari ("TODO: This is done several
	 * times") and loads that version's player routines.
	 */
	public void setTrackerDriverVersion(TrackerDriverVersion version) {
		options.trackerDriverVersion = version;
		atari.init(song.isNTSC(), tuningSettings, tuningRatios);
		atariTrackerDriver.loadRMTRoutines(version);
	}
}
