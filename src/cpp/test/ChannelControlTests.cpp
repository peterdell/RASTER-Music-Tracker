#include "gtest/gtest.h"

#include "ChannelControl.h"

TEST(ChannelControlTest, NewChannelsStartOff) {
    CChannelControl channels(4);
    for (unsigned int i = 0; i < 4; i++) {
        EXPECT_FALSE(channels.IsChannelOn(i));
    }
}

TEST(ChannelControlTest, ToggleChannelOnOffFlipsOnlyThatChannel) {
    CChannelControl channels(4);
    channels.ToggleChannelOnOff(1);
    EXPECT_FALSE(channels.IsChannelOn(0));
    EXPECT_TRUE(channels.IsChannelOn(1));
    EXPECT_FALSE(channels.IsChannelOn(2));

    channels.ToggleChannelOnOff(1);
    EXPECT_FALSE(channels.IsChannelOn(1));
}

TEST(ChannelControlTest, SetAllChannelsOnAndOff) {
    CChannelControl channels(3);
    channels.SetAllChannelsOn();
    for (unsigned int i = 0; i < 3; i++) EXPECT_TRUE(channels.IsChannelOn(i));

    channels.SetAllChannelsOff();
    for (unsigned int i = 0; i < 3; i++) EXPECT_FALSE(channels.IsChannelOn(i));
}

TEST(ChannelControlTest, ToggleAllChannelsOnOffInvertsEachIndependently) {
    CChannelControl channels(3);
    channels.ToggleChannelOnOff(1); // {off, on, off}
    channels.ToggleAllChannelsOnOff(); // {on, off, on}
    EXPECT_TRUE(channels.IsChannelOn(0));
    EXPECT_FALSE(channels.IsChannelOn(1));
    EXPECT_TRUE(channels.IsChannelOn(2));
}

// SetChannelSolo() toggles: soloing an already-solo'd channel (only it is on)
// turns everything back on, rather than leaving it soloed.
TEST(ChannelControlTest, SoloWhenOthersAreOnTurnsOffEverythingElse) {
    CChannelControl channels(4);
    channels.SetAllChannelsOn();

    channels.SetChannelSolo(1);

    EXPECT_FALSE(channels.IsChannelOn(0));
    EXPECT_TRUE(channels.IsChannelOn(1));
    EXPECT_FALSE(channels.IsChannelOn(2));
    EXPECT_FALSE(channels.IsChannelOn(3));
}

TEST(ChannelControlTest, SoloAgainWhenAlreadyAloneTurnsAllBackOn) {
    CChannelControl channels(4);
    channels.SetAllChannelsOn();
    channels.SetChannelSolo(1); // now only channel 1 is on

    channels.SetChannelSolo(1); // solo an already-solo'd channel

    for (unsigned int i = 0; i < 4; i++) EXPECT_TRUE(channels.IsChannelOn(i));
}

TEST(ChannelControlTest, SoloFromAllOffTurnsOnlyTargetOn) {
    CChannelControl channels(4);
    channels.SetChannelSolo(2);

    EXPECT_FALSE(channels.IsChannelOn(0));
    EXPECT_FALSE(channels.IsChannelOn(1));
    EXPECT_TRUE(channels.IsChannelOn(2));
    EXPECT_FALSE(channels.IsChannelOn(3));
}
