package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@code CRmtView::Resize()}'s arithmetic, checked against the values the reference screenshots' own debug line reports. */
class TrackerPanelTest {

	@Test
	void layoutOfTheReferenceCaptureAtScaling200() {
		UiState uiState = new UiState();
		RmtOptions options = new RmtOptions();
		options.scalingPercentage = 200;

		TrackerPanel.computeLayout(uiState, options, 2556, 1308);

		assertEquals(1278, uiState.width); // GW=1278
		assertEquals(654, uiState.height); // GH=0654
		assertEquals(26, uiState.trackLines); // GTL=26
		assertEquals(13, uiState.lineY); // OL=13
	}

	@Test
	void layoutAtScaling100IsOneToOne() {
		UiState uiState = new UiState();
		RmtOptions options = new RmtOptions();
		options.scalingPercentage = 100; // not the default since 2026-10-05, and this test is about 100 %

		TrackerPanel.computeLayout(uiState, options, 800, 600);

		assertEquals(800, uiState.width);
		assertEquals(600, uiState.height);
		assertEquals((600 - (RmtScreenLayout.TRACKS_Y + 3 * 16) - 40) / 16, uiState.trackLines);
	}

	@Test
	void anOutOfRangeScalingIsResetTo100() {
		UiState uiState = new UiState();
		RmtOptions options = new RmtOptions();
		options.scalingPercentage = 350;

		TrackerPanel.computeLayout(uiState, options, 1000, 700);

		assertEquals(100, options.scalingPercentage);
		assertEquals(1000, uiState.width);
	}
}
