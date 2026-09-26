// Generated automatically with "fut". Do not edit,
// EXCEPT for the two RMT-specific methods appended at the end of this class
// (stepFrame/getPokeyRegisterShadow) - see their own javadoc. Everything
// else in this file (and every other file in this package) is vendored,
// unmodified, from asap-8.0.0-java-src.zip (see lib/java/README.md).
package net.sf.asap;
import java.util.Arrays;

/**
 * Atari 8-bit chip music emulator.
 * This class performs no I/O operations - all music data must be passed in byte arrays.
 */
public class ASAP
{
	public ASAP()
	{
		this.silenceCycles = 0;
		this.cpu.asap = this;
	}

	/**
	 * Default output sample rate.
	 */
	public static final int SAMPLE_RATE = 44100;
	int nextEventCycle;
	private final Cpu6502 cpu = new Cpu6502();
	private int nextScanlineCycle;
	private NmiStatus nmist;
	private int consol;
	private final byte[] covox = new byte[4];
	private final PokeyPair pokeys = new PokeyPair();
	private final ASAPInfo moduleInfo = new ASAPInfo();
	private int mptSamplesPage;
	private boolean mptSamples15kHz;
	private int mptSamplesCurrentAddress;
	private boolean mptSamplesSecondNibble;
	private int nextPlayerCycle;
	private int tmcPerFrameCounter;
	private int currentSong;
	private int currentDuration;
	private int blocksPlayed;
	private int silenceCycles;
	private int silenceCyclesCounter;
	private boolean gtiaOrCovoxPlayedThisFrame;
	private int currentSampleRate = 44100;

	/**
	 * Returns the output sample rate.
	 */
	public final int getSampleRate()
	{
		return this.currentSampleRate;
	}

	/**
	 * Sets the output sample rate.
	 */
	public final void setSampleRate(int sampleRate)
	{
		this.currentSampleRate = sampleRate;
	}

	/**
	 * Enables silence detection.
	 * Causes playback to stop after the specified period of silence.
	 * @param seconds Length of silence which ends playback. Zero disables silence detection.
	 */
	public final void detectSilence(int seconds)
	{
		this.silenceCyclesCounter = this.silenceCycles = seconds * 1773447;
	}

	final int peekHardware(int addr)
	{
		switch (addr & 65311) {
		case 53268:
			return this.moduleInfo.isNtsc() ? 15 : 1;
		case 53279:
			return ~this.consol & 15;
		case 53770:
		case 53786:
		case 53774:
		case 53790:
			return this.pokeys.peek(addr, this.cpu.cycle);
		case 53772:
		case 53788:
		case 53775:
		case 53791:
			return 255;
		case 54283:
		case 54299:
			int cycle = this.cpu.cycle;
			if (cycle > (this.moduleInfo.isNtsc() ? 29868 : 35568))
				return 0;
			return cycle / 228;
		case 54287:
		case 54303:
			switch (this.nmist) {
			case RESET:
				return 31;
			case WAS_V_BLANK:
				return 95;
			default:
				return this.cpu.cycle < 28291 ? 31 : 95;
			}
		default:
			return this.cpu.memory[addr] & 0xff;
		}
	}

	final void pokeHardware(int addr, int data)
	{
		if (addr >> 8 == 210) {
			int t = this.pokeys.poke(addr, data, this.cpu.cycle);
			if (this.nextEventCycle > t)
				this.nextEventCycle = t;
		}
		else if ((addr & 65295) == 54282) {
			int x = this.cpu.cycle % 114;
			this.cpu.cycle += (x <= 106 ? 106 : 220) - x;
		}
		else if ((addr & 65295) == 54287) {
			this.nmist = this.cpu.cycle < 28292 ? NmiStatus.ON_V_BLANK : NmiStatus.RESET;
		}
		else if ((addr & 65280) == this.moduleInfo.getCovoxAddress()) {
			addr &= 3;
			Pokey pokey = addr == 0 || addr == 3 ? this.pokeys.basePokey : this.pokeys.extraPokey;
			int delta = data - (this.covox[addr] & 0xff);
			if (delta != 0) {
				pokey.addExternalDelta(this.pokeys, this.cpu.cycle, delta << 17);
				this.covox[addr] = (byte) data;
				this.gtiaOrCovoxPlayedThisFrame = true;
			}
		}
		else if ((addr & 65311) == 53279) {
			int delta = ((this.consol & 8) - (data & 8)) << 20;
			if (delta != 0) {
				int cycle = this.cpu.cycle;
				this.pokeys.basePokey.addExternalDelta(this.pokeys, cycle, delta);
				this.pokeys.extraPokey.addExternalDelta(this.pokeys, cycle, delta);
				this.gtiaOrCovoxPlayedThisFrame = true;
			}
			this.consol = data;
		}
		else
			this.cpu.memory[addr] = (byte) data;
	}

	private void storeJsr(int addr, int target)
	{
		this.cpu.memory[addr] = 32;
		this.cpu.memory[addr + 1] = (byte) target;
		this.cpu.memory[addr + 2] = (byte) (target >> 8);
	}

	private void call6502(int target)
	{
		storeJsr(53760, target);
		this.cpu.memory[53763] = (byte) 210;
		this.cpu.pc = 53760;
	}

	private void call6502PreservingRegisters(int target)
	{
		this.cpu.pushPc();
		this.cpu.memory[53760] = 8;
		this.cpu.memory[53761] = 72;
		this.cpu.memory[53762] = (byte) 138;
		this.cpu.memory[53763] = 72;
		this.cpu.memory[53764] = (byte) 152;
		this.cpu.memory[53765] = 72;
		storeJsr(53766, target);
		this.cpu.memory[53769] = 104;
		this.cpu.memory[53770] = (byte) 168;
		this.cpu.memory[53771] = 104;
		this.cpu.memory[53772] = (byte) 170;
		this.cpu.memory[53773] = 104;
		this.cpu.memory[53774] = 64;
		this.cpu.pc = 53760;
	}

	private void call6502Player()
	{
		int player = this.moduleInfo.player;
		switch (this.moduleInfo.type) {
		case SAP_B:
			call6502(player);
			break;
		case SAP_C:
		case CMC:
		case CM3:
		case CMR:
		case CMS:
			call6502(player + 6);
			break;
		case SAP_D:
			if (player >= 0)
				call6502PreservingRegisters(player);
			break;
		case SAP_S:
			int i = (this.cpu.memory[69] & 0xff) - 1;
			this.cpu.memory[69] = (byte) i;
			if (i == 0)
				this.cpu.memory[45179] = (byte) ((this.cpu.memory[45179] & 0xff) + 1);
			break;
		case DLT:
			call6502(player + 259);
			break;
		case MPT:
		case RMT:
		case TM2:
		case FC:
			call6502(player + 3);
			break;
		case MD1:
		case MD2:
			call6502PreservingRegisters(player + 3);
			break;
		case TMC:
			if (--this.tmcPerFrameCounter <= 0) {
				this.tmcPerFrameCounter = this.cpu.memory[this.moduleInfo.getMusicAddress() + 31] & 0xff;
				call6502(player + 3);
			}
			else
				call6502(player + 6);
			break;
		case D15:
			if (this.cpu.cycle < 1254 || this.mptSamplesCurrentAddress >> 8 >= (this.cpu.memory[this.moduleInfo.getMusicAddress() + 16 + this.currentSong] & 0xff))
				break;
			int b = this.cpu.memory[this.mptSamplesCurrentAddress] & 0xff;
			if (this.mptSamplesSecondNibble) {
				this.mptSamplesCurrentAddress++;
				this.mptSamplesSecondNibble = false;
			}
			else {
				b >>= 4;
				this.mptSamplesSecondNibble = true;
			}
			this.pokeys.poke(53761, b | 240, this.cpu.cycle);
			break;
		}
	}

	final boolean isIrq()
	{
		return this.pokeys.basePokey.irqst != 255;
	}

	final void handleEvent()
	{
		int cycle = this.cpu.cycle;
		if (cycle >= this.nextScanlineCycle) {
			if (cycle - this.nextScanlineCycle < 50)
				this.cpu.cycle = cycle += 9;
			this.nextScanlineCycle += 114;
			if (cycle >= this.nextPlayerCycle) {
				call6502Player();
				this.nextPlayerCycle += 114 * this.moduleInfo.getPlayerRateScanlines();
			}
		}
		int nextEventCycle = this.nextScanlineCycle;
		nextEventCycle = this.pokeys.basePokey.checkIrq(cycle, nextEventCycle);
		nextEventCycle = this.pokeys.extraPokey.checkIrq(cycle, nextEventCycle);
		this.nextEventCycle = nextEventCycle;
	}

	private int do6502Frame()
	{
		this.nextEventCycle = 0;
		this.nextScanlineCycle = 0;
		this.nmist = this.nmist == NmiStatus.RESET ? NmiStatus.ON_V_BLANK : NmiStatus.WAS_V_BLANK;
		int cycles = this.moduleInfo.isNtsc() ? 29868 : 35568;
		this.cpu.doFrame(cycles);
		this.cpu.cycle -= cycles;
		if (this.nextPlayerCycle != 8388608)
			this.nextPlayerCycle -= cycles;
		for (int i = 3;; i >>= 1) {
			this.pokeys.basePokey.channels[i].endFrame(cycles);
			this.pokeys.extraPokey.channels[i].endFrame(cycles);
			if (i == 0)
				break;
		}
		return cycles;
	}

	private int doFrame()
	{
		this.gtiaOrCovoxPlayedThisFrame = false;
		this.pokeys.startFrame();
		int cycles = do6502Frame();
		this.pokeys.endFrame(cycles);
		return cycles;
	}

	/**
	 * Loads music data ("module").
	 * @param filename Filename, used to determine the format.
	 * @param module Contents of the file.
	 * @param moduleLen Length of the file.
	 */
	public final void load(String filename, byte[] module, int moduleLen) throws ASAPFormatException
	{
		loadWithExtraFiles(filename, module, moduleLen, null);
	}

	/**
	 * Loads music data, possibly from several files.
	 * @param filename Filename of the main file ("module").
	 * @param loader File loader.
	 */
	public final void loadFiles(String filename, ASAPFileLoader loader) throws ASAPFormatException
	{
		final byte[] module = new byte[65000];
		int moduleLen = loader.load(filename, module, 65000);
		if (moduleLen < 0)
			throw new ASAPFormatException("File not found");
		loadWithExtraFiles(filename, module, moduleLen, loader);
	}

	/**
	 * Loads music data, possibly with extra files.
	 * @param filename Filename, used to determine the format.
	 * @param module Contents of the main file.
	 * @param moduleLen Length of the main file.
	 * @param loader Loader for extra files, should they be needed.
	 */
	public final void loadWithExtraFiles(String filename, byte[] module, int moduleLen, ASAPFileLoader loader) throws ASAPFormatException
	{
		this.moduleInfo.load(filename, module, moduleLen);
		byte[] playerRoutine = ASAP6502.getPlayerRoutine(this.moduleInfo);
		if (playerRoutine != null) {
			int player = ASAPInfo.getWord(playerRoutine, 2);
			int playerLastByte = ASAPInfo.getWord(playerRoutine, 4);
			int music = this.moduleInfo.getMusicAddress();
			if (music <= playerLastByte)
				throw new ASAPFormatException("Module address conflicts with the player routine");
			if (this.moduleInfo.type == ASAPModuleType.MD1 || this.moduleInfo.type == ASAPModuleType.MD2) {
				if (loader == null)
					throw new ASAPFormatException("MD1/MD2 not supported in this ASAP port");
				final ASAPMptSamples samples = new ASAPMptSamples();
				if (!samples.load(loader, filename))
					throw new ASAPFormatException("Missing D15/D8 file");
				samples.relocate(music, music + moduleLen - 5);
				this.mptSamplesPage = samples.content[0] & 0xff;
				this.mptSamples15kHz = samples.is15kHz;
				System.arraycopy(samples.content, 0, this.cpu.memory, (this.mptSamplesPage << 8) - 32, samples.contentLength);
			}
			this.cpu.memory[19456] = 0;
			if (this.moduleInfo.type == ASAPModuleType.FC)
				System.arraycopy(module, 0, this.cpu.memory, music, moduleLen);
			else
				System.arraycopy(module, 6, this.cpu.memory, music, moduleLen - 6);
			System.arraycopy(playerRoutine, 6, this.cpu.memory, player, playerLastByte + 1 - player);
			if (this.moduleInfo.player < 0)
				this.moduleInfo.player = player;
			return;
		}
		if (this.moduleInfo.type == ASAPModuleType.D15) {
			System.arraycopy(module, 0, this.cpu.memory, this.moduleInfo.getMusicAddress(), moduleLen);
			return;
		}
		Arrays.fill(this.cpu.memory, (byte) 0);
		int moduleIndex = this.moduleInfo.headerLen + 2;
		while (moduleIndex + 5 <= moduleLen) {
			int startAddr = ASAPInfo.getWord(module, moduleIndex);
			int blockLen = ASAPInfo.getWord(module, moduleIndex + 2) + 1 - startAddr;
			if (blockLen <= 0 || moduleIndex + blockLen > moduleLen)
				throw new ASAPFormatException("Invalid binary block");
			moduleIndex += 4;
			System.arraycopy(module, moduleIndex, this.cpu.memory, startAddr, blockLen);
			moduleIndex += blockLen;
			if (moduleIndex == moduleLen)
				return;
			if (moduleIndex + 7 <= moduleLen && module[moduleIndex] == (byte) 255 && module[moduleIndex + 1] == (byte) 255)
				moduleIndex += 2;
		}
		throw new ASAPFormatException("Invalid binary block");
	}

	/**
	 * Returns information about the loaded module.
	 */
	public final ASAPInfo getInfo()
	{
		return this.moduleInfo;
	}

	private void do6502Init(int pc, int a, int x, int y) throws ASAPFormatException
	{
		this.cpu.pc = pc;
		this.cpu.a = a & 255;
		this.cpu.x = x & 255;
		this.cpu.y = y & 255;
		this.cpu.memory[53760] = (byte) 210;
		this.cpu.memory[510] = (byte) 255;
		this.cpu.memory[511] = (byte) 209;
		this.cpu.s = 253;
		for (int frame = 0; frame < 50; frame++) {
			do6502Frame();
			if (this.cpu.pc == 53760)
				return;
		}
		throw new ASAPFormatException("INIT routine didn't return");
	}

	private void restartSong() throws ASAPFormatException
	{
		this.nextPlayerCycle = 8388608;
		this.blocksPlayed = 0;
		this.silenceCyclesCounter = this.silenceCycles;
		this.cpu.reset();
		this.nmist = NmiStatus.ON_V_BLANK;
		this.consol = 8;
		this.covox[0] = (byte) 128;
		this.covox[1] = (byte) 128;
		this.covox[2] = (byte) 128;
		this.covox[3] = (byte) 128;
		this.pokeys.initialize(this.moduleInfo.isNtsc(), this.moduleInfo.getChannels() > 1, this.currentSampleRate);
		int player = this.moduleInfo.player;
		int music = this.moduleInfo.getMusicAddress();
		switch (this.moduleInfo.type) {
		case SAP_B:
			do6502Init(this.moduleInfo.getInitAddress(), this.currentSong, 0, 0);
			break;
		case SAP_C:
		case CMC:
		case CM3:
		case CMR:
		case CMS:
			do6502Init(player + 3, 112, music, music >> 8);
			do6502Init(player + 3, 0, this.currentSong, 0);
			break;
		case SAP_D:
		case SAP_S:
			this.cpu.pc = this.moduleInfo.getInitAddress();
			this.cpu.a = this.currentSong;
			this.cpu.x = 0;
			this.cpu.y = 0;
			this.cpu.s = 255;
			break;
		case DLT:
			do6502Init(player + 256, 0, 0, this.moduleInfo.songPos[this.currentSong] & 0xff);
			break;
		case MPT:
		case MD1:
		case MD2:
			do6502Init(player, 0, music >> 8, music);
			if (this.moduleInfo.type != ASAPModuleType.MPT)
				do6502Init(player, 3, this.mptSamplesPage - 1, 224);
			do6502Init(player, 2, this.moduleInfo.songPos[this.currentSong] & 0xff, 0);
			if (this.moduleInfo.type == ASAPModuleType.MPT)
				break;
			this.cpu.pc = player;
			this.cpu.a = this.moduleInfo.type == ASAPModuleType.MD1 ? 5 : 6;
			this.cpu.x = this.mptSamples15kHz ? 1 : 0;
			this.cpu.s = 255;
			break;
		case RMT:
			do6502Init(player, this.moduleInfo.songPos[this.currentSong] & 0xff, music, music >> 8);
			break;
		case TMC:
		case TM2:
			do6502Init(player, 112, music >> 8, music);
			do6502Init(player, 0, this.currentSong, 0);
			this.tmcPerFrameCounter = 1;
			break;
		case FC:
			do6502Init(player, this.currentSong, 0, 0);
			break;
		case D15:
			this.mptSamplesCurrentAddress = (this.cpu.memory[this.moduleInfo.getMusicAddress() + this.currentSong] & 0xff) << 8;
			this.mptSamplesSecondNibble = false;
			this.cpu.memory[53760] = (byte) 210;
			this.cpu.pc = 53760;
			break;
		}
		this.pokeys.basePokey.endSongInit();
		this.pokeys.extraPokey.endSongInit();
		this.nextPlayerCycle = 0;
	}

	/**
	 * Prepares playback of the specified song of the loaded module.
	 * @param song Zero-based song index.
	 * @param duration Playback time in milliseconds, -1 means infinity.
	 */
	public final void playSong(int song, int duration) throws ASAPArgumentException, ASAPFormatException
	{
		if (song < 0 || song >= this.moduleInfo.getSongs())
			throw new ASAPArgumentException("Song number out of range");
		this.currentSong = song;
		this.currentDuration = duration;
		restartSong();
	}

	/**
	 * Mutes the selected POKEY channels.
	 * @param mask An 8-bit mask which selects POKEY channels to be muted.
	 */
	public final void mutePokeyChannels(int mask)
	{
		this.pokeys.basePokey.mute(mask);
		this.pokeys.extraPokey.mute(mask >> 4);
	}

	/**
	 * Returns current playback position in blocks.
	 * A block is one sample or a pair of samples for stereo.
	 */
	public final int getBlocksPlayed()
	{
		return this.blocksPlayed;
	}

	/**
	 * Returns current playback position in milliseconds.
	 */
	public final int getPosition()
	{
		return this.blocksPlayed * 10 / (this.currentSampleRate / 100);
	}

	private int millisecondsToBlocks(int milliseconds)
	{
		long ms = milliseconds;
		return (int) (ms * this.currentSampleRate / 1000);
	}

	/**
	 * Changes the playback position.
	 * @param block The requested absolute position in samples (always 44100 per second, even in stereo).
	 */
	public final void seekSample(int block) throws ASAPFormatException
	{
		if (block < this.blocksPlayed)
			restartSong();
		while (this.blocksPlayed + this.pokeys.readySamplesEnd < block) {
			this.blocksPlayed += this.pokeys.readySamplesEnd;
			doFrame();
		}
		this.pokeys.readySamplesStart = block - this.blocksPlayed;
		this.blocksPlayed = block;
	}

	/**
	 * Changes the playback position.
	 * @param position The requested absolute position in milliseconds.
	 */
	public final void seek(int position) throws ASAPFormatException
	{
		seekSample(millisecondsToBlocks(position));
	}

	private static void putLittleEndian(byte[] buffer, int offset, int value)
	{
		buffer[offset] = (byte) value;
		buffer[offset + 1] = (byte) (value >> 8);
		buffer[offset + 2] = (byte) (value >> 16);
		buffer[offset + 3] = (byte) (value >> 24);
	}

	private static int fourCC(String s)
	{
		return (s.charAt(0) | s.charAt(1) << 8 | s.charAt(2) << 16 | s.charAt(3) << 24) & 2147483647;
	}

	private static void putLittleEndians(byte[] buffer, int offset, int value1, int value2)
	{
		putLittleEndian(buffer, offset, value1);
		putLittleEndian(buffer, offset + 4, value2);
	}

	private static int putWavMetadata(byte[] buffer, int offset, int fourCC, String value)
	{
		int len = value.length();
		if (len > 0) {
			putLittleEndians(buffer, offset, fourCC, (len | 1) + 1);
			offset += 8;
			for (int i = 0; i < len; i++)
				buffer[offset++] = (byte) value.charAt(i);
			buffer[offset++] = 0;
			if ((len & 1) == 0)
				buffer[offset++] = 0;
		}
		return offset;
	}

	/**
	 * Fills leading bytes of the specified buffer with WAV file header.
	 * Returns the number of changed bytes.
	 * @param buffer The destination buffer.
	 * @param format Format of samples.
	 * @param metadata Include metadata (title, author, date).
	 */
	public final int getWavHeader(byte[] buffer, ASAPSampleFormat format, boolean metadata)
	{
		int use16bit = format != ASAPSampleFormat.U8 ? 1 : 0;
		int blockSize = this.moduleInfo.getChannels() << use16bit;
		int bytesPerSecond = this.currentSampleRate * blockSize;
		int totalBlocks = millisecondsToBlocks(this.currentDuration);
		int nBytes = (totalBlocks - this.blocksPlayed) * blockSize;
		putLittleEndian(buffer, 8, 1163280727);
		putLittleEndians(buffer, 12, 544501094, 16);
		buffer[20] = 1;
		buffer[21] = 0;
		buffer[22] = (byte) this.moduleInfo.getChannels();
		buffer[23] = 0;
		putLittleEndians(buffer, 24, this.currentSampleRate, bytesPerSecond);
		buffer[32] = (byte) blockSize;
		buffer[33] = 0;
		buffer[34] = (byte) (8 << use16bit);
		buffer[35] = 0;
		int i = 36;
		if (metadata) {
			int year = this.moduleInfo.getYear();
			if (this.moduleInfo.getTitle().length() > 0 || this.moduleInfo.getAuthor().length() > 0 || year > 0) {
				putLittleEndian(buffer, 44, 1330007625);
				i = putWavMetadata(buffer, 48, 1296125513, this.moduleInfo.getTitle());
				i = putWavMetadata(buffer, i, 1414676809, this.moduleInfo.getAuthor());
				if (year > 0) {
					putLittleEndians(buffer, i, 1146241865, 6);
					for (int j = 3; j >= 0; j--) {
						buffer[i + 8 + j] = (byte) ('0' + year % 10);
						year /= 10;
					}
					buffer[i + 12] = 0;
					buffer[i + 13] = 0;
					i += 14;
				}
				putLittleEndians(buffer, 36, 1414744396, i - 44);
			}
		}
		putLittleEndians(buffer, 0, 1179011410, i + nBytes);
		putLittleEndians(buffer, i, 1635017060, nBytes);
		return i + 8;
	}

	private int generateAt(byte[] buffer, int bufferOffset, int bufferLen, ASAPSampleFormat format)
	{
		if (this.silenceCycles > 0 && this.silenceCyclesCounter <= 0)
			return 0;
		int blockShift = this.moduleInfo.getChannels() - (format == ASAPSampleFormat.U8 ? 1 : 0);
		int bufferBlocks = bufferLen >> blockShift;
		if (this.currentDuration > 0) {
			int remainingBlocks = millisecondsToBlocks(this.currentDuration) - this.blocksPlayed;
			if (bufferBlocks > remainingBlocks)
				bufferBlocks = remainingBlocks;
		}
		int block = 0;
		for (;;) {
			int blocks = this.pokeys.generate(buffer, bufferOffset + (block << blockShift), bufferBlocks - block, format);
			this.blocksPlayed += blocks;
			block += blocks;
			if (block >= bufferBlocks)
				break;
			int cycles = doFrame();
			if (this.silenceCycles > 0) {
				if (this.pokeys.isSilent() && !this.gtiaOrCovoxPlayedThisFrame) {
					this.silenceCyclesCounter -= cycles;
					if (this.silenceCyclesCounter <= 0)
						break;
				}
				else
					this.silenceCyclesCounter = this.silenceCycles;
			}
		}
		return block << blockShift;
	}

	/**
	 * Fills the specified buffer with generated samples.
	 * @param buffer The destination buffer.
	 * @param bufferLen Number of bytes to fill.
	 * @param format Format of samples.
	 */
	public final int generate(byte[] buffer, int bufferLen, ASAPSampleFormat format)
	{
		return generateAt(buffer, 0, bufferLen, format);
	}

	/**
	 * Returns POKEY channel volume - an integer between 0 and 15.
	 * @param channel POKEY channel number (from 0 to 7).
	 */
	public final int getPokeyChannelVolume(int channel)
	{
		Pokey pokey = (channel & 4) == 0 ? this.pokeys.basePokey : this.pokeys.extraPokey;
		return pokey.channels[channel & 3].audc & 15;
	}

	/**
	 * RMT extension (not part of upstream ASAP - see this file's header
	 * comment). Advances the emulation by exactly one video frame (one
	 * player-routine call plus its POKEY cycles), producing no audio.
	 * Exposes the otherwise-private {@code doFrame()} for frame-accurate
	 * POKEY register capture (see {@link #getPokeyRegisterShadow}) - RMT's
	 * SAP-R-style per-frame export needs exact frame boundaries, which the
	 * public {@link #generate} API cannot guarantee (its own frame stepping
	 * is an internal buffering detail, not aligned 1:1 with the bytes
	 * requested). Call {@link #playSong} first.
	 */
	public final void stepFrame()
	{
		doFrame();
	}

	/**
	 * RMT extension (not part of upstream ASAP - see this file's header
	 * comment). Returns the raw byte most recently written to a POKEY audio
	 * register (AUDF1..AUDF4/AUDC1..AUDC4/AUDCTL) - the write-only "shadow"
	 * value RMT's own SAP-R-style per-frame register dump needs (matching
	 * {@code CPokeyStream::Record()}'s exact byte layout in the original
	 * C++), not a simulated hardware read-back (real POKEY audio registers
	 * cannot be read back at all - unlike {@link #getPokeyChannelVolume},
	 * which already exposes a derived, readable value).
	 * @param chip 0 for the base POKEY, 1 for the second POKEY (stereo only).
	 * @param offset 0-8: AUDF1, AUDC1, AUDF2, AUDC2, AUDF3, AUDC3, AUDF4,
	 * AUDC4, AUDCTL. 15: SKCTL (not part of the 9-byte SAP-R frame layout
	 * itself, but needed by callers replicating {@code CPokeyStream::Record()}'s
	 * own "is Two-Tone mode active" check against $D20F/$D21F).
	 */
	public final int getPokeyRegisterShadow(int chip, int offset)
	{
		Pokey pokey = chip == 0 ? this.pokeys.basePokey : this.pokeys.extraPokey;
		switch (offset) {
		case 0:
			return pokey.channels[0].audf & 0xff;
		case 1:
			return pokey.channels[0].audc & 0xff;
		case 2:
			return pokey.channels[1].audf & 0xff;
		case 3:
			return pokey.channels[1].audc & 0xff;
		case 4:
			return pokey.channels[2].audf & 0xff;
		case 5:
			return pokey.channels[2].audc & 0xff;
		case 6:
			return pokey.channels[3].audf & 0xff;
		case 7:
			return pokey.channels[3].audc & 0xff;
		case 8:
			return pokey.audctl & 0xff;
		case 15:
			return pokey.skctl & 0xff;
		default:
			throw new IllegalArgumentException("offset");
		}
	}
}
