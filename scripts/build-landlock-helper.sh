#!/bin/sh
# Build the native Linux Landlock launcher into Maven's generated resources.
# Runs from Maven on Linux x86-64, both locally (IDE) and on GitHub Actions.
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: sh scripts/build-landlock-helper.sh OUTPUT_PATH" >&2
  exit 2
fi

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
output=$1
case "$output" in
  /*) ;;
  *) output="$project_root/$output" ;;
esac

compiler=${CC:-gcc}
if ! command -v "$compiler" >/dev/null 2>&1; then
  echo "WebPlugin: cannot build bundled Landlock helper: '$compiler' is missing." >&2
  echo "Install a Linux C compiler (gcc) and static libc development files, or use the Linux CI artifact." >&2
  exit 1
fi

mkdir -p "$(dirname -- "$output")"
temporary="$output.tmp.$$"
trap 'rm -f "$temporary"' EXIT HUP INT TERM

"$compiler" -std=gnu11 -static -O2 -Wall -Wextra \
  -o "$temporary" "$project_root/native/webplugin-landlock.c"

chmod 0755 "$temporary"
mv -f "$temporary" "$output"
trap - EXIT HUP INT TERM

echo "WebPlugin: bundled Linux Landlock helper built at $output"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$output"
fi
