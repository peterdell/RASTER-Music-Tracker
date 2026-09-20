#pragma once



// ----------------------------------------------------------------------------
// GUI color setup
// The text is defined in IDB_GFX as bitmap font in various colors
// Text color is defined as a vertical offset in the bitmap font file
enum class TextColor : int {
    WHITE = 0,
    GRAY = 1,
    YELLOW = 2,
    INVERSE_BLUE = 3,
    INVERSE_WHITE = 4,
    CYAN = 5,
    RED = 6,
    INVERSE_RED = 9,
    EXTRA = 10,
    GREEN = 11,
    DARK_GRAY = 12,
    BLUE = 13,
    TURQUOISE = 14
};


class LogicalTextColor {
public:

    static const TextColor SELECTED = TextColor::INVERSE_RED;		// Highlight color
    static const TextColor SELECTED_PROVE = TextColor::INVERSE_BLUE;		// Highlight color in PROVE mode
    static const TextColor HOVERED = TextColor::INVERSE_WHITE;	// Highlight color from cursor hover
};

enum class TextMiniColor : int {
    GRAY = 0,
    BLUE = 1,
    WHITE = 2,
    YELLOW = 3
};
