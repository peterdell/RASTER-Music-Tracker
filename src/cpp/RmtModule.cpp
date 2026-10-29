#include "StdAfx.h"

#include "RmtModule.h"

#include <cstring>
#include <sstream>

namespace {

std::string Hex(int value) {
    std::ostringstream s;
    s << std::hex << value;
    return s.str();
}

void RequireInside(int address, int min, int max, const std::string& what) {
    if (address < min || address > max) {
        throw CRmtModule::FormatException("The " + what + " at $" + Hex(address) + " lies outside $" + Hex(min) + "-$" + Hex(max) + ".");
    }
}

void RequireRange(int value, int max, const std::string& what) {
    if (value < 0 || value > max) {
        throw CRmtModule::FormatException("The " + what + " " + std::to_string(value) + " is not 0-" + std::to_string(max) + ".");
    }
}

void RequireByte(int value, const std::string& what) {
    RequireRange(value, CMemory::MAX_BYTE, what);
}

} // namespace

// ---- CRmtModule ----

CRmtModule::CRmtModule(int channels, int trackLength, int songSpeed, int instrumentSpeed, int formatVersion, const std::map<int, Instrument>& instruments,
    const std::map<int, Track>& tracks, const std::vector<SongLine>& songLines)
    : m_channels(channels), m_trackLength(trackLength), m_songSpeed(songSpeed), m_instrumentSpeed(instrumentSpeed), m_formatVersion(formatVersion), m_instruments(instruments),
      m_tracks(tracks), m_songLines(songLines) {
    if (channels != Channels::MONO && channels != Channels::STEREO) {
        throw FormatException("The module has " + std::to_string(channels) + " channels, not 4 (RMT4) or 8 (RMT8).");
    }
    if (trackLength < 1 || trackLength > MAX_TRACK_LENGTH) {
        throw FormatException("The track length " + std::to_string(trackLength) + " is not 1-" + std::to_string(MAX_TRACK_LENGTH) + ".");
    }
    if (songSpeed < 1 || songSpeed > MAX_SONG_SPEED) {
        throw FormatException("The song speed " + std::to_string(songSpeed) + " is not 1-" + std::to_string(MAX_SONG_SPEED) + ".");
    }
    if (instrumentSpeed < 1 || instrumentSpeed > MAX_INSTRUMENT_SPEED) {
        throw FormatException("The instrument speed " + std::to_string(instrumentSpeed) + " is not 1-" + std::to_string(MAX_INSTRUMENT_SPEED) + ".");
    }
    if (formatVersion != FORMAT_VERSION) {
        throw FormatException("The format version " + std::to_string(formatVersion) + " is not " + std::to_string(FORMAT_VERSION) + ", the only version this container represents.");
    }
    for (const auto& entry : instruments) {
        if (entry.first < 0 || entry.first >= MAX_INSTRUMENTS) {
            throw FormatException("The instrument number " + std::to_string(entry.first) + " is not 0-" + std::to_string(MAX_INSTRUMENTS - 1) + ".");
        }
    }
    for (const auto& entry : tracks) {
        if (entry.first < 0 || entry.first >= MAX_TRACKS) {
            throw FormatException("The track number " + std::to_string(entry.first) + " is not 0-" + std::to_string(MAX_TRACKS - 1) + ".");
        }
    }
    for (const SongLine& line : songLines) {
        if (!line.IsGoTo() && line.GetChannels() != channels) {
            throw FormatException("A song line has " + std::to_string(line.GetChannels()) + " channels, the module " + std::to_string(channels) + ".");
        }
    }
}

CRmtModule CRmtModule::Parse(const unsigned char* mem, int fromAddr, int endAddr) {
    if (fromAddr < 0 || endAddr > CMemory::SIZE || endAddr - fromAddr < HEADER_LENGTH) {
        throw FormatException("The module is shorter than its " + std::to_string(HEADER_LENGTH) + "-byte header.");
    }
    if (std::memcmp(mem + fromAddr, SIGNATURE, SIGNATURE_LENGTH) != 0) {
        throw FormatException("The module does not start with \"RMT\".");
    }
    int channelByte = CMemory::UnsignedByte(mem, fromAddr + Offset::CHANNELS);
    if (channelByte != '0' + Channels::MONO && channelByte != '0' + Channels::STEREO) {
        throw FormatException(std::string("The header is \"RMT") + static_cast<char>(channelByte) + "\", not RMT4 or RMT8.");
    }
    int channels = channelByte - '0';
    int trackLengthByte = CMemory::UnsignedByte(mem, fromAddr + Offset::TRACK_LENGTH);
    int trackLength = trackLengthByte == 0 ? MAX_TRACK_LENGTH : trackLengthByte;
    int songSpeed = CMemory::UnsignedByte(mem, fromAddr + Offset::SONG_SPEED);
    if (songSpeed < 1) {
        throw FormatException("The song speed is 0.");
    }
    int instrumentSpeed = CMemory::UnsignedByte(mem, fromAddr + Offset::INSTRUMENT_SPEED);
    if (instrumentSpeed < 1 || instrumentSpeed > MAX_INSTRUMENT_SPEED) {
        throw FormatException("The instrument speed " + std::to_string(instrumentSpeed) + " is not 1-" + std::to_string(MAX_INSTRUMENT_SPEED) + ".");
    }
    int formatVersion = CMemory::UnsignedByte(mem, fromAddr + Offset::FORMAT_VERSION);
    if (formatVersion == 0) {
        throw FormatException("Format version 0 (the pre-release instrument layout) is not represented by this container.");
    }
    if (formatVersion > FORMAT_VERSION) {
        throw FormatException("The format version " + std::to_string(formatVersion) + " is above " + std::to_string(FORMAT_VERSION) + ".");
    }
    int ptrInstruments = CMemory::Word(mem, fromAddr + Offset::INSTRUMENTS_TABLE);
    int ptrTracksLow = CMemory::Word(mem, fromAddr + Offset::TRACKS_LOW_TABLE);
    int ptrTracksHigh = CMemory::Word(mem, fromAddr + Offset::TRACKS_HIGH_TABLE);
    int ptrSong = CMemory::Word(mem, fromAddr + Offset::SONG);
    RequireInside(ptrInstruments, fromAddr + HEADER_LENGTH, endAddr, "instruments table");
    RequireInside(ptrTracksLow, ptrInstruments, endAddr, "low-byte track addresses table");
    RequireInside(ptrTracksHigh, ptrTracksLow, endAddr, "high-byte track addresses table");
    RequireInside(ptrSong, ptrTracksHigh, endAddr, "track sequence table");
    if ((ptrTracksLow - ptrInstruments) % CMemory::WORD_LENGTH != 0) {
        throw FormatException("The instruments table has an odd length, " + std::to_string(ptrTracksLow - ptrInstruments) + ".");
    }
    int instrumentCount = (ptrTracksLow - ptrInstruments) / CMemory::WORD_LENGTH;
    int trackCount = ptrTracksHigh - ptrTracksLow;
    if (instrumentCount > MAX_INSTRUMENTS) {
        throw FormatException("The instruments table has " + std::to_string(instrumentCount) + " entries, more than " + std::to_string(MAX_INSTRUMENTS) + ".");
    }
    if (trackCount > MAX_TRACKS) {
        throw FormatException("The track addresses tables have " + std::to_string(trackCount) + " entries, more than " + std::to_string(MAX_TRACKS) + ".");
    }

    std::map<int, Instrument> instruments;
    for (int number = 0; number < instrumentCount; number++) {
        int ptr = CMemory::Word(mem, ptrInstruments + number * CMemory::WORD_LENGTH);
        if (ptr == 0) {
            continue; // an absent instrument
        }
        RequireInside(ptr, fromAddr + HEADER_LENGTH, endAddr - 1, "instrument " + std::to_string(number));
        try {
            instruments.emplace(number, Instrument::Parse(mem, ptr, endAddr - ptr));
        } catch (const FormatException& e) {
            throw FormatException("Instrument " + std::to_string(number) + ": " + e.what());
        }
    }

    std::map<int, Track> tracks;
    for (int number = 0; number < trackCount; number++) {
        int ptr = CMemory::SplitWord(mem, ptrTracksLow + number, ptrTracksHigh + number);
        if (ptr == 0) {
            continue; // an absent track
        }
        // A track's extent is not in its bytes: it ends where the next
        // present track starts, the last one where the song lines start.
        int end = ptrSong;
        for (int next = number + 1; next < trackCount; next++) {
            int nextPtr = CMemory::SplitWord(mem, ptrTracksLow + next, ptrTracksHigh + next);
            if (nextPtr != 0) {
                end = nextPtr;
                break;
            }
        }
        RequireInside(ptr, fromAddr + HEADER_LENGTH, endAddr - 1, "track " + std::to_string(number));
        if (end < ptr || end > endAddr) {
            throw FormatException("Track " + std::to_string(number) + " at $" + Hex(ptr) + " ends at $" + Hex(end) + ", before its start or outside the module.");
        }
        try {
            tracks.emplace(number, Track::Of(mem, ptr, end - ptr));
        } catch (const FormatException& e) {
            throw FormatException("Track " + std::to_string(number) + ": " + e.what());
        }
    }

    return CRmtModule(channels, trackLength, songSpeed, instrumentSpeed, formatVersion, instruments, tracks, ParseSongLines(mem, ptrSong, endAddr, channels));
}

std::vector<CRmtModule::SongLine> CRmtModule::ParseSongLines(const unsigned char* mem, int songAddr, int endAddr, int channels) {
    std::vector<SongLine> lines;
    int pos = songAddr;
    while (pos < endAddr) {
        std::string lineNumber = std::to_string(lines.size());
        if (CMemory::UnsignedByte(mem, pos) == SongLine::GO_TO_MARKER) {
            if (pos + SongLine::GO_TO_LENGTH > endAddr) {
                throw FormatException("The go to line " + lineNumber + " is cut short.");
            }
            int target = CMemory::UnsignedByte(mem, pos + SongLine::Offset::TARGET);
            int ptr = CMemory::Word(mem, pos + SongLine::Offset::ADDRESS);
            if (ptr != ((songAddr + target * channels) & CMemory::MAX_ADDRESS)) {
                throw FormatException("The go to line " + lineNumber + " targets line " + std::to_string(target) + " but addresses $" + Hex(ptr) + ".");
            }
            lines.push_back(SongLine::GoTo(target));
        } else {
            if (pos + channels > endAddr) {
                throw FormatException("The song line " + lineNumber + " is cut short.");
            }
            std::vector<int> numbers(channels);
            for (int channel = 0; channel < channels; channel++) {
                numbers[channel] = CMemory::UnsignedByte(mem, pos + channel);
            }
            lines.push_back(SongLine::Tracks(numbers));
        }
        pos += channels;
    }
    return lines;
}

int CRmtModule::SongDataLength() const {
    if (m_songLines.empty()) {
        return 0;
    }
    int last = static_cast<int>(m_songLines.size()) - 1;
    return m_songLines[last].IsGoTo() ? last * m_channels + SongLine::GO_TO_LENGTH : (last + 1) * m_channels;
}

int CRmtModule::GetLength() const {
    return GetLayout().end;
}

CRmtModule::Layout CRmtModule::GetLayout() const {
    Layout layout;
    layout.instrumentsTable = HEADER_LENGTH;
    layout.tracksLowTable = layout.instrumentsTable + InstrumentTableEntries() * CMemory::WORD_LENGTH;
    layout.tracksHighTable = layout.tracksLowTable + TrackTableEntries();
    layout.instrumentData = layout.tracksHighTable + TrackTableEntries();
    int offset = layout.instrumentData;
    for (const auto& entry : m_instruments) {
        layout.instruments[entry.first] = offset;
        offset += entry.second.GetLength();
    }
    layout.trackData = offset;
    for (const auto& entry : m_tracks) {
        layout.tracks[entry.first] = offset;
        offset += entry.second.GetLength();
    }
    layout.song = offset;
    layout.end = offset + SongDataLength();
    return layout;
}

int CRmtModule::Write(unsigned char* mem, int memSize, int loadAddress) const {
    Layout layout = GetLayout();
    int end = loadAddress + layout.end;
    if (loadAddress < 0 || end - 1 > CMemory::MAX_ADDRESS || end > memSize) {
        throw FormatException("The module of " + std::to_string(layout.end) + " bytes does not fit at $" + Hex(loadAddress) + ": its last byte would lie beyond $" + Hex(CMemory::MAX_ADDRESS) + ".");
    }
    std::memcpy(mem + loadAddress, SIGNATURE, SIGNATURE_LENGTH);
    mem[loadAddress + Offset::CHANNELS] = static_cast<unsigned char>('0' + m_channels);
    mem[loadAddress + Offset::TRACK_LENGTH] = static_cast<unsigned char>(m_trackLength == MAX_TRACK_LENGTH ? 0 : m_trackLength);
    mem[loadAddress + Offset::SONG_SPEED] = static_cast<unsigned char>(m_songSpeed);
    mem[loadAddress + Offset::INSTRUMENT_SPEED] = static_cast<unsigned char>(m_instrumentSpeed);
    mem[loadAddress + Offset::FORMAT_VERSION] = static_cast<unsigned char>(m_formatVersion);

    int ptrInstruments = loadAddress + layout.instrumentsTable;
    int ptrTracksLow = loadAddress + layout.tracksLowTable;
    int ptrTracksHigh = loadAddress + layout.tracksHighTable;
    for (int number = 0; number < layout.InstrumentEntries(); number++) {
        auto offset = layout.instruments.find(number);
        int ptr = 0;
        if (offset != layout.instruments.end()) {
            ptr = loadAddress + offset->second;
            m_instruments.at(number).Write(mem, ptr);
        }
        CMemory::WriteWord(mem, ptrInstruments + number * CMemory::WORD_LENGTH, ptr);
    }
    for (int number = 0; number < layout.TrackEntries(); number++) {
        auto offset = layout.tracks.find(number);
        int ptr = 0;
        if (offset != layout.tracks.end()) {
            ptr = loadAddress + offset->second;
            m_tracks.at(number).Write(mem, ptr);
        }
        mem[ptrTracksLow + number] = CMemory::LowByte(ptr);
        mem[ptrTracksHigh + number] = CMemory::HighByte(ptr);
    }
    int ptrSong = loadAddress + layout.song;
    WriteSongLines(mem, ptrSong);

    CMemory::WriteWord(mem, loadAddress + Offset::INSTRUMENTS_TABLE, ptrInstruments);
    CMemory::WriteWord(mem, loadAddress + Offset::TRACKS_LOW_TABLE, ptrTracksLow);
    CMemory::WriteWord(mem, loadAddress + Offset::TRACKS_HIGH_TABLE, ptrTracksHigh);
    CMemory::WriteWord(mem, loadAddress + Offset::SONG, ptrSong);
    return end;
}

void CRmtModule::WriteSongLines(unsigned char* mem, int songAddr) const {
    int end = songAddr + SongDataLength();
    int pos = songAddr;
    for (const SongLine& line : m_songLines) {
        if (line.IsGoTo()) {
            int target = line.GoToTarget();
            int ptr = (songAddr + target * m_channels) & CMemory::MAX_ADDRESS;
            mem[pos] = static_cast<unsigned char>(SongLine::GO_TO_MARKER);
            mem[pos + SongLine::Offset::TARGET] = static_cast<unsigned char>(target);
            CMemory::WriteWord(mem, pos + SongLine::Offset::ADDRESS, ptr);
            for (int channel = SongLine::GO_TO_LENGTH; channel < m_channels && pos + channel < end; channel++) {
                mem[pos + channel] = static_cast<unsigned char>(SongLine::EMPTY); // the padding of a stereo go to line
            }
        } else {
            for (int channel = 0; channel < m_channels; channel++) {
                mem[pos + channel] = static_cast<unsigned char>(line.TrackNumber(channel));
            }
        }
        pos += m_channels;
    }
}

CRmtModule::Bytes CRmtModule::ToBytes(int loadAddress) const {
    int length = GetLength();
    if (loadAddress < 0 || loadAddress + length - 1 > CMemory::MAX_ADDRESS) {
        throw FormatException("The module of " + std::to_string(length) + " bytes does not fit at $" + Hex(loadAddress) + ": its last byte would lie beyond $" + Hex(CMemory::MAX_ADDRESS) + ".");
    }
    std::vector<unsigned char> mem(CMemory::SIZE);
    Write(mem.data(), CMemory::SIZE, loadAddress);
    return Bytes(mem.begin() + loadAddress, mem.begin() + loadAddress + length);
}

bool CRmtModule::operator==(const CRmtModule& other) const {
    return m_channels == other.m_channels && m_trackLength == other.m_trackLength && m_songSpeed == other.m_songSpeed && m_instrumentSpeed == other.m_instrumentSpeed
        && m_formatVersion == other.m_formatVersion && m_instruments == other.m_instruments && m_tracks == other.m_tracks && m_songLines == other.m_songLines;
}

// ---- SongLine ----

CRmtModule::SongLine CRmtModule::SongLine::Tracks(const std::vector<int>& trackNumbers) {
    if (trackNumbers.size() != Channels::MONO && trackNumbers.size() != Channels::STEREO) {
        throw std::invalid_argument("A song line has 4 or 8 channels, not " + std::to_string(trackNumbers.size()) + ".");
    }
    for (int number : trackNumbers) {
        if (number < 0 || number > CMemory::MAX_BYTE) {
            throw std::invalid_argument("A track number is a byte, not " + std::to_string(number) + ".");
        }
    }
    return SongLine(trackNumbers, -1);
}

CRmtModule::SongLine CRmtModule::SongLine::GoTo(int target) {
    if (target < 0 || target > CMemory::MAX_BYTE) {
        throw std::invalid_argument("A go to target is a byte, not " + std::to_string(target) + ".");
    }
    return SongLine(std::vector<int>(), target);
}

int CRmtModule::SongLine::GoToTarget() const {
    if (!IsGoTo()) {
        throw std::logic_error("Not a go to line.");
    }
    return m_goTo;
}

int CRmtModule::SongLine::TrackNumber(int channel) const {
    if (IsGoTo()) {
        throw std::logic_error("A go to line has no track numbers.");
    }
    return m_trackNumbers.at(channel);
}

bool CRmtModule::SongLine::HasTracks() const {
    if (IsGoTo()) {
        return false;
    }
    for (int number : m_trackNumbers) {
        if (number < MAX_TRACKS) {
            return true;
        }
    }
    return false;
}

int CRmtModule::SongLine::GetChannels() const {
    if (IsGoTo()) {
        throw std::logic_error("A go to line has no channels.");
    }
    return static_cast<int>(m_trackNumbers.size());
}

// ---- Track ----

CRmtModule::Track CRmtModule::Track::Of(const unsigned char* data, int offset, int length) {
    if (length > MAX_LENGTH) {
        throw FormatException("The track has " + std::to_string(length) + " bytes, more than " + std::to_string(MAX_LENGTH) + ".");
    }
    Bytes bytes(data + offset, data + offset + length);
    int lineCount = Walk(bytes);
    return Track(bytes, lineCount);
}

namespace {

void RequireSecondByte(const CRmtModule::Bytes& bytes, int pos, const char* event) {
    if (pos + 1 >= static_cast<int>(bytes.size())) {
        throw CRmtModule::FormatException(std::string("The ") + event + " event at offset " + std::to_string(pos) + " is cut short by the track's end.");
    }
}

int TrackEnd(const CRmtModule::Bytes& bytes, int pos, int line) {
    if (pos != static_cast<int>(bytes.size())) {
        throw CRmtModule::FormatException(std::to_string(bytes.size() - pos) + " byte(s) follow the track's end at offset " + std::to_string(pos - 1) + ".");
    }
    return line;
}

} // namespace

// Walks the events, rejecting an event cut short by the track's end, the
// non-event $7f, and bytes after an end; returns the number of lines the
// events cover.
int CRmtModule::Track::Walk(const Bytes& bytes) {
    int length = static_cast<int>(bytes.size());
    int pos = 0;
    int line = 0;
    while (pos < length) {
        int first = bytes[pos];
        int kind = first & EVENT_MASK;
        int high = first & HIGH_BITS_MASK;
        if (kind <= MAX_NOTE || kind == VOLUME_ONLY) {
            RequireSecondByte(bytes, pos, kind <= MAX_NOTE ? "note" : "volume");
            pos += EVENT_WITH_PARAMETER_LENGTH;
            line++;
        } else if (kind == PAUSE) {
            if (high != 0) {
                line += high >> HIGH_BITS_SHIFT;
                pos += EVENT_LENGTH;
            } else {
                RequireSecondByte(bytes, pos, "pause");
                int count = bytes[pos + 1];
                pos += EVENT_WITH_PARAMETER_LENGTH;
                if (count == 0) {
                    return TrackEnd(bytes, pos, line); // a pause of 0 lines ends the track
                }
                line += count;
            }
        } else if (high == CONTROL_SPEED) {
            RequireSecondByte(bytes, pos, "speed");
            pos += EVENT_WITH_PARAMETER_LENGTH;
        } else if (high == CONTROL_JUMP) {
            RequireSecondByte(bytes, pos, "jump");
            int target = bytes[pos + 1];
            if (target >= length) {
                throw FormatException("The jump at offset " + std::to_string(pos) + " targets offset " + std::to_string(target) + ", outside the track.");
            }
            return TrackEnd(bytes, pos + EVENT_WITH_PARAMETER_LENGTH, line);
        } else if (high == CONTROL_END) {
            return TrackEnd(bytes, pos + EVENT_LENGTH, line);
        } else {
            throw FormatException("Byte $" + Hex(first) + " at offset " + std::to_string(pos) + " is not an event.");
        }
    }
    return line; // a track that fills its whole length has no end marker
}

int CRmtModule::Track::Write(unsigned char* mem, int offset) const {
    std::memcpy(mem + offset, m_bytes.data(), m_bytes.size());
    return offset + GetLength();
}

// ---- Instrument ----

CRmtModule::Instrument::Instrument(const Bytes& noteTable, int tableLoop, const std::vector<Envelope>& envelope, int envelopeLoop, int tableSpeed, int tableMode, int tableType,
    int audctl, int volumeSlide, int volumeMinimum, int delay, int vibrato, int frequencyShift, int unused)
    : m_noteTable(noteTable), m_tableLoop(tableLoop), m_envelope(envelope), m_envelopeLoop(envelopeLoop), m_tableSpeed(tableSpeed), m_tableMode(tableMode), m_tableType(tableType),
      m_audctl(audctl), m_volumeSlide(volumeSlide), m_volumeMinimum(volumeMinimum), m_delay(delay), m_vibrato(vibrato), m_frequencyShift(frequencyShift), m_unused(unused) {
    int tableEntries = static_cast<int>(noteTable.size());
    int envelopeEntries = static_cast<int>(envelope.size());
    if (tableEntries < 1 || tableEntries > MAX_NOTE_TABLE_ENTRIES) {
        throw FormatException("The table of notes has " + std::to_string(tableEntries) + " entries, not 1-" + std::to_string(MAX_NOTE_TABLE_ENTRIES) + ".");
    }
    if (tableLoop < 0 || tableLoop >= tableEntries) {
        throw FormatException("The table loop " + std::to_string(tableLoop) + " is outside the table of " + std::to_string(tableEntries) + " entries.");
    }
    if (envelopeEntries < 1 || envelopeEntries > MAX_ENVELOPE_ENTRIES) {
        throw FormatException("The envelope has " + std::to_string(envelopeEntries) + " entries, not 1-" + std::to_string(MAX_ENVELOPE_ENTRIES) + ".");
    }
    if (envelopeLoop < 0 || envelopeLoop >= envelopeEntries) {
        throw FormatException("The envelope loop " + std::to_string(envelopeLoop) + " is outside the envelope of " + std::to_string(envelopeEntries) + " entries.");
    }
    for (const Envelope& entry : envelope) {
        RequireByte(entry.volume, "volume");
        RequireByte(entry.control, "control");
        RequireByte(entry.xy, "XY");
    }
    RequireRange(tableSpeed, MAX_TABLE_SPEED, "table speed");
    RequireRange(tableMode, TABLE_MODE_ADD, "table mode");
    RequireRange(tableType, TABLE_TYPE_FREQUENCIES, "table type");
    RequireByte(audctl, "AUDCTL");
    RequireByte(volumeSlide, "volume slide");
    RequireRange(volumeMinimum, MAX_VOLUME, "volume minimum");
    RequireByte(delay, "delay");
    RequireRange(vibrato, MAX_VIBRATO, "vibrato");
    RequireByte(frequencyShift, "frequency shift");
    RequireByte(unused, "unused byte");
}

CRmtModule::Instrument CRmtModule::Instrument::Parse(const unsigned char* mem, int offset, int available) {
    if (available < PARAMETERS_LENGTH + 1) {
        throw FormatException("Only " + std::to_string(available) + " bytes remain, fewer than the parameters and one table entry.");
    }
    int tlen = mem[offset + Offset::TABLE_END];
    int tgo = mem[offset + Offset::TABLE_LOOP];
    int elen = mem[offset + Offset::ENVELOPE_END];
    int ego = mem[offset + Offset::ENVELOPE_LOOP];
    if (tlen < PARAMETERS_LENGTH) {
        throw FormatException("The table end pointer " + std::to_string(tlen) + " lies before the table's start at " + std::to_string(PARAMETERS_LENGTH) + ".");
    }
    if (tgo < PARAMETERS_LENGTH || tgo > tlen) {
        throw FormatException("The table loop pointer " + std::to_string(tgo) + " is not " + std::to_string(PARAMETERS_LENGTH) + "-" + std::to_string(tlen) + ".");
    }
    int envelopeStart = tlen + 1;
    if (elen < envelopeStart || (elen - envelopeStart) % ENVELOPE_ENTRY_LENGTH != 0) {
        throw FormatException("The envelope end pointer " + std::to_string(elen) + " is not an entry of the envelope starting at " + std::to_string(envelopeStart) + ".");
    }
    if (ego < envelopeStart || ego > elen || (ego - envelopeStart) % ENVELOPE_ENTRY_LENGTH != 0) {
        throw FormatException("The envelope loop pointer " + std::to_string(ego) + " is not an entry of the envelope " + std::to_string(envelopeStart) + "-" + std::to_string(elen) + ".");
    }
    int length = elen + ENVELOPE_ENTRY_LENGTH; // elen addresses the last entry's first byte
    if (length > available) {
        throw FormatException("The struct is " + std::to_string(length) + " bytes long, but only " + std::to_string(available) + " remain.");
    }
    Bytes noteTable(mem + offset + PARAMETERS_LENGTH, mem + offset + tlen + 1);
    std::vector<Envelope> envelope;
    for (int entry = offset + envelopeStart; entry <= offset + elen; entry += ENVELOPE_ENTRY_LENGTH) {
        envelope.push_back(Envelope{mem[entry + Envelope::Offset::VOLUME], mem[entry + Envelope::Offset::CONTROL], mem[entry + Envelope::Offset::XY]});
    }
    int tableControl = mem[offset + Offset::TABLE_CONTROL];
    return Instrument(noteTable, tgo - PARAMETERS_LENGTH, envelope, (ego - envelopeStart) / ENVELOPE_ENTRY_LENGTH, tableControl & MAX_TABLE_SPEED,
        (tableControl >> TABLE_MODE_SHIFT) & BIT_MASK, tableControl >> TABLE_TYPE_SHIFT, mem[offset + Offset::AUDCTL], mem[offset + Offset::VOLUME_SLIDE],
        mem[offset + Offset::VOLUME_MINIMUM] >> HIGH_NIBBLE_SHIFT, mem[offset + Offset::DELAY], mem[offset + Offset::VIBRATO], mem[offset + Offset::FREQUENCY_SHIFT],
        mem[offset + Offset::UNUSED]);
}

int CRmtModule::Instrument::GetLength() const {
    return PARAMETERS_LENGTH + static_cast<int>(m_noteTable.size()) + static_cast<int>(m_envelope.size()) * ENVELOPE_ENTRY_LENGTH;
}

CRmtModule::Bytes CRmtModule::Instrument::ToBytes() const {
    Bytes bytes(GetLength());
    Write(bytes.data(), 0);
    return bytes;
}

int CRmtModule::Instrument::Write(unsigned char* mem, int offset) const {
    int tlen = PARAMETERS_LENGTH + static_cast<int>(m_noteTable.size()) - 1;
    int envelopeStart = tlen + 1;
    mem[offset + Offset::TABLE_END] = static_cast<unsigned char>(tlen);
    mem[offset + Offset::TABLE_LOOP] = static_cast<unsigned char>(PARAMETERS_LENGTH + m_tableLoop);
    mem[offset + Offset::ENVELOPE_END] = static_cast<unsigned char>(envelopeStart + (static_cast<int>(m_envelope.size()) - 1) * ENVELOPE_ENTRY_LENGTH);
    mem[offset + Offset::ENVELOPE_LOOP] = static_cast<unsigned char>(envelopeStart + m_envelopeLoop * ENVELOPE_ENTRY_LENGTH);
    mem[offset + Offset::TABLE_CONTROL] = static_cast<unsigned char>((m_tableType << TABLE_TYPE_SHIFT) | (m_tableMode << TABLE_MODE_SHIFT) | m_tableSpeed);
    mem[offset + Offset::AUDCTL] = static_cast<unsigned char>(m_audctl);
    mem[offset + Offset::VOLUME_SLIDE] = static_cast<unsigned char>(m_volumeSlide);
    mem[offset + Offset::VOLUME_MINIMUM] = static_cast<unsigned char>(m_volumeMinimum << HIGH_NIBBLE_SHIFT);
    mem[offset + Offset::DELAY] = static_cast<unsigned char>(m_delay);
    mem[offset + Offset::VIBRATO] = static_cast<unsigned char>(m_vibrato);
    mem[offset + Offset::FREQUENCY_SHIFT] = static_cast<unsigned char>(m_frequencyShift);
    mem[offset + Offset::UNUSED] = static_cast<unsigned char>(m_unused);
    std::memcpy(mem + offset + PARAMETERS_LENGTH, m_noteTable.data(), m_noteTable.size());
    int entry = offset + envelopeStart;
    for (const Envelope& e : m_envelope) {
        mem[entry + Envelope::Offset::VOLUME] = static_cast<unsigned char>(e.volume);
        mem[entry + Envelope::Offset::CONTROL] = static_cast<unsigned char>(e.control);
        mem[entry + Envelope::Offset::XY] = static_cast<unsigned char>(e.xy);
        entry += ENVELOPE_ENTRY_LENGTH;
    }
    return offset + GetLength();
}
