#!/usr/bin/env bash
export JAVA_HOME=/workspace/toolchains/jdk-21
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export ANDROID_AVD_HOME=/workspace/toolchains/avd
export GRADLE_USER_HOME=/workspace/cache/gradle
export PATH="$JAVA_HOME/bin:/workspace/toolchains/kotlin-2.2.10/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
