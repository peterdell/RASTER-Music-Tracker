#include "gtest/gtest.h"

#include "Instruments.h"

extern int g_tracks4_8; // TODO Move out (see Instruments.cpp/IO_Instruments.cpp)

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
// (Instruments.cpp - see plans/BROADER_SURVEY_PLAN.md's "cheapest win"
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
