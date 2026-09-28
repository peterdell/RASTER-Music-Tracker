package org.atari.raster.rmt.model;

import java.io.ByteArrayOutputStream;

/**
 * Ported from CCompressLzss/CLzss (src/cpp/lzss_sap.h/.cpp) - an optimal
 * LZSS compressor for SAP-R register-dump music files (by DMSC, C++-ported
 * for RMT by VinsCool). Covers exactly what {@code LzssTests.cpp}
 * exercises: the three {@code Optimise_*} passes and the compressor's one
 * real call shape (C++'s {@code LZSS_SAP(src, srclen, dst, optimisation)}
 * four-argument overload, always run with {@code opt='6'}/
 * {@code format_version=0}).
 *
 * <p><b>Public API redesigned around Java arrays</b>: C++'s {@code LZSS_SAP}
 * writes into a caller-supplied, generously oversized {@code dst} buffer
 * and returns the compressed byte count separately - the existing C++ test
 * itself immediately resizes its output vector to that count. Java's
 * {@link #compress} does that trimming internally and simply returns the
 * exact compressed bytes.
 *
 * <p><b>Dead code eliminated, not behavior changed</b>: C++'s
 * {@code LZSS_SAP} chooses {@code bits_moff}/{@code bits_mlen}/
 * {@code min_mlen}/{@code fmt_literal_first}/{@code fmt_pos_start_zero} via
 * two switch statements over local variables ({@code opt}, hardcoded to
 * {@code '6'}; {@code format_version}, hardcoded to {@code 0}) that are
 * never set any other way - there is no public API to reach any other
 * branch. This port hardcodes the one reachable outcome
 * ({@code bitsMoff=8, bitsMlen=8, minMlen=1, fmtLiteralFirst=true,
 * fmtPosStartZero=false}) instead of reproducing switch statements whose
 * other branches can never execute. Likewise, the two-argument
 * {@code registers} parameter on C++'s five-argument {@code LZSS_SAP}
 * overload is declared but never read in {@code Optimize()}'s body (always
 * uses the {@code REGISTERS} constant) and no test calls that overload -
 * dropped entirely.
 *
 * <p><b>Diagnostics omitted, no effect on tested output</b>: every
 * {@code fprintf(log, ...)} call in C++'s {@code Compress()} - channel-skip
 * notices, empty/constant-stream warnings, and the {@code show_stats}/
 * {@code stat_len}/{@code stat_off} verbose dump - writes only to a log
 * stream (hardcoded to {@code stderr} from {@code LZSS_SAP}), never to the
 * returned buffer or size. None of it is reproduced here, and the
 * {@code stat_len}/{@code stat_off} histograms that exist solely to feed
 * that dump are dropped along with it.
 *
 * <p><b>A fragile contract preserved as-is, manifesting differently</b>:
 * neither C++'s {@code Optimize()} nor this port requires {@code src}'s
 * length to be an exact multiple of the 9-byte SAP-R register frame size -
 * a malformed length is already characterized as unhandled. C++ would
 * silently read past the end of {@code src} for a partial trailing frame
 * (undefined behavior, not a crash); Java throws
 * {@code ArrayIndexOutOfBoundsException} instead. Neither
 * {@code LzssTests.cpp} nor any real call site exercises this.
 */
public final class CompressLzss {

	private static final int REGISTERS = 9;

	public void optimiseAudc(byte[] buf) {
		for (int i = 0; i < 4; i++) {
			int audc = i * 2 + 1;
			int value = unsignedByte(buf, audc);
			int vol = value & 0x0F;
			int dist = value & 0xF0;

			// RMT will handle both the Proper Volume Only output, and the
			// SAP-R dump patch for the Two-Tone Filter
			if (dist < 0xF0) {
				if (vol == 0) {
					buf[audc] = 0; // No volume, ignore distortion bits
				} else if ((dist & 0x20) != 0) {
					buf[audc] &= 0xBF; // No noise, ignore noise type bit
				}
			}
		}
	}

	public void optimiseAudctl(byte[] buf) {
		// CH1 is mute, disable High Pass Filter in CH1+3
		if ((unsignedByte(buf, 1) & 0x0F) == 0) {
			buf[8] &= 0xFB;
		}

		// CH1 is mute and Join1+2 is not set, disable 1.79mhz clock in CH1
		if ((unsignedByte(buf, 1) & 0x0F) == 0 && (unsignedByte(buf, 8) & 0x10) == 0) {
			buf[8] &= 0xBF;
		}

		// Both CH1 and CH2 are mute, disable 16-bit mode
		if ((unsignedByte(buf, 1) & 0x0F) == 0 && (unsignedByte(buf, 3) & 0x0F) == 0) {
			buf[8] &= 0xAF;
		}

		// CH2 is mute, disable High Pass Filter in CH2+4
		if ((unsignedByte(buf, 3) & 0x0F) == 0) {
			buf[8] &= 0xFD;
		}

		// CH3 is mute and Join3+4 is not set, disable 1.79mhz clock in CH3,
		// if Filter in CH1+3 is also disabled
		if ((unsignedByte(buf, 5) & 0x0F) == 0 && (unsignedByte(buf, 8) & 0x08) == 0 && (unsignedByte(buf, 8) & 0x04) == 0) {
			buf[8] &= 0xDF;
		}

		// Both CH3 and CH4 are mute, disable 16-bit mode
		if ((unsignedByte(buf, 5) & 0x0F) == 0 && (unsignedByte(buf, 7) & 0x0F) == 0) {
			buf[8] &= 0xF7;
		}
	}

	public void optimiseAudf(byte[] buf) {
		for (int i = 0; i < 4; i++) {
			int audf = i * 2;
			int audc = audf + 1;
			int vol = unsignedByte(buf, audc) & 0x0F;
			int audctl = unsignedByte(buf, 8);
			boolean twotone = (unsignedByte(buf, 1) & 0x10) != 0 && unsignedByte(buf, 1) < 0xF0;

			// Check if there is no volume, and if the AUDCTL actually needs
			// the AUDF. This is literally a case by case situation, this is
			// painful
			if (vol == 0) {
				switch (i) {
				case 0:
					if ((audctl & 0x04) == 0 && (audctl & 0x10) == 0 && (audctl & 0x40) == 0) {
						buf[audf] = 0;
					}
					break;
				case 1:
					if ((audctl & 0x02) == 0 && (audctl & 0x10) == 0 && !twotone) {
						buf[audf] = 0;
					}
					break;
				case 2:
					if ((audctl & 0x04) == 0 && (audctl & 0x08) == 0 && (audctl & 0x20) == 0) {
						buf[audf] = 0;
					}
					break;
				case 3:
					if ((audctl & 0x02) == 0 && (audctl & 0x08) == 0) {
						buf[audf] = 0;
					}
					break;
				}
			}
		}
	}

	public byte[] compress(byte[] src, SapROptimization optimisation) {
		byte[][] data = optimize(src, optimisation);
		int sz = data[0].length;
		return doCompress(data, sz);
	}

	private byte[][] optimize(byte[] src, SapROptimization optimisation) {
		int frameCount = src.length / REGISTERS;
		byte[][] data = new byte[REGISTERS][frameCount];

		byte[] frame = new byte[REGISTERS];
		int destIndex = 0;
		for (int srcIndex = 0; srcIndex < src.length; srcIndex += REGISTERS, destIndex++) {
			// SAP-R frames are processed in groups of REGISTERS bytes, in
			// this order: AUDF0, AUDC0, AUDF1, AUDC1, AUDF2, AUDC2, AUDF3,
			// AUDC3, AUDCTL
			System.arraycopy(src, srcIndex, frame, 0, REGISTERS);

			switch (optimisation) {
			case AUDC:
				optimiseAudc(frame);
				break;
			case AUDCTL:
				optimiseAudctl(frame);
				break;
			case AUDF:
				optimiseAudf(frame);
				break;
			case AUDC_AUDF:
				optimiseAudc(frame);
				optimiseAudf(frame);
				break;
			case AUDCTL_AUDC:
				optimiseAudc(frame);
				optimiseAudctl(frame);
				break;
			case AUDCTL_AUDF:
				optimiseAudctl(frame);
				optimiseAudf(frame);
				break;
			case ALL:
				optimiseAudc(frame);
				optimiseAudctl(frame);
				optimiseAudf(frame);
				break;
			case NONE:
				break;
			}

			for (int i = 0; i < REGISTERS; i++) {
				data[i][destIndex] = frame[i];
			}
		}

		return data;
	}

	private byte[] doCompress(byte[][] data, int sz) {
		Lzss lzss = new Lzss();
		// opt='6': the only branch LZSS_SAP's hardcoded local ever reaches.
		lzss.bitsMoff = 8;
		lzss.bitsMlen = 8;
		lzss.minMlen = 1;
		// format_version=0 (default branch): the only branch ever reached.
		lzss.fmtLiteralFirst = true;
		lzss.fmtPosStartZero = false;

		Bf b = new Bf();

		// Detect channels whose value never changes across the whole
		// stream (skippable, except channel 0 which is always encoded).
		boolean[] chnSkip = new boolean[REGISTERS];
		for (int i = REGISTERS - 1; i >= 0; i--) {
			byte first = data[i][0];
			boolean varies = false;
			for (int j = 0; j < sz; j++) {
				if (data[i][j] != first) {
					varies = true;
					break;
				}
			}
			chnSkip[i] = i != 0 && !varies;
			if (i != 0) {
				b.buf.addBit(chnSkip[i] ? 1 : 0);
			}
		}
		b.bflush();

		// Store initial values for every channel (fmtLiteralFirst is always
		// true here, so this is unconditional).
		for (int i = REGISTERS - 1; i >= 0; i--) {
			b.buf.addByte(data[i][0]);
		}
		b.bflush();

		// Init LZ states
		Lzop[] lz = new Lzop[REGISTERS];
		for (int i = 0; i < REGISTERS; i++) {
			if (!chnSkip[i]) {
				lz[i] = new Lzop(data[i], sz);
				lzss.lzopBackfill(lz[i], false);
			}
		}

		// If every stream ends in a match, fix stream 0 (always encoded) to
		// end in a literal instead.
		boolean endNotOk = true;
		for (int i = 0; i < REGISTERS; i++) {
			if (!chnSkip[i]) {
				endNotOk &= lzss.lzopLastIsMatch(lz[i]);
			}
		}
		if (endNotOk) {
			lzss.lzopBackfill(lz[0], true);
		}

		int[] lpos = new int[REGISTERS];
		java.util.Arrays.fill(lpos, -1);

		for (int pos = lzss.fmtLiteralFirst ? 1 : 0; pos < sz; pos++) {
			for (int i = REGISTERS - 1; i >= 0; i--) {
				if (!chnSkip[i]) {
					lpos[i] = lzss.lzopEncode(b, lz[i], pos, lpos[i]);
				}
			}
		}
		b.bflush();

		return b.out.toByteArray();
	}

	private static int unsignedByte(byte[] buf, int index) {
		return buf[index] & 0xFF;
	}

	/** A single decoded match: a run of {@code length} bytes copied from {@code length} bytes back. */
	private record Match(int length, int position) {
	}

	/** One LZ stream (one SAP-R register's byte sequence across all frames) and its optimal-parse tables. */
	private static final class Lzop {
		final byte[] data;
		int size;
		final int[] bits;
		final int[] mlen;
		final int[] mpos;

		Lzop(byte[] data, int size) {
			this.data = data;
			this.size = size;
			this.bits = new int[size];
			this.mlen = new int[size];
			this.mpos = new int[size];
		}
	}

	/**
	 * A growable bit/half-byte/byte packer. Replaces C++'s fixed
	 * {@code uint8_t buf[128*1024]} scratch array with one that grows as
	 * needed - {@link #addBit} needs to mutate the most recently appended
	 * byte in place (to OR further bits into it), so a plain
	 * append-only structure like {@link ByteArrayOutputStream} won't do.
	 */
	private static final class BitBuffer {
		private byte[] data = new byte[64];
		private int len;
		private int bnum;
		private int bpos = -1;
		private int hpos = -1;

		private void ensureCapacity(int min) {
			if (data.length < min) {
				data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, min));
			}
		}

		void reset() {
			len = 0;
			bnum = 0;
			bpos = -1;
			hpos = -1;
		}

		void addBit(int bit) {
			if (bpos < 0) {
				// Adds a new byte holding bits
				bpos = len;
				bnum = 0;
				ensureCapacity(len + 1);
				data[len] = 0;
				len++;
			}
			if (bit != 0) {
				data[bpos] |= 1 << bnum;
			}
			bnum++;
			if (bnum == 8) {
				bpos = -1;
				bnum = 0;
			}
		}

		void addByte(int value) {
			ensureCapacity(len + 1);
			data[len] = (byte) value;
			len++;
		}

		void addHbyte(int hbyte) {
			if (hpos < 0) {
				// Adds a new byte holding half-bytes
				hpos = len;
				ensureCapacity(len + 1);
				data[hpos] = (byte) (hbyte & 0x0F);
				len++;
			} else {
				// Fixes last h-byte
				data[hpos] |= (byte) (hbyte << 4);
				hpos = -1;
			}
		}
	}

	/** The compressor's overall output: a scratch {@link BitBuffer}, flushed into a growable byte stream. */
	private static final class Bf {
		final BitBuffer buf = new BitBuffer();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		void bflush() {
			out.write(buf.data, 0, buf.len);
			buf.reset();
		}
	}

	/** The LZSS bit-packing engine, ported from CLzss - operates on one compress() call's Bf/Lzop state. */
	private static final class Lzss {
		int bitsMoff;
		int bitsMlen;
		int minMlen;
		boolean fmtLiteralFirst;
		boolean fmtPosStartZero;

		int bitsLiteral() {
			return 1 + 8;
		}

		int bitsMatch() {
			return 1 + bitsMoff + bitsMlen;
		}

		int maxMlen() {
			return minMlen + (1 << bitsMlen) - 1;
		}

		int maxOff() {
			return 1 << bitsMoff;
		}

		// Calculate optimal encoding from the end of stream. If lastLiteral,
		// force the last byte to be encoded as a literal.
		void lzopBackfill(Lzop lz, boolean lastLiteral) {
			if (lz.size == 0) {
				return;
			}

			if (lastLiteral) {
				lz.mlen[lz.size - 1] = 0;
				lz.size--;
				if (lz.size == 0) {
					return;
				}
			}

			lz.bits[lz.size - 1] = bitsLiteral();

			for (int pos = lz.size - 2; pos >= 0; pos--) {
				Match m = match(lz.data, pos, lz.size);

				int best = lz.bits[pos + 1] + bitsLiteral();
				lz.bits[pos] = best;
				lz.mpos[pos] = m.position();
				for (int l = m.length(); l >= minMlen; l--) {
					int bits;
					if (pos + l < lz.size) {
						bits = lz.bits[pos + l] + bitsMatch();
					} else {
						bits = 0;
					}
					if (bits < best) {
						best = bits;
						lz.bits[pos] = best;
						lz.mlen[pos] = l;
						lz.mpos[pos] = m.position();
					}
				}
			}

			if (lastLiteral) {
				lz.size++;
			}
		}

		// Returns true if the coded stream would end in a match.
		boolean lzopLastIsMatch(Lzop lz) {
			boolean last = false;
			for (int pos = 0; pos < lz.size;) {
				int mlen = lz.mlen[pos];
				if (mlen < minMlen) {
					last = false;
					pos++;
				} else {
					pos += mlen;
					last = true;
				}
			}
			return last;
		}

		int lzopEncode(Bf b, Lzop lz, int pos, int lpos) {
			if (pos <= lpos) {
				return lpos;
			}

			int mlen = lz.mlen[pos];
			int mpos = lz.mpos[pos];

			if (mlen < minMlen) {
				// No match, just encode the byte
				b.buf.addBit(1);
				b.buf.addByte(lz.data[pos]);
				return pos;
			} else {
				int codePos = (pos - mpos - (fmtPosStartZero ? 1 : 2)) & (maxOff() - 1);
				int codeLen = mlen - minMlen;

				b.buf.addBit(0);
				if (bitsMlen + bitsMoff <= 8) {
					b.buf.addByte((codePos << bitsMlen) + codeLen);
				} else if (bitsMlen + bitsMoff <= 12) {
					b.buf.addByte((codePos << (8 - bitsMoff)) + (codeLen & ((1 << (8 - bitsMoff)) - 1)));
					b.buf.addHbyte(codeLen >> (8 - bitsMoff));
				} else {
					int mb = ((codeLen + 1) << bitsMoff) + codePos;
					b.buf.addByte(mb & 0xFF);
					b.buf.addByte(mb >> 8);
				}

				return pos + mlen - 1;
			}
		}

		// Returns the maximal match length (and match position) at pos.
		private Match match(byte[] data, int pos, int size) {
			int mxlen = -Math.max(-maxMlen(), pos - size);
			int mlen = 0;
			int mpos = 0;
			for (int i = Math.max(pos - maxOff(), 0); i < pos; i++) {
				int ml = getMlen(data, pos, i, mxlen);
				if (ml > mlen) {
					mlen = ml;
					mpos = pos - i;
				}
			}
			return new Match(mlen, mpos);
		}

		private static int getMlen(byte[] data, int posA, int posB, int max) {
			for (int i = 0; i < max; i++) {
				if (data[posA + i] != data[posB + i]) {
					return i;
				}
			}
			return max;
		}
	}
}
