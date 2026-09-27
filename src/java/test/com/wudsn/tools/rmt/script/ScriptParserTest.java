package com.wudsn.tools.rmt.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ScriptParserTest {

	@Test
	void commandsArgumentsAndOptions() throws ScriptException {
		List<ScriptCommand> commands = ScriptParser.parse(List.of( //
				"# a comment", //
				"", //
				"OPEN Delta.rmt", //
				"export sap \"My Song.sap\" Author=\"Raster / C.P.U.\" subsongs=0 # trailing comment", //
				"  echo done  "));
		assertEquals(3, commands.size());

		ScriptCommand open = commands.get(0);
		assertEquals(3, open.line());
		assertEquals("open", open.name());
		assertEquals(List.of("Delta.rmt"), open.arguments());
		assertTrue(open.options().isEmpty());

		ScriptCommand export = commands.get(1);
		assertEquals(4, export.line());
		assertEquals(List.of("sap", "My Song.sap"), export.arguments());
		assertEquals(Map.of("author", "Raster / C.P.U.", "subsongs", "0"), export.options());
		assertEquals("Raster / C.P.U.", export.option("author", "?"));
		assertEquals("dflt", export.option("name", "dflt"));

		assertEquals(List.of("done"), commands.get(2).arguments());
	}

	@Test
	void quotesEscapesAndEqualsSignsInArguments() throws ScriptException {
		ScriptCommand c = ScriptParser.parseLine(1, "echo \"say \\\"hi\\\"\" a=b\\c \"x=y\" path=C:\\dir\\file.rmt");
		assertEquals(List.of("say \"hi\"", "x=y"), c.arguments()); // a quoted token is never an option
		assertEquals("b\\c", c.options().get("a")); // backslashes outside quotes are plain characters
		assertEquals("C:\\dir\\file.rmt", c.options().get("path"));
		assertEquals("", ScriptParser.parseLine(1, "export xex a.xex text=\"\"").options().get("text"));
	}

	@Test
	void errorsCarryTheLineNumber() {
		ScriptException e = assertThrows(ScriptException.class, () -> ScriptParser.parse(List.of("open a.rmt", "export sap \"unterminated")));
		assertEquals(2, e.getLine());
		assertTrue(e.getLocatedMessage().startsWith("line 2: "));
		assertEquals(1, assertThrows(ScriptException.class, () -> ScriptParser.parseLine(1, "name=value")).getLine());
		assertEquals(7, assertThrows(ScriptException.class, () -> ScriptParser.parseLine(7, "export sap a.sap author=x author=y")).getLine());
	}
}
