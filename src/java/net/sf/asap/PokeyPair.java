// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class PokeyPair
{
	PokeyPair()
	{
		int reg = 511;
		for (int i = 0; i < 511; i++) {
			reg = (((reg >> 5 ^ reg) & 1) << 8) + (reg >> 1);
			this.poly9Lookup[i] = (byte) reg;
		}
		reg = 131071;
		for (int i = 0; i < 16385; i++) {
			reg = (((reg >> 5 ^ reg) & 255) << 9) + (reg >> 8);
			this.poly17Lookup[i] = (byte) (reg >> 1);
		}
		for (int i = 0; i < 1024; i++) {
			double sincSum = 0;
			double leftSum = 0;
			double norm = 0;
			final double[] sinc = new double[31];
			for (int j = -32; j < 32; j++) {
				if (j == -16)
					leftSum = sincSum;
				else if (j == 15)
					norm = sincSum;
				double x = 3.141592653589793 / 1024 * ((j << 10) - i);
				double s = x == 0 ? 1 : Math.sin(x) / x;
				if (j >= -16 && j < 15)
					sinc[16 + j] = s;
				sincSum += s;
			}
			norm = 16384 / (norm + (1 - sincSum) * 0.5);
			this.sincLookup[i][0] = (short) Math.rint((leftSum + (1 - sincSum) * 0.5) * norm);
			for (int j = 1; j < 32; j++)
				this.sincLookup[i][j] = (short) Math.rint(sinc[j - 1] * norm);
		}
	}
	final byte[] poly9Lookup = new byte[511];
	final byte[] poly17Lookup = new byte[16385];
	private int extraPokeyMask;
	final Pokey basePokey = new Pokey();
	final Pokey extraPokey = new Pokey();
	int sampleRate;
	final short[][] sincLookup = new short[1024][32];

	static final int SAMPLE_FACTOR_SHIFT = 18;
	int sampleFactor;
	int sampleOffset;
	int readySamplesStart;
	int readySamplesEnd;

	private int getSampleFactor(int clock)
	{
		return ((this.sampleRate << 13) + (clock >> 6)) / (clock >> 5);
	}

	final void initialize(boolean ntsc, boolean stereo, int sampleRate)
	{
		this.extraPokeyMask = stereo ? 16 : 0;
		this.sampleRate = sampleRate;
		this.basePokey.initialize(sampleRate);
		this.extraPokey.initialize(sampleRate);
		this.sampleFactor = ntsc ? getSampleFactor(1789772) : getSampleFactor(1773447);
		this.sampleOffset = 0;
		this.readySamplesStart = 0;
		this.readySamplesEnd = 0;
	}

	final void initialize(boolean ntsc, boolean stereo)
	{
		initialize(ntsc, stereo, 44100);
	}

	final int poke(int addr, int data, int cycle)
	{
		Pokey pokey = (addr & this.extraPokeyMask) != 0 ? this.extraPokey : this.basePokey;
		return pokey.poke(this, addr, data, cycle);
	}

	final int peek(int addr, int cycle)
	{
		Pokey pokey = (addr & this.extraPokeyMask) != 0 ? this.extraPokey : this.basePokey;
		switch (addr & 15) {
		case 10:
			if (pokey.init)
				return 255;
			int i = cycle + pokey.polyIndex;
			if ((pokey.audctl & 128) != 0)
				return this.poly9Lookup[i % 511] & 0xff;
			i %= 131071;
			int j = i >> 3;
			i &= 7;
			return (((this.poly17Lookup[j] & 0xff) >> i) + ((this.poly17Lookup[j + 1] & 0xff) << (8 - i))) & 255;
		case 14:
			return pokey.irqst;
		default:
			return 255;
		}
	}

	final void startFrame()
	{
		this.basePokey.startFrame();
		if (this.extraPokeyMask != 0)
			this.extraPokey.startFrame();
	}

	final int endFrame(int cycle)
	{
		this.basePokey.endFrame(this, cycle);
		if (this.extraPokeyMask != 0)
			this.extraPokey.endFrame(this, cycle);
		this.sampleOffset += cycle * this.sampleFactor;
		this.readySamplesStart = 0;
		this.readySamplesEnd = this.sampleOffset >> 18;
		this.sampleOffset &= 262143;
		return this.readySamplesEnd;
	}

	/**
	 * Fills buffer with samples from <code>DeltaBuffer</code>.
	 */
	final int generate(byte[] buffer, int bufferOffset, int blocks, ASAPSampleFormat format)
	{
		int i = this.readySamplesStart;
		int samplesEnd = this.readySamplesEnd;
		if (blocks < samplesEnd - i)
			samplesEnd = i + blocks;
		else
			blocks = samplesEnd - i;
		if (blocks > 0) {
			for (; i < samplesEnd; i++) {
				bufferOffset = this.basePokey.storeSample(buffer, bufferOffset, i, format);
				if (this.extraPokeyMask != 0)
					bufferOffset = this.extraPokey.storeSample(buffer, bufferOffset, i, format);
			}
			if (i == this.readySamplesEnd) {
				this.basePokey.accumulateTrailing(i);
				this.extraPokey.accumulateTrailing(i);
			}
			this.readySamplesStart = i;
		}
		return blocks;
	}

	final boolean isSilent()
	{
		return this.basePokey.isSilent() && this.extraPokey.isSilent();
	}
}
