#!/bin/sh
# Proves that the xasm-compatible player sources stay xasm-compatible,
# byte for byte - so an edit that leans on a MADS-only construct (a bare
# accumulator shift, .ifndef, a space before a dta comma, a comment line
# missing its ';') fails the push instead of silently surviving. Beyond
# keeping Raster's original toolchain working, xasm compatibility is
# required for including the player source in ASAP
# (https://github.com/pfusik/asap/), whose 6502 players are built with
# xasm.
#
# xasm treats a space after a complete expression as the start of a comment,
# so some of these mistakes change the assembled bytes WITHOUT an error
# message - exactly that happened during the plan-51 editorial pass
# (plans/54_XASM_TRACKER_COMPATIBILITY_PLAN.md), which is why this check
# compares bytes and not just exit codes.
#
# Assembles with the vendored, pinned xasm (lib/xasm - see its README.md):
# - asm/Patch-16/rmtplayr.a65 (the target selectors on the command line,
#   per plan 54) against the shipped rmt_driver_v6.obx,
# - asm/Unpatched/rmt_ata.a65 against the shipped rmt_driver_v1.obx,
# - asm/Unpatched's rmt_sap4/rmt_sap8/rmt_msx, which have no shipped
#   binaries, against what the vendored MADS assembles from the same
#   sources (cross-assembler equality, no new committed blobs).
#
# The Simple RMT Player targets (asm/Patch-16/rmtplayer.a65, XEX/SAP)
# are deliberately out of scope: they stay MADS-only (.ifndef defaults).
#
# Runs on Linux (CI: .github/workflows/check-drivers.yml) and on Windows
# in Git Bash. Exit code 0 only if every output is byte-identical.

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

case "$(uname -s)" in
Linux*)
    XASM="$ROOT/lib/xasm/linux_x86_64/xasm"
    MADS="$ROOT/lib/mads/linux_x86_64/mads"
    ;;
MINGW* | MSYS* | CYGWIN*)
    XASM="$ROOT/lib/xasm/windows_x86_64/xasm.exe"
    MADS="$ROOT/lib/mads/windows_x86_64/mads.exe"
    ;;
*)
    echo "No xasm binary vendored for $(uname -s) - see lib/xasm/README.md" >&2
    exit 1
    ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# The Windows binaries cannot resolve an MSYS-style /tmp path, so hand
# them the Windows spelling. (The '-d LABEL=VALUE' and '-o FILE' option
# forms start with '-', which Git Bash does not mangle; xasm's '/d:'
# spelling would be rewritten into a path.)
winpath() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

failed=0

# assemble <dir under asm/> <source> <output> [xasm options...]: xasm into $TMP/<output>.
assemble() {
    dir="$1"
    src="$2"
    out="$TMP/$3"
    shift 3
    if ! (cd "$ROOT/asm/$dir" && "$XASM" "$@" -q "$src" -o "$(winpath "$out")" >"$out.log" 2>&1); then
        echo "FAIL $src: xasm could not assemble asm/$dir/$src:" >&2
        cat "$out.log" >&2
        failed=1
        return 1
    fi
}

# compare <output> <reference path> <label>: byte-compare an assembly with its reference.
compare() {
    if ! cmp -s "$TMP/$1" "$2"; then
        echo "FAIL $1: xasm asm output is not byte-identical to $3" >&2
        failed=1
    else
        echo "OK   $1 == $3 ($(wc -c <"$TMP/$1" | tr -d ' ') bytes)"
    fi
}

echo "xasm compatibility check with $("$XASM" 2>/dev/null | head -n 1 || true)"

# The tracker driver: xasm must reproduce the shipped bytes.
if assemble Patch-16 rmtplayr.a65 tracker.obx -d FEAT_IS_TRACKER=1 -d FEAT_IS_SIMPLEP=0; then
    compare tracker.obx "$ROOT/rmt/resources/drivers/rmt_driver_v6.obx" "shipped rmt_driver_v6.obx"
fi

# The unpatched driver: xasm must reproduce the shipped bytes.
if assemble Unpatched rmt_ata.a65 rmt_ata.obx; then
    compare rmt_ata.obx "$ROOT/rmt/resources/drivers/rmt_driver_v1.obx" "shipped rmt_driver_v1.obx"
fi

# The remaining Unpatched targets have no shipped binaries: xasm and the
# vendored MADS must agree on every byte.
for t in rmt_sap4 rmt_sap8 rmt_msx; do
    if assemble Unpatched "$t.a65" "$t.obx"; then
        if ! (cd "$ROOT/asm/Unpatched" && "$MADS" "-o:$(winpath "$TMP/$t-mads.obx")" "$t.a65" >"$TMP/$t-mads.log" 2>&1); then
            echo "FAIL $t.obx: MADS could not assemble asm/Unpatched/$t.a65 for the cross-check:" >&2
            cat "$TMP/$t-mads.log" >&2
            failed=1
        else
            compare "$t.obx" "$TMP/$t-mads.obx" "the vendored MADS's assembly of the same source"
        fi
    fi
done

exit "$failed"
