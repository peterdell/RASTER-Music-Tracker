#pragma once

class CChannelControl {

public:
    typedef unsigned int ChannelNumber;
    static bool IsChannelOn(const ChannelNumber channel);

    static void ToggleChannelOnOff(const ChannelNumber ch);

    static void SetAllChannelsOn();
    static void SetAllChannelsOff();
    static void ToggleAllChannelsOnOff();
    static void SetChannelSolo(const ChannelNumber ch);

private:
    // Parameter onoff is 0=off, 1=on, -1=toggle
    static void SetChannelOnOff(const int ch, int onoff);
};



