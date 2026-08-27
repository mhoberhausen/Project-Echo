#pragma once

#include <Arduino.h>
#include <FS.h>
#include <SD.h>

#include "../audio/AudioFrame.h"
#include "../protocol/HuhAudioProtocol.h"

namespace huh::storage {

class SdWavRecorder {
 public:
  bool begin();
  void end();
  bool start(const protocol::StreamUuid& stream);
  bool append(const audio::AudioFrame& frame);
  bool finish();
  File openFinalized();
  bool removeFinalized();

  bool isMounted() const { return mounted_; }
  bool isRecording() const { return static_cast<bool>(output_); }
  uint32_t audioBytes() const { return audioBytes_; }
  const String& finalizedPath() const { return finalizedPath_; }

 private:
  static bool writeHeader(File& file, uint32_t dataBytes);
  static String uuidText(const protocol::StreamUuid& stream);

  bool mounted_ = false;
  File output_;
  String partialPath_;
  String finalizedPath_;
  uint32_t audioBytes_ = 0;
};

}  // namespace huh::storage
