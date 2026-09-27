package com.wudsn.tools.rmt.ui;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.wudsn.tools.base.repository.Action;

/**
 * The command table of the program - every menu item, toolbar button and
 * accelerator with its description - as {@code CCommands} writes it from
 * {@code Rmt.rc} in the C++ program ({@code dump actions <file>} in a script,
 * {@code doc/rmt_action_infos.md}; plans/DOC_GENERATION_PLAN.md section
 * 2.2). Built from the same data the window is built from: {@link Actions}
 * (labels, keys, descriptions), {@link RmtCommandId}, {@link RmtMainMenu}
 * (the menu tree, with the keys its items display) and {@link RmtToolBars}
 * (the buttons). Same columns, same row order (the menu items as they
 * appear, then the toolbar buttons without a menu item, then the keys
 * without either), same key names as the C++ table, so the cross-program
 * comparison compares the two files byte for byte.
 */
public final class ActionInfos {

	public static final String HEADER = "| Access Path | Entry | Accelerator Key | Action |\n|---|---|---|---|\n";

	/** The table text and the number of ERROR markers in it (none can arise here - the data has no second source to disagree with). */
	public record Result(String text, int errorCount) {
	}

	private static final class Row {
		String menuPath;
		String entry = "";
		String key = "";
		String description = "";
		final List<String> toolBars = new ArrayList<>();
	}

	private ActionInfos() {
	}

	/** Writes the table to {@code file} (UTF-8, LF); returns the number of ERROR markers. */
	public static int write(Path file) throws IOException {
		Result result = build();
		Files.writeString(file, result.text(), StandardCharsets.UTF_8);
		return result.errorCount();
	}

	public static Result build() {
		RmtMainMenu menu = new RmtMainMenu(id -> {
		});
		new RmtToolBars(menu, id -> {
		}, index -> {
		});
		List<Row> rows = new ArrayList<>();
		Map<RmtCommandId, Row> firstRow = new EnumMap<>(RmtCommandId.class); // a command's first menu item: the row its buttons join

		// The menu items, depth first, as they appear (a command in several submenus - the Pokey registers - has a row per item, as its C++ IDs do)
		for (int i = 0; i < menu.menuBar.getMenuCount(); i++) {
			JMenu top = menu.menuBar.getMenu(i);
			walk(menu, top, plainText(top.getText()), rows, firstRow);
		}

		// The toolbar buttons; a button without a menu item gets a row of its own
		toolBar(RmtToolBars.MAIN_BUTTONS, "Main", rows, firstRow);
		Row combo = new Row(); // the "skip lines after note" combo box: a toolbar item without a command, last on the main toolbar
		combo.entry = plainText(Actions.Toolbar_SkipLinesAfterNoteInsert.getLabel());
		combo.description = toolTip(Actions.Toolbar_SkipLinesAfterNoteInsert);
		combo.toolBars.add("Main");
		rows.add(combo);
		toolBar(RmtToolBars.BLOCK_BUTTONS, "Block", rows, firstRow);

		// The keys without a menu item or button
		for (RmtCommandId id : RmtCommandId.values()) {
			if (!firstRow.containsKey(id) && id.action.getAccelerator() != null && !id.acceleratorIsHint) {
				rows.add(commandRow(id, id.action.getAccelerator()));
			}
		}

		StringBuilder sb = new StringBuilder(HEADER);
		for (Row row : rows) {
			append(sb, row);
		}
		return new Result(sb.toString(), 0);
	}

	private static void walk(RmtMainMenu menu, JMenu parent, String path, List<Row> rows, Map<RmtCommandId, Row> firstRow) {
		for (Component component : parent.getMenuComponents()) {
			if (component instanceof JMenu sub) {
				walk(menu, sub, path + " / " + plainText(sub.getText()), rows, firstRow);
			} else if (component instanceof JMenuItem item) {
				RmtCommandId id = menu.commandOf(item);
				if (id == null) {
					continue;
				}
				Row row = commandRow(id, item.getAccelerator()); // the key the item displays (per register in the Pokey submenus)
				row.menuPath = path;
				rows.add(row);
				firstRow.putIfAbsent(id, row);
			}
		}
	}

	private static void toolBar(RmtCommandId[] ids, String name, List<Row> rows, Map<RmtCommandId, Row> firstRow) {
		for (RmtCommandId id : ids) {
			if (id == null) {
				continue;
			}
			Row row = firstRow.get(id);
			if (row == null) {
				row = commandRow(id, id.action.getAccelerator());
				rows.add(row);
				firstRow.put(id, row);
			}
			row.toolBars.add(name);
		}
	}

	private static Row commandRow(RmtCommandId id, KeyStroke key) {
		Row row = new Row();
		row.entry = plainText(id.action.getLabel());
		row.key = key != null ? formatKey(key) : "";
		row.description = toolTip(id.action);
		return row;
	}

	private static void append(StringBuilder sb, Row row) {
		StringBuilder accessPath = new StringBuilder();
		if (row.menuPath != null) {
			accessPath.append("Menu ").append(row.menuPath);
		}
		if (!row.toolBars.isEmpty()) {
			if (accessPath.length() > 0) {
				accessPath.append("<br>");
			}
			accessPath.append("Tool Bar ").append(String.join(", ", row.toolBars));
		}
		String key = row.key.isEmpty() ? "" : "`" + row.key + "`";
		sb.append("| ").append(escape(accessPath.toString())).append(" | ").append(escape(row.entry)).append(" | ").append(key).append(" | ").append(escape(row.description)).append(" |\n");
	}

	private static String toolTip(Action action) {
		String toolTip = action.getToolTip();
		return toolTip == null ? "" : toolTip;
	}

	/** The label without its mnemonic marker ({@code &File} is {@code File}, {@code &&} an ampersand). */
	static String plainText(String label) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (c == '&') {
				if (i + 1 < label.length() && label.charAt(i + 1) == '&') {
					sb.append('&');
					i++;
				}
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static String escape(String cell) {
		return cell.replace("|", "\\|");
	}

	/**
	 * The key as the C++ table names it ({@code CAcceleratorTable::GetText}):
	 * MFC's modifier order Ctrl+Shift+Alt, then the English key name of the US
	 * layout. The platform's command modifier counts as Ctrl - the table
	 * describes the program, not the machine it was written on.
	 */
	static String formatKey(KeyStroke keyStroke) {
		StringBuilder sb = new StringBuilder();
		int modifiers = keyStroke.getModifiers();
		if ((modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0) {
			sb.append("Ctrl+");
		}
		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
			sb.append("Shift+");
		}
		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
			sb.append("Alt+");
		}
		int key = keyStroke.getKeyCode();
		if (key >= KeyEvent.VK_F1 && key <= KeyEvent.VK_F12) {
			return sb.append('F').append(key - KeyEvent.VK_F1 + 1).toString();
		}
		if (key >= KeyEvent.VK_F13 && key <= KeyEvent.VK_F24) {
			return sb.append('F').append(key - KeyEvent.VK_F13 + 13).toString();
		}
		if ((key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9) || (key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z)) {
			return sb.append((char) key).toString();
		}
		if (key >= KeyEvent.VK_NUMPAD0 && key <= KeyEvent.VK_NUMPAD9) {
			return sb.append("Num ").append(key - KeyEvent.VK_NUMPAD0).toString();
		}
		String name = switch (key) {
		case KeyEvent.VK_SPACE -> "Space";
		case KeyEvent.VK_ESCAPE -> "Esc";
		case KeyEvent.VK_ENTER -> "Enter";
		case KeyEvent.VK_TAB -> "Tab";
		case KeyEvent.VK_BACK_SPACE -> "Backspace";
		case KeyEvent.VK_DELETE -> "Del";
		case KeyEvent.VK_INSERT -> "Ins";
		case KeyEvent.VK_HOME -> "Home";
		case KeyEvent.VK_END -> "End";
		case KeyEvent.VK_PAGE_UP -> "PgUp";
		case KeyEvent.VK_PAGE_DOWN -> "PgDn";
		case KeyEvent.VK_UP -> "Up";
		case KeyEvent.VK_DOWN -> "Down";
		case KeyEvent.VK_LEFT -> "Left";
		case KeyEvent.VK_RIGHT -> "Right";
		case KeyEvent.VK_ADD -> "Num +";
		case KeyEvent.VK_SUBTRACT -> "Num -";
		case KeyEvent.VK_MULTIPLY -> "Num *";
		case KeyEvent.VK_DIVIDE -> "Num /";
		case KeyEvent.VK_DECIMAL -> "Num .";
		case KeyEvent.VK_PLUS -> "+"; // the menu labels' "+" and "-" hints (the Pokey Explorer's divisor)
		case KeyEvent.VK_MINUS -> "-";
		case KeyEvent.VK_PAUSE -> "Pause";
		default -> String.format("VK_%02X", key);
		};
		return sb.append(name).toString();
	}
}
