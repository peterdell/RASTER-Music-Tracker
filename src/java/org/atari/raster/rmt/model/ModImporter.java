package org.atari.raster.rmt.model;

/**
 * Ported from {@code CSong::ImportMODParseHeader}/{@code ImportMODApply}
 * (src/cpp/IO_ImporterCore.cpp) - Batch B of {@code plans/JAVA_IMPORTER_PLAN.md}.
 * {@code CSong::ImportMOD()} itself (src/cpp/IO_Importer.cpp) stays
 * unported - a real, still-dialog-showing thin wrapper, matching this
 * project's established "real dialog stays deferred" pattern. Its three
 * differently-worded original guard messages (reconstructed from
 * {@link ParseHeaderResult#errorCode()} by that wrapper) aren't reproduced
 * here either, for the same reason.
 *
 * <p>A new free-standing class (not a {@link Song} method), matching
 * {@link RmtExporter}/{@link AsmFileExporter}/{@link TmcImporter}'s
 * precedent.
 *
 * <p><b>C++'s "continued stream access" wrinkle disappears in Java</b>:
 * {@code TImportMODHeader.mem} only held the header+pattern prefix, so
 * {@code ImportMODApply()} needed the original {@code std::istream&} too,
 * to {@code seekg}/read the sample audio data living further into the
 * file. Since this port loads the *whole* file into one {@code byte[]}
 * upfront (matching every other binary format's established idiom),
 * {@link ParseHeaderResult} just carries that same array, and
 * {@link #apply} indexes into it directly at the computed sample offset -
 * no stream, no seek, no separate "read failed" bookkeeping beyond a
 * plain bounds check.
 *
 * <p><b>The one genuinely tricky part: {@code ImportMODApply()}'s
 * {@code goto}-driven tone-portamento state machine</b>, restructured as
 * follows (see {@link #tonePortamento}):
 * <ul>
 * <li>The {@code TonePortamento:} label's body (checking whether a
 * continuing portamento has crossed into a new note) becomes the private
 * helper {@link #tonePortamento}, called once directly (period invalid -
 * an "empty" cell) and once via the {@code goto TonePortamento;} inside
 * the tone-portamento-effect branch (when the pitch class hasn't changed
 * yet) - safe to extract since C++'s own control flow never re-enters it
 * more than once per line/channel.
 * <li>The {@code NoteByPortamento:} label (writing the resolved note into
 * the destination track) becomes a shared {@code if (noteWritten) {...}}
 * block after the two call sites converge.
 * <li>The {@code Effect3:} label (shared portamento-speed-toward-target
 * math for effects 1/2/3/5) becomes a single {@code if} covering all four
 * effect codes, with only the {@code pspeed}/target-period setup differing
 * per effect - no helper method needed there, since it's a linear
 * "compute inputs, then run the shared tail once" shape, not a
 * multiple-entry state machine like the portamento case above.
 * <li>The {@code OutOfTracks:}/{@code OutOfSongLines:} labels (adjacent,
 * both meaning "stop processing this pass's song/pattern loop entirely")
 * become a single labelled {@code break songLoop;}, matching this port's
 * established {@code playBeat}-style loop-labelling idiom.
 * </ul>
 *
 * <p><b>A discovered, likely-pre-existing dead statement, preserved as-is</b>:
 * the loop-instrument branch of the sample/envelope-generation section
 * computes {@code lopend} (where the sample's loop should end, scaled into
 * envelope columns) but never assigns it anywhere - C++'s body is a bare
 * {@code rmti->parameters[PAR_ENV_LENGTH];} expression statement (reads and
 * discards). This looks like a forgotten assignment, but the *intended*
 * target isn't obvious enough to guess at safely, and it's outside every
 * existing test's reach (the test's sample has no loop) - flagged to the
 * user rather than silently "fixed"; this port computes {@code lopEnd} and
 * likewise leaves it unused, faithfully matching the observed behavior.
 *
 * <p><b>{@code g_Instruments.Update(i)}</b> is called where C++ calls it,
 * for the half of it that exists ({@link Instruments#update}: the
 * display-hint flags; the Atari-memory write is still deferred).
 */
public final class ModImporter {

	private ModImporter() {
	}

	private static final int TABLENOTES = 73;
	private static final int[] PERTABLE = { //
			0x06B0, 0x0650, 0x05F4, 0x05A0, 0x054C, 0x0500, 0x04B8, 0x0474, 0x0434, 0x03F8, 0x03C0, 0x038B, // C3-B3
			0x0358, 0x0328, 0x02FA, 0x02D0, 0x02A6, 0x0280, 0x025C, 0x023A, 0x021A, 0x01FC, 0x01E0, 0x01C5, // C4-B4
			0x01AC, 0x0194, 0x017D, 0x0168, 0x0153, 0x0140, 0x012E, 0x011D, 0x010D, 0x00FE, 0x00F0, 0x00E2, // C5-B5
			0x00D6, 0x00CA, 0x00BE, 0x00B4, 0x00AA, 0x00A0, 0x0097, 0x008F, 0x0087, 0x007F, 0x0078, 0x0071, // C6-B6
			0x006B, 0x0065, 0x005F, 0x005A, 0x0055, 0x0050, 0x004B, 0x0047, 0x0043, 0x003F, 0x003C, 0x0038, // C7-B7
			0x0035, 0x0032, 0x002F, 0x002D, 0x002A, 0x0028, 0x0025, 0x0023, 0x0021, 0x001F, 0x001E, 0x001C, // C8-B8
			0x001B }; // C9

	/**
	 * {@code errorCode}: 0 = ok; 1 = the file is too short to contain a full
	 * 1084-byte module header; 2 = its header doesn't carry a recognized
	 * ProTracker identification ("M.K." or NCHN, 4-8 channels); 3 = the file
	 * is shorter than its own header claims it should be. {@code data} is
	 * the whole file. The rest mirror C++'s {@code TImportMODHeader} fields.
	 */
	public record ParseHeaderResult(int errorCode, byte[] data, int chnls, int modSamples, int song, int patStart, int patternSize, int songLen, int restartPos, int maxPat) {
		public boolean ok() {
			return errorCode == 0;
		}
	}

	/** Mirrors C++'s {@code TImportMODResult} output-parameter struct, plus {@code tracks4_8} - see {@link TmcImporter.ApplyResult}'s javadoc for why. */
	public record ApplyResult(int tracks4_8, int destNum, int nonEmptySamples, int songLines, int optiTracks, int optiBeats, int clearedTracks, int truncatedTracks, int truncatedBeats) {
	}

	/**
	 * The unconditional real work {@code ImportMOD()}'s options dialog needs
	 * done first (its own text needs the parsed channel/sample count):
	 * clears the current song and identifies the module's format
	 * (channel count, 15- vs 31-sample, song/pattern layout). Returns a
	 * non-zero {@link ParseHeaderResult#errorCode()} on a corrupted/
	 * unrecognized/truncated file (matching C++'s three guards).
	 */
	public static ParseHeaderResult parseHeader(byte[] data, Song song, Tracks tracks, Undo undo) {
		tracks.setMaxTrackLength(64);
		song.clearSong(8, undo);

		if (data.length < 1084) {
			return new ParseHeaderResult(1, data, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		int chnls = 0;
		int songOffset = 950; // where the song starts at the 31 sample module
		int patStart = 1084; // the beginning of the pattern at the 31 sample module
		int modSamples = 31;
		if (matches(data, 1080, "M.K.")) {
			chnls = 4; // M.K.
		} else if (matches(data, 1081, "CHN")) {
			chnls = ub(data, 1080) - '0'; // xCHN
		} else {
			for (int i = 0; i < 4; i++) {
				int a = ub(data, 1080 + i);
				if (a < 32 || a > 90) { // it's outside " " and "Z" (i.e. not a letter or a space)
					// => 15 samples MOD
					chnls = 4;
					modSamples = 15;
					songOffset = 470;
					patStart = 600;
					break;
				}
			}
		}

		if (chnls < 4 || chnls > 8) {
			return new ParseHeaderResult(2, data, chnls, modSamples, songOffset, patStart, 0, 0, 0, 0);
		}
		int patternSize = chnls * 256;

		int songLen = ub(data, songOffset);
		int restartPos = ub(data, songOffset + 1);
		if (restartPos >= songLen) {
			restartPos = 0;
		}

		int maxPat = 0;
		if (songLen > Song.SONGLEN - 1) {
			songLen = Song.SONGLEN - 1;
		}
		for (int i = 0; i < songLen; i++) {
			int patNum = ub(data, songOffset + 2 + i);
			if (patNum > maxPat) {
				maxPat = patNum;
			}
		}
		int memLen = songOffset + 130 + patternSize * (maxPat + 1); // 130 = 1 length + 1 repeat + 128 song
		if (songOffset >= 950) {
			memLen += 4; // 4 identification letters (e.g. "M.K.")
		}

		if (data.length < memLen) {
			return new ParseHeaderResult(3, data, chnls, modSamples, songOffset, patStart, patternSize, songLen, restartPos, maxPat);
		}

		return new ParseHeaderResult(0, data, chnls, modSamples, songOffset, patStart, patternSize, songLen, restartPos, maxPat);
	}

	/**
	 * The rest of the real conversion, given the options dialog's flags and
	 * track-layout choice: decodes every used sample as an instrument, then
	 * every pattern's notes/effects into tracks/song lines, then derives a
	 * simple volume-envelope approximation for each sample's waveform.
	 */
	public static ApplyResult apply(ParseHeaderResult header, int rmttype, int[] trackOrder, boolean xShiftDownOctave, boolean xPortamento, boolean xFullVolumeRange, boolean xVolumeIncrease,
			boolean xDecreaseInstrument, boolean xOptimizeLoops, boolean xTruncateUnusedParts, Song song, Tracks tracks, Instruments instruments, Undo undo) {
		byte[] mem = header.data();
		int chnls = header.chnls();
		int modSamples = header.modSamples();
		int songOffset = header.song();
		int patStart = header.patStart();
		int patternSize = header.patternSize();
		int songLen = header.songLen();
		int restartPos = header.restartPos();
		int maxPat = header.maxPat();
		int moduleLength = mem.length;

		int tracks4_8 = song.setTracks(rmttype); // produce RMT4 or RMT8

		// song name - matches C++ exactly: only characters up to the first
		// zero byte (max 20) are written; the rest of the song name is left
		// as whatever clearSong() already set it to ("Noname song", padded).
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		for (int j = 0; j < 20; j++) {
			int a = ub(mem, j);
			if (a == 0) {
				break;
			}
			info.songName[j] = (char) a;
		}
		info.mainSpeed = 6; // default speed
		info.speed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		// instruments 1-31
		InstrumentMark[] imark = new InstrumentMark[32];
		for (int i = 0; i < 32; i++) {
			imark[i] = new InstrumentMark();
		}
		for (int i = 1; i <= modSamples; i++) {
			int sdata = 20 + (i - 1) * 30; // sample header data offset
			Instrument ti = instruments.getInstrument(i);
			int j = 0;
			for (; j < 22; j++) { // 0-21 name
				int a = ub(mem, sdata + j);
				ti.name[j] = (a >= 32 && a <= 126) ? (char) a : ' ';
			}
			for (; j < Instrument.INSTRUMENT_NAME_MAX_LEN; j++) {
				ti.name[j] = ' '; // deletes the rest of the instrument name
			}
			int sampLen = (ub(mem, sdata + 23) | (ub(mem, sdata + 22) << 8)) * 2;

			int volume = ub(mem, sdata + 25);
			if (volume > 0x3f) {
				volume = 0x3f; // 00-3f
			}

			int repPoint = (ub(mem, sdata + 27) | (ub(mem, sdata + 26) << 8)) * 2;
			int repLen = (ub(mem, sdata + 29) | (ub(mem, sdata + 28) << 8)) * 2;

			imark[i].volume = volume;
			imark[i].minNote = Notes.NOTESNUM - 1;
			imark[i].maxNote = 0;
			imark[i].used = 0;
			imark[i].sampleLen = sampLen;
			imark[i].repPoint = repPoint;
			imark[i].repLen = repLen;
			imark[i].trackVolumeIncrease = 1;
			imark[i].trackVolumeMax = 0;
		}
		imark[0].trackVolumeIncrease = 1; // due to volume slide, if performed without specifying a sample (sample number 0)

		// period-to-note table
		int[] perToNote = new int[4096];
		int n12;
		int lastp = 4095;
		for (int i = 0; i < TABLENOTES; i++) {
			int n1 = PERTABLE[i];
			if (i < TABLENOTES - 1) {
				int n2 = PERTABLE[i + 1];
				n12 = (int) (((n1 + n2) / 2.0f) + 0.5);
			} else {
				n12 = 0;
			}
			int note = i;
			while (note >= Notes.NOTESNUM) {
				note -= 12;
			}
			for (int j = lastp; j >= n12; j--) {
				perToNote[j] = note;
			}
			lastp = n12 - 1;
		}

		// BEGINNING OF PROCESSING THE ENTIRE SONG AND PATTERN
		int dsline = 0; // destination song line
		int destNum = 0; // destination track num

		int nofpass = xFullVolumeRange ? 1 : 0;
		for (int pass = 0; pass <= nofpass; pass++) { // ---TRANSITION 0/1---
			destNum = 0;
			int[] tnot = new int[8]; // last edge note (used for portamento)
			java.util.Arrays.fill(tnot, -1);
			boolean[] tporon = new boolean[8]; // portamento yes/no
			int[] tporperiod = new int[8]; // target period for portamento
			int[] tporspeed = new int[8]; // portamento speed
			int[] tper = new int[8]; // current period
			int[] tvol = new int[8]; // volume of individual tracks (used for volume slide)
			int[] tvolslidedebt = new int[8]; // debt at volume slide
			int[] tins = new int[8]; // individual track instruments (used when the sample is == 0)
			int ticks = 6; // m_mainSpeed, default 6 (songspeed, for volume slide)
			int beats = 125; // default beats/min speed

			int lastRowTicks = ticks;
			int lastRowBeats = beats;

			dsline = 0;
			int thisPatternFromRow = 0; // initial pattern line (due to Dxx effect)
			int thisPatternFromColumn = -1;
			int nextPatternFromRow = 0;
			int nextPatternFromColumn = -1;

			songLoop: for (int i = 0; i < songLen; i++) {
				int patnum = ub(mem, songOffset + 2 + i);
				int pdata = patStart + patnum * patternSize; // beginning of the pattern

				int songjump = -1;
				thisPatternFromRow = nextPatternFromRow;
				thisPatternFromColumn = nextPatternFromColumn;
				nextPatternFromRow = 0;
				nextPatternFromColumn = -1;

				// pre-calculated tickrow[0..63] and speedrow[0..63]
				int[] tickrow = new int[64];
				int[] speedrow = new int[64];
				boolean lrow = false;
				ticks = lastRowTicks;
				beats = lastRowBeats;
				lastRowTicks = -1;
				lastRowBeats = -1;
				for (int m = thisPatternFromRow; m < 64; m++) {
					for (int n = 0; n < chnls; n++) {
						int bdata = pdata + m * chnls * 4 + n * 4;
						int effect = ub(mem, bdata + 2) & 0x0f;
						int param = ub(mem, bdata + 3);
						if (effect == 0x0f) { // speed
							if (param <= 0x20) {
								ticks = (param > 0) ? param : 1; // speed 0 is not possible
							} else {
								beats = param;
							}
						} else if (effect == 0x0b || effect == 0x0d) { // pattern break or song jump
							lrow = true; // you have to write it down and even this whole line 0..chnls
						}
					}
					int ss = (int) (((125.0 * ticks)) / beats + 0.5);
					if (ss < 1) {
						ss = 1;
					} else if (ss > 255) {
						ss = 255;
					}

					tickrow[m] = ticks;
					speedrow[m] = ss;

					if ((lrow || m == 63) && lastRowTicks < 0 && lastRowBeats < 0) {
						lastRowTicks = ticks;
						lastRowBeats = beats;
					}
				}

				// tracks in the pattern
				for (int ch = 0; ch < chnls; ch++) {
					int tdata = pdata + ch * 4; // the beginning of the track data source
					Track tr = tracks.getTrack(destNum); // target track
					tracks.clearTrack(destNum); // clean it first
					int dline = 0;
					int sline;
					for (sline = thisPatternFromRow; sline < 64; sline++, dline++) {
						int bdata = tdata + sline * chnls * 4; // 4-byte block

						ticks = tickrow[sline]; // common calculated value of ticks for the whole line

						int period = ub(mem, bdata + 1) | ((ub(mem, bdata) & 0x0f) << 8); // 12 bit
						int sample = (((ub(mem, bdata + 2) & 0xf0) >> 4) | (ub(mem, bdata) & 0x10)) & modSamples; // 0-31 / 0-15, not 0-255!
						int effect = ub(mem, bdata + 2) & 0x0f;
						int param = ub(mem, bdata + 3);
						int sampleorig = sample; // original as in the track

						if (sample == 0) {
							sample = tins[ch]; // sample number 0 means the same as last used
						} else {
							tins[ch] = sample; // saves the last used sample in this "column"
						}

						int note;
						int vol = -1;
						boolean noteWritten;

						if (period < 1 || period >= 4096) {
							// empty place (there is no note)
							TonePortamentoResult r = tonePortamento(xPortamento, tporon, ch, tper, perToNote, tnot, tvol);
							note = r.note();
							vol = r.vol();
							noteWritten = r.written();
						} else {
							// there is a note
							note = perToNote[period];
							noteWritten = false;
							if (xPortamento && (effect == 0x03 || effect == 0x05)) {
								// tone portamento 3xx or continue tone portamento 5
								tporperiod[ch] = period; // target portamento period
								tporon[ch] = true;
								int aper = tper[ch];
								int hfper;
								int pspeed;
								if (effect == 0x03) {
									pspeed = (param != 0) ? (tporspeed[ch] = param) : tporspeed[ch]; // TONE portamento speed only for parameter 3xx
								} else { // effect == 0x05
									pspeed = tporspeed[ch]; // effect 0x05 is continued portamento
								}

								if (aper < period) {
									hfper = aper + pspeed * (ticks - 1) / 2;
									if (hfper > period) {
										hfper = period;
									}
								} else {
									hfper = aper - pspeed * (ticks - 1) / 2;
									if (hfper < period) {
										hfper = period;
									}
								}
								if (perToNote[aper] != perToNote[hfper]) {
									note = perToNote[hfper];
									vol = tvol[ch];
									tporon[ch] = false;
									noteWritten = true;
								} else {
									// goto TonePortamento: solve it as if it were an empty slot
									TonePortamentoResult r = tonePortamento(xPortamento, tporon, ch, tper, perToNote, tnot, tvol);
									note = r.note();
									vol = r.vol();
									noteWritten = r.written();
								}
							} else {
								tper[ch] = period; // current period
								tporon[ch] = false; // no portamento
								vol = 0x40; // the default is full volume (if it's then overwritten)
								tvolslidedebt[ch] = 0; // debt volume slide = 0
								noteWritten = true;
							}
						}

						if (noteWritten) {
							tnot[ch] = note; // last note
							tr.note[dline] = note;
							tr.instr[dline] = sample;
							int avol = atariVolume(vol); // conversion to atari volume
							tr.volume[dline] = avol; // or it will be overwritten by the Cxx parameter
							imark[sample].used |= (1 << trackOrder[ch]); // sample is used on channel "ch"
							if (note > imark[sample].maxNote) {
								imark[sample].maxNote = note;
							}
							if (note < imark[sample].minNote) {
								imark[sample].minNote = note;
							}
						}

						// effect: PORTAMENTO
						int pspeed = 0;
						if (effect == 0x01 || effect == 0x02 || effect == 0x03 || effect == 0x05) {
							if (effect == 0x01) { // 1xx Portamento Up
								tporperiod[ch] = PERTABLE[Notes.NOTESNUM - 1]; // the highest note RMT can play
								pspeed = param;
							} else if (effect == 0x02) { // 2xx Portamento Down
								tporperiod[ch] = PERTABLE[0]; // the lowest note RMT can play
								pspeed = param;
							} else if (effect == 0x05) { // 5xx continue toneportamento (+ simultaneous volume slide)
								pspeed = tporspeed[ch]; // takes over previous speed
							} else { // effect == 0x03: 3xx TonePortamento
								pspeed = (param != 0) ? (tporspeed[ch] = param) : tporspeed[ch];
							}

							int cpor = tporperiod[ch]; // target portamento
							int aper = tper[ch]; // current period
							if (aper > cpor) {
								// portamento towards smaller values, i.e. up to higher tones
								aper -= pspeed * (ticks - 1);
								if (aper < cpor) {
									aper = cpor;
								}
							} else if (aper < cpor) {
								// portamento towards higher values, i.e. down to lower tones
								aper += pspeed * (ticks - 1);
								if (aper > cpor) {
									aper = cpor;
								}
							}

							tper[ch] = aper;
							tporon[ch] = true; // in the next step, the portamento will be resolved
						}

						// effects: VOLUME
						if (effect == 0x0c) { // Cxx setvolume xx=$00-$40
							if (pass == 1) {
								param = (int) (param * imark[sample].trackVolumeIncrease + 0.5);
							}
							if (param > 0x40) {
								param = 0x40; // maximum volume
							}
							tvol[ch] = param;
							tvolslidedebt[ch] = 0; // no debt
							if (param > imark[sample].trackVolumeMax) {
								imark[sample].trackVolumeMax = param;
							}

							tr.volume[dline] = atariVolume(param);
						} else { // it is not Cxx => undefined volume
							if (note >= 0 || sampleorig > 0) {
								// note without volume or sample without note => default maximum volume
								int v = (vol >= 0) ? vol : 0x40; // takes either "vol" from the portamento, or the default full
								tvol[ch] = v;
								imark[sample].trackVolumeMax = v;
							}
						}

						// effect NEXT
						if (effect == 0x0a // Axx volumeslide xx=0x decrease, xx=x0 increase
								|| effect == 0x05 // 5xx continue toneportamento (already handled above) + volumeslide xx
								|| effect == 0x06 // 6xx continue vibrato + volumeslide xx => only volumeslide xx
						) {
							int v = tvol[ch];
							double slidedivide = (note >= 0 || sampleorig > 0) ? 2 : 1; // first half is half
							if ((param & 0xf0) == 0) {
								int voldec = (int) ((param & 0x0f) * (ticks - 1) * imark[sample].trackVolumeIncrease / slidedivide + 0.5);
								v -= voldec; // decrease
								if (slidedivide == 2) {
									tvolslidedebt[ch] = -voldec; // debt volume slide
								}
							} else {
								int volinc = (int) (((param & 0xf0) >> 4) * (ticks - 1) * imark[sample].trackVolumeIncrease / slidedivide + 0.5);
								v += volinc; // increase
								if (slidedivide == 2) {
									tvolslidedebt[ch] = volinc; // debt volume slide
								}
							}

							if (v < 0) {
								v = 0;
							} else if (v > 0x40) {
								v = 0x40;
							}

							tvol[ch] = v;
							if (v > imark[sample].trackVolumeMax) {
								imark[sample].trackVolumeMax = v;
							}

							tr.volume[dline] = atariVolume(v);
						} else if (effect == 0x0f) { // Fxx setspeed/tempo
							tr.speed[dline] = speedrow[sline]; // use the common pre-calculated value for the whole row
						} else if (effect == 0x0d) { // Dxx pattern break
							// end the track on the first occurrence of Dxx from above, continue to position xx
							if (tr.len == 64) {
								tr.len = dline + 1;
								int nxp = (param / 16) * 10 + (param % 16); // it's there in the 10th system
								if (nxp >= 0 && nxp < 64 && nextPatternFromColumn == -1) {
									nextPatternFromRow = nxp;
									nextPatternFromColumn = ch;
								}
							}
						} else if (effect == 0x0b) { // Bxx song jump
							if (tr.len == 64) {
								tr.len = dline + 1; // track break
							}
							if (songjump < 0) {
								songjump = param;
							}
						}

						// the debt will increment if there is any and there is a free slot
						if (tr.volume[dline] < 0) { // the volume is not specified
							int mvol = tvol[ch];
							mvol += tvolslidedebt[ch]; // adjust volume by debt
							if (mvol < 0) {
								mvol = 0;
							} else if (mvol > 0x40) {
								mvol = 0x40;
							}
							int avol = atariVolume(mvol);
							if (avol != atariVolume(tvol[ch])) {
								tr.volume[dline] = avol; // if it comes out differently than it was, add it
							}
							tvol[ch] = mvol;
							tvolslidedebt[ch] = 0; // debt volume slide resolved
						}
					} // line 0-64

					// if the shift (effect Dxx) has started, then it must shorten the length of the respective track
					if (thisPatternFromColumn == ch && tr.len == 64 && dline < 64) {
						tr.len = dline;
					}

					int cit = -1; // track number for the song
					if (!tracks.isEmptyTrack(destNum)) {
						// Removes excess volume 0
						tracks.trackOptimizeVol0(destNum);

						// see if such a track already exists
						for (int k = 0; k < destNum; k++) {
							if (tracks.compareTracks(k, destNum)) {
								cit = k; // found one
								break;
							}
						}
						if (cit < 0) {
							cit = destNum; // was not found
							destNum++; // prepare for the next
						}
					}

					song.getSong()[dsline][trackOrder[ch]] = cit;
					if (destNum >= Tracks.TRACKSNUM) {
						// SendWarningMessage("Warning", "Out of RMT tracks. Tracks converting terminated.") omitted (guard-only)
						break songLoop; // the tracks have reached the end
					}
				} // tracks in the pattern

				dsline++;
				if (dsline >= Song.SONGLEN) {
					// SendWarningMessage("Warning", "Out of song lines. Song converting terminated.") omitted (guard-only)
					break songLoop; // ran out of songlines
				}

				if (songjump >= 0 && songjump < Song.SONGLEN) {
					song.getSongGo()[dsline] = songjump;
					dsline++;
					if (dsline >= Song.SONGLEN) {
						break songLoop;
					}
				}
			}
			// OutOfTracks:/OutOfSongLines: - ALL PATTERNS OF SONG ARE DONE

			// prepare a track volume increase for each sample so the second
			// time it arrives, the volume in the tracks gets increased
			if (pass == 0) {
				for (int i = 1; i <= modSamples; i++) {
					if (imark[i].trackVolumeMax > 0) {
						imark[i].trackVolumeIncrease = 0x40 / (double) imark[i].trackVolumeMax;
					}
				}
			}
			// ---END OF TRANSITION 0/1---
		}

		// corrects the jumps in the song
		for (int i = 0; i < dsline; i++) {
			int go = song.getSongGo()[i];
			if (go < 0) {
				continue;
			}
			int k;
			for (k = 0; k <= go && k < Song.SONGLEN; k++) { // for each goto found before this jump, moves go by 1 step
				if (song.getSongGo()[k] >= 0) {
					go++;
				}
			}
			song.getSongGo()[i] = go; // writes the shifted jump
		}
		if (dsline >= 1 && song.getSongGo()[dsline - 1] < 0) {
			song.getSongGo()[dsline] = restartPos;
			dsline++;
		} // loop at the beginning or where it wants according to the header's restart position

		// add the MIN MAX range to the instrument's description, and find
		// globally the lowest and highest used note of all instruments in
		// the whole song
		int glonomin = Notes.NOTESNUM - 1;
		int glonomax = 0;
		for (int i = 1; i <= modSamples; i++) {
			if (imark[i].used == 0) {
				continue;
			}
			int minnote = imark[i].minNote;
			int maxnote = imark[i].maxNote;
			if (minnote < glonomin) {
				glonomin = minnote;
			}
			if (maxnote > glonomax) {
				glonomax = maxnote;
			}
			int avol = atariVolume(imark[i].trackVolumeMax);
			String s = String.format("%s%s%X%02X", Notes.getNote(minnote), Notes.getNote(maxnote), avol, imark[i].used);
			char[] name = instruments.getName(i);
			for (int c = 0; c < 9; c++) {
				name[23 + c] = s.charAt(c);
			}
		}

		if (xShiftDownOctave) { // shift an octave down for notes tuned too high (if possible)
			if (glonomin >= 12 && glonomax > 36 + 5) {
				int noteshift = 256 - 12; // 1 octave lower
				for (int i = 1; i <= modSamples; i++) {
					if (imark[i].used == 0) {
						continue;
					}
					instruments.getInstrument(i).noteTable[0] = noteshift & 0xff;
				}
			}
		}

		// imitation volume according to sample
		int smpfrom = patStart + (maxPat + 1) * patternSize;
		int nonEmptySamples = 0;
		for (int i = 1; i <= modSamples; i++) {
			InstrumentMark im = imark[i];
			Instrument rmti = instruments.getInstrument(i);

			int samplen = im.sampleLen;
			if (samplen <= 2) {
				smpfrom += samplen;
				continue; // zero length => empty sample
			}

			byte[] smpdata = new byte[samplen]; // zero-initialized, matching C++'s memset
			if (smpfrom >= 0 && smpfrom + samplen <= mem.length) {
				System.arraycopy(mem, smpfrom, smpdata, 0, samplen);
			}
			// else: seek/read would have failed in C++ - guard-only SendWarningMessage omitted, smpdata stays all zero

			int period = PERTABLE[im.minNote];
			int parts = samplen / period;
			if (parts < 1) {
				parts = 1;
			}
			if (im.repLen > 2 || im.repPoint > 0) {
				// there is a loop
				if (parts > 32) {
					parts = 32;
				}
			} else {
				// there is no loop
				if (parts > 31) {
					parts = 31; // 32 parts reserved for silence
				}
			}
			int blocksize = samplen / parts;
			int blockp = blocksize - 1; // -1 to shift the boundaries between partitions by 1 to the left

			long sum = 0;
			int lastsd = 0;
			long maxsum = 1; // the maximum achievable sum in a sample block (1 because it's divided by this)
			int ix = 0;
			long[] sumtab = new long[32];
			for (int k = 0; k < samplen; k++) {
				int sd = ub(smpdata, k);
				sum += Math.abs(sd - lastsd);
				lastsd = sd; // last state of the curve
				if (k == blockp) { // border between divisions
					sumtab[ix] = sum;
					if (sum > maxsum) {
						maxsum = sum; // the highest
					}
					sum = 0;
					blockp += blocksize; // shift the boundary by the length of the section
					ix++;
					if (ix >= parts) {
						break;
					}
				}
			}

			double sampvol = xDecreaseInstrument ? (im.volume / (double) 0x3f) : 1; // decimal number 0 to 1
			if (xVolumeIncrease) {
				sampvol /= im.trackVolumeIncrease; // decreases as the volume in tracks increases
			}
			for (int k = 0; k < ix; k++) {
				int avol = (int) (16 * (sumtab[k] / (double) maxsum) * sampvol + 0.5);
				if (avol > 15) {
					avol = 15;
				}
				rmti.envelope[k][EnvelopeParameter.VOLUMEL] = rmti.envelope[k][EnvelopeParameter.VOLUMER] = avol;
				rmti.envelope[k][EnvelopeParameter.DISTORTION] = 0x0a; // pure tone
			}

			rmti.parameters[Instrument.PAR_ENV_LENGTH] = ix - 1;
			if (im.repLen > 2 || im.repPoint > 0) { // is there a loop?
				int ego = (int) (im.repPoint / (double) blocksize + 0.5);
				if (ego > ix - 1) {
					ego = ix - 1;
				}
				rmti.parameters[Instrument.PAR_ENV_GOTO] = ego;
				// divides the end of the instrument according to the length of the loop
				int lopEnd = (int) ((im.repPoint + im.repLen) / (double) blocksize + 0.5);
				if (lopEnd > ix - 1) {
					lopEnd = ix - 1;
				}
				// C++'s body here is a bare "rmti->parameters[PAR_ENV_LENGTH];" -
				// reads and discards, never assigns lopEnd anywhere. Looks like a
				// forgotten assignment, but the intended target isn't obvious
				// enough to guess safely, and no test reaches this branch (the
				// existing test's sample has no loop) - see class javadoc.
				// lopEnd is therefore computed and, faithfully, left unused.
			} else {
				// no loop (ix is max 31, so a "silent loop" can be added at the end)
				rmti.envelope[ix][EnvelopeParameter.VOLUMEL] = rmti.envelope[ix][EnvelopeParameter.VOLUMER] = 0; // silence at the end
				rmti.parameters[Instrument.PAR_ENV_LENGTH] = ix; // length of 1 cycle
				rmti.parameters[Instrument.PAR_ENV_GOTO] = ix; // jump on the same thing
			}

			nonEmptySamples++;
			smpfrom += samplen;
		}

		// checking the end of the module with the end of the last sample:
		// SendWarningMessage("Warning", "Bad length of module...") omitted (guard-only)

		// and only at the end
		for (int i = 1; i <= modSamples; i++) {
			// send to Atari (the display-hint half of it - see Instruments.update)
			instruments.update(i);
		}

		int optiTracks = 0;
		int optiBeats = 0;
		if (xOptimizeLoops) {
			Song.TracksAllLoopResult r = song.tracksAllBuildLoops(undo);
			optiTracks = r.tracksModified();
			optiBeats = r.beatsOrLoops();
		}

		int clearedTracks = 0;
		int truncatedTracks = 0;
		int truncatedBeats = 0;
		if (xTruncateUnusedParts) {
			Song.ClearUnusedResult r = song.songClearUnusedTracksAndParts(tracks4_8);
			clearedTracks = r.clearedTracks();
			truncatedTracks = r.truncatedTracks();
			truncatedBeats = r.truncatedBeats();
		}

		return new ApplyResult(tracks4_8, destNum, nonEmptySamples, dsline, optiTracks, optiBeats, clearedTracks, truncatedTracks, truncatedBeats);
	}

	/**
	 * Ported from the shared body of C++'s {@code TonePortamento:} label:
	 * if a continuing portamento has crossed into a new note's pitch class,
	 * resolves that note (consuming the portamento); otherwise this cell
	 * stays empty. Called once for a genuinely empty cell (invalid period)
	 * and once when a tone-portamento effect's target hasn't been reached
	 * yet (C++'s {@code goto TonePortamento;}) - safe to share since C++
	 * itself never re-enters this logic more than once per line/channel.
	 */
	private static TonePortamentoResult tonePortamento(boolean xPortamento, boolean[] tporon, int ch, int[] tper, int[] perToNote, int[] tnot, int[] tvol) {
		if (xPortamento && tporon[ch]) { // the last time was portamento
			int pnote = perToNote[tper[ch]];
			if (pnote != tnot[ch]) {
				// portamento effect shifted the frequency to the level of another note
				tporon[ch] = false; // done for now (the added note corresponds to the change by portamento)
				return new TonePortamentoResult(pnote, tvol[ch], true);
			}
		}
		return new TonePortamentoResult(-1, -1, false);
	}

	private record TonePortamentoResult(int note, int vol, boolean written) {
	}

	private static int atariVolume(int volume0_64) {
		int avol = (int) (volume0_64 / 4.0 + 0.5); // conversion to atari volume
		if (volume0_64 < 1) {
			avol = 0;
		} else if (volume0_64 == 1) {
			avol = 1;
		} else if (avol > 0x0f) {
			avol = 0x0f;
		}
		return avol;
	}

	private static boolean matches(byte[] data, int offset, String s) {
		for (int i = 0; i < s.length(); i++) {
			if (ub(data, offset + i) != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static int ub(byte[] data, int index) {
		return data[index] & 0xFF;
	}

	private static final class InstrumentMark {
		int used; // bits determine in which column (on which channel) it is used
		int volume;
		int minNote;
		int maxNote;
		int sampleLen;
		int repPoint;
		int repLen;
		double trackVolumeIncrease = 1;
		int trackVolumeMax;
	}
}
