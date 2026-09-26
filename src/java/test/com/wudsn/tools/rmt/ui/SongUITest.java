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

import com.wudsn.tools.rmt.model.Part;
import com.wudsn.tools.rmt.model.PlayMode;

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

	/** The whole client area. */
	private static final Rectangle FULL_FRAME = new Rectangle(0, 0, WIDTH, HEIGHT);
	/** The "%1.2f FPS" read-out - the one genuinely volatile thing in a non-playing capture (a frame-rate measurement). */
	private static final Rectangle FPS_READOUT = new Rectangle(560 - 9 * 8, RmtScreenLayout.INFO_Y_LINE_1, 80, 16);
	private static final List<Rectangle> VOLATILE = List.of(FPS_READOUT);

	@Test
	void song1MonoTracksScreenMatchesRmtExe() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		// The capture's own debug line: PX=0632 PY=0279 (the mouse position is part of the frame)
		session.uiState.mouseX = 632;
		session.uiState.mouseY = 279;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song1-mono", "tracks-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		reference.assertMatches(frame, "song1-mono-tracks", List.of(FULL_FRAME), VOLATILE);
	}

	@Test
	void song0EmptyStereoTracksScreenMatchesRmtExe() throws IOException {
		RmtSession session = new RmtSession(); // exactly what Rmt.exe starts with
		session.uiState.mouseX = 27;
		session.uiState.mouseY = 2;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song0-empty", "tracks-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		reference.assertMatches(frame, "song0-empty-tracks", List.of(FULL_FRAME), VOLATILE);
	}

	/**
	 * The GOTO capture was taken while playing with follow-play on: song line
	 * 05 is both active and playing, the cursor/play line is $3C (its own
	 * debug line says {@code TA=60}), and the frame is smooth-scrolled - the
	 * track rows sit 8 pixels lower than at rest ({@code speeda * 16 / speed -
	 * 8 = 8} at speed $0A, i.e. speeda 10) and the song rows 7 pixels higher
	 * ({@code trackplayline * 16 / 64 - 8 = 7}). Everything that depends on
	 * the audio state (TIME/BPM, analyzer bars, POKEY values) is excluded;
	 * the GOTO line in the song block, the "GO TO LINE 00" row that ends the
	 * pattern in the tracks, the smooth-scrolled rows and the debug line
	 * are compared.
	 */
	@Test
	void song1MonoGotoLineWhilePlayingMatchesRmtExe() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		session.song.songSetActiveLine(5);
		session.song.songSetPlayLine(5);
		session.song.setActiveLine(0x3C);
		session.song.setPlayLine(0x3C);
		session.song.setPlayMode(PlayMode.PLAY_SONG);
		session.song.setFollowPlayMode(true);
		session.song.setSpeeda(10);
		session.uiState.mouseX = 171;
		session.uiState.mouseY = 178;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song1-mono", "tracks-goto-scale200", 2);

		Rectangle infoLines2To6 = new Rectangle(0, RmtScreenLayout.INFO_Y_LINE_2, 560, 5 * 16);
		Rectangle songBlock = new Rectangle(568, 0, 8 * 18, 180);
		Rectangle tracks = new Rectangle(0, RmtScreenLayout.TRACKS_Y, 551, HEIGHT - RmtScreenLayout.TRACKS_Y);
		reference.assertMatches(frame, "song1-mono-tracks-goto", List.of(infoLines2To6, songBlock, tracks), VOLATILE);
	}

	@Test
	void song0EmptyStereoInstrumentScreenMatchesRmtExe() throws IOException {
		RmtSession session = new RmtSession();
		// The capture shows the instrument screen with the instrument part focused (the envelope's first volume digit is highlighted)
		session.uiState.activeTi = Part.PART_INSTRUMENTS;
		session.uiState.activePart = Part.PART_INSTRUMENTS;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song0-empty", "instruments-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		reference.assertMatches(frame, "song0-empty-instruments", List.of(FULL_FRAME), VOLATILE);
	}

	@Test
	void song1MonoInstrumentScreenMatchesRmtExe() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		session.uiState.activeTi = Part.PART_INSTRUMENTS;
		session.uiState.activePart = Part.PART_INSTRUMENTS;

		BufferedImage frame = renderFrame(session);
		ReferenceScreenshot reference = ReferenceScreenshot.load("song1-mono", "instruments-scale200", 2);
		assertEquals(WIDTH, reference.getLogicalWidth());

		// This capture was taken after playing part of the song (TIME 0:08.12, BPM, song line 03, a
		// yellow play line), so only the parts that don't depend on the play position are compared:
		// the info area below the TIME line and the whole instrument editor.
		Rectangle infoLines2To6 = new Rectangle(0, RmtScreenLayout.INFO_Y_LINE_2, 560, 6 * 16);
		Rectangle instrumentEditor = new Rectangle(0, InstrumentsUI.Y - 8, 8 * 96, HEIGHT - (InstrumentsUI.Y - 8));
		reference.assertMatches(frame, "song1-mono-instruments", List.of(infoLines2To6, instrumentEditor), VOLATILE);
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

	@Test
	void song1MonoInstrumentFrameMatchesGolden() throws IOException {
		RmtSession session = openReference("song1-mono", "Delta.rmt");
		session.uiState.activeTi = Part.PART_INSTRUMENTS;
		session.uiState.activePart = Part.PART_INSTRUMENTS;
		assertGolden(renderFrame(session), "song1-mono-instruments");
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
