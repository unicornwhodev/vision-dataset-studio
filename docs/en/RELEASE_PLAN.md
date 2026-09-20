# Publication plan

[Français](../RELEASE_PLAN.md) · **English**

Target: public `unicornwhodev/vision-dataset-studio`, Apache-2.0. On 20 September 2026 the owner authorized publishing the source and documentation after checking for secrets. The repository remains under qualification: missing features and validation still block a stable release.

| Artifact | Destination | Gate |
|---|---|---|
| Source and bilingual documentation | GitHub repository | Identified tested commit, reviewed scope, no secrets or private assets |
| Debug APK and test APK | Qualification release assets | Successful receipt, correct application ID, signatures and SHA-256 |
| Test reports and screenshots | Release evidence | Executed results, no private corpus |
| Signed release APK/AAB | Stable release | Durable signing-key process and distribution qualification |
| Build environment image | GitHub Packages / GHCR | Dockerfile review, actual image build and validation |

Before any push, verify the active GitHub identity. The intended owner is `unicornwhodev`; an earlier terminal session used another account. Use interactive authentication, never copy a token into chat or project files.

Exclude model weights, user datasets, credentials, signing keys, build caches and pod-specific settings. Preserve required upstream attribution. A debug signature is not a durable distribution signature.

Every published artifact must match its build receipt. Release notes must identify the tested commit, executed and skipped tests, known limitations and supported model/runtime combinations. Pending HF, CI or phone tests must not be presented as passing.

After a clean committed CI build, `python3 tools/package_qualification.py` verifies the successful receipt and APK hashes, requires the matching `github_sha`, and creates the qualification ZIP under `dist/packages/`. It must not be bypassed using a fabricated CI SHA. No package or release has been published.

**Actual outcome:** the [first Actions attempt](https://github.com/unicornwhodev/vision-dataset-studio/actions/runs/35478811998)
was refused before execution because of an account billing issue. The build image has
not been built or published. The prerelease uses the tested pod build.
`tools/package_pod_qualification.py` checks all 155 source hashes at the tested commit,
unchanged Android code, APK bytes and the 14+2 Android evidence. It does not invent a CI
receipt or bypass the separate CI package guard.

`ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc2` is an OCI artifact
containing the qualification ZIP, not a runnable build image. Download it with:

```bash
oras pull ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc2
```

New GHCR packages are private by default; visibility is separate from the GitHub
repository. If needed, the owner changes it to public in package settings. The public
GitHub release also provides the same files and SHA-256 checksums.

For the future build image, the manual
`publish-qualification.yml` workflow builds `packaging/Dockerfile`, actually compiles
the app inside it and runs JVM/lint checks. It publishes that same image at
`ghcr.io/unicornwhodev/vision-dataset-studio-build` and creates a **prerelease** with
the user APK, SHA-256 checksums, image digest and bilingual qualification ZIP.
The instrumentation APK remains inside the technical ZIP. The Docker context contains
no credentials or weights; publication uses the ephemeral GitHub Actions token.
This workflow does not replace API 28/35 instrumentation or complete phone/model QA.
