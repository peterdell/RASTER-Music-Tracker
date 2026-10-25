#!/bin/sh
# Proves that the shipped driver binaries match their checked-in 6502
# sources, byte for byte - so the .obx files in rmt/resources/drivers are
# verifiable builds, not trusted blobs. Assembles each source with the
# vendored MADS (lib/mads, pinned version - see its README.md) and
# byte-compares the result against the shipped binary.
#
# Each source also produces a C header (MADS's -hc: option), one #define per
# label, and that header is checked in under src/cpp/ because the tracker
# addresses the player's variables through it. This script compares those
# too, for the same reason it compares the binaries: src/cpp/tracker_obx.h
# had silently drifted 34 defines behind its source before this check
# existed (plans/42_TRACKER_OBX_HEADER_REFRESH_PLAN.md).
#
# Covers the two drivers whose sources asm/ holds: v1 (RMT 1.28, Legacy)
# and v6 (Patch16). v2-v5, v7 and the VUPlayer (vu_player_v2.obx, from
# https://github.com/VinsCool/VUPlayer-LZSS) have no sources here and stay
# unchecked. Decided in plans/32_RITMO_FORK_ANALYSIS_PLAN.md (section 7):
# the fork's ca65 conversion was not adopted because MADS itself is
# portable and reproduces both drivers from the unmodified sources - MADS
# assembles the XASM-era Legacy source identically too, so one assembler
# suffices.
#
# Runs on Linux (CI: .github/workflows/check-drivers.yml) and on Windows
# in Git Bash. Exit code 0 only if every driver is byte-identical and every
# header is the one its source generates.

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

case "$(uname -s)" in
Linux*)
    MADS="$ROOT/lib/mads/linux_x86_64/mads"
    ;;
MINGW* | MSYS* | CYGWIN*)
    MADS="$ROOT/lib/mads/windows_x86_64/mads.exe"
    ;;
*)
    echo "No MADS binary vendored for $(uname -s) - see lib/mads/README.md" >&2
    exit 1
    ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# The Windows mads.exe cannot resolve an MSYS-style /tmp path, so hand it
# the Windows spelling. (MADS options start with '-', so Git Bash does not
# mangle them the way it would '/o:' style options.)
winpath() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

failed=0

# check <source dir under asm/> <source file> <shipped file under rmt/resources/drivers/> <header under src/cpp/> [extra MADS options]
check() {
    out="$TMP/$3"
    hdr="$TMP/$4"
    # Extra MADS options (one word-split string, e.g. Patch-16's -d: target
    # selectors, which live on the command line so rmt_feat.a65 stays
    # xasm-compatible).
    extra="${5:-}"
    # One MADS run produces both artefacts: -hc: writes the header and leaves
    # the object file untouched, so the binary this compares is still the
    # plain assembly of the source. cd to the source's folder - the sources
    # icl/ins their includes by relative path.
    # shellcheck disable=SC2086
    if ! (cd "$ROOT/asm/$1" && "$MADS" $extra "-o:$(winpath "$out")" "-hc:$(winpath "$hdr")" "$2" >"$TMP/$3.log" 2>&1); then
        echo "FAIL $3: MADS could not assemble asm/$1/$2:" >&2
        cat "$TMP/$3.log" >&2
        failed=1
        return
    fi

    if ! cmp -s "$out" "$ROOT/rmt/resources/drivers/$3"; then
        echo "FAIL $3: asm/$1/$2 assembles, but not to the shipped bytes" >&2
        failed=1
    else
        echo "OK   $3 == mads asm/$1/$2 ($(wc -c <"$out" | tr -d ' ') bytes)"
    fi

    if [ ! -f "$ROOT/src/cpp/$4" ]; then
        echo "FAIL $4: src/cpp/$4 is missing, but asm/$1/$2 generates it" >&2
        failed=1
        return
    fi

    # Compare with the CRs removed: MADS writes CRLF on Windows and the
    # working copy's endings depend on core.autocrlf, neither of which says
    # anything about the addresses this file exists to carry.
    tr -d '\r' <"$hdr" >"$hdr.generated"
    tr -d '\r' <"$ROOT/src/cpp/$4" >"$hdr.checkedin"
    if ! cmp -s "$hdr.generated" "$hdr.checkedin"; then
        echo "FAIL $4: src/cpp/$4 is not what asm/$1/$2 generates now - regenerate it with MADS's -hc: option (asm/Patch-16/build.cmd does it for tracker_obx.h)" >&2
        failed=1
    else
        echo "OK   src/cpp/$4 == mads -hc: asm/$1/$2 ($(grep -c '^#define' "$hdr.generated") defines)"
    fi
}

echo "Driver check with $("$MADS" 2>/dev/null | head -n 1 || true)"
check Legacy rmt_ata.a65 rmt_driver_v1.obx legacy_obx.h
check Patch-16 rmtplayr.a65 rmt_driver_v6.obx tracker_obx.h "-d:FEAT_IS_TRACKER=1 -d:FEAT_IS_SIMPLEP=0"

exit "$failed"
