#pragma once

#include <map>
#include <stdexcept>
#include <string>
#include <vector>

#include "Memory.h"

// An RMT module of format version 1, as content (plans/60_RMT_MODULE_CONTAINER_PLAN.md,
// M6 - the C++ counterpart of RmtModule.java): the header values, the
// instruments and tracks by number, and the song lines - doc/rmt_format.md's
// structs, one type each. The module is free of addresses: the header's four
// table addresses and the pointer tables follow from the layout and the load
// address, so Write() computes them for any load address (which is how a
// module is relocated) and Parse() validates them.
//
// The layout Write() produces is the one RMT has always written: the 16-byte
// header, the instruments table (a word per instrument number up to the
// highest one present, 0 for an absent one), the track addresses tables (the
// low bytes, then the high bytes), the instrument structs in number order,
// the track event bytes in number order, then the song lines. Parse()
// accepts any arrangement the pointers describe within the module's bytes.
// Format version 0 (the pre-release instrument layout) is not a version this
// container represents; Parse() rejects it with that reason.
//
// The CSong conversions live in CRmtModuleCodec, the file around the module
// in CRmtModuleFile.
class CRmtModule {
public:
    typedef std::vector<unsigned char> Bytes;

    // A byte sequence that is not a valid RMT module of format version 1, or
    // a song that cannot be encoded as one - with the reason in what().
    class FormatException : public std::runtime_error {
    public:
        explicit FormatException(const std::string& message) : std::runtime_error(message) {}
    };

    static constexpr int HEADER_LENGTH = 16;
    // The longest track length the header can express ($00 means 256).
    static constexpr int MAX_TRACK_LENGTH = 256;
    // The highest instrument speed the player accepts (player calls per frame).
    static constexpr int MAX_INSTRUMENT_SPEED = 8;
    static constexpr const char* SIGNATURE = "RMT";
    static constexpr int SIGNATURE_LENGTH = 3;

    // The offsets within the header (doc/rmt_format.md, "Module Header Struct").
    struct Offset {
        static constexpr int CHANNELS = 3;
        static constexpr int TRACK_LENGTH = 4;
        static constexpr int SONG_SPEED = 5;
        static constexpr int INSTRUMENT_SPEED = 6;
        static constexpr int FORMAT_VERSION = 7;
        static constexpr int INSTRUMENTS_TABLE = 8;
        static constexpr int TRACKS_LOW_TABLE = 10;
        static constexpr int TRACKS_HIGH_TABLE = 12;
        static constexpr int SONG = 14;
    };

    // The channel counts the format knows, the header's digit: one POKEY's
    // four channels or two POKEYs' eight.
    struct Channels {
        static constexpr int MONO = 4;
        static constexpr int STEREO = 8;
    };

    // The format version this container represents (RMTFormatVersion::V1).
    static constexpr int FORMAT_VERSION = 1;
    static constexpr int MAX_SONG_SPEED = CMemory::MAX_BYTE;
    // The most instruments a module can hold - the editor's INSTRSNUM.
    static constexpr int MAX_INSTRUMENTS = 64;
    // The most tracks a module can hold - the editor's TRACKSNUM: $fe and $ff are the sequence table's markers.
    static constexpr int MAX_TRACKS = 254;
    // The most song lines a module can hold - the editor's SONGLEN: a "go to" target is a byte.
    static constexpr int MAX_SONG_LINES = 256;

    // One line of the track sequence table: the track number of each
    // channel, or a "go to" line (the marker $fe, the target line and the
    // target's address word; the address follows from the song data's own
    // address, so only the target line is content).
    class SongLine {
    public:
        static constexpr int GO_TO_MARKER = 0xfe;
        // The track number that means "no track" on a channel.
        static constexpr int EMPTY = 0xff;
        // The bytes of a "go to" line; a stereo line pads it to the channel count unless it is the last line.
        static constexpr int GO_TO_LENGTH = 4;
        struct Offset {
            static constexpr int TARGET = 1;
            static constexpr int ADDRESS = 2;
        };

        static SongLine Tracks(const std::vector<int>& trackNumbers);
        static SongLine GoTo(int target);

        bool IsGoTo() const { return m_goTo >= 0; }
        int GoToTarget() const;
        int TrackNumber(int channel) const;
        bool IsEmpty(int channel) const { return TrackNumber(channel) >= MAX_TRACKS; }
        // Whether any channel plays a track.
        bool HasTracks() const;
        int GetChannels() const;
        bool operator==(const SongLine& other) const { return m_goTo == other.m_goTo && m_trackNumbers == other.m_trackNumbers; }

    private:
        SongLine(const std::vector<int>& trackNumbers, int goTo) : m_trackNumbers(trackNumbers), m_goTo(goTo) {}
        std::vector<int> m_trackNumbers; // empty for a "go to" line
        int m_goTo; // -1 for a track line
    };

    // One track: the event stream as bytes, validated by a walker that knows
    // the four event kinds. A track's extent is not in the bytes: the module
    // knows it from the next track's address.
    class Track {
    public:
        static constexpr int EVENT_MASK = 0x3f;
        static constexpr int HIGH_BITS_MASK = 0xc0;
        static constexpr int MAX_NOTE = 0x3c;
        static constexpr int VOLUME_ONLY = 0x3d;
        static constexpr int PAUSE = 0x3e;
        static constexpr int CONTROL = 0x3f;
        static constexpr int CONTROL_SPEED = 0x00;
        static constexpr int CONTROL_JUMP = 0x80;
        static constexpr int CONTROL_END = 0xc0;
        static constexpr int MAX_LENGTH = 256;
        static constexpr int HIGH_BITS_SHIFT = 6;
        static constexpr int EVENT_LENGTH = 1;
        static constexpr int EVENT_WITH_PARAMETER_LENGTH = 2;

        // The track with the event bytes data[offset..offset+length), validated.
        static Track Of(const unsigned char* data, int offset, int length);
        static Track Of(const Bytes& bytes) { return Of(bytes.data(), 0, static_cast<int>(bytes.size())); }

        int GetLineCount() const { return m_lineCount; }
        int GetLength() const { return static_cast<int>(m_bytes.size()); }
        const Bytes& GetBytes() const { return m_bytes; }
        int Write(unsigned char* mem, int offset) const;
        bool operator==(const Track& other) const { return m_bytes == other.m_bytes; }

    private:
        Track(const Bytes& bytes, int lineCount) : m_bytes(bytes), m_lineCount(lineCount) {}
        static int Walk(const Bytes& bytes);
        Bytes m_bytes;
        int m_lineCount;
    };

    // One instrument: the twelve parameter bytes as typed fields, the table
    // of notes and the envelope. The four pointer bytes (tlen, tgo, elen,
    // ego) follow from the lengths and the loop positions, so ToBytes()
    // computes them and Parse() validates them.
    class Instrument {
    public:
        static constexpr int PARAMETERS_LENGTH = 12;
        // The editor's NOTE_TABLE_MAX_LEN.
        static constexpr int MAX_NOTE_TABLE_ENTRIES = 32;
        // The editor's ENVELOPE_MAX_COLUMNS.
        static constexpr int MAX_ENVELOPE_ENTRIES = 48;
        static constexpr int ENVELOPE_ENTRY_LENGTH = 3;
        static constexpr int MAX_TABLE_SPEED = 0x3f;
        static constexpr int MAX_VIBRATO = 3;
        static constexpr int MAX_VOLUME = 0x0f;
        static constexpr int MAX_LENGTH = PARAMETERS_LENGTH + MAX_NOTE_TABLE_ENTRIES + MAX_ENVELOPE_ENTRIES * ENVELOPE_ENTRY_LENGTH;
        struct Offset {
            static constexpr int TABLE_END = 0;
            static constexpr int TABLE_LOOP = 1;
            static constexpr int ENVELOPE_END = 2;
            static constexpr int ENVELOPE_LOOP = 3;
            static constexpr int TABLE_CONTROL = 4;
            static constexpr int AUDCTL = 5;
            static constexpr int VOLUME_SLIDE = 6;
            static constexpr int VOLUME_MINIMUM = 7;
            static constexpr int DELAY = 8;
            static constexpr int VIBRATO = 9;
            static constexpr int FREQUENCY_SHIFT = 10;
            static constexpr int UNUSED = 11;
        };
        static constexpr int TABLE_MODE_SHIFT = 6;
        static constexpr int TABLE_TYPE_SHIFT = 7;
        static constexpr int TABLE_MODE_SET = 0;
        static constexpr int TABLE_MODE_ADD = 1;
        static constexpr int TABLE_TYPE_NOTES = 0;
        static constexpr int TABLE_TYPE_FREQUENCIES = 1;
        static constexpr int HIGH_NIBBLE_SHIFT = 4;
        static constexpr int LOW_NIBBLE_MASK = 0x0f;
        static constexpr int BIT_MASK = 0x01;

        // An envelope entry: the three bytes volume, control and XY.
        struct Envelope {
            struct Offset {
                static constexpr int VOLUME = 0;
                static constexpr int CONTROL = 1;
                static constexpr int XY = 2;
            };
            static constexpr int DISTORTION_MASK = 0x0e;
            static constexpr int COMMAND_SHIFT = 4;
            static constexpr int COMMAND_MASK = 0x07;
            static constexpr int FILTER_SHIFT = 7;

            int volume;
            int control;
            int xy;

            int VolumeLeft() const { return volume & LOW_NIBBLE_MASK; }
            int VolumeRight() const { return volume >> HIGH_NIBBLE_SHIFT; }
            int Portamento() const { return control & BIT_MASK; }
            int Distortion() const { return control & DISTORTION_MASK; }
            int Command() const { return (control >> COMMAND_SHIFT) & COMMAND_MASK; }
            int Filter() const { return control >> FILTER_SHIFT; }
            int X() const { return xy >> HIGH_NIBBLE_SHIFT; }
            int Y() const { return xy & LOW_NIBBLE_MASK; }
            bool operator==(const Envelope& other) const { return volume == other.volume && control == other.control && xy == other.xy; }
        };

        Instrument(const Bytes& noteTable, int tableLoop, const std::vector<Envelope>& envelope, int envelopeLoop, int tableSpeed, int tableMode, int tableType, int audctl,
            int volumeSlide, int volumeMinimum, int delay, int vibrato, int frequencyShift, int unused);

        // Parses the struct at mem[offset...], of which available bytes belong to the module.
        static Instrument Parse(const unsigned char* mem, int offset, int available);

        int GetLength() const;
        Bytes ToBytes() const;
        int Write(unsigned char* mem, int offset) const;

        const Bytes& GetNoteTable() const { return m_noteTable; }
        int GetTableLoop() const { return m_tableLoop; }
        const std::vector<Envelope>& GetEnvelope() const { return m_envelope; }
        int GetEnvelopeLoop() const { return m_envelopeLoop; }
        int GetTableSpeed() const { return m_tableSpeed; }
        int GetTableMode() const { return m_tableMode; }
        int GetTableType() const { return m_tableType; }
        int GetAudctl() const { return m_audctl; }
        int GetVolumeSlide() const { return m_volumeSlide; }
        int GetVolumeMinimum() const { return m_volumeMinimum; }
        int GetDelay() const { return m_delay; }
        int GetVibrato() const { return m_vibrato; }
        int GetFrequencyShift() const { return m_frequencyShift; }
        int GetUnused() const { return m_unused; }
        bool operator==(const Instrument& other) const { return ToBytes() == other.ToBytes(); }

    private:
        Bytes m_noteTable;
        int m_tableLoop;
        std::vector<Envelope> m_envelope;
        int m_envelopeLoop;
        int m_tableSpeed;
        int m_tableMode;
        int m_tableType;
        int m_audctl;
        int m_volumeSlide;
        int m_volumeMinimum;
        int m_delay;
        int m_vibrato;
        int m_frequencyShift;
        int m_unused;
    };

    // Where RMT's layout puts each part of the module, as offsets from the
    // module's first byte (RmtModule.Layout in the Java port).
    struct Layout {
        int instrumentsTable;
        int tracksLowTable;
        int tracksHighTable;
        int instrumentData; // where the tables end
        int trackData; // where the instruments' data ends
        int song; // where the tracks' data ends
        int end; // the first offset after the module, its length
        std::map<int, int> instruments; // each present instrument's offset, by number
        std::map<int, int> tracks; // each present track's offset, by number

        int InstrumentEntries() const { return (tracksLowTable - instrumentsTable) / CMemory::WORD_LENGTH; }
        int TrackEntries() const { return tracksHighTable - tracksLowTable; }
    };

    CRmtModule(int channels, int trackLength, int songSpeed, int instrumentSpeed, int formatVersion, const std::map<int, Instrument>& instruments, const std::map<int, Track>& tracks,
        const std::vector<SongLine>& songLines);

    // Parses the module at mem[fromAddr..endAddr), validating the header, the
    // pointers and every struct; the reason for a rejection is the
    // exception's message. mem holds CMemory::SIZE bytes.
    static CRmtModule Parse(const unsigned char* mem, int fromAddr, int endAddr);

    // The song data's length: every line takes a byte per channel, except a
    // "go to" line that is the last line, which takes only its four bytes.
    int SongDataLength() const;
    int GetLength() const;
    Layout GetLayout() const;
    // Writes the module at mem[loadAddress...] (mem holds memSize bytes);
    // returns the address of the first byte after the module.
    int Write(unsigned char* mem, int memSize, int loadAddress) const;
    // The module as bytes, laid out for loadAddress.
    Bytes ToBytes(int loadAddress) const;

    int GetChannels() const { return m_channels; }
    int GetTrackLength() const { return m_trackLength; }
    int GetSongSpeed() const { return m_songSpeed; }
    int GetInstrumentSpeed() const { return m_instrumentSpeed; }
    int GetFormatVersion() const { return m_formatVersion; }
    const std::map<int, Instrument>& GetInstruments() const { return m_instruments; }
    const std::map<int, Track>& GetTracks() const { return m_tracks; }
    const std::vector<SongLine>& GetSongLines() const { return m_songLines; }
    bool operator==(const CRmtModule& other) const;

private:
    int InstrumentTableEntries() const { return m_instruments.empty() ? 0 : m_instruments.rbegin()->first + 1; }
    int TrackTableEntries() const { return m_tracks.empty() ? 0 : m_tracks.rbegin()->first + 1; }
    static std::vector<SongLine> ParseSongLines(const unsigned char* mem, int songAddr, int endAddr, int channels);
    void WriteSongLines(unsigned char* mem, int songAddr) const;

    int m_channels;
    int m_trackLength;
    int m_songSpeed;
    int m_instrumentSpeed;
    int m_formatVersion;
    std::map<int, Instrument> m_instruments;
    std::map<int, Track> m_tracks;
    std::vector<SongLine> m_songLines;
};
