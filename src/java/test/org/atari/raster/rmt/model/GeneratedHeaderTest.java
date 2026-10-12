package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The port's player addresses against the headers MADS generates from the
 * 6502 sources - {@code src/cpp/tracker_obx.h} from
 * {@code asm/Patch-16/rmtplayr.a65} and {@code src/cpp/legacy_obx.h} from
 * {@code asm/Legacy/rmt_ata.a65}.
 *
 * <p>C++ needs no such test: it {@code #include}s both headers and uses the
 * {@code #define}s themselves, so its compiler is the check. The port has no
 * generated header - it carries the handful of addresses it needs as its own
 * constants, with comments naming the entry each one mirrors - and until this
 * test those comments were the only thing holding the two programs to the same
 * layout. They can now be verified instead of believed.
 *
 * <p>This reads the checked-in headers, not freshly assembled ones, because
 * {@code build/check_drivers.sh} already proves those are what today's sources
 * generate (plans/42_TRACKER_OBX_HEADER_REFRESH_PLAN.md). Together the two
 * checks reach from the 6502 source to both programs: MADS pins the header to
 * the source, the header pins C++ directly and the port through here.
 */
class GeneratedHeaderTest {

	static final Path TRACKER_OBX_H = Path.of("src", "cpp", "tracker_obx.h");
	static final Path LEGACY_OBX_H = Path.of("src", "cpp", "legacy_obx.h");

	/** One {@code #define NAME 0x1234} per entry; the include guard has no value and is skipped. */
	private static Map<String, Integer> defines(Path header) throws IOException {
		Pattern pattern = Pattern.compile("^#define (\\w+) 0x([0-9A-Fa-f]+)$");
		Map<String, Integer> defines = new HashMap<>();
		for (String line : Files.readAllLines(header, StandardCharsets.ISO_8859_1)) {
			Matcher matcher = pattern.matcher(line.trim());
			if (matcher.matches()) {
				defines.put(matcher.group(1), Integer.parseInt(matcher.group(2), 16));
			}
		}
		return defines;
	}

	private static void assertAddress(Map<String, Integer> defines, String name, int actual) {
		Integer expected = defines.get(name);
		assertNotNull(expected, name + " is not in the generated header at all");
		assertEquals(expected.intValue(), actual,
				() -> String.format("%s is $%04X in the generated header, but $%04X here", name, expected, actual));
	}

	/**
	 * A parse that silently found nothing would make every assertion below
	 * pass, so the two headers' sizes are pinned first. The numbers are the
	 * ones build/check_drivers.sh reports; they may legitimately change with
	 * the 6502 sources, in which case the addresses are what matters.
	 */
	@Test
	void bothGeneratedHeadersParse() throws IOException {
		int tracker = defines(TRACKER_OBX_H).size();
		int legacy = defines(LEGACY_OBX_H).size();
		assertTrue(tracker > 300, "tracker_obx.h has only " + tracker + " parsed defines");
		assertTrue(legacy > 200, "legacy_obx.h has only " + legacy + " parsed defines");
	}

	/** {@link Atari}'s frequency-table addresses against the Patch 16 player's own labels. */
	@Test
	void atariFrequencyTableAddressesMatchTheTrackerDriverHeader() throws IOException {
		Map<String, Integer> d = defines(TRACKER_OBX_H);

		assertAddress(d, "RMTPLAYR_PAGE_DISTORTION_2", Atari.RMT_FRQTABLES);

		assertAddress(d, "RMTPLAYR_FRQTABPOLY5_64KHZ", Atari.RMT_FRQTAB_POLY5_64KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABDIST4_64KHZ", Atari.RMT_FRQTAB_DIST4_64KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABPURE_64KHZ", Atari.RMT_FRQTAB_PURE_64KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABBUZZY_64KHZ", Atari.RMT_FRQTAB_BUZZY_64KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABGRITTY_64KHZ", Atari.RMT_FRQTAB_GRITTY_64KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABPURE_15KHZ", Atari.RMT_FRQTAB_PURE_15KHZ);
		assertAddress(d, "RMTPLAYR_FRQTABBUZZY_15KHZ", Atari.RMT_FRQTAB_BUZZY_15KHZ);
	}

	/**
	 * The two dispatch tables {@link FrequencyTables} reads - one per driver
	 * generation, each from its own player's source.
	 */
	@Test
	void frequencyTableDispatchAddressesMatchBothDriverHeaders() throws IOException {
		assertAddress(defines(TRACKER_OBX_H), "RMTPLAYR_TABLES_MSB", FrequencyTables.RMT_TABLES_MSB);

		Map<String, Integer> legacy = defines(LEGACY_OBX_H);
		assertAddress(legacy, "RMT_ATA_TABBEGANDDISTOR", FrequencyTables.LEGACY_TABBEGANDDISTOR);
		assertAddress(legacy, "RMT_ATA_FRQTAB", FrequencyTables.LEGACY_FRQTAB);
	}

	/**
	 * The v1 family's other frequency tables, which no code addresses but
	 * doc/rmt_tracker_drivers.md documents: the page they live in is the one
	 * the offsets in tabbeganddistor are into, so the documented addresses and
	 * the dispatch table have to agree about where that page starts.
	 */
	@Test
	void theDocumentedLegacyTableAddressesAreTheOnesItsSourceGenerates() throws IOException {
		Map<String, Integer> legacy = defines(LEGACY_OBX_H);

		assertAddress(legacy, "RMT_ATA_FRQTABBASSLO", 0x31C0);
		assertAddress(legacy, "RMT_ATA_FRQTABBASS1", 0x3200);
		assertAddress(legacy, "RMT_ATA_FRQTABBASS2", 0x3240);
		assertAddress(legacy, "RMT_ATA_FRQTABPURE", 0x3280);
		assertAddress(legacy, "RMT_ATA_FRQTABBASSHI", 0x32C0);

		// The player loads >frqtab as the pointer's high byte, so every table a
		// tabbeganddistor offset can reach has to sit in that one page.
		// frqtabbasslo is deliberately not among them: it is the low half of
		// the 16-bit bass pair, which the player addresses a page below
		// frqtabbasshi rather than through the dispatch table.
		for (String name : List.of("RMT_ATA_FRQTABBASS1", "RMT_ATA_FRQTABBASS2", "RMT_ATA_FRQTABPURE",
				"RMT_ATA_FRQTABBASSHI")) {
			assertEquals(FrequencyTables.LEGACY_FRQTAB >> 8, legacy.get(name) >> 8, name + " is on another page");
		}
		assertEquals(0x0100, legacy.get("RMT_ATA_FRQTABBASSHI") - legacy.get("RMT_ATA_FRQTABBASSLO"),
				"the 16-bit bass pair is no longer exactly a page apart");
	}
}
