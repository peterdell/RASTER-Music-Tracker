package com.wudsn.tools.rmt.model;

/**
 * Ported from CAtariIO (src/cpp/AtariIO.h/.cpp) - only {@code LoadDataAsBinaryFile},
 * the one method {@code AtariTrackerDriverTests.cpp} exercises (indirectly,
 * via {@link AtariTrackerDriver#loadRMTRoutines}). {@code LoadWord}/
 * {@code LoadBinaryBlock}/{@code LoadBinaryFile} all operate on a
 * {@code std::istream} rather than an in-memory buffer and have no
 * dedicated test coverage - deferred.
 *
 * <p>C++'s separate {@code MemorySize size} parameter is dropped - Java
 * arrays already carry their own length, unlike C's raw pointers.
 * {@code minadr}/{@code maxadr} output-reference-parameters become fields
 * on a returned {@link Result}.
 */
public final class AtariIO {

	private AtariIO() {
	}

	/** {@code bytesRead} is the method's own {@code int} return value; {@code minAddress}/{@code maxAddress} were C++'s output parameters. */
	public record Result(int bytesRead, int minAddress, int maxAddress) {
	}

	public static Result loadDataAsBinaryFile(byte[] data, byte[] memory) {
		int minAddr = 0xFFFF;
		int maxAddr = 0;
		int akp = 0;
		while (akp < data.length) {
			int bfrom = unsignedByte(data, akp) | (unsignedByte(data, akp + 1) << 8);
			akp += 2;
			if (bfrom == 0xFFFF) {
				continue;
			}
			int bto = unsignedByte(data, akp) | (unsignedByte(data, akp + 1) << 8);
			akp += 2;
			int blen = bto - bfrom + 1;
			if (blen <= 0) {
				break;
			}
			System.arraycopy(data, akp, memory, bfrom, blen);
			akp += blen;
			if (bfrom < minAddr) {
				minAddr = bfrom;
			}
			if (bto > maxAddr) {
				maxAddr = bto;
			}
		}
		return new Result(akp, minAddr, maxAddr);
	}

	private static int unsignedByte(byte[] buf, int index) {
		return buf[index] & 0xFF;
	}
}
