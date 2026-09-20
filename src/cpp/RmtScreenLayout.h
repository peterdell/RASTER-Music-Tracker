
class CRmtScreenLayout {
public:
    static constexpr int CHARACTER_WIDTH = 8;
    static constexpr int CHARACTER_HEIGHT = 16;

    static constexpr int TRACKS_X = 2 * CHARACTER_WIDTH;
    static constexpr int TRACKS_Y = 8 * CHARACTER_HEIGHT + 8;

    static constexpr int SONG_X = 96 * CHARACTER_WIDTH;
    static constexpr int SONG_Y = 1 * CHARACTER_HEIGHT;

    // Info area
    // Shown at top-left
    // 6 lines of text
    static constexpr int INFO_X = 2 * CHARACTER_WIDTH;
    static constexpr int INFO_Y = 1 * CHARACTER_HEIGHT;

    static constexpr int INFO_Y_LINE_1 = INFO_Y;
    static constexpr int INFO_Y_LINE_2 = INFO_Y + 1 * CHARACTER_HEIGHT;
    static constexpr int INFO_Y_LINE_3 = INFO_Y + 2 * CHARACTER_HEIGHT;
    static constexpr int INFO_Y_LINE_4 = INFO_Y + 3 * CHARACTER_HEIGHT;
    static constexpr int INFO_Y_LINE_5 = INFO_Y + 4 * CHARACTER_HEIGHT;
    static constexpr int INFO_Y_LINE_6 = INFO_Y + 5 * CHARACTER_HEIGHT;

};