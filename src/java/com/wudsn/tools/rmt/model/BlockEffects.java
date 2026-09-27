package com.wudsn.tools.rmt.model;

import java.util.Random;

/**
 * The six block effects of {@code CEffectsDlg::PerformEffect()}
 * (effectsdlg.cpp) as a pure function on a {@link Track}: fade in/out,
 * modify notes/instruments/volumes, echo, expand/shrink lines, volume
 * humanize, volume set/remove. C++ keeps this inside the dialog class
 * (the reason the port plan expected "dialog + its logic together"); the
 * Java port separates the computation ({@link #perform}) from the dialog
 * ({@code BlockEffectDialog} in the UI package) so it can be tested. The
 * parameter texts are parsed as {@code ZpracujChPar()} does: the first
 * non-space letter (upper-cased) and the first number.
 */
public final class BlockEffects {

	private BlockEffects() {
	}

	/** One row of C++'s {@code TEffs effects[]}: the name, the three parameter prompts and their defaults ("" = the parameter is unused). */
	public record Effect(String name, String p1, String e1, String p2, String e2, String p3, String e3) {
	}

	public static final Effect[] EFFECTS = { new Effect("Fade in/out", "Initial volume level, 0-100 (%)", "100", "Final volume level, 0-100 (%)", "100", "Line step", "1"),
			new Effect("Modify notes, instruments and volume values", "Notes tuning (+- semitones)", "0", "Instruments used (+- offset value)", "0", "Volume changes (%)", "100"),
			new Effect("Echo", "Delay (lines)", "3", "Fade out level 0-100 (%), or V1-V15 for linear volume subtraction", "20", "Minimal volume 0-15, or !0-!15 for ending echo on minimal volume", "1"),
			new Effect("Expand/shrink lines", "From step (negative values for bottom-up way)", "1", "To step (negative values for bottom-up way)", "2", "", ""),
			new Effect("Volume humanize", "Random level 0-100 (%)", "30", "Minimal volume 0-15", "1", "Line step", "1"),
			new Effect("Volume set/remove", "Volume range - minimum 0-15", "0", "Volume range - maximum 0-15", "15", "Set volume to 0-15, or 'X' to remove whole note events", "15") };

	/** {@code g_effai} and {@code eff_ed[][]}: the effect and parameter texts the dialog remembers for the session ("" = use the defaults). */
	public static final class Settings {
		public int lastEffect;
		public final String[][] params = new String[EFFECTS.length][3];

		public Settings() {
			for (String[] p : params) {
				java.util.Arrays.fill(p, "");
			}
		}
	}

	/** {@code ZpracujChPar()}'s output: the first letter (0 for none) and the first number (0 for none). */
	public record ChPar(char ch, int par) {
	}

	/** {@code ZpracujChPar(s, ch, par)}: "0", "15", "-5", "100", "E10", "E -5", "X" -> the leading letter and the number. */
	public static ChPar parseChPar(String s) {
		char ch = 0;
		for (int i = 0; i < s.length(); i++) {
			char a = s.charAt(i);
			if (a >= 'a' && a <= 'z') {
				a -= 'a' - 'A';
			}
			if ((a >= '0' && a <= '9') || a == '-') {
				return new ChPar(ch, atoi(s.substring(i)));
			}
			if (ch == 0 && a != ' ') {
				ch = a;
			}
		}
		return new ChPar(ch, 0);
	}

	/** C's {@code atoi} on the text from the first digit/sign on. */
	private static int atoi(String s) {
		int i = 0;
		boolean negative = false;
		if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
			negative = s.charAt(i) == '-';
			i++;
		}
		long value = 0;
		while (i < s.length() && Character.isDigit(s.charAt(i))) {
			value = value * 10 + (s.charAt(i) - '0');
			if (value > Integer.MAX_VALUE) {
				break;
			}
			i++;
		}
		return (int) (negative ? -value : value);
	}

	/**
	 * {@code PerformEffect()}: recomputes {@code target} from
	 * {@code original} (the track as it was when the dialog opened - every
	 * Try starts from it again) for block lines {@code bfro..bto}, limited
	 * to instrument {@code ainstr} unless {@code all}. {@code random} feeds
	 * "Volume humanize" ({@code rand() % 1000}).
	 */
	public static void perform(Track target, Track original, int effect, int bfro, int bto, int ainstr, boolean all, String s1, String s2, String s3, Tracks tracks, Random random) {
		ChPar c1 = parseChPar(s1);
		ChPar c2 = parseChPar(s2);
		ChPar c3 = parseChPar(s3);
		int p1 = c1.par();
		int p2 = c2.par();
		int p3 = c3.par();
		char ch2 = c2.ch();
		char ch3 = c3.ch();

		Track td = new Track();
		td.copyFrom(original);

		float[] fvolume = new float[Track.TRACKLEN]; // volume in real numbers
		for (int i = bfro; i <= bto; i++) {
			fvolume[i] = td.volume[i];
		}
		int[] continstr = new int[Track.TRACKLEN]; // the instrument numbers are continuous
		int lasti = -1;
		for (int i = 0; i <= bto; i++) {
			if (td.instr[i] >= 0) {
				lasti = td.instr[i];
			}
			if (i >= bfro) {
				continstr[i] = lasti;
			}
		}
		Track tempt = new Track(); // auxiliary empty track
		for (int i = 0; i < Track.TRACKLEN; i++) {
			tempt.note[i] = -1;
			tempt.instr[i] = -1;
			tempt.volume[i] = -1;
			tempt.speed[i] = -1;
		}

		switch (effect) {
		case 0 -> { // fade in/out: initial volume level %, final vol.level %, line step
			if (p3 <= 0) {
				break;
			}
			for (int i = bfro; i <= bto; i += p3) { // line step p3
				if (!all && ainstr != continstr[i]) {
					continue;
				}
				if (td.volume[i] < 0) {
					continue; // never without volume
				}
				float proc = (float) p1 / 100;
				if (i > 0) {
					proc += (float) (p2 - p1) / (bto - bfro) * (i - bfro) / 100;
				}
				int h = (int) (proc * td.volume[i] + 0.5); // volume change (rounded)
				td.volume[i] = Math.max(0, Math.min(15, h));
			}
		}
		case 1 -> { // change notes, instruments and volumes: note+-, instr+-, volume%
			int ai = all ? -1 : ainstr;
			tracks.modifyTrack(td, bfro, bto, ai, p1, p2, p3);
		}
		case 2 -> { // echo: delay, fadeout level %, minimal volume 0..15 or !1..!15 echo ending volume
			float dvol = 0;
			if (ch2 == 'V') { // linear calculations
				p2 = Math.max(-15, Math.min(15, p2));
			} else { // percentage calculations
				dvol = 1 - ((float) p2 / 100);
				dvol = Math.max(-15, Math.min(15, dvol));
			}
			for (int i = bfro; i <= bto; i++) {
				if (td.note[i] < 0) {
					continue; // there is no note
				}
				if (!all && td.instr[i] != ainstr) {
					continue; // not for this instrument
				}
				int j = i + p1; // echo for p1
				if (j < bfro || j > bto) {
					continue; // echo is coming out of the block
				}
				if (td.note[j] >= 0) {
					continue; // there is already a note in the final place
				}
				float nv = ch2 == 'V' ? fvolume[i] - p2 : fvolume[i] * dvol;
				int ph = (int) (fvolume[i] + 0.5); // original volume (rounded to the nearest)
				int h = (int) (nv + 0.5); // new volume (rounded to the nearest)
				if (ch3 == '!' && ph <= p3) {
					continue; // ending volume
				}
				if (h < p3) {
					h = p3; // minimal volume p3
				}
				h = Math.max(0, Math.min(15, h));
				fvolume[j] = nv; // volume in real numbers
				td.note[j] = td.note[i]; // copies the note
				td.instr[j] = td.instr[i]; // the same instrument
				td.volume[j] = h; // corresponding volume
			}
		}
		case 3 -> { // expand/shrink lines: from step, to step
			if (p1 == 0 && p2 == 0) {
				break;
			}
			int lenb = bto - bfro;
			for (int i = (p1 >= 0) ? 0 : lenb, j = (p2 >= 0) ? 0 : lenb; i >= 0 && i <= lenb && j >= 0 && j <= lenb; i += p1, j += p2) {
				if (!all && td.instr[bfro + i] != ainstr) {
					continue; // not for this instrument
				}
				tempt.note[j] = td.note[bfro + i];
				tempt.instr[j] = td.instr[bfro + i];
				tempt.volume[j] = td.volume[bfro + i];
				tempt.speed[j] = td.speed[bfro + i];
			}
			for (int i = 0; i <= lenb; i++) {
				td.note[bfro + i] = tempt.note[i];
				td.instr[bfro + i] = tempt.instr[i];
				td.volume[bfro + i] = tempt.volume[i];
				td.speed[bfro + i] = tempt.speed[i];
			}
		}
		case 4 -> { // volume humanize: random level %, minimal volume, line step
			p1 = Math.max(0, Math.min(100, p1));
			p2 = Math.max(0, p2);
			if (p3 <= 0) {
				break;
			}
			for (int i = bfro; i <= bto; i += p3) {
				int vol = td.volume[i];
				if (!all && ainstr != continstr[i]) {
					continue; // not for this instrument
				}
				if (vol < 0) {
					continue; // if there is no volume
				}
				float dol = vol - (float) p1 / 100 * 15;
				if (dol < p2) {
					dol = p2;
				}
				dol -= 0.5;
				float hor = vol + (float) p1 / 100 * 15;
				if (hor > 15) {
					hor = 15;
				}
				hor += 0.5;
				if (hor <= dol) {
					continue;
				}
				int nv = (int) (0.5 + dol + (hor - dol) * ((float) random.nextInt(1000) / 1000));
				td.volume[i] = Math.max(p2, Math.min(15, nv));
			}
		}
		case 5 -> { // Volume set / remove (ch3=='X')
			p1 = Math.max(0, Math.min(15, p1));
			p2 = Math.max(0, Math.min(15, p2));
			p3 = Math.max(0, Math.min(15, p3));
			for (int i = bfro; i <= bto; i++) {
				int vol = td.volume[i];
				if (!all && ainstr != continstr[i]) {
					continue; // not for this instrument
				}
				if (vol < 0) {
					continue; // if there is no volume
				}
				if (p1 <= vol && vol <= p2) {
					if (ch3 == 'X') {
						td.note[i] = -1; // clear
						td.instr[i] = -1;
						td.volume[i] = -1;
					} else {
						td.volume[i] = p3;
					}
				}
			}
		}
		default -> {
			// unknown effect: the original is copied back unchanged
		}
		}

		target.copyFrom(td); // copies to the actual track
	}
}
