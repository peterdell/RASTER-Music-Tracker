#include "StdAfx.h"
#include <optional>
#include <vector>
#include "ASMFile.h"
#include "ASMFileExporter.h"
#include "ASMFileBuilder.h"
#include "Atari.h"
#include "FrequencyTables.h"
#include "Messages.h"
#include "RmtModule.h"
#include "TrackerDriverVersion.h"

// The CASMFileExporter methods here have no dialog/hazard of their own -
// confirmed by reading each one for globals/dialogs and finding only
// already-safe g_Tracks/g_Instruments (plus CSong's own state, which
// CASMFileExporter can read directly as a friend class - see Song.h).
// ExportAsAsm()/ExportAsRelocatableAsmForRmtPlayer() (ASMFileExporter.cpp)
// instantiate real MFC dialogs to gather their parameters, then delegate
// here - same pattern as Song.cpp/SongEditing.cpp etc.

extern CInstruments g_Instruments;
extern CAtari g_Atari; // only read from, and only to see whether the loaded driver has frequency tables

CString g_PrefixForAllAsmLabels; //label prefix for export ASM simple notation

/// <summary>
/// Export the RMT module as assembler
/// </summary>
bool CASMFileExporter::ExportAsAsmApply(const CSong& song, std::ostream& ou, int exportType, int notesIndexOrFreq, int durationsType) {
    // Which player's tables a frequency comes from is the song's own setting,
    // the same way its region is (plans/41_DRIVER_VERSION_IS_A_SONG_PROPERTY_PLAN.md).
    const TrackerDriverVersion trackerDriverVersion = song.GetTrackerDriverVersion();

    if (notesIndexOrFreq == 2 /* frequencies */ &&
        !CFrequencyTables::HasDispatchTable(g_Atari.GetConstMemoryAt(0), trackerDriverVersion)) {
        // Every value would come out of the loaded player's own frequency
        // tables, so without them the whole export is a file of XXX - which
        // looks like a tracker bug rather than a missing driver file. Plan 30
        // settled that a missing driver must not produce a silent export; this
        // is the same rule for a silent one
        // (plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md, L4).
        SendErrorMessage("Export as ASM",
                         "The player routines for the selected tracker driver are not loaded, so there are no frequency tables to "
                         "read.\n\nExport the notes as indices instead, or restore the driver file and reopen the module.");
        return false;
    }

    CString s, snot;
    int maxova = 16; // maximal amount of data per line

    BYTE tracksFlags[TRACKSNUM];
    memset(tracksFlags, 0, TRACKSNUM); // init
    song.MarkTF_USED(tracksFlags);

    TTrack tempTrack; // temporary track

    ou << ";ASM notation source";
    ou << CASMFile::EOL << "XXX\tequ $FF\t;empty note value";
    if (!g_PrefixForAllAsmLabels.IsEmpty()) {
        s.Format("%s_data", g_PrefixForAllAsmLabels);
        ou << CASMFile::EOL << s;
    }
    //
    if (exportType == 1 /* Tracks only*/) {
        // Tracks
        int j, note, dur, instrumentNr;
        for (int trackNr = 0; trackNr < TRACKSNUM; trackNr++) {
            // Only process if the track is used
            if (!(tracksFlags[trackNr] & TrackFlag::TF_USED)) {
                continue;
            }

            s.Format(";Track $%02X", trackNr);
            ou << CASMFile::EOL << s;
            if (!g_PrefixForAllAsmLabels.IsEmpty()) {
                s.Format("%s_track%02X", g_PrefixForAllAsmLabels, trackNr);
                ou << CASMFile::EOL << s;
            }

            tempTrack = *g_Tracks.GetTrack(trackNr);
            g_Tracks.TrackExpandLoop(&tempTrack); //expands tt due to GO loops

            int ova = maxova;

            for (int idx = 0; idx < tempTrack.len; idx++) {
                if (ova >= maxova) {
                    ou << CASMFile::EOL << "\tdta ";
                    ova = 0;
                }
                note = tempTrack.note[idx];
                if (note >= 0) {
                    instrumentNr = tempTrack.instr[idx];
                    if (notesIndexOrFreq == 1) { //notes
                        note = g_Instruments.GetNote(instrumentNr, note);
                    } else { //frequencies
                        note = g_Instruments.GetFrequency(instrumentNr, note, trackerDriverVersion);
                    }
                }
                if (note >= 0) {
                    snot.Format("$%02X", note);
                } else {
                    snot = "XXX";
                }
                for (dur = 1; idx + dur < tempTrack.len && tempTrack.note[idx + dur] < 0; dur++) {
                    ;
                }
                if (durationsType == 1 /* Notes only */) {
                    if (ova > 0) {
                        ou << ",";
                    }
                    ou << snot;
                    ova++;
                    for (j = 1; j < dur; j++, ova++) {
                        if (ova >= maxova) {
                            ova = 0;
                            ou << CASMFile::EOL << "\tdta XXX";
                        } else {
                            ou << ",XXX";
                        }
                    }
                } else if (durationsType == 2 /* Note, duration */) {
                    if (ova > 0) {
                        ou << ",";
                    }
                    ou << snot;
                    ou << "," << dur;
                    ova += 2;
                } else if (durationsType == 3 /* Duration, Note */) {
                    if (ova > 0) {
                        ou << ",";
                    }
                    ou << dur << ",";
                    ou << snot;
                    ova += 2;
                }
                idx += dur - 1;
            }
        }
    } else if (exportType == 2 /* Whole song */) {
        // song columns
        int clm;
        for (clm = 0; clm < song.GetChannels(); clm++) {
            BYTE finished[SONGLEN];
            memset(finished, 0, SONGLEN);
            int sline = 0;
            const char* cnames[] = {"L1", "L2", "L3", "L4", "R1", "R2", "R3", "R4"};
            s.Format(";Song column %s", cnames[clm]);
            ou << CASMFile::EOL << s;
            if (!g_PrefixForAllAsmLabels.IsEmpty()) {
                s.Format("%s_column%s", g_PrefixForAllAsmLabels, cnames[clm]);
                ou << CASMFile::EOL << s;
            }
            while (sline >= 0 && sline < SONGLEN && !finished[sline]) {
                finished[sline] = 1;
                s.Format(";Song line $%02X", sline);
                ou << CASMFile::EOL << s;
                if (song.m_songgo[sline] >= 0) {
                    sline = song.m_songgo[sline]; //GOTO line
                    s.Format(" Go to line $%02X", sline);
                    ou << s;
                    continue;
                }
                int trackslen = g_Tracks.GetMaxTrackLength();
                for (int i = 0; i < song.GetChannels(); i++) {
                    int at = song.m_song[sline][i];
                    if (at < 0 || at >= TRACKSNUM) {
                        continue;
                    }
                    if (g_Tracks.GetGoLine(at) >= 0) {
                        continue;
                    }
                    int al = g_Tracks.GetLastLine(at) + 1;
                    if (al < trackslen) {
                        trackslen = al;
                    }
                }
                int t, i, j, anot, dur, ins;
                int ova = maxova;
                t = song.m_song[sline][clm];
                if (t < 0) {
                    ou << " Track --";
                    if (!g_PrefixForAllAsmLabels.IsEmpty()) {
                        s.Format("%s_column%s_line%02X", g_PrefixForAllAsmLabels, cnames[clm], sline);
                        ou << CASMFile::EOL << s;
                    }
                    if (durationsType == 1) {
                        for (i = 0; i < trackslen; i++, ova++) {
                            if (ova >= maxova) {
                                ova = 0;
                                ou << CASMFile::EOL << "\tdta XXX";
                            } else {
                                ou << ",XXX";
                            }
                        }
                    } else if (durationsType == 2) {
                        ou << CASMFile::EOL << "\tdta XXX,";
                        ou << trackslen;
                        ova += 2;
                    } else if (durationsType == 3) {
                        ou << CASMFile::EOL << "\tdta ";
                        ou << trackslen << ",XXX";
                        ova += 2;
                    }
                    sline++;
                    continue;
                }

                s.Format(" Track $%02X", t);
                ou << s;
                if (!g_PrefixForAllAsmLabels.IsEmpty()) {
                    s.Format("%s_column%s_line%02X", g_PrefixForAllAsmLabels, cnames[clm], sline);
                    ou << CASMFile::EOL << s;
                }

                tempTrack = *g_Tracks.GetTrack(t);
                g_Tracks.TrackExpandLoop(&tempTrack); //expands tt due to GO loops

                for (i = 0; i < trackslen; i++) {
                    if (ova >= maxova) {
                        ova = 0;
                        ou << CASMFile::EOL << "\tdta ";
                    }

                    anot = tempTrack.note[i];
                    if (anot >= 0) {
                        ins = tempTrack.instr[i];
                        if (notesIndexOrFreq == 1) { //notes
                            anot = g_Instruments.GetNote(ins, anot);
                        } else { //frequencies
                            anot = g_Instruments.GetFrequency(ins, anot, trackerDriverVersion);
                        }
                    }
                    if (anot >= 0) {
                        snot.Format("$%02X", anot);
                    } else {
                        snot = "XXX";
                    }
                    for (dur = 1; i + dur < trackslen && tempTrack.note[i + dur] < 0; dur++) {
                        ;
                    }
                    if (durationsType == 1) {
                        if (ova > 0) {
                            ou << ",";
                        }
                        ou << snot;
                        ova++;
                        for (j = 1; j < dur; j++, ova++) {
                            if (ova >= maxova) {
                                ova = 0;
                                ou << CASMFile::EOL << "\tdta XXX";
                            } else {
                                ou << ",XXX";
                            }
                        }
                    } else if (durationsType == 2) {
                        if (ova > 0) {
                            ou << ",";
                        }
                        ou << snot;
                        ou << "," << dur;
                        ova += 2;
                    } else if (durationsType == 3) {
                        if (ova > 0) {
                            ou << ",";
                        }
                        ou << dur << ",";
                        ou << snot;
                        ova += 2;
                    }
                    i += dur - 1;
                }
                sline++;
            }
        }
    }
    ou << CASMFile::EOL;

    return true;
}

// Extracted from CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer()
// (ASMFileExporter.cpp): the dialog-independent work, once the confirmed
// parameters are known. Mostly a thin wrapper around BuildRelocatableAsm()
// below (already dialog-independent), plus the exportDescWithSFX/
// exportDescStripped selection and the final stream write that live
// outside of it.
bool CASMFileExporter::ExportAsRelocatableAsmForRmtPlayerApply(CSong& song, std::ostream& ou, TExportDescription* exportDescStripped, TExportDescription* exportDescWithSFX, const TRelocatableAsmExportParams& p) {
    CString strAsmForModule;

    BOOL isGood = BuildRelocatableAsm(
        song,
        strAsmForModule,
        p.sfxSupport ? exportDescWithSFX : exportDescStripped,
        p.strAsmLabelForStartOfSong,
        p.wantRelocatableTracks ? p.strAsmTracksLabel : (CString) "",
        p.wantRelocatableSongLines ? p.strAsmSongLinesLabel : (CString) "",
        p.wantRelocatableInstruments ? p.strAsmInstrumentsLabel : (CString) "",
        p.assemblerFormat,
        p.sfxSupport,
        p.globalVolumeFade,
        p.noStartingSongLine,
        false);

    if (!isGood) {
        return false;
    }

    ou << strAsmForModule << CASMFile::EOL;

    return true;
}

// ============================================================================
// The code below handles the export of the RMT song data as relocatable
// assembler source code.


/// <summary>
/// Based on the rtm2atasm code
/// </summary>
/// <param name="dest"></param>
/// <param name="exportDesc"></param>
/// <param name="strAsmStartLabel"></param>
/// <param name="strTracksLabel"></param>
/// <param name="strSongLinesLabel"></param>
/// <param name="strInstrumentsLabel"></param>
/// <param name="assemblerFormat"></param>
/// <param name="sfx"></param>
/// <param name="gvf"></param>
/// <param name="nos"></param>
/// <returns></returns>
BOOL CASMFileExporter::BuildRelocatableAsm(
    const CSong& song,
    CString& addCodeHere,
    TExportDescription* exportDesc,
    CString strAsmStartLabel,
    CString strTracksLabel,
    CString strSongLinesLabel,
    CString strInstrumentsLabel,
    AssemblerFormat assemblerFormat,
    BOOL sfx,
    BOOL gvf,
    BOOL nos,
    bool bWantSizeInfoOnly) {
    CString strModuleSequence = "\nSequence of modules:\n  Header\n";
    CString strCode;

    int sizeIntro = 16;
    int sizeInstruments = 0;
    int sizeTrack = 0;
    int sizeSongLines = 0;

    // .byte		|	dta
    // .word		|   dta a()
    // .byte "str"	|	dta c'str'
    // ?localvar	|   __localvar

    // Assembler type setup
    BOOL hasDotLocal = 0;
    const char* _byte = "dta";
    if (assemblerFormat == ATASM) {
        hasDotLocal = 1;
        _byte = ".byte";
    }

    // Sanity checks
    strAsmStartLabel.Trim();
    if (strAsmStartLabel.IsEmpty()) {
        strAsmStartLabel = "RMT_SONG_DATA";
    }

    strCode.Format("; Cut and paste the data from the line '; --------BEGIN--------'  to the line '; --------END--------'\n; into rmt_feat%s\n",
                   assemblerFormat == ATASM ? ".asm" : "./65");

    CString str;
    ComposeRMTFEATstring(song, str, "", exportDesc->instrumentSavedFlags, exportDesc->trackSavedFlags, sfx, gvf, nos, assemblerFormat);
    strCode += str;
    //
    int start = exportDesc->targetAddrOfModule;
    std::optional<CRmtModule> parsed;
    try {
        parsed.emplace(CRmtModule::Parse(exportDesc->mem, start, exportDesc->firstByteAfterModule));
    } catch (const CRmtModule::FormatException&) {
        return 0;
    }
    const CRmtModule& module = *parsed;
    if (module.GetInstruments().empty() || module.GetTracks().empty()) {
        return 0; // the player source needs both
    }
    CRmtModule::Layout layout = module.GetLayout();
    CRmtModule::Bytes moduleBytes = module.ToBytes(start);
    unsigned char* buf = moduleBytes.data();
    int len = layout.end;
    char channelDigit = static_cast<char>(buf[CRmtModule::Offset::CHANNELS]);
    str.Format("; RMT%c file exported as relocatable source code\n"
               "; Original size: $%04x bytes @ $%04x\n",
               channelDigit, len, start);
    strCode += str;

    int numTracks = module.GetChannels();
    int offsetInstrumentPtrTable = layout.instrumentsTable; // This is where the instruments are stored (always directly after this table)
    int offsetTrackPtrTableLow = layout.tracksLowTable; // Track ptrs are broken up into lo and hi bytes
    int offsetTrackPtrTableHigh = layout.tracksHighTable;
    int offsetSong = layout.song; // The song tracks start here
    int first_instr = layout.instrumentData;
    int first_track = layout.trackData;

    // Write assembly output
    str.Format(
        ".local\n"
        "%s\n" // This is the start of the song data
        "?start\n",
        strAsmStartLabel);
    strCode += str;

    if (assemblerFormat == ATASM) {
        str.Format("    {{byte}} \"RMT%c\"\n", channelDigit);
    } else {
        str.Format("    dta c'RMT%c'\n", channelDigit);
    }
    strCode += str;

    str.Format("?song_info\n"
               "    {{byte}} $%02x            ; Track length = %d\n"
               "    {{byte}} $%02x            ; Song speed\n"
               "    {{byte}} $%02x            ; Player Frequency\n"
               "    {{byte}} $%02x            ; Format version\n",
               buf[CRmtModule::Offset::TRACK_LENGTH], buf[CRmtModule::Offset::TRACK_LENGTH], // max track length
               buf[CRmtModule::Offset::SONG_SPEED],
               buf[CRmtModule::Offset::INSTRUMENT_SPEED], // player frequency
               buf[CRmtModule::Offset::FORMAT_VERSION]);
    strCode += str;
    strCode += "; ptrs to tables\n";
    if (assemblerFormat == ATASM) {
        // Atasm
        str.Format(
            "?ptrInstrumentTbl\n    .word ?InstrumentsTable       ; start + $%04x\n"
            "?ptrTracksTblLo\n    .word ?TracksTblLo            ; start + $%04x\n"
            "?ptrTracksTblHi\n    .word ?TracksTblHi            ; start + $%04x\n"
            "?ptrSong\n    .word ?SongData               ; start + $%04x\n",
            offsetInstrumentPtrTable, offsetTrackPtrTableLow, offsetTrackPtrTableHigh, offsetSong);
    } else {
        // Xasm
        str.Format(
            "__ptrInstrumentTbl\n    dta a(__InstrumentsTable)       ; start + $%04x\n"
            "__ptrTracksTblLo\n    dta a(__TracksTblLo)            ; start + $%04x\n"
            "__ptrTracksTblHi\n    dta a(__TracksTblHi)            ; start + $%04x\n"
            "__ptrSong\n    dta a(__SongData)               ; start + $%04x\n",
            offsetInstrumentPtrTable, offsetTrackPtrTableLow, offsetTrackPtrTableHigh, offsetSong);
    }
    strCode += str;

    // List of ptrs to instruments
    strCode += "\n; List of ptrs to instruments\n";
    std::vector<int> instr_pos(CMemory::SIZE);
    strCode += "?InstrumentsTable";

    for (int number = 0; number < layout.InstrumentEntries(); number++) {
        auto offset = layout.instruments.find(number);
        if (offset != layout.instruments.end()) {
            instr_pos[offset->second] = number + 1;
            str.Format(assemblerFormat == ATASM ? "?Instrument_%d" : "a(?Instrument_%d)", number);
        } else {
            str = (assemblerFormat == ATASM ? "  $0000" : " a($0000)");
        }

        sizeIntro += 2; // 2 bytes per used instrument

        strCode += (assemblerFormat == ATASM ? "\n    .word " : "\n    dta ");
        strCode += str;
        str.Format("\t\t; %s", g_Instruments.GetName(number));
        strCode += str;
    }
    strCode += "\n";
    // List of tracks:
    std::vector<int> track_pos(CMemory::SIZE);
    strCode += "\n?TracksTblLo";
    for (int number = 0; number < layout.TrackEntries(); number++) {
        auto offset = layout.tracks.find(number);
        if (number % 8 == 0) {
            strCode += (assemblerFormat == ATASM ? "\n    .byte " : "\n    dta ");
        } else {
            strCode += ",";
        }
        if (offset != layout.tracks.end()) {
            track_pos[offset->second] = number + 1;
            str.Format(assemblerFormat == ATASM ? "<?Track_%02x" : "l(__Track_%02x)", number);
            strCode += str;
        } else {
            strCode += "$00";
        }

        sizeIntro += 1; // 1 byte per used track
    }
    strCode += "\n?TracksTblHi";
    for (int number = 0; number < layout.TrackEntries(); number++) {
        if (number % 8 == 0) {
            strCode += (assemblerFormat == ATASM ? "\n    .byte " : "\n    dta ");
        } else {
            strCode += ",";
        }
        if (layout.tracks.count(number) != 0) {
            str.Format(assemblerFormat == ATASM ? ">?Track_%02x" : "h(__Track_%02x)", number);
            strCode += str;
        } else {
            strCode += "$00";
        }

        sizeIntro += 1; // 1 byte per used track
    }

    strCode += "\n";

    // First dump all the sequencial code, relocated data is dumped after this
    if (strInstrumentsLabel.IsEmpty()) {
        sizeInstruments = CASMFileBuilder::BuildInstrumentData(str, "", buf, first_instr, first_track, instr_pos.data(), assemblerFormat);
        strCode += str;

        strModuleSequence += "  Instruments\n";
    }
    if (strTracksLabel.IsEmpty()) {
        sizeTrack = CASMFileBuilder::BuildTracksData(str, "", buf, first_track, offsetSong, track_pos.data(), assemblerFormat);
        if (sizeTrack == 0) {
            return 0;
        }
        strCode += str;
        strModuleSequence += "  Tracks\n";
    }
    if (strSongLinesLabel.IsEmpty()) {
        sizeSongLines = CASMFileBuilder::BuildSongData(str, "", buf, offsetSong, len, start, numTracks, assemblerFormat);
        strCode += str;
        strModuleSequence += "  Song Lines\n";
    }

    // Dump the relocated data
    if (strInstrumentsLabel.IsEmpty() == false) {
        sizeInstruments = CASMFileBuilder::BuildInstrumentData(str, strInstrumentsLabel, buf, first_instr, first_track, instr_pos.data(), assemblerFormat);
        strCode += str;
        strModuleSequence += "Relocated Instruments\n";
    }
    if (strTracksLabel.IsEmpty() == false) {
        sizeTrack = CASMFileBuilder::BuildTracksData(str, strTracksLabel, buf, first_track, offsetSong, track_pos.data(), assemblerFormat);
        if (sizeTrack == 0) {
            return 0;
        }
        strCode += str;
        strModuleSequence += "Relocated Tracks\n";
    }
    if (strSongLinesLabel.IsEmpty() == false) {
        sizeSongLines = CASMFileBuilder::BuildSongData(str, strSongLinesLabel, buf, offsetSong, len, start, numTracks, assemblerFormat);
        strCode += str;
        strModuleSequence += "Relocated Song Lines\n";
    }

    strCode.Replace("{{byte}}", _byte);
    strCode.Replace("?", "__");

    if (assemblerFormat == XASM) {
        strCode.Replace(".local", "");
    }

    if (bWantSizeInfoOnly) {
        addCodeHere.Format(
            "Header\t\t= $%04x (%d) bytes\n"
            "Instruments\t= $%04x (%d) bytes\n"
            "Tracks\t\t= $%04x (%d) bytes\n"
            "Song Lines\t= $%04x (%d) bytes\n",
            sizeIntro, sizeIntro,
            sizeInstruments, sizeInstruments,
            sizeTrack, sizeTrack,
            sizeSongLines, sizeSongLines);
        addCodeHere += strModuleSequence;
        addCodeHere.Replace("\n", "\x0d\x0a");
    } else {
        addCodeHere = strCode;
    }

    return 1;
}

/// <summary>
/// Create Assembler code to describe which features
/// of the RMTPlayer are to be used
/// </summary>
/// <param name="dest">Output string is appended here</param>
/// <param name="filename">Filename of the song</param>
/// <param name="instrumentSavedFlags">Flags indicating if an instrument is used</param>
/// <param name="trackSavedFlags">Flags indicating if a track is used</param>
/// <param name="soundFXSupport">Flag to indicate if sound effects are to be supported</param>
/// <param name="globalVolumeFade">Flag to indicate support for global volume fading</param>
/// <param name="noStartingSongLine">Flag to indicate if there is a starting song line</param>
/// <param name="assemblerFormat">Which assembler format is to be used ATASM or XASM</param>
void CASMFileExporter::ComposeRMTFEATstring(
    const CSong& song,
    CString& dest,
    //char* filename,
    const char* filename,
    BYTE* instrumentSavedFlags,
    BYTE* trackSavedFlags,
    BOOL soundFXSupport,
    BOOL globalVolumeFade,
    BOOL noStartingSongLine,
    AssemblerFormat assemblerFormat) {
    // Depending on the assembler format: equ or =
    //char* equal = "equ";
    const char* equal = "equ";
    if (assemblerFormat == ATASM) {
        equal = "=";
    }

#define DEST(var, str)                                                    \
    s.Format("%s\t\t%s %i\t\t;(%i times)\n", str, equal, (var > 0), var); \
    dest += s;

    dest.Format("; --------BEGIN--------\n; %s\n", filename);
    //
    int usedCommand[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    int usedCommand7_VolumeOnly = 0;
    int usedCommand7_VolumeOnlyOnChannelX[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    int usedCommand7_SetNote = 0;
    int usedPortamento = 0;
    int usedFilter = 0;
    int usedFilterOnChannelX[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    int usedBass16 = 0;
    int usedBass16OnChannelX[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    int usedTableType = 0;
    int usedTableMode = 0;
    int usedTableGoto = 0;
    int usedAudctlManualSet = 0;
    int usedVolumeMin = 0;
    int usedEffectVibrato = 0;
    int usedEffectFrequencyShift = 0;
    int howManySpeedChanges = 0;

    // Analysis of which instruments are used on which channel and if there is a change in speed
    int instrumentUsedOnChannelX[INSTRSNUM][SONGTRACKS];
    memset(&instrumentUsedOnChannelX, 0, sizeof(instrumentUsedOnChannelX));

    for (int songLineNr = 0; songLineNr < SONGLEN; songLineNr++) {
        if (song.m_songgo[songLineNr] >= 0) {
            continue; // goto line
        }
        for (int channelNr = 0; channelNr < song.GetChannels(); channelNr++) {
            // Get the track# that is at the song line and at the channel
            int trackNr = song.m_song[songLineNr][channelNr];
            if (trackNr < 0 || trackNr >= TRACKSNUM) {
                continue;
            }

            TTrack* tt = g_Tracks.GetTrack(trackNr); // Get the track data
            for (int i = 0; i < tt->len; i++) {
                // Track which instruments are used
                int instrumentNr = tt->instr[i];
                if (instrumentNr >= 0 && instrumentNr < INSTRSNUM) {
                    instrumentUsedOnChannelX[instrumentNr][channelNr]++;
                }

                // Track how often the speed is changed
                int chsp = tt->speed[i];
                if (chsp >= 0) {
                    howManySpeedChanges++;
                }
            }
        }
    }

    // Analyse the individual instruments and what they use
    for (int instrumentNr = 0; instrumentNr < INSTRSNUM; instrumentNr++) {
        if (instrumentSavedFlags[instrumentNr]) {
            // Get access to the instrument
            TInstrument* ai = g_Instruments.GetInstrument(instrumentNr);

            // Run over all commands that an instrument uses
            for (int j = 0; j <= ai->parameters[PAR_ENV_LENGTH]; j++) {
                int cmd = ai->envelope[j][EnvelopeParameter::COMMAND] & 0x07;
                usedCommand[cmd]++;
                if (cmd == 7) { // AUDCTL
                    if (ai->envelope[j][EnvelopeParameter::X] == 0x08 && ai->envelope[j][EnvelopeParameter::Y] == 0x00) {
                        usedCommand7_VolumeOnly++;
                        for (int channelNr = 0; channelNr < song.GetChannels(); channelNr++) {
                            if (instrumentUsedOnChannelX[instrumentNr][channelNr]) {
                                usedCommand7_VolumeOnlyOnChannelX[channelNr]++;
                            }
                        }
                    } else {
                        usedCommand7_SetNote++;
                    }
                }

                // Portamento
                if (ai->envelope[j][EnvelopeParameter::PORTAMENTO]) {
                    usedPortamento++;
                }

                // Filter
                if (ai->envelope[j][EnvelopeParameter::FILTER]) {
                    usedFilter++;
                    for (int channelNr = 0; channelNr < song.GetChannels(); channelNr++) {
                        if (instrumentUsedOnChannelX[instrumentNr][channelNr]) {
                            usedFilterOnChannelX[channelNr]++;
                        }
                    }
                }

                // Bass16
                if (ai->envelope[j][EnvelopeParameter::DISTORTION] == 6) {
                    usedBass16++;
                    for (int channelNr = 0; channelNr < song.GetChannels(); channelNr++) {
                        if (instrumentUsedOnChannelX[instrumentNr][channelNr]) {
                            usedBass16OnChannelX[channelNr]++;
                        }
                    }
                }
            }
            // table type
            if (ai->parameters[PAR_TBL_TYPE]) {
                usedTableType++;
            }
            // table mode
            if (ai->parameters[PAR_TBL_MODE]) {
                usedTableMode++;
            }
            // table go
            if (ai->parameters[PAR_TBL_GOTO]) {
                usedTableGoto++; //non-zero table go
            }
            // AUDCTL manual set
            if (ai->parameters[PAR_AUDCTL_15KHZ] || ai->parameters[PAR_AUDCTL_HPF_CH2] || ai->parameters[PAR_AUDCTL_HPF_CH1] || ai->parameters[PAR_AUDCTL_JOIN_3_4] || ai->parameters[PAR_AUDCTL_JOIN_1_2] || ai->parameters[PAR_AUDCTL_179_CH3] || ai->parameters[PAR_AUDCTL_179_CH1] || ai->parameters[PAR_AUDCTL_POLY9]) {
                usedAudctlManualSet++;
            }
            // Volume mininum
            if (ai->parameters[PAR_VOL_MIN]) {
                usedVolumeMin++;
            }
            // Effect vibrato and frequency shift
            if (ai->parameters[PAR_DELAY]) { // only when the effect delay is non-zero
                if (ai->parameters[PAR_VIBRATO]) {
                    usedEffectVibrato++;
                }
                if (ai->parameters[PAR_FREQ_SHIFT]) {
                    usedEffectFrequencyShift++;
                }
            }
        }
    }

    // Generate strings
    CString s;

    s.Format("FEAT_SFX\t\t%s %u\n", equal, soundFXSupport);
    dest += s;

    s.Format("FEAT_GLOBALVOLUMEFADE\t%s %u\t\t;RMTGLOBALVOLUMEFADE variable\n", equal, globalVolumeFade);
    dest += s;

    s.Format("FEAT_NOSTARTINGSONGLINE\t%s %u\n", equal, noStartingSongLine);
    dest += s;

    s.Format("FEAT_INSTRSPEED\t\t%s %i\n", equal, song.GetInstrumentSpeed());
    dest += s;

    s.Format("FEAT_CONSTANTSPEED\t\t%s %i\t\t;(%i times)\n", equal, (howManySpeedChanges == 0) ? song.m_mainSpeed : 0, howManySpeedChanges);
    dest += s;

    // Commands 1-6
    for (int i = 1; i <= 6; i++) {
        s.Format("FEAT_COMMAND%i\t\t%s %i\t\t;(%i times)\n", i, equal, (usedCommand[i] > 0), usedCommand[i]);
        dest += s;
    }

    // Command 7
    DEST(usedCommand7_SetNote, "FEAT_COMMAND7SETNOTE");
    DEST(usedCommand7_VolumeOnly, "FEAT_COMMAND7VOLUMEONLY");
    //
    DEST(usedPortamento, "FEAT_PORTAMENTO");
    //
    DEST(usedFilter, "FEAT_FILTER");
    DEST(usedFilterOnChannelX[0], "FEAT_FILTERG0L");
    DEST(usedFilterOnChannelX[1], "FEAT_FILTERG1L");
    DEST(usedFilterOnChannelX[0 + 4], "FEAT_FILTERG0R");
    DEST(usedFilterOnChannelX[1 + 4], "FEAT_FILTERG1R");
    //
    DEST(usedBass16, "FEAT_BASS16");
    DEST(usedBass16OnChannelX[1], "FEAT_BASS16G1L");
    DEST(usedBass16OnChannelX[3], "FEAT_BASS16G3L");
    DEST(usedBass16OnChannelX[1 + 4], "FEAT_BASS16G1R");
    DEST(usedBass16OnChannelX[3 + 4], "FEAT_BASS16G3R");
    //
    DEST(usedCommand7_VolumeOnlyOnChannelX[0], "FEAT_VOLUMEONLYG0L");
    DEST(usedCommand7_VolumeOnlyOnChannelX[2], "FEAT_VOLUMEONLYG2L");
    DEST(usedCommand7_VolumeOnlyOnChannelX[3], "FEAT_VOLUMEONLYG3L");
    DEST(usedCommand7_VolumeOnlyOnChannelX[0 + 4], "FEAT_VOLUMEONLYG0R");
    DEST(usedCommand7_VolumeOnlyOnChannelX[2 + 4], "FEAT_VOLUMEONLYG2R");
    DEST(usedCommand7_VolumeOnlyOnChannelX[3 + 4], "FEAT_VOLUMEONLYG3R");
    //
    DEST(usedTableType, "FEAT_TABLETYPE");
    DEST(usedTableMode, "FEAT_TABLEMODE");
    DEST(usedTableGoto, "FEAT_TABLEGO");
    DEST(usedAudctlManualSet, "FEAT_AUDCTLMANUALSET");
    DEST(usedVolumeMin, "FEAT_VOLUMEMIN");

    DEST(usedEffectVibrato, "FEAT_EFFECTVIBRATO");
    DEST(usedEffectFrequencyShift, "FEAT_EFFECTFSHIFT");

    dest += "; --------END--------\n";
    dest.Replace("\n", "\x0d\x0a");
}
