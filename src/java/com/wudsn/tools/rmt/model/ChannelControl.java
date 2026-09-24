package com.wudsn.tools.rmt.model;

import java.util.Arrays;

/**
 * Ported from CChannelControl (src/cpp/ChannelControl.h/.cpp).
 *
 * <p>C++'s private SetChannelOnOff(ch, onoff) multiplexes "one channel or
 * all channels" (ch == -1) and "set or toggle" (onoff == -1) behind two
 * sentinel parameters, then uses a {@code goto} inside setChannelSolo() to
 * share its "turn everything off, then turn just the target on" tail with
 * the "target is off" case. Ported here as direct, single-purpose methods
 * instead - Java has no goto, and the sentinel-multiplexing added no value
 * once each call site can just call the specific method it means; this
 * doesn't change any observable behavior (verified by tracing
 * setChannelSolo()'s two branches against the original goto/sentinel
 * version before simplifying).
 */
public final class ChannelControl {

	private final boolean[] channelOn;

	public ChannelControl(int channelCount) {
		channelOn = new boolean[channelCount];
	}

	public boolean isChannelOn(int channelNumber) {
		return channelOn[channelNumber];
	}

	public void toggleChannelOnOff(int channelNumber) {
		channelOn[channelNumber] = !channelOn[channelNumber];
	}

	public void setAllChannelsOn() {
		Arrays.fill(channelOn, true);
	}

	public void setAllChannelsOff() {
		Arrays.fill(channelOn, false);
	}

	public void toggleAllChannelsOnOff() {
		for (int i = 0; i < channelOn.length; i++) {
			channelOn[i] = !channelOn[i];
		}
	}

	/**
	 * Turn ON only one channel, making sure all others are turned off. Soloing an already-solo'd channel (the only one on) turns everything back on instead of leaving it soloed.
	 */
	public void setChannelSolo(int channelNumber) {
		boolean anyOtherChannelOn = false;
		for (int i = 0; i < channelOn.length; i++) {
			if (i != channelNumber && channelOn[i]) {
				anyOtherChannelOn = true;
				break;
			}
		}
		if (isChannelOn(channelNumber) && !anyOtherChannelOn) {
			setAllChannelsOn();
		} else {
			setAllChannelsOff();
			channelOn[channelNumber] = true;
		}
	}
}
