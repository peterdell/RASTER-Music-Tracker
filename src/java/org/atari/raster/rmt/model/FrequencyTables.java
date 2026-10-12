package org.atari.raster.rmt.model;

/**
 * Which of the Patch 16 player's AUDF tables a note is read from, and where
 * that table is - the port of {@code CFrequencyTables}
 * (src/cpp/FrequencyTables.h/.cpp).
 *
 * <p>The player chooses in two steps (asm/Patch-16/rmtplayr.a65): its
 * {@code TABLES_MSB} dispatch table maps the distortion to one of the five
 * 256-byte pages, then {@code store_tables_lsb} picks a 64-byte slice by the
 * channel's clock - 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80 - while
 * {@code check_15khz} takes the two 15 kHz tables from PAGE_EXTRA_0 instead.
 *
 * <p>The page half of that is READ FROM THE DRIVER IN MEMORY rather than
 * hardcoded here, because keeping a private copy of the layout is exactly how
 * this went wrong twice: {@link Instruments#getFrequency} still used the RMT
 * 1.28 one-page offsets (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md) and the
 * MIDI input path still uses the RMT 1.34 five-page addresses
 * (plans/39_MIDI_TABLE_OFFSETS_PLAN.md). Read from the driver, the mapping
 * lives once in the 6502 source, where build/check_drivers.sh already pins it
 * to the shipped binary, and both programs follow a page reshuffle by
 * themselves.
 */
public final class FrequencyTables {

	/** {@code RMTPLAYR_TABLES_MSB}: eight (page MSB, AUDC) pairs, indexed by distortion / 2. */
	public static final int RMT_TABLES_MSB = 0x3208;

	public enum Mode {
		CLOCK_64KHZ,
		CLOCK_179MHZ,
		CLOCK_15KHZ,
		JOIN_16BIT,
	}

	private FrequencyTables() {
	}

	/**
	 * Whether the driver in {@code atariMemory} carries the dispatch table
	 * these methods read - true only for the Patch 16 driver, which is the one
	 * with the $B000 table pages. The v1-family drivers keep their tables
	 * inside the player at $31c0-$32ff and never read that region, so
	 * everything below answers 0 or -1 for them and the caller reports "no
	 * frequency" rather than inventing one. That is correct but not yet the
	 * whole truth, since those drivers' own tables could be read instead
	 * (plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md).
	 *
	 * <p>The check covers the whole table, not just the entry about to be
	 * read: every page MSB must name one of the five distortion pages, and
	 * every AUDC must carry distortion bits only, the volume nibble being the
	 * player's to fill in. Of the seven drivers RMT ships, only
	 * rmt_driver_v6.obx passes; in the other six these bytes are their own
	 * frequency-table data, which lies far outside $B0-$B4. It remains a
	 * plausibility check rather than a driver-version test.
	 */
	public static boolean hasDispatchTable(byte[] atariMemory) {
		for (int i = 0; i < 8; i++) {
			int pageMsb = atariMemory[RMT_TABLES_MSB + i * 2] & 0xFF;
			int audc = atariMemory[RMT_TABLES_MSB + 1 + i * 2] & 0xFF;
			if (pageMsb < (Atari.RMT_FRQTAB_POLY5_64KHZ >> 8) || pageMsb > (Atari.RMT_FRQTAB_GRITTY_64KHZ >> 8)) {
				return false;
			}
			if ((audc & 0x0F) != 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The address of the AUDF table the player reads for this distortion and
	 * mode. The table holds 64 bytes, except in {@link Mode#JOIN_16BIT} mode,
	 * where it holds 64 little-endian words.
	 *
	 * @param atariMemory the emulated Atari's memory, with a Patch 16 driver loaded
	 * @param distortion the instrument's RMT distortion, $00 to $0E
	 * @param mode which clock or width the channel plays at
	 * @return the table's address, or 0 when the loaded driver has no such table
	 */
	public static int getTableAddress(byte[] atariMemory, int distortion, Mode mode) {
		if (distortion < 0x00 || distortion > 0x0E) {
			return 0;
		}

		if (mode == Mode.CLOCK_15KHZ) {
			// check_15khz: distortion C and E take the buzzy table, the rest
			// the pure one. These two are not in the dispatch table - the
			// player loads their addresses directly.
			return distortion >= 0x0C ? Atari.RMT_FRQTAB_BUZZY_15KHZ : Atari.RMT_FRQTAB_PURE_15KHZ;
		}

		if (!hasDispatchTable(atariMemory)) {
			return 0;
		}

		// Only the page MSB is of interest here; the AUDC the player writes to
		// POKEY is the caller's own business.
		int page = (atariMemory[RMT_TABLES_MSB + (distortion >> 1) * 2] & 0xFF) << 8;
		switch (mode) {
		case CLOCK_179MHZ:
			return page + 0x40;
		case JOIN_16BIT:
			return page + 0x80;
		default:
			return page;
		}
	}

	/**
	 * Which clock or width a channel plays at, from the AUDCTL the driver is
	 * about to write.
	 *
	 * <p>Only channels 1 and 3 of each chip can run at 1.79 MHz (tracks 0/4
	 * and 2/6), and a 16-bit pair is addressed through its high byte, which is
	 * the channel above (tracks 1/5 and 3/7) - so the same AUDCTL means
	 * different things to different channels. 1.79 MHz and 16-bit both
	 * override the 15 kHz clock, as they do in the player.
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
	 * rather than computed, because it is not simply the distortion shifted
	 * up: distortion $06 plays with AUDC $A0 and $0E with $C0.
	 */
	public static int getDistortionAudc(byte[] atariMemory, int distortion) {
		// $00 is a valid AUDC, so the entry's own bytes cannot tell a missing
		// dispatch table from distortion 0 - the page MSB beside it can, and
		// getTableAddress() already checks it.
		if (getTableAddress(atariMemory, distortion, Mode.CLOCK_64KHZ) == 0) {
			return -1;
		}

		// DISTORTIONS is the second byte of each TABLES_MSB pair.
		return atariMemory[RMT_TABLES_MSB + 1 + (distortion >> 1) * 2] & 0xFF;
	}
}
