File Name:      testsfx.rmt
Title:          (none - a stripped module, names removed)
Author:         rra (AtariAge)
Source:         https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/5
                (testsfx.zip, https://github.com/user-attachments/files/24539593/testsfx.zip)
Speed:          Song speed 8, instrument speed 1, track length 2.
Channels:       Mono (RMT4), module at $6800-$69CA (465 bytes), format
                version 1, stripped (no name block).

An SFX-only module: 9 instruments and NO tracks or song lines - the
instruments exist solely to be triggered directly as sound effects
(RASTERMUSICTRACKER+15, FEAT_SFX). The issue: since RMT 1.34, "Export
As RMT Stripped Song File" with "SFX Support" checked stripped every
instrument not referenced by a track - for this module, all of them -
and wrote a 30-byte husk; 1.28/1.33 correctly kept the instruments and
only rebased the module address. Root cause: the RMT/RMTSTRIPPED
iotypes were swapped on the SFX-support flag in
CRmtExporter::ExportAsStrippedRMTApply (and faithfully ported to
RmtExporter.exportAsStrippedRMTApply).

ImportExportTest.exportStrippedRmtWithSfxKeepsAnSfxOnlyModule pins the
fix: with SFX support, the export at the original address $6800
reproduces testsfx.rmt byte for byte, and a relocation keeps its full
length; without SFX support, stripping an SFX-only module down to the
header remains the correct behavior.
