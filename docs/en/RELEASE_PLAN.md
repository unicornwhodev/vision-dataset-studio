# Windows qualification prerelease — 4.2.0-rc5

**Verified publication :** [v4.2.0-rc5](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5) · [JSON](../RC5_PUBLICATION_RECEIPT.json).

Build `20260923T104142Z-befce3e3596a` passed 67 JVM tests and 39/39 Android core
tests on both 4 KB and 16 KB API 35 emulators. The Python suite passed 70 tests
before packaging (63 at build time plus seven packaging checks). The original
model stays intact; later training continues its separate learned version.

**rc5 cannot update rc4:** the Windows Debug certificate differs from the one
used on the previous VM, whose private key is unavailable on this workstation.
Keep existing installations and their data. Do not uninstall an installation
containing data to work around the signature mismatch. Private signing keys
are excluded from Git and packages.

Assets: Debug user APK, qualification ZIP with instrumentation APK and selected
evidence, rebuilt Flex local Maven runtime, unsigned 100.2 MB ARM64 Release
candidate, `PACKAGE.json` and `SHA256SUMS`. The unsigned candidate is not directly
installable. GHCR distributes an artifact package and retains its existing
private visibility; GitHub release assets are public.

`tools/package_verified_release.py` verifies 162 of 163 recorded files byte for
byte against Git blobs. The only difference is CRLF-to-LF normalization of the
host Python bootstrap script; both hashes are recorded. Android source bytes
are identical. `PACKAGE.json` binds the tested bytes to that commit;
the original receipts retain their earlier base commit. Physical ARM, whole-app
16 KB qualification, real HF writes, model quality, durable signing and the
remaining native transitive notices are still open. Remote CI has not run for
this build. See the [Windows evidence](../WINDOWS_QUALIFICATION_2026_09.md),
[publication checks](../PUBLICATION_CHECKS.md) and [packaging command](../RELEASE_PLAN.md).

## Historical rc4 distribution

# Publication — 4.2.0-rc4

[Français](../RELEASE_PLAN.md). Public repository: [unicornwhodev/vision-dataset-studio](https://github.com/unicornwhodev/vision-dataset-studio). Apache-2.0 licence.

rc4 is a **Debug qualification prerelease**. It publishes the current development snapshot requested by the owner, with the partial LiteRT matrix and open defects. It is not a stable release.

## Artifacts

- `vision-dataset-studio.apk`: the single user-facing APK, with no model weights.
- `vision-dataset-studio-4.2.0-rc4-qualification.zip`: user APK, test-only instrumentation APK, receipts, results, FR/EN documentation and licence.
- `PACKAGE.json`, `SHA256SUMS` and OCI digest: provenance and integrity.

`ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc4` distributes that archive as OCI, **not a runnable image**. It is linked to the GitHub repository; package visibility is currently private and separate from repository visibility. Release assets are public. With authorized GHCR access:

```bash
oras pull ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc4
```

`tools/package_workstation_release.py` requires a clean Git tree, a complete compiled-source manifest matching the commit, APK hashes and Android evidence from the same build. It refuses embedded weights and failed or skipped instrumented suites. Development-build model receipts remain distinct.

```bash
python3 tools/package_workstation_release.py --build-dir dist/rc4-build \
  --evidence test-results/stabilization-rc4 --version 4.2.0-rc4
```

The historical rc2 packager and CI packager remain separate. No CI receipt is fabricated. The [latest Actions attempt](https://github.com/unicornwhodev/vision-dataset-studio/actions/runs/35539418339) was refused before execution because of an account billing issue. The build Dockerfile is not qualified.

## Signing and resumption

The rc4 Debug key is backed up outside Git for future builds. The owner confirmed that nobody downloaded rc2; no rc2 distribution migration is planned. A future stable release requires durable signing, notice review and physical-device QA.

Sources, SDK, emulators, conversions and evidence remain under `/workspace` on the persistent volume. APKs and public evidence are also backed up to the Chromebook before pod shutdown. No credentials, weights or private keys are published.

## rc4 QA

The rc4 build evidence is in [`test-results/stabilization-rc4/`](../../test-results/stabilization-rc4/). The core suite runs separately from the two model classes requiring explicit fixtures/options. EdgeNeXt and RepViT have separate Android receipts; unexecuted models remain unqualified. September 20 and the preceding stabilization build retain their original evidence and build identifiers.
