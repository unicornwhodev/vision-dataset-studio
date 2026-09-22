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
