package com.wudsn.tools.rmt.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.wudsn.tools.rmt.model.Atari;
import com.wudsn.tools.rmt.model.AtariTrackerDriver;
import com.wudsn.tools.rmt.model.ChannelControl;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.Messages;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.SongIOType;
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
		atariTrackerDriver.loadRMTRoutines(TrackerDriverVersion.PATCH16); // g_trackerDriverVersion's default
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
	}

	/**
	 * {@code CSong::FileOpen(filename, FALSE)} for an {@code .rmt} file: clears
	 * the song, decodes the module, and on success remembers the file and
	 * turns all channels on; on a decode failure the song is cleared again
	 * (C++ then also shows an error box - the caller's job here). Returns
	 * whether the file was loaded. The {@code .txt}/{@code .rmw} formats
	 * and the file dialog itself come with the file-dialog batch (B7).
	 */
	public boolean openRmtFile(Path path) throws IOException {
		byte[] data = Files.readAllBytes(path);

		song.clearSong(tracks4_8, undo);
		Song.LoadRmtResult loaded = song.loadRMT(data);
		if (!loaded.success()) {
			tracks4_8 = song.clearSong(tracks4_8, undo); // Erases everything
			return false;
		}
		tracks4_8 = loaded.tracks4_8();
		song.setLoadedFile(path.toString(), SongIOType.RMT);
		channelControl.setAllChannelsOn();
		return true;
	}
}
