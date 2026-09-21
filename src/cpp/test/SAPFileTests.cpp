#include "gtest/gtest.h"

#include "SAPFile.h"

#include <sstream>

// CSAPFile::Export() only calls ThrowRuntimeException() (an empty/invalid
// TYPE) on error paths, and that macro shows a blocking MessageBox and then
// calls exit(2) - not a catchable C++ exception. Tests here deliberately
// never exercise those paths (always set a valid "B" or "R" type), since
// doing so would hang or kill the whole test binary. Flagged in
// plans/NOTES.md as a real testability hazard, not something to trigger.

TEST(SAPFileTest, ExportTypeBWithInitAndPlayer) {
    CSAPFile sap;
    sap.SetAuthor(" AtariGuy ");
    sap.SetName(" Cool Song ");
    sap.SetDate("21/09/2026");
    sap.SetType("B");
    sap.SetSongs(2);
    sap.SetDefaultSong(1);
    sap.SetInitAddress(0x4000);
    sap.SetPlayerAddress(0x4700);

    std::ostringstream out;
    sap.Export(out);

    // Characterization: DEFSONG prints m_songs, not m_defsong - an existing
    // bug in CSAPFile::Export(), not something introduced by this test.
    EXPECT_EQ(out.str(),
        "SAP\x0d\x0a"
        "AUTHOR \" AtariGuy\"\x0d\x0a"
        "NAME \" Cool Song\"\x0d\x0a"
        "DATE \"21/09/2026\"\x0d\x0a"
        "TYPE B\x0d\x0a"
        "SONGS 2\x0d\x0a"
        "DEFSONG 2\x0d\x0a"
        "INIT 4000\x0d\x0a"
        "PLAYER 4700\x0d\x0a"
        "\x0d\x0a");
}

TEST(SAPFileTest, ExportTypeRIgnoresInitAndPlayer) {
    CSAPFile sap;
    sap.SetAuthor("RCoder");
    sap.SetName("RSong");
    sap.SetDate("01/01/2000");
    sap.SetType("R");
    sap.SetStereo(true);
    // Type "R" never emits INIT/PLAYER, even though they're set here.
    sap.SetInitAddress(0x9999);
    sap.SetPlayerAddress(0x8888);

    std::ostringstream out;
    sap.Export(out);

    EXPECT_EQ(out.str(),
        "SAP\x0d\x0a"
        "AUTHOR \"RCoder\"\x0d\x0a"
        "NAME \"RSong\"\x0d\x0a"
        "DATE \"01/01/2000\"\x0d\x0a"
        "TYPE R\x0d\x0a"
        "STEREO\x0d\x0a"
        "\x0d\x0a");
}

TEST(SAPFileTest, NormalizeReplacesQuotesWithApostrophes) {
    CSAPFile sap;
    sap.SetAuthor("Say \"Hi\"");
    sap.SetName("N");
    sap.SetDate("D");
    sap.SetType("R");

    std::ostringstream out;
    sap.Export(out);

    EXPECT_NE(out.str().find("AUTHOR \"Say 'Hi'\"\x0d\x0a"), std::string::npos);
}

TEST(SAPFileTest, GettersReturnWhatWasSet) {
    CSAPFile sap;
    sap.SetAuthor("A");
    sap.SetName("B");
    sap.SetDate("C");
    sap.SetSongs(5);
    sap.SetDefaultSong(2);
    sap.SetStereo(true);
    sap.SetNTSC(true);
    sap.SetType("B");
    sap.SetInitAddress(0x1234);
    sap.SetPlayerAddress(0x5678);

    EXPECT_STREQ(sap.GetAuthor(), "A");
    EXPECT_STREQ(sap.GetName(), "B");
    EXPECT_STREQ(sap.GetDate(), "C");
    EXPECT_EQ(sap.GetSongs(), 5);
    EXPECT_EQ(sap.GetDefaultSong(), 2);
    EXPECT_TRUE(sap.IsStereo());
    EXPECT_TRUE(sap.IsNTSC());
    EXPECT_STREQ(sap.GetType(), "B");
    EXPECT_EQ(sap.GetInitAddress(), 0x1234);
    EXPECT_EQ(sap.GetPlayerAddress(), 0x5678);
}
