#!/bin/sh
# Takes the DevShots screenshots (see DevShots.java) in a game directory of
# their own, run-devshots/, on a throwaway copy of a world - never in run/,
# where the real test worlds are, since the steps change the world they run in.
#
# Usage: tools/devshots.sh [step prefixes...]   e.g. tools/devshots.sh mannequin guide
# Screenshots land in run-devshots/screenshots/.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/run-devshots"
cd "$ROOT"

if pgrep -f "fabric.dli.config=$ROOT/" >/dev/null 2>&1; then
	echo "A dev client of this project is running - close it first." >&2
	exit 1
fi

mkdir -p "$DIR/saves" "$DIR/resourcepacks" "$DIR/screenshots"
if [ ! -d "$DIR/saves/DevShots" ]; then
	latest=$(ls -td "$ROOT"/run/saves/*/ 2>/dev/null | head -1)
	if [ -z "$latest" ]; then
		echo "No world to copy: create one in the dev client first (./gradlew runClient)." >&2
		exit 1
	fi
	echo "Copying $(basename "$latest") as the throwaway DevShots world"
	cp -r "$latest" "$DIR/saves/DevShots"
	rm -f "$DIR/saves/DevShots/session.lock"
fi
[ -f "$ROOT/run/options.txt" ] && cp "$ROOT/run/options.txt" "$DIR/options.txt"
# The freshly converted add-on, as the mod would have converted it into this game directory.
rm -rf "$DIR/resourcepacks/villagernewsjavafied-converted"
cp -r "$ROOT/dev/converted" "$DIR/resourcepacks/villagernewsjavafied-converted"
[ -d "$ROOT/run/villagernewsjavafied" ] && cp -r "$ROOT/run/villagernewsjavafied" "$DIR/"

find "$DIR/screenshots" -name 'devshot-*.png' -delete
printf '%s' "$*" > "$DIR/villagernewsjavafied-devshots"
./gradlew runClient -q --args="--gameDir $DIR --quickPlaySingleplayer DevShots" > "$DIR/devshots.log" 2>&1 || true
ls "$DIR/screenshots" | grep devshot || echo "No screenshots - see $DIR/devshots.log" >&2
