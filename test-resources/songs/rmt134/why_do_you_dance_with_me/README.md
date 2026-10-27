File Name:      Why_Do_You_Dance_With_Me.rmt
Title:          Why Do You Dance With Me?
Author:         Zdenek Eisenhammer (PG)
Date:           10/12/2021
RMT Version:    RMT 1.32
Tracker Driver: Classic (v1 family) with custom tuning tables - identified
                from the player embedded in the party XEX, see plan 36.
Tuning:         Custom, and not expressible in RMT's tuning settings - the
                composer's player carries its own tables inside the driver
                binary (the pure-tone table shifted 1-2 divisor steps
                sharper, the 64-byte table at $31c0 rebuilt), where RMT's
                generated tables at $b000 cannot reach. Playing this
                module through any driver RMT ships therefore does not
                reproduce the party release. Confirmed tick by tick (plan
                36 F3): RMT's classic driver with only these tables and
                the one distortion entry transplanted reproduces the
                party release exactly - 24320 of 24320 player ticks, both
                POKEYs, every register.
Source:         https://ftp.pigwa.net/stuff/parties/prods/Silly_Venture,2021.12.08-12,Gdansk,Poland/atari_xl_music/whydoyou.zip

Why_Do_You_Dance_With_Me-original-SV2k21WE.xex is WHYDOYOU.XEX from
the party release (Silly Venture 2k21 WE, 10-12 December 2021; the
zip holds the XEX and the info text "Requires stereo POKEY"). The
.rmt is byte-identical to the XEX's module segment ($4000-$570f), so
the party release is the source of both files. The XEX's player is
the classic v1-family driver with retuned frequency tables and one
changed distortion-table entry; the module plays at instrument
speed 4 (FASTPLAY 78). The recovered driver and tuning details are in
plans/36_MODULE_PROVENANCE_PLAN.md.

Why_Do_You_Dance_With_Me-original.sapr is the composer's sound, not an
RMT export: a tick-accurate recording of the party XEX (AltirraBridge,
build/record_sapr_bridge.py --stereo --tick-scanlines 78), cut to one
pass from the music's first tick and stored in RMT's chip order with
the FASTPLAY 78 header, so it compares row for row with RMT's own SAP-R
exports. RMT cannot produce this stream from the .rmt alone, because
no shipped driver plays the composer's tables.
