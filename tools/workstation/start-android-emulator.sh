#!/usr/bin/env bash
set -euo pipefail
source /workspace/toolchains/android-env.sh
QA=/workspace/qa/vision-dataset-studio
mkdir -p "$QA" "$ANDROID_AVD_HOME"
emulator_memory="${VDS_EMULATOR_MEMORY_MB:-4096}"
emulator_cores="${VDS_EMULATOR_CORES:-4}"
[[ "$emulator_memory" =~ ^[0-9]+$ && "$emulator_cores" =~ ^[0-9]+$ ]] || { echo 'Numeric emulator memory/cores required' >&2; exit 2; }
(( emulator_memory >= 1024 && emulator_memory <= 12288 && emulator_cores >= 1 && emulator_cores <= 16 )) || { echo 'Emulator memory/cores outside supported limits' >&2; exit 2; }
case "${VDS_EMULATOR_PROFILE:-workstation}" in
  workstation) width=960; height=720; density=160; scale=1.0 ;;
  phone) width=480; height=800; density=240; scale=0.9 ;;
  *) echo 'VDS_EMULATOR_PROFILE must be workstation or phone' >&2; exit 2 ;;
esac
if pgrep -f 'qemu-system.*-avd vds-api28-aosp ' >/dev/null; then
  echo 'vds-api28-aosp is already running or booting; leaving it untouched.'
  exit 0
fi
if ! avdmanager list avd -c | grep -qx vds-api28-aosp; then
  printf 'no\n' | avdmanager create avd --name vds-api28-aosp --package 'system-images;android-28;default;x86_64' --device pixel_2
fi
python3 - "$ANDROID_AVD_HOME/vds-api28-aosp.avd/config.ini" "$width" "$height" "$density" <<'PYCONFIG'
from pathlib import Path
import sys
p=Path(sys.argv[1]); lines=p.read_text().splitlines()
values={'hw.lcd.width':sys.argv[2],'hw.lcd.height':sys.argv[3],'hw.lcd.density':sys.argv[4],'showDeviceFrame':'no'}
lines=[line for line in lines if line.split('=',1)[0] not in values]
p.write_text('\n'.join(lines+[key+'='+value for key,value in values.items()])+'\n')
PYCONFIG
if ! pgrep -f '^Xvfb :99 ' >/dev/null; then
  nohup Xvfb :99 -screen 0 1280x1000x24 -nolisten tcp > "$QA/xvfb.log" 2>&1 </dev/null &
fi
export DISPLAY=:99
for attempt in {1..20}; do
  [[ -S /tmp/.X11-unix/X99 ]] && break
  sleep 0.2
done
if ! pgrep -f 'x11vnc.*5901' >/dev/null; then
  nohup x11vnc -display :99 -localhost -rfbport 5901 -forever -shared -nopw > "$QA/x11vnc.log" 2>&1 </dev/null &
fi
if ! pgrep -f 'websockify.*6080' >/dev/null; then
  nohup websockify --web=/usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901 > "$QA/novnc.log" 2>&1 </dev/null &
fi
accel=off
if [[ -r /dev/kvm && -w /dev/kvm ]]; then accel=on; fi
nohup emulator -avd vds-api28-aosp -port 5554 -accel "$accel" -gpu swiftshader \
  -no-audio -no-snapshot -no-boot-anim -memory "$emulator_memory" -cores "$emulator_cores" \
  -skin "${width}x${height}" -scale "$scale" -no-metrics > "$QA/emulator-api28.log" 2>&1 </dev/null &
echo "Emulator starting; log: $QA/emulator-api28.log"
echo 'Browser access: ssh -N -L 6080:127.0.0.1:6080 runpod-workstation'
echo 'Then open http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale'
