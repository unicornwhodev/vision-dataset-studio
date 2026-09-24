![Cadryl — Un regard. Un dataset.](docs/brand/cadryl-hero.png)

# Cadryl

**An Android workspace for turning images into datasets.**

Import your images, review the model’s suggestions, make your corrections and export. Cadryl brings those steps together while keeping your annotations in your hands. You can work entirely by hand, or train a compatible model on batches you have reviewed.

An independent project by **Unicorn Who Dev**, previously called *Vision Dataset Studio*.

[Get rc6](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6) · [Your first batch](docs/en/GETTING_STARTED.md) · [Documentation](docs/en/README.md) · [Français](README.md)

## What you can do

- **Organise images.** Use a local folder or Hugging Face source, process small batches, track duplicates and return to your project later.
- **Annotate with some help.** Boxes, points and masks: the model suggests, you edit and decide. Saved human corrections stay protected.
- **Export a usable dataset.** Keep the full JSONL, plus COCO, YOLO, WebDataset or vision-language exports where appropriate. Copies must be read back before cleanup.
- **Keep improving a model copy.** The first training run creates a separate version. Later runs continue from its last validated weights. The original remains available.

Training is optional and off by default. **The APK includes no model weights or datasets.** Manual annotation works without a model.

## Inside the app

<img src="test-results/rc6-release/arm/home.png" alt="Cadryl rc6 running on the test emulator" width="260">

This is a real emulator screenshot. The banner is an illustration; [visual sources are documented](docs/VISUALS.md).

## Try it

You need **Android 9 or later and an ARM64 phone**. Download `vision-dataset-studio.apk` from the release and start with a few images you have permission to use. The technical APK filename and Android application ID stay unchanged for continuity.

**Already using rc4 or rc5?** Those Debug APKs use different signing keys. rc6 cannot update them directly; keep the installation and its data. [Installation and signing](docs/en/GETTING_STARTED.md#install-cadryl).

The documented public model sources are [Charlbi’s conversions](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) and [FireViewer’s models](https://huggingface.co/fireviewer/litert-models). Check each variant’s results before choosing it. Loading successfully says nothing about accuracy on your images.

## Project status

**4.2.0-rc6 is a prerelease.** Both signed Release APKs pass 40 core tests and four UI scenarios on the Android 16 emulator with 16 KB pages. Strict native alignment checks also pass. ARM64 runs through translation there; a physical ARM 16 KB phone is still needed.

Next up: the final candidate on a phone, longer sessions, model quality, remote CI and the remaining native licence notices. The historical ART crash has no confirmed root cause. [Test results](docs/en/VALIDATION.md) · [Known limits](docs/en/KNOWN_LIMITATIONS.md) · [Roadmap](docs/en/ROADMAP.md).

## Work on the app

The app uses **Kotlin, Compose, Room and LiteRT**. The [development guide](docs/en/DEVELOPMENT_RESUME.md) covers native dependencies, Windows/Linux builds and tests. Start with the [architecture](docs/en/ARCHITECTURE.md) to find your way around the code.

Found a bug or have an idea? [Open an issue](https://github.com/unicornwhodev/vision-dataset-studio/issues) with the version, device and steps to reproduce. Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

Project code is [Apache-2.0](LICENSE). Models, datasets and third-party components retain their own terms. [Licensing status](LICENSING_STATUS.md).
