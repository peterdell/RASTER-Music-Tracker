#pragma once

#include "StdAfx.h"
#include<vector>

// ----------------------------------------------------------------------------
// File open/save dialog format selections
class FILE_LOADSAVE {
public:
    static constexpr int FILTER_IDX_RMT = 1;
    static constexpr int FILTER_IDX_TXT = 2;
    static constexpr int FILTER_IDX_RMW = 3;
    static constexpr int FILTER_IDX_MIN = FILTER_IDX_RMT;
    static constexpr int FILTER_IDX_MAX = FILTER_IDX_RMW;

    static CString GetFilters() {
        return  "RMT song file (*.rmt)|*.rmt|TXT song file (*.txt)|*.txt|RMW song work file (*.rmw)|*.rmw||";
    }
                                
    static std::vector<CString> GetExtensionArray() {

        return { ".rmt", ".txt", ".rmw" };
    }
};

class SongIO
{

};

