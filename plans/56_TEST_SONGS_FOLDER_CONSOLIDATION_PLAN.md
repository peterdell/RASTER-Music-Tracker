# Plan 56: consolidate the test-resources/songs folder structure

Status: DONE 2026-10-25. The user decided for option 2 with one
modification: the version buckets `rmt128`/`rmt134` stay the top level
as before, and an issue fixture's bucket is the version line its issue
was reported against - every current GitHub issue falls under
`rmt134`. That removes option 2's classification cost (no research
into a module's own compatibility needed). Executed the same day:
`rmt134/issue_30_two_tone_filter` (was `makary_two_tone_filter`),
`rmt134/issue_5_stripped_sfx_export` (was `issues/issue5_sfx_only`;
the stray `issues/` branch is gone), the `TestSongs` constants renamed
to `ISSUE_30_TWO_TONE_FILTER`/`ISSUE_5_STRIPPED_SFX_EXPORT`, and the
layout rule recorded in `test-resources/songs/README.md`. The
rmtscript paths were untouched (they open `delta` and
`why_do_you_dance_with_me` only).

## The problem

`test-resources/songs/` nests inconsistently. Plan 35 set up version
buckets (`rmt128/`, `rmt134/` - compatibility, not creation version,
repeating `rmt/songs`' convention) with one song-name folder per song:

    rmt128/delta/            rmt134/why_do_you_dance_with_me/
    rmt128/rebbstars/        rmt134/captain_future_theme/
                             rmt134/makary_two_tone_filter/

Issue 30's module fit that shape; issue 5's module did not: an SFX-only
stripped file has no natural version bucket, so it landed in a third,
differently-shaped branch, `issues/issue5_sfx_only/`. Every future
fixture poses the same classification question, and plan 35 itself
already had one uncertain case (`why_do_you_dance_with_me` sits in
`rmt134` on a "to be checked" driver claim).

What the structure must keep: one folder per song holding the `.rmt`,
its README metadata sheet and any committed reference exports (the
Captain Future pattern); `scripts/` and `ui-reference/` untouched.
References that move with any rename: `TestSongs.java` (six
constants), three `open` paths in `test-resources/scripts/*.rmtscript`,
and the issue-5 README's own path mention. `TestSongsTest`'s
byte-comparison against `rmt/songs/rmt134/buddy/` is unaffected -
`rmt/songs` is the shipped browsing collection with its own layout
(version buckets AND author folders) and is not part of this
consolidation.

## The naming rule (applies to every option)

Two kinds of fixtures, two name sources:

- A real song is named after itself: `delta`, `rebbstars`,
  `captain_future_theme`, `why_do_you_dance_with_me`.
- An issue fixture is named `issue_<n>_<primary problem>` - the names
  of files uploaded to issues are typically meaningless (`sync.rmt`,
  `testsfx.rmt`), so the issue number and its primary problem are the
  identity; the uploaded file name and the issue link are README
  lines. Issue 30's folder becomes `issue_30_two_tone_filter`, issue
  5's `issue_5_stripped_sfx_export` (the stripped-RMT export with SFX
  support destroyed the module). The prefix makes the provenance
  visible in every path and listing, groups the issue fixtures
  together even in a flat folder, and replaces the author prefix of
  `makary_two_tone_filter` - the author is a README line like
  everywhere else.

The committed file inside keeps its uploaded name (`testsfx.rmt`,
`Makary_Two_Tone_Filter.rmt`): the folder names the issue and its
problem, the file stays traceable to the issue attachment.

## Option 1 - flat folders (RECOMMENDED)

    test-resources/songs/captain_future_theme/
                         delta/
                         issue_5_stripped_sfx_export/
                         issue_30_two_tone_filter/
                         rebbstars/
                         why_do_you_dance_with_me/

One level, folder = song name or problem name, nothing else in the
path. The compatibility version and the provenance (author, issue,
source URL) live in each folder's README, where they already are - the
same argument plan 35 used against an author level applies to the
version level: for test fixtures it is a README line, not a path. Six
fixtures do not need a taxonomy, and the one inconsistency to date
(`issues/`) arose exactly because the buckets could not place a
fixture. The divergence from `rmt/songs`' layout is accepted: that
folder is a shipped collection users browse (where buckets and author
folders help), this one is internal fixtures addressed through
`TestSongs.java` constants; the two already diverge (no author level
here).

Migration: six `git mv`, the six `TestSongs.java` constants (the issue
ones named like the folders, `ISSUE_5_STRIPPED_SFX_EXPORT` and
`ISSUE_30_TWO_TONE_FILTER`), three rmtscript paths, the issue-5 README
path line.

## Option 2 - keep the version buckets, dissolve `issues/`

    rmt128/delta/  rmt128/rebbstars/  rmt128/issue_5_stripped_sfx_export/
    rmt134/why_do_you_dance_with_me/  rmt134/captain_future_theme/
    rmt134/issue_30_two_tone_filter/

The smallest structural change: issue 5's module demonstrably worked
in RMT 1.28, so it has a bucket. Keeps plan 35's "one layout rule"
congruence with `rmt/songs`. The cost returns with every future
fixture: a synthetic, broken or stripped module must first be
researched into a bucket - and a problem-named fixture in a version
bucket reads oddly, because its identity has nothing to do with a
version. Two-level nesting stays for a six-fixture collection.

## Option 3 - purpose buckets

    songs/music/...  songs/issues/...

Group by why the file is here. The `issue_<n>_` prefix makes this
bucket fully redundant: `issues/` in front of
`issue_5_stripped_sfx_export` says the same thing twice, and dropping
the prefix inside the bucket would hide the issue number from every
listing and constant that uses the folder name. The naming rule does
the work; the bucket repeats it.

## Option 4 - flat files, no per-song folders

    songs/Delta.rmt + Delta.md ...

The fewest levels, but it breaks the folder-per-song pattern the
structure must keep: delta alone carries four reference files beside
the module, captain_future_theme and issue_30_two_tone_filter their
reference XEXs. Not viable; listed for completeness.

## Ranking

1. Option 1 - flat folders with the two-source naming rule: consistent
   by construction, no future classification questions, and the
   `issue_<n>_<problem>` name carries the identity and provenance an
   issue file never had.
2. Option 2 - consistent buckets: smallest diff, keeps the plan-35
   rule, but keeps the classification question that produced the
   inconsistency, and version buckets say nothing about issue-named
   fixtures.
3. Option 3 - purpose buckets: fully redundant once the `issue_<n>_`
   prefix names the kind.
4. Option 4 - flat files: breaks the reference-export pattern.

Plan 35 stays as the historical record of the original move; if option
1 is chosen, this plan supersedes its version-bucket decision for
`test-resources/songs` (and only there).
