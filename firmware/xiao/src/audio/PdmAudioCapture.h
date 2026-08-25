#pragma once

#include <ESP_I2S.h>

#include "AudioFrame.h"

namespace huh::audio {

class PdmAudioCapture {
 public:
  bool begin(int clockPin, int dataPin);
  bool readFrame(AudioFrame& frame);
  void end();
  bool isRunning() const { return running_; }

 private:
  I2SClass i2s_;
  AudioFrameAssembler assembler_;
  bool running_ = false;
};

}  // namespace huh::audio
