# Develop Cadryl

[Documentation](README.md) · [Français](../DEVELOPMENT_RESUME.md)

Development is on `main`. Current version: **4.2.0-rc6**, Android version code **11**. Cadryl is the display name; keep `com.unicornwhodev.visiondatasetstudio` as the application ID for updates and data continuity.

## Set up the machine

Install JDK 21, Python 3.11+, Android SDK 36 and build-tools 36.0.0. The repository bootstrap uses its pinned Gradle version. Point `JAVA_HOME` and `ANDROID_HOME` at your own installations.

```powershell
git clone https://github.com/unicornwhodev/vision-dataset-studio.git
cd vision-dataset-studio
git switch main
git pull --ff-only
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-21'
$env:ANDROID_HOME='D:/Android/Sdk'
$env:PATH=$env:JAVA_HOME+'/bin;'+$env:ANDROID_HOME+'/platform-tools;'+$env:PATH
```

Those two paths are examples; adjust them to your machine.

## Prepare the native dependencies

Gradle uses three verified local AARs: Flex `2.16.1-vds16k1`, Graphics Path `1.0.1-vds16k1` and LiteRT `2.2.0-vds16k2`. They are not stored in Git.

Download their ZIPs from [rc6](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6), verify `SHA256SUMS` and extract the `dist/native-*/maven/...` paths into the clone root. Keep each AAR, POM and receipt together. Android build scripts check their hashes and recipe before use.

Source rebuilds require Linux or WSL with the pinned tools. See [Flex](../FLEX_16K.md), [Graphics Path](../GRAPHICS_PATH_16K.md) and [LiteRT](../LITERT_16K_STATUS.md). Later Android builds can run directly on Windows.

## Build and test

```powershell
python -m unittest discover -s tools/qa -p 'test_*.py'
python -X utf8 tools/build_android.py
```

The real build resolves dependencies, generates Room/KSP code, runs its checks and produces Debug app/test APKs. Every attempt writes logs, hashes and results under `dist/android/runs/`. An APK left behind by a failed attempt does not mean qualification passed.

For the minified Release:

```powershell
python -X utf8 tools/qa/build_release_test_apks.py --abi arm64-v8a
python -X utf8 tools/qa/build_release_test_apks.py --abi x86_64
```

Run these sequentially. [Release testing](../RELEASE_TESTING.md) explains app/test signing, R8 mappings and the 40 core tests. The independent `release-qa` driver covers four UI scenarios. Private keys stay outside the repository.

On Linux, use `python3` and your shell’s environment syntax. `tools/build_android.sh` wraps the same Python build.

## Test on Android

Select a dedicated device from `adb devices` and prepare the fixtures in the [Android acceptance guide](../ANDROID_QUALIFICATION.md). Use the runners that verify APK hashes. They do not uninstall an app to bypass a certificate conflict.

Model fixtures are staged separately and use device storage. HF writes, fault injection and model conversions have separate requirements and permissions. Skipped tests are unexecuted tests. Do not clear crash logs to turn a campaign green.

## Find your way around

Start with [architecture](ARCHITECTURE.md), the [data schema](../../DATA_SCHEMA.md), [model contract](LITERT_TRAINING_CONTRACT.md) and [brand resources](../BRAND.md).

Preserve human corrections, read back copies before cleanup and keep the original model intact. Later training runs continue the validated copy. Interrupted work must never be labelled successful.

## Current next steps

rc6 includes the native fixes and Release test suite. Phone acceptance, physical ARM 16 KB, longer sessions, catalogue quality, remote CI and native notice review remain open. See [results](VALIDATION.md) and the [roadmap](ROADMAP.md).
