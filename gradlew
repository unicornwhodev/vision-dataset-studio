#!/usr/bin/env sh
# See tools/gradle_bootstrap.py: checksummed launcher, not the official wrapper JAR.
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec python3 "$DIR/tools/gradle_bootstrap.py" "$@"
