#pragma once

#include <Arduino.h>
#include <FS.h>
#include <SD.h>
#include <vector>

#include "../audio/AudioFrame.h"
#include "../protocol/HuhAudioProtocol.h"

namespace huh::storage {

struct FinalizedCapture {
  protocol::StreamUuid stream{};
  uint32_t audioBytes = 0;
};

class SdWavRecorder {
 public:
  bool begin();
  void end();
  bool start(const protocol::StreamUuid& stream);
  bool append(const audio::AudioFrame& frame);
  bool finish();
  File openFinalized();
  bool removeFinalized();
  size_t printFinalizedCaptures(Print& out) const;
  std::vector<FinalizedCapture> finalizedCaptures() const;
  bool selectFinalized(const protocol::StreamUuid& stream);

  bool isMounted() const { return mounted_; }
  int chipSelectPin() const { return chipSelectPin_; }
  bool isRecording() const { return static_cast<bool>(output_); }
  uint32_t audioBytes() const { return audioBytes_; }
  const String& finalizedPath() const { return finalizedPath_; }

 private:
  static bool writeHeader(File& file, uint32_t dataBytes);
  static bool validateWav(File& file, uint32_t& audioBytes);
  static String uuidText(const protocol::StreamUuid& stream);
  static bool parseCaptureName(const String& name, protocol::StreamUuid& stream);
  size_t recoverInterruptedCaptures();

  bool mounted_ = false;
  int chipSelectPin_ = -1;
  File output_;
  String partialPath_;
  String finalizedPath_;
  uint32_t audioBytes_ = 0;
};

}  // namespace huh::storage
