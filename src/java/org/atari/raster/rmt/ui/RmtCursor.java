package org.atari.raster.rmt.ui;

/**
 * The mouse cursor shapes {@code CRmtView::MouseAction()} switches between
 * ({@code m_cursororig} plus the five custom {@code .cur} resources, which
 * {@link CursorLoader} reads).
 */
public enum RmtCursor {
	ARROW(null), //
	GOTO("cursor_goto.cur"), //
	CHANNEL_ON_OFF("cursor_channel_on_off.cur"), //
	DIALOG("cursor_goto_dialog.cur"), //
	ENVELOPE_VOLUME("cursor_envelope_volume.cur"), //
	SET_POSITION("cursor_set_position.cur");

	/** The {@code .cur} resource next to {@link CursorLoader}, or {@code null} for the system arrow. */
	public final String resourceName;

	RmtCursor(String resourceName) {
		this.resourceName = resourceName;
	}
}
