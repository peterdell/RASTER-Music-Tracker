package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code CEffectsDlg::PerformEffect()}'s six effects on a small block, each hand-derived from the C++ arithmetic. */
class BlockEffectsTest {

	private Tracks tracks;
	private Track original;
	private Track target;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();
		original = new Track();
		original.copyFrom(tracks.getTrack(0));
		original.len = 16;
		// lines 0,2,4,6: notes 10,12,14,16 with instrument 1, volume 12; line 8: note 20 instrument 2 volume 8
		for (int i = 0; i < 4; i++) {
			original.note[i * 2] = 10 + i * 2;
			original.instr[i * 2] = 1;
			original.volume[i * 2] = 12;
		}
		original.note[8] = 20;
		original.instr[8] = 2;
		original.volume[8] = 8;
		target = new Track();
	}

	private void perform(int effect, String s1, String s2, String s3, boolean all, int ainstr) {
		BlockEffects.perform(target, original, effect, 0, 8, ainstr, all, s1, s2, s3, tracks, new Random(7));
	}

	@Test
	void parseChParSplitsLetterAndNumber() {
		assertEquals(new BlockEffects.ChPar((char) 0, 15), BlockEffects.parseChPar("15"));
		assertEquals(new BlockEffects.ChPar((char) 0, -5), BlockEffects.parseChPar("-5"));
		assertEquals(new BlockEffects.ChPar('E', 10), BlockEffects.parseChPar("e10"));
		assertEquals(new BlockEffects.ChPar('E', -5), BlockEffects.parseChPar("E -5"));
		assertEquals(new BlockEffects.ChPar('X', 0), BlockEffects.parseChPar("X"));
		assertEquals(new BlockEffects.ChPar((char) 0, 0), BlockEffects.parseChPar(""));
	}

	@Test
	void fadeOutHalvesTheVolumeLinearly() {
		perform(0, "100", "0", "1", true, 0);
		assertEquals(12, target.volume[0]); // 100% at the block start
		assertEquals(9, target.volume[2]); // 75% of 12
		assertEquals(6, target.volume[4]);
		assertEquals(3, target.volume[6]);
		assertEquals(0, target.volume[8]); // 0% at the end
		assertEquals(10, target.note[0]); // notes untouched
		assertEquals(-1, target.volume[1]); // empty lines stay empty

		perform(0, "100", "0", "1", false, 2); // only instrument 2
		assertEquals(12, target.volume[0]);
		assertEquals(0, target.volume[8]);
	}

	@Test
	void modifyTransposesAndOffsets() {
		perform(1, "2", "1", "50", true, 0);
		assertEquals(12, target.note[0]);
		assertEquals(2, target.instr[0]);
		assertEquals(6, target.volume[0]);
		assertEquals(3, target.instr[8]);
	}

	@Test
	void echoCopiesNotesIntoEmptyLinesWithFadedVolume() {
		perform(2, "1", "50", "1", true, 0); // delay 1, 50% fade, minimum 1
		assertEquals(10, target.note[1]);
		assertEquals(1, target.instr[1]);
		assertEquals(6, target.volume[1]);
		assertEquals(12, target.note[3]);
		assertEquals(20, target.note[8]); // occupied lines are left alone
		assertEquals(-1, target.note[9]); // outside the block

		perform(2, "1", "V4", "1", true, 0); // linear: 12 - 4
		assertEquals(8, target.volume[1]);

		perform(2, "1", "50", "!10", true, 0); // ending volume: echoes only for notes louder than 10, and never quieter than 10
		assertEquals(10, target.volume[1]);
		assertEquals(-1, target.note[9]);
		perform(2, "1", "50", "!12", true, 0); // nothing is louder than 12: no echo at all
		assertEquals(-1, target.note[1]);
	}

	@Test
	void expandDoublesTheLineSpacing() {
		perform(3, "1", "2", "", true, 0); // from step 1 to step 2: lines 0..4 land on 0,2,4,6,8
		assertEquals(10, target.note[0]);
		assertEquals(-1, target.note[2]); // line 1 was empty
		assertEquals(12, target.note[4]);
		assertEquals(14, target.note[8]);
		perform(3, "2", "1", "", true, 0); // shrink: every second line
		assertEquals(10, target.note[0]);
		assertEquals(12, target.note[1]);
		assertEquals(14, target.note[2]);
		assertEquals(16, target.note[3]);
		assertEquals(20, target.note[4]);
		assertEquals(-1, target.note[8]);
	}

	@Test
	void humanizeStaysWithinTheRangeAndIsDeterministicForASeed() {
		perform(4, "30", "1", "1", true, 0);
		Track first = new Track();
		first.copyFrom(target);
		for (int i = 0; i <= 8; i += 2) {
			int v = target.volume[i];
			int o = original.volume[i];
			org.junit.jupiter.api.Assertions.assertTrue(v >= Math.max(1, o - 5) && v <= Math.min(15, o + 5), "line " + i + ": " + v);
		}
		perform(4, "30", "1", "1", true, 0);
		for (int i = 0; i < 16; i++) {
			assertEquals(first.volume[i], target.volume[i]);
		}
		perform(4, "0", "1", "1", true, 0); // no randomness: hor <= dol, nothing changes
		assertEquals(12, target.volume[0]);
	}

	@Test
	void volumeSetAndRemove() {
		perform(5, "8", "8", "3", true, 0); // only volume 8 -> 3
		assertEquals(3, target.volume[8]);
		assertEquals(12, target.volume[0]);
		perform(5, "0", "15", "X", true, 0); // remove all note events
		assertEquals(-1, target.note[0]);
		assertEquals(-1, target.instr[0]);
		assertEquals(-1, target.volume[8]);
		perform(5, "0", "15", "X", false, 2); // only instrument 2
		assertEquals(10, target.note[0]);
		assertEquals(-1, target.note[8]);
	}
}
