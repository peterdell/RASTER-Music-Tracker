package org.atari.raster.rmt.model;

/**
 * Ported from CAtariIO (src/cpp/AtariIO.h/.cpp) - {@code LoadDataAsBinaryFile}
 * (exercised indirectly via {@link AtariTrackerDriver#loadRMTRoutines}),
 * {@code LoadBinaryBlock} (exercised indirectly via {@link Song#loadRMT}),
 * and {@link #loadBinaryFile} (exercised indirectly via
 * {@code SapFileExporter#exportSapBLzss}'s real on-disk
 * {@code vu_player_v2.obx} load). {@code LoadWord} has no direct Java
 * equivalent - {@link #loadBinaryBlock} inlines its two-byte little-endian
 * reads directly, matching this class's byte-array idiom.
 *
 * <p>C++'s separate {@code MemorySize size} parameter is dropped - Java
 * arrays already carry their own length, unlike C's raw pointers.
 * {@code minadr}/{@code maxadr}/{@code fromAddr}/{@code toAddr}
 * output-reference-parameters become fields on returned records.
 *
 * <p>{@link #loadBinaryBlock} takes a byte array plus an offset instead of
 * C++'s {@code std::istream&} - this port prefers plain byte arrays over
 * stream abstractions throughout (matching {@code SapFile}/
 * {@code AsmFileBuilder}'s established idiom) - and additionally reports
 * how many bytes of the *input* were consumed (header plus data), which a
 * stream-based caller gets for free from the stream's own advanced
 * position but a byte-array caller needs explicitly to find where a
 * second, subsequent block starts.
 */
public final class AtariIO {

	private AtariIO() {
	}

	/** {@code bytesRead} is the method's own {@code int} return value; {@code minAddress}/{@code maxAddress} were C++'s output parameters. */
	public record Result(int bytesRead, int minAddress, int maxAddress) {
	}

	/**
	 * {@code fromAddr}/{@code toAddr} were C++'s output parameters;
	 * {@code inputBytesConsumed} has no direct C++ equivalent (see this
	 * class's own javadoc for why a byte-array-based port needs it).
	 * {@code length() <= 0} indicates failure - mirrors C++'s own
	 * {@code int} return value (the data length, 0 on any failure).
	 */
	public record BinaryBlockResult(int inputBytesConsumed, int fromAddr, int toAddr) {
		public int length() {
			return toAddr - fromAddr + 1;
		}
	}

	private static final BinaryBlockResult BINARY_BLOCK_FAILURE = new BinaryBlockResult(0, 0, -1);

	/** Reads one "binary block" (an Atari executable's block-load format: {@code fromAddr}/{@code toAddr} words, optionally preceded by a {@code 0xFFFF} marker, then the data in between) from {@code data} at {@code offset}, copying it into {@code memory} at {@code fromAddr}. */
	public static BinaryBlockResult loadBinaryBlock(byte[] data, int offset, byte[] memory) {
		int pos = offset;
		if (pos + 2 > data.length) {
			return BINARY_BLOCK_FAILURE;
		}
		int fromAddr = unsignedByte(data, pos) | (unsignedByte(data, pos + 1) << 8);
		pos += 2;
		if (fromAddr == 0xFFFF) {
			// Skip the binary block header (0xFFFF)
			if (pos + 2 > data.length) {
				return BINARY_BLOCK_FAILURE;
			}
			fromAddr = unsignedByte(data, pos) | (unsignedByte(data, pos + 1) << 8);
			pos += 2;
		}
		if (pos + 2 > data.length) {
			return BINARY_BLOCK_FAILURE;
		}
		int toAddr = unsignedByte(data, pos) | (unsignedByte(data, pos + 1) << 8);
		pos += 2;

		// Sanity check that the end is not before the start.
		if (toAddr < fromAddr) {
			return BINARY_BLOCK_FAILURE;
		}

		int length = toAddr - fromAddr + 1;
		if (pos + length > data.length) {
			return BINARY_BLOCK_FAILURE;
		}
		System.arraycopy(data, pos, memory, fromAddr, length);
		pos += length;

		return new BinaryBlockResult(pos - offset, fromAddr, toAddr);
	}

	/**
	 * Loads every "binary block" (see {@link #loadBinaryBlock}) found in
	 * {@code data} back to back - mirrors C++'s {@code LoadBinaryFile}
	 * exactly, minus the actual file open/read (C++ takes a filename and
	 * opens an {@code ifstream} itself; this port takes the already-read
	 * file bytes directly, matching this class's own established
	 * byte-array-over-stream idiom - the caller reads the file, e.g. via
	 * {@code Files.readAllBytes}).
	 */
	public static Result loadBinaryFile(byte[] data, byte[] memory) {
		int minAddr = 0xFFFF;
		int maxAddr = 0;
		int fsize = 0;
		int pos = 0;
		while (pos < data.length) {
			BinaryBlockResult result = loadBinaryBlock(data, pos, memory);
			int blen = result.length();
			if (blen <= 0) {
				break;
			}
			if (result.fromAddr() < minAddr) {
				minAddr = result.fromAddr();
			}
			if (result.toAddr() > maxAddr) {
				maxAddr = result.toAddr();
			}
			fsize += blen;
			pos += result.inputBytesConsumed();
		}
		return new Result(fsize, minAddr, maxAddr);
	}

	/**
	 * Encodes one "binary block" ({@link #loadBinaryBlock}'s counterpart):
	 * {@code fromAddr}/{@code toAddr} words (optionally preceded by a
	 * {@code 0xFFFF} marker), then {@code memory[fromAddr..toAddr]}
	 * inclusive. Returns the encoded bytes directly rather than writing to
	 * C++'s {@code std::ostream&} - matches this class's established
	 * byte-array-over-stream idiom. Empty (zero-length) if {@code fromAddr > toAddr},
	 * matching C++'s own no-op guard.
	 */
	public static byte[] saveBinaryBlock(byte[] memory, int fromAddr, int toAddr, boolean withBinaryBlockHeader) {
		if (fromAddr > toAddr) {
			return new byte[0];
		}
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		if (withBinaryBlockHeader) {
			out.write(0xff);
			out.write(0xff);
		}
		out.write(fromAddr & 0xff);
		out.write((fromAddr >> 8) & 0xff);
		out.write(toAddr & 0xff);
		out.write((toAddr >> 8) & 0xff);
		out.writeBytes(java.util.Arrays.copyOfRange(memory, fromAddr, toAddr + 1));
		return out.toByteArray();
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
