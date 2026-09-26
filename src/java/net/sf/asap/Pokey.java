// Generated automatically with "fut". Do not edit,
// EXCEPT for widening "skctl" from private to package-private below (an RMT
// addition - see ASAP.java's header comment and its getPokeyRegisterShadow()
// method, which needs to read this field from the same package).
package net.sf.asap;
import java.util.Arrays;

class Pokey
{
	Pokey()
	{
		for (int _i0 = 0; _i0 < 4; _i0++) {
			this.channels[_i0] = new PokeyChannel();
		}
	}
	final PokeyChannel[] channels = new PokeyChannel[4];
	int audctl;
	int skctl;
	int irqst;
	boolean init;
	private int divCycles;
	private int reloadCycles1;
	private int reloadCycles3;
	int polyIndex;

	static final int NEVER_CYCLE = 8388608;
	private int deltaBufferLength;
	private int[] deltaBuffer;
	private int sumDACInputs;
	private int sumDACOutputs;

	private static final short[] COMPRESSED_SUMS = { 0, 35, 73, 111, 149, 189, 228, 266, 304, 342, 379, 415, 450, 484, 516, 546,
		575, 602, 628, 652, 674, 695, 715, 733, 750, 766, 782, 796, 809, 822, 834, 846,
		856, 867, 876, 886, 894, 903, 911, 918, 926, 933, 939, 946, 952, 958, 963, 969,
		974, 979, 984, 988, 993, 997, 1001, 1005, 1009, 1013, 1016, 1019, 1023 };

	static final int INTERPOLATION_SHIFT = 10;

	static final int UNIT_DELTA_LENGTH = 32;

	static final int DELTA_RESOLUTION = 14;
	private int iirRate;
	private int iirAcc;
	private int trailing;

	final void startFrame()
	{
		System.arraycopy(this.deltaBuffer, this.trailing, this.deltaBuffer, 0, this.deltaBufferLength - this.trailing);
		Arrays.fill(this.deltaBuffer, this.deltaBufferLength - this.trailing, this.deltaBufferLength - this.trailing + this.trailing, 0);
	}

	final void initialize(int sampleRate)
	{
		long sr = sampleRate;
		this.deltaBufferLength = (int) (sr * 312 * 114 / 1773447 + 32 + 2);
		this.deltaBuffer = new int[this.deltaBufferLength];
		this.trailing = this.deltaBufferLength;
		for (PokeyChannel c : this.channels)
			c.initialize();
		this.audctl = 0;
		this.skctl = 3;
		this.irqst = 255;
		this.init = false;
		this.divCycles = 28;
		this.reloadCycles1 = 28;
		this.reloadCycles3 = 28;
		this.polyIndex = 60948015;
		this.iirAcc = 0;
		this.iirRate = 264600 / sampleRate;
		this.sumDACInputs = 0;
		this.sumDACOutputs = 0;
		startFrame();
	}

	final void initialize()
	{
		initialize(44100);
	}

	private void addDelta(PokeyPair pokeys, int cycle, int delta)
	{
		if (delta == 0)
			return;
		int i = cycle * pokeys.sampleFactor + pokeys.sampleOffset;
		int fraction = i >> 8 & 1023;
		i >>= 18;
		delta >>= 14;
		for (int j = 0; j < 32; j++)
			this.deltaBuffer[i + j] += delta * pokeys.sincLookup[fraction][j];
	}

	final void addPokeyDelta(PokeyPair pokeys, int cycle, int delta, boolean muted)
	{
		this.sumDACInputs += delta;
		if (muted)
			return;
		int newOutput = COMPRESSED_SUMS[this.sumDACInputs] << 16;
		addDelta(pokeys, cycle, newOutput - this.sumDACOutputs);
		this.sumDACOutputs = newOutput;
	}

	final void addExternalDelta(PokeyPair pokeys, int cycle, int delta)
	{
		if ((this.channels[0].mute & 8) == 0)
			addDelta(pokeys, cycle, delta);
	}

	/**
	 * Fills <code>DeltaBuffer</code> up to <code>cycleLimit</code> basing on current Audf/Audc/Audctl values.
	 */
	final void generateUntilCycle(PokeyPair pokeys, int cycleLimit)
	{
		for (;;) {
			int cycle = cycleLimit;
			for (PokeyChannel c : this.channels) {
				int tickCycle = c.tickCycle;
				if (cycle > tickCycle)
					cycle = tickCycle;
			}
			if (cycle == cycleLimit)
				break;
			if (cycle == this.channels[2].tickCycle) {
				if ((this.audctl & 4) != 0)
					this.channels[0].slopeDown(this, pokeys, cycle);
				this.channels[2].doTick(this, pokeys, cycle, 2);
			}
			if (cycle == this.channels[3].tickCycle) {
				if ((this.audctl & 8) != 0)
					this.channels[2].tickCycle = cycle + this.reloadCycles3;
				if ((this.audctl & 2) != 0)
					this.channels[1].slopeDown(this, pokeys, cycle);
				this.channels[3].doTick(this, pokeys, cycle, 3);
			}
			if (cycle == this.channels[0].tickCycle) {
				if ((this.skctl & 136) == 8)
					this.channels[1].tickCycle = cycle + this.channels[1].periodCycles;
				this.channels[0].doTick(this, pokeys, cycle, 0);
			}
			if (cycle == this.channels[1].tickCycle) {
				if ((this.audctl & 16) != 0)
					this.channels[0].tickCycle = cycle + this.reloadCycles1;
				else if ((this.skctl & 8) != 0)
					this.channels[0].tickCycle = cycle + this.channels[0].periodCycles;
				this.channels[1].doTick(this, pokeys, cycle, 1);
			}
		}
	}

	final void endFrame(PokeyPair pokeys, int cycle)
	{
		generateUntilCycle(pokeys, cycle);
		this.polyIndex += cycle;
		int m = (this.audctl & 128) != 0 ? 237615 : 60948015;
		if (this.polyIndex >= 2 * m)
			this.polyIndex -= m;
		for (PokeyChannel c : this.channels) {
			int tickCycle = c.tickCycle;
			if (tickCycle != 8388608)
				c.tickCycle = tickCycle - cycle;
		}
	}

	final boolean isSilent()
	{
		for (PokeyChannel c : this.channels)
			if ((c.audc & 15) != 0)
				return false;
		return true;
	}

	final void mute(int mask)
	{
		for (int i = 0; i < 4; i++)
			this.channels[i].setMute((mask & 1 << i) != 0, 2, 0);
	}

	final void endSongInit()
	{
		for (PokeyChannel channel : this.channels)
			channel.setMute(false, 8, 0);
	}

	private void initMute(int cycle)
	{
		boolean init = this.init;
		int audctl = this.audctl;
		this.channels[0].setMute(init && (audctl & 64) == 0, 1, cycle);
		this.channels[1].setMute(init && (audctl & 80) != 80, 1, cycle);
		this.channels[2].setMute(init && (audctl & 32) == 0, 1, cycle);
		this.channels[3].setMute(init && (audctl & 40) != 40, 1, cycle);
	}

	final int poke(PokeyPair pokeys, int addr, int data, int cycle)
	{
		int nextEventCycle = 8388608;
		switch (addr & 15) {
		case 0:
			if (data == this.channels[0].audf)
				break;
			generateUntilCycle(pokeys, cycle);
			this.channels[0].audf = data;
			switch (this.audctl & 80) {
			case 0:
				this.channels[0].periodCycles = this.divCycles * (data + 1);
				break;
			case 16:
				this.channels[1].periodCycles = this.divCycles * (data + (this.channels[1].audf << 8) + 1);
				this.reloadCycles1 = this.divCycles * (data + 1);
				break;
			case 64:
				this.channels[0].periodCycles = data + 4;
				break;
			case 80:
				this.channels[1].periodCycles = data + (this.channels[1].audf << 8) + 7;
				this.reloadCycles1 = data + 4;
				break;
			default:
				throw new AssertionError();
			}
			break;
		case 1:
			this.channels[0].setAudc(this, pokeys, data, cycle);
			break;
		case 2:
			if (data == this.channels[1].audf)
				break;
			generateUntilCycle(pokeys, cycle);
			this.channels[1].audf = data;
			switch (this.audctl & 80) {
			case 0:
			case 64:
				this.channels[1].periodCycles = this.divCycles * (data + 1);
				break;
			case 16:
				this.channels[1].periodCycles = this.divCycles * (this.channels[0].audf + (data << 8) + 1);
				break;
			case 80:
				this.channels[1].periodCycles = this.channels[0].audf + (data << 8) + 7;
				break;
			default:
				throw new AssertionError();
			}
			break;
		case 3:
			this.channels[1].setAudc(this, pokeys, data, cycle);
			break;
		case 4:
			if (data == this.channels[2].audf)
				break;
			generateUntilCycle(pokeys, cycle);
			this.channels[2].audf = data;
			switch (this.audctl & 40) {
			case 0:
				this.channels[2].periodCycles = this.divCycles * (data + 1);
				break;
			case 8:
				this.channels[3].periodCycles = this.divCycles * (data + (this.channels[3].audf << 8) + 1);
				this.reloadCycles3 = this.divCycles * (data + 1);
				break;
			case 32:
				this.channels[2].periodCycles = data + 4;
				break;
			case 40:
				this.channels[3].periodCycles = data + (this.channels[3].audf << 8) + 7;
				this.reloadCycles3 = data + 4;
				break;
			default:
				throw new AssertionError();
			}
			break;
		case 5:
			this.channels[2].setAudc(this, pokeys, data, cycle);
			break;
		case 6:
			if (data == this.channels[3].audf)
				break;
			generateUntilCycle(pokeys, cycle);
			this.channels[3].audf = data;
			switch (this.audctl & 40) {
			case 0:
			case 32:
				this.channels[3].periodCycles = this.divCycles * (data + 1);
				break;
			case 8:
				this.channels[3].periodCycles = this.divCycles * (this.channels[2].audf + (data << 8) + 1);
				break;
			case 40:
				this.channels[3].periodCycles = this.channels[2].audf + (data << 8) + 7;
				break;
			default:
				throw new AssertionError();
			}
			break;
		case 7:
			this.channels[3].setAudc(this, pokeys, data, cycle);
			break;
		case 8:
			if (data == this.audctl)
				break;
			generateUntilCycle(pokeys, cycle);
			this.audctl = data;
			this.divCycles = (data & 1) != 0 ? 114 : 28;
			switch (data & 80) {
			case 0:
				this.channels[0].periodCycles = this.divCycles * (this.channels[0].audf + 1);
				this.channels[1].periodCycles = this.divCycles * (this.channels[1].audf + 1);
				break;
			case 16:
				this.channels[0].periodCycles = this.divCycles << 8;
				this.channels[1].periodCycles = this.divCycles * (this.channels[0].audf + (this.channels[1].audf << 8) + 1);
				this.reloadCycles1 = this.divCycles * (this.channels[0].audf + 1);
				break;
			case 64:
				this.channels[0].periodCycles = this.channels[0].audf + 4;
				this.channels[1].periodCycles = this.divCycles * (this.channels[1].audf + 1);
				break;
			case 80:
				this.channels[0].periodCycles = 256;
				this.channels[1].periodCycles = this.channels[0].audf + (this.channels[1].audf << 8) + 7;
				this.reloadCycles1 = this.channels[0].audf + 4;
				break;
			default:
				throw new AssertionError();
			}
			switch (data & 40) {
			case 0:
				this.channels[2].periodCycles = this.divCycles * (this.channels[2].audf + 1);
				this.channels[3].periodCycles = this.divCycles * (this.channels[3].audf + 1);
				break;
			case 8:
				this.channels[2].periodCycles = this.divCycles << 8;
				this.channels[3].periodCycles = this.divCycles * (this.channels[2].audf + (this.channels[3].audf << 8) + 1);
				this.reloadCycles3 = this.divCycles * (this.channels[2].audf + 1);
				break;
			case 32:
				this.channels[2].periodCycles = this.channels[2].audf + 4;
				this.channels[3].periodCycles = this.divCycles * (this.channels[3].audf + 1);
				break;
			case 40:
				this.channels[2].periodCycles = 256;
				this.channels[3].periodCycles = this.channels[2].audf + (this.channels[3].audf << 8) + 7;
				this.reloadCycles3 = this.channels[2].audf + 4;
				break;
			default:
				throw new AssertionError();
			}
			initMute(cycle);
			break;
		case 9:
			generateUntilCycle(pokeys, cycle);
			this.channels[0].doStimer(this, pokeys, cycle, (this.audctl & 16) == 0 ? this.channels[0].periodCycles : this.reloadCycles1);
			this.channels[1].doStimer(this, pokeys, cycle, this.channels[1].periodCycles);
			this.channels[2].doStimer(this, pokeys, cycle, (this.audctl & 8) == 0 ? this.channels[2].periodCycles : this.reloadCycles3);
			this.channels[3].doStimer(this, pokeys, cycle, this.channels[3].periodCycles);
			break;
		case 14:
			this.irqst |= data ^ 255;
			for (int i = 3;; i >>= 1) {
				if ((data & this.irqst & (i + 1)) != 0) {
					if (this.channels[i].timerCycle == 8388608) {
						int t = this.channels[i].tickCycle;
						while (t < cycle)
							t += this.channels[i].periodCycles;
						this.channels[i].timerCycle = t;
						if (nextEventCycle > t)
							nextEventCycle = t;
					}
				}
				else
					this.channels[i].timerCycle = 8388608;
				if (i == 0)
					break;
			}
			break;
		case 15:
			if (data == this.skctl)
				break;
			generateUntilCycle(pokeys, cycle);
			this.skctl = data;
			boolean init = (data & 3) == 0;
			if (this.init && !init)
				this.polyIndex = ((this.audctl & 128) != 0 ? 237614 : 60948014) - cycle;
			this.init = init;
			initMute(cycle);
			this.channels[2].setMute((data & 16) != 0, 4, cycle);
			this.channels[3].setMute((data & 16) != 0, 4, cycle);
			break;
		default:
			break;
		}
		return nextEventCycle;
	}

	final int checkIrq(int cycle, int nextEventCycle)
	{
		for (int i = 3;; i >>= 1) {
			int timerCycle = this.channels[i].timerCycle;
			if (cycle >= timerCycle) {
				this.irqst &= ~(i + 1);
				this.channels[i].timerCycle = 8388608;
			}
			else if (nextEventCycle > timerCycle)
				nextEventCycle = timerCycle;
			if (i == 0)
				break;
		}
		return nextEventCycle;
	}

	final int storeSample(byte[] buffer, int bufferOffset, int i, ASAPSampleFormat format)
	{
		this.iirAcc += this.deltaBuffer[i] - (this.iirRate * this.iirAcc >> 11);
		int sample = this.iirAcc >> 11;
		if (sample < -32767)
			sample = -32767;
		else if (sample > 32767)
			sample = 32767;
		switch (format) {
		case U8:
			buffer[bufferOffset++] = (byte) ((sample >> 8) + 128);
			break;
		case S16_L_E:
			buffer[bufferOffset++] = (byte) sample;
			buffer[bufferOffset++] = (byte) (sample >> 8);
			break;
		case S16_B_E:
			buffer[bufferOffset++] = (byte) (sample >> 8);
			buffer[bufferOffset++] = (byte) sample;
			break;
		}
		return bufferOffset;
	}

	final void accumulateTrailing(int i)
	{
		this.trailing = i;
	}
}
