<p align="center">
  <img src="docs/logo.svg" width="128" height="128" alt="OpenWispr Logo">
</p>

# OpenWispr-S1

> [!WARNING]
> **Project status: active maturation.**
>
> OpenWispr-S1 is functional, but it is still under active development and validation. Builds may contain bugs, change frequently, and include behavior that has not yet been broadly tested across devices.
>
> **General or production use is not recommended yet.** Canary and Beta builds are intended for testing, and the APKs currently published here should be treated as development builds.
>
> When a Stable release has completed the project's validation process, it will be clearly identified as the recommended build for normal use.

OpenWispr-S1 is an **independent derivative project based on [OpenWhispr by EdiBianco](https://github.com/EdiBianco/OpenWhispr)**, distributed under the Apache License 2.0.

This repository is not affiliated with or endorsed by the original maintainer. If you want the history, rationale and documentation of the original project, please visit the upstream repository above.

## Our story

This fork began as a personal experiment to make Android push-to-talk dictation fit a very specific daily workflow: tap, speak, get clean text into the active field, and get out of the way.

The project now focuses on low-friction dictation, predictable overlay behaviour, direct text insertion when Android exposes a suitable editable field, careful fallback behaviour, and a deliberately conservative release process.

Development is tested heavily on Samsung / One UI because that is the primary device environment used for this fork. That is simply our main test platform; OpenWispr-S1 is not presented as an official Samsung or Android component.

## What this fork currently adds

- Refined floating bubble / dot behaviour.
- Configurable bubble size, dot size, transparency and dot timeout.
- Direct accessibility text insertion first, with clipboard only as a compatibility fallback.
- Additional recovery and overlay controls.
- Separate release channels for **Stable**, **Beta** and **Canary** development.
- Ongoing UX and reliability work.

The underlying transcription and post-processing architecture comes from OpenWhispr. Please see the upstream project for its broader feature history.

## Release channels

- **Stable**: the current proven build on `main`.
- **Beta**: changes that have passed initial testing and are candidates for Stable.
- **Canary**: experimental work and early validation.

The Android package IDs are intentionally separate for the three channels so they can coexist on one device. Only one OpenWispr accessibility service should be enabled at a time.

## Privacy

OpenWispr-S1 can use local transcription or cloud services depending on configuration.

When Groq cloud transcription or cleanup is enabled, audio and/or transcript text is sent directly from the device to Groq using the user's own API key. This project does not operate a separate backend.

See [PRIVACY.md](PRIVACY.md) for the inherited privacy documentation and review your own configuration before using cloud features with sensitive material.

## Installation

Prebuilt test APKs are published in this repository's [Releases](https://github.com/sromero78/OpenWhispr-S1/releases).

These builds are development/test builds. Production distribution would require a separate production signing and release process.

## Build from source

Requires JDK 17 and the Android SDK.

```bash
git clone https://github.com/sromero78/OpenWhispr-S1.git
cd OpenWhispr-S1
./gradlew assembleDebug
```

## Attribution and license

OpenWispr-S1 is derived from [EdiBianco/OpenWhispr](https://github.com/EdiBianco/OpenWhispr), which in turn documents its lineage from [kafkasl/phone-whisper](https://github.com/kafkasl/phone-whisper).

See [ATTRIBUTION.md](ATTRIBUTION.md) for the attribution statement and modification notice.

The original Apache License 2.0 is preserved in [LICENSE](LICENSE). Modified source files in this fork carry a modification notice as required by the license.
