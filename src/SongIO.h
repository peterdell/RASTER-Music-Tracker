#pragma once

#include "StdAfx.h"
#include<vector>

class FileDialogParameters {

public:
    typedef int FilterIndex;
    virtual CString GetFilters() = 0;
    virtual std::vector<CString> GetExtensionArray() { return {}; }

    bool IsValidFilterIndex(const FilterIndex filterIndex) {
        return (filterIndex >= 1) && (filterIndex <= GetExtensionArray().size());
    }
};

// ----------------------------------------------------------------------------
// File open/save dialog format selections
class FILE_LOADSAVE : public FileDialogParameters {
public:
    static constexpr FilterIndex FILTER_IDX_RMT = 1;
    static constexpr FilterIndex FILTER_IDX_TXT = 2;
    static constexpr FilterIndex FILTER_IDX_RMW = 3;


    CString GetFilters() {
        return  "RMT song file (*.rmt)|*.rmt|TXT song file (*.txt)|*.txt|RMW song work file (*.rmw)|*.rmw||";
    }


    std::vector<CString> GetExtensionArray() {

        return { ".rmt", ".txt", ".rmw" };
    }
};

// File import dialog format selections
class FILE_IMPORT : public FileDialogParameters {

public:
    static constexpr FilterIndex FILTER_IDX_MOD = 1;
    static constexpr FilterIndex FILTER_IDX_TMC = 2;

    CString GetFilters() {
        return   "ProTracker Modules (*.mod)|*.mod|TMC song files (*.tmc,*.tm8)|*.tmc;*.tm8||";
    }

    std::vector<CString> GetExtensionArray() {

        return { ".mod", ".tmc; .tm8" }; // TODO Dangerous, two extension in one entry
    }
};

// ----------------------------------------------------------------------------
// File export dialog format selections
class FILE_EXPORT {

public:
    static constexpr int FILTER_IDX_STRIPPED_RMT = 1;
    static constexpr int FILTER_IDX_SIMPLE_ASM = 2;
    static constexpr int FILTER_IDX_SAPR = 3;
    static constexpr int FILTER_IDX_LZSS = 4;
    static constexpr int FILTER_IDX_SAP = 5;
    static constexpr int FILTER_IDX_XEX = 6;
    static constexpr int FILTER_IDX_RELOC_ASM = 7;
    static constexpr int FILTER_IDX_WAV = 8;
    static constexpr int FILTER_IDX_MIN = FILTER_IDX_STRIPPED_RMT;
    static constexpr int FILTER_IDX_MAX = FILTER_IDX_WAV;


};

#define FILE_EXPORT_FILTERS \
		"RMT stripped song file (*.rmt)|*.rmt|" \
		"ASM simple notation source (*.asm)|*.asm|" \
		"SAP-R data stream (*.sapr)|*.sapr|" \
		"Compressed SAP-R data stream (*.lzss)|*.lzss|" \
		"SAP file + LZSS driver (*.sap)|*.sap|" \
		"XEX Atari executable + LZSS driver (*.xex)|*.xex|" \
		"Relocatable ASM for RMTPlayer (*.asm)|*.asm|" \
		"WAV audio file (*.wav)|*.wav|" \
		"|"
#define FILE_EXPORT_EXTENSIONS_ARRAY { ".rmt",".asm",".sapr",".lzss",".sap",".xex",".asm",".wav" };
#define FILE_EXPORT_EXTENSIONS_LENGTH_ARRAY { 4, 4, 5, 5, 4, 4, 4, 4}



class SongIO
{

};

