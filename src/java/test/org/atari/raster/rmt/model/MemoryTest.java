package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link Memory}: the byte and word accesses the file formats share (plan 60). */
class MemoryTest {

	@Test
	void wordsAreStoredLowByteFirst() {
		byte[] mem = new byte[4];
		Memory.writeWord(mem, 1, 0xbeef);
		assertEquals((byte) 0xef, mem[1]);
		assertEquals((byte) 0xbe, mem[2]);
		assertEquals(0xbeef, Memory.word(mem, 1));
		assertEquals(0xef, Memory.unsignedByte(mem, 1));
		assertEquals((byte) 0xef, Memory.lowByte(0xbeef));
		assertEquals((byte) 0xbe, Memory.highByte(0xbeef));
	}

	@Test
	void aSplitWordJoinsItsHalvesFromTwoOffsets() {
		byte[] mem = { (byte) 0x34, 0, 0, (byte) 0x12 };
		assertEquals(0x1234, Memory.splitWord(mem, 0, 3));
	}

	@Test
	void theAddressSpaceIsTheAtarisMemory() {
		assertEquals(Atari.MEMORY_SIZE, Memory.SIZE);
		assertEquals(Memory.SIZE - 1, Memory.MAX_ADDRESS);
		assertEquals(Memory.WORD_LENGTH * Memory.BITS_PER_BYTE, 16, "a word addresses the 16-bit space");
	}
}
