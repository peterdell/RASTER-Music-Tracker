// POKEY Frequencies Calculator
// by VinsCool
// Based on the code originally used for the Pitch Calculations in Raster Music Tracker 1.31+
// Backported to RMT with additional improvements
//
// The two methods here are the only parts of CTuning that read global tuning
// state (g_tuning/g_tuningRatios/g_notesperoctave) and the emulated Atari
// memory, so they're kept in a separate translation unit from the rest of
// CTuning's pure pitch-math methods (Tuning.cpp) — that lets tests link
// against the pure math without pulling in Global.h's much larger dependency
// graph.

#include "Global.h"
#include "Tuning.h"
#include "Messages.h"

/// <summary> Generate a POKEY Frequencies lookup table using the given parameters </summary>
/// <param name = "table"> Memory address the table will be written to, typically the emulated Atari memory </param>
/// <param name = "length"> Length of the table in number of semitones, 16-bit tables will use twice number of bytes </param>
/// <param name = "semitone"> Number of semitones above base note, useful for transposing a table to a different key/octave </param>
/// <param name = "timbre"> POKEY sound timbre output using the Distortion as well as the modulo of the Frequency </param>
/// <param name = "audctl"> POKEY modes used to generate the frequencies, typically, 15Khz/64Khz clock, 1.79mHz clock, 16-bit mode, etc </param>
void CTuning::GenerateTable(byte* table, int length, int semitone, Timbre timbre, int audctl) {
    //variables for pitch calculation, divisors must never be 0!
    double divisor = 1;
    int coarse_divisor = 1;
    int cycle = 1;

    //register variables
    //int distortion = timbre & 0xF0;
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
    //the channel number doesn't actually matter for creating tables, so the parameter is omitted
    bool JOIN_16BIT = ((JOIN_12 && CH1_179) || (JOIN_34 && CH3_179)) ? 1 : 0;
    bool CLOCK_179 = (CH1_179 || CH3_179) ? 1 : 0;
    if (JOIN_16BIT || CLOCK_179) {
        CLOCK_15 = 0; //override, these 2 take priority over 15khz mode if they are enabled at the same time
    }

    //TODO: apply Two-Tone timer offset into calculations when channel 1+2 are linked in 1.79mhz mode
    //This would help generating tables using patterns discovered by synthpopalooza
    if (JOIN_16BIT) {
        cycle = 7;
    } else if (CLOCK_179) {
        cycle = 4;
    } else {
        coarse_divisor = (CLOCK_15) ? 114 : 28;
    }

    //Many combinations depend entirely on the Modulo of POKEY frequencies to generate different tones
    //If a known value provide unstable results, it may be avoided on purpose
    bool MOD3 = 0;
    bool MOD5 = 0;
    bool MOD7 = 0;
    bool MOD15 = 0;
    bool MOD31 = 0;
    bool MOD73 = 0;

    //Use the modulo flags to make sure the correct timbre will be output
    switch (timbre) {
    case Timbre::PINK_NOISE:
        break;

    case Timbre::BROWNIAN_NOISE:
        divisor = 36.5; //Brownian noise, not MOD31 and not MOD73
        break;

    case Timbre::FUZZY_NOISE:
        divisor = 255.5; //Fuzzy noise, not MOD7, not MOD31 and not MOD73
        break;

    case Timbre::BELL:
        divisor = 31; //Bell tones, not MOD31
        break;

    case Timbre::BUZZY_4:
        divisor = 232.5; //Buzzy tones, neither MOD3 or MOD5 or MOD31
        break;

    case Timbre::SMOOTH_4:
        divisor = 77.5; //Smooth tones, MOD3 but not MOD5 or MOD31
        break;

    case Timbre::WHITE_NOISE:
        break;

    case Timbre::METALLIC_NOISE:
        divisor = 36.5; //Metallic noise, not MOD73
        break;

    case Timbre::BUZZY_NOISE:
        divisor = 255.5; //Buzzy noise, not MOD7 and not MOD73
        break;

    case Timbre::PURE_A:
        break;

    case Timbre::GRITTY_C:
        divisor = 7.5; //Gritty tones, neither MOD3 or MOD5
        break;

    case Timbre::BUZZY_C:
        divisor = 2.5; //Buzzy tones, MOD3 but not MOD5
        break;

    case Timbre::UNSTABLE_C:
        divisor = 1.5; //Unstable Buzzy tones, MOD5 but not MOD3
        break;

    default:
        //Distortion A is assumed if no valid parameter is supplied
        break;
    }

    //generate the table using all the initialised parameters
    for (int i = 0; i < length; i++) {
        //get the current semitone
        auto note = i + semitone;

        //calculate the reference pitch using the semitone as an offset
        auto pitch = GetTruePitch(g_tuning.basetuning, g_tuning.temperament, g_tuning.basenote, note);

        //get the nearest POKEY frequency using the reference pitch
        auto audf = GetAUDF(pitch, coarse_divisor, divisor, cycle);

        //TODO: insert whatever delta method that could be suitable here...
        MOD3 = ((audf + cycle) % 3 == 0);
        MOD5 = ((audf + cycle) % 5 == 0);
        MOD7 = ((audf + cycle) % 7 == 0);
        MOD15 = ((audf + cycle) % 15 == 0);
        MOD31 = ((audf + cycle) % 31 == 0);
        MOD73 = ((audf + cycle) % 73 == 0);

        switch (timbre) {
        case Timbre::BELL:
            if (MOD31) {
                audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, divisor, cycle, timbre);
            }
            break;

        case Timbre::BUZZY_4:
            if (MOD3 || MOD5 || MOD31) {
                audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, divisor, cycle, timbre);
            }
            break;

        case Timbre::SMOOTH_4:
            if (!(MOD3 || CLOCK_15) || MOD5) {
                audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, divisor, cycle, timbre);
            }
            if (!JOIN_16BIT && audf > 0xFF) { //use the buzzy timbre on the lower range instead
                audf = GetAUDF(pitch, coarse_divisor, 232.5, cycle);
                MOD3 = ((audf + cycle) % 3 == 0);
                MOD5 = ((audf + cycle) % 5 == 0);
                if (MOD3 || MOD5) {
                    audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, 232.5, cycle, Timbre::BUZZY_4);
                }
            }
            break;

        case Timbre::GRITTY_C:
            if (MOD3 || MOD5) {
                audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, divisor, cycle, timbre);
            }
            break;

        case Timbre::BUZZY_C:
            if (!(MOD3 || CLOCK_15) || MOD5) {
                audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, divisor, cycle, timbre);
            }
            if (!JOIN_16BIT && audf > 0xFF) { //use the gritty timbre on the lower range instead
                audf = GetAUDF(pitch, coarse_divisor, 7.5, cycle);
                MOD3 = ((audf + cycle) % 3 == 0);
                MOD5 = ((audf + cycle) % 5 == 0);
                if (MOD3 || MOD5) {
                    audf = CalculateDeltaAUDF(pitch, audf, coarse_divisor, 7.5, cycle, Timbre::GRITTY_C);
                }
            }
            break;
        }

        if (audf < 0) {
            audf = 0;
        }
        if (!JOIN_16BIT && audf > 0xFF) {
            audf = 0xFF;
        }
        if (JOIN_16BIT && audf > 0xFFFF) {
            audf = 0xFFFF;
        }

        // Write the POKEY frequency to the table
        if (JOIN_16BIT) { // In 16-bit tables, 2 bytes have to be written contiguously
            table[i * 2] = audf & 0x0FF; // LSB
            table[i * 2 + 1] = audf >> 8; // MSB
        } else {
            table[i] = audf;
        }
    }
}

// The two sawtooth tables of PAGE_EXTRA_0 ($B500 for channel 1, $B540 for
// channel 3), copied verbatim from asm/Patch-16/rmtdata.a65, which stays
// their source of truth. No formula produces them: the values are hand-picked
// and non-monotonic, two channels beating against each other rather than a
// scale. InitTuning() writes them alongside the generated tables so that the
// whole $B000-$B5FF region is a pure function of the tuning settings - it
// must not depend on a driver binary having been loaded over it first
// (plans/37_TUNING_COMPATIBILITY_PLAN.md, T6).
static const byte sawtooth_tables[128] = {
    0xE4, 0xDE, 0xD7, 0xD1, 0xCB, 0xC5, 0xBF, 0xBA, 0xB4, 0xAF, 0xAA, 0xA5,
    0xA0, 0x9B, 0x97, 0x92, 0x8E, 0x8A, 0x86, 0x82, 0x7E, 0x7A, 0x77, 0x73,
    0x70, 0x6D, 0x69, 0x66, 0x63, 0x60, 0x5D, 0x81, 0x58, 0x7A, 0x76, 0x50,
    0x4E, 0x6C, 0x69, 0x47, 0x45, 0x43, 0x5D, 0x5A, 0x57, 0x3B, 0x52, 0x62,
    0x4D, 0x34, 0x68, 0x31, 0x62, 0x42, 0x5C, 0x2B, 0x6A, 0x67, 0x6C, 0x58,
    0x35, 0x4A, 0x32, 0x4E, 0xE5, 0xDF, 0xD8, 0xD2, 0xCC, 0xC6, 0xC0, 0xBB,
    0xB5, 0xB0, 0xAB, 0xA6, 0xA1, 0x9C, 0x98, 0x93, 0x8F, 0x8B, 0x87, 0x83,
    0x7F, 0x7B, 0x78, 0x74, 0x71, 0x6E, 0x6A, 0x67, 0x64, 0x61, 0x5E, 0x83,
    0x59, 0x7C, 0x78, 0x51, 0x4F, 0x6E, 0x6B, 0x48, 0x46, 0x44, 0x5F, 0x5C,
    0x59, 0x3C, 0x54, 0x65, 0x4F, 0x35, 0x6C, 0x32, 0x66, 0x44, 0x60, 0x2C,
    0x70, 0x6D, 0x73, 0x5D, 0x37, 0x4E, 0x34, 0x53};

/// <summary> Initialize the tuning variables, and generate the POKEY frequencies (AUDF) lookup tables into the emulated Atari memory </summary>
void CTuning::InitTuning() {
    if (!g_tuning.basetuning) { //if base tuning is 0.0, make sure to reset it, else the program could crash!
        SendErrorMessage("Program error", "An invalid tuning has been detected!\n\nBasetuning is zero. ");
        exit(1);
    }

    g_notesperoctave = 12; //by default, an octave uses 12 semitones...

    if (g_tuning.temperament > NO_TEMPERAMENT && g_tuning.temperament < TUNING_CUSTOM) { //...unless it is specified otherwise in the Temperament presets
        for (int i = 0; i < PRESETS_LENGTH; i++) {
            if (temperament_preset[g_tuning.temperament][i]) {
                continue;
            }
            g_notesperoctave = i - 1;
            break;
        }
    }

    // calculate the custom ratio used for each semitone
    //TODO: restructure this to something much better, and flexible
    //these individual variables are just too uncomfortable to use that way
    CUSTOM[0] = (double)g_tuningRatios.UNISON;
    CUSTOM[1] = (double)g_tuningRatios.MIN_2ND;
    CUSTOM[2] = (double)g_tuningRatios.MAJ_2ND;
    CUSTOM[3] = (double)g_tuningRatios.MIN_3RD;
    CUSTOM[4] = (double)g_tuningRatios.MAJ_3RD;
    CUSTOM[5] = (double)g_tuningRatios.PERF_4TH;
    CUSTOM[6] = (double)g_tuningRatios.TRITONE;
    CUSTOM[7] = (double)g_tuningRatios.PERF_5TH;
    CUSTOM[8] = (double)g_tuningRatios.MIN_6TH;
    CUSTOM[9] = (double)g_tuningRatios.MAJ_6TH;
    CUSTOM[10] = (double)g_tuningRatios.MIN_7TH;
    CUSTOM[11] = (double)g_tuningRatios.MAJ_7TH;
    CUSTOM[12] = (double)g_tuningRatios.OCTAVE;

    //Generate all lookup tables used by the RMT driver for tuning purposes
    //TODO: optimise this procedure, even if right now this is much better than what it used to be

    //Distortion 2, at 0xB000
    GenerateTable(m_table_memory + 0x000, 64, dist_2_bell.table_64khz * g_notesperoctave, Timbre::BELL, 0x00);
    GenerateTable(m_table_memory + 0x040, 64, dist_2_bell.table_179mhz * g_notesperoctave, Timbre::BELL, 0x40);
    GenerateTable(m_table_memory + 0x080, 64, dist_2_bell.table_16bit * g_notesperoctave, Timbre::BELL, 0x50);
    //no 15kHz table...

    //Distortion 4 (Smooth), at 0xB100
    GenerateTable(m_table_memory + 0x100, 64, dist_4_smooth.table_64khz * g_notesperoctave, Timbre::SMOOTH_4, 0x00);
    GenerateTable(m_table_memory + 0x140, 64, dist_4_smooth.table_179mhz * g_notesperoctave, Timbre::SMOOTH_4, 0x40);
    GenerateTable(m_table_memory + 0x180, 64, dist_4_smooth.table_16bit * g_notesperoctave, Timbre::SMOOTH_4, 0x50);
    //no 15kHz table...

    //Distortion A (Pure), at 0xB200
    GenerateTable(m_table_memory + 0x200, 64, dist_a_pure.table_64khz * g_notesperoctave, Timbre::PURE_A, 0x00);
    GenerateTable(m_table_memory + 0x240, 64, dist_a_pure.table_179mhz * g_notesperoctave, Timbre::PURE_A, 0x40);
    GenerateTable(m_table_memory + 0x280, 64, dist_a_pure.table_16bit * g_notesperoctave, Timbre::PURE_A, 0x50);
    GenerateTable(m_table_memory + 0x580, 64, dist_a_pure.table_15khz * g_notesperoctave, Timbre::PURE_A, 0x01);

    //Distortion C (Buzzy), at 0xB300
    GenerateTable(m_table_memory + 0x300, 64, dist_c_buzzy.table_64khz * g_notesperoctave, Timbre::BUZZY_C, 0x00);
    GenerateTable(m_table_memory + 0x340, 64, dist_c_buzzy.table_179mhz * g_notesperoctave, Timbre::BUZZY_C, 0x40);
    GenerateTable(m_table_memory + 0x380, 64, dist_c_buzzy.table_16bit * g_notesperoctave, Timbre::BUZZY_C, 0x50);
    GenerateTable(m_table_memory + 0x5C0, 64, dist_c_buzzy.table_15khz * g_notesperoctave, Timbre::BUZZY_C, 0x01);

    //Distortion C (Buzzy), at 0xB300
    GenerateTable(m_table_memory + 0x400, 64, dist_c_gritty.table_64khz * g_notesperoctave, Timbre::GRITTY_C, 0x00);
    GenerateTable(m_table_memory + 0x440, 64, dist_c_gritty.table_179mhz * g_notesperoctave, Timbre::GRITTY_C, 0x40);
    GenerateTable(m_table_memory + 0x480, 64, dist_c_gritty.table_16bit * g_notesperoctave, Timbre::GRITTY_C, 0x50);
    //no 15kHz table...

    //Sawtooth, at 0xB500 and 0xB540 - hand-made, not generated (see sawtooth_tables above)
    memcpy(m_table_memory + 0x500, sawtooth_tables, sizeof(sawtooth_tables));
}
