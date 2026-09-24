# What comes next for Cadryl

[Documentation](README.md) · [Français](../ROADMAP.md)

The priority is a reliable phone workflow. New features should follow feedback on that foundation.

## Finish candidate acceptance

- Run the final runtime and both Release suites on Honor, then check a signed update with data preserved.
- Test a physical ARM phone with 16 KB pages. Strict alignment is fixed; hardware evidence is missing.
- Repeat longer sessions and observe background restrictions. Keep any ART crash traces; the historical cause is still unknown.

## Data and reproducibility

Extend interruption and large-transfer coverage using authorised test destinations. Check an older database from real use, working from a backup. Reproduce the build on another machine and run the prepared CI.

## Model quality

Rerun the catalogue on the new runtime, complete missing variants and bundles, then measure quality on independent data. The Charlbi campaign currently has 13/25 passes, one timeout and eleven unrun variants. Its eight successful training runs do not establish an accuracy improvement.

## Durable distribution

Keep the same signing key, add an off-machine backup, finish native notice review and track APK size. Models remain separate downloads. Use the [known limits](KNOWN_LIMITATIONS.md) and [test results](VALIDATION.md) before each release.
