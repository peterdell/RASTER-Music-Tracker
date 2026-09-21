#include "gtest/gtest.h"

#include "RmtCommandLineInfo.h"

TEST(RmtCommandLineInfoTest, NoParamsLeavesNeitherFileSpecified) {
    CRmtCommandLineInfo info;
    EXPECT_FALSE(info.IsScriptFileSpecified());
    EXPECT_FALSE(info.IsTestFileSpecified());
}

TEST(RmtCommandLineInfoTest, ScriptSwitchSetsScriptFilePath) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("SCRIPT:test-script.txt"), TRUE, TRUE);

    EXPECT_TRUE(info.IsScriptFileSpecified());
    EXPECT_STREQ(info.GetScriptFilePath(), "test-script.txt");
    EXPECT_FALSE(info.IsTestFileSpecified());
}

TEST(RmtCommandLineInfoTest, TestSwitchSetsTestFilePath) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("TEST:song.rmt"), TRUE, TRUE);

    EXPECT_TRUE(info.IsTestFileSpecified());
    EXPECT_STREQ(info.GetTestFilePath(), "song.rmt");
    EXPECT_FALSE(info.IsScriptFileSpecified());
}

TEST(RmtCommandLineInfoTest, SwitchNameIsCaseInsensitive) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("script:lower.txt"), TRUE, TRUE);

    EXPECT_TRUE(info.IsScriptFileSpecified());
    EXPECT_STREQ(info.GetScriptFilePath(), "lower.txt");
}

TEST(RmtCommandLineInfoTest, SwitchWithoutColonHasEmptyValue) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("SCRIPT"), TRUE, TRUE);

    EXPECT_TRUE(info.IsScriptFileSpecified());
    EXPECT_STREQ(info.GetScriptFilePath(), "");
}

TEST(RmtCommandLineInfoTest, UnrecognizedFlagLeavesBothFilesUnspecified) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("SOMETHINGELSE:value"), TRUE, TRUE);

    EXPECT_FALSE(info.IsScriptFileSpecified());
    EXPECT_FALSE(info.IsTestFileSpecified());
}

TEST(RmtCommandLineInfoTest, PlainFilenameParamDoesNotSetCustomFlags) {
    CRmtCommandLineInfo info;
    info.ParseParam(_T("song.rmt"), FALSE, TRUE);

    EXPECT_FALSE(info.IsScriptFileSpecified());
    EXPECT_FALSE(info.IsTestFileSpecified());
}
