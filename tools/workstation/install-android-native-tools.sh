#!/usr/bin/env bash
set -euo pipefail
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  libpulse0 libnss3 libx11-6 libxcb1 libxcomposite1 libxcursor1 libxi6 \
  libxtst6 libxrandr2 libxkbcommon0 libasound2t64 libgl1 libglu1-mesa \
  libxkbcommon-x11-0 libxcb-cursor0 libxcb-icccm4 libxcb-image0 \
  libxcb-keysyms1 libxcb-render-util0 libxcb-xkb1 \
  xvfb x11vnc novnc websockify ripgrep
