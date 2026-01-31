#pragma once

class CChannelControl {

public:
    typedef unsigned int ChannelNumber;
    static bool IsChannelOn(const ChannelNumber channel);
};

extern void SetChannelOnOff(int ch, int onoff);
extern void ToggleChannelOnOff(int ch);
extern int GetChannelOnOff(int ch);

extern void SetAllChannelsOn();
extern void SetAllChannelsOff();
extern void ToggleAllChannelsOnOff();
extern void SetChannelSolo(int ch);


