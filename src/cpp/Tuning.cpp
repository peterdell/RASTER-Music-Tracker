// POKEY Frequencies Calculator
// by VinsCool
// Based on the code originally used for the Pitch Calculations in Raster Music Tracker 1.31+
// Backported to RMT with additional improvements
//
// TODO: further cleanup, better documentation, better structure, fix any bug I may have missed so far

#include "Tuning.h"
#include <assert.h>

/// <summary> Generate the POKEY audio pitch using the given parameters </summary>
/// <param name = "audc"> POKEY Distortion and Volume output mode </param>
/// <param name = "audf"> POKEY Frequency, either 8-bit or 16-bit </param>
/// <param name = "audctl"> POKEY modes used to generate the frequencies, typically, 15Khz/64Khz clock, 1.79mHz clock, 16-bit mode, etc </param>
/// <param name = "channel"> POKEY channel number between 0 and 3, multiple parameters might give different results </param>
/// <returns> POKEY audio pitch (in Hertz) </returns> 
CTuning::Pitch CTuning::GetPOKEYPitch(const int audc, const AUDF audf, const int audctl, const int channel) const
{
    assert(0 <= channel && channel <= 3);

    // variables for pitch calculation, divisors must never be 0!
    double divisor = 1;
    int coarse_divisor = 1;
    int cycle = 1;

    // register variables 
    int distortion = audc & 0xf0;
    //int skctl = 0;	//not yet implemented in calculations
    //bool TWO_TONE = (skctl == 0x8B) ? 1 : 0;
    bool CLOCK_15 = audctl & 0x01;
    bool HPF_CH24 = audctl & 0x02;
    bool HPF_CH13 = audctl & 0x04;
    bool JOIN_34 = audctl & 0x08;
    bool JOIN_12 = audctl & 0x10;
    bool CH3_179 = audctl & 0x20;
    bool CH1_179 = audctl & 0x40;
    bool POLY9 = audctl & 0x80;

    //combined modes for some special output...
    bool JOIN_16BIT = ((JOIN_12 && CH1_179 && (channel == 1) || (JOIN_34 && CH3_179 && (channel == 3)))) ? true : false;
    bool CLOCK_179 = ((CH1_179 && (channel == 0)) || (CH3_179 && (channel == 2))) ? true : false;
    if (JOIN_16BIT || CLOCK_179) { CLOCK_15 = false; }	//override, these 2 take priority over 15khz mode if they are enabled at the same time

    /*
    //TODO: Sawtooth generation needs to be optimal in order to compromise the high pitched hiss versus the tuning accuracy
    SAWTOOTH = (CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (i == 0 || i == 4)) ? 1 : 0;
    SAWTOOTH_INVERTED = 0;
    if (i % 4 == 0)	//only in valid sawtooth channels
    audf3 = memory[idx[i + 2]];
    */

    //TODO: apply Two-Tone timer offset into calculations when channel 1+2 are linked in 1.79mhz mode
    //This would help generating tables using patterns discovered by synthpopalooza
    if (JOIN_16BIT) cycle = 7;
    else if (CLOCK_179) cycle = 4;
    else coarse_divisor = (CLOCK_15) ? 114 : 28;

    //Many combinations depend entirely on the Modulo of POKEY frequencies to generate different tones
    //If a known value provide unstable results, it may be avoided on purpose 
    bool MOD3 = ((audf + cycle) % 3 == 0);
    bool MOD5 = ((audf + cycle) % 5 == 0);
    bool MOD7 = ((audf + cycle) % 7 == 0);
    bool MOD15 = ((audf + cycle) % 15 == 0);
    bool MOD31 = ((audf + cycle) % 31 == 0);
    bool MOD73 = ((audf + cycle) % 73 == 0);

    switch (distortion)
    {
    case 0x00:
        if (POLY9)
        {
            divisor = 255.5;	//Metallic Buzzy
            if (MOD7 || (!CLOCK_15 && !CLOCK_179 && !JOIN_16BIT)) divisor = 36.5;	//seems to only sound "uniform" in 64kHz mode for some reason 
            if (MOD31 || MOD73) return 0;	//MOD31 and MOD73 values are invalid 
        }
        break;

    case 0x20:
    case 0x60:	//Duplicate of Distortion 2
        divisor = 31;
        if (MOD31) return 0;
        break;

    case 0x40:
        divisor = 232.5;		//Buzzy tones, neither MOD3 or MOD5 or MOD31
        if (MOD3 || CLOCK_15) divisor = 77.5;	//Smooth tones, MOD3 but not MOD5 or MOD31
        if (MOD5) divisor = 46.5;	//Unstable tones #1, MOD5 but not MOD3 or MOD31
        if (MOD31) divisor = (MOD3 || MOD5) ? 2.5 : 7.5;	//Unstables Tones #2 and #3, MOD31, with MOD3 or MOD5 
        if (MOD15 || (MOD5 && CLOCK_15)) return 0;	//Both MOD3 and MOD5 at once are invalid 
        break;

    case 0x80:
        if (POLY9)
        {
            divisor = 255.5;	//Metallic Buzzy
            if (MOD7 || (!CLOCK_15 && !CLOCK_179 && !JOIN_16BIT)) divisor = 36.5;	//seems to only sound "uniform" in 64kHz mode for some reason 
            if (MOD73) return 0;	//MOD73 values are invalid
        }
        break;

    case 0xC0:
        divisor = 7.5;	//Gritty tones, neither MOD3 or MOD5
        if (MOD3 || CLOCK_15) divisor = 2.5;	//Buzzy tones, MOD3 but not MOD5
        if (MOD5) divisor = 1.5;	//Unstable Buzzy tones, MOD5 but not MOD3
        if (MOD15 || (MOD5 && CLOCK_15)) return 0;	//Both MOD3 and MOD5 at once are invalid 
        break;
    }
    return GetPitch(audf, coarse_divisor, divisor, cycle);
}

/// <summary> Calculate the POKEY audio pitch using the given parameters </summary>
/// <param name = "audf"> POKEY Frequency, either 8-bit or 16-bit </param>
/// <param name = "coarse_divisor"> Coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division </param>
/// <param name = "divisor"> Fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division </param> 
/// <param name = "cycle"> Offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither </param>
/// <returns> POKEY audio pitch (in Hertz) </returns> 
CTuning::Pitch CTuning::GetPitch(AUDF audf, int coarse_divisor, double divisor, int cycle) const
{
    return ((m_clockFrequency / (coarse_divisor * divisor)) / (audf + cycle)) / 2;
}

/// <summary> Find the nearest POKEY Frequency (AUDF) using the given parameters </summary>
/// <param name = "pitch"> Source audio pitch (in Hertz) </param>
/// <param name = "coarse_divisor"> Coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division </param>
/// <param name = "divisor"> Fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division </param> 
/// <param name = "cycle"> Offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither </param>
/// <returns> POKEY Frequency (AUDF) </returns> 
CTuning::AUDF CTuning::GetAUDF(Pitch pitch, int coarse_divisor, double divisor, int cycle) const
{
    return (int)round(((m_clockFrequency / (coarse_divisor * divisor)) / (2 * pitch)) - cycle);
}

/// <summary> Calculate the difference between 2 POKEY frequencies (AUDF) within the conditions intended for the timbre to be output </summary>
/// <param name = "pitch"> Reference audio pitch (in Hertz) </param>
/// <param name = "audf"> Invalid POKEY Frequency (AUDF) referenced to find the nearest compromised frequency </param>
/// <param name = "coarse_divisor"> Coarse division, 28 in 64kHz mode, 114 in 15kHz mode, 1 for no division </param>
/// <param name = "divisor"> Fine division, variable relative to Distortion, Cycle, and frequency modulo, 1 for no division </param> 
/// <param name = "cycle"> Offset added to AUDF, 4 for 1.79mHz mode, 7 for 16-bit+1.79mHz mode, 1 for neither </param>
/// <param name = "timbre"> POKEY sound timbre output using the Distortion as well as the modulo of the Frequency </param>
/// <returns> Compromised POKEY Frequency (AUDF) which is now valid within the conditions established for the generated timbre </returns> 
CTuning::AUDF CTuning::CalculateDeltaAUDF(Pitch pitch, AUDF audf, int coarse_divisor, double divisor, int cycle, Timbre timbre) const
{
    //TODO: Optimise this procedure a lot more, this is poorly written, but it gets the job done for now 
    int distortion = ((byte)timbre) & 0xF0;

    int tmp_audf_up = audf;		//begin from the currently invalid audf
    int tmp_audf_down = audf;
    double tmp_freq_up = 0;
    double tmp_freq_down = 0;
    double PITCH = 0;

    if (distortion != 0x40 && distortion != 0xC0) { tmp_audf_up++; tmp_audf_down--; }	//anything not distortion 4 or C, simpliest delta method

    else if (distortion == 0x40)
    {
        if (timbre == Timbre::SMOOTH_4)	//verify MOD3 integrity
        {
            for (int o = 0; o < 6; o++)
            {
                if ((tmp_audf_up + cycle) % 3 != 0 || (tmp_audf_up + cycle) % 5 == 0 || (tmp_audf_up + cycle) % 31 == 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 3 != 0 || (tmp_audf_down + cycle) % 5 == 0 || (tmp_audf_down + cycle) % 31 == 0) tmp_audf_down--;
            }
        }
        else if (timbre == Timbre::BUZZY_4)
        {
            for (int o = 0; o < 6; o++)
            {
                if ((tmp_audf_up + cycle) % 3 == 0 || (tmp_audf_up + cycle) % 5 == 0 || (tmp_audf_up + cycle) % 31 == 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 3 == 0 || (tmp_audf_down + cycle) % 5 == 0 || (tmp_audf_down + cycle) % 31 == 0) tmp_audf_down--;
            }
        }
        else return 0;	//invalid parameter most likely 
    }

    else if (distortion == 0xC0)
    {
        //if (CLOCK_15)
        if (coarse_divisor == 114)	//15kHz mode
        {
            for (int o = 0; o < 3; o++)	//MOD5 must be avoided!
            {
                if ((tmp_audf_up + cycle) % 5 == 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 5 == 0) tmp_audf_down--;
            }
        }
        else if (timbre == Timbre::BUZZY_C)	//verify MOD3 integrity
        {
            for (int o = 0; o < 6; o++)
            {
                if ((tmp_audf_up + cycle) % 3 != 0 || (tmp_audf_up + cycle) % 5 == 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 3 != 0 || (tmp_audf_down + cycle) % 5 == 0) tmp_audf_down--;
            }
        }
        else if (timbre == Timbre::GRITTY_C)	//verify neither MOD3 or MOD5 is used
        {
            for (int o = 0; o < 6; o++)	//get the closest compromise up and down first
            {
                if ((tmp_audf_up + cycle) % 3 == 0 || (tmp_audf_up + cycle) % 5 == 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 3 == 0 || (tmp_audf_down + cycle) % 5 == 0) tmp_audf_down--;
            }
        }
        else if (timbre == Timbre::UNSTABLE_C)	//verify MOD5 integrity
        {
            for (int o = 0; o < 6; o++)	//get the closest compromise up and down first
            {
                if ((tmp_audf_up + cycle) % 3 == 0 || (tmp_audf_up + cycle) % 5 != 0) tmp_audf_up++;
                if ((tmp_audf_down + cycle) % 3 == 0 || (tmp_audf_down + cycle) % 5 != 0) tmp_audf_down--;
            }
        }
        else return 0;	//invalid parameter most likely
    }

    PITCH = GetPitch(tmp_audf_up, coarse_divisor, divisor, cycle);
    tmp_freq_up = pitch - PITCH;	//first delta, up
    PITCH = GetPitch(tmp_audf_down, coarse_divisor, divisor, cycle);
    tmp_freq_down = PITCH - pitch;	//second delta, down
    PITCH = tmp_freq_down - tmp_freq_up;

    if (PITCH > 0) { audf = tmp_audf_up; } //positive, meaning delta up is closer than delta down
    else { audf = tmp_audf_down; } //negative, meaning delta down is closer than delta up
    return audf;
}

/// <summary> Calculate the true audio pitch output using the given parameters </summary>
/// <param name = "tuning"> Tuning base pitch (in Hertz), usually the A-4 note </param>
/// <param name = "temperament"> Temperament used in calculations, 0 for Equal Temperament, 1 to 29 (inclusive) for Presets, otherwise Custom Ratio will be assumed </param>
/// <param name = "basenote"> Base note/key from which the tuning is calculated, typically it is the key of A- or C- </param> 
/// <param name = "semitone"> Semitones added to base note for calculating higher pitches </param>
/// <returns> True audio pitch for a given note (in Hertz) </returns> 
double CTuning::GetTruePitch(double tuning, Temperament temperament, int basenote, int semitone)
{
    int notesnum = 12;	//unless specified otherwise
    int note = (semitone + basenote) % notesnum;	//current note
    double ratio = 0;	//will be used for calculations
    double octave = 2;	//an octave is usually the frequency of a note multiplied by 2
    double multi = 1; //octave multiplyer for the ratio

    //Equal temperament is generated using the 12th root of 2
    if (temperament == NO_TEMPERAMENT)
    {
        ratio = pow(2.0, 1.0 / 12.0);
        return (tuning / 64) * pow(ratio, semitone + basenote);
    }
    if (temperament >= TUNING_CUSTOM)	//custom temperament will be used using ratio
    {
        octave = CUSTOM[notesnum];
        ratio = CUSTOM[note];
    }
    else	//any temperament preset will be used
    {
        for (int i = 0; i < PRESETS_LENGTH; i++)
        {
            if (temperament_preset[temperament][i]) continue;
            notesnum = i - 1;
            break;
        }
        octave = temperament_preset[temperament][notesnum];
        note = (semitone + basenote) % notesnum;
        ratio = temperament_preset[temperament][note];
    }
    multi = pow(octave, trunc((semitone + basenote) / notesnum));
    return (tuning / 64) * (multi * ratio);
}

/// <summary> Initialize the tuning variables, and generate the POKEY frequencies (AUDF) lookup tables into the emulated Atari memory </summary>
/// <remarks> Implemented in TuningTables.cpp, along with GenerateTable(), since both read
/// global tuning state (see that file's header comment for why they're split out). </remarks>
void CTuning::InitTuning(const C6502::ClockFrequency clockFrequency, byte* table_memory)
{
    m_clockFrequency = clockFrequency;
    m_table_memory = table_memory;
    InitTuning();
}