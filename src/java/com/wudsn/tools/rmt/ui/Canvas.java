package com.wudsn.tools.rmt.ui;

import java.awt.Color;

/**
 * Ported from CCanvas (src/cpp/Canvas.h/.cpp) - a thin "cursor" over
 * {@link CanvasXY} for the mini-font (8x8 cell) text blocks: an origin plus
 * a current column/row, with chainable positioning and printing.
 *
 * <p>C++'s {@code TextMiniAt(txt, int row, int column, color)} has its two
 * position parameters <em>named</em> the wrong way round (the first is
 * multiplied by the character width, i.e. it is the column) - and
 * {@code PrintMini} passes {@code (column, row)} into it, so the net effect
 * is correct. {@link #textMiniAt} keeps the correct behavior under correct
 * names.
 *
 * <p>C++'s {@code PrintfMini(size, format, ...)} truncates the formatted
 * text to {@code size} characters via {@code vsnprintf(buffer, size + 1,
 * ...)}; {@link #printfMini} does the same, including the consequence that
 * {@link #printNibble} formats {@code value << 4} and keeps only the first
 * character (C++'s exact expression), rather than masking to a nibble.
 */
public final class Canvas {

	private static final int CHAR_WIDTH = 8;
	private static final int CHAR_HEIGHT = 8;

	private final CanvasXY canvasXY;
	private final int originX;
	private final int originY;

	private TextMiniColor colorMini = TextMiniColor.WHITE;
	private int column;
	private int row;

	public Canvas(CanvasXY canvasXY, int originX, int originY) {
		this.canvasXY = canvasXY;
		this.originX = originX;
		this.originY = originY;
	}

	public int getOriginX() {
		return originX;
	}

	public int getOriginY() {
		return originY;
	}

	public Canvas colorMini(TextMiniColor colorMini) {
		this.colorMini = colorMini;
		return this;
	}

	public Canvas at(int column, int row) {
		this.column = column;
		this.row = row;
		return this;
	}

	public Canvas atColumn(int column) {
		this.column = column;
		return this;
	}

	public Canvas nextRow() {
		this.row++;
		return this;
	}

	public void textMiniAt(String txt, int column, int row, TextMiniColor color) {
		canvasXY.textMiniXY(txt, originX + column * CHAR_WIDTH, originY + row * CHAR_HEIGHT, color);
	}

	public Canvas printMini(String txt) {
		textMiniAt(txt, column, row, colorMini);
		return this;
	}

	/** Formats (in the C locale, like {@code vsnprintf} - a decimal point, never a comma) then truncates to {@code size} characters - see class javadoc. */
	public Canvas printfMini(int size, String format, Object... args) {
		String text = String.format(java.util.Locale.ROOT, format, args);
		if (text.length() > size) {
			text = text.substring(0, size);
		}
		return printMini(text);
	}

	/** Prints a value between $0 and $F - via C++'s exact {@code "%01hX", value << 4} expression, see class javadoc. */
	public Canvas printNibble(int value) {
		return printfMini(1, "%01X", (value & 0xFF) << 4);
	}

	/** Prints a value between $00 and $FF. */
	public Canvas printByte(int value) {
		return printfMini(2, "%02X", value & 0xFF);
	}

	public void fillSolidRect(int x, int y, int width, int height, Color color) {
		canvasXY.fillSolidRect(originX + x, originY + y, width, height, color);
	}
}
