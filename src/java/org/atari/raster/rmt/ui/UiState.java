package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.EditMode;
import org.atari.raster.rmt.model.Part;

/**
 * The transient editor state that C++ keeps in {@code Global.h} globals
 * ({@code g_activepart}, {@code g_prove}, {@code g_mouse}, {@code g_shiftkey},
 * ...) - one plain mutable holder, passed explicitly to whatever reads it,
 * per this port's "C++ global -> explicit parameter" idiom (no static
 * singleton). Persisted options ({@code g_scaling_percentage},
 * {@code TViewState g_view}, {@code g_keyboard_*}, ...) are deliberately
 * not here - they belong to {@link RmtOptions}, which {@link RmtConfig}
 * persists as {@code rmt.ini}.
 *
 * <p>Field names follow the C++ globals minus their {@code g_} prefix so
 * the drawing/input code ports read side by side. Defaults match
 * {@code CRmtView::OnInitialUpdate()} (both parts start at
 * {@link Part#PART_TRACKS}, no modifier held) and the C++ static
 * zero-initialization of everything else.
 */
public final class UiState {

	/** {@code g_activepart} - which part has keyboard focus. */
	public Part activePart = Part.PART_TRACKS;
	/** {@code g_active_ti} - which primary screen is shown below the info area: tracks or instruments. */
	public Part activeTi = Part.PART_TRACKS;
	/** {@code g_prove}. */
	public EditMode editMode = EditMode.EDIT_MODE;

	public boolean shiftKey;
	public boolean controlKey;
	public boolean altKey;
	/** The Caps Lock toggle state ({@code GetKeyState(VK_CAPITAL)}), read per key event by the key handlers. */
	public boolean capsLock;

	/** {@code g_mouse.pointX/pointY/button/wheelDelta} - in logical (unscaled) canvas coordinates. */
	public int mouseX;
	public int mouseY;
	public int mouseButton;
	public int mouseWheelDelta;
	/** {@code g_mousebutt} - the buttons currently held ({@link MouseInput#MK_LBUTTON}/{@link MouseInput#MK_RBUTTON}), so dragging over the envelope keeps drawing. */
	public int mouseButtonsHeld;
	/** The cursor shape the last {@code MouseAction()} chose ({@code SetCursor(m_cursor...)}). */
	public RmtCursor cursor = RmtCursor.ARROW;

	/** {@code g_width}/{@code g_height} - the logical (unscaled) canvas size, set by the view's resize handling. */
	public int width;
	public int height;
	/** {@code g_tracklines} - how many track lines fit on screen at the current height. */
	public int trackLines;
	/** {@code g_line_y} - the on-screen row the active track line is centered on. */
	public int lineY;
	/** {@code g_cursoractview}. */
	public int cursorActView;

	/** {@code g_screenupdate} - the "redraw on next paint" dirty flag. */
	public boolean screenUpdate;
	/** {@code g_timerGlobalCount} - ticks forever once started. */
	public int timerGlobalCount;
	/** {@code g_changes} - unsaved modifications exist. */
	public boolean changes;

	/** {@code g_isEditingInstrumentName}. */
	public boolean isEditingInstrumentName;
	/** {@code is_editing_infos} - the song name field is being typed into. */
	public boolean isEditingInfos;
	/** {@code g_lastKeyPressed} - for debugging key input. */
	public int lastKeyPressed;

	/** {@code g_playtime} - frames played since Play started; drives the TIME/BPM counter. */
	public int playTime;
	/** {@code last_fps} - the debug FPS read-out, measured by the view's {@code GetFPS()}. */
	public double lastFps;
	/** {@code g_respectvolume} - the F11 "respect volume" toggle (transient: {@code CSong::ClearSong()} resets it, {@code rmt.ini} never stores it). */
	public boolean respectVolume;

	/**
	 * Ported from {@code SwitchEditMode()} (Global.cpp): the Edit/Jam toggle.
	 * Edit -> mono jam; mono jam -> stereo jam on a stereo song, else back to
	 * {@code targetEditMode}; a special (explorer) mode always drops back to
	 * edit mode.
	 */
	public void switchEditMode(EditMode targetEditMode, boolean stereo) {
		EditMode mode = editMode;
		if (mode == EditMode.EDIT_MODE) {
			mode = EditMode.JAM_MONO_MODE;
		} else if (mode.isSpecialProveMode()) {
			mode = EditMode.EDIT_MODE;
		} // Disable the special test modes immediately
		else {
			// Mono Jam or stereo jam?
			if (mode == EditMode.JAM_MONO_MODE && stereo) {
				mode = EditMode.JAM_STEREO_MODE;
			} else {
				mode = targetEditMode;
			}
		}
		editMode = mode;
	}

	/** Mirrors C++'s {@code IsHoveredXY()} (GuiHelpers.cpp): is the mouse inside the given logical rectangle? */
	public boolean isHovered(int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
