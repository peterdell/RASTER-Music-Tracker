package org.atari.raster.rmt.model;

/**
 * The 6502's memory as the file formats see it (plan 60): a 64K address
 * space of bytes, with address words stored low byte first. The byte and
 * word accesses the formats need live here, once - {@link RmtModule} reads
 * and writes its structs through them.
 */
public final class Memory {

	/** The bits of a byte. */
	public static final int BITS_PER_BYTE = 8;

	/** The highest value a byte holds. */
	public static final int MAX_BYTE = 0xff;

	/** The bytes of an address word, low byte first. */
	public static final int WORD_LENGTH = 2;

	/** The last address of the 16-bit address space. */
	public static final int MAX_ADDRESS = 0xffff;

	/** The size of the address space: {@link Atari#MEMORY_SIZE}. */
	public static final int SIZE = MAX_ADDRESS + 1;

	private Memory() {
	}

	/** The byte at {@code mem[offset]}, 0-255. */
	public static int unsignedByte(byte[] mem, int offset) {
		return mem[offset] & MAX_BYTE;
	}

	/** The address word at {@code mem[offset]}, low byte first. */
	public static int word(byte[] mem, int offset) {
		return splitWord(mem, offset, offset + 1);
	}

	/** An address word whose low and high bytes lie at two offsets - RMT's track addresses tables keep them apart. */
	public static int splitWord(byte[] mem, int lowOffset, int highOffset) {
		return unsignedByte(mem, lowOffset) | (unsignedByte(mem, highOffset) << BITS_PER_BYTE);
	}

	/** The low byte of an address word. */
	public static byte lowByte(int word) {
		return (byte) word;
	}

	/** The high byte of an address word. */
	public static byte highByte(int word) {
		return (byte) (word >> BITS_PER_BYTE);
	}

	/** Writes the address word at {@code mem[offset]}, low byte first. */
	public static void writeWord(byte[] mem, int offset, int word) {
		mem[offset] = lowByte(word);
		mem[offset + 1] = highByte(word);
	}
}
