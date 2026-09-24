package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ enum class SAPROptimization (lzss_sap.h). A plain
 * Java {@code enum} - unlike some other ported enums, nothing in
 * {@code LzssTests.cpp} needs a synthetic out-of-range value here.
 */
public enum SapROptimization {
	NONE,
	AUDC,
	AUDCTL,
	AUDF,
	AUDC_AUDF,
	AUDCTL_AUDC,
	AUDCTL_AUDF,
	ALL
}
