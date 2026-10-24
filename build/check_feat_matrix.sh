#!/bin/sh
# Assembles the two player sources (asm/Unpatched, asm/Patch-16) with every
# feature flag of rmt_feat.a65 toggled once, and compares the outputs'
# SHA-256 hashes against the checked-in manifest build/feat_matrix.sha256
# (plans/52_FEAT_MATRIX_PLAN.md).
#
# Why: the default configuration leaves whole IFT branches unassembled
# (five flags default to 0, and the ELS sides of the default-1 flags are
# equally dark), so a comment merge in those branches could break or change
# code without build/check_drivers.sh noticing. One flag toggled at a time
# assembles both sides of every single-flag IFT; the manifest extends the
# "comment edits change no bytes" proof over all of them. An intentional
# code change regenerates the manifest, and its diff shows exactly which
# configurations are affected.
#
# The sources are never modified: each variant is the folder's rmt_feat.a65
# with one flag's 0/1 inverted, written into a temp copy of the folder.
# (".ifndef plus -d:" was rejected: MADS-only syntax, and asm/Unpatched
# stays xasm-compatible.) Patch-16's FEAT_IS_TRACKER/FEAT_IS_SIMPLEP are
# not flipped - they select the target, the ERT guards reject the other
# combinations, and the three targets exercise them already.
#
# A variant that does not assemble is recorded as ASSEMBLY-ERROR: a
# documented known-broken toggle rather than a silent skip, and a newly
# breaking one is a manifest diff, hence a failure. (In Patch-16 several
# toggles are genuinely broken - labels defined inside the flag's IFT
# block are referenced outside it, e.g. v_skctl under FEAT_TWO_TONE.)
#
# Unpatched targets: rmt_ata.a65 is stereo-only, and the SAP harnesses -
# the only mono builds - guard their hardcoded init address with
# "ert start!=RMTPLAYEREND", which correctly fires whenever a flag flip
# changes the player's size. So the matrix assembles generated wrappers
# (STEREOMODE equ N + icl "rmt.a65", temp files, not repository sources)
# for all four stereo modes per variant, and the SAP harnesses only in
# the DEFAULT configuration, where their size guard holds.
#
# Usage:
#   sh build/check_feat_matrix.sh          # check against the manifest
#   sh build/check_feat_matrix.sh update   # rewrite the manifest
#
# Runs on Linux (CI: .github/workflows/check-drivers.yml) and on Windows in
# Git Bash, with the vendored MADS (lib/mads; check_drivers.sh proves it
# reproduces the xasm-era source byte for byte). Exit code 0 only if the
# matrix matches the manifest and the DEFAULT builds match the shipped
# drivers.

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-check}"
MANIFEST="$ROOT/build/feat_matrix.sha256"

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
# the Windows spelling (same helper as check_drivers.sh).
winpath() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

GEN="$TMP/feat_matrix.sha256"
: >"$GEN"
failed=0

# The flags: every "FEAT_* equ 0|1" line of the folder's rmt_feat.a65, in
# file order, minus Patch-16's target selectors.
flags_of() {
    grep -E '^FEAT_[A-Z0-9_]+[[:space:]]+equ[[:space:]]+[01]' "$1/rmt_feat.a65" |
        sed -E 's/^(FEAT_[A-Z0-9_]+).*/\1/' |
        grep -v -E '^FEAT_IS_(TRACKER|SIMPLEP)$'
}

# flip <src rmt_feat.a65> <flag> <dst>: invert the flag's 0/1.
flip() {
    sed -E "s/^($2[[:space:]]+equ[[:space:]]+)0/\1@ONE@/; s/^($2[[:space:]]+equ[[:space:]]+)1/\10/; s/@ONE@/1/" "$1" >"$3"
}

# record <folder> <flag> <target> <work dir> <out file> <mads args...>:
# assemble one target of one variant and append its manifest line.
record() {
    r_folder="$1" r_flag="$2" r_target="$3" r_work="$4" r_out="$5"
    shift 5
    if (cd "$r_work" && "$MADS" "$@" "-o:$(winpath "$TMP/$r_out")" "$r_target" >"$TMP/mads.log" 2>&1); then
        r_hash="$(sha256sum "$TMP/$r_out" | cut -c1-64)"
    else
        r_hash="ASSEMBLY-ERROR"
        echo "note: $r_folder $r_flag ${r_out#"$r_folder"-} does not assemble" >&2
    fi
    printf '%s %s %s %s\n' "$r_hash" "$r_folder" "$r_flag" "${r_out#"$r_folder"-}" >>"$GEN"
}

# shipped <out file> <shipped driver>: the DEFAULT build must be the binary
# the tracker ships - ties the matrix to rmt/resources/drivers.
shipped() {
    if ! cmp -s "$TMP/$1" "$ROOT/rmt/resources/drivers/$2"; then
        echo "FAIL $1 (DEFAULT) is not the shipped $2" >&2
        failed=1
    fi
}

# matrix <folder under asm/>: DEFAULT first, then one variant per flag.
# The folder's targets come from targets_<name>, called with the variant's
# folder name and flag; setup_<name> may generate extra temp sources.
matrix() {
    m_src="$ROOT/asm/$1"
    m_work="$TMP/$1"
    rm -rf "$m_work"
    cp -r "$m_src" "$m_work"
    setup_"$2" "$m_work"
    targets_"$2" "$1" DEFAULT "$m_work"
    for m_flag in $(flags_of "$m_src"); do
        flip "$m_src/rmt_feat.a65" "$m_flag" "$m_work/rmt_feat.a65"
        targets_"$2" "$1" "$m_flag" "$m_work"
    done
}

setup_unpatched() {
    for sm in 0 1 2 3; do
        printf '; Generated by build/check_feat_matrix.sh: the bare player in\n; stereo mode %s. A temp file, not a repository source.\nSTEREOMODE\tequ %s\n\ticl "rmt.a65"\n' "$sm" "$sm" >"$1/wrapper_sm$sm.a65"
    done
}

targets_unpatched() {
    record "$1" "$2" rmt_ata.a65 "$3" "$1-rmt_ata.obx"
    for sm in 0 1 2 3; do
        record "$1" "$2" wrapper_sm$sm.a65 "$3" "$1-wrapper_sm$sm.obx"
    done
    if [ "$2" = DEFAULT ]; then
        record "$1" "$2" rmt_sap4.a65 "$3" "$1-rmt_sap4.obx"
        record "$1" "$2" rmt_sap8.a65 "$3" "$1-rmt_sap8.obx"
        shipped "$1-rmt_ata.obx" rmt_driver_v1.obx
    fi
}

setup_patch16() {
    :
}

targets_patch16() {
    record "$1" "$2" rmtplayr.a65 "$3" "$1-tracker.obx"
    record "$1" "$2" rmtplayer.a65 "$3" "$1-rmtplayer.xex" -d:FEAT_IS_TRACKER=0 -d:FEAT_IS_SIMPLEP=1
    record "$1" "$2" rmtplayer.a65 "$3" "$1-rmtplayer.sap" -d:FEAT_IS_TRACKER=0 -d:FEAT_IS_SIMPLEP=1 -d:EXPORTXEX=0 -d:EXPORTSAP=1
    if [ "$2" = DEFAULT ]; then
        shipped "$1-tracker.obx" rmt_driver_v6.obx
    fi
}

echo "Feature-flag matrix with $("$MADS" 2>/dev/null | head -n 1 || true)"
matrix Unpatched unpatched
matrix Patch-16 patch16

variants="$(sort -u -k2,3 "$GEN" | wc -l | tr -d ' ')"
lines="$(wc -l <"$GEN" | tr -d ' ')"
errors="$(grep -c '^ASSEMBLY-ERROR' "$GEN" || true)"

case "$MODE" in
update)
    cp "$GEN" "$MANIFEST"
    echo "Wrote $lines lines ($errors ASSEMBLY-ERROR) to build/feat_matrix.sha256"
    ;;
check)
    if [ ! -f "$MANIFEST" ]; then
        echo "FAIL build/feat_matrix.sha256 is missing - run: sh build/check_feat_matrix.sh update" >&2
        failed=1
    # Compare with the CRs removed: on Windows the checked-out manifest may
    # carry CRLF endings (same normalization as check_drivers.sh's headers).
    elif ! tr -d '\r' <"$MANIFEST" | diff -u - "$GEN"; then
        echo "FAIL the feature-flag matrix differs from build/feat_matrix.sha256." >&2
        echo "A comment-only change must not get here. For an intentional code change," >&2
        echo "regenerate with: sh build/check_feat_matrix.sh update" >&2
        failed=1
    else
        echo "OK   $lines matrix lines match build/feat_matrix.sha256 ($errors known ASSEMBLY-ERROR)"
    fi
    ;;
*)
    echo "Unknown mode '$MODE' (use: check | update)" >&2
    exit 1
    ;;
esac

exit "$failed"
