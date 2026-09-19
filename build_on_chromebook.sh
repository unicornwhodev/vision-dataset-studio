#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_REV="15859902"
CMDLINE_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
CMDLINE_ZIP="commandlinetools-linux-${CMDLINE_REV}_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/${CMDLINE_ZIP}"

if [[ "$(uname -m)" != "x86_64" ]]; then
  echo "Ce script est préparé pour le Linux x86_64 de ton Chromebook." >&2
  exit 2
fi

sudo apt-get update
sudo apt-get install -y --no-install-recommends \
  openjdk-17-jdk-headless python3 python3-venv curl unzip zip ca-certificates git

JAVA_HOME_CANDIDATE="/usr/lib/jvm/java-17-openjdk-amd64"
if [[ -d "$JAVA_HOME_CANDIDATE" ]]; then
  export JAVA_HOME="$JAVA_HOME_CANDIDATE"
fi
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$SDK_ROOT/cmdline-tools"
if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  echo "Téléchargement des Android command-line tools..."
  curl -fL --retry 4 --retry-delay 3 "$CMDLINE_URL" -o "$TMP/$CMDLINE_ZIP"
  echo "$CMDLINE_SHA256  $TMP/$CMDLINE_ZIP" | sha256sum -c -
  unzip -q "$TMP/$CMDLINE_ZIP" -d "$TMP/unpacked"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools/latest"
  mv "$TMP/unpacked/cmdline-tools/"* "$SDK_ROOT/cmdline-tools/latest/"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/build-tools/36.0.0:$PATH"

# L'exécution de ce script signifie que l'opérateur choisit d'installer ces composants.
yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/local.properties"

cd "$ROOT"
chmod +x gradlew tools/build_android.sh || true

set +e
bash tools/build_android.sh
BUILD_CODE=$?
set -e

LATEST_JSON="$ROOT/dist/android/latest.json"
if [[ -f "$LATEST_JSON" ]]; then
  RUN_REL="$(python3 - <<'PY'
import json
from pathlib import Path
p=Path('dist/android/latest.json')
print(json.loads(p.read_text()).get('run',''))
PY
)"
else
  RUN_REL=""
fi
RUN_DIR="$ROOT/dist/android/$RUN_REL"

DEST=""
if [[ -d /mnt/chromeos/MyFiles/Downloads ]]; then
  DEST="/mnt/chromeos/MyFiles/Downloads/VisionDatasetStudio-build"
else
  DEST="$HOME/Downloads/VisionDatasetStudio-build"
fi
mkdir -p "$DEST"

APK=""
if [[ -n "$RUN_REL" && -d "$RUN_DIR" ]]; then
  APK="$(find "$RUN_DIR" -maxdepth 1 -type f -name '*.apk' ! -name '*test*.apk' | head -n1 || true)"
fi

if [[ -n "$APK" && -f "$APK" ]]; then
  cp -f "$APK" "$DEST/"
  [[ -f "$APK.sha256" ]] && cp -f "$APK.sha256" "$DEST/" || true
  [[ -f "$RUN_DIR/status.json" ]] && cp -f "$RUN_DIR/status.json" "$DEST/" || true
  echo
  echo "APK construite :"
  echo "$DEST/$(basename "$APK")"
else
  ERR="$DEST/build-errors.txt"
  {
    echo "Vision Dataset Studio build failed"
    echo "exit_code=$BUILD_CODE"
    echo "run_dir=$RUN_DIR"
    echo
    for f in "$RUN_DIR/assemble.log" "$RUN_DIR/checks.log" "$RUN_DIR/gradle-version.log" "$RUN_DIR/status.json"; do
      if [[ -f "$f" ]]; then
        echo "===== $f ====="
        cat "$f"
        echo
      fi
    done
  } > "$ERR"
  echo
  echo "Aucune APK produite. Rapport :"
  echo "$ERR"
  echo "Envoie-moi build-errors.txt et je corrigerai les erreurs de compilation exactes."
fi

exit "$BUILD_CODE"
