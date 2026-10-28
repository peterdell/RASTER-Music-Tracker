package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link RmtModule}, {@link RmtModuleFile} and {@link RmtModuleCodec} (plan
 * 60, M1): the spec of doc/rmt_format.md made executable against every
 * {@code .rmt} in test-resources/songs, and the container proven equivalent
 * to the program's codec ({@code SongIO.makeModule}/{@code decodeModule},
 * {@code RmtExporter.exportAsRMT}) byte for byte - which is what lets M2
 * swap the codec underneath the program later.
 */
class RmtModuleTest {

	/** Every committed module ({@link TestSongs#ALL}): mono and stereo, RMT 1.28 and 1.34; four of them stripped, one of those at $6800. */
	static List<Path> fixtures() {
		return TestSongs.ALL;
	}

	static RmtModuleFile read(Path path) throws IOException {
		return RmtModuleFile.read(Files.readAllBytes(path));
	}

	/** The load address the comparisons use: RMT's default. */
	static final int LOAD = RmtModuleFile.DEFAULT_LOAD_ADDRESS;

	/** The track length of the built songs: the editor's default. */
	static final int TRACK_LENGTH = 64;

	/** The song speed of the built songs. */
	static final int SONG_SPEED = 6;

	/** A song the way {@code SongIOTest} builds one. */
	static Song newSong() {
		Tracks tracks = new Tracks();
		tracks.setMaxTrackLength(TRACK_LENGTH);
		tracks.initTracks();
		Instruments instruments = new Instruments();
		instruments.initInstruments();
		Song song = new Song(instruments, tracks);
		for (int line = 0; line < Song.SONGLEN; line++) {
			Arrays.fill(song.song[line], -1);
			song.songGo[line] = -1;
		}
		return song;
	}

	static byte[] makeModule(Song song, int address, SongIOType iotype) {
		byte[] mem = new byte[Memory.SIZE];
		int end = SongIO.makeModule(song, mem, address, iotype, new byte[Instruments.INSTRSNUM], new byte[Tracks.TRACKSNUM]);
		assertTrue(end > 0, "makeModule failed");
		return Arrays.copyOfRange(mem, address, end);
	}

	@Test
	void everyFixtureReadsAndWritesBackByteForByte() throws IOException {
		for (Path path : fixtures()) {
			byte[] data = Files.readAllBytes(path);
			RmtModuleFile file = RmtModuleFile.read(data);
			assertArrayEquals(data, file.write(), path.toString());
			assertEquals(TestSongs.STRIPPED.contains(path), file.isStripped(), path + " stripped");
		}
	}

	@Test
	void theFixturesAreWhatTheirReadmesSay() throws IOException {
		RmtModuleFile delta = read(TestSongs.DELTA);
		assertEquals(RmtModule.Channels.MONO, delta.module().channels());
		assertEquals(RmtModuleFile.DEFAULT_LOAD_ADDRESS, delta.loadAddress());
		assertTrue(delta.isStripped(), "Raster's own export carries no names");
		RmtModuleFile captain = read(TestSongs.CAPTAIN_FUTURE_THEME);
		assertTrue(captain.songName().startsWith("Buddy - Captain"), captain.songName());
		assertEquals(captain.module().instruments().size(), captain.instrumentNames().size());
		RmtModuleFile sfx = read(TestSongs.ISSUE_5_STRIPPED_SFX_EXPORT);
		assertEquals(0x6800, sfx.loadAddress(), "exported at $6800, per its README");
		assertEquals(10, sfx.module().instruments().size(), "instruments 0-9 (the README said 9 until plan 60 counted them)");
		// "No tracks or song lines" in the README means: nothing plays. The file
		// holds eleven one-event audition tracks no song line references, and a
		// song of one empty line looping to itself.
		assertEquals(11, sfx.module().tracks().size());
		assertTrue(sfx.module().tracks().values().stream().allMatch(track -> track.lineCount() == 1));
		int empty = RmtModule.SongLine.EMPTY;
		assertEquals(List.of(RmtModule.SongLine.tracks(empty, empty, empty, empty), RmtModule.SongLine.goTo(0)), sfx.module().songLines());
		RmtModuleFile why = read(TestSongs.WHY_DO_YOU_DANCE_WITH_ME);
		assertEquals(RmtModule.Channels.STEREO, why.module().channels());
		assertEquals(4, why.module().instrumentSpeed());
	}

	@Test
	void writingAtAnotherAddressRelocatesTheModule() throws IOException {
		for (Path path : fixtures()) {
			RmtModule module = read(path).module();
			byte[] relocated = module.toBytes(0x6000); // any address other than the default
			assertEquals(module.length(), relocated.length);
			byte[] mem = new byte[Memory.SIZE];
			System.arraycopy(relocated, 0, mem, 0x6000, relocated.length);
			assertEquals(module, RmtModule.parse(mem, 0x6000, 0x6000 + relocated.length), path.toString());
			assertFalse(Arrays.equals(relocated, module.toBytes(RmtModuleFile.DEFAULT_LOAD_ADDRESS)), "the pointers differ by address");
		}
	}

	@Test
	void fromSongWritesExactlyWhatMakeModuleWrites() throws IOException {
		for (Path path : fixtures()) {
			Song song = newSong();
			SongIO.LoadRmtResult loaded = SongIO.loadRMT(song, Files.readAllBytes(path));
			assertTrue(loaded.success(), path.toString());
			for (SongIOType iotype : new SongIOType[] { SongIOType.RMT, SongIOType.RMTSTRIPPED }) {
				RmtModule module = RmtModuleCodec.fromSong(song, iotype);
				for (int address : new int[] { RmtModuleFile.DEFAULT_LOAD_ADDRESS, 0x2e00 }) {
					assertArrayEquals(makeModule(song, address, iotype), module.toBytes(address), path + " " + iotype + " at $" + Integer.toHexString(address));
				}
			}
		}
	}

	@Test
	void toSongDecodesExactlyAsDecodeModuleDoes() throws IOException {
		for (Path path : fixtures()) {
			Song original = newSong();
			assertTrue(SongIO.loadRMT(original, Files.readAllBytes(path)).success(), path.toString());
			byte[] mem = new byte[Memory.SIZE];
			int end = SongIO.makeModule(original, mem, LOAD, SongIOType.RMT, new byte[Instruments.INSTRSNUM], new byte[Tracks.TRACKSNUM]);

			Song viaContainer = newSong();
			RmtModuleCodec.toSong(RmtModule.parse(mem, LOAD, end), viaContainer);
			Song viaDecodeModule = newSong();
			int version = SongIO.decodeModule(viaDecodeModule, mem, LOAD, end, new byte[Instruments.INSTRSNUM], new byte[Tracks.TRACKSNUM]);

			assertEquals(viaDecodeModule.getChannels(), viaContainer.getChannels(), path.toString());
			assertEquals(RmtFormatVersion.V1, version);
			// The same song state generates the same module again
			assertArrayEquals(makeModule(viaDecodeModule, LOAD, SongIOType.RMT), makeModule(viaContainer, LOAD, SongIOType.RMT), path.toString());
			assertEquals(viaDecodeModule.mainSpeed, viaContainer.mainSpeed);
			assertEquals(viaDecodeModule.instrumentSpeed, viaContainer.instrumentSpeed);
			assertEquals(viaDecodeModule.tracks.getMaxTrackLength(), viaContainer.tracks.getMaxTrackLength());
		}
	}

	@Test
	void fileFromSongWritesExactlyWhatExportAsRmtWrites() throws IOException {
		for (Path path : fixtures()) {
			Song song = newSong();
			byte[] data = Files.readAllBytes(path);
			assertTrue(SongIO.loadRMT(song, data).success(), path.toString());
			byte[] mem = new byte[Memory.SIZE];
			byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
			int end = SongIO.makeModule(song, mem, LOAD, SongIOType.RMT, instrumentSavedFlags, new byte[Tracks.TRACKSNUM]);
			byte[] expected = RmtExporter.exportAsRMT(song, song.instruments, mem, LOAD, end, instrumentSavedFlags);
			assertArrayEquals(expected, RmtModuleCodec.fileFromSong(song, LOAD).write(), path.toString());
		}
	}

	@Test
	void namesRoundTripThroughTheSong() throws IOException {
		RmtModuleFile file = read(TestSongs.ISSUE_30_TWO_TONE_FILTER);
		assertEquals("Makary - Two-Tone Filter", file.songName());
		Song song = newSong();
		RmtModuleCodec.toSong(file.module(), song);
		RmtModuleCodec.namesToSong(file, song);
		assertEquals(file.songName(), song.getName());
		RmtModuleFile again = RmtModuleCodec.fileFromSong(song, file.loadAddress());
		assertEquals(file.songName(), again.songName());
		assertEquals(file.instrumentNames(), again.instrumentNames());
	}

	@Test
	void theInstrumentStructIsTyped() throws IOException {
		RmtModule module = read(TestSongs.DELTA).module();
		RmtModule.Instrument first = module.instruments().values().iterator().next();
		assertEquals(first.length(), first.toBytes().length);
		assertEquals(RmtModule.Instrument.PARAMETERS_LENGTH + first.noteTable().length + first.envelope().size() * RmtModule.Instrument.ENVELOPE_ENTRY_LENGTH, first.length());
		assertTrue(first.tableLoop() < first.noteTable().length);
		assertTrue(first.envelopeLoop() < first.envelope().size());
		// The struct's limits are the editor's
		assertEquals(org.atari.raster.rmt.model.Instrument.NOTE_TABLE_MAX_LEN, RmtModule.Instrument.MAX_NOTE_TABLE_ENTRIES);
		assertEquals(org.atari.raster.rmt.model.Instrument.ENVELOPE_MAX_COLUMNS, RmtModule.Instrument.MAX_ENVELOPE_ENTRIES);
		RmtModule.Instrument.Envelope envelope = new RmtModule.Instrument.Envelope(0xa5, 0x9b, 0x3c);
		assertEquals(5, envelope.volumeLeft());
		assertEquals(0xa, envelope.volumeRight());
		assertEquals(1, envelope.portamento());
		assertEquals(0xa, envelope.distortion());
		assertEquals(1, envelope.command());
		assertEquals(1, envelope.filter());
		assertEquals(3, envelope.x());
		assertEquals(0xc, envelope.y());
	}

	/** The format's own limits (plan 60: the container stays free of the editor) are the editor's. */
	@Test
	void theFormatLimitsAreTheEditors() {
		assertEquals(Instruments.INSTRSNUM, RmtModule.MAX_INSTRUMENTS);
		assertEquals(Tracks.TRACKSNUM, RmtModule.MAX_TRACKS);
		assertEquals(Song.SONGLEN, RmtModule.MAX_SONG_LINES);
		assertEquals(Song.SONGTRACKS, RmtModule.Channels.STEREO);
		assertEquals(RmtFormatVersion.V1, RmtModule.FORMAT_VERSION);
		assertEquals(0x4000, RmtModuleFile.DEFAULT_LOAD_ADDRESS);
		assertEquals(Atari.MEMORY_SIZE - 1, Memory.MAX_ADDRESS);
	}

	@Test
	void aModuleMayEndExactlyAtTheLastAddress() throws IOException {
		RmtModule module = read(TestSongs.DELTA).module();
		int lastFit = Memory.MAX_ADDRESS - module.length() + 1;
		assertEquals(Memory.MAX_ADDRESS, new RmtModuleFile(module, lastFit, null, null).loadAddress() + module.length() - 1);
		assertThrows(RmtModule.FormatException.class, () -> new RmtModuleFile(module, lastFit + 1, null, null));
		assertThrows(RmtModule.FormatException.class, () -> module.toBytes(lastFit + 1));
	}

	@Test
	void theTrackWalkerKnowsTheFourEventKinds() {
		int shift = RmtModule.Track.HIGH_BITS_SHIFT;
		byte noteC1Instrument2Volume15 = (byte) ((15 & 3) << shift), noteParameter = (byte) ((2 << 2) | (15 >> 2));
		byte volumeOnly9 = (byte) (((9 & 3) << shift) | RmtModule.Track.VOLUME_ONLY), volumeParameter = (byte) (9 >> 2);
		byte pause3 = (byte) ((3 << shift) | RmtModule.Track.PAUSE);
		byte pauseCounted = (byte) RmtModule.Track.PAUSE, pauseCount7 = 7;
		byte speed = (byte) (RmtModule.Track.CONTROL | RmtModule.Track.CONTROL_SPEED), speed5 = 5;
		byte jump = (byte) (RmtModule.Track.CONTROL | RmtModule.Track.CONTROL_JUMP);
		byte end = (byte) (RmtModule.Track.CONTROL | RmtModule.Track.CONTROL_END);
		RmtModule.Track track = RmtModule.Track.of(new byte[] { noteC1Instrument2Volume15, noteParameter, volumeOnly9, volumeParameter, pause3, pauseCounted, pauseCount7, speed, speed5, end });
		assertEquals(1 + 1 + 3 + 7, track.lineCount());
		// a track that fills its length has no end marker
		assertEquals(2, RmtModule.Track.of(new byte[] { 0, 0, (byte) RmtModule.Track.VOLUME_ONLY, 0 }).lineCount());
		// a jump ends the track
		assertEquals(1, RmtModule.Track.of(new byte[] { 0, 0, jump, 0 }).lineCount());
		assertEquals(0, RmtModule.Track.of(new byte[] { pauseCounted, 0 }).lineCount()); // a pause of 0 lines ends the track
	}

	@Test
	void malformedBytesAreRejectedWithTheReason() {
		byte nonEvent = (byte) (RmtModule.Track.CONTROL | (1 << RmtModule.Track.HIGH_BITS_SHIFT)); // $7f: the one control kind that does not exist
		byte end = (byte) (RmtModule.Track.CONTROL | RmtModule.Track.CONTROL_END);
		byte jump = (byte) (RmtModule.Track.CONTROL | RmtModule.Track.CONTROL_JUMP);
		assertThrows(RmtModule.FormatException.class, () -> RmtModule.Track.of(new byte[] { nonEvent }), "$7f is not an event");
		assertThrows(RmtModule.FormatException.class, () -> RmtModule.Track.of(new byte[] { 0 }), "a note cut short");
		assertThrows(RmtModule.FormatException.class, () -> RmtModule.Track.of(new byte[] { end, 0 }), "a byte after the end");
		assertThrows(RmtModule.FormatException.class, () -> RmtModule.Track.of(new byte[] { jump, 5 }), "a jump outside the track");

		byte[] module = RmtModuleCodec.fromSong(newSongWithOneNote(), SongIOType.RMT).toBytes(LOAD);
		byte[] mem = new byte[Memory.SIZE];
		System.arraycopy(module, 0, mem, LOAD, module.length);
		RmtModule.parse(mem, LOAD, LOAD + module.length); // valid as built
		assertRejected(mem, module.length, 0, 'X', "RMT");
		assertRejected(mem, module.length, RmtModule.Offset.CHANNELS, '6', "RMT4 or RMT8");
		assertRejected(mem, module.length, RmtModule.Offset.SONG_SPEED, 0, "song speed is 0");
		assertRejected(mem, module.length, RmtModule.Offset.INSTRUMENT_SPEED, RmtModule.MAX_INSTRUMENT_SPEED + 1, "instrument speed 9");
		assertRejected(mem, module.length, RmtModule.Offset.FORMAT_VERSION, 0, "Format version 0");
		assertRejected(mem, module.length, RmtModule.Offset.FORMAT_VERSION, RmtModule.FORMAT_VERSION + 1, "format version 2");
		assertRejected(mem, module.length, RmtModule.Offset.SONG, Memory.MAX_BYTE, "track sequence table"); // the low byte alone points past the module
	}

	private static void assertRejected(byte[] mem, int length, int offset, int value, String reason) {
		byte[] copy = mem.clone();
		copy[LOAD + offset] = (byte) value;
		RmtModule.FormatException e = assertThrows(RmtModule.FormatException.class, () -> RmtModule.parse(copy, LOAD, LOAD + length));
		assertTrue(e.getMessage().contains(reason), e.getMessage());
	}

	private static Song newSongWithOneNote() {
		Song song = newSong();
		song.setMainSpeed(SONG_SPEED);
		song.setInstrumentSpeed(1);
		song.setChannels(Song.Channels.MONO);
		Track track = song.tracks.getTrack(0);
		track.note[0] = 12;
		track.instr[0] = 0;
		track.volume[0] = 15;
		track.len = TRACK_LENGTH;
		song.song[0][0] = 0;
		song.songGo[1] = 0;
		return song;
	}

	@Test
	void aStereoGoToAsTheLastLineTakesFourBytesLikeRmtWrites() {
		RmtModule module = new RmtModule(RmtModule.Channels.STEREO, TRACK_LENGTH, SONG_SPEED, 1, RmtModule.FORMAT_VERSION, Map.of(), Map.of(),
				List.of(RmtModule.SongLine.tracks(0, 1, 2, 3, 4, 5, 6, 7), RmtModule.SongLine.goTo(0)));
		assertEquals(RmtModule.Channels.STEREO + RmtModule.SongLine.GO_TO_LENGTH, module.songDataLength());
		byte[] bytes = module.toBytes(LOAD);
		assertEquals(RmtModule.HEADER_LENGTH + module.songDataLength(), bytes.length);
		byte[] mem = new byte[Memory.SIZE];
		System.arraycopy(bytes, 0, mem, LOAD, bytes.length);
		assertEquals(module, RmtModule.parse(mem, LOAD, LOAD + bytes.length));
	}
}
