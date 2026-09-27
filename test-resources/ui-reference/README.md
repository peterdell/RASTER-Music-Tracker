# test-resources/ui-reference

Reference screenshots of the real `Rmt.exe` (the C++ app), used by the
Java UI port's golden-image tests to check that the Java rendering matches
today's Windows UI pixel for pixel (see `plans/JAVA_UI_PORT_PLAN.md`,
decision 6 and batches B1/B2). Each scenario is one self-contained folder
holding the `.rmt` it shows (so the Java test can load the same module)
and its screenshots.

## Two different scalings - read this first

- **Windows display scaling** is **150%** on the capturing machine. It is
  fixed and applies to every screenshot - but only to the window frame,
  menu and toolbars. The captures themselves show that `Rmt.exe` draws
  its client area 1:1 in device pixels regardless (its own debug line
  reports `GW=1278` for the 2556-pixel-wide client area of a maximized
  window on the 2559-pixel desktop at RMT 200%), so no 1.5x stretch is
  applied to the tracker screen.
- **RMT's own scaling option** (Options dialog, 100-300%) is how much RMT
  itself stretches its fixed-size internal canvas before drawing it. This
  is what the file-name suffix refers to: no suffix = the option at its
  default 100%, `-scale200` = the option set to 200%.

So a plain capture shows every logical RMT pixel as exactly one device
pixel, and a `-scale200` capture as an exact 2x2 block - **both are
lossless**; the tests (`ReferenceScreenshot` in `src/java/test/`) read a
`-scale200` capture by sampling every second pixel and check that the
blocks really are uniform. The two variants are still both useful: the
200% one is what the user actually works with (and is readable), the
100% one shows the layout at the full logical width.

Other conventions: full-desktop captures (2559x1440) are fine - the tests
locate the RMT client area themselves; dialogs are captured window-only.
Unless the name says otherwise: cursor on song line 0 / track line 0, edit
mode (not jam), not playing, default view options.

## Naming

`<scenario>/<view>[-<state>][-scale200].png`

- `<scenario>`: `song0-empty`, `song1-mono`, `song2-stereo`, `dialogs`.
- `<view>`: `tracks` (Edit Tracks screen) or `instruments` (Edit
  Instruments screen).
- `<state>` (optional): `playing`, `jam`, `goto`.

Tests skip any reference image smaller than a real capture, so should a
1x1 placeholder ever be added for a new capture request again, it is
harmless until overwritten with the real capture under the same name.

## Present

Every requested capture is present (the last five dialogs arrived on
2026-09-27).

| Folder | Module | Captures |
|---|---|---|
| `song0-empty/` | none loaded | `tracks.png`, `instruments.png` (RMT 100%); `tracks-scale200.png`, `instruments-scale200.png` |
| `song1-mono/` | `Delta.rmt` (`RMT4`, mono) | `tracks-scale200.png`, `instruments-scale200.png`, `tracks-playing-scale200.png`, `tracks-goto-scale200.png` |
| `song2-stereo/` | `Why_Do_You_Dance_With_Me-132-$4000.rmt` (`RMT8`, stereo) | `tracks.png`, `instruments.png` (RMT 100%); `tracks-scale200.png`, `instruments-scale200.png`, `tracks-playing-scale200.png`, `tracks-jam-scale200.png` |
| `dialogs/` | - | see the table below |

## Dialogs - what each capture shows and how to open it

Layout references for batch B7 (`Rmt.rc` holds the exact geometry; these
are the visual sanity check). "Song loaded" means any module is open.

| File | Dialog (C++ class) | Menu path |
|---|---|---|
| `about.png` | `CAboutDialog` | Help > About RASTER Music Tracker |
| `options.png` | `COptionsDialog` | Tools > Options... |
| `options-paths.png` | `COptionsPathsDialog` | Tools > Options... > the paths sub-dialog |
| `tuning.png` | `TuningDlg` | (captured; reached from the tuning controls) |
| `file-new.png` | `CFileNewDlg` | File > New (Ctrl+W) |
| `change-max-track-length.png` | `CChangeMaxtracklenDlg` | Song > Change maximal length of tracks... |
| `insert-copy-clone.png` | `CInsertCopyOrCloneOfSongLinesDlg` | Song > Insert copy or clone of song line(s)... (Ctrl+O) |
| `tracks-order.png` | `CSongTracksOrderDlg` | Song > Song columns' order change/copy/clear... |
| `instrument-change.png` | `CInstrumentChangeDlg` | Instrument > Change all the instrument occurences... |
| `renumber-instruments.png` | `CRenumberInstrumentsDlg` | Instrument > Renumber all instruments... |
| `renumber-tracks.png` | `CRenumberTracksDlg` | Track > Renumber all tracks... |
| `block-effects.png` | `CEffectsDlg` | select a block first, then Block > Effects/Tools... (Ctrl+F) |
| `import-mod.png` | `CImportModDlg` | File > Import..., pick a `.mod` |
| `import-tmc.png` | `CImportTmcDlg` | File > Import..., pick a `.tmc` |
| `export-sap-type-b.png` | `CSAPFileExportDialog` | File > Export, choose the SAP type B file type |
| `export-xex.png` | `CExpMSXDlg` | File > Export, choose the XEX file type |
| `export-stripped-rmt.png` | `CExportStrippedRMTDialog` | File > Export, choose the stripped RMT file type |
| `export-asm.png` | `CExportAsmDlg` | File > Export, choose the ASM file type |
| `export-asm-relocatable.png` | `CExportRelocatableAsmForRmtPlayer` | File > Export, choose the relocatable ASM file type |

Not captured, not needed: the three click-positioned popups
(`COctaveSelectDlg`/`CVolumeSelectDlg`/`CInstrumentSelectDlg` - they are
drawn from `Rmt.rc` in B4), the standard Windows open/save file dialogs,
and the help/info screens.
