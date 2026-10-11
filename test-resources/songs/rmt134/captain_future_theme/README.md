File Name:      Captain_Future_Theme.rmt
Title:          Captain Future Theme
Author:         Christian Duesterhoeft (Buddy)
Date:           13/08/2024
RMT Version:    RMT 1.34
Tracker Driver: Patch16
Source:         ASMA submission "Buddy - Captain Future (for SV24se) (Stereo-Pokey)"

The master tune of the Simple RMT Player: an RMT8 stereo module for the
Patch16 driver. Three byte-identical copies exist, guarded by a test
(TestSongsTest): this one, the example song
rmt/songs/rmt134/buddy/Captain_Future_Theme.rmt, and the player bundle
asm/Patch-16/music.rmt.

Captain_Future_Theme.sapr is the permanent SAP-R reference: RMT's own
register dump of one full, non-repeating pass (7524 stereo frames),
which build/record_sapr_bridge.py compares the Simple RMT Player's
replay against (plans/33_ALTIRRA_REPLAY_VERIFICATION_PLAN.md).
Regenerate it with an rmtscript: open the .rmt, then
set driver patch16, then
export sapr Captain_Future_Theme.sapr date="13/08/2024".
Regenerated 2026-10-11 with the GENERATED tuning tables (plan 37:
the tables now win over the driver obx's static copy, as in RMT
1.34) - 3727 AUDF bytes moved by one step against the first dump.
Until the obx's static page is aligned, the Simple RMT Player
bundle still plays the static tables, so the plan 33 comparison
shows exactly those one-step differences.

Captain_Future_Theme-original-SV2k24SE.xex is the composer's own
release build ("Buddy - Captain Future (for SV24se) (Stereo-Pokey)
.xex", RMT 1.34 export, Silly Venture 2k24 SE, 15-18 August 2024) -
the plan 36 anchor. It is Vin Samuel (VinsCool)'s VUPlayer 2.0 LZSS
stream player carrying an LZSS-compressed register dump (7524 stereo
frames), not an RMT module player. Its stream matches our SAP-R
reference once the LZSS export's audibility-neutral normalization
(CCompressLzss::Optimise_AUDC/AUDF in src/cpp/lzss_sap.cpp, the
AUDC_AUDF level) is applied; what remains is a +-1 AUDF tuning-table
remap (RMT 1.34's generated tables versus 1.36's fixed tables) and
one AUDCTL $20 bit in the 573 intro frames - stale state from the
composer's own export session, baked into the dump by RMT 1.34's
LZSS export; our clean-init rendering is the correct one. See plan
36, F2.
