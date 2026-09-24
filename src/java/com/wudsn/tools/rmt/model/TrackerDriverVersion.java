package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ enum class TrackerDriverVersion (TrackerDriverVersion.h).
 * C++'s explicit backing values (NONE=0 .. PATCH_PRINCE_OF_PERSIA=7) are
 * consecutive from 0 in the same order declared here, so this enum's own
 * {@link #ordinal()} already matches them - no explicit backing field needed.
 */
public enum TrackerDriverVersion {
	NONE, UNPATCHED, UNPATCHED_WITH_TUNING, PATCH3, PATCH6, PATCH8, PATCH16, PATCH_PRINCE_OF_PERSIA
}
