package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.model.AsmFileExporter;
import org.atari.raster.rmt.model.AssemblerFormat;
import org.atari.raster.rmt.model.AtariIO;
import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.MessageButtons;
import org.atari.raster.rmt.model.Messages;
import org.atari.raster.rmt.model.SongIOType;

/** {@code FileImport()}/{@code FileExportAs()} flows, headless: the dialogs are the scripted stub, the importers/exporters the real ones. */
class ImportExportTest {

	private static final class RecordingMessages implements Messages.Handler {
		final List<String> log = new ArrayList<>();
		MessageAnswer answer = MessageAnswer.YES;

		@Override
		public void showError(String title, String message) {
			log.add("E:" + title + ":" + message);
		}

		@Override
		public void showWarning(String title, String message) {
			log.add("W:" + title);
		}

		@Override
		public void showInformation(String title, String message) {
			log.add("I:" + title + ":" + message);
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			log.add("Q:" + title);
			return answer;
		}
	}

	@TempDir
	Path dir;
	private RmtSession session;
	private StubSongFilesHost host;
	private RecordingMessages messages;
	private SongFiles files;

	@BeforeEach
	void setUp() {
		session = new RmtSession();
		host = new StubSongFilesHost();
		messages = new RecordingMessages();
		session.messages.setHandler(messages);
		files = new SongFiles(session, host);
	}

	/** The minimal 4-channel "M.K." module ModImporterTest uses: one pattern, one note, one sample. */
	static byte[] minimalMod() {
		byte[] buf = new byte[2116];
		buf[43] = 0x04;
		buf[45] = 0x40;
		buf[950] = 1;
		buf[952] = 0;
		buf[1080] = 'M';
		buf[1081] = '.';
		buf[1082] = 'K';
		buf[1083] = '.';
		buf[1084] = 0x06;
		buf[1085] = (byte) 0xB0;
		buf[1086] = 0x10;
		for (int i = 2109; i < 2116; i += 2) {
			buf[i] = 50;
		}
		return buf;
	}

	/** The minimal TMC block TmcImporterTest uses: song line 0 with track 0 holding one note. */
	static byte[] minimalTmc() {
		byte[] mem = new byte[435];
		mem[0] = (byte) 0xFF;
		mem[30] = 5;
		mem[31] = 1;
		mem[160] = (byte) 0xB0;
		mem[288] = 0x01;
		for (int i = 417; i <= 429; i += 2) {
			mem[i] = (byte) 0xFF;
		}
		mem[432] = 0x01;
		mem[434] = (byte) 0xFF;
		return AtariIO.saveBinaryBlock(mem, 0, 434, true);
	}

	@Test
	void importModRunsTheTwoDialogsAndKeepsOrDiscardsTheResult() throws IOException {
		Path mod = dir.resolve("song.mod");
		Files.write(mod, minimalMod());
		host.answer(mod, SongFiles.FILTER_MOD);
		host.nextImportMod = new SongFiles.ImportModChoice(true, false, false, false, false, false, true, true);
		files.fileImport();

		assertEquals(List.of("open:Import Song File", "importMod:RMT4 with 1,2,3,4 tracks order|RMT8 with 1,4 / 2,3 tracks order", "importFinished:mod"), host.calls);
		assertTrue(host.lastImportInfo.startsWith("4 channels 31 samples ProTracker module detected."), host.lastImportInfo);
		assertTrue(host.lastImportInfo.contains("(Header bytes \"M.K.\".)"));
		assertTrue(host.lastFinishedInfo.startsWith("1 tracks, 1 instruments, "), host.lastFinishedInfo);
		assertTrue(host.lastFinishedInfo.contains("\nOptimization: Loops in "), host.lastFinishedInfo);
		assertTrue(host.lastFinishedInfo.contains("\nOptimization: Cleared "), host.lastFinishedInfo);
		assertEquals(4, session.tracks4_8); // RMT4 chosen
		assertEquals(0, session.song.getSong()[0][0]);
		assertEquals("", session.song.getFilename());
		assertEquals(mod.toString(), host.importedFileName);
		assertEquals(SongFiles.FILTER_MOD, session.exportSettings.lastImportTypeIndex);
		assertTrue(messages.log.isEmpty(), messages.log.toString());

		// the second time the finished box is cancelled: the previous (empty, 4-track) song comes back
		host.calls.clear();
		host.answer(mod, SongFiles.FILTER_MOD);
		host.nextImportMod = new SongFiles.ImportModChoice(false, true, true, true, true, true, false, false); // RMT8 with 1,4 / 2,3
		host.importFinishedOk = false;
		files.fileImport();
		assertEquals(SongFiles.FILTER_MOD, host.lastInitialFilterIndex); // "Restore the last imported file type"
		assertEquals("I:Import...:Module import aborted.", messages.log.get(messages.log.size() - 1));
		assertEquals(4, session.tracks4_8); // ClearSong(originalg_tracks4_8)
		assertEquals(-1, session.song.getSong()[0][0]);
	}

	@Test
	void importModRejectsBadFilesWithTheThreeMessages() throws IOException {
		Path mod = dir.resolve("short.mod");
		Files.write(mod, new byte[10]);
		host.answer(mod, SongFiles.FILTER_MOD);
		files.fileImport();
		assertEquals("E:Error:Bad file format.", messages.log.get(0));
		assertEquals(8, session.tracks4_8); // ParseHeader's ClearSong(8), then FileImport's ClearSong

		byte[] noId = minimalMod();
		noId[1080] = 'X';
		noId[1081] = 'Y';
		noId[1082] = 'Z';
		noId[1083] = '!';
		Files.write(mod, noId);
		host.answer(mod, SongFiles.FILTER_MOD);
		files.fileImport();
		assertTrue(messages.log.get(1).endsWith("but there is \"XYZ!\"."), messages.log.get(1));
		assertTrue(host.calls.stream().noneMatch(c -> c.startsWith("importMod")));
	}

	@Test
	void importTmcRunsTheTwoDialogs() throws IOException {
		Path tmc = dir.resolve("song.tmc");
		Files.write(tmc, minimalTmc());
		host.answer(tmc, SongFiles.FILTER_TMC);
		host.nextImportTmc = new SongFiles.ImportTmcChoice(false, false, false);
		files.fileImport();
		assertEquals(List.of("open:Import Song File", "importTmc", "importFinished:tmc"), host.calls);
		assertTrue(host.lastImportInfo.startsWith("TMC module: "), host.lastImportInfo);
		assertTrue(host.lastFinishedInfo.endsWith(" songlines"), host.lastFinishedInfo);
		assertEquals(4, session.tracks4_8); // only column 0 used: the importer settles on mono
		assertEquals(0, session.song.getSong()[0][0]);

		Files.write(tmc, new byte[] { 1, 2 });
		host.answer(tmc, SongFiles.FILTER_TMC);
		files.fileImport();
		assertEquals("E:Open error:Corrupted TMC file or unsupported format version.", messages.log.get(0));
	}

	@Test
	void importAsksAboutUnsavedChangesAndCanBeCancelled() {
		session.song.getSong()[0][0] = 0;
		new SongInput(session).keyDown(VirtualKey.VK_Z);
		messages.answer = MessageAnswer.CANCEL;
		files.fileImport();
		assertEquals(List.of("Q:Current song has been changed"), messages.log);
		assertTrue(host.calls.isEmpty());
		assertTrue(session.uiState.changes);
	}

	private void openDelta() {
		assertTrue(files.fileOpen(SongFilesTest.DELTA, false));
		assertEquals(0x4000, session.exportSettings.rmtStrippedAddress);
	}

	private Path export(String name, int filterIndex) {
		host.answer(dir.resolve(name), filterIndex);
		files.fileExportAs();
		return SongFiles.ensureFileExtension(dir.resolve(name), SongFiles.EXPORT_FILTERS, filterIndex);
	}

	@Test
	void exportStrippedRmtUsesTheDialogsChoiceAndRemembersIt() throws IOException {
		openDelta();
		host.nextStrippedRmt = new SongFiles.StrippedRmtChoice(0x5000, AssemblerFormat.ATASM, false, true, false);
		Path out = export("delta", 1);
		assertTrue(Files.exists(out));
		assertEquals("exportStrippedRmt", host.calls.get(host.calls.size() - 1));
		assertTrue(host.lastStripped.length() > 0 && host.lastWithSfx.length() >= host.lastStripped.length());
		byte[] data = Files.readAllBytes(out);
		assertEquals((byte) 0xFF, data[0]); // a binary block
		assertEquals(0x00, data[2]);
		assertEquals(0x50, data[3] & 0xFF); // at $5000
		assertEquals(0x5000, session.exportSettings.rmtStrippedAddress);
		assertEquals(AssemblerFormat.ATASM, session.exportSettings.asmFormat);
		assertTrue(session.exportSettings.rmtStrippedGlobalVolumeFade);
		assertEquals(SongIOType.RMTSTRIPPED, session.song.getLastExportIOType());
		assertTrue(messages.log.isEmpty(), messages.log.toString());

		// cancelling the format dialog deletes the already created file and says so
		host.nextStrippedRmt = null;
		Path cancelled = export("cancelled", 1);
		assertFalse(Files.exists(cancelled));
		assertEquals("W:Export aborted", messages.log.get(messages.log.size() - 1));
		assertEquals(1, host.lastInitialFilterIndex); // the last export type preselected
	}

	@Test
	void exportAsmVariants() throws IOException {
		openDelta();
		host.nextAsm = new SongFiles.AsmChoice(2, 1, 2, "TUNE");
		Path out = export("delta", 2);
		String asm = Files.readString(out, SongFiles.TEXT_CHARSET);
		assertTrue(asm.startsWith(";ASM notation source"), asm.substring(0, 40));
		assertTrue(asm.contains("TUNE"));
		assertEquals("TUNE", session.exportSettings.prefixForAllAsmLabels);

		host.nextRelocatableAsm = new AsmFileExporter.RelocatableAsmExportParams("RMT_SONG_DATA", false, true, false, "I", "RMT_SONG_TRACKS", "S", AssemblerFormat.XASM, false, false, false);
		Path reloc = export("delta_reloc", 7);
		String code = Files.readString(reloc, SongFiles.TEXT_CHARSET);
		assertTrue(code.contains("RMT_SONG_DATA"), code.substring(0, Math.min(200, code.length())));
		assertTrue(code.contains("RMT_SONG_TRACKS"));
		assertTrue(session.exportSettings.asmWantRelocatableTracks);
		assertEquals("RMT_SONG_TRACKS", session.exportSettings.asmTracksLabel);
	}

	@Test
	void exportStreamsSapRLzssSapBXexAndWav() throws IOException {
		openDelta();
		host.nextSap = new SongFiles.SapChoice("Me", "Delta", "01/01/2026", "00 05");
		session.uiState.playTime = 77; // the export's register dump plays the song; the play time shown must survive it
		Path sapr = export("delta", 3);
		assertEquals(77, session.uiState.playTime);
		String header = new String(Files.readAllBytes(sapr), 0, 80, SongFiles.TEXT_CHARSET);
		assertTrue(header.startsWith("SAP\r\n"), header);
		assertTrue(header.contains("AUTHOR \"Me\""), header);
		assertTrue(header.contains("TYPE R"), header);
		assertEquals("exportSap:R", host.calls.get(host.calls.size() - 1));
		assertEquals("???", host.lastSapAuthor); // CSAPFile::Init's defaults were offered
		assertEquals("00", host.lastSubsongs);
		assertEquals(2, host.lastSapFile.getSongs());

		Path lzss = export("delta", 4);
		assertTrue(Files.exists(lzss));
		assertTrue(Files.size(lzss) > 16);
		assertTrue(Files.exists(dir.resolve("delta_LOOP.lzss")));

		host.nextSap = new SongFiles.SapChoice("Me", "Delta", "01/01/2026", "0");
		Path sap = export("delta", 5);
		assertTrue(Files.size(sap) > 1000);
		assertEquals("exportSap:B", host.calls.get(host.calls.size() - 1));

		host.nextXex = new SongFiles.XexChoice("Delta\nSTEREO\n01/01/2026\nAuthor: (press SHIFT key)\nAuthor: me", true, false, true, 20);
		Path xex = export("delta", 6);
		assertTrue(Files.size(xex) > 1000);
		assertTrue(host.lastXexText.startsWith(session.song.getName() + "\n\n"), host.lastXexText); // the song's name, the (empty, mono) STEREO line, the date and the two author lines
		assertTrue(host.lastXexText.endsWith("\nAuthor: (press SHIFT key)\nAuthor: ???"), host.lastXexText);
		assertTrue(host.lastSpeedInfo.contains("50 Hz"));
		assertEquals("Delta\nSTEREO\n01/01/2026\nAuthor: (press SHIFT key)\nAuthor: me", session.exportSettings.msxText);
		assertEquals(20, session.exportSettings.msxColor);
		assertFalse(session.exportSettings.msxShuffle);

		Path wav = export("delta", 8);
		byte[] wavData = Files.readAllBytes(wav);
		assertEquals("RIFF", new String(wavData, 0, 4, SongFiles.TEXT_CHARSET));
		assertTrue(wavData.length > 44 + 44100);
		assertEquals(SongIOType.WAV, session.song.getLastExportIOType());
		assertTrue(messages.log.isEmpty(), messages.log.toString());
	}

	@Test
	void exportOfAnEmptySongIsRefusedBeforeAnyDialog() {
		files.fileExportAs();
		assertEquals(List.of("E:Errors:Error: Song is empty.\n", "W:Warning"), messages.log);
		assertTrue(host.calls.isEmpty());
	}

	@Test
	void subsongParsingCountsHexNumbersSeparatedByAnythingElse() {
		assertEquals(1, SongFiles.parseSubsongs("00"));
		assertEquals(3, SongFiles.parseSubsongs("00 0a,1F"));
		assertEquals(0, SongFiles.parseSubsongs(""));
		assertEquals(2, SongFiles.parseSubsongs("x1x2x"));
	}

	@Test
	void exportFiltersMapToTheIoTypes() {
		assertEquals(SongFiles.EXPORT_FILTERS.size(), SongFiles.EXPORT_IO_TYPES.length);
		assertEquals(Path.of("a.sapr"), SongFiles.ensureFileExtension(Path.of("a"), SongFiles.EXPORT_FILTERS, 3));
		assertNull(host.nextStrippedRmt);
	}
}
