File Name:      RebbStars.rmt
Title:          RebbStars
Author:         Teemu Pohjanlehto (Rebb)
Date:           20/04/2014 (DATE in RebbStars.sap)
RMT Version:    RMT 1.28
Tracker Driver: RMT 1.28
Speed:          Instrument speed 3 (FASTPLAY 104 in the SAP, PAL 312/3) -
                a genuinely multi-speed tune: the player runs three times
                per video frame, which is what the per-player-call
                stepping (ASAP stepPlayer, plans/49) exists for. Delta,
                the other rmt128 test song, is single-speed.
Channels:       Mono (RMT4), song speed 6, track length 64.
Source:         https://asma.atari.org/asma/Composers/Pohjanlehto_Teemu/RebbStars.sap

RebbStars.rmt is the native module extracted from that SAP with ASAP's
own converter (asapconv -o .%e RebbStars.sap), which unwraps the module
bytes the SAP's RMT player plays at $4000; the extraction is exact, not
a re-export.

RebbStars.sapr is the permanent SAP-R reference: RMT's register dump of
one full pass with the unpatched (classic) driver, one row per player
tick - three per video frame at instrument speed 3, 8064 rows.
Regenerate it with an rmtscript: open the .rmt, then
set driver unpatched
export sapr RebbStars.sapr author="Teemu Pohjanlehto (Rebb)" name="RebbStars" date="20/04/2014"

RebbStars.xex is ASMA's own XEX download of the same tune, an
ASAP-generated player XEX (made by an older ASAP than the vendored one,
so it is not a byte reference for ASAP's writer). Its role is the
independent replay anchor: run in AltirraBridge (the cycle-accurate
Altirra core, plan 33) and sampled every 104 scanlines, its POKEY
stream matches RebbStars.sapr in all 8064 rows (FULL PASS IDENTICAL,
2026-10-22):
  python build/record_sapr_bridge.py --xex RebbStars.xex --out tmp.sapr \
      --region pal --tick-scanlines 104 --warmup 5 --reference RebbStars.sapr
