package me.zssu.ime.keyboard

/** Colour of the letters in the flick guide: the theme's own colours, or always white / black. */
enum class GuideTextColor {
    THEME, WHITE, BLACK;

    companion object {
        fun of(name: String?): GuideTextColor = entries.firstOrNull { it.name == name } ?: THEME
    }
}

/** How the flick practice hint is drawn: a triangle at 70% size, or a full-size square cell like the other guide cells. */
enum class PracticeShape {
    TRIANGLE, SQUARE;

    companion object {
        fun of(name: String?): PracticeShape = entries.firstOrNull { it.name == name } ?: TRIANGLE
    }
}
