#pragma once

#include "StdAfx.h"
#include<vector>

class FileDialogParameters {

public:
    typedef int FilterIndex;
    virtual CString GetFilters() = 0;

    bool IsValidFilterIndex(const FilterIndex filterIndex) {
        return (filterIndex >= 1) && (filterIndex <= GetExtensionArray().size());
    }

    void EnsureFileExtension(CString& fileName, const FilterIndex filterIndex) {
        auto extensionArray = GetExtensionArray();
        auto extensionLength = extensionArray[filterIndex - 1].GetLength();

        auto ext = fileName.Right(extensionLength).MakeLower();
        if (ext != extensionArray[filterIndex - 1]) {
            fileName += extensionArray[filterIndex - 1];
        }
    }

protected:
    virtual std::vector<CString> GetExtensionArray() { return {}; }


};


// File open/save dialog format selections
class FILE_LOADSAVE : public FileDialogParameters {
public:
    static constexpr FilterIndex RMT = 1;
    static constexpr FilterIndex TXT = 2;
    static constexpr FilterIndex RMW = 3;


    CString GetFilters() {
        return  "RMT song file (*.rmt)|*.rmt|"\
            "TXT song file(*.txt)|*.txt|"\
            "RMW song work file(*.rmw)|*.rmw|"\
            "|";
    }


protected:
    std::vector<CString> GetExtensionArray() {

        return { ".rmt", ".txt", ".rmw" };
    }
};

// File import dialog format selections
class FILE_IMPORT : public FileDialogParameters {

public:
    static constexpr FilterIndex MOD = 1;
    static constexpr FilterIndex TMC = 2;

    CString GetFilters() {
        return   "ProTracker Modules (*.mod)|*.mod|"\
            "TMC Song Files (*.tmc, *.tm8)|*.tmc; *.tm8|"\
            "|";
    }


protected:
    std::vector<CString> GetExtensionArray() {

        return { ".mod", ".tmc; .tm8" }; // TODO Dangerous, two extension in one entry
    }
};

// ----------------------------------------------------------------------------
// File export dialog format selections
class FILE_EXPORT : public FileDialogParameters {


public:
    static constexpr int STRIPPED_RMT = 1;
    static constexpr int SIMPLE_ASM = 2;
    static constexpr int SAPR = 3;
    static constexpr int LZSS = 4;
    static constexpr int SAP = 5;
    static constexpr int XEX = 6;
    static constexpr int RELOC_ASM= 7;
    static constexpr int FILTER_IDX_WAV = 8;

    CString GetFilters() {
        return 		"RMT stripped song file (*.rmt)|*.rmt|" \
            "ASM simple notation source (*.asm)|*.asm|" \
            "SAP-R data stream (*.sapr)|*.sapr|" \
            "Compressed SAP-R data stream (*.lzss)|*.lzss|" \
            "SAP file + LZSS driver (*.sap)|*.sap|" \
            "XEX Atari executable + LZSS driver (*.xex)|*.xex|" \
            "Relocatable ASM for RMTPlayer (*.asm)|*.asm|" \
            "WAV audio file (*.wav)|*.wav|" \
            "|";
    }


protected:
    std::vector<CString> GetExtensionArray() {

        return { ".rmt",".asm",".sapr",".lzss",".sap",".xex",".asm",".wav" };
    }

};


class SongIO
{

};

