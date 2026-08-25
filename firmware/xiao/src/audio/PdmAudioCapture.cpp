#include "PdmAudioCapture.h"

namespace huh::audio {

static_assert(__BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__,
              "HUH1 PCM transmission requires a little-endian ESP target");

bool PdmAudioCapture::begin(int clockPin, int dataPin) {
  if (running_) return true;

  assembler_.reset();
  i2s_.setPinsPdmRx(clockPin, dataPin);
  running_ = i2s_.begin(I2S_MODE_PDM_RX, kSampleRateHz,
                        I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO);
  return running_;
}

bool PdmAudioCapture::readFrame(AudioFrame& frame) {
  if (!running_) return false;

  while (assembler_.filledBytes() < kBytesPerFrame) {
    uint8_t chunk[kBytesPerFrame];
    const size_t wanted = assembler_.bytesNeeded();
    const size_t received =
        i2s_.readBytes(reinterpret_cast<char*>(chunk), wanted);
    if (received == 0 || received > wanted) return false;
    if (assembler_.append(chunk, received, frame)) return true;
  }
  return false;
}

void PdmAudioCapture::end() {
  if (running_) i2s_.end();
  running_ = false;
  assembler_.reset();
}

}  // namespace huh::audio
