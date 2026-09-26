package com.wudsn.tools.rmt.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The tracker's one drawing surface - the port of {@code CRmtView}'s
 * rendering side (RmtView.cpp: {@code Resize()}, {@code OnTimer()},
 * {@code OnDraw()}, {@code DrawAll()}). C++ composes every frame in full
 * into an off-screen bitmap at the <em>logical</em> resolution
 * ({@code g_width = m_width * 100 / g_scaling_percentage}, same for the
 * height) and {@code StretchBlt}s it onto the window; here the bitmap is
 * the {@link #canvas} {@link BufferedImage} and the blit is a
 * nearest-neighbor {@code drawImage} in {@link #paintComponent}.
 *
 * <p><b>Timing.</b> C++ re-arms a one-shot timer with the 16/16/15 ms tick
 * cycle ({@code m_timerDisplayTick}) and each tick calls
 * {@code RefreshScreen()}, which sets the {@code g_screenupdate} dirty flag
 * and invalidates the window; {@code OnDraw} then runs {@code Resize()},
 * {@code RespectBoundaries()} and {@code DrawAll()} only when the flag is
 * set. {@link #timer} does the same with {@link Timer#setDelay} and
 * {@link #refreshScreen()}. The one difference Swing forces: a paint that
 * finds the flag clear still has to put the canvas on screen (Swing does
 * not preserve window contents), so the blit is unconditional and only the
 * re-rendering is gated.
 *
 * <p><b>HiDPI.</b> All of {@code CRmtView}'s sizes are device pixels, and
 * the reference {@code Rmt.exe} screenshots confirm it renders 1:1 at 150%
 * Windows scaling (a 2556-pixel-wide client area reports {@code GW=1278}
 * at RMT's 200% option). Swing hands this panel a {@link Graphics2D} whose
 * transform already carries the display scale, and drawing a pixel image
 * through a fractional scale gives irregular row/column doubling (the
 * dis6502 {@code ComputerFont} lesson). So {@link #paintComponent} reads
 * that scale, sizes the canvas from the panel's <em>device</em> size, and
 * blits with an integer-translation-only transform - exactly one canvas
 * pixel per {@code scalingPercentage/100} device pixels, never through
 * the 1.5x.
 */
public final class TrackerPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** {@code CRmtView::m_timerDisplayTick}. */
	private static final int[] TIMER_DISPLAY_TICK = { 16, 16, 15 };

	private final RmtSession session;
	private final CanvasXY canvasXY;
	private final SongUI songUI;
	private final SongInput songInput;
	private final Timer timer;

	/** {@code CRmtView::m_width/m_height} - the client area in device pixels. */
	private int width;
	private int height;
	private BufferedImage canvas;
	/** The display scale of the last paint - what a Swing (user-space) mouse coordinate is multiplied by to get device pixels. */
	private double deviceScale = 1.0;
	private int tickCount;

	public TrackerPanel(RmtSession session) {
		this.session = session;
		this.canvasXY = new CanvasXY(CanvasXY.loadGlyphSheet(), session.uiState);
		this.songUI = new SongUI(session);
		this.songInput = new SongInput(session);
		songUI.setCanvas(canvasXY);

		setBackground(RgbColor.BACKGROUND);
		setOpaque(true);
		setFocusable(true);
		setFocusTraversalKeysEnabled(false); // Tab is a tracker key, not focus traversal
		setPreferredSize(new Dimension(1000, 650));

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				songInput.keyDown(e.getKeyCode());
				refreshScreen();
			}

			@Override
			public void keyReleased(KeyEvent e) {
				songInput.keyUp(e.getKeyCode());
			}
		});
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				storeMousePosition(e);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				storeMousePosition(e);
			}
		});

		timer = new Timer(TIMER_DISPLAY_TICK[0], null);
		timer.addActionListener(e -> {
			tickCount++;
			timer.setDelay(TIMER_DISPLAY_TICK[tickCount % 3]);
			refreshScreen();
		});
		timer.setRepeats(true);
	}

	/** {@code CRmtView::OnInitialUpdate()}'s {@code SetTimer(1, 16, NULL)}. */
	public void startDisplayTimer() {
		timer.start();
	}

	/** {@code CRmtView::OnDestroy()}'s {@code KillTimer}. */
	public void stopDisplayTimer() {
		timer.stop();
	}

	/** {@code RefreshScreen()} (GuiHelpers.cpp): mark the frame dirty and ask for a paint. */
	public void refreshScreen() {
		session.uiState.screenUpdate = true;
		repaint();
	}

	public SongInput getSongInput() {
		return songInput;
	}

	/** The composed logical frame - for tests and for saving reference images; {@code null} until the first paint. */
	public BufferedImage getCanvas() {
		return canvas;
	}

	/** {@code CRmtView::MouseAction()}'s first lines: the mouse position in logical canvas coordinates ({@code INVERSE_SCALE} of the device position). The hit-testing itself comes with B4. */
	private void storeMousePosition(MouseEvent e) {
		UiState ui = session.uiState;
		int scaling = session.options.scalingPercentage;
		ui.mouseX = (int) Math.round(e.getX() * deviceScale) * 100 / scaling;
		ui.mouseY = (int) Math.round(e.getY() * deviceScale) * 100 / scaling;
	}

	/**
	 * {@code CRmtView::Resize()}'s arithmetic, separated from the bitmap
	 * handling so it can be tested: the logical size and the derived
	 * track-line count/center row for a client area of {@code width} x
	 * {@code height} device pixels. Also applies C++'s fail-safe of resetting
	 * an out-of-range scaling option to 100.
	 */
	static void computeLayout(UiState uiState, RmtOptions options, int width, int height) {
		// If the values are beyond those limits, reset the default scaling as a failsafe
		if (options.scalingPercentage > 300 || options.scalingPercentage < 100) {
			options.scalingPercentage = 100;
		}

		uiState.width = width * 100 / options.scalingPercentage;
		uiState.height = height * 100 / options.scalingPercentage;

		// The number of track lines that can be displayed is based on the scaled window height
		uiState.trackLines = (uiState.height - (RmtScreenLayout.TRACKS_Y + 3 * 16) - 40) / 16;
		uiState.lineY = uiState.trackLines / 2;
	}

	/** {@code CRmtView::Resize()}: returns without changing anything if the window was not resized. (Not named {@code resize}: {@code java.awt.Component} has a deprecated public one.) */
	private void resizeCanvas(int newWidth, int newHeight) {
		if (newWidth == width && newHeight == height && canvas != null) {
			return;
		}
		width = newWidth;
		height = newHeight;
		computeLayout(session.uiState, session.options, width, height);

		canvas = new BufferedImage(Math.max(1, session.uiState.width), Math.max(1, session.uiState.height), BufferedImage.TYPE_INT_RGB);
		canvasXY.setTarget(canvas);
		canvasXY.setLineColor(RgbColor.LINES); // m_pen1
	}

	/** {@code CRmtView::OnDraw()}. */
	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		AffineTransform transform = g2.getTransform();
		deviceScale = transform.getScaleX();
		int deviceWidth = (int) Math.round(getWidth() * transform.getScaleX());
		int deviceHeight = (int) Math.round(getHeight() * transform.getScaleY());

		UiState ui = session.uiState;
		if (ui.screenUpdate || canvas == null) {
			resizeCanvas(deviceWidth, deviceHeight);
			session.song.respectBoundaries(session.tracks4_8);
			songUI.drawAll();
		}
		ui.screenUpdate = false;

		// StretchBlt(0, 0, m_width, m_height, &m_mem_dc, 0, 0, g_width, g_height, SRCCOPY) - in device pixels, see the class javadoc.
		g2.setTransform(AffineTransform.getTranslateInstance(Math.round(transform.getTranslateX()), Math.round(transform.getTranslateY())));
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g2.setColor(RgbColor.BACKGROUND);
		g2.fillRect(0, 0, deviceWidth, deviceHeight);
		g2.drawImage(canvas, 0, 0, width, height, 0, 0, canvas.getWidth(), canvas.getHeight(), null);
		g2.setTransform(transform);
	}
}
