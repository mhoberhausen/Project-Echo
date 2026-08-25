#include "AudioFrame.h"

#include <cstring>

namespace huh::audio {

bool AudioFrameAssembler::append(const uint8_t* bytes, size_t length,
                                 AudioFrame& out) {
  if (bytes == nullptr || length == 0 || length > bytesNeeded()) {
    return false;
  }

  std::memcpy(pending_.pcmLittleEndian.data() + filled_, bytes, length);
  filled_ += length;
  if (filled_ != kBytesPerFrame) {
    return false;
  }

  out = pending_;
  filled_ = 0;
  return true;
}

}  // namespace huh::audio

