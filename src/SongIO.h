#pragma once


// ----------------------------------------------------------------------------
// File open/save dialog format selections
// .rmt / .txt / .rmw
#define FILE_LOADSAVE_FILTERS "RMT song file (*.rmt)|*.rmt|TXT song file (*.txt)|*.txt|RMW song work file (*.rmw)|*.rmw||"

#define FILE_LOADSAVE_EXTENSIONS_ARRAY { ".rmt",".txt",".rmw" }

class FILE_LOADSAVE {
public:
    static constexpr int FILTER_IDX_RMT = 1;
    static constexpr int FILTER_IDX_TXT = 2;
    static constexpr int FILTER_IDX_RMW = 3;
    static constexpr int FILTER_IDX_MIN = FILTER_IDX_RMT;
    static constexpr int FILTER_IDX_MAX = FILTER_IDX_RMW;

};

class SongIO
{

};

