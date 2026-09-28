// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class FlashPack
{
	FlashPack()
	{
		for (int _i0 = 0; _i0 < 64; _i0++) {
			this.items[_i0] = new FlashPackItem();
		}
	}
	private final short[] memory = new short[65536];

	private static final int MIN_LOAD_ADDRESS = 8192;

	private int findHole() throws ASAPConversionException
	{
		int end = 48159;
		for (;;) {
			while (this.memory[end] >= 0)
				if (--end < 9216)
					throw new ASAPConversionException("Too much data to compress");
			int start = end;
			while (this.memory[--start] < 0)
				if (end - start >= 1023)
					return end;
			end = start;
		}
	}
	private final byte[] compressed = new byte[65536];
	private int compressedLength;
	private final FlashPackItem[] items = new FlashPackItem[64];
	private int itemsCount;

	private int getInnerFlags(int index)
	{
		int flags = 1;
		do {
			flags <<= 1;
			if (index < this.itemsCount) {
				if (this.items[index++].type != FlashPackItemType.LITERAL)
					flags++;
			}
		}
		while (flags < 256);
		return flags & 255;
	}

	private void putItems()
	{
		int outerFlags = 0;
		for (int i = 0; i < this.itemsCount; i += 8) {
			if (getInnerFlags(i) != 0)
				outerFlags |= 128 >> (i >> 3);
		}
		this.compressed[this.compressedLength++] = (byte) outerFlags;
		for (int i = 0; i < this.itemsCount; i++) {
			if ((i & 7) == 0) {
				int flags = getInnerFlags(i);
				if (flags != 0)
					this.compressed[this.compressedLength++] = (byte) flags;
			}
			this.compressedLength += this.items[i].writeValueTo(this.compressed, this.compressedLength);
		}
	}

	private void putItem(FlashPackItemType type, int value)
	{
		if (this.itemsCount >= 64) {
			putItems();
			this.itemsCount = 0;
		}
		this.items[this.itemsCount].type = type;
		this.items[this.itemsCount].value = value;
		this.itemsCount++;
	}

	private boolean isLiteralPreferred()
	{
		return (this.itemsCount & 7) == 7 && getInnerFlags(this.itemsCount - 7) == 0;
	}

	private void compressMemoryArea(int startAddress, int endAddress)
	{
		int lastDistance = -1;
		for (int address = startAddress; address <= endAddress;) {
			while (this.memory[address] < 0)
				if (++address > endAddress)
					return;
			putItem(FlashPackItemType.SET_ADDRESS, address);
			while (address <= endAddress && this.memory[address] >= 0) {
				int bestMatch = 0;
				int bestDistance = -1;
				for (int backAddress = address - 1; backAddress >= startAddress && address - backAddress < 128; backAddress--) {
					int match;
					for (match = 0; address + match <= endAddress; match++) {
						int data = this.memory[address + match];
						if (data < 0 || data != this.memory[backAddress + match])
							break;
					}
					if (bestMatch < match) {
						bestMatch = match;
						bestDistance = address - backAddress;
					}
					else if (bestMatch == match && address - backAddress == lastDistance)
						bestDistance = lastDistance;
				}
				switch (bestMatch) {
				case 0:
				case 1:
					putItem(FlashPackItemType.LITERAL, this.memory[address++]);
					continue;
				case 2:
					putItem(FlashPackItemType.COPY_TWO_BYTES, bestDistance);
					break;
				case 3:
					putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
					break;
				case 4:
					if (bestDistance == lastDistance)
						putItem(FlashPackItemType.COPY_MANY_BYTES, 4);
					else if (isLiteralPreferred()) {
						putItem(FlashPackItemType.LITERAL, this.memory[address]);
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
					}
					else {
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
						putItem(FlashPackItemType.LITERAL, this.memory[address + 3]);
					}
					break;
				case 5:
					if (bestDistance == lastDistance)
						putItem(FlashPackItemType.COPY_MANY_BYTES, 5);
					else {
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
						putItem(FlashPackItemType.COPY_TWO_BYTES, bestDistance);
					}
					break;
				case 6:
					if (bestDistance == lastDistance)
						putItem(FlashPackItemType.COPY_MANY_BYTES, 6);
					else {
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
					}
					break;
				default:
					int length = bestMatch;
					if (bestDistance != lastDistance) {
						if (isLiteralPreferred() && length % 255 == 4) {
							putItem(FlashPackItemType.LITERAL, this.memory[address]);
							length--;
						}
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
						length -= 3;
					}
					else if (isLiteralPreferred() && length % 255 == 1) {
						putItem(FlashPackItemType.LITERAL, this.memory[address]);
						length--;
					}
					for (; length > 255; length -= 255)
						putItem(FlashPackItemType.COPY_MANY_BYTES, 255);
					switch (length) {
					case 0:
						break;
					case 1:
						putItem(FlashPackItemType.LITERAL, this.memory[address + bestMatch - 1]);
						break;
					case 2:
						putItem(FlashPackItemType.COPY_TWO_BYTES, bestDistance);
						break;
					case 3:
						putItem(FlashPackItemType.COPY_THREE_BYTES, bestDistance);
						break;
					default:
						putItem(FlashPackItemType.COPY_MANY_BYTES, length);
						break;
					}
					break;
				}
				address += bestMatch;
				lastDistance = bestDistance;
			}
		}
	}

	private void putPoke(int address, int value)
	{
		putItem(FlashPackItemType.SET_ADDRESS, address);
		putItem(FlashPackItemType.LITERAL, value);
	}

	final void compress(ASAPWriter w) throws ASAPConversionException
	{
		for (int i = 0; i < 65536; i++)
			this.memory[i] = -1;
		byte[] input = w.getOutput();
		int inputLen = w.getOutputLength();
		for (int i = 0; i + 5 <= inputLen;) {
			int startAddress = (input[i] & 0xff) + ((input[i + 1] & 0xff) << 8);
			if (startAddress == 65535) {
				i += 2;
				startAddress = (input[i] & 0xff) + ((input[i + 1] & 0xff) << 8);
			}
			int endAddress = (input[i + 2] & 0xff) + ((input[i + 3] & 0xff) << 8);
			if (startAddress > endAddress)
				throw new ASAPConversionException("Start address greater than end address");
			i += 4;
			if (i + endAddress - startAddress >= inputLen)
				throw new ASAPConversionException("Truncated block");
			while (startAddress <= endAddress)
				this.memory[startAddress++] = (short) (input[i++] & 0xff);
		}
		if (this.memory[736] < 0 || this.memory[737] < 0)
			throw new ASAPConversionException("Missing run address");
		if (this.memory[252] >= 0 || this.memory[253] >= 0 || this.memory[254] >= 0 || this.memory[255] >= 0)
			throw new ASAPConversionException("Conflict with decompressor variables");
		int runAddress = this.memory[736] + (this.memory[737] << 8);
		this.memory[736] = this.memory[737] = -1;
		int depackerEndAddress = findHole();
		this.compressedLength = 0;
		this.itemsCount = 0;
		putPoke(54286, 0);
		putPoke(53774, 0);
		putPoke(54272, 0);
		putPoke(54017, 254);
		putPoke(580, 255);
		compressMemoryArea(depackerEndAddress, 65535);
		compressMemoryArea(0, depackerEndAddress);
		putItem(FlashPackItemType.END_OF_STREAM, 0);
		putItems();
		int depackerStartAddress = depackerEndAddress - 87;
		int compressedStartAddress = depackerStartAddress - this.compressedLength;
		if (compressedStartAddress < 8192)
			throw new ASAPConversionException("Too much compressed data");
		w.clearOutput();
		w.writeWord(65535);
		w.writeWord(54017);
		w.writeWord(54017);
		w.writeByte(255);
		w.writeWord(compressedStartAddress);
		w.writeWord(depackerEndAddress);
		w.writeBytes(this.compressed, 0, this.compressedLength);
		w.writeByte(173);
		w.writeWord(compressedStartAddress);
		w.writeByte(238);
		w.writeWord(depackerStartAddress + 1);
		w.writeByte(208);
		w.writeByte(3);
		w.writeByte(238);
		w.writeWord(depackerStartAddress + 2);
		w.writeByte(96);
		w.writeByte(76);
		w.writeWord(runAddress);
		w.writeByte(133);
		w.writeByte(254);
		w.writeByte(138);
		w.writeByte(42);
		w.writeByte(170);
		w.writeByte(240);
		w.writeByte(246);
		w.writeByte(177);
		w.writeByte(254);
		w.writeByte(153);
		w.writeByte(128);
		w.writeByte(128);
		w.writeByte(200);
		w.writeByte(208);
		w.writeByte(9);
		w.writeByte(152);
		w.writeByte(56);
		w.writeByte(101);
		w.writeByte(255);
		w.writeByte(133);
		w.writeByte(255);
		w.writeByte(141);
		w.writeWord(depackerStartAddress + 26);
		w.writeByte(202);
		w.writeByte(208);
		w.writeByte(236);
		w.writeByte(6);
		w.writeByte(253);
		w.writeByte(208);
		w.writeByte(21);
		w.writeByte(6);
		w.writeByte(252);
		w.writeByte(208);
		w.writeByte(7);
		w.writeByte(56);
		w.writeByte(32);
		w.writeWord(depackerStartAddress);
		w.writeByte(42);
		w.writeByte(133);
		w.writeByte(252);
		w.writeByte(169);
		w.writeByte(1);
		w.writeByte(144);
		w.writeByte(4);
		w.writeByte(32);
		w.writeWord(depackerStartAddress);
		w.writeByte(42);
		w.writeByte(133);
		w.writeByte(253);
		w.writeByte(32);
		w.writeWord(depackerStartAddress);
		w.writeByte(162);
		w.writeByte(1);
		w.writeByte(144);
		w.writeByte(206);
		w.writeByte(74);
		w.writeByte(208);
		w.writeByte(194);
		w.writeByte(32);
		w.writeWord(depackerStartAddress);
		w.writeByte(176);
		w.writeByte(193);
		w.writeByte(168);
		w.writeByte(32);
		w.writeWord(depackerStartAddress);
		w.writeByte(144);
		w.writeByte(202);
		w.writeWord(736);
		w.writeWord(737);
		w.writeWord(depackerStartAddress + 50);
	}
}
