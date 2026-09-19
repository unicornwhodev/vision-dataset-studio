#!/usr/bin/env bash
# Samples one app only; no full-system log dump or private dataset collection.
set -euo pipefail
: "${ANDROID_SERIAL:?Set the intended test device serial}"
OUT="${VDS_EVIDENCE_DIR:-test-results/metrics-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"
PACKAGE=com.unicornwhodev.visiondatasetstudio
adb -s "$ANDROID_SERIAL" shell getprop ro.product.model >"$OUT/device-model.txt"
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk >"$OUT/android-sdk.txt"
for n in 1 2 3 4 5; do
 adb -s "$ANDROID_SERIAL" shell dumpsys meminfo "$PACKAGE" >"$OUT/meminfo-$n.txt"
 sleep 1
done
adb -s "$ANDROID_SERIAL" shell dumpsys gfxinfo "$PACKAGE" framestats >"$OUT/framestats.txt"
echo 'Five sampled memory snapshots, NOT continuous peak memory. Export the in-app model benchmark separately.'
