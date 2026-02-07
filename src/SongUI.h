#pragma once

class CSong;

class CSongUI
{

public:
    CSongUI(CSong& song);

    void DrawAnalyzer();

private:
    CSong* m_song;
};