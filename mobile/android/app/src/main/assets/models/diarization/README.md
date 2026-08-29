# Offline speaker diarization models

Run `mobile/android/scripts/setup-sherpa-onnx.ps1` from PowerShell to install the pinned
sherpa-onnx arm64 runtime and the two required models. Generated `.onnx` and `.so` files
are intentionally ignored by Git.

The default configuration uses:

- `pyannote/segmentation-3.0`, converted and distributed by sherpa-onnx, MIT license.
- `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k`, Apache-2.0 license.
- sherpa-onnx v1.13.6 and ONNX Runtime, distributed in sherpa-onnx's pinned Android archive.

The segmentation model expects 16 kHz mono audio. Model and runtime downloads are verified
against SHA-256 hashes in the setup script.
