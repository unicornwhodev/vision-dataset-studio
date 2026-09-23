# Resume development

[Français](../DEVELOPMENT_RESUME.md) · **English**

Source is on `main`. The development version is **4.2.0-rc5**, Android code **10**. The **rc5** prerelease and artifact package are published: [release](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5), [verified receipt](../RC5_PUBLICATION_RECEIPT.json). Its Windows certificate differs from rc4, so it cannot update rc4. Existing rc4 assets are preserved. Exact results and build identities are in [validation](VALIDATION.md) and [Windows evidence](../../test-results/windows-rc5-release/README.md).

## Local workstation

Use JDK 21, Python 3.11+, Android SDK 36, build-tools 36.0.0 and `adb`. Set `ANDROID_HOME` to the SDK and add its `platform-tools` to `PATH`. Keep weights and datasets outside Git and the APK.

```bash
git clone https://github.com/unicornwhodev/vision-dataset-studio.git
cd vision-dataset-studio
git switch main
git pull --ff-only
python3 -m unittest discover -s tools/qa -p 'test_*.py'
bash tools/build_android.sh
```

The build produces one user APK and one instrumentation APK, with receipts under `dist/android/`. Preserve the signing key when updating an existing installation; never uninstall or clear data to bypass a signature mismatch. The remote workstation's Debug key is retained outside Git and is not included in a clone.

Use a **dedicated QA device or emulator**, select its identifier using `adb devices`, then run:

```bash
export ANDROID_SERIAL=emulator-5554  # replace with the selected QA device
export VDS_ALLOW_TEST_INSTALL=1
bash tools/qa/run_device_qualification.sh
```

Tests requiring external fixtures or authorization are separate. A run with skipped tests does not reproduce the documented 39-test selection. `stage_android_fixture.py` injects fixtures outside the APK; `qualify_converted_models.py --help` describes per-conversion qualification. QA receipts identify tested artifacts; the application requires no SHA manifest to import or use models.

Conversion fixtures consume storage inside the QA installation and count toward its budget. Use a QA device separate from production and archive completed campaign fixtures before the core suite. A storage-budget refusal does not establish model incompatibility.

## Behavior to preserve

- Batch production: import, preannotation, review, verified export, optional learning, cleanup, next batch. Deduplication history survives cleanup.
- Changing prompts, settings or models never rewrites saved annotations. Explicit rerun is required and still protects human corrections.
- Inference-only models remain usable. Training is off by default and runs inside Android on the exported batch.
- Interrupted training can be resumed or explicitly abandoned. Abandonment neither activates weights nor deletes images; cleanup requires a separate confirmation.

## Next qualification

1. Execute the 15 remaining conversions and retry the three timed-out cases on suitable hardware. The existing 13 passes, including 8 with learning, do not qualify the entire catalogue.
2. Test physical ARM devices, real SAF permissions, memory, latency and a representative corpus. The pod used software emulation without KVM.
3. Qualify a real agent server and live HF writes on a newly created, explicitly authorized private QA repository. Existing repositories are never used for destructive QA.
4. Establish durable signing and finish distribution qualification. rc5 is a Debug prerelease; the optimized ARM64 candidate is unsigned. GHCR retains its private visibility.

The supplied trainable HF conversions freeze their encoders; their passes cover heads or adapters. Internal-layer training is demonstrated only by the synthetic fixture. No accuracy gain is claimed.
