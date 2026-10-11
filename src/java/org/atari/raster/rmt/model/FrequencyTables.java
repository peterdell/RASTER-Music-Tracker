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

		// Only the page MSB is of interest here; the AUDC the player writes to
		// POKEY is the caller's own business.
		int pageMsb = atariMemory[RMT_TABLES_MSB + (distortion >> 1) * 2] & 0xFF;

		// A sanity check on the loaded driver, not a driver-version test: the
		// five distortion pages are the only ones the dispatch table can name,
		// so anything else means these bytes are not that table (no Patch 16
		// driver loaded - the v1-family drivers keep their tables inside the
		// player and never read this region at all).
		if (pageMsb < (Atari.RMT_FRQTAB_POLY5_64KHZ >> 8) || pageMsb > (Atari.RMT_FRQTAB_GRITTY_64KHZ >> 8)) {
			return 0;
		}

		int page = pageMsb << 8;
		switch (mode) {
		case CLOCK_179MHZ:
			return page + 0x40;
		case JOIN_16BIT:
			return page + 0x80;
		default:
			return page;
		}
	}
}
