#!/usr/bin/env bash
# Stages the Java port's distribution layout - the checked-in rmt/ folder as
# Rmt.exe ships it (resources, instruments, songs, exports, rmt.ini,
# tuning.ini), docs/ generated from doc/*.md by the jar's DocGenerator (as the
# C++ pre-build does into the gitignored rmt/docs; plans/23_DOC_GENERATION_PLAN.md),
# and target/rmt.jar - into the given folder (default:
# target/stage). That folder is jpackage's --input, so the jar ends up next
# to resources/ and docs/ and ProgramFolder resolves them as g_prgpath does.
# Used by .github/workflows/release.yml and for local dry runs:
#   mvn -o package && bash build/stage_java_release.sh && jpackage --type app-image --name rmt --input target/stage --main-jar rmt.jar --main-class org.atari.raster.rmt.ui.RmtApplication --app-version 1.36.0 --dest target/dist
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAGE="${1:-$ROOT/target/stage}"

if [ ! -f "$ROOT/target/rmt.jar" ]; then
	echo "ERROR: $ROOT/target/rmt.jar not found - run 'mvn package' first." >&2
	exit 1
fi

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$ROOT/target/rmt.jar" "$STAGE/"
for folder in exports instruments resources songs; do
	cp -r "$ROOT/rmt/$folder" "$STAGE/"
done
cp "$ROOT/rmt/rmt.ini" "$ROOT/rmt/tuning.ini" "$STAGE/"
# The HTML documentation from doc/*.md (plus the manuals and images as they are)
java -cp "$ROOT/target/rmt.jar" org.atari.raster.rmt.doc.DocGenerator "$ROOT/doc" "$STAGE/docs"
echo "Staged the Java port's distribution layout in $STAGE"
