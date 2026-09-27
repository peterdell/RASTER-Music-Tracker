package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The emulated 6502 + POKEY behind live playback: JSR semantics, plain-RAM hardware pages, the real RMT driver, and sample rendering. */
class AtariCpuTest {

	@Test
	void jsrRunsARoutineWithRegistersInAndOut() {
		AtariCpu cpu = new AtariCpu(false, false);
		byte[] mem = cpu.getMemory();
		// $0600: LDA #$05 ; INX ; INY ; STA $D200 ; RTS
		int p = 0x600;
		mem[p++] = (byte) 0xA9;
		mem[p++] = 0x05;
		mem[p++] = (byte) 0xE8;
		mem[p++] = (byte) 0xC8;
		mem[p++] = (byte) 0x8D;
		mem[p++] = 0x00;
		mem[p++] = (byte) 0xD2;
		mem[p++] = 0x60;
		AtariCpu.Registers r = cpu.jsr(0x600, 0, 0x10, 0x20);
		assertEquals(5, r.a());
		assertEquals(0x11, r.x());
		assertEquals(0x21, r.y());
		assertTrue(r.returned());
		assertEquals(5, mem[0xD200] & 0xFF); // the POKEY page is plain RAM in RMT mode (the register shadow)

		// a routine that never returns hits the frame's cycle limit
		mem[0x700] = 0x4C; // JMP $0700
		mem[0x701] = 0x00;
		mem[0x702] = 0x07;
		assertFalse(cpu.jsr(0x700, 0, 0, 0).returned());
		// and the CPU is usable again afterwards
		assertEquals(5, cpu.jsr(0x600, 0, 0, 0).a());
	}

	@Test
	void theRealDriverInitializesAndPlaysANote() {
		AtariCpu cpu = new AtariCpu(false, false);
		Atari atari = new Atari(cpu);
		TuningSettings tuning = new TuningSettings();
		tuning.initialize(false);
		TuningRatios ratios = new TuningRatios();
		ratios.initialize();
		atari.init(false, tuning, ratios);
		AtariTrackerDriver driver = new AtariTrackerDriver(atari);
		assertTrue(driver.loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
		Instruments instruments = new Instruments();
		instruments.initInstruments();
		instruments.getInstrument(0).parameters[Instrument.PAR_ENV_LENGTH] = 0;
		instruments.getInstrument(0).envelope[0][EnvelopeParameter.VOLUMEL] = 15;
		instruments.getInstrument(0).envelope[0][EnvelopeParameter.VOLUMER] = 15;
		instruments.attachAtari(atari, () -> false);
		// instrument 0's Atari bytes were written at $4000: byte 0 = table end (12 + empty table), byte 13 = first envelope volume byte (mono: L<<4 | L)
		assertEquals(12, atari.getByteAt(0x4000) & 0xFF);
		assertEquals(0xFF, atari.getByteAt(0x4000 + 13) & 0xFF);

		AtariCpu.Registers r = cpu.jsr(AtariTrackerDriver.RMT_INIT, 0, 0, 0x3f);
		assertTrue(r.returned(), "RMT_INIT returns");
		driver.init();
		driver.setPokey();
		assertEquals(0, atari.getByteAt(0xD201) & 0x0F, "silence after init");

		driver.setTrackNoteInstrumentVolume(0, 24, 0, 15); // note C-3, instrument 0, full volume on channel 0
		driver.play();
		int audc1 = atari.getByteAt(0xD201);
		assertEquals(15, audc1 & 0x0F, "channel 0 sounds at volume 15 after one tick: AUDC1 = " + Integer.toHexString(audc1));
		assertNotEquals(0, atari.getByteAt(0xD200), "AUDF1 set from the frequency table");

		byte[] buffer = new byte[4096];
		cpu.pokeRegister(0, atari.getByteAt(0xD200));
		cpu.pokeRegister(1, audc1);
		int blocks = cpu.render(Atari.getFrameCycleCount(false), buffer, 0);
		assertTrue(blocks >= 880 && blocks <= 884, "one PAL frame is ~882 samples, was " + blocks);
		boolean nonZero = false;
		for (int i = 0; i < blocks * 2; i++) {
			nonZero |= buffer[i] != 0;
		}
		assertTrue(nonZero, "the POKEY produced sound");

		driver.instrumentTurnOff(0);
		assertEquals(0, atari.getByteAt(0xD201));
	}

	@Test
	void aMemoryOnlyAtariKeepsTheOldNoOpContract() {
		AtariTrackerDriver driver = new AtariTrackerDriver(new Atari());
		assertEquals(0, driver.init());
		driver.play();
		driver.setTrackNoteInstrumentVolume(1, 10, 3, 8);
		assertEquals(3, driver.getRmtInstrument(1));
	}
}
