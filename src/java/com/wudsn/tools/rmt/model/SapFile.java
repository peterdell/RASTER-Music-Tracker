package com.wudsn.tools.rmt.model;

/**
 * Ported from CSAPFile (src/cpp/SAPFile.h/.cpp) - the already-tested
 * subset: field getters/setters, {@code clear}/{@code normalize}, and
 * {@code export}. {@code Init(const CSong&)} is deferred - untested in
 * C++ (only stubbed there for linking), it also needs {@link Song} methods
 * this project's deliberately minimal {@code Song} slice doesn't have
 * ({@code GetName}/{@code IsStereo}/{@code IsNTSC}/{@code GetInstrumentSpeed})
 * plus a real system-clock read for the export date.
 *
 * <p>C++'s {@code Export(std::ostream&)} becomes {@link #export()}
 * returning a {@code String} directly - simpler than threading a
 * {@code Writer}/{@code Appendable} through (and its checked
 * {@code IOException}) for what's fundamentally just building text.
 *
 * <p><b>A real bug found while porting, fixed in both languages</b>: the
 * {@code DEFSONG} line printed {@code songs} instead of {@code defaultSong}
 * - already characterized (not fixed) in an earlier session's
 * {@code SAPFileTests.cpp}; fixed here per the user's explicit decision
 * before this class was committed, and in {@code SAPFile.cpp}/
 * {@code SAPFileTests.cpp} in the same batch. No other production code
 * depended on the buggy value (verified by a repo-wide search).
 *
 * <p>C++'s {@code ThrowRuntimeException} (an empty/invalid {@code type} -
 * shows a blocking {@code MessageBox} then calls {@code exit(2)}, not a
 * catchable exception) becomes a thrown {@link IllegalStateException},
 * matching this project's established idiom for translating a fatal C++
 * precondition into a Java exception (e.g. {@link Tuning#initTuning}).
 */
public final class SapFile {

	public static final int MAXSUBSONGS = 128; // Maximum number of subsongs in exported SAP file

	private static final String EOL = "\r\n"; // SAP format is defined to use CR/LF

	private String author = "";
	private String name = "";
	private String date = "";
	private int songs;
	private int defaultSong;
	private boolean stereo;
	private boolean ntsc;
	private int fastplay;
	private String type = "";
	private int initAddress;
	private int playerAddress;

	public SapFile() {
		clear();
	}

	public void clear() {
		author = "";
		name = "";
		date = "";
		songs = 0;
		defaultSong = 0;
		stereo = false;
		ntsc = false;
		fastplay = 0;
		type = "";
		initAddress = 0;
		playerAddress = 0;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public int getSongs() {
		return songs;
	}

	public void setSongs(int songs) {
		this.songs = songs;
	}

	public int getDefaultSong() {
		return defaultSong;
	}

	public void setDefaultSong(int defaultSong) {
		this.defaultSong = defaultSong;
	}

	public boolean isStereo() {
		return stereo;
	}

	public void setStereo(boolean stereo) {
		this.stereo = stereo;
	}

	public boolean isNTSC() {
		return ntsc;
	}

	public void setNTSC(boolean ntsc) {
		this.ntsc = ntsc;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getInitAddress() {
		return initAddress;
	}

	public void setInitAddress(int initAddress) {
		this.initAddress = initAddress;
	}

	public int getPlayerAddress() {
		return playerAddress;
	}

	public void setPlayerAddress(int playerAddress) {
		this.playerAddress = playerAddress;
	}

	public void normalize() {
		author = normalize(author);
		name = normalize(name);
		date = normalize(date);
	}

	private static String normalize(String s) {
		int end = s.length();
		while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
			end--; // Cuts spaces after the name
		}
		return s.substring(0, end).replace('"', '\''); // Replaces quotation marks with an apostrophe
	}

	/**
	 * Format the SAP file header text.
	 *
	 * @throws IllegalStateException if no type is set, or the type isn't "B" or "R" - see class javadoc
	 */
	public String export() {
		normalize();
		StringBuilder sb = new StringBuilder();
		sb.append("SAP").append(EOL);
		sb.append("AUTHOR \"").append(author).append("\"").append(EOL);
		sb.append("NAME \"").append(name).append("\"").append(EOL);
		sb.append("DATE \"").append(date).append("\"").append(EOL);
		sb.append("TYPE ").append(type).append(EOL);

		if (songs > 0) {
			sb.append("SONGS ").append(songs).append(EOL);
		}

		if (defaultSong > 0) {
			sb.append("DEFSONG ").append(defaultSong).append(EOL);
		}

		if (stereo) {
			sb.append("STEREO").append(EOL);
		}

		if (ntsc) {
			sb.append("NTSC").append(EOL);
		}

		if (fastplay > 0) {
			sb.append("FASTPLAY ").append(fastplay).append(EOL);
		}

		if (type.isEmpty()) {
			throw new IllegalStateException("SAP file has no type set.");
		}
		if (type.equals("B")) {
			if (initAddress > 0) {
				sb.append("INIT ").append(formatMemoryAddress(initAddress)).append(EOL);
			}
			if (playerAddress > 0) {
				sb.append("PLAYER ").append(formatMemoryAddress(playerAddress)).append(EOL);
			}
		} else if (type.equals("R")) {
			// nothing extra for type R
		} else {
			throw new IllegalStateException("SAP file has type \"" + type + "\". Only type \"B\" and \"R\" are supported for export.");
		}

		// A double EOL is necessary for making the SAP-R export functional
		sb.append(EOL);
		return sb.toString();
	}

	private static String formatMemoryAddress(int address) {
		return String.format("%04X", address);
	}
}
