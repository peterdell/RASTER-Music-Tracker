#include "gtest/gtest.h"

#include "SAPFile.h"
#include "Script.h"

// The script parser (Script.cpp, no MFC) - the same cases as the Java port's
// ScriptParserTest, so both implementations read a script the same way.

TEST(ScriptParserTest, CommandsArgumentsAndOptions) {
    std::vector<TScriptCommand> commands = CScriptParser::Parse({
        "# a comment",
        "",
        "OPEN Delta.rmt",
        "export sap \"My Song.sap\" Author=\"Raster / C.P.U.\" subsongs=0 # trailing comment",
        "  echo done  ",
    });
    ASSERT_EQ(commands.size(), 3u);

    EXPECT_EQ(commands[0].line, 3);
    EXPECT_EQ(commands[0].name, "open");
    ASSERT_EQ(commands[0].arguments.size(), 1u);
    EXPECT_EQ(commands[0].arguments[0], "Delta.rmt");
    EXPECT_TRUE(commands[0].options.empty());

    const TScriptCommand& e = commands[1];
    EXPECT_EQ(e.line, 4);
    ASSERT_EQ(e.arguments.size(), 2u);
    EXPECT_EQ(e.arguments[0], "sap");
    EXPECT_EQ(e.arguments[1], "My Song.sap");
    EXPECT_EQ(e.options.size(), 2u);
    EXPECT_EQ(e.GetOption("author", "?"), "Raster / C.P.U.");
    EXPECT_EQ(e.GetOption("subsongs", "?"), "0");
    EXPECT_EQ(e.GetOption("name", "dflt"), "dflt");
    EXPECT_TRUE(e.HasOption("author"));
    EXPECT_FALSE(e.HasOption("name"));

    EXPECT_EQ(commands[2].GetArgument(0), "done");
    EXPECT_EQ(commands[2].GetArgument(5), "");
}

TEST(ScriptParserTest, QuotesEscapesAndEqualsSignsInArguments) {
    TScriptCommand c;
    ASSERT_TRUE(CScriptParser::ParseLine(1, "echo \"say \\\"hi\\\"\" a=b\\c \"x=y\" path=C:\\dir\\file.rmt", c));
    ASSERT_EQ(c.arguments.size(), 2u);
    EXPECT_EQ(c.arguments[0], "say \"hi\""); // \" inside quotes
    EXPECT_EQ(c.arguments[1], "x=y"); // a quoted token is never an option
    EXPECT_EQ(c.GetOption("a", ""), "b\\c"); // backslashes outside quotes are plain characters
    EXPECT_EQ(c.GetOption("path", ""), "C:\\dir\\file.rmt");

    TScriptCommand empty;
    ASSERT_TRUE(CScriptParser::ParseLine(1, "export xex a.xex text=\"\"", empty));
    EXPECT_TRUE(empty.HasOption("text"));
    EXPECT_EQ(empty.GetOption("text", "?"), "");

    TScriptCommand blank;
    EXPECT_FALSE(CScriptParser::ParseLine(1, "   # only a comment", blank));
}

TEST(ScriptParserTest, ErrorsCarryTheLineNumber) {
    try {
        CScriptParser::Parse({ "open a.rmt", "export sap \"unterminated" });
        FAIL() << "a missing quote must throw";
    } catch (const CScriptError& e) {
        EXPECT_EQ(e.GetLine(), 2);
        EXPECT_EQ(e.GetLocatedMessage().rfind("line 2: ", 0), 0u);
    }
    TScriptCommand c;
    EXPECT_THROW(CScriptParser::ParseLine(1, "name=value", c), CScriptError);
    EXPECT_THROW(CScriptParser::ParseLine(7, "export sap a.sap author=x author=y", c), CScriptError);
}

TEST(ScriptParserTest, SplitLinesAcceptsEveryLineEnd) {
    std::vector<std::string> lines = CScriptParser::SplitLines("one\r\ntwo\nthree\rfour");
    ASSERT_EQ(lines.size(), 4u);
    EXPECT_EQ(lines[0], "one");
    EXPECT_EQ(lines[1], "two");
    EXPECT_EQ(lines[2], "three");
    EXPECT_EQ(lines[3], "four");
    EXPECT_EQ(CScriptParser::SplitLines("").size(), 0u);
    EXPECT_EQ(CScriptParser::SplitLines("x\n").size(), 1u);
}

// CSAPFile::ParseSubsongs - the SAP export dialog's "Subsongs" line, now
// shared with the script runner: hex songline numbers separated by anything.

TEST(SAPFileParseSubsongsTest, CountsHexNumbersSeparatedByAnything) {
    EXPECT_EQ(CSAPFile::ParseSubsongs(""), 0);
    EXPECT_EQ(CSAPFile::ParseSubsongs("0"), 1);
    EXPECT_EQ(CSAPFile::ParseSubsongs("0 10 1F"), 3);
    EXPECT_EQ(CSAPFile::ParseSubsongs("0,a;ff"), 3); // lower case, any separators
    EXPECT_EQ(CSAPFile::ParseSubsongs("   "), 0);
}
