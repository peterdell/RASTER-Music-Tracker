#include "gtest/gtest.h"

#include <filesystem>
#include <fstream>
#include <iterator>
#include <memory>
#include <sstream>

#include "AtariIO.h"
#include "Instruments.h"
#include "RmtExporter.h"
#include "RmtModule.h"
#include "RmtModuleCodec.h"
#include "RmtModuleFile.h"
#include "Song.h"
#include "Tracks.h"

extern CTracks g_Tracks;
extern CInstruments g_Instruments;

// CRmtModule, CRmtModuleFile and CRmtModuleCodec (plans/60_RMT_MODULE_CONTAINER_PLAN.md,
// M6 - RmtModuleTest.java in the Java port): the spec of doc/rmt_format.md
// made executable against every .rmt in test-resources/songs, and the
// container proven equivalent to the program's codec (CSong::MakeModule()/
// DecodeModule(), CRmtExporter::ExportAsRMT()) byte for byte.

namespace {

constexpr int LOAD = CRmtModuleFile::DEFAULT_LOAD_ADDRESS;
constexpr int TRACK_LENGTH = 64;
constexpr int SONG_SPEED = 6;

std::filesystem::path SongsRoot() {
    // __FILE__ is src/cpp/test/RmtModuleTests.cpp; the fixtures are in the
    // repository's test-resources/songs.
    std::filesystem::path root = std::filesystem::path(__FILE__).parent_path().parent_path().parent_path().parent_path() / "test-resources" / "songs";
    if (std::filesystem::exists(root)) {
        return root;
    }
    std::filesystem::path dir = std::filesystem::current_path();
    for (int i = 0; i < 6; i++, dir = dir.parent_path()) {
        if (std::filesystem::exists(dir / "test-resources" / "songs")) {
            return dir / "test-resources" / "songs";
        }
    }
    return root;
}

const std::filesystem::path DELTA = std::filesystem::path("rmt128") / "delta" / "Delta.rmt";
const std::filesystem::path REBB_STARS = std::filesystem::path("rmt128") / "rebbstars" / "RebbStars.rmt";
const std::filesystem::path WHY_DO_YOU_DANCE_WITH_ME = std::filesystem::path("rmt134") / "why_do_you_dance_with_me" / "Why_Do_You_Dance_With_Me.rmt";
const std::filesystem::path CAPTAIN_FUTURE_THEME = std::filesystem::path("rmt134") / "captain_future_theme" / "Captain_Future_Theme.rmt";
const std::filesystem::path ISSUE_30_TWO_TONE_FILTER = std::filesystem::path("rmt134") / "issue_30_two_tone_filter" / "Makary_Two_Tone_Filter.rmt";
const std::filesystem::path ISSUE_5_STRIPPED_SFX_EXPORT = std::filesystem::path("rmt134") / "issue_5_stripped_sfx_export" / "testsfx.rmt";

const std::vector<std::filesystem::path> ALL = {DELTA, REBB_STARS, CAPTAIN_FUTURE_THEME, ISSUE_30_TWO_TONE_FILTER, ISSUE_5_STRIPPED_SFX_EXPORT, WHY_DO_YOU_DANCE_WITH_ME};
const std::vector<std::filesystem::path> STRIPPED = {DELTA, REBB_STARS, ISSUE_5_STRIPPED_SFX_EXPORT, WHY_DO_YOU_DANCE_WITH_ME};

CRmtModule::Bytes ReadBytes(const std::filesystem::path& fixture) {
    std::ifstream in(SongsRoot() / fixture, std::ios::binary);
    return CRmtModule::Bytes(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
}

CRmtModuleFile Read(const std::filesystem::path& fixture) {
    return CRmtModuleFile::Read(ReadBytes(fixture));
}

bool IsStrippedFixture(const std::filesystem::path& fixture) {
    return std::find(STRIPPED.begin(), STRIPPED.end(), fixture) != STRIPPED.end();
}

} // namespace

class RmtModuleTest : public ::testing::Test {
  protected:
    CSong song;

    void SetUp() override {
        song.SetChannels(CSong::Channels::MONO);
        g_Tracks.SetMaxTrackLength(TRACK_LENGTH);
        g_Tracks.InitTracks();
        g_Instruments.InitInstruments();
        for (int i = 0; i < INSTRSNUM; i++) {
            memset(g_Instruments.GetInstrument(i), 0, sizeof(TInstrument));
        }
        Blank(song);
    }

    static void Blank(CSong& s) {
        auto* songArr = s.GetSong();
        auto* songGoArr = s.GetSongGo();
        for (int line = 0; line < SONGLEN; line++) {
            for (int col = 0; col < SONGTRACKS; col++) {
                (*songArr)[line][col] = -1;
            }
            (*songGoArr)[line] = -1;
        }
    }

    // The fixture into the song, the way the program has always decoded it.
    void Decode(const std::filesystem::path& fixture, CSong& target) {
        CRmtModule::Bytes data = ReadBytes(fixture);
        std::istringstream in(std::string(data.begin(), data.end()));
        std::vector<unsigned char> mem(CMemory::SIZE);
        MemoryAddress fromAddr = 0, toAddr = 0;
        ASSERT_GT(CAtariIO::LoadBinaryBlock(in, mem.data(), fromAddr, toAddr), 0);
        BYTE instrumentFlags[INSTRSNUM];
        BYTE trackFlags[TRACKSNUM];
        ASSERT_GT(target.DecodeModule(mem.data(), fromAddr, toAddr + 1, instrumentFlags, trackFlags), 0) << fixture.string();
    }

    static CRmtModule::Bytes MakeModule(CSong& s, int address, SongIOType iotype) {
        std::vector<unsigned char> mem(CMemory::SIZE);
        BYTE instrumentFlags[INSTRSNUM];
        BYTE trackFlags[TRACKSNUM];
        int end = s.MakeModule(mem.data(), address, iotype, instrumentFlags, trackFlags);
        EXPECT_GT(end, 0) << "MakeModule failed";
        return CRmtModule::Bytes(mem.begin() + address, mem.begin() + (end > 0 ? end : address));
    }
};

TEST_F(RmtModuleTest, EveryFixtureReadsAndWritesBackByteForByte) {
    for (const auto& fixture : ALL) {
        CRmtModule::Bytes data = ReadBytes(fixture);
        ASSERT_FALSE(data.empty()) << fixture.string();
        CRmtModuleFile file = CRmtModuleFile::Read(data);
        EXPECT_EQ(data, file.Write()) << fixture.string();
        EXPECT_EQ(IsStrippedFixture(fixture), file.IsStripped()) << fixture.string();
    }
}

TEST_F(RmtModuleTest, TheFixturesAreWhatTheirReadmesSay) {
    CRmtModuleFile delta = Read(DELTA);
    EXPECT_EQ(CRmtModule::Channels::MONO, delta.GetModule().GetChannels());
    EXPECT_EQ(CRmtModuleFile::DEFAULT_LOAD_ADDRESS, delta.GetLoadAddress());
    EXPECT_TRUE(delta.IsStripped()) << "Raster's own export carries no names";
    CRmtModuleFile captain = Read(CAPTAIN_FUTURE_THEME);
    EXPECT_EQ(0u, captain.GetSongName()->rfind("Buddy - Captain", 0)) << *captain.GetSongName();
    EXPECT_EQ(captain.GetModule().GetInstruments().size(), captain.GetInstrumentNames()->size());
    CRmtModuleFile sfx = Read(ISSUE_5_STRIPPED_SFX_EXPORT);
    EXPECT_EQ(0x6800, sfx.GetLoadAddress()) << "exported at $6800, per its README";
    EXPECT_EQ(10u, sfx.GetModule().GetInstruments().size());
    // "No tracks or song lines" in the README means: nothing plays - eleven
    // one-event audition tracks, and one empty line looping to itself.
    EXPECT_EQ(11u, sfx.GetModule().GetTracks().size());
    for (const auto& entry : sfx.GetModule().GetTracks()) {
        EXPECT_EQ(1, entry.second.GetLineCount());
    }
    int empty = CRmtModule::SongLine::EMPTY;
    std::vector<CRmtModule::SongLine> expected = {CRmtModule::SongLine::Tracks({empty, empty, empty, empty}), CRmtModule::SongLine::GoTo(0)};
    EXPECT_TRUE(expected == sfx.GetModule().GetSongLines());
    CRmtModuleFile why = Read(WHY_DO_YOU_DANCE_WITH_ME);
    EXPECT_EQ(CRmtModule::Channels::STEREO, why.GetModule().GetChannels());
    EXPECT_EQ(4, why.GetModule().GetInstrumentSpeed());
}

TEST_F(RmtModuleTest, WritingAtAnotherAddressRelocatesTheModule) {
    for (const auto& fixture : ALL) {
        CRmtModule module = Read(fixture).GetModule();
        CRmtModule::Bytes relocated = module.ToBytes(0x6000); // any address other than the default
        EXPECT_EQ(module.GetLength(), static_cast<int>(relocated.size()));
        std::vector<unsigned char> mem(CMemory::SIZE);
        std::copy(relocated.begin(), relocated.end(), mem.begin() + 0x6000);
        EXPECT_TRUE(module == CRmtModule::Parse(mem.data(), 0x6000, 0x6000 + static_cast<int>(relocated.size()))) << fixture.string();
        EXPECT_NE(relocated, module.ToBytes(LOAD)) << "the pointers differ by address";
    }
}

TEST_F(RmtModuleTest, FromSongWritesExactlyWhatMakeModuleWrites) {
    for (const auto& fixture : ALL) {
        Blank(song);
        Decode(fixture, song);
        for (SongIOType iotype : {SongIOType::RMT, SongIOType::RMTSTRIPPED}) {
            CRmtModule module = CRmtModuleCodec::FromSong(song, iotype);
            for (int address : {LOAD, 0x2e00}) {
                EXPECT_EQ(MakeModule(song, address, iotype), module.ToBytes(address)) << fixture.string() << " at $" << std::hex << address;
            }
        }
    }
}

TEST_F(RmtModuleTest, ToSongDecodesExactlyAsDecodeModuleDoes) {
    for (const auto& fixture : ALL) {
        Blank(song);
        Decode(fixture, song);
        CRmtModule::Bytes viaDecodeModule = MakeModule(song, LOAD, SongIOType::RMT);
        int channels = song.GetChannels();
        TInfo before;
        song.GetSongInfoPars(&before);

        Blank(song);
        CRmtModuleCodec::ToSong(Read(fixture).GetModule(), song);
        EXPECT_EQ(channels, song.GetChannels()) << fixture.string();
        TInfo after;
        song.GetSongInfoPars(&after);
        EXPECT_EQ(before.mainspeed, after.mainspeed) << fixture.string();
        EXPECT_EQ(before.instrspeed, after.instrspeed) << fixture.string();
        EXPECT_EQ(viaDecodeModule, MakeModule(song, LOAD, SongIOType::RMT)) << fixture.string();
    }
}

TEST_F(RmtModuleTest, FileFromSongWritesExactlyWhatExportAsRmtWrites) {
    for (const auto& fixture : ALL) {
        Blank(song);
        Decode(fixture, song);
        auto desc = std::make_unique<TExportDescription>();
        memset(desc.get(), 0, sizeof(TExportDescription));
        desc->targetAddrOfModule = LOAD;
        desc->firstByteAfterModule = song.MakeModule(desc->mem, LOAD, SongIOType::RMT, desc->instrumentSavedFlags, desc->trackSavedFlags);
        ASSERT_GT(desc->firstByteAfterModule, 0);
        std::ostringstream out;
        ASSERT_TRUE(CRmtExporter::ExportAsRMT(song, out, desc.get()));
        std::string expected = out.str();
        EXPECT_EQ(CRmtModule::Bytes(expected.begin(), expected.end()), CRmtModuleCodec::FileFromSong(song, LOAD).Write()) << fixture.string();
    }
}

TEST_F(RmtModuleTest, NamesRoundTripThroughTheSong) {
    CRmtModuleFile file = Read(ISSUE_30_TWO_TONE_FILTER);
    EXPECT_EQ("Makary - Two-Tone Filter", *file.GetSongName());
    CRmtModuleCodec::ToSong(file.GetModule(), song);
    CRmtModuleCodec::NamesToSong(file, song);
    EXPECT_STREQ(file.GetSongName()->c_str(), (LPCSTR)song.GetName());
    CRmtModuleFile again = CRmtModuleCodec::FileFromSong(song, file.GetLoadAddress());
    EXPECT_EQ(*file.GetSongName(), *again.GetSongName());
    EXPECT_EQ(*file.GetInstrumentNames(), *again.GetInstrumentNames());
}

TEST_F(RmtModuleTest, TheInstrumentStructIsTyped) {
    CRmtModule module = Read(DELTA).GetModule();
    const CRmtModule::Instrument& first = module.GetInstruments().begin()->second;
    EXPECT_EQ(first.GetLength(), static_cast<int>(first.ToBytes().size()));
    EXPECT_EQ(CRmtModule::Instrument::PARAMETERS_LENGTH + static_cast<int>(first.GetNoteTable().size())
                  + static_cast<int>(first.GetEnvelope().size()) * CRmtModule::Instrument::ENVELOPE_ENTRY_LENGTH,
        first.GetLength());
    EXPECT_LT(first.GetTableLoop(), static_cast<int>(first.GetNoteTable().size()));
    EXPECT_LT(first.GetEnvelopeLoop(), static_cast<int>(first.GetEnvelope().size()));
    CRmtModule::Instrument::Envelope envelope{0xa5, 0x9b, 0x3c};
    EXPECT_EQ(5, envelope.VolumeLeft());
    EXPECT_EQ(0xa, envelope.VolumeRight());
    EXPECT_EQ(1, envelope.Portamento());
    EXPECT_EQ(0xa, envelope.Distortion());
    EXPECT_EQ(1, envelope.Command());
    EXPECT_EQ(1, envelope.Filter());
    EXPECT_EQ(3, envelope.X());
    EXPECT_EQ(0xc, envelope.Y());
}

// The format's own limits (the container stays free of the editor) are the editor's.
TEST_F(RmtModuleTest, TheFormatLimitsAreTheEditors) {
    EXPECT_EQ(INSTRSNUM, CRmtModule::MAX_INSTRUMENTS);
    EXPECT_EQ(TRACKSNUM, CRmtModule::MAX_TRACKS);
    EXPECT_EQ(SONGLEN, CRmtModule::MAX_SONG_LINES);
    EXPECT_EQ(SONGTRACKS, CRmtModule::Channels::STEREO);
    EXPECT_EQ(CSong::Channels::MONO, CRmtModule::Channels::MONO);
    EXPECT_EQ(CSong::Channels::STEREO, CRmtModule::Channels::STEREO);
    EXPECT_EQ(static_cast<int>(RMTFormatVersion::V1), CRmtModule::FORMAT_VERSION);
    EXPECT_EQ(SONG_NAME_MAX_LEN, CRmtModuleFile::SONG_NAME_MAX_LENGTH);
    EXPECT_EQ(INSTRUMENT_NAME_MAX_LEN, CRmtModuleFile::INSTRUMENT_NAME_MAX_LENGTH);
    EXPECT_EQ(static_cast<int>(RAM_SIZE), CMemory::SIZE);
    EXPECT_EQ(ATARI_MAX_TRACK_LENGTH, CRmtModule::Track::MAX_LENGTH);
}

TEST_F(RmtModuleTest, AModuleMayEndExactlyAtTheLastAddress) {
    CRmtModule module = Read(DELTA).GetModule();
    int lastFit = CMemory::MAX_ADDRESS - module.GetLength() + 1;
    EXPECT_EQ(CMemory::MAX_ADDRESS, CRmtModuleFile::Stripped(module, lastFit).GetLoadAddress() + module.GetLength() - 1);
    EXPECT_THROW(CRmtModuleFile::Stripped(module, lastFit + 1), CRmtModule::FormatException);
    EXPECT_THROW(module.ToBytes(lastFit + 1), CRmtModule::FormatException);
}

TEST_F(RmtModuleTest, TheTrackWalkerKnowsTheFourEventKinds) {
    const int shift = CRmtModule::Track::HIGH_BITS_SHIFT;
    unsigned char noteC1Instrument2Volume15 = (15 & 3) << shift, noteParameter = (2 << 2) | (15 >> 2);
    unsigned char volumeOnly9 = ((9 & 3) << shift) | CRmtModule::Track::VOLUME_ONLY, volumeParameter = 9 >> 2;
    unsigned char pause3 = (3 << shift) | CRmtModule::Track::PAUSE;
    unsigned char pauseCounted = CRmtModule::Track::PAUSE, pauseCount7 = 7;
    unsigned char speed = CRmtModule::Track::CONTROL | CRmtModule::Track::CONTROL_SPEED, speed5 = 5;
    unsigned char jump = CRmtModule::Track::CONTROL | CRmtModule::Track::CONTROL_JUMP;
    unsigned char end = CRmtModule::Track::CONTROL | CRmtModule::Track::CONTROL_END;
    CRmtModule::Track track = CRmtModule::Track::Of({noteC1Instrument2Volume15, noteParameter, volumeOnly9, volumeParameter, pause3, pauseCounted, pauseCount7, speed, speed5, end});
    EXPECT_EQ(1 + 1 + 3 + 7, track.GetLineCount());
    // a track that fills its length has no end marker
    EXPECT_EQ(2, CRmtModule::Track::Of({0, 0, CRmtModule::Track::VOLUME_ONLY, 0}).GetLineCount());
    // a jump ends the track
    EXPECT_EQ(1, CRmtModule::Track::Of({0, 0, jump, 0}).GetLineCount());
    EXPECT_EQ(0, CRmtModule::Track::Of({pauseCounted, 0}).GetLineCount()); // a pause of 0 lines ends the track
}

namespace {

void ExpectRejected(const std::vector<unsigned char>& mem, int length, int offset, int value, const std::string& reason) {
    std::vector<unsigned char> copy = mem;
    copy[LOAD + offset] = static_cast<unsigned char>(value);
    try {
        CRmtModule::Parse(copy.data(), LOAD, LOAD + length);
        ADD_FAILURE() << "not rejected: " << reason;
    } catch (const CRmtModule::FormatException& e) {
        EXPECT_NE(std::string::npos, std::string(e.what()).find(reason)) << e.what();
    }
}

} // namespace

TEST_F(RmtModuleTest, MalformedBytesAreRejectedWithTheReason) {
    unsigned char nonEvent = CRmtModule::Track::CONTROL | (1 << CRmtModule::Track::HIGH_BITS_SHIFT); // $7f: the one control kind that does not exist
    unsigned char end = CRmtModule::Track::CONTROL | CRmtModule::Track::CONTROL_END;
    unsigned char jump = CRmtModule::Track::CONTROL | CRmtModule::Track::CONTROL_JUMP;
    EXPECT_THROW(CRmtModule::Track::Of({nonEvent}), CRmtModule::FormatException) << "$7f is not an event";
    EXPECT_THROW(CRmtModule::Track::Of({0}), CRmtModule::FormatException) << "a note cut short";
    EXPECT_THROW(CRmtModule::Track::Of({end, 0}), CRmtModule::FormatException) << "a byte after the end";
    EXPECT_THROW(CRmtModule::Track::Of({jump, 5}), CRmtModule::FormatException) << "a jump outside the track";

    TInfo info;
    song.GetSongInfoPars(&info);
    info.mainspeed = SONG_SPEED;
    info.instrspeed = 1;
    song.SetSongInfoPars(&info);
    TTrack* track = g_Tracks.GetTrack(0);
    track->note[0] = 12;
    track->instr[0] = 0;
    track->volume[0] = 15;
    track->len = TRACK_LENGTH;
    (*song.GetSong())[0][0] = 0;
    (*song.GetSongGo())[1] = 0;
    CRmtModule::Bytes module = CRmtModuleCodec::FromSong(song, SongIOType::RMT).ToBytes(LOAD);
    std::vector<unsigned char> mem(CMemory::SIZE);
    std::copy(module.begin(), module.end(), mem.begin() + LOAD);
    int length = static_cast<int>(module.size());
    CRmtModule::Parse(mem.data(), LOAD, LOAD + length); // valid as built
    ExpectRejected(mem, length, 0, 'X', "RMT");
    ExpectRejected(mem, length, CRmtModule::Offset::CHANNELS, '6', "RMT4 or RMT8");
    ExpectRejected(mem, length, CRmtModule::Offset::SONG_SPEED, 0, "song speed is 0");
    ExpectRejected(mem, length, CRmtModule::Offset::INSTRUMENT_SPEED, CRmtModule::MAX_INSTRUMENT_SPEED + 1, "instrument speed 9");
    ExpectRejected(mem, length, CRmtModule::Offset::FORMAT_VERSION, 0, "Format version 0");
    ExpectRejected(mem, length, CRmtModule::Offset::FORMAT_VERSION, CRmtModule::FORMAT_VERSION + 1, "format version 2");
    ExpectRejected(mem, length, CRmtModule::Offset::SONG, CMemory::MAX_BYTE, "track sequence table"); // the low byte alone points past the module
}

TEST_F(RmtModuleTest, AStereoGoToAsTheLastLineTakesFourBytesLikeRmtWrites) {
    CRmtModule module(CRmtModule::Channels::STEREO, TRACK_LENGTH, SONG_SPEED, 1, CRmtModule::FORMAT_VERSION, {}, {},
        {CRmtModule::SongLine::Tracks({0, 1, 2, 3, 4, 5, 6, 7}), CRmtModule::SongLine::GoTo(0)});
    EXPECT_EQ(CRmtModule::Channels::STEREO + CRmtModule::SongLine::GO_TO_LENGTH, module.SongDataLength());
    CRmtModule::Bytes bytes = module.ToBytes(LOAD);
    EXPECT_EQ(CRmtModule::HEADER_LENGTH + module.SongDataLength(), static_cast<int>(bytes.size()));
    std::vector<unsigned char> mem(CMemory::SIZE);
    std::copy(bytes.begin(), bytes.end(), mem.begin() + LOAD);
    EXPECT_TRUE(module == CRmtModule::Parse(mem.data(), LOAD, LOAD + static_cast<int>(bytes.size())));
}

TEST(MemoryTest, WordsAreStoredLowByteFirst) {
    unsigned char mem[4] = {};
    CMemory::WriteWord(mem, 1, 0xbeef);
    EXPECT_EQ(0xef, mem[1]);
    EXPECT_EQ(0xbe, mem[2]);
    EXPECT_EQ(0xbeef, CMemory::Word(mem, 1));
    EXPECT_EQ(0xef, CMemory::UnsignedByte(mem, 1));
    EXPECT_EQ(0xef, CMemory::LowByte(0xbeef));
    EXPECT_EQ(0xbe, CMemory::HighByte(0xbeef));
    unsigned char split[4] = {0x34, 0, 0, 0x12};
    EXPECT_EQ(0x1234, CMemory::SplitWord(split, 0, 3));
}
