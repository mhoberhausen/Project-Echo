#pragma once

#include <Arduino.h>
#include <atomic>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include "../audio/AudioFrame.h"

namespace huh::runtime {

class AudioFrameQueue {
 public:
  ~AudioFrameQueue();

  bool begin(size_t capacityFrames);
  bool tryPush(const audio::AudioFrame& frame);
  bool tryPop(audio::AudioFrame& frame, TickType_t waitTicks = 0);
  void clear();
  size_t waiting() const;
  uint32_t overrunCount() const { return overruns_.load(); }
  size_t highWaterMark() const { return highWaterMark_.load(); }
  size_t capacity() const { return capacity_; }

 private:
  QueueHandle_t queue_ = nullptr;
  size_t capacity_ = 0;
  std::atomic<uint32_t> overruns_{0};
  std::atomic<size_t> highWaterMark_{0};
};

}  // namespace huh::runtime
