#!/usr/bin/env python3
"""Records a SAP type R register dump of an Atari executable, headlessly.

The player XEX runs in AltirraBridgeServer (the windowless build of the
AltirraSDL fork, github.com/ilmenit/AltirraSDL - the same cycle-accurate
Altirra core, scripted over a local socket): CONFIG sets the machine,
BOOT loads the XEX with no dialog, and the recording is frame-exact -
FRAME 1 steps one video frame, POKEY returns the write-latch register
state, and one SAP-R frame (AUDF1, AUDC1, ... AUDF4, AUDC4, AUDCTL) is
appended per step. No GUI, no dialogs, no wall-clock timing: the
emulation runs as fast as the host allows.

With --reference (RMT's own SAP-R export of the module, which holds
exactly one full, non-repeating pass by construction), the recording
length is the reference's frame count plus margin, and the two streams
are compared register by register after alignment.

With --stereo, BOTH chips are recorded per frame (POKEY and POKEY 2,
18 bytes per SAP-R frame, STEREO in the header) - the chip index went
upstream as github.com/ilmenit/AltirraSDL pull request 96, merged
2026-10-10, so the installed bridge nightly must be 381df2d9 or newer.
The player's physical chip order may be swapped relative to the dump
layout; the comparison tries both orders and reports which matched.

Example:
  python build/record_sapr_bridge.py --xex asm/Patch-16/out/rmtplayer.xex \
      --out captain_bridge.sapr --reference captain_rmt.sapr --region pal --stereo
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time

ALTIRRA_SDL_DIR = r"C:\jac\system\Atari800\Tools\EMU\AltirraSDL"

# One SAP-R frame of one POKEY, in RMT's order (PokeyStream.java).
REGISTERS = ["AUDF1", "AUDC1", "AUDF2", "AUDC2", "AUDF3", "AUDC3", "AUDF4", "AUDC4", "AUDCTL"]


def sapr_frames(path, bytes_per_frame):
    """The register frames of a .sapr file, one bytes() per frame."""
    data = open(path, "rb").read()
    body = data[data.index(b"\r\n\r\n") + 4:]
    count = len(body) // bytes_per_frame
    return [body[i * bytes_per_frame:(i + 1) * bytes_per_frame] for i in range(count)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--xex", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--reference", help="RMT's SAP-R export of the module: sets the length, enables the comparison")
    parser.add_argument("--frames", type=int, default=1500, help="frames to record without --reference")
    parser.add_argument("--region", choices=["pal", "ntsc"], default="pal")
    parser.add_argument("--stereo", action="store_true", help="dual-POKEY machine: both chips are recorded (18 bytes/frame)")
    parser.add_argument("--warmup", type=int, default=60, help="boot/init frames before the recording starts (low enough to catch the music's first frame; leading silence is skipped by the alignment)")
    parser.add_argument("--server-dir", default=ALTIRRA_SDL_DIR)
    args = parser.parse_args()

    if not args.out.endswith(".sapr"):
        args.out = os.path.splitext(args.out)[0] + ".sapr"
        print(f"Output renamed to {args.out} (SAP-R files carry the .sapr extension)")

    reference = None
    frames_wanted = args.frames
    if args.reference:
        # The reference is stereo (18) or mono (9); compare on the first chip.
        data = open(args.reference, "rb").read()
        ref_stereo = b"\r\nSTEREO\r\n" in data[:300]
        reference = sapr_frames(args.reference, 18 if ref_stereo else 9)
        frames_wanted = len(reference) + 200
        print(f"Reference: {len(reference)} frames = {len(reference) / (50 if args.region == 'pal' else 60):.1f} s; recording {frames_wanted} frames")

    sys.path.insert(0, os.path.join(args.server_dir, "sdk", "python"))
    from altirra_bridge import AltirraBridge

    exe = os.path.join(args.server_dir, "AltirraBridgeServer.exe")
    # The server's output goes to a FILE, never a pipe: it logs per
    # command, and an undrained pipe buffer fills up and blocks it.
    log_path = args.out + ".server.log"
    log = open(log_path, "w")
    server = subprocess.Popen([exe, "--bridge=tcp:127.0.0.1:0"],
                              stdout=log, stderr=subprocess.STDOUT, text=True)
    try:
        # The server prints the token file path on startup.
        token_file = None
        deadline = time.time() + 20
        while time.time() < deadline and token_file is None:
            time.sleep(0.2)
            m = re.search(r"token-file:\s*(.+)", open(log_path).read())
            if m:
                token_file = m.group(1).strip()
        if not token_file:
            raise RuntimeError("AltirraBridgeServer printed no token file")

        with AltirraBridge.from_token_file(token_file) as bridge:
            bridge.config("video", args.region)
            if args.stereo:
                bridge.config("stereo", "true")
            bridge.boot(os.path.abspath(args.xex))
            bridge.frame(args.warmup)

            # The emulation itself is near-free (FRAME 200 completes in
            # 0.05 s); each round trip costs a server main-loop tick
            # (~16 ms). So the FRAME 1 / POKEY pairs are PIPELINED on the
            # raw socket in batches: many commands land in one tick, and
            # the responses are read back in bulk.
            chips = 2 if args.stereo else 1
            commands = b"FRAME 1\nPOKEY\n" if chips == 1 else b"FRAME 1\nPOKEY\nPOKEY 2\n"
            recorded = bytearray()
            started = time.time()
            sock = bridge._sock
            reader = sock.makefile("rb")
            batch = 500
            done = 0
            while done < frames_wanted:
                n = min(batch, frames_wanted - done)
                sock.sendall(commands * n)
                for _ in range(n):
                    if not json.loads(reader.readline())["ok"]:
                        raise RuntimeError("FRAME failed")
                    for _chip in range(chips):
                        state = json.loads(reader.readline())
                        if not state["ok"]:
                            raise RuntimeError(state.get("error", "POKEY failed"))
                        recorded += bytes(int(state[r].lstrip("$"), 16) for r in REGISTERS)
                done += n
            print(f"Recorded {frames_wanted} frames in {time.time() - started:.1f} s host time")
            bridge.quit()
    finally:
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()

    frame_size = 18 if args.stereo else 9
    header = ("SAP\r\n"
              'AUTHOR "???"\r\n'
              f'NAME "{os.path.splitext(os.path.basename(args.xex))[0]}"\r\n'
              'DATE "???"\r\n'
              "TYPE R\r\n"
              + ("STEREO\r\n" if args.stereo else "")
              + "\r\n").encode("ascii")
    with open(args.out, "wb") as f:
        f.write(header + recorded)
    print(f"Wrote {args.out} ({len(recorded) // frame_size} frames, {'both POKEYs' if args.stereo else 'one POKEY'})")

    if reference:
        rec = [bytes(recorded[i * frame_size:(i + 1) * frame_size])
               for i in range(len(recorded) // frame_size)]
        # The player and the dump may order the chips differently
        # (measured: the player's first POKEY carries the dump's second
        # half), so both orders - or against a stereo reference with a
        # mono recording, both halves - are candidates.
        bytes_per_ref = len(reference[0])
        if bytes_per_ref == frame_size:
            halves = [("as recorded", reference)]
            if frame_size == 18:
                halves.append(("chips swapped", [f[9:] + f[:9] for f in reference]))
        elif bytes_per_ref == 18 and frame_size == 9:
            halves = [("first POKEY", [f[:9] for f in reference]),
                      ("second POKEY", [f[9:] for f in reference])]
        else:
            print("COMPARISON FAILED: a mono reference cannot cover a stereo recording")
            return 1
        def score(ref, offset):
            agree = 0
            for k in range(len(ref)):
                i = k + offset
                if 0 <= i < len(rec) and rec[i] == ref[k]:
                    agree += 1
            return agree

        for chip_name, ref in halves:
            # The tune may repeat sections, so a short probe can anchor on
            # the wrong repetition: score every candidate offset and keep
            # the best.
            mid = len(ref) // 2
            probe = ref[mid:mid + 10]
            hits = [i for i in range(len(rec) - 10) if rec[i:i + 10] == probe]
            if not hits:
                continue
            offset = max((h - mid for h in hits[:50]), key=lambda o: score(ref, o))
            agree = differ = missing = 0
            first_diff = None
            for k in range(len(ref)):
                i = k + offset
                if not 0 <= i < len(rec):
                    missing += 1
                elif rec[i] == ref[k]:
                    agree += 1
                else:
                    differ += 1
                    if first_diff is None:
                        first_diff = (k, ref[k].hex(), rec[i].hex())
            print(f"Aligned with the reference's {chip_name} at offset {offset}: "
                  f"{agree} frames identical, {differ} different, {missing} not covered")
            if differ == 0 and missing == 0:
                print(f"FULL PASS IDENTICAL: the player produces RMT's register stream, frame for frame ({chip_name})")
                return 0
            if first_diff:
                k, a, b = first_diff
                print(f"  first difference at reference frame {k}: RMT={a} recorded={b}")
            if missing:
                print("  (frames not covered: lower --warmup or raise the margin)")
            return 1
        print("COMPARISON FAILED: no alignment found against either reference chip")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
