# What still needs work

[Project home](../../README.en.md) · [Français](../../KNOWN_LIMITATIONS.md)

Cadryl **4.2.0-rc6** is a prerelease. The [test report](VALIDATION.md) links each result to its actual build and device.

## Devices and stability

Both signed Release APKs pass on the Android 16 emulator with 16 KB pages. Strict alignment checks pass for LiteRT, Flex, Graphics Path and DataStore. **The final candidate still needs phone acceptance**, and no physical ARM 16 KB device has been tested. Translated ARM64 on an emulator does not replace that check.

The available Honor uses 4 KB pages. Previous successful phone runs are retained, but ADB disconnected during the last campaign on an intermediate runtime. Those results do not qualify rc6.

The historical ART crash has no confirmed root cause. It has not recurred in the new environment; test runners reject campaigns containing an ART crash, including system-process crashes. Long sessions, restarts and manufacturer background restrictions still need testing. The recorded twelve-minute Honor run used an earlier candidate.

## Models and quality

Catalogue coverage is partial and needs another pass with the new runtime. The public Charlbi campaign of 22 September recorded **13 of 25 variants passing**, including eight trainable variants, one timeout and eleven not run. These are execution results, not accuracy measurements.

The supplied trainable conversions adjust output heads or adapters with a frozen encoder. The internal-layer synthetic test is separate. Accuracy, error patterns, forgetting, RAM and latency still need measurement on representative data and ARM hardware.

Some bundles need multiple graphs, processors and tokenizers. A single `.tflite` file is not a replacement for a complete bundle.

## Data and transfers

Earlier campaigns injected real SAF, storage, HF and cleanup failures. Coverage does not include every storage provider, large multipart transfer or interruption. A real local agent server and some cloud integrations are also unqualified.

Duplicate tracking covers identical files or decoded pixels inside one project, not every crop or lossy recompression. **A dataset export is not a full project backup.** Room migrations do not recover another application’s data or an uninstalled app’s private storage.

## Distribution

rc6 uses the durable signing key. Differently signed rc4/rc5 Debug installations cannot be updated directly; preserve their data. The current key and backup have been checked on two disks in the same PC. An off-machine backup is still needed.

Remote CI is prepared but has not run. The 109-dependency inventory is available; native transitive notice review remains open. [Signing](../SIGNING.md) · [Licensing](../../LICENSING_STATUS.md) · [Roadmap](ROADMAP.md).
