package org.atari.raster.rmt.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@code .rmt} file (plan 60): a binary load file whose first segment
 * holds the {@link RmtModule} at its load address, followed - unless the
 * module is "stripped" - by a second segment with the song's name and the
 * name of every instrument present, each terminated by a zero byte, the
 * instruments in number order. The second segment is RMT's own addition
 * to doc/rmt_format.md's version 1 (which the document does not describe
 * yet, plan 60 M4); it loads right after the module, which is where RMT
 * has always put it.
 */
public final class RmtModuleFile {

	/** Where RMT loads a module unless told otherwise - the spec's "default is $4000". */
	public static final int DEFAULT_LOAD_ADDRESS = 0x4000;

	private final RmtModule module;
	private final int loadAddress;
	private final String songName; // null when stripped
	private final List<String> instrumentNames; // null when stripped, else one per instrument present, in number order

	/**
	 * @param module the module
	 * @param loadAddress where the first segment loads the module
	 * @param songName the song's name, or {@code null} for a stripped file without the names segment
	 * @param instrumentNames the name of every instrument present, in number order; {@code null} for a stripped file
	 */
	public RmtModuleFile(RmtModule module, int loadAddress, String songName, List<String> instrumentNames) {
		if ((songName == null) != (instrumentNames == null)) {
			throw new IllegalArgumentException("The song name and the instrument names are both present or both absent.");
		}
		if (instrumentNames != null && instrumentNames.size() != module.instruments().size()) {
			throw new IllegalArgumentException("There are " + instrumentNames.size() + " instrument names for " + module.instruments().size() + " instruments.");
		}
		if (loadAddress < 0 || loadAddress + module.length() - 1 > Memory.MAX_ADDRESS) {
			throw new RmtModule.FormatException("The module of " + module.length() + " bytes does not fit at $" + Integer.toHexString(loadAddress) + ": its last byte would lie beyond $" + Integer.toHexString(Memory.MAX_ADDRESS) + ".");
		}
		this.module = module;
		this.loadAddress = loadAddress;
		this.songName = songName;
		this.instrumentNames = instrumentNames == null ? null : List.copyOf(instrumentNames);
	}

	/** A stripped file: the module only. */
	public static RmtModuleFile stripped(RmtModule module, int loadAddress) {
		return new RmtModuleFile(module, loadAddress, null, null);
	}

	/**
	 * Reads the file's bytes: the module from the first segment, the names
	 * from the second one if there is any; a name the segment does not hold
	 * (a short segment) is empty.
	 */
	public static RmtModuleFile read(byte[] data) {
		byte[] mem = new byte[Memory.SIZE];
		AtariIO.BinaryBlockResult moduleBlock = AtariIO.loadBinaryBlock(data, 0, mem);
		if (moduleBlock.length() <= 0) {
			throw new RmtModule.FormatException("The file does not start with a binary load segment.");
		}
		RmtModule module = RmtModule.parse(mem, moduleBlock.fromAddr(), moduleBlock.toAddr() + 1);

		AtariIO.BinaryBlockResult namesBlock = AtariIO.loadBinaryBlock(data, moduleBlock.inputBytesConsumed(), mem);
		if (namesBlock.length() < 1) {
			return stripped(module, moduleBlock.fromAddr());
		}
		int[] pos = { namesBlock.fromAddr() };
		int end = namesBlock.toAddr() + 1;
		String songName = readName(mem, pos, end, SongInfo.SONG_NAME_MAX_LEN);
		List<String> instrumentNames = new ArrayList<>();
		for (int i = 0; i < module.instruments().size(); i++) {
			instrumentNames.add(readName(mem, pos, end, Instrument.INSTRUMENT_NAME_MAX_LEN));
		}
		return new RmtModuleFile(module, moduleBlock.fromAddr(), songName, instrumentNames);
	}

	/** The zero-terminated name at {@code mem[pos[0]...]}, at most {@code maxLength} characters, bounded by {@code end}; advances past the terminator. */
	private static String readName(byte[] mem, int[] pos, int end, int maxLength) {
		StringBuilder name = new StringBuilder();
		while (pos[0] < end && name.length() < maxLength && mem[pos[0]] != 0) {
			name.append((char) (mem[pos[0]] & 0xff));
			pos[0]++;
		}
		pos[0]++; // the terminator (or one past the end)
		return name.toString();
	}

	/** The file's bytes: the module's segment with the {@code $ffff} marker, then the names segment unless stripped. */
	public byte[] write() {
		byte[] mem = new byte[Memory.SIZE];
		int firstByteAfterModule = module.write(mem, loadAddress);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(AtariIO.saveBinaryBlock(mem, loadAddress, firstByteAfterModule - 1, true));
		if (!isStripped()) {
			ByteArrayOutputStream names = new ByteArrayOutputStream();
			names.writeBytes(songName.getBytes(StandardCharsets.US_ASCII));
			names.write(0);
			for (String name : instrumentNames) {
				names.writeBytes(name.getBytes(StandardCharsets.US_ASCII));
				names.write(0);
			}
			byte[] namesBytes = names.toByteArray();
			System.arraycopy(namesBytes, 0, mem, firstByteAfterModule, namesBytes.length);
			out.writeBytes(AtariIO.saveBinaryBlock(mem, firstByteAfterModule, firstByteAfterModule + namesBytes.length - 1, false));
		}
		return out.toByteArray();
	}

	public RmtModule module() {
		return module;
	}

	public int loadAddress() {
		return loadAddress;
	}

	/** Whether the file has no names segment - what RMT calls a stripped module. */
	public boolean isStripped() {
		return songName == null;
	}

	/** The song's name, or {@code null} for a stripped file. */
	public String songName() {
		return songName;
	}

	/** The name of every instrument present, in number order, or {@code null} for a stripped file. */
	public List<String> instrumentNames() {
		return instrumentNames == null ? null : Collections.unmodifiableList(instrumentNames);
	}
}
