# Cadryl test results

[Project home](../../README.en.md) · [Français](../../TEST_REPORT.md)

Results belong to a specific build, APK and device. The current candidate is kept separate from earlier campaigns.

## Current candidate: 4.2.0-rc6

| Check | Result |
|---|---|
| JVM | 70 passed, no failures or skips |
| Python tooling | 87 passed |
| Android lint | 0 errors, 96 warnings |
| Signed ARM64 Release | 40/40 core and 4/4 UI on API 36 / 16 KB emulator with ARM translation |
| Signed x86_64 Release | 40/40 core and 4/4 UI on the same emulator |
| Native alignment | All four libraries per APK pass strict ELF and ZIP checks |
| Model continuity | Original preserved, Save/Restore and continued training verified on synthetic data |

[Release note](../RC6_RELEASE.md) · [Evidence](../../test-results/rc6-release/README.md) · [Summary](../../test-results/rc6-release/summary.json) · [Machine-readable status](../../QUALIFICATION_STATUS.json).

ARM64 uses `libndk_translation` on an x86_64 host. **This is not phone acceptance.** These are the actual minified, signed Release APKs. External HF writes, fault campaigns and model catalogue coverage remain separate.

Early driver runs retained two keyboard-related failures: 39/40 x86_64 core and 3/4 ARM64 UI. The waits were fixed without removing scenarios. The final campaign uses Cadryl-branded APKs.

## Earlier evidence

[Native fixes](../NATIVE_FIX_2026_09.md), [native investigation](../NATIVE_FOLLOWUP_2026_09.md), [Release/Honor](../RELEASE_CLOSURE_2026_09.md), [background work](../RELEASE_HARDENING_2026_09.md), [data and interruptions](../P1_QUALIFICATION_2026_09.md), [Windows/rc5](../WINDOWS_QUALIFICATION_2026_09.md), [LiteRT catalogue](LITERT_QUALIFICATION.md) and [functional audit](../FUNCTIONAL_AUDIT_2026_09.md).

Dated reports may use the old product name. Their original receipts remain unchanged; older successes do not automatically qualify rc6.

## Still pending

The candidate on Honor and physical ARM 16 KB, longer sessions, catalogue tests on the new runtime, model quality and remote CI. The historical ART crash still has no confirmed cause. [Known limits](KNOWN_LIMITATIONS.md) · [Android matrix](../ANDROID_QUALIFICATION.md) · [Release testing](../RELEASE_TESTING.md).
