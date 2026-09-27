package org.atari.raster.rmt.model;

/**
 * Ported from CASMFileExporter (src/cpp/ASMFileExporter.h/.cpp,
 * ASMFileExporterCore.cpp) - {@link #exportAsAsmApply}/
 * {@link #buildRelocatableAsm}/{@link #exportAsRelocatableAsmForRmtPlayerApply}/
 * {@link #composeRMTFEATstring}, the dialog-independent subset
 * {@code ASMFileExporterCore.cpp}'s own header comment already confirms is
 * hazard-free ({@code ExportAsAsm()}/{@code ExportAsRelocatableAsmForRmtPlayer()}
 * in {@code ASMFileExporter.cpp} instantiate real MFC dialogs to gather
 * their parameters before delegating here - not ported, matching this
 * project's established "real dialog stays deferred" pattern).
 *
 * <p>Static methods taking {@link Song}/{@link Instruments}/{@link Tracks}
 * explicitly (matching C++'s own free-standing exporter class shape, not a
 * {@link Song} method), plus {@code tracks4_8}/{@code g_PrefixForAllAsmLabels}
 * as explicit parameters (this project's established idiom for globals a
 * ported method needs - the latter is normally a stored global the
 * dialog-driving wrapper writes to before calling {@link #exportAsAsmApply};
 * a caller here just passes whatever value it wants read).
 *
 * <p>C++'s {@code std::ostream&} output parameters become returned
 * {@code String}s (matching {@code SapFile.export()}'s established idiom -
 * this format is text, unlike {@link RmtExporter}'s binary one).
 *
 * <p><b>{@link #composeRMTFEATstring} drops C++'s unused
 * {@code trackSavedFlags} parameter</b>: confirmed by inspection to never be
 * read anywhere in the C++ body (only {@code instrumentSavedFlags} is) -
 * dead in the original, so it carries no behavior to preserve.
 *
 * <p><b>Pointer-offset-into-shared-buffer pattern</b>: C++'s
 * {@code unsigned char* buf = &exportDesc->mem[exportDesc->targetAddrOfModule];}
 * in {@link #buildRelocatableAsm} becomes a copied scratch buffer
 * ({@code Arrays.copyOfRange}), matching this project's established
 * treatment of the same pattern elsewhere (e.g. {@code Song#decodeModule}) -
 * every offset C++ computes relative to {@code buf} (already relative to
 * {@code start}) stays numerically identical against the copy.
 */
public final class AsmFileExporter {

	private AsmFileExporter() {
	}

	private static final String EOL = "\n";

	/** Mirrors {@code TRelocatableAsmExportParams} (ASMFileExporter.h). */
	public record RelocatableAsmExportParams(String strAsmLabelForStartOfSong, boolean wantRelocatableInstruments, boolean wantRelocatableTracks,
			boolean wantRelocatableSongLines, String strAsmInstrumentsLabel, String strAsmTracksLabel, String strAsmSongLinesLabel,
			AssemblerFormat assemblerFormat, boolean sfxSupport, boolean globalVolumeFade, boolean noStartingSongLine) {
	}

	/** {@code success} is the method's own {@code BOOL} return value; {@code code} was C++'s {@code CString&} output parameter (only meaningful when {@code success}). */
	public record Result(boolean success, String code) {
	}

	/**
	 * Renders the song as simple-notation assembler text: either
	 * {@code exportType == 1} (each used track, independently) or
	 * {@code exportType == 2} (each song column, line by line, following
	 * goto lines).
	 */
	public static String exportAsAsmApply(Song song, Instruments instruments, Tracks tracks, byte[] atariMemory, String prefixForAllAsmLabels, int tracks4_8, int exportType,
			int notesIndexOrFreq, int durationsType) {
		StringBuilder ou = new StringBuilder();
		int maxova = 16; // maximal amount of data per line

		byte[] tracksFlags = new byte[Tracks.TRACKSNUM];
		song.markTfUsed(tracksFlags, tracks4_8);

		ou.append(";ASM notation source");
		ou.append(EOL).append("XXX\tequ $FF\t;empty note value");
		if (!prefixForAllAsmLabels.isEmpty()) {
			ou.append(EOL).append(prefixForAllAsmLabels).append("_data");
		}

		if (exportType == 1) { // Tracks only
			for (int trackNr = 0; trackNr < Tracks.TRACKSNUM; trackNr++) {
				if ((tracksFlags[trackNr] & TrackFlag.TF_USED) == 0) {
					continue;
				}

				ou.append(EOL).append(String.format(";Track $%02X", trackNr));
				if (!prefixForAllAsmLabels.isEmpty()) {
					ou.append(EOL).append(String.format("%s_track%02X", prefixForAllAsmLabels, trackNr));
				}

				Track tempTrack = new Track();
				tempTrack.copyFrom(tracks.getTrack(trackNr));
				tracks.trackExpandLoop(tempTrack); // expands tt due to GO loops

				int ova = maxova;
				for (int idx = 0; idx < tempTrack.len; idx++) {
					if (ova >= maxova) {
						ou.append(EOL).append("\tdta ");
						ova = 0;
					}
					int note = tempTrack.note[idx];
					if (note >= 0) {
						int instrumentNr = tempTrack.instr[idx];
						note = (notesIndexOrFreq == 1) ? instruments.getNote(instrumentNr, note) : instruments.getFrequency(instrumentNr, note, atariMemory);
					}
					String snot = (note >= 0) ? String.format("$%02X", note) : "XXX";

					int dur;
					for (dur = 1; idx + dur < tempTrack.len && tempTrack.note[idx + dur] < 0; dur++) {
						// count consecutive empty notes
					}
					if (durationsType == 1) {
						if (ova > 0) {
							ou.append(",");
						}
						ou.append(snot);
						ova++;
						for (int j = 1; j < dur; j++, ova++) {
							if (ova >= maxova) {
								ova = 0;
								ou.append(EOL).append("\tdta XXX");
							} else {
								ou.append(",XXX");
							}
						}
					} else if (durationsType == 2) {
						if (ova > 0) {
							ou.append(",");
						}
						ou.append(snot).append(",").append(dur);
						ova += 2;
					} else if (durationsType == 3) {
						if (ova > 0) {
							ou.append(",");
						}
						ou.append(dur).append(",").append(snot);
						ova += 2;
					}
					idx += dur - 1;
				}
			}
		} else if (exportType == 2) { // Whole song
			String[] cnames = { "L1", "L2", "L3", "L4", "R1", "R2", "R3", "R4" };
			for (int clm = 0; clm < song.getTracks(tracks4_8); clm++) {
				boolean[] finished = new boolean[Song.SONGLEN];
				int sline = 0;
				ou.append(EOL).append(String.format(";Song column %s", cnames[clm]));
				if (!prefixForAllAsmLabels.isEmpty()) {
					ou.append(EOL).append(String.format("%s_column%s", prefixForAllAsmLabels, cnames[clm]));
				}
				while (sline >= 0 && sline < Song.SONGLEN && !finished[sline]) {
					finished[sline] = true;
					ou.append(EOL).append(String.format(";Song line $%02X", sline));
					if (song.getSongGo()[sline] >= 0) {
						sline = song.getSongGo()[sline]; // GOTO line
						ou.append(String.format(" Go to line $%02X", sline));
						continue;
					}
					int trackslen = tracks.getMaxTrackLength();
					for (int i = 0; i < song.getTracks(tracks4_8); i++) {
						int at = song.getSong()[sline][i];
						if (at < 0 || at >= Tracks.TRACKSNUM) {
							continue;
						}
						if (tracks.getGoLine(at) >= 0) {
							continue;
						}
						int al = tracks.getLastLine(at) + 1;
						if (al < trackslen) {
							trackslen = al;
						}
					}
					int ova = maxova;
					int t = song.getSong()[sline][clm];
					if (t < 0) {
						ou.append(" Track --");
						if (!prefixForAllAsmLabels.isEmpty()) {
							ou.append(EOL).append(String.format("%s_column%s_line%02X", prefixForAllAsmLabels, cnames[clm], sline));
						}
						if (durationsType == 1) {
							for (int i = 0; i < trackslen; i++, ova++) {
								if (ova >= maxova) {
									ova = 0;
									ou.append(EOL).append("\tdta XXX");
								} else {
									ou.append(",XXX");
								}
							}
						} else if (durationsType == 2) {
							ou.append(EOL).append("\tdta XXX,").append(trackslen);
						} else if (durationsType == 3) {
							ou.append(EOL).append("\tdta ").append(trackslen).append(",XXX");
						}
						sline++;
						continue;
					}

					ou.append(String.format(" Track $%02X", t));
					if (!prefixForAllAsmLabels.isEmpty()) {
						ou.append(EOL).append(String.format("%s_column%s_line%02X", prefixForAllAsmLabels, cnames[clm], sline));
					}

					Track tempTrack = new Track();
					tempTrack.copyFrom(tracks.getTrack(t));
					tracks.trackExpandLoop(tempTrack); // expands tt due to GO loops

					for (int i = 0; i < trackslen; i++) {
						if (ova >= maxova) {
							ova = 0;
							ou.append(EOL).append("\tdta ");
						}

						int anot = tempTrack.note[i];
						if (anot >= 0) {
							int ins = tempTrack.instr[i];
							anot = (notesIndexOrFreq == 1) ? instruments.getNote(ins, anot) : instruments.getFrequency(ins, anot, atariMemory);
						}
						String snot = (anot >= 0) ? String.format("$%02X", anot) : "XXX";

						int dur;
						for (dur = 1; i + dur < trackslen && tempTrack.note[i + dur] < 0; dur++) {
							// count consecutive empty notes
						}
						if (durationsType == 1) {
							if (ova > 0) {
								ou.append(",");
							}
							ou.append(snot);
							ova++;
							for (int j = 1; j < dur; j++, ova++) {
								if (ova >= maxova) {
									ova = 0;
									ou.append(EOL).append("\tdta XXX");
								} else {
									ou.append(",XXX");
								}
							}
						} else if (durationsType == 2) {
							if (ova > 0) {
								ou.append(",");
							}
							ou.append(snot).append(",").append(dur);
							ova += 2;
						} else if (durationsType == 3) {
							if (ova > 0) {
								ou.append(",");
							}
							ou.append(dur).append(",").append(snot);
							ova += 2;
						}
						i += dur - 1;
					}
					sline++;
				}
			}
		}
		ou.append(EOL);

		return ou.toString();
	}

	/**
	 * Mostly a thin wrapper around {@link #buildRelocatableAsm}: builds the
	 * relocatable assembler source for whichever of {@code exportDescStripped}/
	 * {@code exportDescWithSFX} applies ({@code p.sfxSupport()} selects), and
	 * appends it (plus a trailing {@link #EOL}) to the returned code.
	 */
	public static Result exportAsRelocatableAsmForRmtPlayerApply(Song song, Instruments instruments, Tracks tracks, int tracks4_8, byte[] memStripped, int targetAddrOfModuleStripped,
			int firstByteAfterModuleStripped, byte[] instrumentSavedFlagsStripped, byte[] memWithSFX, int targetAddrOfModuleWithSFX, int firstByteAfterModuleWithSFX,
			byte[] instrumentSavedFlagsWithSFX, RelocatableAsmExportParams p) {
		byte[] mem = p.sfxSupport() ? memWithSFX : memStripped;
		int targetAddrOfModule = p.sfxSupport() ? targetAddrOfModuleWithSFX : targetAddrOfModuleStripped;
		int firstByteAfterModule = p.sfxSupport() ? firstByteAfterModuleWithSFX : firstByteAfterModuleStripped;
		byte[] instrumentSavedFlags = p.sfxSupport() ? instrumentSavedFlagsWithSFX : instrumentSavedFlagsStripped;

		Result r = buildRelocatableAsm(song, instruments, tracks, tracks4_8, mem, targetAddrOfModule, firstByteAfterModule, instrumentSavedFlags, p.strAsmLabelForStartOfSong(),
				p.wantRelocatableTracks() ? p.strAsmTracksLabel() : "", p.wantRelocatableSongLines() ? p.strAsmSongLinesLabel() : "",
				p.wantRelocatableInstruments() ? p.strAsmInstrumentsLabel() : "", p.assemblerFormat(), p.sfxSupport(), p.globalVolumeFade(), p.noStartingSongLine(), false);

		if (!r.success()) {
			return new Result(false, null);
		}
		return new Result(true, r.code() + EOL);
	}

	/**
	 * Disassembles an already-built RMT module ({@code mem[targetAddrOfModule..firstByteAfterModule)},
	 * from {@link Song#makeModule}) back into relocatable assembler source,
	 * based on the rmt2atasm tool. Fails ({@code success = false}) if the
	 * module's internal pointer tables don't parse as expected - a
	 * corruption/format-mismatch guard, not something any valid
	 * {@link Song#makeModule} output should ever trigger.
	 */
	public static Result buildRelocatableAsm(Song song, Instruments instruments, Tracks tracks, int tracks4_8, byte[] mem, int targetAddrOfModule, int firstByteAfterModule,
			byte[] instrumentSavedFlags, String strAsmStartLabel, String strTracksLabel, String strSongLinesLabel, String strInstrumentsLabel, AssemblerFormat assemblerFormat, boolean sfx,
			boolean gvf, boolean nos, boolean wantSizeInfoOnly) {
		StringBuilder strModuleSequence = new StringBuilder("\nSequence of modules:\n  Header\n");
		StringBuilder strCode = new StringBuilder();

		int sizeIntro = 16;
		int sizeInstruments = 0;
		int sizeTrack = 0;
		int sizeSongLines = 0;

		boolean isAtasm = assemblerFormat == AssemblerFormat.ATASM;
		String byteDirective = isAtasm ? ".byte" : "dta";

		strAsmStartLabel = strAsmStartLabel.strip();
		if (strAsmStartLabel.isEmpty()) {
			strAsmStartLabel = "RMT_SONG_DATA";
		}

		strCode.append(String.format("; Cut and paste the data from the line ';* --------BEGIN--------'  to the line ';* --------END--------'\n; into rmt_feat%s\n", isAtasm ? ".asm" : "./65"));

		strCode.append(composeRMTFEATstring(song, instruments, tracks, tracks4_8, "", instrumentSavedFlags, sfx, gvf, nos, assemblerFormat));

		int start = targetAddrOfModule;
		int end = firstByteAfterModule;
		int len = end - start;
		byte[] buf = java.util.Arrays.copyOfRange(mem, start, mem.length);

		strCode.append(String.format("; RMT%c file exported as relocatable source code\n; Original size: $%04x bytes @ $%04x\n", (char) ub(buf, 3), len, start));

		// Read parameters from file:
		int numTracks = ub(buf, 3) - '0'; // RMTx - x is 4 or 8
		int offsetInstrumentPtrTable = rword(buf, 8) - start; // This is where the instruments are stored (always directly after this table)
		int offsetTrackPtrTableLow = rword(buf, 10) - start; // Track ptrs are broken up into lo and hi bytes
		int offsetTrackPtrTableHigh = rword(buf, 12) - start;
		int offsetSong = rword(buf, 14) - start; // The song tracks start here

		int numInstruments = offsetTrackPtrTableLow - offsetInstrumentPtrTable;
		int numtrk = offsetTrackPtrTableHigh - offsetTrackPtrTableLow;

		// Read all tracks addresses searching for the lowest address
		int firstTrack = 0xFFFF;
		for (int i = 0; i < numtrk; i++) {
			int x = ub(buf, offsetTrackPtrTableLow + i) + (ub(buf, offsetTrackPtrTableHigh + i) << 8);
			if (x != 0) {
				x -= start;
				if (x < 0 || x >= offsetSong) {
					return new Result(false, null);
				}
				if (x < firstTrack) {
					firstTrack = x;
				}
			}
		}
		// Read all instrument addresses searching for the lowest address
		int firstInstr = 0xFFFF;
		for (int i = 0; i < numInstruments; i += 2) {
			int x = rword(buf, offsetInstrumentPtrTable + i);
			if (x != 0) {
				x -= start;
				if (x < 0 || x >= firstTrack) {
					return new Result(false, null);
				}
				if (x < firstInstr) {
					firstInstr = x;
				}
			}
		}
		if (firstInstr < 0 || firstInstr >= len || firstTrack < 0 || firstTrack >= len) {
			if (firstInstr == 0xFFFF) {
				strCode.append("; No instrument data!\n");
			}
			if (firstTrack != 0) {
				return new Result(false, null);
			}
		}
		if (offsetTrackPtrTableHigh + numtrk != firstInstr) {
			return new Result(false, null);
		}
		if (firstTrack < firstInstr) {
			return new Result(false, null);
		}

		// Write assembly output
		strCode.append(String.format(".local\n%s\n?start\n", strAsmStartLabel));

		if (isAtasm) {
			strCode.append(String.format("    {{byte}} \"RMT%c\"\n", (char) ub(buf, 3)));
		} else {
			strCode.append(String.format("    dta c'RMT%c'\n", (char) ub(buf, 3)));
		}

		strCode.append(String.format("?song_info\n    {{byte}} $%02x            ; Track length = %d\n    {{byte}} $%02x            ; Song speed\n"
				+ "    {{byte}} $%02x            ; Player Frequency\n    {{byte}} $%02x            ; Format version\n", ub(buf, 4), ub(buf, 4), ub(buf, 5), ub(buf, 6), ub(buf, 7)));

		strCode.append("; ptrs to tables\n");
		if (isAtasm) {
			strCode.append(String.format("?ptrInstrumentTbl\n    .word ?InstrumentsTable       ; start + $%04x\n" + "?ptrTracksTblLo\n    .word ?TracksTblLo            ; start + $%04x\n"
					+ "?ptrTracksTblHi\n    .word ?TracksTblHi            ; start + $%04x\n" + "?ptrSong\n    .word ?SongData               ; start + $%04x\n", offsetInstrumentPtrTable,
					offsetTrackPtrTableLow, offsetTrackPtrTableHigh, offsetSong));
		} else {
			strCode.append(String.format(
					"__ptrInstrumentTbl\n    dta a(__InstrumentsTable)       ; start + $%04x\n" + "__ptrTracksTblLo\n    dta a(__TracksTblLo)            ; start + $%04x\n"
							+ "__ptrTracksTblHi\n    dta a(__TracksTblHi)            ; start + $%04x\n" + "__ptrSong\n    dta a(__SongData)               ; start + $%04x\n",
					offsetInstrumentPtrTable, offsetTrackPtrTableLow, offsetTrackPtrTableHigh, offsetSong));
		}

		// List of ptrs to instruments
		strCode.append("\n; List of ptrs to instruments\n");
		int[] instrPos = new int[65536];
		strCode.append("?InstrumentsTable");

		for (int i = 0; i < numInstruments; i += 2) {
			int loc = rword(buf, i + offsetInstrumentPtrTable) - start;
			String str;
			if (loc >= firstInstr && loc < firstTrack && loc < len) {
				instrPos[loc] = (i >> 1) + 1;
				str = String.format(isAtasm ? "?Instrument_%d" : "a(?Instrument_%d)", i >> 1);
			} else if (loc == -start) {
				str = isAtasm ? "  $0000" : " a($0000)";
			} else {
				return new Result(false, null);
			}

			sizeIntro += 2; // 2 bytes per used instrument

			strCode.append(isAtasm ? "\n    .word " : "\n    dta ");
			strCode.append(str);
			strCode.append(String.format("\t\t; %s", Song.nameToString(instruments.getName(i >> 1))));
		}
		strCode.append("\n");

		// List of tracks:
		int[] trackPos = new int[65536];
		strCode.append("\n?TracksTblLo");
		for (int i = 0; i < numtrk; i++) {
			int loc = ub(buf, i + offsetTrackPtrTableLow) + (ub(buf, i + offsetTrackPtrTableHigh) << 8) - start;
			strCode.append(i % 8 == 0 ? (isAtasm ? "\n    .byte " : "\n    dta ") : ",");
			if (loc >= firstTrack && loc < offsetSong && loc < len) {
				trackPos[loc] = i + 1;
				strCode.append(String.format(isAtasm ? "<?Track_%02x" : "l(__Track_%02x)", i));
			} else if (loc == -start) {
				strCode.append("$00");
			} else {
				return new Result(false, null);
			}

			sizeIntro += 1; // 1 byte per used track
		}
		strCode.append("\n?TracksTblHi");
		for (int i = 0; i < numtrk; i++) {
			int loc = ub(buf, i + offsetTrackPtrTableLow) + (ub(buf, i + offsetTrackPtrTableHigh) << 8) - start;
			strCode.append(i % 8 == 0 ? (isAtasm ? "\n    .byte " : "\n    dta ") : ",");
			if (loc >= firstTrack && loc < offsetSong && loc < len) {
				strCode.append(String.format(isAtasm ? ">?Track_%02x" : "h(__Track_%02x)", i));
			} else if (loc == -start) {
				strCode.append("$00");
			} else {
				return new Result(false, null);
			}

			sizeIntro += 1; // 1 byte per used track
		}

		strCode.append("\n");

		// First dump all the sequential code, relocated data is dumped after this
		if (strInstrumentsLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildInstrumentData("", buf, firstInstr, firstTrack, instrPos, assemblerFormat);
			sizeInstruments = r.size();
			strCode.append(r.code());
			strModuleSequence.append("  Instruments\n");
		}
		if (strTracksLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildTracksData("", buf, firstTrack, offsetSong, trackPos, assemblerFormat);
			sizeTrack = r.size();
			if (sizeTrack == 0) {
				return new Result(false, null);
			}
			strCode.append(r.code());
			strModuleSequence.append("  Tracks\n");
		}
		if (strSongLinesLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildSongData("", buf, offsetSong, len, start, numTracks, assemblerFormat);
			sizeSongLines = r.size();
			strCode.append(r.code());
			strModuleSequence.append("  Song Lines\n");
		}

		// Dump the relocated data
		if (!strInstrumentsLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildInstrumentData(strInstrumentsLabel, buf, firstInstr, firstTrack, instrPos, assemblerFormat);
			sizeInstruments = r.size();
			strCode.append(r.code());
			strModuleSequence.append("Relocated Instruments\n");
		}
		if (!strTracksLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildTracksData(strTracksLabel, buf, firstTrack, offsetSong, trackPos, assemblerFormat);
			sizeTrack = r.size();
			if (sizeTrack == 0) {
				return new Result(false, null);
			}
			strCode.append(r.code());
			strModuleSequence.append("Relocated Tracks\n");
		}
		if (!strSongLinesLabel.isEmpty()) {
			AsmFileBuilder.Result r = AsmFileBuilder.buildSongData(strSongLinesLabel, buf, offsetSong, len, start, numTracks, assemblerFormat);
			sizeSongLines = r.size();
			strCode.append(r.code());
			strModuleSequence.append("Relocated Song Lines\n");
		}

		String code = strCode.toString().replace("{{byte}}", byteDirective).replace("?", "__");
		if (assemblerFormat == AssemblerFormat.XASM) {
			code = code.replace(".local", "");
		}

		if (wantSizeInfoOnly) {
			String sizeInfo = String.format("Header\t\t= $%04x (%d) bytes\nInstruments\t= $%04x (%d) bytes\nTracks\t\t= $%04x (%d) bytes\nSong Lines\t= $%04x (%d) bytes\n", sizeIntro, sizeIntro,
					sizeInstruments, sizeInstruments, sizeTrack, sizeTrack, sizeSongLines, sizeSongLines);
			return new Result(true, (sizeInfo + strModuleSequence).replace("\n", "\r\n"));
		}
		return new Result(true, code);
	}

	/**
	 * Builds the assembler "feature" declarations block (which RMTPLAYER.asm
	 * features the module actually needs), based on scanning every saved
	 * instrument's envelope/parameters plus which instruments/speeds are
	 * used on which channel. C++'s {@code trackSavedFlags} parameter is
	 * dropped - see this class's javadoc.
	 */
	public static String composeRMTFEATstring(Song song, Instruments instruments, Tracks tracks, int tracks4_8, String filename, byte[] instrumentSavedFlags, boolean soundFXSupport,
			boolean globalVolumeFade, boolean noStartingSongLine, AssemblerFormat assemblerFormat) {
		String equal = (assemblerFormat == AssemblerFormat.ATASM) ? "=" : "equ";

		StringBuilder dest = new StringBuilder(String.format(";* --------BEGIN--------\n;* %s\n", filename));

		int[] usedCommand = new int[8];
		int usedCommand7VolumeOnly = 0;
		int[] usedCommand7VolumeOnlyOnChannelX = new int[8];
		int usedCommand7SetNote = 0;
		int usedPortamento = 0;
		int usedFilter = 0;
		int[] usedFilterOnChannelX = new int[8];
		int usedBass16 = 0;
		int[] usedBass16OnChannelX = new int[8];
		int usedTableType = 0;
		int usedTableMode = 0;
		int usedTableGoto = 0;
		int usedAudctlManualSet = 0;
		int usedVolumeMin = 0;
		int usedEffectVibrato = 0;
		int usedEffectFrequencyShift = 0;
		int howManySpeedChanges = 0;

		int[][] instrumentUsedOnChannelX = new int[Instruments.INSTRSNUM][Song.SONGTRACKS];

		for (int songLineNr = 0; songLineNr < Song.SONGLEN; songLineNr++) {
			if (song.getSongGo()[songLineNr] >= 0) {
				continue; // goto line
			}
			for (int channelNr = 0; channelNr < tracks4_8; channelNr++) {
				int trackNr = song.getSong()[songLineNr][channelNr];
				if (trackNr < 0 || trackNr >= Tracks.TRACKSNUM) {
					continue;
				}
				Track tt = tracks.getTrack(trackNr);
				for (int i = 0; i < tt.len; i++) {
					int instrumentNr = tt.instr[i];
					if (instrumentNr >= 0 && instrumentNr < Instruments.INSTRSNUM) {
						instrumentUsedOnChannelX[instrumentNr][channelNr]++;
					}
					if (tt.speed[i] >= 0) {
						howManySpeedChanges++;
					}
				}
			}
		}

		for (int instrumentNr = 0; instrumentNr < Instruments.INSTRSNUM; instrumentNr++) {
			if (instrumentSavedFlags[instrumentNr] != 0) {
				Instrument ai = instruments.getInstrument(instrumentNr);

				for (int j = 0; j <= ai.parameters[Instrument.PAR_ENV_LENGTH]; j++) {
					int cmd = ai.envelope[j][EnvelopeParameter.COMMAND] & 0x07;
					usedCommand[cmd]++;
					if (cmd == 7) { // AUDCTL
						if (ai.envelope[j][EnvelopeParameter.X] == 0x08 && ai.envelope[j][EnvelopeParameter.Y] == 0x00) {
							usedCommand7VolumeOnly++;
							for (int channelNr = 0; channelNr < tracks4_8; channelNr++) {
								if (instrumentUsedOnChannelX[instrumentNr][channelNr] != 0) {
									usedCommand7VolumeOnlyOnChannelX[channelNr]++;
								}
							}
						} else {
							usedCommand7SetNote++;
						}
					}

					if (ai.envelope[j][EnvelopeParameter.PORTAMENTO] != 0) {
						usedPortamento++;
					}

					if (ai.envelope[j][EnvelopeParameter.FILTER] != 0) {
						usedFilter++;
						for (int channelNr = 0; channelNr < tracks4_8; channelNr++) {
							if (instrumentUsedOnChannelX[instrumentNr][channelNr] != 0) {
								usedFilterOnChannelX[channelNr]++;
							}
						}
					}

					if (ai.envelope[j][EnvelopeParameter.DISTORTION] == 6) {
						usedBass16++;
						for (int channelNr = 0; channelNr < tracks4_8; channelNr++) {
							if (instrumentUsedOnChannelX[instrumentNr][channelNr] != 0) {
								usedBass16OnChannelX[channelNr]++;
							}
						}
					}
				}
				if (ai.parameters[Instrument.PAR_TBL_TYPE] != 0) {
					usedTableType++;
				}
				if (ai.parameters[Instrument.PAR_TBL_MODE] != 0) {
					usedTableMode++;
				}
				if (ai.parameters[Instrument.PAR_TBL_GOTO] != 0) {
					usedTableGoto++; // non-zero table go
				}
				if (ai.parameters[Instrument.PAR_AUDCTL_15KHZ] != 0 || ai.parameters[Instrument.PAR_AUDCTL_HPF_CH2] != 0 || ai.parameters[Instrument.PAR_AUDCTL_HPF_CH1] != 0
						|| ai.parameters[Instrument.PAR_AUDCTL_JOIN_3_4] != 0 || ai.parameters[Instrument.PAR_AUDCTL_JOIN_1_2] != 0 || ai.parameters[Instrument.PAR_AUDCTL_179_CH3] != 0
						|| ai.parameters[Instrument.PAR_AUDCTL_179_CH1] != 0 || ai.parameters[Instrument.PAR_AUDCTL_POLY9] != 0) {
					usedAudctlManualSet++;
				}
				if (ai.parameters[Instrument.PAR_VOL_MIN] != 0) {
					usedVolumeMin++;
				}
				if (ai.parameters[Instrument.PAR_DELAY] != 0) { // only when the effect delay is non-zero
					if (ai.parameters[Instrument.PAR_VIBRATO] != 0) {
						usedEffectVibrato++;
					}
					if (ai.parameters[Instrument.PAR_FREQ_SHIFT] != 0) {
						usedEffectFrequencyShift++;
					}
				}
			}
		}

		dest.append(String.format("FEAT_SFX\t\t%s %d\n", equal, soundFXSupport ? 1 : 0));
		dest.append(String.format("FEAT_GLOBALVOLUMEFADE\t%s %d\t\t;RMTGLOBALVOLUMEFADE variable\n", equal, globalVolumeFade ? 1 : 0));
		dest.append(String.format("FEAT_NOSTARTINGSONGLINE\t%s %d\n", equal, noStartingSongLine ? 1 : 0));
		dest.append(String.format("FEAT_INSTRSPEED\t\t%s %d\n", equal, song.getInstrumentSpeed()));
		dest.append(String.format("FEAT_CONSTANTSPEED\t\t%s %d\t\t;(%d times)\n", equal, (howManySpeedChanges == 0) ? song.getMainSpeed() : 0, howManySpeedChanges));

		for (int i = 1; i <= 6; i++) {
			dest.append(String.format("FEAT_COMMAND%d\t\t%s %d\t\t;(%d times)\n", i, equal, (usedCommand[i] > 0) ? 1 : 0, usedCommand[i]));
		}

		appendFeature(dest, equal, "FEAT_COMMAND7SETNOTE", usedCommand7SetNote);
		appendFeature(dest, equal, "FEAT_COMMAND7VOLUMEONLY", usedCommand7VolumeOnly);
		appendFeature(dest, equal, "FEAT_PORTAMENTO", usedPortamento);
		appendFeature(dest, equal, "FEAT_FILTER", usedFilter);
		appendFeature(dest, equal, "FEAT_FILTERG0L", usedFilterOnChannelX[0]);
		appendFeature(dest, equal, "FEAT_FILTERG1L", usedFilterOnChannelX[1]);
		appendFeature(dest, equal, "FEAT_FILTERG0R", usedFilterOnChannelX[0 + 4]);
		appendFeature(dest, equal, "FEAT_FILTERG1R", usedFilterOnChannelX[1 + 4]);
		appendFeature(dest, equal, "FEAT_BASS16", usedBass16);
		appendFeature(dest, equal, "FEAT_BASS16G1L", usedBass16OnChannelX[1]);
		appendFeature(dest, equal, "FEAT_BASS16G3L", usedBass16OnChannelX[3]);
		appendFeature(dest, equal, "FEAT_BASS16G1R", usedBass16OnChannelX[1 + 4]);
		appendFeature(dest, equal, "FEAT_BASS16G3R", usedBass16OnChannelX[3 + 4]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG0L", usedCommand7VolumeOnlyOnChannelX[0]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG2L", usedCommand7VolumeOnlyOnChannelX[2]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG3L", usedCommand7VolumeOnlyOnChannelX[3]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG0R", usedCommand7VolumeOnlyOnChannelX[0 + 4]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG2R", usedCommand7VolumeOnlyOnChannelX[2 + 4]);
		appendFeature(dest, equal, "FEAT_VOLUMEONLYG3R", usedCommand7VolumeOnlyOnChannelX[3 + 4]);
		appendFeature(dest, equal, "FEAT_TABLETYPE", usedTableType);
		appendFeature(dest, equal, "FEAT_TABLEMODE", usedTableMode);
		appendFeature(dest, equal, "FEAT_TABLEGO", usedTableGoto);
		appendFeature(dest, equal, "FEAT_AUDCTLMANUALSET", usedAudctlManualSet);
		appendFeature(dest, equal, "FEAT_VOLUMEMIN", usedVolumeMin);
		appendFeature(dest, equal, "FEAT_EFFECTVIBRATO", usedEffectVibrato);
		appendFeature(dest, equal, "FEAT_EFFECTFSHIFT", usedEffectFrequencyShift);

		dest.append(";* --------END--------\n");

		return dest.toString().replace("\n", "\r\n");
	}

	private static void appendFeature(StringBuilder dest, String equal, String label, int var) {
		dest.append(String.format("%s\t\t%s %d\t\t;(%d times)\n", label, equal, (var > 0) ? 1 : 0, var));
	}

	private static int ub(byte[] buf, int i) {
		return buf[i] & 0xFF;
	}

	private static int rword(byte[] buf, int i) {
		return ub(buf, i) | (ub(buf, i + 1) << 8);
	}
}
