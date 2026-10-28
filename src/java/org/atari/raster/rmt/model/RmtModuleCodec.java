package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The conversions between a {@link Song} and an {@link RmtModule} (plan
 * 60, decision 4: kept out of the container, so that stays reusable
 * without RMT's editing model). They reuse the per-entity codecs RMT has
 * always used - {@code Instruments.instrToAta}/{@code ataToInstr},
 * {@code Tracks.trackToAta}/{@code ataToTrack} - and encode the song lines,
 * the one trivial struct, themselves. The selection rule of
 * {@code SongIO.makeModule} is decided here: {@link SongIOType#RMT} keeps
 * every non-empty track and instrument, any other type only the used ones.
 *
 * <p>Until plan 60 M2, {@code SongIO.makeModule}/{@code decodeModule} stay
 * the program's codec; the tests prove the two agree byte for byte.
 */
public final class RmtModuleCodec {

	private RmtModuleCodec() {
	}

	/**
	 * The module of the song: with {@link SongIOType#RMT} every non-empty
	 * track and instrument, otherwise only the tracks the song plays and the
	 * instruments those tracks use - {@code SongIO.makeModule}'s rule.
	 *
	 * @throws RmtModule.FormatException for a track too event-dense to encode in 256 bytes (the one failure {@code makeModule} knows), or a song speed/instrument speed outside the header's range
	 */
	public static RmtModule fromSong(Song song, SongIOType iotype, int tracks4_8) {
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		song.markTfUsed(trackSavedFlags, tracks4_8); // mark all tracks as used
		if (iotype == SongIOType.RMT) {
			song.markTfNoEmpty(trackSavedFlags); // in addition to the used ones, all non-empty tracks
		}
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (trackSavedFlags[i] > 0) {
				Track track = song.tracks.getTrack(i);
				for (int j = 0; j < track.len; j++) {
					if (song.tracks.isValidInstrument(track.instr[j])) {
						instrumentSavedFlags[track.instr[j]] = Instrument.IF_USED;
					}
				}
			}
		}
		if (iotype == SongIOType.RMT) {
			for (int i = 0; i < Instruments.INSTRSNUM; i++) {
				if (song.instruments.calculateNotEmpty(i)) {
					instrumentSavedFlags[i] |= Instrument.IF_NOEMPTY;
				}
			}
		}

		boolean stereo = song.isStereo(tracks4_8);
		byte[] scratch = new byte[256];
		Map<Integer, RmtModule.Instrument> instruments = new TreeMap<>();
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			if (instrumentSavedFlags[i] != 0) {
				int length = song.instruments.instrToAta(i, scratch, stereo);
				instruments.put(i, RmtModule.Instrument.parse(scratch, 0, length));
			}
		}
		Map<Integer, RmtModule.Track> tracks = new TreeMap<>();
		for (int i = 0; i < Tracks.TRACKSNUM; i++) {
			if (trackSavedFlags[i] != 0) {
				int length = song.tracks.trackToAta(i, scratch);
				if (length < 1) {
					throw new RmtModule.FormatException("Track " + i + " has too many events to encode.");
				}
				tracks.put(i, RmtModule.Track.of(scratch, 0, length));
			}
		}

		// The song lines up to the last one with a go to or a track - the
		// lines SongIO.songToAta counts.
		List<RmtModule.SongLine> lines = new ArrayList<>();
		int last = -1;
		for (int line = 0; line < Song.SONGLEN; line++) {
			if (song.songGo[line] >= 0) {
				lines.add(RmtModule.SongLine.goTo(song.songGo[line]));
				last = line;
			} else {
				int[] numbers = new int[tracks4_8];
				for (int channel = 0; channel < tracks4_8; channel++) {
					int number = song.song[line][channel];
					numbers[channel] = number >= 0 && number < Tracks.TRACKSNUM ? number : RmtModule.SongLine.EMPTY;
				}
				RmtModule.SongLine songLine = RmtModule.SongLine.tracks(numbers);
				lines.add(songLine);
				if (songLine.hasTracks()) {
					last = line;
				}
			}
		}
		return new RmtModule(tracks4_8, song.tracks.getMaxTrackLength(), song.mainSpeed, song.instrumentSpeed, RmtFormatVersion.V1, instruments, tracks, lines.subList(0, last + 1));
	}

	/** The {@code .rmt} file of the song as RMT saves it: the module with every non-empty track and instrument, and the names segment. */
	public static RmtModuleFile fileFromSong(Song song, int tracks4_8, int loadAddress) {
		RmtModule module = fromSong(song, SongIOType.RMT, tracks4_8);
		List<String> instrumentNames = new ArrayList<>();
		for (int number : module.instruments().keySet()) {
			instrumentNames.add(Song.nameToString(song.instruments.getName(number)).stripTrailing());
		}
		return new RmtModuleFile(module, loadAddress, song.getName(), instrumentNames);
	}

	/**
	 * The module into the song - {@code SongIO.decodeModule}'s effect: the
	 * channel count, track length and speeds, every instrument and track
	 * decoded in place, the song lines set; the lines beyond the module's
	 * are left as they are, as {@code decodeModule} leaves them. Returns
	 * the song's {@code tracks4_8}, the module's channel count.
	 *
	 * @throws RmtModule.FormatException if an instrument's or a track's bytes do not decode
	 */
	public static int toSong(RmtModule module, Song song) {
		int tracks4_8 = song.setTracks(module.channels());
		song.tracks.setMaxTrackLength(module.trackLength());
		song.mainSpeed = module.songSpeed();
		song.instrumentSpeed = module.instrumentSpeed();
		song.tracks.initTracks();
		boolean stereo = song.isStereo(tracks4_8);
		for (Map.Entry<Integer, RmtModule.Instrument> entry : module.instruments().entrySet()) {
			if (!song.instruments.ataToInstr(entry.getValue().toBytes(), entry.getKey(), stereo)) {
				throw new RmtModule.FormatException("Instrument " + entry.getKey() + " does not decode.");
			}
			song.instruments.update(entry.getKey());
		}
		for (Map.Entry<Integer, RmtModule.Track> entry : module.tracks().entrySet()) {
			RmtModule.Track track = entry.getValue();
			if (!song.tracks.ataToTrack(track.toBytes(), track.length(), entry.getKey())) {
				throw new RmtModule.FormatException("Track " + entry.getKey() + " does not decode.");
			}
		}
		// SongIO.ataToSong's rules: a go to beyond the complete lines (a
		// final stereo go to line is not one) or beyond the song jumps to 0.
		List<RmtModule.SongLine> lines = module.songLines();
		int completeLines = module.songDataLength() / tracks4_8;
		for (int line = 0; line < lines.size() && line < Song.SONGLEN; line++) {
			RmtModule.SongLine songLine = lines.get(line);
			if (songLine.isGoTo()) {
				int target = songLine.goToTarget();
				song.songGo[line] = target < completeLines && target < Song.SONGLEN ? target : 0;
			} else {
				for (int channel = 0; channel < tracks4_8; channel++) {
					int number = songLine.trackNumber(channel);
					song.song[line][channel] = number < Tracks.TRACKSNUM ? number : -1;
				}
			}
		}
		return tracks4_8;
	}

	/** The names of a non-stripped file into the song, space-padded as {@code SongIO.loadRMT} stores them; a stripped file leaves them alone. */
	public static void namesToSong(RmtModuleFile file, Song song) {
		if (file.isStripped()) {
			return;
		}
		fillName(song.songName, file.songName());
		int i = 0;
		for (int number : file.module().instruments().keySet()) {
			fillName(song.instruments.getName(number), file.instrumentNames().get(i++));
		}
	}

	private static void fillName(char[] target, String name) {
		Arrays.fill(target, ' ');
		name.getChars(0, Math.min(name.length(), target.length), target, 0);
	}
}
