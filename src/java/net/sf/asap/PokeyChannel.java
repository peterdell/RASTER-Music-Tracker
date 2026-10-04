// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class PokeyChannel
{
	int audf;
	int audc;
	int periodCycles;
	int tickCycle;
	int timerCycle;

	static final int MUTE_INIT = 1;

	static final int MUTE_USER = 2;

	static final int MUTE_SERIAL_INPUT = 4;
	int mute;
	private int out;
	int delta;

	final void initialize()
	{
		this.audf = 0;
		this.audc = 0;
		this.periodCycles = 28;
		this.tickCycle = 8388608;
		this.timerCycle = 8388608;
		this.mute = 0;
		this.out = 0;
		this.delta = 0;
	}

	private void addDelta(Pokey pokey, PokeyPair pokeys, int cycle, int delta)
	{
		pokey.addDelta(pokeys, cycle, delta, (this.mute & 2) != 0);
	}

	private void slope(Pokey pokey, PokeyPair pokeys, int cycle)
	{
		this.delta = -this.delta;
		addDelta(pokey, pokeys, cycle, this.delta);
	}

	final void doTick(Pokey pokey, PokeyPair pokeys, int cycle, int ch)
	{
		this.tickCycle += this.periodCycles;
		int audc = this.audc;
		if ((audc & 176) == 160)
			this.out ^= 1;
		else if ((audc & 16) != 0 || pokey.init)
			return;
		else {
			int poly = cycle + pokey.polyIndex - ch;
			if (audc < 128 && (1706902752 & 1 << poly % 31) == 0)
				return;
			if ((audc & 32) != 0)
				this.out ^= 1;
			else {
				int newOut;
				if ((audc & 64) != 0)
					newOut = 21360 >> poly % 15;
				else if (pokey.audctl < 128) {
					poly %= 131071;
					newOut = (pokeys.poly17Lookup[poly >> 3] & 0xff) >> (poly & 7);
				}
				else
					newOut = pokeys.poly9Lookup[poly % 511] & 0xff;
				newOut &= 1;
				if (this.out == newOut)
					return;
				this.out = newOut;
			}
		}
		slope(pokey, pokeys, cycle);
	}

	final void slopeDown(Pokey pokey, PokeyPair pokeys, int cycle)
	{
		if (this.delta > 0 && this.mute == 0)
			slope(pokey, pokeys, cycle);
	}

	final void doStimer(Pokey pokey, PokeyPair pokeys, int cycle, int reload)
	{
		if (this.tickCycle != 8388608)
			this.tickCycle = cycle + reload;
		if (this.out != 0) {
			this.out = 0;
			slope(pokey, pokeys, cycle);
		}
	}

	final void setMute(boolean enable, int mask, int cycle)
	{
		if (enable) {
			this.mute |= mask;
			this.tickCycle = 8388608;
		}
		else {
			this.mute &= ~mask;
			if (this.mute == 0 && this.tickCycle == 8388608)
				this.tickCycle = cycle;
		}
	}

	final void setAudc(Pokey pokey, PokeyPair pokeys, int data, int cycle)
	{
		if (this.audc == data)
			return;
		pokey.generateUntilCycle(pokeys, cycle);
		this.audc = data;
		if ((data & 16) != 0) {
			data &= 15;
			addDelta(pokey, pokeys, cycle, this.delta > 0 ? data - this.delta : data);
			this.delta = data;
		}
		else {
			data &= 15;
			if (this.delta > 0) {
				addDelta(pokey, pokeys, cycle, data - this.delta);
				this.delta = data;
			}
			else
				this.delta = -data;
		}
	}

	final void endFrame(int cycle)
	{
		if (this.timerCycle != 8388608)
			this.timerCycle -= cycle;
	}
}
