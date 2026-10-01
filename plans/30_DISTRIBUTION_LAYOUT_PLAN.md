# Plan: the distribution layout of the Java port (D1-D4)

Status: DONE 2026-10-02 (D2-D4; D1 dropped, see decision 5.4); every decision settled (section 5). D1 was done and
then reverted on 2026-10-01 - see decision 5.4: the binaries stay beside
the jar, as files.

The user's problem with the jpackage releases: the content a user opens
through a file chooser (songs, instruments) is buried inside the
application, and writing `rmt.ini` there fails because the application
folder is not a place a program may write. The user's two premises:

- the content folders ship **as is in the download**, present the moment
  the archive is unpacked - nothing is copied at runtime;
- the files under `resources/` are vital and hardly change, so they
  travel **with the application**, and it must stay possible to **drop in
  a different driver binary by hand for testing** (decision 5.4: as files
  beside the jar, not inside it).

## 1. Where things are today

`jpackage --type app-image --input stage` puts everything from the
staging folder next to the jar. Verified on 2026-10-01 against the
artifacts of workflow run 36791292509:

| Platform | The jar, and with it every staged folder |
|---|---|
| Windows | `rmt\app\` beside `rmt\rmt.exe`, runtime in `rmt\runtime\` |
| Linux | `rmt/lib/app/` beside `rmt/bin/rmt` |
| macOS | `rmt.app/Contents/app/`, in a bundle that also carries `_CodeSignature` |

So `rmt.ini`, `tuning.ini`, `songs/`, `instruments/`, `exports/`,
`docs/` and `resources/` all sit in that one folder, which
`RmtApplication.getInstallFolder()` returns and `ProgramFolder` resolves
everything from.

Why that is wrong for two of the three kinds of content: on macOS a write
inside `Contents` invalidates the ad-hoc signature the bundle really
carries, and Finder does not let a user browse into a bundle; on Windows
an install under Program Files needs elevation and may be redirected into
VirtualStore, which looks like a setting that silently did not stick.

What is actually read from `resources/`, in full: seven driver binaries
(`RmtAtariBinaries.getTrackerDriverBinary`) and one VU player
(`SapFileExporter.vuPlayerPath`) - eight `.obx` files, 48 KB. `docs/` is
read in one place, `RmtCommands` opening `rmt_en.html` for the local
help; it is 409 KB of generated HTML and images.

## 2. The three layers

| Layer | Content | Size | Why there |
|---|---|---|---|
| Inside the application image | the 8 `.obx` files under `resources/`, the generated `docs/` | 48 KB + 409 KB | read-only and vital; they travel with the application when it is moved, and a driver can still be replaced in place |
| Beside the application | `songs/`, `instruments/`, `exports/`, `rmt.ini`, `tuning.ini` | 2.3 MB | the user's own content and settings: browsable, writable, unpacked as is |

The result is the `rmt/` layout the C++ distribution already ships, with
the application added beside it, so both programs' downloads look alike
again.

### 2.1 Replacing a driver by hand (the user's testing requirement)

The binaries stay what they are today: files under `resources/drivers`
and `resources/players` next to the jar, inside the application image.
Replacing one means writing the file and starting the program; deleting
it again restores the shipped one. That is exactly `Rmt.exe`'s
mechanism, so the two programs keep one explanation.

On macOS that folder sits inside `rmt.app`, reachable through Show
Package Contents, and writing there invalidates the bundle's ad-hoc
signature. The user does not test driver replacements on macOS
(2026-10-01), so this does not weigh against the simpler arrangement.

### 2.2 Finding the content root

The content root can no longer be recognised by `resources/` - that moved
into the jar. Instead: start at the jar's folder and walk up at most four
levels, taking the first folder that contains `songs` or `rmt.ini`. One
rule, no platform branch, and it covers all three depths (one level up on
Windows, two on Linux, four out of the macOS bundle) as well as a
development checkout, where it finds `rmt/` as `getResourceRoot()` does
today. `-Drmt.config.dir=` keeps overriding everything, as it does now.

### 2.3 When there is no content root

The macOS convention is to drag `rmt.app` into Applications, which leaves
the siblings behind. Because the drivers are in the jar, the program then
**still starts and plays** - only the content is missing:

- `rmt.ini`/`tuning.ini` go to the per-user configuration folder
  (`%APPDATA%\RMT`, `~/Library/Application Support/RMT`,
  `$XDG_CONFIG_HOME/rmt` or `~/.config/rmt`);
- the file choosers start in the user's documents folder;
- the About box names both the folder that was expected and the one in
  use, so the cause is discoverable (decision 5.2: no message box and no
  permanent status bar line - nothing interrupts a normal start).

This is a fallback, not the normal path: nothing is ever copied there.

## 3. Batches

### D1 - (dropped)

The binaries were briefly copied into the jar, with the file next to the
program kept as an override (commit "The Atari binaries travel in the
jar"). Reverted the same day: the application image already carries them
when it is moved, which was the only robustness the jar added, while
in-place replacement became awkward on macOS and the download carried the
48 KB twice. `RmtAtariBinaries` kept the tidier shape of that batch -
`getVUPlayerBinary()` beside `getTrackerDriverBinary()`, so both load the
same way - and `RmtAtariBinariesTest` now covers the replacement.

### D2 - the archive layout - DONE 2026-10-01

`build/stage_java_release.sh` splits into what goes into `--input` (the
jar, `docs/`) and what goes beside the application (`songs/`,
`instruments/`, `exports/`, the two ini files). The workflow's archive
steps copy the latter next to the produced image before packing: into the
image folder on Windows and Linux, beside `rmt.app` on macOS, where
nothing may enter the bundle. The six assets keep their names.

### D3 - the content root and the fallback - DONE 2026-10-02

`ProgramFolder` gains the walk of 2.2 and a configuration root; `RmtConfig`
resolves the two ini files from the latter, `SongFiles`'s default song and
instrument paths from the former. `ProgramFolderTest` grows cases for the
three bundle depths (simulated with temporary folders) and for the
fallback. The ini **content** does not change, so `RmtConfigTest` and the
cross-program comparison stay as they are.

### D4 - documentation - DONE 2026-10-02

`build/release-README.txt`, staged as `README.txt` beside the
application: keep the folder together, what each folder is, the macOS
warning about dragging `rmt.app` out alone, and where to put a driver
build for testing (per platform, since the path into the application
image differs). The manual gained a "Files and Folders" section, and
`doc/rmt_changes.md` a 1.36 entry; `plans/README.md`, `NOTES.md`.

## 4. What this does not change

`Rmt.exe` and its zip keep their layout: the C++ program reads everything
from its program folder, which is exactly what the unpacked archive
provides. The export comparison runs both programs from a checkout, where
the content root is `rmt/` as before. No `rmt.ini` key changes, so an
existing configuration keeps working.

## 5. Decisions (all settled 2026-10-01)

1. **`exports/`** ships beside the application, with the songs and
   instruments, under its present name. It is example output - three
   `.xex` from RMT 1.10 and six `.sap` from 1.28, 68 KB - that nothing in
   either program reads, so a new user can hear what RMT produces without
   exporting anything first.
2. **A missing content root stays silent.** The program starts normally
   on the per-user configuration folder; the About box names both the
   folder that was expected and the one actually in use, so the cause is
   discoverable without anything interrupting a normal start. No message
   box, no permanent status bar line.
3. **`docs/` stays inside the application image**, read-only like the
   drivers. The Help menu opens it by path, which works inside a macOS
   bundle, and a user never has to find it.
4. **The `resources/` binaries stay beside the jar, as files** - not
   inside it. The application image travels as a unit, so they survive a
   move either way; keeping them as files preserves in-place replacement
   on the platform the user tests on and keeps the mechanism identical to
   `Rmt.exe`.

## 6. Verification

`mvn -o clean package` per batch; `build/compare_exports.ps1` must stay
identical throughout, since the driver bytes decide every export - that
is the real net for D1. For D2/D3 the proof is manual and cheap: unpack
each of the six archives, start the program, check that Open lands in
`songs`, that the Options survive a restart, and that a driver dropped
into `resources/drivers` is picked up. The macOS case needs the bundle
moved to Applications once.

## 7. Estimate

D1 small (two loaders, a pom entry, tests). D2 small-medium (shell and
workflow, no Java). D3 medium (the walk, the fallback, the tests). D4
small. Half a day to a day in total; D1 is worth doing first and on its
own, because it is the part that makes the program robust.
