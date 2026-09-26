package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Full-frame tests of {@link SongUI#drawAll} - the first frame of the
 * tracks screen, rendered headless at the logical size the reference
 * screenshots were taken at (1278x654, i.e. {@code GW=1278 GH=0654} in
 * their own debug line).
 *
 * <p>Two kinds of checks: (1) against the real {@code Rmt.exe} captures in
 * {@code test-resources/ui-reference/} (see {@link ReferenceScreenshot}),
 * restricted to the regions B1 draws - the info area, the SONG block and
 * the tracks screen - and excluding what B2 still owes (the volume
 * analyzer strip above the track headers, the FPS read-out, the POKEY
 * register/tuning panel); (2) against golden PNGs of the Java frame itself
 * under {@code golden/}, which catch any change to the whole frame. Run
 * with {@code -Drmt.updateGoldens=true} to rewrite the goldens after an
 * intended change.
 */
class SongUITest {

	private static final int WIDTH = 1278;
	private static final int HEIGHT = 654;

	private static final Path GOLDEN_DIR = Path.of("src", "java", "test", "com", "wudsn", "tools", "rmt", "ui", "golden");

	private static BufferedImage sheet;

	@BeforeAll
	static void loadSheet() {
		sheet = CanvasXY.loadGlyphSheet();
	}

	/** The panel's paint path without the panel: layout for a 1278x654 client area at RMT 100%, then {@code drawAll()}. */
	static BufferedImage renderFrame(RmtSession session) {
		TrackerPanel.computeLayout(session.uiState, session.options, WIDTH, HEIGHT);
		session.song.respectBoundaries(session.tracks4_8);

		BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		CanvasXY canvasXY = new CanvasXY(sheet, session.uiState);
		canvasXY.setTarget(frame);
		canvasXY.setLineColor(RgbColor.LINES);
		SongUI songUI = new SongUI(session);
		songUI.setCanvas(canvasXY);
		songUI.drawAll();
		return frame;
	}

	private static RmtSession openReference(String scenario, String rmtName) throws IOException {
		RmtSession session = new RmtSession();
		assertTrue(session.openRmtFile(ReferenceScreenshot.ROOT.resolve(scenario).resolve(rmtName)));
		return session;
	}

	/** The info area (6 lines) without the FPS read-out (B2). */
	private static final Rectangle INFO_AREA = new Rectangle(0, 0, 560, 104);
	private static final Rectangle FPS_READOUT = new Rectangle(560 - 9 * 8, RmtScreenLayout.INFO_Y_LINE_1, 80, 16);
	/** The volume analyzer strip above the track headers (B2), which in stereo reaches under the SONG block. */
	private static final Rectangle ANALYZER_STRIP = new Rectangle(0, RmtScreenLayout.TRACKS_Y - 16, WIDTH, 16);
	private static final List<Rectangle> B2_EXCLUSIONS = List.of(FPS_READOUT, ANALYZER_STRIP);
	/** The tracks screen from the "TRACK Lx" headers down to and including the debug line; the analyzer strip above (y 120-135) is B2. */
	private static final Rectangle TRACKS_MONO = new Rectangle(0, RmtScreenLayout.TRACKS_Y, 551, HEIGHT - 16 - RmtScreenLayout.TRACKS_Y);
	private static final Rectangle TRACKS_STEREO = new Rectangle(0, RmtScreenLayout.TRACKS_Y, 1064, HEIGHT - 16 - RmtScreenLayout.TRACKS_Y);

	@Test
	void song1MonoTracksScreenMatchesRmtExe() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		// The capture's own debug line: PX=0632 PY=0279 (the mouse position is part of the frame)
		session.uiState.mouseX = 632;
		session.uiState.mouseY = 279;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song1-mono", "tracks-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		Rectangle songBlock = new Rectangle(568, 0, 8 * 18, 180); // SONG_OFFSET_X for a mono song = SONG_X - 200; the tuning/POKEY panel (B2) starts at y 186
		reference.assertMatches(frame, "song1-mono-tracks", List.of(INFO_AREA, songBlock, TRACKS_MONO), B2_EXCLUSIONS);
	}

	@Test
	void song0EmptyStereoTracksScreenMatchesRmtExe() throws IOException {
		RmtSession session = new RmtSession(); // exactly what Rmt.exe starts with
		session.uiState.mouseX = 27;
		session.uiState.mouseY = 2;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song0-empty", "tracks-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		Rectangle songBlock = new Rectangle(828, 0, 8 * 30, 144); // stereo, narrower than 1320: SONG_X - 250 + 310, compact 5-line block
		reference.assertMatches(frame, "song0-empty-tracks", List.of(INFO_AREA, songBlock, TRACKS_STEREO), B2_EXCLUSIONS);
	}

	@Test
	void song1MonoFirstFrameMatchesGolden() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		assertGolden(renderFrame(session), "song1-mono-tracks");
	}

	@Test
	void song0EmptyFirstFrameMatchesGolden() throws IOException {
		assertGolden(renderFrame(new RmtSession()), "song0-empty-tracks");
	}

	private static void assertGolden(BufferedImage frame, String name) throws IOException {
		Path golden = GOLDEN_DIR.resolve(name + ".png");
		if (Boolean.getBoolean("rmt.updateGoldens") || !Files.exists(golden)) {
			Files.createDirectories(GOLDEN_DIR);
			ImageIO.write(frame, "png", golden.toFile());
			if (!Boolean.getBoolean("rmt.updateGoldens")) {
				fail("Golden " + golden + " did not exist - written now, review it and re-run");
			}
			return;
		}
		BufferedImage expected = ImageIO.read(golden.toFile());
		assertEquals(expected.getWidth(), frame.getWidth());
		assertEquals(expected.getHeight(), frame.getHeight());
		for (int y = 0; y < expected.getHeight(); y++) {
			for (int x = 0; x < expected.getWidth(); x++) {
				if ((expected.getRGB(x, y) & 0xFFFFFF) != (frame.getRGB(x, y) & 0xFFFFFF)) {
					Path out = Path.of("target", "ui-reference-diff", name + "-golden-actual.png");
					Files.createDirectories(out.getParent());
					ImageIO.write(frame, "png", out.toFile());
					fail(String.format("Frame differs from golden %s at (%d,%d): golden %06X, actual %06X (actual written to %s)", golden, x, y, expected.getRGB(x, y) & 0xFFFFFF, frame.getRGB(x, y) & 0xFFFFFF, out));
				}
			}
		}
	}
}
