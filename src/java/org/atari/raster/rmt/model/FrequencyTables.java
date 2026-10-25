package org.atari.raster.rmt.model;

/**
 * Which of the loaded player's AUDF tables a note is read from, and where that
 * table is - the port of {@code CFrequencyTables}
 * (src/cpp/FrequencyTables.h/.cpp).
 *
 * <p>There are two layouts, because there are two generations of driver, and
 * the version decides which one to read
 * (plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md):
 *
 * <p>{@link TrackerDriverVersion#PATCH16} (asm/Patch-16/rmtplayr.a65) chooses
 * in two steps: its {@code frqtab_hi_tab} dispatch table (named {@code TABLES_MSB} by Patch16 before plans/53 realigned the labels, {@code tabbeganddistor} until 2026-10-25) maps the distortion to
 * one of the five 256-byte pages at $B000, then {@code store_tables_lsb} picks
 * a 64-byte slice by the channel's clock - 64 kHz at +$00, 1.79 MHz at +$40,
 * 16-bit at +$80 - while {@code check_15khz} takes the two 15 kHz tables from
 * PAGE_EXTRA_0 instead.
 *
 * <p>The v1 family - every other driver RMT ships (asm/Legacy/rmt.a65) - has
 * one page of 64-byte tables below the player instead, and its
 * {@code tabbeganddistor} maps the distortion to a byte offset into that page.
 * It has no clock variants at all: a 1.79 MHz or 15 kHz channel reads the very
 * same table, which is why those two clocks play out of tune on those drivers
 * rather than playing nothing.
 *
 * <p>Both layouts are READ FROM THE DRIVER IN MEMORY rather than hardcoded
 * here, because keeping a private copy is exactly how this went wrong twice:
 * {@link Instruments#getFrequency} still used the RMT 1.28 one-page offsets
 * (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md) and the MIDI input path still
 * used the RMT 1.34 five-page addresses
 * (plans/39_MIDI_TABLE_OFFSETS_PLAN.md). Read from the driver, the mapping
 * lives once in the 6502 source, where build/check_drivers.sh already pins it
 * to the shipped binary, and both programs follow a reshuffle by themselves.
 * For the v1 family that is not a convenience but a necessity: only v1's
 * source is in this repository, and the five patched binaries each changed the
 * table's contents - PATCH6 plays distortion $06 with AUDC $A0 where v1 plays
 * it with $C0, and PATCH8 and PATCH_PRINCE_OF_PERSIA do not even keep their
 * tables on $40 boundaries.
 */
public final class FrequencyTables {

	/**
	 * {@code RMTPLAYR_FRQTAB_HI_TAB}: eight (page MSB, AUDC) pairs, indexed by
	 * distortion / 2.
	 *
	 * <p>C++ takes this from src/cpp/tracker_obx.h, the header MADS generates
	 * from asm/Patch-16/rmtplayr.a65; the port has no generated header, so
	 * {@code GeneratedHeaderTest} asserts this constant against that file
	 * instead. Same for the two v1-family addresses below and
	 * {@link Atari}'s frequency-table addresses.
	 */
	public static final int PATCH16_FRQTAB_HI_TAB = 0x3208;

	/** {@code RMTPLAYR_DISTORTION_TAB}: the AUDC half of the pairs, at the odd bytes. */
	public static final int PATCH16_DISTORTION_TAB = PATCH16_FRQTAB_HI_TAB + 1;

	/**
	 * {@code tabbeganddistor}: the v1-family layout's eight (offset, AUDC)
	 * pairs, indexed by distortion / 2, from asm/Legacy/rmt.a65. The offset is
	 * a byte offset into the {@link #LEGACY_FRQTAB} page: the player loads
	 * {@code >frqtab} as the pointer's high byte and the offset as its low
	 * byte, so the table's address is simply LEGACY_FRQTAB + offset.
	 *
	 * <p>Both addresses are fixed by an {@code org} relative to PLAYER
	 * ($3400), so they hold for every driver built from that source, whatever
	 * its feature flags. C++ names them through src/cpp/legacy_obx.h, the
	 * header MADS generates from asm/Legacy/rmt_ata.a65, as
	 * {@code RMT_ATA_TABBEGANDDISTOR} and {@code RMT_ATA_FRQTAB}.
	 */
	public static final int LEGACY_TABBEGANDDISTOR = 0x3182;

	/** {@code frqtab}: the page the {@link #LEGACY_TABBEGANDDISTOR} offsets are into. */
	public static final int LEGACY_FRQTAB = 0x3200;

	public enum Mode {
		CLOCK_64KHZ,
		CLOCK_179MHZ,
		CLOCK_15KHZ,
		JOIN_16BIT,
	}

	private FrequencyTables() {
	}

	/**
	 * Whether this version's player is one of the v1 family, i.e. everything
	 * but the Patch 16 driver. They differ in plenty of other ways, but all of
	 * them read their note tables through {@code tabbeganddistor}.
	 */
	private static boolean isLegacyLayout(TrackerDriverVersion version) {
		return version == TrackerDriverVersion.UNPATCHED || version == TrackerDriverVersion.UNPATCHED_WITH_TUNING
				|| version == TrackerDriverVersion.PATCH3 || version == TrackerDriverVersion.PATCH6
				|| version == TrackerDriverVersion.PATCH8 || version == TrackerDriverVersion.PATCH_PRINCE_OF_PERSIA;
	}

	/**
	 * Whether the driver in {@code atariMemory} really carries the table this
	 * version's layout reads.
	 *
	 * <p>A plausibility check on the bytes, not a test of the version: it is
	 * the version that selects the layout, and this only says whether the bytes
	 * where that layout expects its dispatch table could be one. It is what
	 * separates a loaded driver from an empty Atari, and - measured against all
	 * seven drivers RMT ships - a driver of one family from the other's layout.
	 */
	public static boolean hasDispatchTable(byte[] atariMemory, TrackerDriverVersion version) {
		if (version == TrackerDriverVersion.PATCH16) {
			// Checks the whole table, not just the entry about to be read:
			// every page MSB must name one of the five distortion pages, and
			// every AUDC must carry distortion bits only, the volume nibble
			// being the player's to fill in. Of the seven drivers RMT ships,
			// only rmt_driver_v6.obx passes; in the other six these bytes are
			// their own frequency-table data, far outside $B0-$B4.
			for (int i = 0; i < 8; i++) {
				int pageMsb = atariMemory[PATCH16_FRQTAB_HI_TAB + i * 2] & 0xFF;
				int audc = atariMemory[PATCH16_DISTORTION_TAB + i * 2] & 0xFF;
				if (pageMsb < (Atari.RMT_FRQTAB_POLY5_64KHZ >> 8) || pageMsb > (Atari.RMT_FRQTAB_GRITTY_64KHZ >> 8)) {
					return false;
				}
				if ((audc & 0x0F) != 0) {
					return false;
				}
			}
			return true;
		}

		if (isLegacyLayout(version)) {
			// The offsets cannot be checked the same way: PATCH8 and
			// PATCH_PRINCE_OF_PERSIA use offsets that are not multiples of
			// $40, so any byte is a possible one. What can be checked is the
			// AUDC half, plus that the table is there at all - the Patch 16
			// driver loads nothing into this region, so its bytes stay zero,
			// and so do an empty Atari's. A real table cannot be all zeros:
			// that would play every distortion as POKEY distortion 0 out of
			// one table.
			boolean anySet = false;
			for (int i = 0; i < 8; i++) {
				int offset = atariMemory[LEGACY_TABBEGANDDISTOR + i * 2] & 0xFF;
				int audc = atariMemory[LEGACY_TABBEGANDDISTOR + 1 + i * 2] & 0xFF;
				if ((audc & 0x0F) != 0) {
					return false;
				}
				if (offset != 0 || audc != 0) {
					anySet = true;
				}
			}
			return anySet;
		}

		return false; // TrackerDriverVersion.NONE, and anything added without a layout
	}

	/**
	 * The address of the AUDF table the player reads for this distortion and
	 * mode. The table holds 64 bytes, except in {@link Mode#JOIN_16BIT} mode
	 * on PATCH16, where it holds 64 little-endian words. The v1 family answers
	 * 0 for JOIN_16BIT: its 16-bit bass reads two separate tables
	 * ({@code frqtabbasslo}/{@code frqtabbasshi}) a page apart, which no single
	 * address can name, and which only the two drivers built from the
	 * checked-in source can be shown to still hold - the patched binaries have
	 * no source here to identify them against.
	 *
	 * @param atariMemory the emulated Atari's memory, with a driver loaded
	 * @param version which driver that is, and so which layout to read
	 * @param distortion the instrument's RMT distortion, $00 to $0E
	 * @param mode which clock or width the channel plays at
	 * @return the table's address, or 0 when the loaded driver has no such table
	 */
	public static int getTableAddress(byte[] atariMemory, TrackerDriverVersion version, int distortion, Mode mode) {
		if (distortion < 0x00 || distortion > 0x0E) {
			return 0;
		}

		if (version == TrackerDriverVersion.PATCH16) {
			if (mode == Mode.CLOCK_15KHZ) {
				// check_15khz: distortion C and E take the buzzy table, the
				// rest the pure one. These two are not in the dispatch table -
				// the player loads their addresses directly.
				return distortion >= 0x0C ? Atari.RMT_FRQTAB_BUZZY_15KHZ : Atari.RMT_FRQTAB_PURE_15KHZ;
			}

			if (!hasDispatchTable(atariMemory, version)) {
				return 0;
			}

			// Only the page MSB is of interest here; the AUDC the player writes
			// to POKEY is the caller's own business.
			int page = (atariMemory[PATCH16_FRQTAB_HI_TAB + (distortion >> 1) * 2] & 0xFF) << 8;
			switch (mode) {
			case CLOCK_179MHZ:
				return page + 0x40;
			case JOIN_16BIT:
				return page + 0x80;
			default:
				return page;
			}
		}

		if (isLegacyLayout(version)) {
			if (!hasDispatchTable(atariMemory, version)) {
				return 0;
			}

			if (mode == Mode.JOIN_16BIT) {
				return 0; // two tables a page apart - see this method's docs
			}

			// Every other mode reads the same table, because that player has
			// no clock variants: it builds its pointer from `>frqtab` and the
			// offset in tabbeganddistor and indexes it by the note, whatever
			// clock the channel is running at.
			return LEGACY_FRQTAB + (atariMemory[LEGACY_TABBEGANDDISTOR + (distortion >> 1) * 2] & 0xFF);
		}

		return 0;
	}

	/**
	 * Which clock or width a channel plays at, from the AUDCTL the driver is
	 * about to write.
	 *
	 * <p>Only channels 1 and 3 of each chip can run at 1.79 MHz (tracks 0/4
	 * and 2/6), and a 16-bit pair is addressed through its high byte, which is
	 * the channel above (tracks 1/5 and 3/7) - so the same AUDCTL means
	 * different things to different channels. 1.79 MHz and 16-bit both
	 * override the 15 kHz clock, as they do in the player. What a mode then
	 * means for the tables is the layout's business, not this method's.
	 *
	 * @param audctl the AUDCTL value, as in the driver's {@code v_audctl}
	 * @param track the POKEY channel, 0-3 for the first chip and 4-7 for the second
	 */
	public static Mode getModeForChannel(int audctl, int track) {
		boolean clock15 = (audctl & 0x01) != 0;
		boolean join34 = (audctl & 0x08) != 0;
		boolean join12 = (audctl & 0x10) != 0;
		boolean ch3_179 = (audctl & 0x20) != 0;
		boolean ch1_179 = (audctl & 0x40) != 0;

		// A joined pair is addressed through its high byte, the channel above
		// the 1.79 MHz one.
		if ((join12 && ch1_179 && (track == 1 || track == 5)) || (join34 && ch3_179 && (track == 3 || track == 7))) {
			return Mode.JOIN_16BIT;
		}
		if ((ch1_179 && (track == 0 || track == 4)) || (ch3_179 && (track == 2 || track == 6))) {
			return Mode.CLOCK_179MHZ;
		}
		return clock15 ? Mode.CLOCK_15KHZ : Mode.CLOCK_64KHZ;
	}

	/**
	 * The AUDC distortion bits the player writes to POKEY for this distortion,
	 * or -1 when the loaded driver has no dispatch table. Read from that table
	 * rather than computed, because it is not simply the distortion shifted up,
	 * and not even the same from driver to driver: PATCH16 plays distortion $06
	 * with AUDC $A0 and $0E with $C0, while v1 plays $06 with $C0 and PATCH6
	 * with $A0.
	 */
	public static int getDistortionAudc(byte[] atariMemory, TrackerDriverVersion version, int distortion) {
		// $00 is a valid AUDC, so the entry's own bytes cannot tell a missing
		// dispatch table from distortion 0 - the byte beside it can, and
		// getTableAddress() already checks it. 64 kHz is the mode every layout
		// answers, so it is the one to ask with.
		if (getTableAddress(atariMemory, version, distortion, Mode.CLOCK_64KHZ) == 0) {
			return -1;
		}

		// Both layouts keep the AUDC in the second byte of each pair.
		int table = version == TrackerDriverVersion.PATCH16 ? PATCH16_FRQTAB_HI_TAB : LEGACY_TABBEGANDDISTOR;
		return atariMemory[table + 1 + (distortion >> 1) * 2] & 0xFF;
	}
}
