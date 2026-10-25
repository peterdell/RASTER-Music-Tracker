# The test songs

The input modules the session, audio, playability, script and UI tests
open (plan 35), addressed through the `TestSongs.java` constants. The
layout rule (plan 56):

- The top level is the version bucket, as in `rmt/songs`: `rmt128/`
  (plays with RMT 1.28) versus `rmt134/` (needs 1.34 or newer).
- Below it, one folder per song holding the `.rmt`, its README
  metadata sheet (author, source, speeds - metadata is a README line,
  not a path) and any committed reference exports.
- A real song's folder is named after the song: `delta`,
  `captain_future_theme`.
- A module from a GitHub issue is named `issue_<n>_<primary problem>`
  (`issue_30_two_tone_filter`, `issue_5_stripped_sfx_export`) - the
  names of files uploaded to issues are typically meaningless, so the
  issue and its problem are the identity, and the committed file
  inside keeps its uploaded name for traceability to the attachment.
  The bucket is the version line the issue was reported against; every
  current GitHub issue falls under `rmt134`.
