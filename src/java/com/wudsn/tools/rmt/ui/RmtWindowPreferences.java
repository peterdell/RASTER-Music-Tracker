package com.wudsn.tools.rmt.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.wudsn.tools.base.gui.MainWindowPreferences;

/**
 * The main window's geometry across runs - what {@code CMainFrame}
 * ({@code PreCreateWindow}/{@code OnClose}) keeps in the registry under
 * {@code RMT\Frame} as {@code Status/Top/Left/Bottom/Right}, here as WUDSN
 * Base's {@link MainWindowPreferences} on {@link Preferences} (per the
 * plan's decision 3: {@code rmt.ini} for the options, Preferences only for
 * the window geometry). As in C++, the geometry is restored only when a
 * complete one was saved before, and the location is kept on screen.
 */
public final class RmtWindowPreferences implements MainWindowPreferences {

	private static final String EXTENDED_STATE = "extendedState";
	private static final String X = "x";
	private static final String Y = "y";
	private static final String WIDTH = "width";
	private static final String HEIGHT = "height";

	private final Preferences node;

	public RmtWindowPreferences(Preferences node) {
		this.node = node;
	}

	/** The user's own node - C++'s {@code HKCU\Software\RASTER Music Tracker\RMT\Frame}. */
	public static RmtWindowPreferences forUser() {
		return new RmtWindowPreferences(Preferences.userNodeForPackage(RmtWindowPreferences.class).node("Frame"));
	}

	/** {@code PreCreateWindow}'s "only restore if there is a previously saved position": all five values present. */
	public boolean isStored() {
		return node.getInt(WIDTH, -1) != -1 && node.getInt(HEIGHT, -1) != -1 && node.getInt(X, Integer.MIN_VALUE) != Integer.MIN_VALUE && node.getInt(Y, Integer.MIN_VALUE) != Integer.MIN_VALUE && node.getInt(EXTENDED_STATE, -1) != -1;
	}

	@Override
	public void setMainWindowExtendedState(int mainWindowExtendedState) {
		node.putInt(EXTENDED_STATE, mainWindowExtendedState);
	}

	@Override
	public int getMainWindowExtendedState() {
		return node.getInt(EXTENDED_STATE, 0);
	}

	@Override
	public void setMainWindowLocation(Point mainWindowLocation) {
		if (mainWindowLocation != null) {
			node.putInt(X, mainWindowLocation.x);
			node.putInt(Y, mainWindowLocation.y);
		}
	}

	/** The saved location, moved so the window is "not completely out of sight" ({@code PreCreateWindow}'s clamp to the screen minus an icon); {@code null} if none was saved. */
	@Override
	public Point getMainWindowLocation() {
		int x = node.getInt(X, Integer.MIN_VALUE);
		int y = node.getInt(Y, Integer.MIN_VALUE);
		if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
			return null;
		}
		if (!GraphicsEnvironment.isHeadless()) {
			Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			int iconSize = 32;
			x = Math.max(screen.x, Math.min(x, screen.x + screen.width - iconSize));
			y = Math.max(screen.y, Math.min(y, screen.y + screen.height - iconSize));
		}
		return new Point(x, y);
	}

	@Override
	public void setMainWindowSize(Dimension mainWindowSize) {
		if (mainWindowSize != null) {
			node.putInt(WIDTH, mainWindowSize.width);
			node.putInt(HEIGHT, mainWindowSize.height);
		}
	}

	@Override
	public Dimension getMainWindowSize() {
		return new Dimension(node.getInt(WIDTH, 1000), node.getInt(HEIGHT, 650));
	}

	/** Writes the values through to the backing store; a failure is not worth failing the exit for. */
	public void flush() {
		try {
			node.flush();
		} catch (BackingStoreException ex) {
			// ignore
		}
	}
}
