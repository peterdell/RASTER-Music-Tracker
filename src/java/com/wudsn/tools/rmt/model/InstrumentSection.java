package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ enum class InstrumentSection (InstrumentTypes.h) -
 * which section of an instrument's data is currently being edited. C++'s
 * explicit NONE = -1 backing value isn't preserved (nothing reads this
 * enum's underlying numeric value anywhere in the source, only compares it
 * for equality).
 */
public enum InstrumentSection {
	NONE, NAME, PARAMETERS, ENVELOPE, NOTETABLE
}
