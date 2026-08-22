# Echo Keep Agent Guidance

## Product Context

Echo Keep is an offline-first Android voice-capture app. The core flow records speech,
transcribes it locally with bundled Whisper models, removes filler words, and optionally
processes the transcript locally with Gemma. Do not introduce cloud services, analytics,
or the Android `INTERNET` permission unless the user explicitly changes that requirement.

The primary target device is a Pixel 8 Pro running Android 17. The UI is built with Kotlin
and Jetpack Compose. Preserve the existing Manual / Live mode shell and clearly label
placeholder behavior that is not implemented.

## Project Skills

- Use `$android-cli` for applicable Android SDK, build, emulator, connected-device,
  documentation, screenshot, layout-inspection, and deployment work. Follow its inspection
  requirements when visually checking the app.
- Use `$mobile-developer` for Android application architecture and implementation,
  lifecycle and state handling, permissions, offline storage, native/JNI boundaries,
  performance, and mobile test strategy. Combine it with `$android-cli` when the work
  requires SDK documentation, builds, deployment, or device diagnostics.
- Use `$ui-designer` for UI/UX design, visual refinements, component or screen changes,
  accessibility reviews, theming, and design QA. For implementation tasks, combine it with
  `$mobile-developer` and `$android-cli` so the behavior and rendered result are both
  verified on the target device when available.

## UI Expectations

- Follow Android and Material interaction conventions while retaining Echo Keep's visual
  identity.
- Support light and dark themes, system insets, visible status/navigation controls, font
  scaling, semantic labels, and at least 48dp touch targets.
- Keep unfinished Live Mode and chat-history features honest; do not present placeholders
  as active capture or persisted data.
- Prefer focused improvements to the existing component language over unrelated redesigns.

## Verification

Use the Android Studio bundled JDK when running Gradle unless the project configuration
explicitly changes. For meaningful implementation changes, run tests, lint, and a debug
build in proportion to the change. For UI changes, inspect the affected states on the
connected Pixel when available and capture screenshots when visual confirmation matters.

Preserve user changes and update `SPEC.md` when completed work materially changes the
documented product scope or implementation status.
