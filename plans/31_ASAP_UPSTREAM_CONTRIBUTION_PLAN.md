# Plan 31: Contribute RMT's ASAP changes upstream

Status: IN PROGRESS. A1 done 2026-10-04; A2-A6 open.

Offer the changes the Java port made to ASAP (Another Slight Atari Player)
back to its author, Piotr Fusik ("Fox"), as merge requests on SourceForge,
so the port can carry fewer local patches.

## 1. Starting point

The port's adapted copy lives in `src/java/net/sf/asap/`, unpacked from
`lib/java/asap-8.0.0-java-src.zip` (the official 8.0.0 Java build, which
Fox provided) and then hand-edited. Upstream is `p/asap/code` on
SourceForge; the personal fork is `u/peterdell/asap`.

Two unrelated things that came up while establishing this and are *not*
part of this plan:

- The POKEY dump in the working tree of the `asap-code` clone
  (`asap.fu`/`pokey.fu`, uncommitted, April 2026) belongs to the
  asma.atari.org project, which builds its own jar from it. See that
  project's own `ASAP.md`.
- The fork's only own commit, "Fix POKEY initialization", is already
  upstream with a byte-identical patch. The fork clone is therefore
  disposable; only the fork on SourceForge matters, since a merge request
  has to come from there.

## 2. The obstacle that shapes the plan

Upstream cannot take a Java diff. The port's changes are hand edits to
*generated* Java, in files whose first line says not to edit them. Fox
regenerates those from the portable Fusion (`.fu`) sources, so every change
has to be re-expressed in `asap.fu`/`pokey.fu` and regenerated. This is a
translation job, not a cherry-pick.

Upstream also tracks the generated `asap.c` and `asap.h` as files in the
repository, so a source-only commit would leave it inconsistent. The
transpiler is available locally (`fut`, Fusion Transpiler 3.2.13).

## 3. What the port actually changed

Measured by diffing `src/java/net/sf/asap/` against the pristine archive.
Two files, four hunks, and not one upstream line deleted or rewritten:

| Change | Size | Nature |
|---|---|---|
| `skctl` widened to package-private in `Pokey.java` | 1 line | artifact of patching generated code |
| two guards inserted in `peekHardware`/`pokeHardware` | 6 lines | RMT mode only |
| appended block: 7 public methods, 1 flag, 1 constant | 161 lines | the real content |

The seven methods split into two groups.

**General-purpose (offer first):**

- `stepFrame()` - calls the existing private frame routine, so callers get
  exact frame boundaries. The public `generate` API cannot promise them,
  because its own frame stepping is a buffering detail.
- `getPokeyRegisterShadow(chip, offset)` - the last byte written to an
  audio register, read from the channel frequency and control fields, the
  control register and the serial control register. This answers a real
  gap: those registers are write-only in hardware, so upstream has no way
  to read back what was poked, which is exactly what a per-frame register
  dump needs.

**RMT-specific (offer separately, if at all):**

- `rmtInitialize`, `rmtMemory`, `rmtJsr`, `rmtPokeRegister`, `rmtRender`,
  the `rmtMode` flag, the halt-address constant, and the two guards in
  `peekHardware`/`pokeHardware`. Together they run a foreign driver binary
  on the processor with the hardware page as plain memory, and render from
  the registers the driver stores there. A much larger ask, because it
  changes what the hardware page means.

## 4. Steps

- **A1** (DONE 2026-10-04) Settle the fork's baseline, so that anything
  offered later is a difference against current upstream rather than
  against a 2025 snapshot.

  The clone now at `C:\jac\system\Fusion\Programming\Repositories\Tools\asap` was
  given an `upstream` remote for `p/asap/code` and reset to
  `upstream/master` (`28af663`, 2026-09-22), which took it from 6.0.3 to
  8.0.0. The fork's `master` was force-pushed to the same commit and its
  `dev` branch deleted; `android-background` was left alone, matching
  upstream. The only commit dropped was `442f924`, which Fox already has
  as `50b7639` with a byte-identical patch. Local, fork and upstream now
  agree.

  Pushing to SourceForge needs SSH: HTTPS is read-only there. The push
  URL of `origin` is `ssh://git.code.sf.net/u/peterdell/asap`, authorized
  by an Ed25519 key (`~/.ssh/sourceforge`, registered on the account,
  passphrase protected, selected through a `~/.ssh/config` host block).
  A successful connection answers "Interactive git shell is not enabled",
  which is `git-shell` refusing a login, not a failure.
- **A2** Re-express `stepFrame` and `getPokeyRegisterShadow` in `asap.fu`.
  The first is a one-line wrapper. The second reads the channel and control
  fields directly. The `skctl` widening then disappears: the transpiler
  decides generated visibility, so a correctly placed accessor needs no
  hand edit.
- **A3** Regenerate and commit `asap.c`/`asap.h` alongside the sources.
- **A4** Verify against this repository before sending anything: build
  `java/asap.jar` from the regenerated sources, drop it into the port in
  place of the patched copy, and run the suite. `LivePlaybackTest`, which
  plays an exported module against ASAP's independent emulation, is the
  test that would catch a translation slip.
- **A5** Push the branch to the fork and open the merge request from the
  fork's Git page against `master` in `p/asap/code`.
- **A6** Only once A1-A5 have landed or been answered, repeat for the
  RMT-mode set as a second, separate merge request.

## 5. Why it is worth doing

If Fox takes the general-purpose pair, the port drops two of its patches
and the `Pokey.java` edit entirely, leaving only the RMT-mode block to
carry. That gain holds even if the second request goes nowhere, which is
why the two are kept apart.
