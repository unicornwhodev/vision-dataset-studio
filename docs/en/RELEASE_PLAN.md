# Ship Cadryl

[Documentation](README.md) · [Français](../RELEASE_PLAN.md)

Current delivery: **4.2.0-rc6**, a prerelease signed with the durable key. Files keep their technical `vision-dataset-studio` names for existing links and scripts. [rc6 contents and results](../RC6_RELEASE.md).

## Published files

- `vision-dataset-studio.apk`: ARM64 Release app, Android 9+, no model weights.
- `vision-dataset-studio-4.2.0-rc6-qualification.zip`: ARM64/x86_64 app/test APKs, UI driver, docs, notices and selected evidence.
- Maven ZIPs for Flex `2.16.1-vds16k1`, Graphics Path `1.0.1-vds16k1` and LiteRT `2.2.0-vds16k2`.
- `PACKAGE.json`, `PUBLICATION_CHECKS.json` and `SHA256SUMS`: provenance and integrity.

Assets are available through the [GitHub release](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6). The [GHCR package](https://github.com/users/unicornwhodev/packages/container/package/vision-dataset-studio-qualification) remains private. It holds artifacts, not a runnable container.

## Prepare a release

Build both Release app/test pairs, sign with the same durable key and test the signed bytes. Retain failures and partial results. Select public evidence without secrets, weights or user datasets. Follow [Release testing](../RELEASE_TESTING.md).

After review and commit, `tools/package_native_release.py` checks a clean tree, compiled sources against Git, APKs against signing/test receipts and strict native alignment. It refuses to overwrite an existing delivery folder. Its build, signing, evidence and UI-driver arguments must identify the exact tested attempts.

Review archives before upload. Use a new tag rather than overwriting an existing release. Verify GitHub asset hashes and sizes after publication, then pull back and check the OCI layers. [Publication checks](../PUBLICATION_CHECKS.md).

## Updates

Keep the Android ID and [durable key](../SIGNING.md), then increase `versionCode`. rc4/rc5 Debug certificate incompatibility stays explicit. Uninstalling is not a data migration.

Prepared CI has not run for this delivery. Hardware gaps, the historical ART cause and pending native notices remain visible. rc6 is not labelled stable. The [rc5 publication receipt](../RC5_PUBLICATION_RECEIPT.json) is retained.
