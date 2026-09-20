#include "gtest/gtest.h"

#include "StringUtility.h"

TEST(StringUtilityTest, MatchesSameCase) {
    EXPECT_TRUE(CStringUtility::EndsWithNoCase("song.rmt", ".rmt"));
}

TEST(StringUtilityTest, MatchesDifferentCase) {
    EXPECT_TRUE(CStringUtility::EndsWithNoCase("song.RMT", ".rmt"));
    EXPECT_TRUE(CStringUtility::EndsWithNoCase("song.rmt", ".RMT"));
}

TEST(StringUtilityTest, DoesNotMatchDifferentSuffix) {
    EXPECT_FALSE(CStringUtility::EndsWithNoCase("song.rmt", ".rti"));
}

TEST(StringUtilityTest, EmptySuffixAlwaysMatches) {
    EXPECT_TRUE(CStringUtility::EndsWithNoCase("song.rmt", ""));
    EXPECT_TRUE(CStringUtility::EndsWithNoCase("", ""));
}

TEST(StringUtilityTest, SuffixLongerThanStringDoesNotMatch) {
    EXPECT_FALSE(CStringUtility::EndsWithNoCase("rmt", "song.rmt"));
}
