// Generated automatically with "fut". Do not edit.
package net.sf.asap;

/**
 * Converter between the SAP format, native module formats
 * and the Atari 8-bit XEX executables.
 */
public class ASAPWriter
{
	public ASAPWriter()
	{
	}

	/**
	 * Maximum number of extensions returned by <code>GetSaveExts</code>.
	 */
	public static final int MAX_SAVE_EXTS = 3;

	/**
	 * Enumerates possible file types the given module can be written as.
	 * Returns the number of extensions written to <code>exts</code>.
	 * @param exts Receives filename extensions without the leading dot.
	 * @param info File information.
	 */
	public static int getSaveExts(String[] exts, ASAPInfo info)
	{
		int i = 0;
		switch (info.type) {
		case SAP_B:
		case SAP_C:
		case SAP_D:
			exts[i++] = "sap";
			String ext = info.getOriginalModuleExt();
			if (ext != null)
				exts[i++] = ext;
			if (info.type != ASAPModuleType.SAP_D || info.getPlayerRateScanlines() == 312)
				exts[i++] = "xex";
			break;
		case SAP_S:
			exts[i++] = "sap";
			break;
		case D15:
			exts[i++] = info.getOriginalModuleExt();
			break;
		default:
			exts[i++] = info.getOriginalModuleExt();
			exts[i++] = "sap";
			exts[i++] = "xex";
			break;
		}
		return i;
	}

	private static void twoDigitsToString(byte[] result, int offset, int value)
	{
		result[offset] = (byte) ('0' + value / 10);
		result[offset + 1] = (byte) ('0' + value % 10);
	}

	private static boolean secondsToString(byte[] result, int offset, int value)
	{
		if (value < 0 || value >= 6000000)
			return false;
		value /= 1000;
		twoDigitsToString(result, offset, value / 60);
		result[offset + 2] = ':';
		twoDigitsToString(result, offset + 3, value % 60);
		return true;
	}

	/**
	 * Maximum length of text representation of a duration.
	 * Corresponds to the longest format which is <code>"mm:ss.xxx"</code>.
	 */
	public static final int MAX_DURATION_LENGTH = 9;

	/**
	 * Writes text representation of the given duration.
	 * Returns the number of bytes written to <code>result</code>.
	 * @param result The output buffer.
	 * @param value Number of milliseconds.
	 */
	public static int durationToString(byte[] result, int value)
	{
		if (!secondsToString(result, 0, value))
			return 0;
		value %= 1000;
		if (value == 0)
			return 5;
		result[5] = '.';
		twoDigitsToString(result, 6, value / 10);
		value %= 10;
		if (value == 0)
			return 8;
		result[8] = (byte) ('0' + value);
		return 9;
	}
	private String sourceFilename;
	private ASAPFileLoader loader = null;

	/**
	 * Sets the loader for extra input files, if they are needed.
	 * Conversion from MD1/MD2 to SAP/XEX needs an extra D15/D8 file.
	 * @param sourceFilename Filename of the main input.
	 * @param loader Loader for extra files, if needed.
	 */
	public final void setInput(String sourceFilename, ASAPFileLoader loader)
	{
		this.sourceFilename = sourceFilename;
		this.loader = loader;
	}
	int init;
	int player;
	private int outputLen;
	private final byte[] output = new byte[65000];

	final void clearOutput()
	{
		this.outputLen = 0;
	}

	final byte[] getOutput()
	{
		return this.output;
	}

	final void writeByte(int value) throws ASAPConversionException
	{
		if (this.outputLen >= 65000)
			throw new ASAPConversionException("Output full");
		this.output[this.outputLen++] = (byte) value;
	}

	final void writeWord(int value) throws ASAPConversionException
	{
		writeByte(value & 255);
		writeByte(value >> 8);
	}

	final void writeBytes(byte[] array, int startIndex, int endIndex) throws ASAPConversionException
	{
		int length = endIndex - startIndex;
		if (this.outputLen + length > 65000)
			throw new ASAPConversionException("Output full");
		System.arraycopy(array, startIndex, this.output, this.outputLen, length);
		this.outputLen += length;
	}

	private void writeString(String s) throws ASAPConversionException
	{
		for (int _i = 0; _i < s.length(); _i++) {
			int c = s.charAt(_i);
			writeByte(c);
		}
	}

	private void writeDec(int value) throws ASAPConversionException
	{
		if (value >= 10) {
			writeDec(value / 10);
			value %= 10;
		}
		writeByte('0' + value);
	}

	private void writeTextSapTag(String tag, String value) throws ASAPConversionException
	{
		writeString(tag);
		writeByte('"');
		if (value.length() == 0)
			value = "<?>";
		writeString(value);
		writeByte('"');
		writeByte('\r');
		writeByte('\n');
	}

	private void writeDecSapTag(String tag, int value) throws ASAPConversionException
	{
		writeString(tag);
		writeDec(value);
		writeByte('\r');
		writeByte('\n');
	}

	private void writeHexSapTag(String tag, int value) throws ASAPConversionException
	{
		if (value < 0)
			return;
		writeString(tag);
		for (int i = 12; i >= 0; i -= 4) {
			int digit = value >> i & 15;
			writeByte(digit + (digit < 10 ? '0' : 55));
		}
		writeByte('\r');
		writeByte('\n');
	}

	private void writeSapHeader(ASAPInfo info, int type) throws ASAPConversionException
	{
		writeString("SAP\r\n");
		writeTextSapTag("AUTHOR ", info.getAuthor());
		writeTextSapTag("NAME ", info.getTitle());
		writeTextSapTag("DATE ", info.getDate());
		if (info.getSongs() > 1) {
			writeDecSapTag("SONGS ", info.getSongs());
			if (info.getDefaultSong() > 0)
				writeDecSapTag("DEFSONG ", info.getDefaultSong());
		}
		if (info.getChannels() > 1)
			writeString("STEREO\r\n");
		if (info.isNtsc())
			writeString("NTSC\r\n");
		writeString("TYPE ");
		writeByte(type);
		writeByte('\r');
		writeByte('\n');
		if (info.getPlayerRateScanlines() != (type == 'S' ? 78 : 312) || info.isNtsc())
			writeDecSapTag("FASTPLAY ", info.getPlayerRateScanlines());
		if (type == 'C')
			writeHexSapTag("MUSIC ", info.music);
		writeHexSapTag("INIT ", this.init);
		writeHexSapTag("PLAYER ", this.player);
		writeHexSapTag("COVOX ", info.getCovoxAddress());
		for (int song = 0; song < info.getSongs(); song++) {
			if (info.getDuration(song) < 0)
				break;
			writeString("TIME ");
			final byte[] s = new byte[9];
			writeBytes(s, 0, durationToString(s, info.getDuration(song)));
			if (info.getLoop(song))
				writeString(" LOOP");
			writeByte('\r');
			writeByte('\n');
		}
	}

	private void writePlaTaxLda0() throws ASAPConversionException
	{
		writeByte(104);
		writeByte(170);
		writeByte(169);
		writeByte(0);
	}

	private void writeCmcInit(ASAPInfo info) throws ASAPConversionException
	{
		writeWord(4064);
		writeWord(4080);
		writeByte(72);
		writeByte(162);
		writeByte(info.music & 255);
		writeByte(160);
		writeByte(info.music >> 8);
		writeByte(169);
		writeByte(112);
		writeByte(32);
		writeWord(this.player + 3);
		writePlaTaxLda0();
		writeByte(76);
		writeWord(this.player + 3);
		this.init = 4064;
		this.player += 6;
	}

	private void writeExecutableFromSap(boolean toSap, ASAPInfo info, int type, byte[] module, int moduleLen) throws ASAPConversionException
	{
		this.init = info.getInitAddress();
		this.player = info.player;
		if (toSap)
			writeSapHeader(info, type);
		writeBytes(module, info.headerLen, moduleLen);
	}

	/**
	 * Writes a native module, possibly extracting it from a SAP file.
	 * @param info Source file information, optionally with the address changed.
	 * @param module Contents of the source file.
	 * @param moduleLen Length of the source file.
	 */
	private byte[] writeNative(ASAPInfo info, byte[] module, int moduleLen, boolean rmtNames) throws ASAPConversionException
	{
		final ASAPModuleTransfer trans = new ASAPModuleTransfer();
		trans.source = module;
		trans.output = this.output;
		trans.outputOffset = this.outputLen;
		switch (info.type) {
		case SAP_B:
		case SAP_C:
		case SAP_D:
			int offset = info.getRmtSapOffset(module, moduleLen);
			if (offset > 0) {
				trans.sourceOffset = offset - 2;
				trans.transferModule(info, moduleLen, true, false);
				break;
			}
			trans.sourceOffset = info.headerLen;
			int blockLen = info.getFirstBlockLen(module);
			if (blockLen < 7 || trans.sourceOffset + blockLen >= moduleLen)
				throw new ASAPConversionException("Cannot extract module from SAP");
			if (info.originalType == ASAPModuleType.FC)
				trans.writeBytes(module, trans.sourceOffset + 6, blockLen - 6);
			else
				trans.transferModule(info, trans.sourceOffset + blockLen, true, false);
			break;
		case FC:
		case D15:
			trans.writeBytes(module, 0, moduleLen);
			break;
		default:
			trans.sourceOffset = 0;
			trans.transferModule(info, moduleLen, true, rmtNames);
			break;
		}
		this.outputLen = trans.outputOffset;
		return this.output;
	}

	private int writeExecutableHeaderForSongPos(boolean toSap, ASAPInfo info, int type, int player, int codeForOneSong, int codeForManySongs, int playerOffset) throws ASAPConversionException
	{
		this.player = player + playerOffset;
		if (info.getSongs() != 1) {
			this.init = player - codeForManySongs;
			if (toSap)
				writeSapHeader(info, type);
			return player - codeForManySongs - info.getSongs();
		}
		this.init = player - codeForOneSong;
		if (toSap)
			writeSapHeader(info, type);
		return player - codeForOneSong;
	}

	private void writeMptInit(ASAPInfo info, int player) throws ASAPConversionException
	{
		if (info.getSongs() != 1) {
			writeBytes(info.songPos, 0, info.getSongs());
			writeByte(72);
		}
		writeByte(160);
		writeByte(info.music & 255);
		writeByte(162);
		writeByte(info.music >> 8);
		writeByte(169);
		writeByte(0);
		writeByte(32);
		writeWord(player);
	}

	private void writeMptPlaySong(ASAPInfo info, int startAddr) throws ASAPConversionException
	{
		if (info.getSongs() != 1) {
			writeByte(104);
			writeByte(168);
			writeByte(190);
			writeWord(startAddr);
		}
		else {
			writeByte(162);
			writeByte(0);
		}
		writeByte(169);
		writeByte(2);
	}

	final void writeExecutable(boolean toSap, ASAPInfo info, byte[] module, int moduleLen) throws ASAPConversionException
	{
		byte[] playerRoutine = ASAP6502.getPlayerRoutine(info);
		int player = -1;
		int playerLastByte = -1;
		int music = info.music;
		if (playerRoutine != null) {
			player = ASAPInfo.getWord(playerRoutine, 2);
			playerLastByte = ASAPInfo.getWord(playerRoutine, 4);
			if (music <= playerLastByte)
				throw new ASAPConversionException("Module address conflicts with the player routine");
		}
		this.outputLen = 0;
		int startAddr;
		switch (info.type) {
		case SAP_B:
			writeExecutableFromSap(toSap, info, 'B', module, moduleLen);
			break;
		case SAP_C:
			writeExecutableFromSap(toSap, info, 'C', module, moduleLen);
			if (!toSap)
				writeCmcInit(info);
			break;
		case SAP_D:
			writeExecutableFromSap(toSap, info, 'D', module, moduleLen);
			break;
		case SAP_S:
			writeExecutableFromSap(toSap, info, 'S', module, moduleLen);
			break;
		case CMC:
		case CM3:
		case CMR:
		case CMS:
			this.init = -1;
			this.player = player;
			if (toSap)
				writeSapHeader(info, 'C');
			writeNative(info, module, moduleLen, false);
			writeBytes(playerRoutine, 2, playerLastByte - player + 7);
			if (!toSap)
				writeCmcInit(info);
			break;
		case DLT:
			startAddr = writeExecutableHeaderForSongPos(toSap, info, 'B', player, 5, 7, 259);
			if (moduleLen == 11270) {
				writeBytes(module, 0, 4);
				writeWord(19456);
				writeBytes(module, 6, moduleLen);
				writeByte(0);
			}
			else
				writeBytes(module, 0, moduleLen);
			writeWord(startAddr);
			writeWord(playerLastByte);
			if (info.getSongs() != 1) {
				writeBytes(info.songPos, 0, info.getSongs());
				writeByte(170);
				writeByte(188);
				writeWord(startAddr);
			}
			else {
				writeByte(160);
				writeByte(0);
			}
			writeByte(76);
			writeWord(player + 256);
			writeBytes(playerRoutine, 6, playerLastByte - player + 7);
			break;
		case MPT:
			startAddr = writeExecutableHeaderForSongPos(toSap, info, 'B', player, 13, 17, 3);
			writeNative(info, module, moduleLen, false);
			writeWord(startAddr);
			writeWord(playerLastByte);
			writeMptInit(info, player);
			writeMptPlaySong(info, startAddr);
			writeBytes(playerRoutine, 6, playerLastByte - player + 7);
			break;
		case MD1:
		case MD2:
			if (this.loader == null)
				throw new ASAPConversionException("MD1/MD2 conversion not supported by this program");
			final ASAPMptSamples samples = new ASAPMptSamples();
			if (!samples.load(this.loader, this.sourceFilename))
				throw new ASAPConversionException("Missing D15/D8 file");
			samples.relocate(music, music + moduleLen - 5);
			int samplesPage = (samples.content[0] & 0xff) - 1;
			int codeForOneSong = info.type == ASAPModuleType.MD1 ? 29 : 27;
			startAddr = writeExecutableHeaderForSongPos(toSap, info, 'D', player, codeForOneSong, codeForOneSong + 4, 3);
			writeNative(info, module, moduleLen, false);
			writeByte(224);
			writeByte(samplesPage);
			writeByte(255);
			writeByte(samplesPage + (samples.contentLength >> 8));
			writeBytes(samples.content, 0, samples.contentLength);
			writeWord(startAddr);
			writeWord(playerLastByte);
			writeMptInit(info, player);
			writeByte(160);
			writeByte(224);
			writeByte(162);
			writeByte(samplesPage);
			writeByte(169);
			writeByte(3);
			writeByte(32);
			writeWord(player);
			writeMptPlaySong(info, startAddr);
			writeByte(32);
			writeWord(player);
			if (info.type == ASAPModuleType.MD1) {
				writeByte(162);
				writeByte(samples.is15kHz ? 1 : 0);
				writeByte(169);
				writeByte(5);
			}
			else {
				writeByte(169);
				writeByte(6);
			}
			writeBytes(playerRoutine, 6, playerLastByte - player + 7);
			break;
		case RMT:
			this.init = 3200;
			this.player = 1539;
			if (toSap)
				writeSapHeader(info, 'B');
			writeNative(info, module, moduleLen, false);
			writeWord(3200);
			if (info.getSongs() != 1) {
				writeWord(3210 + info.getSongs());
				writeByte(168);
				writeByte(185);
				writeWord(3211);
			}
			else {
				writeWord(3208);
				writeByte(169);
				writeByte(0);
			}
			writeByte(162);
			writeByte(music & 255);
			writeByte(160);
			writeByte(music >> 8);
			writeByte(76);
			writeWord(1536);
			if (info.getSongs() != 1)
				writeBytes(info.songPos, 0, info.getSongs());
			writeBytes(playerRoutine, 2, playerLastByte - player + 7);
			break;
		case TMC:
			int perFrame = module[37] & 0xff;
			this.player = player + WRITE_EXECUTABLE_TMC_PLAYER_OFFSET[perFrame - 1];
			this.init = this.player + WRITE_EXECUTABLE_TMC_INIT_OFFSET[perFrame - 1];
			if (info.getSongs() != 1)
				this.init -= 3;
			if (toSap)
				writeSapHeader(info, 'B');
			writeNative(info, module, moduleLen, false);
			writeWord(this.init);
			writeWord(playerLastByte);
			if (info.getSongs() != 1)
				writeByte(72);
			writeByte(160);
			writeByte(music & 255);
			writeByte(162);
			writeByte(music >> 8);
			writeByte(169);
			writeByte(112);
			writeByte(32);
			writeWord(player);
			if (info.getSongs() != 1)
				writePlaTaxLda0();
			else {
				writeByte(169);
				writeByte(96);
			}
			switch (perFrame) {
			case 2:
				writeByte(6);
				writeByte(0);
				writeByte(76);
				writeWord(player);
				writeByte(165);
				writeByte(0);
				writeByte(230);
				writeByte(0);
				writeByte(74);
				writeByte(144);
				writeByte(5);
				writeByte(176);
				writeByte(6);
				break;
			case 3:
			case 4:
				writeByte(160);
				writeByte(1);
				writeByte(132);
				writeByte(0);
				writeByte(208);
				writeByte(10);
				writeByte(198);
				writeByte(0);
				writeByte(208);
				writeByte(12);
				writeByte(160);
				writeByte(perFrame);
				writeByte(132);
				writeByte(0);
				writeByte(208);
				writeByte(3);
				break;
			default:
				break;
			}
			writeBytes(playerRoutine, 6, playerLastByte - player + 7);
			break;
		case TM2:
			this.init = 4992;
			this.player = 2051;
			if (toSap)
				writeSapHeader(info, 'B');
			writeNative(info, module, moduleLen, false);
			writeWord(4992);
			if (info.getSongs() != 1) {
				writeWord(5008);
				writeByte(72);
			}
			else
				writeWord(5006);
			writeByte(160);
			writeByte(music & 255);
			writeByte(162);
			writeByte(music >> 8);
			writeByte(169);
			writeByte(112);
			writeByte(32);
			writeWord(2048);
			if (info.getSongs() != 1)
				writePlaTaxLda0();
			else {
				writeByte(169);
				writeByte(0);
				writeByte(170);
			}
			writeByte(76);
			writeWord(2048);
			writeBytes(playerRoutine, 2, playerLastByte - player + 7);
			break;
		case FC:
			this.init = player;
			this.player = player + 3;
			if (toSap)
				writeSapHeader(info, 'B');
			writeWord(65535);
			writeWord(music);
			writeWord(music + moduleLen - 1);
			writeBytes(module, 0, moduleLen);
			writeBytes(playerRoutine, 2, playerLastByte - player + 7);
			break;
		default:
			throw new ASAPConversionException("Unknown format");
		}
	}

	private static final int XEX_INFO_CHARACTERS_PER_LINE = 32;

	private static int padXexInfo(byte[] dest, int offset, int endColumn)
	{
		while (offset % 32 != endColumn)
			dest[offset++] = ' ';
		return offset;
	}

	private static int formatXexInfoText(byte[] dest, int destLen, int endColumn, String src, boolean author)
	{
		int srcLen = src.length();
		for (int srcOffset = 0; srcOffset < srcLen;) {
			int c = src.charAt(srcOffset++);
			if (c == ' ') {
				if (author && srcOffset < srcLen && src.charAt(srcOffset) == '&') {
					int authorLen;
					for (authorLen = 1; srcOffset + authorLen < srcLen; authorLen++) {
						if (src.charAt(srcOffset + authorLen) == ' ' && srcOffset + authorLen + 1 < srcLen && src.charAt(srcOffset + authorLen + 1) == '&')
							break;
					}
					if (authorLen <= 32 && destLen % 32 + 1 + authorLen > 32) {
						destLen = padXexInfo(dest, destLen, 1);
						continue;
					}
				}
				int wordLen;
				for (wordLen = 0; srcOffset + wordLen < srcLen && src.charAt(srcOffset + wordLen) != ' '; wordLen++) {
				}
				if (wordLen <= 32 && destLen % 32 + 1 + wordLen > 32) {
					destLen = padXexInfo(dest, destLen, 0);
					continue;
				}
			}
			dest[destLen++] = (byte) c;
		}
		return padXexInfo(dest, destLen, endColumn);
	}

	private void writeXexInfoTextDl(int address, int len, int verticalScrollAt) throws ASAPConversionException
	{
		writeByte(verticalScrollAt == 0 ? 98 : 66);
		writeWord(address);
		for (int i = 32; i < len; i += 32)
			writeByte(i == verticalScrollAt ? 34 : 2);
	}

	private void writeXexInfo(ASAPInfo info) throws ASAPConversionException
	{
		final byte[] title = new byte[256];
		int titleLen = formatXexInfoText(title, 0, 0, info.getTitle().length() == 0 ? "(untitled)" : info.getTitle(), false);
		final byte[] author = new byte[256];
		int authorLen;
		if (info.getAuthor().length() > 0) {
			author[0] = 'b';
			author[1] = 'y';
			author[2] = ' ';
			authorLen = formatXexInfoText(author, 3, 0, info.getAuthor(), true);
		}
		else
			authorLen = 0;
		final byte[] other = new byte[256];
		int otherLen = formatXexInfoText(other, 0, 19, info.getDate(), false);
		otherLen = formatXexInfoText(other, otherLen, 27, info.getChannels() > 1 ? " STEREO" : "   MONO", false);
		int duration = info.getDuration(info.getDefaultSong());
		if (duration > 0 && secondsToString(other, otherLen, duration + 999))
			otherLen += 5;
		else
			otherLen = padXexInfo(other, otherLen, 0);
		int totalCharacters = titleLen + authorLen + otherLen;
		int totalLines = totalCharacters / 32;
		int otherAddress = 64592 - otherLen;
		int titleAddress = otherAddress - authorLen - 8 - titleLen;
		writeWord(titleAddress);
		writeBytes(FuResource.getByteArray("xexinfo.obx", 178), 4, 6);
		writeBytes(title, 0, titleLen);
		for (int i = 0; i < 8; i++)
			writeByte(85);
		writeBytes(author, 0, authorLen);
		writeBytes(other, 0, otherLen);
		for (int i = totalLines; i < 26; i++)
			writeByte(112);
		writeByte(48);
		writeXexInfoTextDl(titleAddress, titleLen, titleLen - 32);
		writeByte(8);
		writeByte(0);
		for (int i = 0; i < authorLen; i += 32)
			writeByte(2);
		writeByte(16);
		for (int i = 0; i < otherLen; i += 32)
			writeByte(2);
		writeBytes(FuResource.getByteArray("xexinfo.obx", 178), 6, 178);
	}

	/**
	 * Writes the given module in the SAP format.
	 * @param info Source file information, with metadata updated for the output.
	 * @param module Contents of the source file.
	 * @param moduleLen Length of the source file.
	 */
	public final byte[] writeSap(ASAPInfo info, byte[] module, int moduleLen) throws ASAPConversionException
	{
		writeExecutable(true, info, module, moduleLen);
		return this.output;
	}

	/**
	 * Returns the number of bytes written by <code>WriteNative</code> or <code>WriteSap</code>.
	 */
	public final int getOutputLength()
	{
		return this.outputLen;
	}

	/**
	 * Writes a SAP/XEX/native module file.
	 * If <code>Write</code> is used, <code>Save</code> needs to be overridden in a subclass of <code>ASAPWriter</code>.
	 * @param filename Output filename.
	 * @param buffer Contents to write.
	 * @param offset Starting index in <code>buffer</code> to write into the file.
	 * @param length Number of bytes to write.
	 */
	protected void save(String filename, byte[] buffer, int offset, int length) throws ASAPIOException
	{
	}

	private void writeMptSamples(String moduleFilename, ASAPInfo info, byte[] module, int moduleLen, int extNumber) throws ASAPConversionException, ASAPIOException
	{
		int offset = info.headerLen + this.outputLen + 4;
		if (offset + 256 >= moduleLen || module[offset - 4] != (byte) 224 || module[offset - 2] != (byte) 255)
			throw new ASAPConversionException("Invalid samples block");
		int length = ((module[offset - 1] & 0xff) - (module[offset - 3] & 0xff)) << 8 | 32;
		if (offset + length >= moduleLen)
			throw new ASAPConversionException("Invalid samples block");
		int moduleExt = moduleFilename.length() - 3;
		String extInitial = moduleFilename.charAt(moduleExt) == 'M' ? "D" : "d";
		String filename = String.format("%s%s%d", moduleFilename.substring(0, moduleExt), extInitial, extNumber);
		save(filename, module, offset, length);
	}

	/**
	 * Writes the given module in a possibly different file format.
	 * @param targetFilename Output filename, used to determine the format.
	 * @param info File information got from the source file with data updated for the output file.
	 * @param module Contents of the source file.
	 * @param moduleLen Length of the source file.
	 * @param tag Display information (xex output only).
	 */
	public final void write(String targetFilename, ASAPInfo info, byte[] module, int moduleLen, boolean tag) throws ASAPConversionException, ASAPIOException
	{
		int destExt = ASAPInfo.getPackedExt(targetFilename);
		switch (destExt) {
		case 7364979:
			writeExecutable(true, info, module, moduleLen);
			save(targetFilename, this.output, 0, this.outputLen);
			return;
		case 7890296:
			writeExecutable(false, info, module, moduleLen);
			switch (info.type) {
			case SAP_D:
			case MD1:
			case MD2:
				if (info.getPlayerRateScanlines() != 312)
					throw new ASAPConversionException("Impossible conversion");
				writeBytes(FuResource.getByteArray("xexd.obx", 114), 2, 114);
				writeWord(this.init);
				if (this.player < 0) {
					writeByte(96);
					writeByte(96);
					writeByte(96);
				}
				else {
					writeByte(76);
					writeWord(this.player);
				}
				writeByte(info.getDefaultSong());
				break;
			case SAP_S:
				throw new ASAPConversionException("Impossible conversion");
			default:
				writeBytes(FuResource.getByteArray("xexb.obx", 183), 2, 183);
				writeWord(this.init);
				writeByte(76);
				writeWord(this.player);
				writeByte(info.getDefaultSong());
				int fastplay = info.getPlayerRateScanlines();
				writeByte(fastplay & 1);
				writeByte((fastplay >> 1) % 156);
				writeByte((fastplay >> 1) % 131);
				writeByte(fastplay / 312);
				writeByte(fastplay / 262);
				break;
			}
			if (tag)
				writeXexInfo(info);
			writeWord(736);
			writeWord(737);
			writeWord(tag ? 256 : 292);
			final FlashPack flashPack = new FlashPack();
			flashPack.compress(this);
			save(targetFilename, this.output, 0, this.outputLen);
			return;
		default:
			String possibleExt = info.getOriginalModuleExt();
			if (possibleExt != null) {
				int packedPossibleExt = ASAPInfo.packExt(possibleExt);
				if (destExt == packedPossibleExt || (destExt == 3698036 && packedPossibleExt == 6516084)) {
					this.outputLen = 0;
					writeNative(info, module, moduleLen, true);
					save(targetFilename, this.output, 0, this.outputLen);
					if (info.type == ASAPModuleType.SAP_D) {
						switch (info.originalType) {
						case MD1:
							switch (module[moduleLen - 2230]) {
							case 0:
								writeMptSamples(targetFilename, info, module, moduleLen, 8);
								break;
							case 1:
								writeMptSamples(targetFilename, info, module, moduleLen, 15);
								break;
							default:
								throw new ASAPConversionException("Unknown MD1 samples");
							}
							break;
						case MD2:
							writeMptSamples(targetFilename, info, module, moduleLen, 8);
							break;
						default:
							break;
						}
					}
					return;
				}
			}
			throw new ASAPConversionException("Impossible conversion");
		}
	}

	private static final int[] WRITE_EXECUTABLE_TMC_PLAYER_OFFSET = { 3, -9, -10, -10 };

	private static final int[] WRITE_EXECUTABLE_TMC_INIT_OFFSET = { -14, -16, -17, -17 };
}
