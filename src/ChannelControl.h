#pragma once

#include "StdAfx.h"

#include <vector>

class CChannelControl {

public:
    typedef unsigned int ChannelNumber;

    CChannelControl(unsigned int channelCount);

    bool IsChannelOn(const ChannelNumber channelNumber) const;

    void ToggleChannelOnOff(const ChannelNumber channelNumber);

    void SetAllChannelsOn();
    void SetAllChannelsOff();
    void ToggleAllChannelsOnOff();
    void SetChannelSolo(const ChannelNumber channelNumber);

private:
    unsigned int m_channelCount;
    std::vector<bool> m_channelon;

    // Parameter onoff is 0=off, 1=on, -1=toggle
    void SetChannelOnOff(const int ch, int onoff);
};



