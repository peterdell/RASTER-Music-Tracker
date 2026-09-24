package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/StringUtilityTests.cpp. */
class StringUtilityTest {

	@Test
	void matchesSameCase() {
		assertTrue(StringUtility.endsWithNoCase("song.rmt", ".rmt"));
	}

	@Test
	void matchesDifferentCase() {
		assertTrue(StringUtility.endsWithNoCase("song.RMT", ".rmt"));
		assertTrue(StringUtility.endsWithNoCase("song.rmt", ".RMT"));
	}

	@Test
	void doesNotMatchDifferentSuffix() {
		assertFalse(StringUtility.endsWithNoCase("song.rmt", ".rti"));
	}

	@Test
	void emptySuffixAlwaysMatches() {
		assertTrue(StringUtility.endsWithNoCase("song.rmt", ""));
		assertTrue(StringUtility.endsWithNoCase("", ""));
	}

	@Test
	void suffixLongerThanStringDoesNotMatch() {
		assertFalse(StringUtility.endsWithNoCase("rmt", "song.rmt"));
	}
}
