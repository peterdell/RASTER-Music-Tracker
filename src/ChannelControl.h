#pragma once

#include "StdAfx.h"

#include <vector>

class CChannelControl {

public:

    CChannelControl(unsigned int channelCount);

    typedef unsigned int ChannelNumber;
    bool IsChannelOn(const ChannelNumber channel);

    void ToggleChannelOnOff(const ChannelNumber ch);

    void SetAllChannelsOn();
    void SetAllChannelsOff();
    void ToggleAllChannelsOnOff();
    void SetChannelSolo(const ChannelNumber ch);

private:
    unsigned int m_channelCount;
    std::vector<bool> m_channelon;

    // Parameter onoff is 0=off, 1=on, -1=toggle
    void SetChannelOnOff(const int ch, int onoff);
};



