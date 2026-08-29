`ggml-tiny.en.bin`, `ggml-base.en.bin`, and `gemma3-1b-it-int4.litertlm` are required
here when building the complete offline app.
Model binaries are ignored by Git because of their size. Download both with the pinned
upstream script:

```powershell
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd tiny.en app\src\main\assets\models
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd base.en app\src\main\assets\models
```

Gemma is license-gated. Accept its terms at
https://huggingface.co/litert-community/Gemma3-1B-IT, authenticate locally, then download
`gemma3-1b-it-int4.litertlm` into this directory. The expected size is 584,417,280 bytes.

Optional local speaker diarization uses sherpa-onnx. Run:

```powershell
.\scripts\setup-sherpa-onnx.ps1
```

from `mobile/android` to install the pinned arm64 runtime plus segmentation and speaker-
embedding models. Without those ignored artifacts, the app intentionally uses the
pass-through diarization implementation.
