#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace huh::audio {

constexpr uint32_t kSampleRateHz = 16000;
constexpr uint16_t kChannelCount = 1;
constexpr uint16_t kBitsPerSample = 16;
constexpr uint16_t kFrameDurationMs = 20;
constexpr size_t kSamplesPerFrame = 320;
constexpr size_t kBytesPerFrame = kSamplesPerFrame * sizeof(int16_t);

struct AudioFrame {
  std::array<uint8_t, kBytesPerFrame> pcmLittleEndian{};
};

// Accumulates arbitrary short reads without ever exposing an incomplete frame.
class AudioFrameAssembler {
 public:
  size_t bytesNeeded() const { return kBytesPerFrame - filled_; }
  size_t filledBytes() const { return filled_; }

  // The input must not exceed bytesNeeded(). Returns true only when out is complete.
  bool append(const uint8_t* bytes, size_t length, AudioFrame& out);
  void reset() { filled_ = 0; }

 private:
  AudioFrame pending_{};
  size_t filled_ = 0;
};

}  // namespace huh::audio

