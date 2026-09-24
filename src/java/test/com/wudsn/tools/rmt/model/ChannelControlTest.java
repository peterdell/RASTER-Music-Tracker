package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/ChannelControlTests.cpp. */
class ChannelControlTest {

	@Test
	void newChannelsStartOff() {
		ChannelControl channels = new ChannelControl(4);
		for (int i = 0; i < 4; i++) {
			assertFalse(channels.isChannelOn(i));
		}
	}

	@Test
	void toggleChannelOnOffFlipsOnlyThatChannel() {
		ChannelControl channels = new ChannelControl(4);
		channels.toggleChannelOnOff(1);
		assertFalse(channels.isChannelOn(0));
		assertTrue(channels.isChannelOn(1));
		assertFalse(channels.isChannelOn(2));

		channels.toggleChannelOnOff(1);
		assertFalse(channels.isChannelOn(1));
	}

	@Test
	void setAllChannelsOnAndOff() {
		ChannelControl channels = new ChannelControl(3);
		channels.setAllChannelsOn();
		for (int i = 0; i < 3; i++) {
			assertTrue(channels.isChannelOn(i));
		}

		channels.setAllChannelsOff();
		for (int i = 0; i < 3; i++) {
			assertFalse(channels.isChannelOn(i));
		}
	}

	@Test
	void toggleAllChannelsOnOffInvertsEachIndependently() {
		ChannelControl channels = new ChannelControl(3);
		channels.toggleChannelOnOff(1); // {off, on, off}
		channels.toggleAllChannelsOnOff(); // {on, off, on}
		assertTrue(channels.isChannelOn(0));
		assertFalse(channels.isChannelOn(1));
		assertTrue(channels.isChannelOn(2));
	}

	// setChannelSolo() toggles: soloing an already-solo'd channel (only it is on)
	// turns everything back on, rather than leaving it soloed.
	@Test
	void soloWhenOthersAreOnTurnsOffEverythingElse() {
		ChannelControl channels = new ChannelControl(4);
		channels.setAllChannelsOn();

		channels.setChannelSolo(1);

		assertFalse(channels.isChannelOn(0));
		assertTrue(channels.isChannelOn(1));
		assertFalse(channels.isChannelOn(2));
		assertFalse(channels.isChannelOn(3));
	}

	@Test
	void soloAgainWhenAlreadyAloneTurnsAllBackOn() {
		ChannelControl channels = new ChannelControl(4);
		channels.setAllChannelsOn();
		channels.setChannelSolo(1); // now only channel 1 is on

		channels.setChannelSolo(1); // solo an already-solo'd channel

		for (int i = 0; i < 4; i++) {
			assertTrue(channels.isChannelOn(i));
		}
	}

	@Test
	void soloFromAllOffTurnsOnlyTargetOn() {
		ChannelControl channels = new ChannelControl(4);
		channels.setChannelSolo(2);

		assertFalse(channels.isChannelOn(0));
		assertFalse(channels.isChannelOn(1));
		assertTrue(channels.isChannelOn(2));
		assertFalse(channels.isChannelOn(3));
	}
}
