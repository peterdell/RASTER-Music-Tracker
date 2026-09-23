#include "gtest/gtest.h"

#include "lzss_sap.h"

#include <vector>

// SAP-R register frame layout: AUDF0,AUDC0,AUDF1,AUDC1,AUDF2,AUDC2,AUDF3,AUDC3,AUDCTL

TEST(OptimiseAudcTest, ZeroVolumeClearsDistortionBits) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0, 0x30, 0, 0, 0, 0, 0, 0, 0}; // AUDC0: dist=0x30, vol=0
    compressor.Optimise_AUDC(buf);
    EXPECT_EQ(buf[1], 0x00);
}

TEST(OptimiseAudcTest, NoiseTypeBitClearedWhenBit5DistortionSet) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0, 0xE5, 0, 0, 0, 0, 0, 0, 0}; // AUDC0: dist=0xE0 (bit5 set), vol=5
    compressor.Optimise_AUDC(buf);
    EXPECT_EQ(buf[1], 0xA5); // bit 0x40 cleared
}

TEST(OptimiseAudcTest, DistortionAtOrAboveF0IsLeftUntouched) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0, 0xF5, 0, 0, 0, 0, 0, 0, 0}; // dist=0xF0, vol=5 (two-tone filter range)
    compressor.Optimise_AUDC(buf);
    EXPECT_EQ(buf[1], 0xF5);
}

TEST(OptimiseAudcTlTest, MutingAllChannelsClearsFilterAndClockBits) {
    CCompressLzss compressor;
    // AUDC0/1/3 muted (volume nibble 0), AUDC2 has volume 5, AUDCTL all bits set.
    uint8_t buf[9] = {0, 0x00, 0, 0x00, 0, 0x05, 0, 0x00, 0xFF};
    compressor.Optimise_AUDCTL(buf);
    EXPECT_EQ(buf[8], 0xA9);
}

TEST(OptimiseAudcTlTest, NoMutedChannelsLeavesAudctlUntouched) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0, 0x01, 0, 0x02, 0, 0x03, 0, 0x04, 0xFF};
    compressor.Optimise_AUDCTL(buf);
    EXPECT_EQ(buf[8], 0xFF);
}

TEST(OptimiseAudfTest, MutedChannelsWithNoConflictingAudctlBitsAreZeroed) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0xAB, 0x00, 0xCD, 0x00, 0xEF, 0x00, 0x12, 0x00, 0x00};
    compressor.Optimise_AUDF(buf);
    EXPECT_EQ(buf[0], 0);
    EXPECT_EQ(buf[2], 0);
    EXPECT_EQ(buf[4], 0);
    EXPECT_EQ(buf[6], 0);
}

TEST(OptimiseAudfTest, MutedChannelsPreserveAudfWhenAudctlNeedsThem) {
    // AUDCTL bit 0x04 keeps AUDF0 (case 0) and AUDF2 (case 2), but not
    // AUDF1 (case 1, needs 0x02/0x10) or AUDF3 (case 3, needs 0x02/0x08).
    CCompressLzss compressor;
    uint8_t buf[9] = {0xAB, 0x00, 0xCD, 0x00, 0xEF, 0x00, 0x12, 0x00, 0x04};
    compressor.Optimise_AUDF(buf);
    EXPECT_EQ(buf[0], 0xAB);
    EXPECT_EQ(buf[2], 0);
    EXPECT_EQ(buf[4], 0xEF);
    EXPECT_EQ(buf[6], 0);
}

TEST(OptimiseAudfTest, NonZeroVolumeLeavesAudfUntouched) {
    CCompressLzss compressor;
    uint8_t buf[9] = {0xAB, 0x01, 0xCD, 0x02, 0xEF, 0x03, 0x12, 0x04, 0x00};
    compressor.Optimise_AUDF(buf);
    EXPECT_EQ(buf[0], 0xAB);
    EXPECT_EQ(buf[2], 0xCD);
    EXPECT_EQ(buf[4], 0xEF);
    EXPECT_EQ(buf[6], 0x12);
}

// CCompressLzss::LZSS_SAP() is the only entry point that exercises the LZSS
// matching/bit-packing itself. Expected byte arrays below were captured by
// running the actual implementation once ("golden master"), not
// hand-derived, since that logic is too intricate to safely hand-verify.

namespace {

std::vector<uint8_t> Compress(const std::vector<uint8_t>& src, SAPROptimization optimisation) {
    CCompressLzss compressor;
    std::vector<uint8_t> dest(src.size() * 3 + 64, 0);
    int compressedSize = compressor.LZSS_SAP(src.data(), src.size(), dest.data(), optimisation);
    dest.resize(compressedSize);
    return dest;
}

} // namespace

TEST(LzssTest, AllSilenceFrames) {
    // 3 identical all-zero 9-byte SAP-R register frames.
    std::vector<uint8_t> src(27, 0);

    auto compressed = Compress(src, SAPROptimization::NONE);

    std::vector<uint8_t> expected{
        0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFE, 0x02};
    EXPECT_EQ(compressed, expected);
}

TEST(LzssTest, RepeatingAndVaryingFrames) {
    // 4 frames: the 1st, 2nd and 4th are identical, the 3rd differs slightly,
    // giving the LZSS matcher a real (short) match to find.
    std::vector<uint8_t> src{
        10,
        0x81,
        20,
        0x82,
        30,
        0x83,
        40,
        0x84,
        0x00,
        10,
        0x81,
        20,
        0x82,
        30,
        0x83,
        40,
        0x84,
        0x00,
        11,
        0x81,
        21,
        0x82,
        31,
        0x83,
        41,
        0x84,
        0x00,
        10,
        0x81,
        20,
        0x82,
        30,
        0x83,
        40,
        0x84,
        0x00,
    };

    auto compressed = Compress(src, SAPROptimization::NONE);

    std::vector<uint8_t> expected{
        0xAB, 0x00, 0x84, 0x28, 0x83, 0x1E, 0x82, 0x14, 0x81, 0x0A, 0xFF, 0x28,
        0x1E, 0x14, 0x0A, 0x29, 0x1F, 0x15, 0x0B, 0x0F, 0x28, 0x1E, 0x14, 0x0A};
    EXPECT_EQ(compressed, expected);
}

TEST(LzssTest, AllOptimizationsAppliedToMutedChannels) {
    // All 4 channels muted (volume nibble 0) and AUDCTL all-bits-set: the
    // AUDC/AUDCTL/AUDF optimisation passes should all fire before
    // compression, so the compressed output differs from the equivalent
    // input run through SAPROptimization::NONE.
    std::vector<uint8_t> src{
        0xAB,
        0xE0,
        0xCD,
        0x30,
        0xEF,
        0x00,
        0x12,
        0x00,
        0xFF,
        0xAB,
        0xE0,
        0xCD,
        0x30,
        0xEF,
        0x00,
        0x12,
        0x00,
        0xFF,
    };

    auto compressed = Compress(src, SAPROptimization::ALL);

    std::vector<uint8_t> expected{
        0xFF, 0xA1, 0x00, 0x00, 0x00, 0xEF, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00};
    EXPECT_EQ(compressed, expected);
}
