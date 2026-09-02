# Changelog

All notable user-facing changes to Huh? Android are documented in this file.

This project follows the spirit of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and uses [semantic versioning](https://semver.org/). 

## [0.1.1] - 2026-09-01

### Changed

- The Play release now declares only microphone foreground capture and data-sync foreground
  work. Local audio-to-text work is not presented as media transcoding.
- Huh? Puck live capture is not included in this release. Phone active listening, saved
  recordings, local transcription, session history, and on-device privacy behavior remain.

## [0.1.0] - 2026-09-01

### Added

- First public release of Huh?, an offline-first conversation-memory app.
- Local phone recording and transcription with the bundled Accurate (`base.en`) Whisper model.
- Active listening with visible foreground-service controls and local transcription processing.
- Session history with transcript review, editing, speaker labels, filtering, sharing, and deletion.
- Optional on-device AI interpretation for concise takeaways.
- Privacy-first defaults: recordings and transcripts stay on the device; optional diagnostics are
  disabled by default.

### Google Play release notes

> Welcome to Huh? — a private, on-device way to capture what was said.
>
> - Record and transcribe conversations locally
> - Review and organize captured sessions
> - Keep your voice data and transcripts on your device
> - Optional on-device AI interpretation for clearer takeaways
>
> Thanks for being an early user.
