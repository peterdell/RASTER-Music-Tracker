#include "gtest/gtest.h"

#include "Atari.h"
#include "FrequencyTables.h"
#include "Instruments.h"
#include "Notes.h"

extern int g_tracks4_8; // TODO Move out (see Instruments.cpp/IO_Instruments.cpp)
extern CAtari g_Atari; // real, linked, cheap global (see AtariStub.cpp) - needed by GetFrequency()

namespace {
constexpr int kInstr = 0;

void SetUpSampleInstrument(CInstruments& instruments) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    int* par = ai->parameters;
    par[PAR_TBL_LENGTH] = 1;
    par[PAR_TBL_GOTO] = 0;
    par[PAR_ENV_LENGTH] = 1;
    par[PAR_ENV_GOTO] = 0;
    par[PAR_TBL_TYPE] = 0;
    par[PAR_TBL_MODE] = 0;
    par[PAR_TBL_SPEED] = 5;
    par[PAR_AUDCTL_15KHZ] = 1;
    par[PAR_AUDCTL_HPF_CH2] = 0;
    par[PAR_AUDCTL_HPF_CH1] = 1;
    par[PAR_AUDCTL_JOIN_3_4] = 0;
    par[PAR_AUDCTL_JOIN_1_2] = 0;
    par[PAR_AUDCTL_179_CH3] = 0;
    par[PAR_AUDCTL_179_CH1] = 0;
    par[PAR_AUDCTL_POLY9] = 0;
    par[PAR_VOL_FADEOUT] = 3;
    par[PAR_VOL_MIN] = 2;
    par[PAR_DELAY] = 4;
    par[PAR_VIBRATO] = 1;
    par[PAR_FREQ_SHIFT] = 7;

    ai->noteTable[0] = 10;
    ai->noteTable[1] = 20;

    int* env0 = ai->envelope[0];
    env0[EnvelopeParameter::VOLUMER] = 3;
    env0[EnvelopeParameter::VOLUMEL] = 5;
    env0[EnvelopeParameter::DISTORTION] = 4;
    env0[EnvelopeParameter::COMMAND] = 2;
    env0[EnvelopeParameter::X] = 6;
    env0[EnvelopeParameter::Y] = 9;
    env0[EnvelopeParameter::FILTER] = 1;
    env0[EnvelopeParameter::PORTAMENTO] = 1;

    int* env1 = ai->envelope[1];
    env1[EnvelopeParameter::VOLUMER] = 7;
    env1[EnvelopeParameter::VOLUMEL] = 8;
    env1[EnvelopeParameter::DISTORTION] = 2;
    env1[EnvelopeParameter::COMMAND] = 1;
    env1[EnvelopeParameter::X] = 3;
    env1[EnvelopeParameter::Y] = 4;
    env1[EnvelopeParameter::FILTER] = 0;
    env1[EnvelopeParameter::PORTAMENTO] = 0;
}
} // namespace

class InstrumentAtaFormatTest : public ::testing::Test {
  protected:
    CInstruments instruments;

    void TearDown() override {
        g_tracks4_8 = 4; // restore the default so other tests aren't affected
    }
};

TEST_F(InstrumentAtaFormatTest, InstrToAtaMonoEncodesExactBytes) {
    g_tracks4_8 = 4; // mono
    SetUpSampleInstrument(instruments);

    unsigned char ata[32] = {0};
    BYTE size = instruments.InstrToAta(kInstr, ata, sizeof(ata));

    ASSERT_EQ(size, 20);
    const unsigned char expected[20] = {
        0x0D, 0x0C, 0x11, 0x0E, 0x05, 0x05, 0x03, 0x20, 0x04, 0x01,
        0x07, 0x00, 0x0A, 0x14, 0x55, 0xA5, 0x69, 0x88, 0x12, 0x34};
    EXPECT_EQ(memcmp(ata, expected, size), 0);
}

// Mono packing only stores one volume nibble per envelope entry
// ("VOLUME R = VOLUME L", per InstrToAta()'s own comment), so decoding a
// mono-encoded instrument loses the original, distinct VOLUMER value - this
// characterizes that intentional lossiness rather than treating it as a bug.
TEST_F(InstrumentAtaFormatTest, AtaToInstrMonoRoundTripLosesVolumeR) {
    g_tracks4_8 = 4; // mono
    SetUpSampleInstrument(instruments);

    unsigned char ata[32] = {0};
    BYTE size = instruments.InstrToAta(kInstr, ata, sizeof(ata));

    constexpr int kDecoded = 1;
    ASSERT_TRUE(instruments.AtaToInstr(ata, kDecoded));
    TInstrument* decoded = instruments.GetInstrument(kDecoded);

    EXPECT_EQ(decoded->parameters[PAR_TBL_LENGTH], 1);
    EXPECT_EQ(decoded->parameters[PAR_ENV_LENGTH], 1);
    EXPECT_EQ(decoded->parameters[PAR_TBL_SPEED], 5);
    EXPECT_EQ(decoded->parameters[PAR_AUDCTL_15KHZ], 1);
    EXPECT_EQ(decoded->parameters[PAR_AUDCTL_HPF_CH1], 1);
    EXPECT_EQ(decoded->parameters[PAR_VOL_FADEOUT], 3);
    EXPECT_EQ(decoded->parameters[PAR_VOL_MIN], 2);
    EXPECT_EQ(decoded->parameters[PAR_DELAY], 4);
    EXPECT_EQ(decoded->parameters[PAR_VIBRATO], 1);
    EXPECT_EQ(decoded->parameters[PAR_FREQ_SHIFT], 7);
    EXPECT_EQ(decoded->noteTable[0], 10);
    EXPECT_EQ(decoded->noteTable[1], 20);

    int* env0 = decoded->envelope[0];
    EXPECT_EQ(env0[EnvelopeParameter::VOLUMER], 5); // lossy: collapsed to VOLUMEL, not the original 3
    EXPECT_EQ(env0[EnvelopeParameter::VOLUMEL], 5);
    EXPECT_EQ(env0[EnvelopeParameter::DISTORTION], 4);
    EXPECT_EQ(env0[EnvelopeParameter::COMMAND], 2);
    EXPECT_EQ(env0[EnvelopeParameter::X], 6);
    EXPECT_EQ(env0[EnvelopeParameter::Y], 9);
    EXPECT_EQ(env0[EnvelopeParameter::FILTER], 1);
    EXPECT_EQ(env0[EnvelopeParameter::PORTAMENTO], 1);
}

// The stereo path stores both volume nibbles separately, so (unlike mono)
// VOLUMER round-trips exactly.
TEST_F(InstrumentAtaFormatTest, AtaToInstrStereoRoundTripPreservesBothVolumes) {
    g_tracks4_8 = 8; // stereo
    SetUpSampleInstrument(instruments);

    unsigned char ata[32] = {0};
    BYTE size = instruments.InstrToAta(kInstr, ata, sizeof(ata));

    ASSERT_EQ(size, 20);
    const unsigned char expected[20] = {
        0x0D, 0x0C, 0x11, 0x0E, 0x05, 0x05, 0x03, 0x20, 0x04, 0x01,
        0x07, 0x00, 0x0A, 0x14, 0x35, 0xA5, 0x69, 0x78, 0x12, 0x34};
    EXPECT_EQ(memcmp(ata, expected, size), 0);

    constexpr int kDecoded = 1;
    ASSERT_TRUE(instruments.AtaToInstr(ata, kDecoded));
    TInstrument* decoded = instruments.GetInstrument(kDecoded);

    EXPECT_EQ(decoded->envelope[0][EnvelopeParameter::VOLUMER], 3);
    EXPECT_EQ(decoded->envelope[0][EnvelopeParameter::VOLUMEL], 5);
    EXPECT_EQ(decoded->envelope[1][EnvelopeParameter::VOLUMER], 7);
    EXPECT_EQ(decoded->envelope[1][EnvelopeParameter::VOLUMEL], 8);
}

TEST_F(InstrumentAtaFormatTest, AtaToInstrRejectsOutOfBoundsEnvelope) {
    unsigned char ata[32] = {0};
    ata[0] = 12; // note table length 0, fine
    ata[1] = 12;
    ata[2] = 12 + 1 + (ENVELOPE_MAX_COLUMNS) * 3; // envelopeLength == ENVELOPE_MAX_COLUMNS -> out of bounds
    ata[3] = ata[2];

    EXPECT_FALSE(instruments.AtaToInstr(ata, kInstr));
}

TEST_F(InstrumentAtaFormatTest, AtaV0ToInstrDecodesOldFormat) {
    g_tracks4_8 = 4; // mono
    unsigned char ata[32] = {0};
    for (int i = 0; i < 8; i++) {
        ata[i] = i + 1; // note table 1..8
    }
    ata[8] = 0x0A; // ENV_LENGTH=1, TBL_LENGTH=2
    ata[9] = 0x01; // ENV_GOTO=0, TBL_GOTO=1
    ata[10] = 0x45; // TBL_TYPE=0, TBL_MODE=1, TBL_SPEED=5
    ata[11] = 9; // VOL_FADEOUT
    ata[12] = 0x33; // VOL_MIN=3, 15KHZ=1, POLY9=1
    ata[13] = 6; // DELAY
    ata[14] = 2; // VIBRATO
    ata[15] = 11; // FREQ_SHIFT
    ata[16] = 0x37;
    ata[17] = 0x94;
    ata[18] = 0x5B; // envelope entry 0
    ata[19] = 0x2C;
    ata[20] = 0x61;
    ata[21] = 0x8F; // envelope entry 1

    ASSERT_TRUE(instruments.AtaV0ToInstr(ata, kInstr));
    TInstrument* ai = instruments.GetInstrument(kInstr);

    for (int i = 0; i < 8; i++) {
        EXPECT_EQ(ai->noteTable[i], i + 1);
    }
    EXPECT_EQ(ai->parameters[PAR_ENV_LENGTH], 1);
    EXPECT_EQ(ai->parameters[PAR_TBL_LENGTH], 2);
    EXPECT_EQ(ai->parameters[PAR_ENV_GOTO], 0);
    EXPECT_EQ(ai->parameters[PAR_TBL_GOTO], 1);
    EXPECT_EQ(ai->parameters[PAR_TBL_TYPE], 0);
    EXPECT_EQ(ai->parameters[PAR_TBL_MODE], 1);
    EXPECT_EQ(ai->parameters[PAR_TBL_SPEED], 5);
    EXPECT_EQ(ai->parameters[PAR_VOL_FADEOUT], 9);
    EXPECT_EQ(ai->parameters[PAR_VOL_MIN], 3);
    EXPECT_EQ(ai->parameters[PAR_AUDCTL_15KHZ], 1);
    EXPECT_EQ(ai->parameters[PAR_AUDCTL_POLY9], 1);
    EXPECT_EQ(ai->parameters[PAR_AUDCTL_HPF_CH2], 0);
    EXPECT_EQ(ai->parameters[PAR_DELAY], 6);
    EXPECT_EQ(ai->parameters[PAR_VIBRATO], 2);
    EXPECT_EQ(ai->parameters[PAR_FREQ_SHIFT], 11);

    int* env0 = ai->envelope[0];
    EXPECT_EQ(env0[EnvelopeParameter::VOLUMER], 7); // mono: R == L
    EXPECT_EQ(env0[EnvelopeParameter::VOLUMEL], 7);
    EXPECT_EQ(env0[EnvelopeParameter::FILTER], 1);
    EXPECT_EQ(env0[EnvelopeParameter::COMMAND], 1);
    EXPECT_EQ(env0[EnvelopeParameter::DISTORTION], 4);
    EXPECT_EQ(env0[EnvelopeParameter::PORTAMENTO], 0);
    EXPECT_EQ(env0[EnvelopeParameter::X], 5);
    EXPECT_EQ(env0[EnvelopeParameter::Y], 11);

    int* env1 = ai->envelope[1];
    EXPECT_EQ(env1[EnvelopeParameter::VOLUMEL], 12);
    EXPECT_EQ(env1[EnvelopeParameter::FILTER], 0);
    EXPECT_EQ(env1[EnvelopeParameter::COMMAND], 6);
    EXPECT_EQ(env1[EnvelopeParameter::DISTORTION], 0);
    EXPECT_EQ(env1[EnvelopeParameter::PORTAMENTO], 1);
    EXPECT_EQ(env1[EnvelopeParameter::X], 8);
    EXPECT_EQ(env1[EnvelopeParameter::Y], 15);
}

// --- ClearInstrument / SetEnvelopeVolume / MemorizeOctaveAndVolume / RememberOctaveAndVolume ---
// (Instruments.cpp - see plans/04_BROADER_SURVEY_PLAN.md's "cheapest win"
// candidate: every dependency here turned out already real and safe.)

extern BOOL g_keyboard_RememberOctavesAndVolumes;

class InstrumentsCoreTest : public ::testing::Test {
  protected:
    CInstruments instruments;

    void TearDown() override {
        g_tracks4_8 = 4;
        g_keyboard_RememberOctavesAndVolumes = FALSE;
    }
};

TEST_F(InstrumentsCoreTest, ClearInstrumentResetsToStartupDefaults) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 5;
    ai->octave = 3;
    ai->volume = 2;
    ai->activeEditSection = InstrumentSection::NAME;

    instruments.ClearInstrument(kInstr);

    EXPECT_EQ(memcmp(ai->name, "Instrument 00  ", 15), 0);
    EXPECT_EQ(ai->activeEditSection, InstrumentSection::ENVELOPE);
    EXPECT_EQ(ai->editNameCursorPos, 0);
    EXPECT_EQ(ai->editParameterNr, PAR_ENV_LENGTH);
    EXPECT_EQ(ai->editEnvelopeX, 0);
    EXPECT_EQ(ai->editEnvelopeY, 1);
    EXPECT_EQ(ai->editNoteTableCursorPos, 0);
    EXPECT_EQ(ai->octave, 0);
    EXPECT_EQ(ai->volume, 15); // MAXVOLUME (SongTypes.h, not otherwise needed by this file)
    EXPECT_EQ(ai->parameters[PAR_ENV_LENGTH], 0);
}

TEST_F(InstrumentsCoreTest, ClearInstrumentIgnoresOutOfRangeIndex) {
    instruments.ClearInstrument(-1);
    instruments.ClearInstrument(INSTRSNUM);
    // No crash - nothing further to assert (GetInstrument() guards both).
}

TEST_F(InstrumentsCoreTest, SetEnvelopeVolumeSetsLeftChannelInMonoModeRegardlessOfRightFlag) {
    g_tracks4_8 = 4; // mono
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 1;

    instruments.SetEnvelopeVolume(kInstr, TRUE, 0, 9);

    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMEL], 9);
    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMER], 0);
}

TEST_F(InstrumentsCoreTest, SetEnvelopeVolumeSetsRightChannelInStereoModeWhenRequested) {
    g_tracks4_8 = 8; // stereo
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 1;

    instruments.SetEnvelopeVolume(kInstr, TRUE, 0, 9);

    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMER], 9);
    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMEL], 0);
}

TEST_F(InstrumentsCoreTest, SetEnvelopeVolumeIgnoresOutOfRangePosition) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 1;

    instruments.SetEnvelopeVolume(kInstr, FALSE, -1, 9);
    instruments.SetEnvelopeVolume(kInstr, FALSE, 2, 9); // > PAR_ENV_LENGTH + 1

    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMEL], 0);
}

TEST_F(InstrumentsCoreTest, SetEnvelopeVolumeIgnoresOutOfRangeVolume) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 1;

    instruments.SetEnvelopeVolume(kInstr, FALSE, 0, -1);
    instruments.SetEnvelopeVolume(kInstr, FALSE, 0, 16);

    EXPECT_EQ(ai->envelope[0][EnvelopeParameter::VOLUMEL], 0);
}

TEST_F(InstrumentsCoreTest, MemorizeOctaveAndVolumeStoresBothWhenEnabled) {
    g_keyboard_RememberOctavesAndVolumes = TRUE;
    TInstrument* ai = instruments.GetInstrument(kInstr);

    instruments.MemorizeOctaveAndVolume(kInstr, 3, 10);

    EXPECT_EQ(ai->octave, 3);
    EXPECT_EQ(ai->volume, 10);
}

TEST_F(InstrumentsCoreTest, MemorizeOctaveAndVolumeIgnoresNegativeValues) {
    g_keyboard_RememberOctavesAndVolumes = TRUE;
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->octave = 3;
    ai->volume = 10;

    instruments.MemorizeOctaveAndVolume(kInstr, -1, -1);

    EXPECT_EQ(ai->octave, 3);
    EXPECT_EQ(ai->volume, 10);
}

TEST_F(InstrumentsCoreTest, MemorizeOctaveAndVolumeDoesNothingWhenDisabled) {
    g_keyboard_RememberOctavesAndVolumes = FALSE;
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->octave = 3;
    ai->volume = 10;

    instruments.MemorizeOctaveAndVolume(kInstr, 5, 12);

    EXPECT_EQ(ai->octave, 3);
    EXPECT_EQ(ai->volume, 10);
}

TEST_F(InstrumentsCoreTest, RememberOctaveAndVolumeReadsBothWhenEnabled) {
    g_keyboard_RememberOctavesAndVolumes = TRUE;
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->octave = 4;
    ai->volume = 11;

    int oct = -99, vol = -99;
    instruments.RememberOctaveAndVolume(kInstr, oct, vol);

    EXPECT_EQ(oct, 4);
    EXPECT_EQ(vol, 11);
}

TEST_F(InstrumentsCoreTest, RememberOctaveAndVolumeDoesNothingWhenDisabled) {
    g_keyboard_RememberOctavesAndVolumes = FALSE;
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->octave = 4;
    ai->volume = 11;

    int oct = -99, vol = -99;
    instruments.RememberOctaveAndVolume(kInstr, oct, vol);

    EXPECT_EQ(oct, -99);
    EXPECT_EQ(vol, -99);
}

// --- CheckInstrumentParameters ---
// Clamps 4 cursor/loop-goto fields so they never exceed their corresponding
// length field, after e.g. shortening a table or envelope.

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersIgnoresOutOfRangeIndex) {
    instruments.CheckInstrumentParameters(-1);
    instruments.CheckInstrumentParameters(INSTRSNUM);
    // No crash - nothing further to assert (GetInstrument() guards both).
}

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersClampsEnvGotoToEnvLength) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 3;
    ai->parameters[PAR_ENV_GOTO] = 10;

    instruments.CheckInstrumentParameters(kInstr);

    EXPECT_EQ(ai->parameters[PAR_ENV_GOTO], 3);
}

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersClampsTblGotoToTblLength) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_LENGTH] = 2;
    ai->parameters[PAR_TBL_GOTO] = 10;

    instruments.CheckInstrumentParameters(kInstr);

    EXPECT_EQ(ai->parameters[PAR_TBL_GOTO], 2);
}

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersClampsEditEnvelopeXToEnvLength) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 3;
    ai->editEnvelopeX = 10;

    instruments.CheckInstrumentParameters(kInstr);

    EXPECT_EQ(ai->editEnvelopeX, 3);
}

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersClampsEditNoteTableCursorPosToTblLength) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_LENGTH] = 2;
    ai->editNoteTableCursorPos = 10;

    instruments.CheckInstrumentParameters(kInstr);

    EXPECT_EQ(ai->editNoteTableCursorPos, 2);
}

TEST_F(InstrumentsCoreTest, CheckInstrumentParametersLeavesValuesUnchangedWhenWithinBounds) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 5;
    ai->parameters[PAR_ENV_GOTO] = 2;
    ai->parameters[PAR_TBL_LENGTH] = 5;
    ai->parameters[PAR_TBL_GOTO] = 2;
    ai->editEnvelopeX = 2;
    ai->editNoteTableCursorPos = 2;

    instruments.CheckInstrumentParameters(kInstr);

    EXPECT_EQ(ai->parameters[PAR_ENV_GOTO], 2);
    EXPECT_EQ(ai->parameters[PAR_TBL_GOTO], 2);
    EXPECT_EQ(ai->editEnvelopeX, 2);
    EXPECT_EQ(ai->editNoteTableCursorPos, 2);
}

// --- RecalculateFlag ---
// Computes displayHintFlags from the envelope (rows 0..PAR_ENV_LENGTH) and
// the AUDCTL parameter range.

TEST_F(InstrumentsCoreTest, RecalculateFlagIgnoresOutOfRangeIndex) {
    instruments.RecalculateFlag(-1);
    instruments.RecalculateFlag(INSTRSNUM);
    // No crash - nothing further to assert (GetInstrument() guards both).
}

TEST_F(InstrumentsCoreTest, RecalculateFlagSetsFilterFlagWhenEnvelopeHasFilter) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->envelope[0][EnvelopeParameter::FILTER] = 1;

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_FILTER);
    EXPECT_FALSE(ai->displayHintFlags & IF_BASS16);
    EXPECT_FALSE(ai->displayHintFlags & IF_PORTAMENTO);
}

TEST_F(InstrumentsCoreTest, RecalculateFlagSetsBass16FlagWhenDistortionIsSix) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 6;

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_BASS16);
}

TEST_F(InstrumentsCoreTest, RecalculateFlagSetsPortamentoFlagWhenEnvelopeHasPortamento) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->envelope[0][EnvelopeParameter::PORTAMENTO] = 1;

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_PORTAMENTO);
}

TEST_F(InstrumentsCoreTest, RecalculateFlagSetsAudctlFlagWhenAnyAudctlParameterIsSet) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_AUDCTL_JOIN_1_2] = 1;

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_AUDCTL);
}

// Autofilter takes priority over Bass16 (RMT 1.28 driver only) - both would
// independently set their own flag, but the Bass16 bit gets cleared again
// when Filter is also set.
TEST_F(InstrumentsCoreTest, RecalculateFlagFilterTakesPriorityOverBass16WhenBothSet) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->envelope[0][EnvelopeParameter::FILTER] = 1;
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 6;

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_FILTER);
    EXPECT_FALSE(ai->displayHintFlags & IF_BASS16);
}

TEST_F(InstrumentsCoreTest, RecalculateFlagChecksEnvelopeRowsUpToEnvLengthInclusive) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 2;
    ai->envelope[2][EnvelopeParameter::FILTER] = 1; // last row still in range

    instruments.RecalculateFlag(kInstr);

    EXPECT_TRUE(ai->displayHintFlags & IF_FILTER);
}

// --- CalculateNotEmpty ---

TEST_F(InstrumentsCoreTest, CalculateNotEmptyReturnsFalseForOutOfRangeIndex) {
    EXPECT_FALSE(instruments.CalculateNotEmpty(-1));
    EXPECT_FALSE(instruments.CalculateNotEmpty(INSTRSNUM));
}

TEST_F(InstrumentsCoreTest, CalculateNotEmptyReturnsFalseForFreshlyConstructedInstrument) {
    EXPECT_FALSE(instruments.CalculateNotEmpty(kInstr));
}

TEST_F(InstrumentsCoreTest, CalculateNotEmptyReturnsTrueWhenEnvelopeHasNonZeroValue) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->envelope[0][EnvelopeParameter::X] = 5;

    EXPECT_TRUE(instruments.CalculateNotEmpty(kInstr));
}

TEST_F(InstrumentsCoreTest, CalculateNotEmptyReturnsTrueWhenAnyParameterIsNonZero) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_DELAY] = 3;

    EXPECT_TRUE(instruments.CalculateNotEmpty(kInstr));
}

TEST_F(InstrumentsCoreTest, CalculateNotEmptyIgnoresEnvelopeRowsBeyondEnvLength) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_ENV_LENGTH] = 0; // only row 0 is checked
    ai->envelope[5][EnvelopeParameter::X] = 9; // out of the checked range

    EXPECT_FALSE(instruments.CalculateNotEmpty(kInstr));
}

// --- GetNote ---

TEST_F(InstrumentsCoreTest, GetNoteReturnsMinusOneForOutOfRangeIndex) {
    EXPECT_EQ(instruments.GetNote(-1, 10), -1);
    EXPECT_EQ(instruments.GetNote(INSTRSNUM, 10), -1);
}

TEST_F(InstrumentsCoreTest, GetNoteReturnsNoteUnshiftedWhenTableTypeIsNotZero) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1; // frequencies, not notes - no shift applied
    ai->noteTable[0] = 5;

    EXPECT_EQ(instruments.GetNote(kInstr, 10), 10);
}

TEST_F(InstrumentsCoreTest, GetNoteShiftsByNoteTableZeroWhenTableTypeIsZero) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 0;
    ai->noteTable[0] = 5;

    EXPECT_EQ(instruments.GetNote(kInstr, 10), 15);
}

TEST_F(InstrumentsCoreTest, GetNoteReturnsMinusOneForInvalidShiftedNote) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 0;
    ai->noteTable[0] = 100; // shifts note 0 -> 100, well past CNotes::NOTESNUM (61)

    EXPECT_EQ(instruments.GetNote(kInstr, 0), -1);
}

// --- GetFrequency ---
// Reads a byte from g_Atari's memory at an offset selected by the
// instrument's envelope[0] distortion value. g_Atari is a real, cheap,
// already-linked global (see AtariStub.cpp) - GetByteAt()/SetByteAt() are
// plain array accessors with no hazard of their own, so this needed no
// special test-only seam, unlike CTuning::InitTuning()'s guarded globals.

// GetFrequency() asks CFrequencyTables, which reads the table out of the
// driver's own dispatch table, so these tests put a SYNTHETIC dispatch table in
// g_Atari's memory - one that sends every distortion somewhere else than the
// real driver does - and then assert that GetFrequency follows it. That is the
// property worth pinning here: it must read the driver's choice rather than a
// map of its own. The real drivers' mappings are pinned once, against the
// shipped binaries, in FrequencyTablesTests.cpp.
class InstrumentFrequencyTest : public InstrumentsCoreTest {
  protected:
    // Deliberately not the driver's own mapping: distortion $00 -> the gritty
    // page, $02 -> buzzy, and so on backwards, so a hardcoded map could not
    // pass these tests.
    static constexpr byte kPageMsbs[8] = {0xB4, 0xB3, 0xB2, 0xB1, 0xB0, 0xB4, 0xB3, 0xB2};

    void SetUp() override {
        for (int i = 0; i < 8; i++) {
            g_Atari.SetByteAt(RMTPLAYR_FRQTAB_HI_TAB + i * 2, kPageMsbs[i]);
            g_Atari.SetByteAt(RMTPLAYR_DISTORTION_TAB + i * 2, i * 2 * 0x10); // the AUDC half, unused here
        }
    }

    void TearDown() override {
        InstrumentsCoreTest::TearDown();
        // g_Atari is shared with the other fixtures that use it, so leave no
        // dispatch table and no table bytes behind.
        g_Atari.ClearMemory();
    }

    MemoryAddress Table(int distortion) const {
        return (MemoryAddress)(kPageMsbs[distortion >> 1] << 8);
    }
};

TEST_F(InstrumentFrequencyTest, ReturnsMinusOneForOutOfRangeIndex) {
    EXPECT_EQ(instruments.GetFrequency(-1, 0, TrackerDriverVersion::PATCH16), -1);
    EXPECT_EQ(instruments.GetFrequency(INSTRSNUM, 0, TrackerDriverVersion::PATCH16), -1);
}

TEST_F(InstrumentFrequencyTest, ReturnsMinusOneForOutOfRangeNote) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1; // no shift

    EXPECT_EQ(instruments.GetFrequency(kInstr, -1, TrackerDriverVersion::PATCH16), -1);
    EXPECT_EQ(instruments.GetFrequency(kInstr, CNotes::NOTESNUM, TrackerDriverVersion::PATCH16), -1);
}

// Each distortion reads the 64 kHz table of the page the driver's dispatch
// table names for it. Until plan 38 these offsets were 64/128/192 from $B000 -
// the RMT 1.28 one-page layout, which in the Patch 16 six-page layout all land
// inside the Poly5 page, so every distortion returned a Poly5 value.
TEST_F(InstrumentFrequencyTest, ReadsTheSixtyFourKhzTableOfThePageTheDriverNamesForTheDistortion) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1; // no shift

    for (int distortion = 0x00; distortion <= 0x0E; distortion += 2) {
        if (distortion == 0x06) {
            continue; // 16-bit, covered by its own test below
        }
        const byte expected = (byte)(0x10 + distortion);
        g_Atari.SetByteAt(Table(distortion) + 5, expected);

        ai->envelope[0][EnvelopeParameter::DISTORTION] = distortion;

        EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), expected) << "distortion " << distortion;
    }
}

TEST_F(InstrumentFrequencyTest, ReturnsMinusOneWhenNoDriverSuppliesADispatchTable) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1;
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x0A;
    g_Atari.ClearMemory(); // no driver loaded: no table to read

    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), -1);
}

TEST_F(InstrumentFrequencyTest, ReadsTheFifteenKhzTableWhenTheInstrumentSetsThatAudctlBit) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1;
    ai->parameters[PAR_AUDCTL_15KHZ] = 1;
    g_Atari.SetByteAt(RMTPLAYR_FRQTABPURE_15KHZ + 5, 66);
    g_Atari.SetByteAt(RMTPLAYR_FRQTABBUZZY_15KHZ + 5, 77);

    // check_15khz: distortion C and above take the buzzy table.
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x00;
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), 66);
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x0C;
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), 77);
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x0E;
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), 77);
}

TEST_F(InstrumentFrequencyTest, ReturnsMinusOneForDistortionSixBecauseItPlaysSixteenBit) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1;
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x06;

    // BASS16: a 16-bit frequency, which no single AUDF byte describes.
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), -1);
}

// The version, not the bytes, picks the layout: the same note, the same
// distortion and two dispatch tables in memory at once give two answers.
TEST_F(InstrumentFrequencyTest, TheDriverVersionDecidesWhichLayoutIsRead) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 1; // no shift
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x0A;

    // A synthetic v1-family table beside the Patch 16 one SetUp() installed,
    // sending distortion $0A to frqtab+$40 rather than the real +$80.
    for (int i = 0; i < 8; i++) {
        g_Atari.SetByteAt(CFrequencyTables::LEGACY_TABBEGANDDISTOR + i * 2, 0x40);
        g_Atari.SetByteAt(CFrequencyTables::LEGACY_TABBEGANDDISTOR + 1 + i * 2, 0xA0);
    }
    g_Atari.SetByteAt(CFrequencyTables::LEGACY_FRQTAB + 0x40 + 5, 99);
    g_Atari.SetByteAt(Table(0x0A) + 5, 11);

    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), 11);
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::UNPATCHED), 99);
    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::NONE), -1);
}

TEST_F(InstrumentFrequencyTest, ShiftsNoteByNoteTableZeroWhenTableTypeIsZero) {
    TInstrument* ai = instruments.GetInstrument(kInstr);
    ai->parameters[PAR_TBL_TYPE] = 0;
    ai->noteTable[0] = 5;
    ai->envelope[0][EnvelopeParameter::DISTORTION] = 0x00;
    g_Atari.SetByteAt(Table(0x00) + 10, 42); // note 5 shifted by 5 -> 10

    EXPECT_EQ(instruments.GetFrequency(kInstr, 5, TrackerDriverVersion::PATCH16), 42);
}
