#pragma once

#include <Arduino.h>
#include <atomic>

#include "../audio/PdmAudioCapture.h"
#include "AudioFrameQueue.h"
#include "ConnectionState.h"

namespace huh::runtime {

class StreamingController {
 public:
  explicit StreamingController(audio::PdmAudioCapture& capture)
      : capture_(capture) {}

  bool begin(size_t queueCapacityFrames);
  bool startCapture(int clockPin, int dataPin);
  bool tryTakeFrame(audio::AudioFrame& frame, TickType_t waitTicks = 0);
  void stopCapture();
  void clearQueuedFrames() { queue_.clear(); }

  uint32_t capturedFrames() const { return capturedFrames_.load(); }
  uint32_t overrunCount() const { return queue_.overrunCount(); }
  size_t queueHighWaterMark() const { return queue_.highWaterMark(); }
  size_t queuedFrames() const { return queue_.waiting(); }
  bool captureFailed() const { return captureFailed_.load(); }
  ConnectionState state() const { return state_.load(); }
  void setState(ConnectionState state);

 private:
  static void captureTaskEntry(void* context);
  void captureLoop();

  audio::PdmAudioCapture& capture_;
  AudioFrameQueue queue_;
  TaskHandle_t captureTask_ = nullptr;
  std::atomic<bool> captureRequested_{false};
  std::atomic<uint32_t> capturedFrames_{0};
  std::atomic<bool> captureFailed_{false};
  std::atomic<ConnectionState> state_{ConnectionState::kDisconnected};
};

}  // namespace huh::runtime
