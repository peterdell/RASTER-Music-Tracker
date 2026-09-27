package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.rmt.model.Atari;
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

	/** {@code g_tracks4_8}: 4 (mono) or 8 (stereo). */
	public int tracks4_8;

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
		atari = new Atari();
		atari.init(song.isNTSC(), tuningSettings, tuningRatios);
		tuning = new Tuning(atari.getClockFrequency());
		atariTrackerDriver = new AtariTrackerDriver(atari);
		atariTrackerDriver.loadRMTRoutines(options.trackerDriverVersion); // g_trackerDriverVersion's default; InitInstance runs before rmt.ini is read
		atariTrackerDriver.init();
		// What the driver's initialization leaves in the POKEY register
		// shadow at $D200-$D21F (the 6502 code C++ runs through its
		// emulator, which this port doesn't execute until the audio batch
		// B8): every AUDF/AUDC/AUDCTL byte 0 and SKCTL = 3 - the values the
		// reference screenshots show in the analyzer and POKEY view.
		atari.setByteAt(0xD20F, 0x03);
		atari.setByteAt(0xD21F, 0x03);

		tracks4_8 = song.clearSong(8, undo);
		channelControl.setAllChannelsOn();
		undo.setChangeListener(() -> uiState.changes = true); // CUndo::InsertEvent's g_changes = 1
	}

	/** {@code CRmtView::SetNTSC()}: rescales the base tuning between the two clocks and switches the song ("TODO code... well 3 times.." in C++). */
	public void setNTSC(boolean ntsc) {
		tuningSettings.basetuning = ntsc ? (tuningSettings.basetuning * Atari.FREQ_17_NTSC) / Atari.FREQ_17_PAL : (tuningSettings.basetuning * Atari.FREQ_17_PAL) / Atari.FREQ_17_NTSC;
		song.setNTSC(ntsc);
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
