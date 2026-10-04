// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class ASAPModuleTransfer
{
	byte[] source;
	int sourceOffset;
	byte[] output;
	int outputOffset;
	private int addressDiff;

	private void writeByte(int value)
	{
		this.output[this.outputOffset++] = (byte) value;
	}

	private void writeWord(int value)
	{
		writeByte(value & 255);
		writeByte(value >> 8);
	}

	final void writeBytes(byte[] source, int offset, int count)
	{
		System.arraycopy(source, offset, this.output, this.outputOffset, count);
		this.outputOffset += count;
	}

	private int readWord()
	{
		int value = ASAPInfo.getWord(this.source, this.sourceOffset);
		this.sourceOffset += 2;
		return value;
	}

	private void copy(int count)
	{
		writeBytes(this.source, this.sourceOffset, count);
		this.sourceOffset += count;
	}

	private void relocateBytes(int lowOffset, int highOffset, int count, int shift)
	{
		lowOffset += this.sourceOffset;
		highOffset += this.sourceOffset;
		for (int i = 0; i < count; i++) {
			int address = (this.source[lowOffset + i] & 0xff) + ((this.source[highOffset + i] & 0xff) << 8);
			if (address != 0 && address != 65535)
				address += this.addressDiff;
			writeByte(address >> shift & 255);
		}
		this.sourceOffset += count;
	}

	private void relocateLowHigh(int count)
	{
		relocateBytes(0, count, count, 0);
		relocateBytes(-count, 0, count, 8);
	}

	private void relocateWords(int count)
	{
		while (--count >= 0) {
			int address = readWord();
			if (address != 0 && address != 65535)
				address += this.addressDiff;
			writeWord(address);
		}
	}

	final void transferModule(ASAPInfo info, int sourceEnd, boolean header, boolean rmtNames)
	{
		this.sourceOffset += 2;
		int startAddr = readWord();
		int endAddr = readWord();
		this.addressDiff = info.music < 0 ? 0 : info.music - startAddr;
		if (header) {
			writeWord(65535);
			writeWord(startAddr + this.addressDiff);
			writeWord(endAddr + this.addressDiff);
		}
		switch (info.originalType) {
		case CMC:
		case CM3:
		case CMR:
		case CMS:
			copy(20);
			relocateLowHigh(64);
			break;
		case DLT:
			break;
		case MPT:
		case MD1:
		case MD2:
			final int[] trackAddr = new int[5];
			trackAddr[4] = 0;
			for (int i = 0; i < 96; i++) {
				int address = readWord();
				if (address != 0) {
					address += this.addressDiff;
					if (trackAddr[4] == 0)
						trackAddr[4] = address;
				}
				writeWord(address);
			}
			if (trackAddr[4] == 0)
				trackAddr[4] = endAddr + 1 + this.addressDiff;
			copy(256);
			for (int ch = 0; ch < 4; ch++) {
				int address = (this.source[this.sourceOffset + ch] & 0xff) + ((this.source[this.sourceOffset + 4 + ch] & 0xff) << 8) + this.addressDiff;
				trackAddr[ch] = address;
				writeByte(address & 255);
			}
			for (int ch = 0; ch < 4; ch++)
				writeByte(trackAddr[ch] >> 8);
			this.sourceOffset += 8;
			if (info.mptSongBugExtra != 0) {
				int track0Len = trackAddr[1] - trackAddr[0];
				copy(2 + track0Len);
				for (int ch = 1; ch < 4; ch++) {
					int trackLen = trackAddr[ch + 1] - trackAddr[ch];
					if (trackLen <= track0Len) {
						copy(trackLen);
						this.sourceOffset += track0Len - trackLen;
					}
					else {
						copy(track0Len);
						for (; trackLen > track0Len; trackLen--)
							writeByte(255);
					}
				}
			}
			break;
		case RMT:
			int startOffset = this.sourceOffset;
			int channels = (this.source[startOffset + 3] & 0xff) - '0';
			copy(8);
			int patternLowAddr = ASAPInfo.getWord(this.source, startOffset + 10);
			int patternHighAddr = ASAPInfo.getWord(this.source, startOffset + 12);
			int songAddr = ASAPInfo.getWord(this.source, startOffset + 14);
			relocateWords((patternLowAddr - startAddr - 8) >> 1);
			relocateLowHigh(patternHighAddr - patternLowAddr);
			copy(songAddr - startAddr + startOffset - this.sourceOffset);
			int songEnd = endAddr + 1 - startAddr + startOffset;
			while (this.sourceOffset < songEnd) {
				int songLineLen = Math.min(channels, songEnd - this.sourceOffset);
				if (songLineLen >= 4 && this.source[this.sourceOffset] == (byte) 254) {
					copy(2);
					relocateWords(1);
					songLineLen -= 4;
				}
				copy(songLineLen);
			}
			if (!rmtNames)
				return;
			if (sourceEnd >= songEnd + 5)
				relocateWords(2);
			break;
		case TMC:
			copy(32);
			relocateLowHigh(64);
			relocateLowHigh(128);
			break;
		case TM2:
			copy(128);
			relocateBytes(0, 640, 128, 0);
			relocateLowHigh(256);
			relocateBytes(-640, 0, 128, 8);
			break;
		default:
			throw new AssertionError();
		}
		copy(sourceEnd - this.sourceOffset);
	}
}
