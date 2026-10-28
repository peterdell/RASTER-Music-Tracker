package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SongIO} - the file formats and the module codec, with the tests
 * they had as {@code Song} methods ({@code SongEditingTest}/{@code SongTest})
 * before plan 45 moved both. The fixture is {@code SongEditingTest}'s.
 * {@link SongIO#checkBeforeFileSave}'s error and warning branches are
 * asserted here for the first time in either program - the C++ original
 * (IO_Song.cpp's {@code TestBeforeFileSave}, renamed
 * {@code CheckBeforeFileSave} with this plan) sits outside the test
 * binary's link.
 */
class SongIOTest {

	private Tracks tracks;
	private Instruments instruments;
	private Song song;
	private Undo undo;
	private Messages messages;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();

		instruments = new Instruments();
		instruments.initInstruments();
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			java.util.Arrays.fill(instruments.getInstrument(i).name, '\0');
		}

		song = new Song(instruments, tracks);
		song.setChannels(4); // the C++ tests' g_tracks4_8 = 4
		undo = new Undo(tracks, instruments, song, new TrackClipboard());
		messages = new Messages();

		for (int line = 0; line < Song.SONGLEN; line++) {
			for (int col = 0; col < Song.SONGTRACKS; col++) {
				song.getSong()[line][col] = -1;
			}
			song.getSongGo()[line] = -1;
		}
		song.songSetActiveLine(0);
		song.setActiveLine(0);
	}

	private static void setName(char[] name, String value) {
		for (int i = 0; i < value.length(); i++) {
			name[i] = value.charAt(i);
		}
	}

	private static String nameToString(char[] name) {
		int end = 0;
		while (end < name.length && name[end] != '\0') {
			end++;
		}
		return new String(name, 0, end);
	}

	// --- songToAta / ataToSong (from SongTest) ---

	@Test
	void songToAtaEncodesTrackDataThenFillsRestAsUnused() {
		song.getSong()[0][0] = 5;
		song.getSong()[0][1] = 10;
		song.getSong()[0][2] = 15;
		song.getSong()[0][3] = 20;

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = SongIO.songToAta(song, dest, dest.length, 0x4000);

		assertEquals(4, size);
		assertEquals(5, dest[0]);
		assertEquals(10, dest[1]);
		assertEquals(15, dest[2]);
		assertEquals(20, dest[3]);
		// Every other line is empty (-1), encoded as 255 per track slot.
		assertEquals((byte) 255, dest[4]);
		assertEquals((byte) 255, dest[Song.SONGLEN * 4 - 1]);
	}

	@Test
	void songToAtaAndAtaToSongRoundTripTrackData() {
		song.getSong()[0][0] = 5;
		song.getSong()[0][1] = 10;
		song.getSong()[0][2] = 15;
		song.getSong()[0][3] = 20;

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = SongIO.songToAta(song, dest, dest.length, 0x4000);

		Song decoded = new Song(new Instruments(), new Tracks());
		decoded.setChannels(4); // as decodeModule sets it from the header before ataToSong
		assertTrue(SongIO.ataToSong(decoded, dest, size, 0x4000));
		assertEquals(5, decoded.getSong()[0][0]);
		assertEquals(10, decoded.getSong()[0][1]);
		assertEquals(15, decoded.getSong()[0][2]);
		assertEquals(20, decoded.getSong()[0][3]);
	}

	@Test
	void songToAtaAndAtaToSongRoundTripGotoLine() {
		song.getSongGo()[0] = 1; // line 0: "goto line 1"

		byte[] dest = new byte[Song.SONGLEN * 4];
		int size = SongIO.songToAta(song, dest, dest.length, 0x4000);

		assertEquals(4, size);
		assertEquals((byte) 254, dest[0]); // go command marker

		// Decode using a byte range covering (at least) 2 lines, so the
		// decoded goto target (line 1) is within bounds - ataToSong()'s
		// "len" parameter represents how much of the module's song section
		// is being decoded, not songToAta()'s own (smaller) "bytes actually
		// used" return value.
		Song decoded = new Song(new Instruments(), new Tracks());
		decoded.setChannels(4); // as decodeModule sets it from the header before ataToSong
		assertTrue(SongIO.ataToSong(decoded, dest, 8, 0x4000));
		assertEquals(1, decoded.songGetGo(0));
	}

	// --- makeModule / decodeModule ---
	// Round-trip test, mirroring songToAta/ataToSong's approach in SongTest:
	// lets the real encode/decode logic prove itself internally consistent
	// rather than hand-deriving the RMT header's byte layout.

	@Test
	void makeModuleAndDecodeModuleRoundTripASimpleSong() {
		// mainSpeed/instrumentSpeed default to 0, but decodeModule() rejects
		// a decoded speed byte of 0 as invalid (there can be no zero speed) -
		// give them valid values first.
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5; // song line 0, column 0 references track 5

		Track tr = tracks.getTrack(5);
		tr.len = 4;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		setName(instruments.getInstrument(2).name, "Lead");
		instruments.getInstrument(2).parameters[0] = 5;

		byte[] mem = new byte[8192];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];

		int endAddr = SongIO.makeModule(song, mem, 0, SongIOType.RMT, instrSavedFlags, trackSavedFlags);
		assertTrue(endAddr > 0);
		assertTrue(endAddr <= mem.length);

		Song decoded = new Song(instruments, tracks);
		byte[] instrLoadedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackLoadedFlags = new byte[Tracks.TRACKSNUM];
		int version = SongIO.decodeModule(decoded, mem, 0, endAddr, instrLoadedFlags, trackLoadedFlags);

		assertEquals(RmtFormatVersion.V1, version);
		assertEquals(5, decoded.getSong()[0][0]);
		// decodeModule() decodes back into the same shared tracks/instruments
		// it was encoded from (there's only one in production too).
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
		assertEquals("Lead", nameToString(instruments.getInstrument(2).name));
		assertEquals(5, instruments.getInstrument(2).parameters[0]);
	}

	// --- saveTxt ---

	@Test
	void saveTxtWritesModuleHeaderAndSongLineData() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5; // only line 0 has data - the rest stay "--"

		String text = SongIO.saveTxt(song);

		assertTrue(text.contains("[MODULE]"));
		assertTrue(text.contains("[SONG]"));
		assertTrue(text.contains("05 -- -- --\n")); // track 05 in column 0, columns 1-3 empty
	}

	// --- loadTxt ---
	// Round-trips through saveTxt, same philosophy as loadRMT's round trip
	// below. loadTxt() has no unconditional-success dialog to avoid (unlike
	// loadRMW's version-mismatch MessageBox) - it only ever fails silently by
	// leaving fields at their clearSong() defaults for a segment it doesn't
	// recognize.

	@Test
	void loadTxtRoundTripsTheModuleHeaderAndTheSongData() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		setName(info.songName, "TestSong");
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		String text = SongIO.saveTxt(song);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		SongIO.loadTxt(loaded, text, loadedUndo);

		// The [MODULE] header block parses correctly...
		assertEquals(4, loaded.getChannels()); // RMT: 04 round-trips the channel count via setChannels()
		SongInfo loadedInfo = new SongInfo();
		loaded.getSongInfoPars(loadedInfo);
		assertEquals(6, loadedInfo.mainSpeed);
		assertEquals(2, loadedInfo.instrumentSpeed);
		assertEquals("TestSong", loaded.getName());

		// ...and so does "[SONG]", restoring the saved track 5.
		assertEquals(5, loaded.getSong()[0][0]);
	}

	// Mirrors src/cpp/test/SongEditingTests.cpp's
	// SaveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack - exercises
	// Instruments/Tracks' saveAllTxt/loadInstrumentTxt/loadTrackTxt for
	// real (every other TXT round-trip test above has no non-empty
	// instruments/tracks, so saveAllTxt's non-empty-only rule never
	// actually emits an "[INSTRUMENT]"/"[TRACK]" segment). instruments/
	// tracks are the same objects both `song` and `loaded` share
	// (loadInstrumentTxt/loadTrackTxt write through them directly, not
	// through any per-Song state) - matching loadRMTDecodesTheModuleAndNamesBlocks's
	// own established pattern.
	@Test
	void saveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		Instrument instr = instruments.getInstrument(2);
		setName(instr.name, "Lead");
		instr.parameters[Instrument.PAR_ENV_LENGTH] = 2;
		instr.envelope[0][EnvelopeParameter.VOLUMEL] = 10;

		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		String text = SongIO.saveTxt(song);
		assertTrue(text.contains("[INSTRUMENT]"));
		assertTrue(text.contains("[TRACK]"));

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		SongIO.loadTxt(loaded, text, loadedUndo);

		assertEquals("Lead", new String(instruments.getInstrument(2).name).stripTrailing());
		assertEquals(10, instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL]);

		assertEquals(2, tracks.getTrack(5).len);
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
	}

	// --- saveRMW ---

	@Test
	void saveRMWWritesTheVersionStringFirst() {
		byte[] out = SongIO.saveRMW(song);

		String prefix = new String(out, 0, RmtVersion.RMT_VERSION_STRING.length(), java.nio.charset.StandardCharsets.US_ASCII);
		assertEquals(RmtVersion.RMT_VERSION_STRING, prefix);
	}

	// FIXED BUG (was pre-existing, from when this project was 32-bit - see
	// plans/15_JAVA_SONGEDITING_PLAN.md's sub-batch 8 entry): SaveRMW/LoadRMW's
	// main-parameters loop used to write/read 8 bytes per parameter instead
	// of 4 on this 64-bit C++ build. This Java port always writes exactly 4
	// bytes per parameter (no pointer-array sizeof to get wrong in the first
	// place), but this test proves the exact byte layout directly anyway,
	// mirroring the C++ regression test added alongside that fix.
	@Test
	void saveRMWWritesEachMainParameterAsExactlyFourBytes() {
		song.getSong()[0][0] = 5; // the first value after the main-parameters block

		byte[] out = SongIO.saveRMW(song);

		int offset = RmtVersion.RMT_VERSION_STRING.length() + 1; // version line + '\n'
		offset += SongInfo.SONG_NAME_MAX_LEN + 1; // songName plus the extra C++ byte

		int paramCount = readIntLE(out, offset);
		offset += 4;
		assertEquals(31, paramCount);

		int firstParam = readIntLE(out, offset); // the channel count, 4
		assertEquals(4, firstParam);

		// If each parameter were (incorrectly) 8 bytes, this offset would land
		// in the middle of the main-parameters block instead of at song[0][0].
		offset += paramCount * 4;
		int firstSongValue = readIntLE(out, offset);
		assertEquals(5, firstSongValue);
	}

	private static int readIntLE(byte[] data, int pos) {
		return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8) | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
	}

	// --- loadRMW ---
	// Round-trips through saveRMW. loadRMW's version-mismatch branch (a real
	// MessageBox, unconditional on the error path) is deliberately never
	// exercised - only ever fed a byte array that starts with a matching
	// version string, same "avoidable with valid test data" treatment as
	// loadRMT's guard-only error branches.

	@Test
	void loadRMWRoundTripsSongDataThroughSaveRMW() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		setName(info.songName, "TestSong");
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		song.getSongGo()[2] = 7;

		byte[] out = SongIO.saveRMW(song);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		SongIO.LoadRmwResult result = SongIO.loadRMW(loaded, out, loadedUndo);

		assertTrue(result.success());
		assertEquals(5, loaded.getSong()[0][0]);
		assertEquals(7, loaded.getSongGo()[2]);

		SongInfo loadedInfo = new SongInfo();
		loaded.getSongInfoPars(loadedInfo);
		assertEquals(6, loadedInfo.mainSpeed);
		assertEquals(2, loadedInfo.instrumentSpeed);
		assertEquals("TestSong", loaded.getName());
	}

	// Mirrors src/cpp/test/SongEditingTests.cpp's
	// SaveRMWAndLoadRMWRoundTripNonEmptyInstrumentAndTrack. Unlike TXT's
	// saveAllTxt, RMW's always writes every instrument/track
	// unconditionally, so this is really about proving non-trivial data
	// (not just all-default/empty content) survives the round trip.
	@Test
	void saveRMWAndLoadRMWRoundTripNonEmptyInstrumentAndTrack() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		Instrument instr = instruments.getInstrument(2);
		setName(instr.name, "Lead");
		instr.parameters[Instrument.PAR_ENV_LENGTH] = 2;
		instr.envelope[0][EnvelopeParameter.VOLUMEL] = 10;

		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		byte[] out = SongIO.saveRMW(song);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		assertTrue(SongIO.loadRMW(loaded, out, loadedUndo).success());

		assertEquals("Lead", nameToString(instruments.getInstrument(2).name));
		assertEquals(10, instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL]);

		assertEquals(2, tracks.getTrack(5).len);
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
	}

	// The 16 UI settings (C++'s g_activepart..g_keyboard_escresetatarisound
	// globals among the main parameters) are the caller's: passed in to
	// saveRMW, handed back by loadRMW. Found by the cross-program comparison
	// (CrossProgramExportTest) on 2026-09-27 - the port wrote zeros.
	@Test
	void saveRMWAndLoadRMWRoundTripTheCallersUiSettings() {
		int[] uiParams = new int[SongIO.RMW_UI_PARAMS_COUNT];
		for (int i = 0; i < uiParams.length; i++) {
			uiParams[i] = 100 + i;
		}

		byte[] out = SongIO.saveRMW(song, uiParams);

		int offset = RmtVersion.RMT_VERSION_STRING.length() + 1 + SongInfo.SONG_NAME_MAX_LEN + 1 + 4;
		assertEquals(100, readIntLE(out, offset + SongIO.RMW_UI_PARAMS_INDEX * 4));
		assertEquals(115, readIntLE(out, offset + (SongIO.RMW_UI_PARAMS_INDEX + 15) * 4));

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		SongIO.LoadRmwResult result = SongIO.loadRMW(loaded, out, loadedUndo);
		assertTrue(result.success());
		assertArrayEquals(uiParams, result.uiParams());

		assertArrayEquals(new int[SongIO.RMW_UI_PARAMS_COUNT], SongIO.loadRMW(loaded, SongIO.saveRMW(song), loadedUndo).uiParams()); // the overload without them writes zeros
	}

	// --- loadRMT ---
	// Builds a valid two-block RMT file in memory (module block via
	// makeModule, names block by hand) rather than hand-deriving the RMT
	// header's byte layout - same round-trip philosophy as
	// makeModule/decodeModule above.

	private static void writeBinaryBlock(java.io.ByteArrayOutputStream out, byte[] mem, int fromAddr, int toAddr) {
		out.write(fromAddr & 0xFF);
		out.write((fromAddr >> 8) & 0xFF);
		out.write(toAddr & 0xFF);
		out.write((toAddr >> 8) & 0xFF);
		out.write(mem, fromAddr, toAddr - fromAddr + 1);
	}

	@Test
	void loadRMTDecodesTheModuleAndNamesBlocks() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;
		setName(instruments.getInstrument(2).name, "Lead");

		byte[] mem = new byte[8192];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int fromAddr = 0x100;
		int endAddr = SongIO.makeModule(song, mem, fromAddr, SongIOType.RMT, instrSavedFlags, trackSavedFlags);
		assertTrue(endAddr > 0);

		java.io.ByteArrayOutputStream blocks = new java.io.ByteArrayOutputStream();
		writeBinaryBlock(blocks, mem, fromAddr, endAddr - 1);

		// Names block: song name, then the name of each *loaded* instrument
		// (in index order) - here just instrument 2, since it's the only one used.
		byte[] namesMem = new byte[64];
		int p = 0;
		for (char c : "TestSong".toCharArray()) {
			namesMem[p++] = (byte) c;
		}
		namesMem[p++] = 0;
		for (char c : "Lead".toCharArray()) {
			namesMem[p++] = (byte) c;
		}
		namesMem[p++] = 0;
		writeBinaryBlock(blocks, namesMem, 0, p - 1);

		Song decoded = new Song(instruments, tracks);
		SongIO.LoadRmtResult loaded = SongIO.loadRMT(decoded, blocks.toByteArray());
		assertTrue(loaded.success());
		assertEquals(4, decoded.getChannels());

		assertEquals("TestSong", decoded.getName());
		// Unlike getName(), the raw instrument name field isn't trimmed -
		// loadRMT() fills the remainder with spaces up to the name's own length.
		assertEquals("Lead", new String(instruments.getInstrument(2).name).stripTrailing());
	}

	// --- checkBeforeFileSave ---
	// The save gate (C++: IO_Song.cpp's TestBeforeFileSave, renamed with plan
	// 45 because it is a check, not a test). First coverage of its error and
	// warning branches in either program.

	/** The messages the check sends, recorded through the {@link Messages.Handler} seam. */
	private List<String> record(MessageAnswer questionAnswer) {
		List<String> calls = new ArrayList<>();
		messages.setHandler(new Messages.Handler() {
			@Override
			public void showError(String title, String message) {
				calls.add("error: " + message);
			}

			@Override
			public void showWarning(String title, String message) {
				calls.add("warning: " + message);
			}

			@Override
			public void showInformation(String title, String message) {
				calls.add("information: " + message);
			}

			@Override
			public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
				calls.add("question: " + message);
				return questionAnswer;
			}
		});
		return calls;
	}

	private void aValidOneLineSong() {
		song.getSong()[0][0] = 0; // one track on line 0
		song.getSongGo()[1] = 0; // and the final "go to line 0"
	}

	@Test
	void checkBeforeFileSavePassesAValidSongSilently() {
		aValidOneLineSong();
		List<String> calls = record(MessageAnswer.CANCEL);
		assertTrue(SongIO.checkBeforeFileSave(song, messages));
		assertTrue(calls.isEmpty(), calls.toString());
	}

	@Test
	void checkBeforeFileSaveRefusesAnEmptySong() {
		List<String> calls = record(MessageAnswer.CANCEL);
		assertFalse(SongIO.checkBeforeFileSave(song, messages));
		assertEquals(1, calls.size());
		assertTrue(calls.get(0).startsWith("error: Error: Song is empty."), calls.get(0));
	}

	@Test
	void checkBeforeFileSaveRefusesAGoToPastTheLastUsedLineAndAGoToOnAGoTo() {
		song.getSong()[0][0] = 0;
		song.getSongGo()[1] = 2; // past itself, the last used line
		List<String> calls = record(MessageAnswer.CANCEL);
		assertFalse(SongIO.checkBeforeFileSave(song, messages));
		assertTrue(calls.get(0).contains("Go to line over last used song line"), calls.get(0));

		setUp();
		song.getSong()[0][0] = 0;
		song.getSongGo()[1] = 2;
		song.getSongGo()[2] = 0; // a "go to line" pointing at another one
		calls = record(MessageAnswer.CANCEL);
		assertFalse(SongIO.checkBeforeFileSave(song, messages));
		assertTrue(calls.get(0).contains("Recursive \"go to line\" to \"go to line\""), calls.get(0));
	}

	@Test
	void checkBeforeFileSaveAppendsTheMissingFinalGoToAfterAnInformationBox() {
		song.getSong()[0][0] = 0; // a track on the last used line, no "go to" anywhere
		List<String> calls = record(MessageAnswer.CANCEL);
		assertTrue(SongIO.checkBeforeFileSave(song, messages));
		assertEquals(1, calls.size());
		assertTrue(calls.get(0).startsWith("information: "), calls.get(0));
		assertEquals(0, song.getSongGo()[1]); // the appended "go to line 0"
	}

	@Test
	void checkBeforeFileSaveLeavesWarningsToTheUser() {
		// consecutive "go to line" lines are a warning, not an error
		song.getSong()[0][0] = 0;
		song.getSongGo()[1] = 0;
		song.getSongGo()[2] = 0;
		List<String> calls = record(MessageAnswer.YES);
		assertTrue(SongIO.checkBeforeFileSave(song, messages));
		assertTrue(calls.get(0).contains("More \"go to line\" on subsequent lines"), calls.get(0));
		assertTrue(calls.get(0).contains("Ignore warnings and save anyway?"), calls.get(0));

		setUp();
		song.getSong()[0][0] = 0;
		song.getSongGo()[1] = 0;
		song.getSongGo()[2] = 0;
		record(MessageAnswer.NO);
		assertFalse(SongIO.checkBeforeFileSave(song, messages));
	}
}
