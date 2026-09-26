package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * A screenshot of the real {@code Rmt.exe} from
 * {@code test-resources/ui-reference/}, with the tracker's client area
 * located inside it so a Java-rendered frame can be compared against it
 * pixel by pixel in logical coordinates.
 *
 * <p>The captures are whole-desktop PNGs. {@code Rmt.exe} renders 1:1 in
 * device pixels (its own debug line shows {@code GW=1278} for the 2556
 * pixel wide client area of a maximized window on the 2559 pixel desktop),
 * so at RMT's 200% scaling option every logical pixel is an exact 2x2
 * block and at 100% a single pixel - {@link #scale} - and the reference is
 * lossless either way. The client area's top-left is found as the first
 * row that is mostly {@link RgbColor#BACKGROUND} (the menu and toolbars
 * above it are light gray) and that row's first background pixel.
 */
final class ReferenceScreenshot {

	static final Path ROOT = Path.of("test-resources", "ui-reference");

	final BufferedImage image;
	final int scale;
	final int originX;
	final int originY;

	private ReferenceScreenshot(BufferedImage image, int scale, int originX, int originY) {
		this.image = image;
		this.scale = scale;
		this.originX = originX;
		this.originY = originY;
	}

	/** Loads {@code <scenario>/<name>.png}; {@code scale} is 2 for a {@code -scale200} capture, 1 otherwise. */
	static ReferenceScreenshot load(String scenario, String name, int scale) throws IOException {
		Path path = ROOT.resolve(scenario).resolve(name + ".png");
		BufferedImage image = ImageIO.read(Files.newInputStream(path));
		int background = RgbColor.BACKGROUND.getRGB() & 0xFFFFFF;

		for (int y = 0; y < image.getHeight(); y++) {
			int count = 0;
			int first = -1;
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) == background) {
					count++;
					if (first < 0) {
						first = x;
					}
				}
			}
			if (count > image.getWidth() / 2) {
				return new ReferenceScreenshot(image, scale, first, y);
			}
		}
		throw new IllegalStateException("No client area found in " + path);
	}

	/** The reference's color at the logical pixel {@code (x, y)}, as {@code 0xRRGGBB}. */
	int rgb(int x, int y) {
		return image.getRGB(originX + x * scale, originY + y * scale) & 0xFFFFFF;
	}

	/** The logical width of the client area the capture shows (its remaining device width divided by {@link #scale}). */
	int getLogicalWidth() {
		return (image.getWidth() - originX) / scale;
	}

	/**
	 * Compares {@code frame} against the reference inside each of
	 * {@code regions} (logical coordinates), ignoring the {@code excluded}
	 * rectangles. Reports the first mismatch per region with both colors
	 * and writes {@code target/ui-reference-diff/<label>.png} - the frame
	 * with every differing pixel painted magenta - so a failure can be
	 * looked at.
	 */
	void assertMatches(BufferedImage frame, String label, List<Rectangle> regions, List<Rectangle> excluded) throws IOException {
		// The 2x2 (or 1x1) blocks must be uniform, or the capture isn't the lossless 1:1 one this class assumes.
		for (Rectangle region : regions) {
			for (int y = region.y; y < region.y + region.height; y += 7) {
				for (int x = region.x; x < region.x + region.width; x += 5) {
					int reference = rgb(x, y);
					for (int dy = 0; dy < scale; dy++) {
						for (int dx = 0; dx < scale; dx++) {
							int other = image.getRGB(originX + x * scale + dx, originY + y * scale + dy) & 0xFFFFFF;
							assertTrue(other == reference, String.format("Reference %s is not a uniform %dx scale at logical (%d,%d): %06X vs %06X", label, scale, x, y, reference, other));
						}
					}
				}
			}
		}

		BufferedImage diff = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
		diff.getGraphics().drawImage(frame, 0, 0, null);
		StringBuilder problems = new StringBuilder();
		int differing = 0;
		for (Rectangle region : regions) {
			String first = null;
			for (int y = region.y; y < region.y + region.height; y++) {
				for (int x = region.x; x < region.x + region.width; x++) {
					if (isExcluded(x, y, excluded)) {
						continue;
					}
					int expected = rgb(x, y);
					int actual = frame.getRGB(x, y) & 0xFFFFFF;
					if (expected != actual) {
						differing++;
						diff.setRGB(x, y, 0xFF00FF);
						if (first == null) {
							first = String.format("region %s first differs at (%d,%d): Rmt.exe %06X, Java %06X", region, x, y, expected, actual);
						}
					}
				}
			}
			if (first != null) {
				problems.append('\n').append(first);
			}
		}
		if (differing > 0) {
			Path out = Path.of("target", "ui-reference-diff", label + ".png");
			Files.createDirectories(out.getParent());
			ImageIO.write(diff, "png", out.toFile());
			fail(differing + " pixel(s) differ from " + label + " (diff written to " + out + "):" + problems);
		}
	}

	private static boolean isExcluded(int x, int y, List<Rectangle> excluded) {
		for (Rectangle r : excluded) {
			if (r.contains(x, y)) {
				return true;
			}
		}
		return false;
	}
}
