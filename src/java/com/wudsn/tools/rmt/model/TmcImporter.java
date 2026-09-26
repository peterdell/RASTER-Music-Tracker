package com.wudsn.tools.rmt.model;

/**
 * Ported from {@code CSong::ImportTMCParseHeader}/{@code ImportTMCApply}
 * (src/cpp/IO_ImporterCore.cpp) - Batch A of {@code plans/JAVA_IMPORTER_PLAN.md}.
 * {@code CSong::ImportTMC()} itself (src/cpp/IO_Importer.cpp) stays
 * unported - a real, still-dialog-showing thin wrapper (two real MFC
 * dialogs), matching this project's established "real dialog stays
 * deferred" pattern.
 *
 * <p>A new free-standing class (not a {@link Song} method), matching
 * {@link RmtExporter}/{@link AsmFileExporter}'s precedent, taking
 * {@link Song}/{@link Tracks}/{@link Instruments} explicitly.
 *
 * <p>C++'s {@code std::istream&} becomes a plain {@code byte[]} (the whole
 * file), matching every other binary format's established idiom in this
 * port.
 *
 * <p><b>{@code CConvertTracks}/{@code TSourceTrack}/{@code TDestinationMark}/
 * {@code TInstrumentMark}</b> (TMC-only helpers, confirmed unused by MOD
 * import) become the private nested classes {@link ConvertTracks}/
 * {@link SourceTrack}/{@link DestinationMark}/{@link InstrumentMark} below.
 *
 * <p><b>{@code g_Instruments.Update(i)}</b> is called where C++ calls it,
 * for the half of it that exists ({@link Instruments#update}: the
 * display-hint flags; the Atari-memory write is still deferred - no
 * {@code Atari} dependency is modeled on either {@link Song} or here).
 *
 * <p><b>The TMC command-5 {@code rand()} call is genuinely non-deterministic
 * in C++ too</b>: reached only when a saved instrument uses TMC envelope
 * command 5 ("PN->AUDF", a fixed random frequency) - not exercised by
 * {@code ImportTMCApplyConvertsANoteIntoTheDestinationTrack} (its buffer
 * defines no instruments at all). A byte-exact match to C++'s {@code rand()}
 * sequence isn't meaningful to preserve (different PRNG, different
 * seeding) - this port uses {@link java.util.concurrent.ThreadLocalRandom}
 * for the same "some unpredictable byte" intent instead.
 */
public final class TmcImporter {

	private TmcImporter() {
	}

	private static final int VOLUMES_L = 1;
	private static final int VOLUMES_R = 2;

	/** {@code ok}/C++'s own {@code bool} return value are the same thing; {@code mem}/{@code bfrom} were {@link Song}'s {@code TImportTMCHeader} output-parameter fields. */
	public record ParseHeaderResult(boolean ok, byte[] mem, int bfrom) {
	}

	/** Mirrors C++'s {@code TImportTMCResult} output-parameter struct, plus {@code tracks4_8} - {@link #apply} may change it via {@link Song#setTracks}, and (matching this port's established "no stored global" idiom) the caller needs the resulting value back. */
	public record ApplyResult(int tracks4_8, int numOfTracks, int nonEmptyInstruments, int songLines, int optiTracks, int optiBeats, int clearedTracks, int truncatedTracks, int truncatedBeats) {
	}

	/**
	 * The unconditional real work {@code ImportTMC()}'s options dialog needs
	 * done first (its own text needs the parsed song name): clears the
	 * current song, loads the file's one binary block, and sets the song
	 * name from its header. Returns {@code ParseHeaderResult(false, ...)}
	 * on a corrupted/truncated file (matching C++'s guard).
	 */
	public static ParseHeaderResult parseHeader(byte[] data, Song song, Tracks tracks, Undo undo) {
		tracks.setMaxTrackLength(64); // track length is 64
		song.clearSong(8, undo); // standard TMC is 8 tracks, clear everything

		byte[] mem = new byte[Atari.MEMORY_SIZE];
		AtariIO.BinaryBlockResult block = AtariIO.loadBinaryBlock(data, 0, mem);

		if (block.length() <= 0) {
			return new ParseHeaderResult(false, mem, 0);
		}

		char[] songName = new char[SongInfo.SONG_NAME_MAX_LEN];
		int j = 0;
		for (; j < 30; j++) {
			char a = (char) ub(mem, block.fromAddr() + j);
			if (a == 0) {
				break;
			}
			if (a < 32 || a >= 127) {
				a = ' ';
			}
			songName[j] = a;
		}
		for (int k = j; k < SongInfo.SONG_NAME_MAX_LEN; k++) {
			songName[k] = ' '; // fill in the gaps
		}

		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		System.arraycopy(songName, 0, info.songName, 0, SongInfo.SONG_NAME_MAX_LEN);
		song.setSongInfoPars(info);

		return new ParseHeaderResult(true, mem, block.fromAddr());
	}

	/**
	 * The rest of the real conversion, given the options dialog's three
	 * flags: decodes every track, every used instrument, and the song data
	 * itself, then optionally runs the loop-building/unused-cleanup
	 * optimizations.
	 */
	public static ApplyResult apply(ParseHeaderResult header, boolean useTable, boolean optimizeLoops, boolean truncateUnusedParts, Song song, Tracks tracks, Instruments instruments, Undo undo) {
		byte[] mem = header.mem();
		int bfrom = header.bfrom();

		ConvertTracks cot = new ConvertTracks(tracks);

		int[] instrPtr = new int[64];
		int[] trackPtr = new int[128];
		boolean[] instrUsed = new boolean[64];

		// speeds
		int mainSpeed = ub(mem, bfrom + 30) + 1;
		int instrumentSpeed = ub(mem, bfrom + 31);
		int speco = 0; // speed correction
		if (instrumentSpeed > 4) {
			speco = instrumentSpeed - 4;
			instrumentSpeed = 4; // 4x instrspeed maximum
		} else if (instrumentSpeed < 1) {
			instrumentSpeed = 1; // 1x instrspeed minimum
		}

		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = mainSpeed;
		info.speed = mainSpeed;
		info.instrumentSpeed = instrumentSpeed;
		song.setSongInfoPars(info);

		// instrument vectors
		for (int i = 0; i < 64; i++) {
			instrPtr[i] = ub(mem, bfrom + 32 + i) + (ub(mem, bfrom + 32 + 64 + i) << 8);
		}
		// track vectors
		for (int i = 0; i < 128; i++) {
			trackPtr[i] = ub(mem, bfrom + 32 + 128 + i) + (ub(mem, bfrom + 32 + 128 + 128 + i) << 8);
		}

		// tracks
		for (int i = 0; i < 128; i++) {
			int adr = trackPtr[i];
			if (ub(mem, adr) == 0xff) {
				continue; // points to FF
			}
			int line = 0;
			SourceTrack ts = cot.getSTrack(i);
			int ains = 0;
			int volL;
			int volR;
			boolean endt = false;

			while (true) {
				if (line >= 64) {
					ts.len = 64;
					break;
				}

				int c = ub(mem, adr++);
				int txx = c & 0xc0;
				if (txx == 0x80) {
					// instrument
					ains = c & 0x3f;
					instrUsed[ains] = true;
				} else if (txx == 0x00) {
					// note and volume
					int note = (c & 0x3f) - 1;
					if (note >= 0) {
						ts.note[line] = note;
						ts.instr[line] = ains;
					}
					c = ub(mem, adr++);
					volL = 15 - ((c & 0xf0) >> 4);
					volR = 15 - (c & 0x0f);
					if (volL != volR) {
						ts.stereo = true;
					}
					if (volL != 0 || volR != 0) { // in TMC there may be no note if vol L and R are both equal to 0
						ts.volumeL[line] = volL;
						ts.volumeR[line] = volR;
					}
					line++;
				} else if (txx == 0x40) {
					// note speed (+possible volume)
					int note = (c & 0x3f) - 1;
					if (note >= 0) {
						ts.note[line] = note;
						ts.instr[line] = ains;
					}
					c = ub(mem, adr++);
					int speed = c & 0x0f;
					if (speed == 0) {
						ts.len = line + 1;
						endt = true;
					} else {
						ts.speed[line] = speed + 1;
					}
					if ((c & 0x80) != 0) { // behind speed is also the volume
						c = ub(mem, adr++);
						volL = 15 - ((c & 0xf0) >> 4);
						volR = 15 - (c & 0x0f);
						if (volL != volR) {
							ts.stereo = true;
						}
						if (volL != 0 || volR != 0) {
							ts.volumeL[line] = volL;
							ts.volumeR[line] = volR;
						}
					}
					if (endt) {
						break; // end of this track via speed = 0
					}
					line++;
				} else if (txx == 0xc0) {
					// gap
					int space = (c & 0x3f) + 1;
					if (space > 63 || line + space > 63) {
						if (line > 0) {
							ts.len = 64;
						}
						break; // space = 64 (ff) => end of this track
					}
					line += space;
				}
			}
		}

		// instruments
		int nonEmptyInstruments = 0;
		for (int i = 0; i < 64; i++) {
			Instrument ai = instruments.getInstrument(i);

			if (instrUsed[i]) { // this instrument is used somewhere in some track
				String s = String.format("TMC instrument imitation %02X", i);
				for (int c = 0; c < s.length(); c++) {
					ai.name[c] = s.charAt(c);
				}
			}

			int adrE = instrPtr[i];
			if (adrE == 0) {
				continue; // undefined
			}

			nonEmptyInstruments++;

			int adrT = instrPtr[i] + 63;
			int adrP = instrPtr[i] + 63 + 8;

			int audctl1 = ub(mem, adrP + 1);
			int audctl2 = ub(mem, adrP + 2);

			boolean anyRightVolIsntZero = false;
			boolean filterU = false; // is it using the filter?
			int cmd1_2 = 0;
			int par1_2 = 0;
			int cmd2_2 = 0;
			int par2_2 = 0;
			int cmd6_6 = 0;
			int par6_6 = 0;
			int maxVolL = 0;
			int maxVolR = 0;
			int lastVol = 0;
			for (int j = 0; j < 21; j++) {
				int c1 = ub(mem, adrE + j * 3);
				int c2 = ub(mem, adrE + j * 3 + 1);
				int c3 = ub(mem, adrE + j * 3 + 2);

				int dist08 = (c1 >> 4) & 0x01; // forced volume bit
				int dist = (c1 >> 4) & 0x0e; // distortions, in step of 2
				if (dist == 0x0e) {
					dist = 0x0a; // pure tones
				} else if (dist == 0x06) {
					dist = 0x02; // distortion 2 sharp tones
				}
				// now dist is 0,2,4,8,A,C (without 0x06 and 0x0e)

				int basstable = ub(mem, adrP + 7) & 0xc0;
				if (dist == 0x0c && (basstable == 0x80 || basstable == 0xc0)) {
					dist = 0x0e;
				}

				int com08 = (c2 >> 4) & 0x08; // command 8x
				int audctl = (com08 != 0) ? audctl2 : audctl1;

				// 16 bit bass?
				if (((audctl & 0x50) == 0x50 || (audctl & 0x28) == 0x28) && (dist == 0x0c)) {
					dist = 0x06; // 16bit bass
				}

				// filter
				if ((audctl & 0x04) == 0x04 || (audctl & 0x02) == 0x02) {
					ai.envelope[j][EnvelopeParameter.FILTER] = 1;
					filterU = true;
				}

				ai.envelope[j][EnvelopeParameter.DISTORTION] = dist;
				int vol = c1 & 0x0f; // volumeL 0-F
				ai.envelope[j][EnvelopeParameter.VOLUMEL] = vol;
				lastVol = vol; // needed to correct the fading
				if (vol > maxVolL) {
					maxVolL = vol;
				}
				vol = c2 & 0x0f; // volumeR 0-F
				ai.envelope[j][EnvelopeParameter.VOLUMER] = vol;
				if (vol > maxVolR) {
					maxVolR = vol;
				}
				if (vol > 0) {
					anyRightVolIsntZero = true;
				}

				int tmcCmd = (c2 >> 4) & 0x07;
				int tmcPar = c3;
				int rmtCmd = tmcCmd;
				int rmtPar = tmcPar;

				// TMC to RMT command conversion
				switch (tmcCmd) {
				case 0: // without effect
					rmtCmd = 0;
					rmtPar = 0;
					break;
				case 1: // P-> AUDF (same as TMC)
					cmd1_2 = 2;
					par1_2 = tmcPar;
					if (com08 != 0 || tmcPar == 0x00) { // but cmd 9 or 1 with parameter 00 is volume only
						rmtCmd = 7; // Volume only
						rmtPar = 0x80;
					}
					break;
				case 2: // P+A->AUDF
					if (j > 0 && cmd1_2 != 0) {
						rmtCmd = 1;
						rmtPar = (par1_2 + tmcPar) & 0xff;
						cmd1_2 = 2;
						par1_2 = rmtPar;
					} else if (j > 0 && cmd2_2 != 0) {
						rmtCmd = 2;
						rmtPar = (par2_2 + tmcPar) & 0xff;
						cmd2_2 = 2;
						par2_2 = rmtPar;
					} else {
						rmtCmd = 2;
						rmtPar = tmcPar;
						cmd2_2 = 2;
						par2_2 = rmtPar;
					}
					break;
				case 3: // P+N->AUDF
					rmtCmd = 2;
					rmtPar = tmcPar;
					break;
				case 4: // P&RND->AUDF - see class javadoc re: rand()
					rmtCmd = 1;
					rmtPar = java.util.concurrent.ThreadLocalRandom.current().nextInt(256);
					break;
				case 5: // PN->AUDF (plays a fixed note with that index)
					rmtCmd = 1;
					rmtPar = (256 - (tmcPar * 4)) & 0xff;
					break;
				case 6: // PN+A->AUDF
					if (j > 0 && cmd6_6 != 0) {
						rmtCmd = 0;
						rmtPar = (par6_6 + tmcPar) & 0xff;
						cmd6_6 = 2;
						par6_6 = rmtPar;
					} else {
						rmtCmd = 0;
						rmtPar = tmcPar;
						cmd6_6 = 2;
						par6_6 = rmtPar;
					}
					break;
				case 7: // PN+N->AUDF
					rmtCmd = 0; // in RMT, the note shift is done by command 0
					break;
				default:
					break;
				}

				// bass shift
				if (dist == 0x0c && rmtCmd == 0) {
					rmtPar += 8;
				}

				// forced volume
				if (dist08 != 0) {
					rmtCmd = 7;
					rmtPar = 0x80;
				}

				ai.envelope[j][EnvelopeParameter.COMMAND] = rmtCmd;
				ai.envelope[j][EnvelopeParameter.X] = (rmtPar >> 4) & 0x0f;
				ai.envelope[j][EnvelopeParameter.Y] = rmtPar & 0x0f;

				if (cmd1_2 > 0) {
					cmd1_2--;
				}
				if (cmd2_2 > 0) {
					cmd2_2--;
				}
				if (cmd6_6 > 0) {
					cmd6_6--;
				}
			} // 0-20 column envelope

			// is all right volume = 0? => copies left to right
			if (!anyRightVolIsntZero) {
				for (int j = 0; j <= 21; j++) {
					ai.envelope[j][EnvelopeParameter.VOLUMER] = ai.envelope[j][EnvelopeParameter.VOLUMEL];
				}
				maxVolR = maxVolL;
			}

			// envelope length
			ai.parameters[Instrument.PAR_ENV_LENGTH] = 20; // the envelope is 21 columns
			ai.parameters[Instrument.PAR_ENV_GOTO] = 20;

			InstrumentMark im = cot.getIMark(i);
			im.maxVolL = maxVolL;
			im.maxVolR = maxVolR;
			im.stereo = anyRightVolIsntZero;

			// table
			boolean tableU = false; // table used
			for (int j = 0; j < 8; j++) {
				int nut = ub(mem, adrT + j);
				if (nut >= 0x80 && nut <= 0xc0) {
					nut += 0x40;
				}
				if (nut >= 0x40 && nut <= 0x7f) {
					nut -= 0x40;
				}
				if (nut != 0) {
					tableU = true;
				}
				ai.noteTable[j] = nut;
			}

			// parameters
			// table length and speed
			int tablen = (ub(mem, adrP + 8) >> 4) & 0x07;
			ai.parameters[Instrument.PAR_TBL_LENGTH] = tablen;
			ai.parameters[Instrument.PAR_TBL_SPEED] = ub(mem, adrP + 8) & 0x0f;

			// volume slide
			int t1vslide = ub(mem, adrP + 3);
			// speco is the correction when the instrument decelerates from 5 and more to 4
			int t2vslide = (t1vslide < 1) ? 0 : (int) (15.0 / lastVol * 255.0 / (t1vslide * (1 - speco / 4.0)) + 0.5) & 0xff;
			ai.parameters[Instrument.PAR_VOL_FADEOUT] = t2vslide;
			ai.parameters[Instrument.PAR_VOL_MIN] = 0;

			// vibrato or fshift
			int pvib = ub(mem, adrP + 5) & 0x7f;
			int pvib8 = ub(mem, adrP + 5) & 0x80; // highest bit
			int delay = ub(mem, adrP + 6);
			int vibspe = ub(mem, adrP + 7) & 0x3f; // vibrato speed
			int vib = 0;
			int fshift = 0;
			boolean vpt = false; // vibrato through the table succeeded

			int posuntable = (int) ((double) delay / (vibspe + 1) + 0.5);

			boolean nobytable = !useTable || filterU; // if it tries to convert to a table

			// what if the table is used, but only to move to the 0th place
			if (!nobytable && tableU && tablen == 0 && (pvib & 0x40) != 0) {
				// that is, it optimizes over the shift of all notes in the envelope
				int psn = ai.noteTable[0]; // 0th place in the table
				for (int j = 0; j < 21; j++) {
					if (ai.envelope[j][EnvelopeParameter.COMMAND] == 0) { // music shift
						int notenum = (ai.envelope[j][EnvelopeParameter.X] << 4) + ai.envelope[j][EnvelopeParameter.Y];
						notenum = (notenum + psn) & 0xff; // shifts
						ai.envelope[j][EnvelopeParameter.X] = (notenum >> 4) & 0x0f;
						ai.envelope[j][EnvelopeParameter.Y] = notenum & 0x0f;
					}
				}
				ai.noteTable[0] = 0; // so the parameter in the table is reset
				tableU = false; // and thus the table is free for further use
			}

			// and now the individual values of the "vibrato" parameter:
			if (pvib > 0x10 && pvib < 0x3f) {
				// vibrato
				int hn = pvib >> 4; // vibrato type 1-3
				int dn = pvib & 0x0f; // cut out vibrato 0-f

				vib = 3;
				if (hn == 1) {
					vib = 1 + vibspe;
				} else if (hn == 2 && dn == 1) {
					vib = 2 + vibspe;
				}
				if (vib > 3) {
					vib = 3;
				}

				// if you do not use the table, try to use vibrato through the table
				if (!nobytable && !tableU) {
					if (hn == 1 && (dn > 2 || vibspe > 0)) {
						if (posuntable > Instrument.NOTE_TABLE_MAX_LEN - 4) {
							posuntable = Instrument.NOTE_TABLE_MAX_LEN - 4;
						}
						ai.noteTable[posuntable] = 0;
						ai.noteTable[posuntable + 1] = (pvib8 != 0) ? (256 - dn) & 0xff : dn;
						ai.noteTable[posuntable + 2] = 0;
						ai.noteTable[posuntable + 3] = (pvib8 != 0) ? dn : (256 - dn) & 0xff;
						ai.parameters[Instrument.PAR_TBL_LENGTH] = posuntable + 3;
						ai.parameters[Instrument.PAR_TBL_GOTO] = posuntable;
						ai.parameters[Instrument.PAR_TBL_TYPE] = 1; // frequency table
						ai.parameters[Instrument.PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
						vpt = true;
					} else if (hn == 2 && (dn > 2 || vibspe > 0)) {
						if (posuntable > Instrument.NOTE_TABLE_MAX_LEN - 4) {
							posuntable = Instrument.NOTE_TABLE_MAX_LEN - 4;
						}
						ai.noteTable[posuntable] = (pvib8 != 0) ? (256 - dn) & 0xff : dn;
						ai.noteTable[posuntable + 1] = 0;
						ai.noteTable[posuntable + 2] = (pvib8 != 0) ? dn : (256 - dn) & 0xff;
						ai.noteTable[posuntable + 3] = 0;
						ai.parameters[Instrument.PAR_TBL_LENGTH] = posuntable + 3;
						ai.parameters[Instrument.PAR_TBL_GOTO] = posuntable;
						ai.parameters[Instrument.PAR_TBL_TYPE] = 1;
						int sp = dn * (vibspe + 1) - 1;
						if (sp < 0) {
							sp = 0;
						} else if (sp > 0x3f) {
							sp = 0x3f;
						}
						ai.parameters[Instrument.PAR_TBL_SPEED] = sp;
						vpt = true;
					} else if (hn == 3 && (dn > 2 || vibspe > 0)) {
						if (posuntable > Instrument.NOTE_TABLE_MAX_LEN - 4) {
							posuntable = Instrument.NOTE_TABLE_MAX_LEN - 4;
						}
						ai.noteTable[posuntable] = (pvib8 != 0) ? (dn * 4) & 0xff : (256 - (dn * 4)) & 0xff;
						ai.noteTable[posuntable + 1] = 0;
						ai.noteTable[posuntable + 2] = (pvib8 != 0) ? (256 - (dn * 4)) & 0xff : (dn * 4) & 0xff;
						ai.noteTable[posuntable + 3] = 0;
						ai.parameters[Instrument.PAR_TBL_LENGTH] = posuntable + 3;
						ai.parameters[Instrument.PAR_TBL_GOTO] = posuntable;
						ai.parameters[Instrument.PAR_TBL_TYPE] = 1;
						int sp = dn * (vibspe + 1) - 1;
						if (sp < 0) {
							sp = 0;
						} else if (sp > 0x3f) {
							sp = 0x3f;
						}
						ai.parameters[Instrument.PAR_TBL_SPEED] = sp;
						vpt = true;
					}
				}
			} else if (pvib > 0x40 && pvib <= 0x4f) {
				// fshift down (added frq)
				fshift = pvib - 0x40;
				if (pvib8 != 0) {
					fshift = (256 - fshift) & 0xff;
				}

				if (!nobytable && !tableU && vibspe > 0) {
					if (posuntable > Instrument.NOTE_TABLE_MAX_LEN - 2) {
						posuntable = Instrument.NOTE_TABLE_MAX_LEN - 2;
					}
					ai.noteTable[posuntable] = fshift;
					ai.noteTable[posuntable + 1] = fshift;
					ai.parameters[Instrument.PAR_TBL_LENGTH] = posuntable + 1;
					ai.parameters[Instrument.PAR_TBL_GOTO] = posuntable + 1;
					ai.parameters[Instrument.PAR_TBL_TYPE] = 1;
					ai.parameters[Instrument.PAR_TBL_MODE] = 1; // read
					ai.parameters[Instrument.PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
					vpt = true;
				}
			} else if (pvib > 0x50 && pvib <= 0x5f) {
				// shift in notes down (left) => shift in frequency 255/61 * shift_in_notes
				fshift = (int) (((255 / 61) * (pvib - 0x50)) + 0.5);
				if (pvib8 != 0) {
					fshift = (256 - fshift) & 0xff;
				}

				if (!nobytable && !tableU) {
					if (posuntable > Instrument.NOTE_TABLE_MAX_LEN - 2) {
						posuntable = Instrument.NOTE_TABLE_MAX_LEN - 2;
					}
					int nshift = pvib - 0x50;
					if (pvib8 == 0) {
						nshift = (256 - nshift) & 0xff; // for notes it is the opposite (5x is <- down, Dx is up ->)
					}
					ai.noteTable[posuntable] = nshift;
					ai.noteTable[posuntable + 1] = nshift;
					ai.parameters[Instrument.PAR_TBL_LENGTH] = posuntable + 1;
					ai.parameters[Instrument.PAR_TBL_GOTO] = posuntable + 1;
					ai.parameters[Instrument.PAR_TBL_TYPE] = 0; // note table
					ai.parameters[Instrument.PAR_TBL_MODE] = 1; // read
					ai.parameters[Instrument.PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
					vpt = true;
				}
			}
			// pvib > 0x60 && pvib <= 0x6f: "tuned $60-$6f => -0 to -15 to frequency - we don't know" (C++'s own comment - no logic there)

			// verify whether the vibrato effect was done via table or "normal" via vibrato and fshift
			if (vpt) {
				vib = 0;
				fshift = 0;
				delay = 0;
			}

			ai.parameters[Instrument.PAR_VIBRATO] = vib;
			ai.parameters[Instrument.PAR_FREQ_SHIFT] = fshift;
			if (vib == 0 && fshift == 0) {
				delay = 0;
			} else if ((vib > 0 || fshift > 0) && delay == 0) {
				delay = 1;
			}
			ai.parameters[Instrument.PAR_DELAY] = delay;

			// optimization: envelope length
			int lastNonZeroVolumeCol = -1;
			int lastChangeCol = 0;
			for (int k = 0; k <= 20; k++) {
				if (ai.envelope[k][EnvelopeParameter.VOLUMEL] > 0 || ai.envelope[k][EnvelopeParameter.VOLUMER] > 0) {
					lastNonZeroVolumeCol = k;
				}
				if (k > 0) {
					for (int m = 0; m < Instrument.ENVROWS; m++) {
						if (ai.envelope[k][m] != ai.envelope[k - 1][m]) {
							lastChangeCol = k;
							break;
						}
					}
				}
			}

			if (lastNonZeroVolumeCol < 20) {
				ai.parameters[Instrument.PAR_ENV_LENGTH] = ai.parameters[Instrument.PAR_ENV_GOTO] = lastNonZeroVolumeCol + 1;
			}

			if (lastChangeCol < lastNonZeroVolumeCol && ai.parameters[Instrument.PAR_VOL_FADEOUT] == 0) {
				ai.parameters[Instrument.PAR_ENV_LENGTH] = ai.parameters[Instrument.PAR_ENV_GOTO] = lastChangeCol;
			}

			// table optimization
			boolean tableAllZero = true;
			for (int v = 0; v <= ai.parameters[Instrument.PAR_TBL_LENGTH]; v++) {
				if (ai.noteTable[v] != 0) {
					tableAllZero = false;
					break;
				}
			}
			if (tableAllZero && ai.parameters[Instrument.PAR_TBL_LENGTH] >= 1 && ai.parameters[Instrument.PAR_TBL_GOTO] == 0) {
				ai.parameters[Instrument.PAR_TBL_LENGTH] = 0;
				ai.parameters[Instrument.PAR_TBL_SPEED] = 0;
			}

			// projected instrument into Atari's RAM (the display-hint half of it - see Instruments.update)
			instruments.update(i);
		} // and another instrument

		// song
		int line = 0;
		int lr = 0;
		boolean stereoModul = false;
		int numOfTracks = 0;
		int adrEndSong = (instrPtr[0] > 0) ? instrPtr[0] : trackPtr[0];

		for (int adr = bfrom + 32 + 128 + 256; adr < adrEndSong; adr += 16, line++) {
			if ((ub(mem, adr + 15) & 0x80) == 0x80) { // goto line?
				song.getSongGo()[line] = ub(mem, adr + 14) & 0x7f;
				continue;
			}
			for (int i = 0; i < 8; i++) {
				int t = ub(mem, adr + 15 - i * 2);
				int preladeni = mem[adr + 14 - i * 2]; // signed - matches C++'s (char) cast
				if (t >= 0 && t < 128) {
					lr = VOLUMES_L; // matches C++'s own dead-code override (see IO_ImporterCore.cpp)

					int vyslednytrack = cot.makeOrFindTrackShiftLR(t, preladeni, lr);

					song.getSong()[line][i] = vyslednytrack;

					if (vyslednytrack > numOfTracks) {
						numOfTracks = vyslednytrack; // total number of tracks
					}

					if (vyslednytrack >= 0 && i >= 4) {
						stereoModul = true;
					}
				}
			}
		}
		// is there a goto in the end?
		if (line >= 1 && song.getSongGo()[line - 1] < 0) {
			song.getSongGo()[line + 1] = line; // no, so it adds an endless loop to the end
		}

		int tracks4_8 = 8;
		if (!stereoModul) { // mono module
			tracks4_8 = song.setTracks(4);
		}

		int optiTracks = 0;
		int optiBeats = 0;
		if (optimizeLoops) {
			Song.TracksAllLoopResult r = song.tracksAllBuildLoops(undo);
			optiTracks = r.tracksModified();
			optiBeats = r.beatsOrLoops();
		}

		int clearedTracks = 0;
		int truncatedTracks = 0;
		int truncatedBeats = 0;
		if (truncateUnusedParts) {
			Song.ClearUnusedResult r = song.songClearUnusedTracksAndParts(tracks4_8);
			clearedTracks = r.clearedTracks();
			truncatedTracks = r.truncatedTracks();
			truncatedBeats = r.truncatedBeats();
		}

		return new ApplyResult(tracks4_8, numOfTracks, nonEmptyInstruments, line, optiTracks, optiBeats, clearedTracks, truncatedTracks, truncatedBeats);
	}

	private static int ub(byte[] mem, int index) {
		return mem[index] & 0xFF;
	}

	private static final class SourceTrack {
		final int[] note = new int[64];
		final int[] instr = new int[64];
		final int[] volumeL = new int[64];
		final int[] volumeR = new int[64];
		final int[] speed = new int[64];
		int len = -1;
		boolean stereo;

		SourceTrack() {
			java.util.Arrays.fill(note, -1);
			java.util.Arrays.fill(instr, -1);
			java.util.Arrays.fill(volumeL, -1);
			java.util.Arrays.fill(volumeR, -1);
			java.util.Arrays.fill(speed, -1);
		}
	}

	private static final class DestinationMark {
		int fromTrack = -1;
		int shift;
		int leftRight;
	}

	private static final class InstrumentMark {
		int maxVolL;
		int maxVolR;
		boolean stereo;
	}

	/** Ported from {@code CConvertTracks} (IO_ImporterCore.cpp) - TMC-only, confirmed unused by MOD import. */
	private static final class ConvertTracks {

		private final SourceTrack[] sTrack = new SourceTrack[Tracks.TRACKSNUM];
		private final DestinationMark[] dMark = new DestinationMark[Tracks.TRACKSNUM];
		private final InstrumentMark[] iMark = new InstrumentMark[Instruments.INSTRSNUM];
		private final Tracks tracks;

		ConvertTracks(Tracks tracks) {
			this.tracks = tracks;
			for (int i = 0; i < Tracks.TRACKSNUM; i++) {
				sTrack[i] = new SourceTrack();
				dMark[i] = new DestinationMark();
			}
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				iMark[i] = new InstrumentMark();
			}
		}

		SourceTrack getSTrack(int t) {
			return (t >= 0 && t < Tracks.TRACKSNUM) ? sTrack[t] : null;
		}

		InstrumentMark getIMark(int instr) {
			return (instr >= 0 && instr < Instruments.INSTRSNUM) ? iMark[instr] : null;
		}

		/** Will try to find a matching track among the already created ones, or create a new one (as close as possible to the original track number). Returns its number, or -1. */
		int makeOrFindTrackShiftLR(int from, int shift, int lr) {
			if (sTrack[from].len < 0) {
				return -1; // this source track is empty
			}

			// will find the number where it will first create the new one
			int track;
			if (dMark[from].fromTrack < 0) {
				track = from; // the same number is free
			} else {
				// finds a free place as close as possible to the "from" track
				int i;
				for (i = from + 1; i != from; i++) {
					if (i >= Tracks.TRACKSNUM) {
						i = 0; // when it reaches the end, it starts from the beginning
					}
					if (dMark[i].fromTrack < 0) {
						break; // found a free track
					}
				}
				if (i == from) {
					return -1; // did not find one
				}
				track = i;
			}

			// rewritten from source track to system track
			SourceTrack ts = sTrack[from];
			if (!ts.stereo) {
				lr = VOLUMES_L | VOLUMES_R; // if it's not a stereo track and it wants the right channel, give it the left channel anyway
			}
			Track td = tracks.getTrack(track);
			int activeInstr = -1;
			for (int i = 0; i < ts.len; i++) {
				int note = ts.note[i];
				if (note >= 0) {
					note += shift;
					while (note < 0) {
						note += 64;
					}
					while (note >= 64) {
						note -= 64;
					}
					if (note >= Notes.NOTESNUM) {
						note = Notes.NOTESNUM - 1;
					}
				}
				td.note[i] = note;

				int instr = ts.instr[i];
				td.instr[i] = instr;
				if (instr >= 0) {
					activeInstr = instr;
				}

				int volume = ((lr & VOLUMES_L) != 0) ? ts.volumeL[i] : ts.volumeR[i];
				if (activeInstr >= 0 && volume > 0) {
					int maxvol = ((lr & VOLUMES_L) != 0) ? iMark[activeInstr].maxVolL : iMark[activeInstr].maxVolR;
					if (maxvol - (15 - volume) <= 0) {
						volume = 0;
					}
				}
				td.volume[i] = volume;

				td.speed[i] = ts.speed[i];
			}
			td.len = ts.len;

			// search if this one does not match one that already exists
			for (int i = 0; i < Tracks.TRACKSNUM; i++) {
				if (track == i) {
					continue;
				}
				if (tracks.compareTracks(track, i)) {
					// found the same - delete the newly created one, return the former number
					tracks.clearTrack(track);
					return i;
				}
			}

			// does not match the previous one
			DestinationMark dm = dMark[track];
			dm.fromTrack = from;
			dm.shift = shift;
			dm.leftRight = lr;
			return track;
		}
	}
}
