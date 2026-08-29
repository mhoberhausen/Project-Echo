# sherpa-onnx diarization notices

The optional offline speaker-diarization pipeline uses these pinned upstream artifacts:

- sherpa-onnx v1.13.6 Kotlin/JNI API and Android runtime, Apache License 2.0:
  https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.6
- ONNX Runtime, MIT License:
  https://github.com/microsoft/onnxruntime
- pyannote segmentation 3.0 converted model, MIT License, copyright 2022 CNRS:
  https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-segmentation-models
- 3D-Speaker ERes2Net speaker-embedding model, Apache License 2.0:
  https://github.com/modelscope/3D-Speaker

The Kotlin bindings under `app/src/main/java/com/k2fsa/sherpa/onnx` are adapted from
sherpa-onnx v1.13.6. The setup script records exact download URLs and SHA-256 hashes.

The Apache License 2.0 text is available at https://www.apache.org/licenses/LICENSE-2.0.
The MIT License text is available at https://opensource.org/license/mit.
