#include "ChannelControl.h"
#include "StdAfx.h"

CChannelControl::CChannelControl(unsigned int channelCount) : m_channelCount(channelCount) {

    for (unsigned int i = 0; i < channelCount; i++) {
        m_channelon.push_back(false);
    }
}


bool CChannelControl::IsChannelOn(const ChannelNumber channel) {
    return m_channelon[channel];
}


// ----------------------------------------------------------------------------
// Channel On/Off helper functions

/// <summary>
/// Turn all or individual sound channels on or off
/// </summary>
/// <param name="ch">-1 = all channels, 0 - 7 = the sound channel</param>
/// <param name="onoff">-1 = invert state, 0 = off, 1 = on</param>
void CChannelControl::SetChannelOnOff(const int ch, int onoff)
{
    if (ch < 0)
    {
        // All channels
        if (onoff >= 0)
            for (unsigned int i = 0; i < m_channelon.size(); i++) { m_channelon[i] = onoff; } // Set the given on/off state
        else
            for (unsigned int i = 0; i < m_channelon.size(); i++) { m_channelon[i] = !m_channelon[i]; } // Invert the on/off state
    }
    else if (ch < m_channelon.size())
    {
        // Just that one
        if (onoff >= 0) {
            m_channelon[ch] = onoff;	// Set the given on/off state
        }
        else {
            m_channelon[ch] = !m_channelon[ch];		// Invert the on/off state
        }
    }
}

void CChannelControl::ToggleChannelOnOff(const ChannelNumber channelNumber) {
    SetChannelOnOff(channelNumber, -1);
}

void CChannelControl::SetAllChannelsOff() {
    SetChannelOnOff(-1, 0);
}

void CChannelControl::SetAllChannelsOn() {
    SetChannelOnOff(-1, 1);
}

void CChannelControl::ToggleAllChannelsOnOff() {
    SetChannelOnOff(-1, -1);
}

/// <summary>
/// Turn ON only one channel, making sure that all others are turned off
/// </summary>
/// <param name="ch"></param>
void CChannelControl::SetChannelSolo(const ChannelNumber channelNumber)
{
    if (IsChannelOn(channelNumber))
    {
        // Target channel is ON
        // If any other channel is on then turn them all off except for the target channel
        for (unsigned int i = 0; i < m_channelCount; i++)
        {
            if (i != channelNumber && IsChannelOn(i)) goto Channel_SOLO;
        }
        // All other channels are off, turn them all on
        SetAllChannelsOn();
    }
    else
    {
        // Target channel is OFF
    Channel_SOLO:
        SetAllChannelsOff();
        SetChannelOnOff(channelNumber, 1);	// and turn ON only the solo channel
    }

}
