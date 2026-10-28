package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An RMT module of format version 1, as content (plan 60): the header
 * values, the instruments and tracks by number, and the song lines -
 * doc/rmt_format.md's structs, one Java type each. The module is free of
 * addresses: the header's four table addresses and the pointer tables
 * follow from the layout and the load address (the 16-bit space of
 * {@link Memory}), so {@link #write} computes
 * them for any load address (which is how a module is relocated) and
 * {@link #parse} validates them.
 *
 * <p>The layout {@link #write} produces is the one RMT has always written
 * ({@code SongIO.makeModule}): the 16-byte header, the instrument pointer
 * table (a word per instrument number up to the highest one present, 0 for
 * an absent one), the track pointer tables (the low bytes, then the high
 * bytes, a byte per track number up to the highest present, 0 for an
 * absent one), the instrument structs in number order, the track event
 * bytes in number order, then the song lines. {@link #parse} accepts any
 * arrangement the pointers describe within the module's bytes.
 *
 * <p>Format version 0 - the pre-release instrument layout
 * {@code Instruments.ataV0ToInstr} still reads - is not a version this
 * container represents; {@link #parse} rejects it with that reason.
 *
 * <p>The structs are the nested classes {@link SongLine}, {@link Track}
 * and {@link Instrument} (whose names shadow the editor's {@code Instrument}
 * and {@code Track} inside this class - the container needs neither). The
 * {@code Song} conversions live in {@link RmtModuleCodec}, the file around
 * the module in {@link RmtModuleFile}.
 */
public final class RmtModule {

	/** The header's length in bytes. */
	public static final int HEADER_LENGTH = 16;

	/** The longest track length the header can express ({@code $00} means 256). */
	public static final int MAX_TRACK_LENGTH = 256;

	/** The highest instrument speed the player accepts (player calls per frame). */
	public static final int MAX_INSTRUMENT_SPEED = 8;

	/** The header's first three bytes. */
	public static final String SIGNATURE = "RMT";

	/** The offsets within the header (doc/rmt_format.md, "Module Header Struct"). */
	public static final class Offset {

		/** The channel digit, right after the signature. */
		public static final int CHANNELS = 3;

		/** The track length byte ({@code $00} means 256). */
		public static final int TRACK_LENGTH = 4;

		/** The song speed byte. */
		public static final int SONG_SPEED = 5;

		/** The instrument speed byte. */
		public static final int INSTRUMENT_SPEED = 6;

		/** The format version byte. */
		public static final int FORMAT_VERSION = 7;

		/** The instruments table's address word. */
		public static final int INSTRUMENTS_TABLE = 8;

		/** The address word of the track addresses' low-byte table. */
		public static final int TRACKS_LOW_TABLE = 10;

		/** The address word of the track addresses' high-byte table. */
		public static final int TRACKS_HIGH_TABLE = 12;

		/** The track sequence table's address word. */
		public static final int SONG = 14;

		private Offset() {
		}
	}

	/**
	 * The channel counts the format knows, the header's digit: one POKEY's
	 * four channels or two POKEYs' eight. They are also the counts the
	 * tracker drivers play, so {@link Song#setChannels} accepts exactly these.
	 */
	public static final class Channels {

		/** A mono module ({@code RMT4}): CH1-CH4 of one POKEY. */
		public static final int MONO = 4;

		/** A stereo module ({@code RMT8}): CH1-CH4 of the left and of the right POKEY. */
		public static final int STEREO = 8;

		private Channels() {
		}
	}

	/** The format version this container represents ({@link RmtFormatVersion#V1}). */
	public static final int FORMAT_VERSION = RmtFormatVersion.V1;

	/** The highest song speed the header byte can hold (frames per song line). */
	public static final int MAX_SONG_SPEED = Memory.MAX_BYTE;

	/** The most instruments a module can hold - the editor's {@code Instruments.INSTRSNUM}. */
	public static final int MAX_INSTRUMENTS = 64;

	/** The most tracks a module can hold - the editor's {@code Tracks.TRACKSNUM}: {@code $fe} and {@code $ff} are the sequence table's markers. */
	public static final int MAX_TRACKS = 254;

	/** The most song lines a module can hold - the editor's {@code Song.SONGLEN}: a "go to" target is a byte. */
	public static final int MAX_SONG_LINES = 256;

	private final int channels;
	private final int trackLength;
	private final int songSpeed;
	private final int instrumentSpeed;
	private final int formatVersion;
	private final SortedMap<Integer, Instrument> instruments;
	private final SortedMap<Integer, Track> tracks;
	private final List<SongLine> songLines;

	/**
	 * @param channels {@link Channels#MONO} ({@code RMT4}) or {@link Channels#STEREO} ({@code RMT8})
	 * @param trackLength the length of every track in lines, 1-{@link #MAX_TRACK_LENGTH}
	 * @param songSpeed frames per song line, 1-255
	 * @param instrumentSpeed player calls per frame, 1-{@link #MAX_INSTRUMENT_SPEED}
	 * @param formatVersion {@link RmtFormatVersion#V1}, the only version this container represents
	 * @param instruments the instruments by number, 0-{@code Instruments.INSTRSNUM - 1}
	 * @param tracks the tracks by number, 0-{@code Tracks.TRACKSNUM - 1}; a track line referencing a missing number plays an empty track
	 * @param songLines the song lines; a track line's channel count must equal {@code channels}
	 */
	public RmtModule(int channels, int trackLength, int songSpeed, int instrumentSpeed, int formatVersion, Map<Integer, Instrument> instruments, Map<Integer, Track> tracks,
			List<SongLine> songLines) {
		if (channels != Channels.MONO && channels != Channels.STEREO) {
			throw new FormatException("The module has " + channels + " channels, not " + Channels.MONO + " (RMT4) or " + Channels.STEREO + " (RMT8).");
		}
		if (trackLength < 1 || trackLength > MAX_TRACK_LENGTH) {
			throw new FormatException("The track length " + trackLength + " is not 1-" + MAX_TRACK_LENGTH + ".");
		}
		if (songSpeed < 1 || songSpeed > MAX_SONG_SPEED) {
			throw new FormatException("The song speed " + songSpeed + " is not 1-" + MAX_SONG_SPEED + ".");
		}
		if (instrumentSpeed < 1 || instrumentSpeed > MAX_INSTRUMENT_SPEED) {
			throw new FormatException("The instrument speed " + instrumentSpeed + " is not 1-" + MAX_INSTRUMENT_SPEED + ".");
		}
		if (formatVersion != FORMAT_VERSION) {
			throw new FormatException("The format version " + formatVersion + " is not " + FORMAT_VERSION + ", the only version this container represents.");
		}
		for (int number : instruments.keySet()) {
			if (number < 0 || number >= MAX_INSTRUMENTS) {
				throw new FormatException("The instrument number " + number + " is not 0-" + (MAX_INSTRUMENTS - 1) + ".");
			}
		}
		for (int number : tracks.keySet()) {
			if (number < 0 || number >= MAX_TRACKS) {
				throw new FormatException("The track number " + number + " is not 0-" + (MAX_TRACKS - 1) + ".");
			}
		}
		for (SongLine line : songLines) {
			if (!line.isGoTo() && line.channels() != channels) {
				throw new FormatException("A song line has " + line.channels() + " channels, the module " + channels + ".");
			}
		}
		this.channels = channels;
		this.trackLength = trackLength;
		this.songSpeed = songSpeed;
		this.instrumentSpeed = instrumentSpeed;
		this.formatVersion = formatVersion;
		this.instruments = Collections.unmodifiableSortedMap(new TreeMap<>(instruments));
		this.tracks = Collections.unmodifiableSortedMap(new TreeMap<>(tracks));
		this.songLines = List.copyOf(songLines);
	}

	// ---- parsing ----

	/**
	 * Parses the module at {@code mem[fromAddr..endAddr)} - the bytes a
	 * binary load block of an {@code .rmt} file puts there - validating the
	 * header, the pointers and every struct; the reason for a rejection is
	 * the exception's message.
	 */
	public static RmtModule parse(byte[] mem, int fromAddr, int endAddr) {
		if (fromAddr < 0 || endAddr > mem.length || endAddr - fromAddr < HEADER_LENGTH) {
			throw new FormatException("The module is shorter than its " + HEADER_LENGTH + "-byte header.");
		}
		for (int i = 0; i < SIGNATURE.length(); i++) {
			if (mem[fromAddr + i] != SIGNATURE.charAt(i)) {
				throw new FormatException("The module does not start with \"" + SIGNATURE + "\".");
			}
		}
		int channelByte = Memory.unsignedByte(mem, fromAddr + Offset.CHANNELS);
		if (channelByte != '0' + Channels.MONO && channelByte != '0' + Channels.STEREO) {
			throw new FormatException("The header is \"RMT" + (char) channelByte + "\", not RMT4 or RMT8.");
		}
		int channels = channelByte - '0';
		int trackLengthByte = Memory.unsignedByte(mem, fromAddr + Offset.TRACK_LENGTH);
		int trackLength = trackLengthByte == 0 ? MAX_TRACK_LENGTH : trackLengthByte;
		int songSpeed = Memory.unsignedByte(mem, fromAddr + Offset.SONG_SPEED);
		if (songSpeed < 1) {
			throw new FormatException("The song speed is 0.");
		}
		int instrumentSpeed = Memory.unsignedByte(mem, fromAddr + Offset.INSTRUMENT_SPEED);
		if (instrumentSpeed < 1 || instrumentSpeed > MAX_INSTRUMENT_SPEED) {
			throw new FormatException("The instrument speed " + instrumentSpeed + " is not 1-" + MAX_INSTRUMENT_SPEED + ".");
		}
		int formatVersion = Memory.unsignedByte(mem, fromAddr + Offset.FORMAT_VERSION);
		if (formatVersion == 0) {
			throw new FormatException("Format version 0 (the pre-release instrument layout) is not represented by this container.");
		}
		if (formatVersion > FORMAT_VERSION) {
			throw new FormatException("The format version " + formatVersion + " is above " + FORMAT_VERSION + ".");
		}
		int ptrInstruments = Memory.word(mem, fromAddr + Offset.INSTRUMENTS_TABLE);
		int ptrTracksLow = Memory.word(mem, fromAddr + Offset.TRACKS_LOW_TABLE);
		int ptrTracksHigh = Memory.word(mem, fromAddr + Offset.TRACKS_HIGH_TABLE);
		int ptrSong = Memory.word(mem, fromAddr + Offset.SONG);
		requireInside(ptrInstruments, fromAddr + HEADER_LENGTH, endAddr, "instruments table");
		requireInside(ptrTracksLow, ptrInstruments, endAddr, "low-byte track addresses table");
		requireInside(ptrTracksHigh, ptrTracksLow, endAddr, "high-byte track addresses table");
		requireInside(ptrSong, ptrTracksHigh, endAddr, "track sequence table");
		if ((ptrTracksLow - ptrInstruments) % Memory.WORD_LENGTH != 0) {
			throw new FormatException("The instruments table has an odd length, " + (ptrTracksLow - ptrInstruments) + ".");
		}
		int instrumentCount = (ptrTracksLow - ptrInstruments) / Memory.WORD_LENGTH;
		int trackCount = ptrTracksHigh - ptrTracksLow;
		if (instrumentCount > MAX_INSTRUMENTS) {
			throw new FormatException("The instruments table has " + instrumentCount + " entries, more than " + MAX_INSTRUMENTS + ".");
		}
		if (trackCount > MAX_TRACKS) {
			throw new FormatException("The track addresses tables have " + trackCount + " entries, more than " + MAX_TRACKS + ".");
		}

		Map<Integer, Instrument> instruments = new TreeMap<>();
		for (int number = 0; number < instrumentCount; number++) {
			int ptr = Memory.word(mem, ptrInstruments + number * Memory.WORD_LENGTH);
			if (ptr == 0) {
				continue; // an absent instrument
			}
			requireInside(ptr, fromAddr + HEADER_LENGTH, endAddr - 1, "instrument " + number);
			try {
				instruments.put(number, Instrument.parse(mem, ptr, endAddr - ptr));
			} catch (FormatException e) {
				throw new FormatException("Instrument " + number + ": " + e.getMessage());
			}
		}

		Map<Integer, Track> tracks = new TreeMap<>();
		for (int number = 0; number < trackCount; number++) {
			int ptr = Memory.splitWord(mem, ptrTracksLow + number, ptrTracksHigh + number);
			if (ptr == 0) {
				continue; // an absent track
			}
			// A track's extent is not in its bytes: it ends where the next
			// present track starts, the last one where the song lines start.
			int end = ptrSong;
			for (int next = number + 1; next < trackCount; next++) {
				int nextPtr = Memory.splitWord(mem, ptrTracksLow + next, ptrTracksHigh + next);
				if (nextPtr != 0) {
					end = nextPtr;
					break;
				}
			}
			requireInside(ptr, fromAddr + HEADER_LENGTH, endAddr - 1, "track " + number);
			if (end < ptr || end > endAddr) {
				throw new FormatException("Track " + number + " at $" + Integer.toHexString(ptr) + " ends at $" + Integer.toHexString(end) + ", before its start or outside the module.");
			}
			try {
				tracks.put(number, Track.of(mem, ptr, end - ptr));
			} catch (FormatException e) {
				throw new FormatException("Track " + number + ": " + e.getMessage());
			}
		}

		List<SongLine> songLines = parseSongLines(mem, ptrSong, endAddr, channels);
		return new RmtModule(channels, trackLength, songSpeed, instrumentSpeed, formatVersion, instruments, tracks, songLines);
	}

	private static void requireInside(int address, int min, int max, String what) {
		if (address < min || address > max) {
			throw new FormatException("The " + what + " at $" + Integer.toHexString(address) + " lies outside $" + Integer.toHexString(min) + "-$" + Integer.toHexString(max) + ".");
		}
	}

	/**
	 * The track sequence table at {@code mem[songAddr..endAddr)}: a line per
	 * {@code channels} bytes, a "go to" line's address word checked against
	 * its target; a final "go to" line in a stereo module may end after its
	 * four bytes, which is how RMT writes it.
	 */
	private static List<SongLine> parseSongLines(byte[] mem, int songAddr, int endAddr, int channels) {
		List<SongLine> lines = new ArrayList<>();
		int pos = songAddr;
		while (pos < endAddr) {
			int lineNumber = lines.size();
			if (Memory.unsignedByte(mem, pos) == SongLine.GO_TO_MARKER) {
				if (pos + SongLine.GO_TO_LENGTH > endAddr) {
					throw new FormatException("The go to line " + lineNumber + " is cut short.");
				}
				int target = Memory.unsignedByte(mem, pos + SongLine.Offset.TARGET);
				int ptr = Memory.word(mem, pos + SongLine.Offset.ADDRESS);
				if (ptr != ((songAddr + target * channels) & Memory.MAX_ADDRESS)) {
					throw new FormatException("The go to line " + lineNumber + " targets line " + target + " but addresses $" + Integer.toHexString(ptr) + ".");
				}
				lines.add(SongLine.goTo(target));
			} else {
				if (pos + channels > endAddr) {
					throw new FormatException("The song line " + lineNumber + " is cut short.");
				}
				int[] numbers = new int[channels];
				for (int channel = 0; channel < channels; channel++) {
					numbers[channel] = Memory.unsignedByte(mem, pos + channel);
				}
				lines.add(SongLine.tracks(numbers));
			}
			pos += channels;
		}
		return lines;
	}

	// ---- writing ----

	/** The number of instrument pointer table entries: up to the highest instrument number present. */
	private int instrumentTableEntries() {
		return instruments.isEmpty() ? 0 : instruments.lastKey() + 1;
	}

	/** The number of track address table entries: up to the highest track number present. */
	private int trackTableEntries() {
		return tracks.isEmpty() ? 0 : tracks.lastKey() + 1;
	}

	/**
	 * The song data's length in bytes: every line takes a byte per channel,
	 * except a "go to" line that is the last line, which takes only its
	 * four bytes - as RMT has always written it.
	 */
	public int songDataLength() {
		if (songLines.isEmpty()) {
			return 0;
		}
		int last = songLines.size() - 1;
		return songLines.get(last).isGoTo() ? last * channels + SongLine.GO_TO_LENGTH : (last + 1) * channels;
	}

	/** The module's length in bytes when written. */
	public int length() {
		int length = HEADER_LENGTH + instrumentTableEntries() * Memory.WORD_LENGTH + trackTableEntries() * Memory.WORD_LENGTH; // a track's address is a word too, split over the two tables
		for (Instrument instrument : instruments.values()) {
			length += instrument.length();
		}
		for (Track track : tracks.values()) {
			length += track.length();
		}
		return length + songDataLength();
	}

	/**
	 * Writes the module at {@code mem[loadAddress...]} in RMT's layout,
	 * with every pointer relative to {@code loadAddress}; returns the
	 * address of the first byte after the module.
	 */
	public int write(byte[] mem, int loadAddress) {
		int end = loadAddress + length();
		if (loadAddress < 0 || end - 1 > Memory.MAX_ADDRESS || end > mem.length) {
			throw new FormatException("The module of " + length() + " bytes does not fit at $" + Integer.toHexString(loadAddress) + ": its last byte would lie beyond $" + Integer.toHexString(Memory.MAX_ADDRESS) + ".");
		}
		for (int i = 0; i < SIGNATURE.length(); i++) {
			mem[loadAddress + i] = (byte) SIGNATURE.charAt(i);
		}
		mem[loadAddress + Offset.CHANNELS] = (byte) ('0' + channels);
		mem[loadAddress + Offset.TRACK_LENGTH] = (byte) (trackLength == MAX_TRACK_LENGTH ? 0 : trackLength);
		mem[loadAddress + Offset.SONG_SPEED] = (byte) songSpeed;
		mem[loadAddress + Offset.INSTRUMENT_SPEED] = (byte) instrumentSpeed;
		mem[loadAddress + Offset.FORMAT_VERSION] = (byte) formatVersion;

		int instrumentEntries = instrumentTableEntries();
		int trackEntries = trackTableEntries();
		int ptrInstruments = loadAddress + HEADER_LENGTH;
		int ptrTracksLow = ptrInstruments + instrumentEntries * Memory.WORD_LENGTH;
		int ptrTracksHigh = ptrTracksLow + trackEntries;
		int ptrData = ptrTracksHigh + trackEntries;

		for (int number = 0; number < instrumentEntries; number++) {
			Instrument instrument = instruments.get(number);
			int ptr = 0;
			if (instrument != null) {
				ptr = ptrData;
				ptrData = instrument.write(mem, ptrData);
			}
			Memory.writeWord(mem, ptrInstruments + number * Memory.WORD_LENGTH, ptr);
		}
		for (int number = 0; number < trackEntries; number++) {
			Track track = tracks.get(number);
			int ptr = 0;
			if (track != null) {
				ptr = ptrData;
				ptrData = track.write(mem, ptrData);
			}
			mem[ptrTracksLow + number] = Memory.lowByte(ptr);
			mem[ptrTracksHigh + number] = Memory.highByte(ptr);
		}
		int ptrSong = ptrData;
		writeSongLines(mem, ptrSong);

		Memory.writeWord(mem, loadAddress + Offset.INSTRUMENTS_TABLE, ptrInstruments);
		Memory.writeWord(mem, loadAddress + Offset.TRACKS_LOW_TABLE, ptrTracksLow);
		Memory.writeWord(mem, loadAddress + Offset.TRACKS_HIGH_TABLE, ptrTracksHigh);
		Memory.writeWord(mem, loadAddress + Offset.SONG, ptrSong);
		return end;
	}

	private void writeSongLines(byte[] mem, int songAddr) {
		int end = songAddr + songDataLength();
		int pos = songAddr;
		for (SongLine line : songLines) {
			if (line.isGoTo()) {
				int target = line.goToTarget();
				int ptr = (songAddr + target * channels) & Memory.MAX_ADDRESS;
				mem[pos] = (byte) SongLine.GO_TO_MARKER;
				mem[pos + SongLine.Offset.TARGET] = (byte) target;
				Memory.writeWord(mem, pos + SongLine.Offset.ADDRESS, ptr);
				for (int channel = SongLine.GO_TO_LENGTH; channel < channels && pos + channel < end; channel++) {
					mem[pos + channel] = (byte) SongLine.EMPTY; // the padding of a stereo go to line
				}
			} else {
				for (int channel = 0; channel < channels; channel++) {
					mem[pos + channel] = (byte) line.trackNumber(channel);
				}
			}
			pos += channels;
		}
	}

	/** The module as bytes, laid out for {@code loadAddress}. */
	public byte[] toBytes(int loadAddress) {
		byte[] mem = new byte[loadAddress + length()];
		write(mem, loadAddress);
		return Arrays.copyOfRange(mem, loadAddress, mem.length);
	}

	// ---- content ----

	/** 4 (mono, {@code RMT4}) or 8 (stereo, {@code RMT8}). */
	public int channels() {
		return channels;
	}

	public int trackLength() {
		return trackLength;
	}

	public int songSpeed() {
		return songSpeed;
	}

	public int instrumentSpeed() {
		return instrumentSpeed;
	}

	public int formatVersion() {
		return formatVersion;
	}

	/** The instruments by number, in number order. */
	public SortedMap<Integer, Instrument> instruments() {
		return instruments;
	}

	/** The tracks by number, in number order. */
	public SortedMap<Integer, Track> tracks() {
		return tracks;
	}

	public List<SongLine> songLines() {
		return songLines;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RmtModule m && channels == m.channels && trackLength == m.trackLength && songSpeed == m.songSpeed && instrumentSpeed == m.instrumentSpeed
				&& formatVersion == m.formatVersion && instruments.equals(m.instruments) && tracks.equals(m.tracks) && songLines.equals(m.songLines);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(channels, trackLength, songSpeed, instrumentSpeed, formatVersion, instruments, tracks, songLines);
	}

	@Override
	public String toString() {
		return "RMT" + channels + " module: " + instruments.size() + " instruments, " + tracks.size() + " tracks, " + songLines.size() + " song lines, " + length() + " bytes";
	}

	// ---- the structs ----

	/**
	 * A byte sequence that is not a valid RMT module of format version 1, or
	 * a song that cannot be encoded as one - with the reason in the message.
	 * Unchecked, like the {@link IllegalArgumentException} it is: the callers
	 * that want the C++ codec's silent failure value catch it.
	 */
	public static final class FormatException extends IllegalArgumentException {

		private static final long serialVersionUID = 1L;

		public FormatException(String message) {
			super(message);
		}
	}

	/**
	 * One line of the module's track sequence table (doc/rmt_format.md, "Track
	 * Sequence Table"): either the track number of each channel - a value of
	 * {@link #EMPTY} or above means an empty track - or a "go to" line, which
	 * the file holds as the marker {@code $fe}, the target line, and the
	 * target's address as a word; the address follows from the song data's
	 * own address, so only the target line is content.
	 */
	public static final class SongLine {

		/** The marker in the first channel's byte of a "go to" line. */
		public static final int GO_TO_MARKER = 0xfe;

		/** The track number that means "no track" on a channel; {@code $fe} outside the first channel means the same. */
		public static final int EMPTY = 0xff;

		/** The bytes a "go to" line consists of: the marker, the target line and the target's address word; a stereo line pads it to the channel count unless it is the last line. */
		public static final int GO_TO_LENGTH = 4;

		/** The offsets within a "go to" line; the marker is at 0. */
		public static final class Offset {

			/** The target line byte. */
			public static final int TARGET = 1;

			/** The target's address word. */
			public static final int ADDRESS = 2;

			private Offset() {
			}
		}

		private final int[] trackNumbers; // null for a "go to" line
		private final int goTo; // -1 for a track line

		private SongLine(int[] trackNumbers, int goTo) {
			this.trackNumbers = trackNumbers;
			this.goTo = goTo;
		}

		/** A line of track numbers, one per channel (4 or 8), each 0-255. */
		public static SongLine tracks(int... trackNumbers) {
			if (trackNumbers.length != Channels.MONO && trackNumbers.length != Channels.STEREO) {
				throw new IllegalArgumentException("A song line has " + Channels.MONO + " or " + Channels.STEREO + " channels, not " + trackNumbers.length + ".");
			}
			for (int number : trackNumbers) {
				if (number < 0 || number > Memory.MAX_BYTE) {
					throw new IllegalArgumentException("A track number is a byte, not " + number + ".");
				}
			}
			return new SongLine(trackNumbers.clone(), -1);
		}

		/** A "go to" line: the song continues at line {@code target} (0-255). */
		public static SongLine goTo(int target) {
			if (target < 0 || target > Memory.MAX_BYTE) {
				throw new IllegalArgumentException("A go to target is a byte, not " + target + ".");
			}
			return new SongLine(null, target);
		}

		public boolean isGoTo() {
			return trackNumbers == null;
		}

		/** The target line of a "go to" line; a track line has none. */
		public int goToTarget() {
			if (!isGoTo()) {
				throw new IllegalStateException("Not a go to line.");
			}
			return goTo;
		}

		/** The track number of {@code channel}, as stored (255 means empty); a "go to" line has none. */
		public int trackNumber(int channel) {
			if (isGoTo()) {
				throw new IllegalStateException("A go to line has no track numbers.");
			}
			return trackNumbers[channel];
		}

		/** Whether the channel plays no track ({@link RmtModule#MAX_TRACKS} and above, i.e. {@code $fe}/{@code $ff}). */
		public boolean isEmpty(int channel) {
			return trackNumber(channel) >= MAX_TRACKS;
		}

		/** Whether any channel plays a track - what makes the line count toward the song data's length. */
		public boolean hasTracks() {
			if (isGoTo()) {
				return false;
			}
			for (int number : trackNumbers) {
				if (number < MAX_TRACKS) {
					return true;
				}
			}
			return false;
		}

		/** The number of channels of a track line. */
		public int channels() {
			if (isGoTo()) {
				throw new IllegalStateException("A go to line has no channels.");
			}
			return trackNumbers.length;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof SongLine line && goTo == line.goTo && Arrays.equals(trackNumbers, line.trackNumbers);
		}

		@Override
		public int hashCode() {
			return 31 * goTo + Arrays.hashCode(trackNumbers);
		}

		@Override
		public String toString() {
			return isGoTo() ? "go to " + goTo : Arrays.toString(trackNumbers);
		}
	}

	/**
	 * One track of the module (doc/rmt_format.md, "Track Struct"): the event
	 * stream as bytes, validated by a walker that knows the four event kinds
	 * (plan 60, decision 2 - no typed event model yet). A track's extent is
	 * not in the bytes: the module knows it from the next track's address, so
	 * a track ends either at its end marker or, for a track that fills its
	 * whole length, at its last event.
	 *
	 * <p>The events, by the low six bits of the first byte: {@code $00-$3c} a
	 * note (second byte: instrument and the low volume bits), {@code $3d}
	 * volume only (second byte), {@code $3e} a pause of 1-3 lines in the
	 * high two bits or, if those are zero, of the next byte's count - a
	 * count of 0 ends the track, {@code $3f} with the high two bits zero a
	 * speed change (next byte), with {@code $80} a jump to the next byte's
	 * offset within the track (ends the track), with {@code $c0} the end
	 * marker. {@code $3f} with the high bits {@code $40} is not an event.
	 */
	public static final class Track {

		/** The bits of an event's first byte that hold the event kind. */
		public static final int EVENT_MASK = 0x3f;

		/** The two high bits of an event's first byte: the volume's high bits of a note, the pause count, or the control kind. */
		public static final int HIGH_BITS_MASK = 0xc0;

		/** The highest note event; {@code $00-$3c} are notes. */
		public static final int MAX_NOTE = 0x3c;

		/** The volume-only event. */
		public static final int VOLUME_ONLY = 0x3d;

		/** The pause event: 1-3 lines in the high bits, or a count in the next byte. */
		public static final int PAUSE = 0x3e;

		/** The control event; its high bits say which: {@link #CONTROL_SPEED}, {@link #CONTROL_JUMP} or {@link #CONTROL_END}. */
		public static final int CONTROL = 0x3f;

		/** The high bits of a speed change (the next byte). */
		public static final int CONTROL_SPEED = 0x00;

		/** The high bits of a jump to the next byte's offset within the track, which ends the track. */
		public static final int CONTROL_JUMP = 0x80;

		/** The high bits of the end marker. */
		public static final int CONTROL_END = 0xc0;

		/** The most event bytes a track can have - the encoder's buffer. */
		public static final int MAX_LENGTH = 256;

		/** The bit the two high bits start at - a note's high volume bits, a pause's count, a control event's kind. */
		public static final int HIGH_BITS_SHIFT = 6;

		/** The bytes of an event without a parameter byte: a short pause, the end marker. */
		public static final int EVENT_LENGTH = 1;

		/** The bytes of an event with a parameter byte: a note, a volume, a counted pause, a speed, a jump. */
		public static final int EVENT_WITH_PARAMETER_LENGTH = 2;

		private final byte[] bytes;
		private final int lineCount;

		private Track(byte[] bytes, int lineCount) {
			this.bytes = bytes;
			this.lineCount = lineCount;
		}

		/** The track with these event bytes, validated; the bytes are copied. */
		public static Track of(byte[] bytes) {
			return of(bytes, 0, bytes.length);
		}

		/** The track with the event bytes {@code data[offset..offset+length)}, validated; the bytes are copied. */
		public static Track of(byte[] data, int offset, int length) {
			if (length > MAX_LENGTH) {
				throw new FormatException("The track has " + length + " bytes, more than " + MAX_LENGTH + ".");
			}
			byte[] bytes = Arrays.copyOfRange(data, offset, offset + length);
			return new Track(bytes, walk(bytes));
		}

		/**
		 * Walks the events, rejecting an event cut short by the track's end,
		 * the non-event {@code $7f}, and bytes after an end; returns the number
		 * of lines the events cover.
		 */
		private static int walk(byte[] bytes) {
			int pos = 0;
			int line = 0;
			while (pos < bytes.length) {
				int first = Memory.unsignedByte(bytes, pos);
				int kind = first & EVENT_MASK;
				int high = first & HIGH_BITS_MASK;
				if (kind <= MAX_NOTE || kind == VOLUME_ONLY) {
					requireSecondByte(bytes, pos, kind <= MAX_NOTE ? "note" : "volume");
					pos += EVENT_WITH_PARAMETER_LENGTH;
					line++;
				} else if (kind == PAUSE) {
					if (high != 0) {
						line += high >> HIGH_BITS_SHIFT;
						pos += EVENT_LENGTH;
					} else {
						requireSecondByte(bytes, pos, "pause");
						int count = Memory.unsignedByte(bytes, pos + 1);
						pos += EVENT_WITH_PARAMETER_LENGTH;
						if (count == 0) {
							return end(bytes, pos, line); // a pause of 0 lines ends the track
						}
						line += count;
					}
				} else if (high == CONTROL_SPEED) {
					requireSecondByte(bytes, pos, "speed");
					pos += EVENT_WITH_PARAMETER_LENGTH;
				} else if (high == CONTROL_JUMP) {
					requireSecondByte(bytes, pos, "jump");
					int target = Memory.unsignedByte(bytes, pos + 1);
					if (target >= bytes.length) {
						throw new FormatException("The jump at offset " + pos + " targets offset " + target + ", outside the track.");
					}
					return end(bytes, pos + EVENT_WITH_PARAMETER_LENGTH, line);
				} else if (high == CONTROL_END) {
					return end(bytes, pos + EVENT_LENGTH, line);
				} else {
					throw new FormatException("Byte $" + Integer.toHexString(first) + " at offset " + pos + " is not an event.");
				}
			}
			return line; // a track that fills its whole length has no end marker
		}

		private static void requireSecondByte(byte[] bytes, int pos, String event) {
			if (pos + 1 >= bytes.length) {
				throw new FormatException("The " + event + " event at offset " + pos + " is cut short by the track's end.");
			}
		}

		private static int end(byte[] bytes, int pos, int line) {
			if (pos != bytes.length) {
				throw new FormatException((bytes.length - pos) + " byte(s) follow the track's end at offset " + (pos - 1) + ".");
			}
			return line;
		}

		/** The number of lines the events cover. */
		public int lineCount() {
			return lineCount;
		}

		/** The length of the event bytes. */
		public int length() {
			return bytes.length;
		}

		/** A copy of the event bytes. */
		public byte[] toBytes() {
			return bytes.clone();
		}

		/** Writes the event bytes at {@code mem[offset...]}; returns the offset after them. */
		int write(byte[] mem, int offset) {
			System.arraycopy(bytes, 0, mem, offset, bytes.length);
			return offset + bytes.length;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Track track && Arrays.equals(bytes, track.bytes);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(bytes);
		}

		@Override
		public String toString() {
			return "track of " + bytes.length + " bytes, " + lineCount + " lines";
		}
	}

	/**
	 * One instrument of the module (doc/rmt_format.md, "Instrument Struct"):
	 * the twelve parameter bytes as typed fields, the table of notes and the
	 * envelope (plan 60). The struct's four pointer bytes - {@code tlen},
	 * {@code tgo}, {@code elen}, {@code ego}, offsets of the table's and the
	 * envelope's end and loop entries from the struct's start - are not
	 * content: they follow from the table's and the envelope's lengths and
	 * the loop positions, so {@link #toBytes} computes them and
	 * {@link #parse} validates them.
	 *
	 * <p>Layout: bytes 0-3 the pointers, 4 {@code tspd} (bits 0-5),
	 * {@code tmode} (bit 6), {@code ttype} (bit 7), 5 AUDCTL, 6 {@code Vslide},
	 * 7 {@code Vmin} (bits 4-7), 8 delay, 9 vibrato, 10 {@code fshift}, 11
	 * unused, 12... the table of notes (1-32 bytes), then the envelope (1-48
	 * entries of 3 bytes).
	 */
	public static final class Instrument {

		/** The number of parameter bytes before the table of notes. */
		public static final int PARAMETERS_LENGTH = 12;

		/** The most entries a table of notes can have - the editor's {@code Instrument.NOTE_TABLE_MAX_LEN}, which the player's tables fix. */
		public static final int MAX_NOTE_TABLE_ENTRIES = 32;

		/** The most entries an envelope can have - the editor's {@code Instrument.ENVELOPE_MAX_COLUMNS}. */
		public static final int MAX_ENVELOPE_ENTRIES = 48;

		/** The bytes of an envelope entry: volume, control, XY. */
		public static final int ENVELOPE_ENTRY_LENGTH = 3;

		/** The highest {@code tspd} (bits 0-5 of byte 4). */
		public static final int MAX_TABLE_SPEED = 0x3f;

		/** The highest vibrato value. */
		public static final int MAX_VIBRATO = 3;

		/** The highest volume, of the envelope's left/right nibbles and of {@code Vmin}. */
		public static final int MAX_VOLUME = 0x0f;

		/** The longest struct: the parameters, a full table of notes and a full envelope. */
		public static final int MAX_LENGTH = PARAMETERS_LENGTH + MAX_NOTE_TABLE_ENTRIES + MAX_ENVELOPE_ENTRIES * ENVELOPE_ENTRY_LENGTH;

		/** The offsets within the struct's parameter bytes (doc/rmt_format.md, "Instrument Struct"); the table of notes follows at {@link #PARAMETERS_LENGTH}. */
		public static final class Offset {

			/** {@code tlen}, the table of notes' last entry. */
			public static final int TABLE_END = 0;

			/** {@code tgo}, the table of notes' loop entry. */
			public static final int TABLE_LOOP = 1;

			/** {@code elen}, the envelope's last entry. */
			public static final int ENVELOPE_END = 2;

			/** {@code ego}, the envelope's loop entry. */
			public static final int ENVELOPE_LOOP = 3;

			/** The byte holding {@code tspd} (bits 0-5), {@code tmode} (bit 6) and {@code ttype} (bit 7). */
			public static final int TABLE_CONTROL = 4;

			/** The AUDCTL byte. */
			public static final int AUDCTL = 5;

			/** {@code Vslide}. */
			public static final int VOLUME_SLIDE = 6;

			/** {@code Vmin} (bits 4-7). */
			public static final int VOLUME_MINIMUM = 7;

			/** The delay byte. */
			public static final int DELAY = 8;

			/** The vibrato byte. */
			public static final int VIBRATO = 9;

			/** {@code fshift}. */
			public static final int FREQUENCY_SHIFT = 10;

			/** The unused byte. */
			public static final int UNUSED = 11;

			private Offset() {
			}
		}

		/** The bit of the table control byte that holds {@code tmode}. */
		public static final int TABLE_MODE_SHIFT = 6;

		/** The bit of the table control byte that holds {@code ttype}. */
		public static final int TABLE_TYPE_SHIFT = 7;

		/** {@code tmode} 0: the table's value is set as the note. */
		public static final int TABLE_MODE_SET = 0;

		/** {@code tmode} 1: the table's value is added to the note. */
		public static final int TABLE_MODE_ADD = 1;

		/** {@code ttype} 0: the table holds notes. */
		public static final int TABLE_TYPE_NOTES = 0;

		/** {@code ttype} 1: the table holds frequencies. */
		public static final int TABLE_TYPE_FREQUENCIES = 1;

		/** The bit a byte's high nibble starts at ({@code Vmin}, the right volume, X). */
		public static final int HIGH_NIBBLE_SHIFT = 4;

		/** The low nibble of a byte. */
		public static final int LOW_NIBBLE_MASK = 0x0f;

		/** A single bit. */
		public static final int BIT_MASK = 0x01;

		/** An envelope entry: the three bytes volume, control and XY. */
		public record Envelope(int volume, int control, int xy) {

			/** The offsets within an entry. */
			public static final class Offset {

				/** The volume byte. */
				public static final int VOLUME = 0;

				/** The control byte. */
				public static final int CONTROL = 1;

				/** The XY byte. */
				public static final int XY = 2;

				private Offset() {
				}
			}

			/** Bits 1-3 of the control byte: the distortion, an even AUDC value. */
			public static final int DISTORTION_MASK = 0x0e;

			/** The bit of the control byte the command starts at. */
			public static final int COMMAND_SHIFT = 4;

			/** The three bits of the command. */
			public static final int COMMAND_MASK = 0x07;

			/** The bit of the control byte that holds the filter flag. */
			public static final int FILTER_SHIFT = 7;

			public Envelope {
				requireByte(volume, "volume");
				requireByte(control, "control");
				requireByte(xy, "XY");
			}

			/** Bits 0-3 of the volume byte. */
			public int volumeLeft() {
				return volume & LOW_NIBBLE_MASK;
			}

			/** Bits 4-7 of the volume byte (the same as the left volume in a mono module). */
			public int volumeRight() {
				return volume >> HIGH_NIBBLE_SHIFT;
			}

			/** Bit 0 of the control byte. */
			public int portamento() {
				return control & BIT_MASK;
			}

			/** Bits 1-3 of the control byte, as the even AUDC distortion value 0-14. */
			public int distortion() {
				return control & DISTORTION_MASK;
			}

			/** Bits 4-6 of the control byte. */
			public int command() {
				return (control >> COMMAND_SHIFT) & COMMAND_MASK;
			}

			/** Bit 7 of the control byte. */
			public int filter() {
				return control >> FILTER_SHIFT;
			}

			/** Bits 4-7 of the XY byte. */
			public int x() {
				return xy >> HIGH_NIBBLE_SHIFT;
			}

			/** Bits 0-3 of the XY byte. */
			public int y() {
				return xy & LOW_NIBBLE_MASK;
			}
		}

		private final byte[] noteTable;
		private final int tableLoop;
		private final List<Envelope> envelope;
		private final int envelopeLoop;
		private final int tableSpeed;
		private final int tableMode;
		private final int tableType;
		private final int audctl;
		private final int volumeSlide;
		private final int volumeMinimum;
		private final int delay;
		private final int vibrato;
		private final int frequencyShift;
		private final int unused;

		/**
		 * @param noteTable the table of notes, 1-{@link #MAX_NOTE_TABLE_ENTRIES} bytes
		 * @param tableLoop the index the table loops to ({@code tgo})
		 * @param envelope 1-{@link #MAX_ENVELOPE_ENTRIES} entries
		 * @param envelopeLoop the index the envelope loops to ({@code ego})
		 * @param tableSpeed {@code tspd}, 0-63
		 * @param tableMode {@code tmode}: {@link #TABLE_MODE_SET} or {@link #TABLE_MODE_ADD}
		 * @param tableType {@code ttype}: {@link #TABLE_TYPE_NOTES} or {@link #TABLE_TYPE_FREQUENCIES}
		 * @param audctl the AUDCTL byte
		 * @param volumeSlide {@code Vslide}
		 * @param volumeMinimum {@code Vmin}, 0-15 (stored in bits 4-7)
		 * @param delay the vibrato/frequency-shift delay, 0 for none
		 * @param vibrato 0-3
		 * @param frequencyShift {@code fshift}
		 * @param unused the twelfth byte, 0 in every module RMT writes
		 */
		public Instrument(byte[] noteTable, int tableLoop, List<Envelope> envelope, int envelopeLoop, int tableSpeed, int tableMode, int tableType, int audctl, int volumeSlide,
				int volumeMinimum, int delay, int vibrato, int frequencyShift, int unused) {
			if (noteTable.length < 1 || noteTable.length > MAX_NOTE_TABLE_ENTRIES) {
				throw new FormatException("The table of notes has " + noteTable.length + " entries, not 1-" + MAX_NOTE_TABLE_ENTRIES + ".");
			}
			if (tableLoop < 0 || tableLoop >= noteTable.length) {
				throw new FormatException("The table loop " + tableLoop + " is outside the table of " + noteTable.length + " entries.");
			}
			if (envelope.isEmpty() || envelope.size() > MAX_ENVELOPE_ENTRIES) {
				throw new FormatException("The envelope has " + envelope.size() + " entries, not 1-" + MAX_ENVELOPE_ENTRIES + ".");
			}
			if (envelopeLoop < 0 || envelopeLoop >= envelope.size()) {
				throw new FormatException("The envelope loop " + envelopeLoop + " is outside the envelope of " + envelope.size() + " entries.");
			}
			requireRange(tableSpeed, MAX_TABLE_SPEED, "table speed");
			requireRange(tableMode, TABLE_MODE_ADD, "table mode");
			requireRange(tableType, TABLE_TYPE_FREQUENCIES, "table type");
			requireByte(audctl, "AUDCTL");
			requireByte(volumeSlide, "volume slide");
			requireRange(volumeMinimum, MAX_VOLUME, "volume minimum");
			requireByte(delay, "delay");
			requireRange(vibrato, MAX_VIBRATO, "vibrato");
			requireByte(frequencyShift, "frequency shift");
			requireByte(unused, "unused byte");
			this.noteTable = noteTable.clone();
			this.tableLoop = tableLoop;
			this.envelope = List.copyOf(envelope);
			this.envelopeLoop = envelopeLoop;
			this.tableSpeed = tableSpeed;
			this.tableMode = tableMode;
			this.tableType = tableType;
			this.audctl = audctl;
			this.volumeSlide = volumeSlide;
			this.volumeMinimum = volumeMinimum;
			this.delay = delay;
			this.vibrato = vibrato;
			this.frequencyShift = frequencyShift;
			this.unused = unused;
		}

		private static void requireByte(int value, String what) {
			requireRange(value, Memory.MAX_BYTE, what);
		}

		private static void requireRange(int value, int max, String what) {
			if (value < 0 || value > max) {
				throw new FormatException("The " + what + " " + value + " is not 0-" + max + ".");
			}
		}

		/**
		 * Parses the struct at {@code mem[offset...]}, of which {@code available}
		 * bytes belong to the module; the struct's own pointers say how many of
		 * them it uses (see {@link #length()}).
		 */
		public static Instrument parse(byte[] mem, int offset, int available) {
			if (available < PARAMETERS_LENGTH + 1) {
				throw new FormatException("Only " + available + " bytes remain, fewer than the parameters and one table entry.");
			}
			int tlen = Memory.unsignedByte(mem, offset + Offset.TABLE_END);
			int tgo = Memory.unsignedByte(mem, offset + Offset.TABLE_LOOP);
			int elen = Memory.unsignedByte(mem, offset + Offset.ENVELOPE_END);
			int ego = Memory.unsignedByte(mem, offset + Offset.ENVELOPE_LOOP);
			if (tlen < PARAMETERS_LENGTH) {
				throw new FormatException("The table end pointer " + tlen + " lies before the table's start at " + PARAMETERS_LENGTH + ".");
			}
			if (tgo < PARAMETERS_LENGTH || tgo > tlen) {
				throw new FormatException("The table loop pointer " + tgo + " is not " + PARAMETERS_LENGTH + "-" + tlen + ".");
			}
			int envelopeStart = tlen + 1;
			if (elen < envelopeStart || (elen - envelopeStart) % ENVELOPE_ENTRY_LENGTH != 0) {
				throw new FormatException("The envelope end pointer " + elen + " is not an entry of the envelope starting at " + envelopeStart + ".");
			}
			if (ego < envelopeStart || ego > elen || (ego - envelopeStart) % ENVELOPE_ENTRY_LENGTH != 0) {
				throw new FormatException("The envelope loop pointer " + ego + " is not an entry of the envelope " + envelopeStart + "-" + elen + ".");
			}
			int length = elen + ENVELOPE_ENTRY_LENGTH; // elen addresses the last entry's first byte
			if (length > available) {
				throw new FormatException("The struct is " + length + " bytes long, but only " + available + " remain.");
			}
			byte[] noteTable = Arrays.copyOfRange(mem, offset + PARAMETERS_LENGTH, offset + tlen + 1);
			List<Envelope> envelope = new ArrayList<>();
			for (int entry = offset + envelopeStart; entry <= offset + elen; entry += ENVELOPE_ENTRY_LENGTH) {
				envelope.add(new Envelope(Memory.unsignedByte(mem, entry + Envelope.Offset.VOLUME), Memory.unsignedByte(mem, entry + Envelope.Offset.CONTROL), Memory.unsignedByte(mem, entry + Envelope.Offset.XY)));
			}
			int tableControl = Memory.unsignedByte(mem, offset + Offset.TABLE_CONTROL);
			return new Instrument(noteTable, tgo - PARAMETERS_LENGTH, envelope, (ego - envelopeStart) / ENVELOPE_ENTRY_LENGTH, tableControl & MAX_TABLE_SPEED,
					(tableControl >> TABLE_MODE_SHIFT) & BIT_MASK, tableControl >> TABLE_TYPE_SHIFT, Memory.unsignedByte(mem, offset + Offset.AUDCTL), Memory.unsignedByte(mem, offset + Offset.VOLUME_SLIDE),
					Memory.unsignedByte(mem, offset + Offset.VOLUME_MINIMUM) >> HIGH_NIBBLE_SHIFT, Memory.unsignedByte(mem, offset + Offset.DELAY), Memory.unsignedByte(mem, offset + Offset.VIBRATO),
					Memory.unsignedByte(mem, offset + Offset.FREQUENCY_SHIFT), Memory.unsignedByte(mem, offset + Offset.UNUSED));
		}

		/** The struct's length in bytes: the parameters, the table and the envelope. */
		public int length() {
			return PARAMETERS_LENGTH + noteTable.length + envelope.size() * ENVELOPE_ENTRY_LENGTH;
		}

		/** The struct as bytes. */
		public byte[] toBytes() {
			byte[] bytes = new byte[length()];
			write(bytes, 0);
			return bytes;
		}

		/** Writes the struct at {@code mem[offset...]}; returns the offset after it. */
		int write(byte[] mem, int offset) {
			int tlen = PARAMETERS_LENGTH + noteTable.length - 1;
			int envelopeStart = tlen + 1;
			mem[offset + Offset.TABLE_END] = (byte) tlen;
			mem[offset + Offset.TABLE_LOOP] = (byte) (PARAMETERS_LENGTH + tableLoop);
			mem[offset + Offset.ENVELOPE_END] = (byte) (envelopeStart + (envelope.size() - 1) * ENVELOPE_ENTRY_LENGTH);
			mem[offset + Offset.ENVELOPE_LOOP] = (byte) (envelopeStart + envelopeLoop * ENVELOPE_ENTRY_LENGTH);
			mem[offset + Offset.TABLE_CONTROL] = (byte) ((tableType << TABLE_TYPE_SHIFT) | (tableMode << TABLE_MODE_SHIFT) | tableSpeed);
			mem[offset + Offset.AUDCTL] = (byte) audctl;
			mem[offset + Offset.VOLUME_SLIDE] = (byte) volumeSlide;
			mem[offset + Offset.VOLUME_MINIMUM] = (byte) (volumeMinimum << HIGH_NIBBLE_SHIFT);
			mem[offset + Offset.DELAY] = (byte) delay;
			mem[offset + Offset.VIBRATO] = (byte) vibrato;
			mem[offset + Offset.FREQUENCY_SHIFT] = (byte) frequencyShift;
			mem[offset + Offset.UNUSED] = (byte) unused;
			System.arraycopy(noteTable, 0, mem, offset + PARAMETERS_LENGTH, noteTable.length);
			int entry = offset + envelopeStart;
			for (Envelope e : envelope) {
				mem[entry + Envelope.Offset.VOLUME] = (byte) e.volume();
				mem[entry + Envelope.Offset.CONTROL] = (byte) e.control();
				mem[entry + Envelope.Offset.XY] = (byte) e.xy();
				entry += ENVELOPE_ENTRY_LENGTH;
			}
			return offset + length();
		}

		/** A copy of the table of notes. */
		public byte[] noteTable() {
			return noteTable.clone();
		}

		public int tableLoop() {
			return tableLoop;
		}

		public List<Envelope> envelope() {
			return Collections.unmodifiableList(envelope);
		}

		public int envelopeLoop() {
			return envelopeLoop;
		}

		public int tableSpeed() {
			return tableSpeed;
		}

		public int tableMode() {
			return tableMode;
		}

		public int tableType() {
			return tableType;
		}

		public int audctl() {
			return audctl;
		}

		public int volumeSlide() {
			return volumeSlide;
		}

		public int volumeMinimum() {
			return volumeMinimum;
		}

		public int delay() {
			return delay;
		}

		public int vibrato() {
			return vibrato;
		}

		public int frequencyShift() {
			return frequencyShift;
		}

		public int unused() {
			return unused;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Instrument i && Arrays.equals(noteTable, i.noteTable) && tableLoop == i.tableLoop && envelope.equals(i.envelope) && envelopeLoop == i.envelopeLoop
					&& tableSpeed == i.tableSpeed && tableMode == i.tableMode && tableType == i.tableType && audctl == i.audctl && volumeSlide == i.volumeSlide && volumeMinimum == i.volumeMinimum
					&& delay == i.delay && vibrato == i.vibrato && frequencyShift == i.frequencyShift && unused == i.unused;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(toBytes());
		}

		@Override
		public String toString() {
			return "instrument with " + noteTable.length + " table entries, " + envelope.size() + " envelope entries";
		}
	}
}
