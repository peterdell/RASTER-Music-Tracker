// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class ASAPNativeModuleWriter
{
	ASAPWriter writer;
	byte[] sourceModule;
	int sourceOffset;
	private int addressDiff;

	private int getByte(int offset)
	{
		return this.sourceModule[this.sourceOffset + offset] & 0xff;
	}

	final int getWord(int offset)
	{
		return ASAPInfo.getWord(this.sourceModule, this.sourceOffset + offset);
	}

	private void copy(int endOffset) throws ASAPConversionException
	{
		this.writer.writeBytes(this.sourceModule, this.sourceOffset + this.writer.getOutputLength(), this.sourceOffset + endOffset);
	}

	private void relocateBytes(int lowOffset, int highOffset, int count, int shift) throws ASAPConversionException
	{
		lowOffset += this.sourceOffset;
		highOffset += this.sourceOffset;
		for (int i = 0; i < count; i++) {
			int address = (this.sourceModule[lowOffset + i] & 0xff) + ((this.sourceModule[highOffset + i] & 0xff) << 8);
			if (address != 0 && address != 65535)
				address += this.addressDiff;
			this.writer.writeByte(address >> shift & 255);
		}
	}

	private void relocateLowHigh(int count) throws ASAPConversionException
	{
		int lowOffset = this.writer.getOutputLength();
		relocateBytes(lowOffset, lowOffset + count, count, 0);
		relocateBytes(lowOffset, lowOffset + count, count, 8);
	}

	private void relocateWords(int count) throws ASAPConversionException
	{
		while (--count >= 0) {
			int address = getWord(this.writer.getOutputLength());
			if (address != 0 && address != 65535)
				address += this.addressDiff;
			this.writer.writeWord(address);
		}
	}

	final void write(ASAPInfo info, ASAPModuleType type, int moduleLen) throws ASAPConversionException
	{
		int startAddr = getWord(2);
		this.addressDiff = info.getMusicAddress() < 0 ? 0 : info.getMusicAddress() - startAddr;
		if (getWord(4) + this.addressDiff > 65535)
			throw new ASAPConversionException("Address set too high");
		switch (type) {
		case CMC:
		case CM3:
		case CMR:
		case CMS:
			relocateWords(3);
			copy(26);
			relocateLowHigh(64);
			break;
		case DLT:
			relocateWords(3);
			break;
		case MPT:
		case MD1:
		case MD2:
			relocateWords(99);
			copy(454);
			relocateLowHigh(4);
			break;
		case RMT:
			this.writer.writeWord(65535);
			relocateWords(2);
			copy(14);
			int patternLowAddress = getWord(16);
			relocateWords((patternLowAddress - startAddr - 8) >> 1);
			relocateLowHigh(getWord(18) - patternLowAddress);
			int songOffset = 6 + getWord(20) - startAddr;
			copy(songOffset);
			int songEnd = 7 + getWord(4) - startAddr;
			while (songOffset + 3 < songEnd) {
				int nextSongOffset = Math.min(songOffset + getByte(9) - '0', songEnd);
				if (getByte(songOffset) == 254) {
					copy(songOffset + 2);
					relocateWords(1);
				}
				copy(nextSongOffset);
				songOffset = nextSongOffset;
			}
			copy(songEnd);
			if (moduleLen >= songEnd + 5)
				relocateWords(2);
			break;
		case TMC:
			relocateWords(3);
			copy(38);
			relocateLowHigh(64);
			relocateLowHigh(128);
			break;
		case TM2:
			relocateWords(3);
			copy(134);
			relocateBytes(134, 774, 128, 0);
			relocateLowHigh(256);
			relocateBytes(134, 774, 128, 8);
			break;
		default:
			throw new ASAPConversionException("Impossible conversion");
		}
		copy(moduleLen);
	}
}
