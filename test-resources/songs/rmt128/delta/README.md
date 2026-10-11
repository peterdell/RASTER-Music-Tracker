File Name:      Delta.rmt
Title:          Delta
Author:         Radek Sterba (Raster)
Date:           01/2003 (DATE in delta.sap, unchanged in all releases since RMT 1.00 beta)
RMT Version:    RMT 1.00b
Tracker Driver: RMT 1.28
Source:         https://asma.atari.org/asma/Composers/Sterba_Radek/Delta.sap

Original exports by the composer (the anchor for plan 36; both differ,
so both are kept):
Delta-original-rmt10b.xex:  from Raster's rmt10b.zip (same build in
                            rmt102b.zip) - pre-3.2.2003 frequency tables.
Delta-original-rmt128.xex:  from Raster's rmt110.zip through rmt128.zip
                            (one unchanged build) - the definitive export.

Delta.sapr is the permanent SAP-R reference: RMT's register dump of one
full pass with the unpatched (classic) driver - the stream the plan 36
anchoring matched against Raster's own rmt128 XEX export (3750 of 3840
frames identical, every difference inside the ~115-frame intro).
Regenerate it with an rmtscript: open the .rmt, then
set driver unpatched
export sapr Delta.sapr author="Radek Sterba (Raster)" name="Delta" date="01/2003"
