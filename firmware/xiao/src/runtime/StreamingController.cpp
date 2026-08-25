#include "StreamingController.h"

namespace huh::runtime {

bool StreamingController::begin(size_t queueCapacityFrames) {
  return queue_.begin(queueCapacityFrames);
}

bool StreamingController::startCapture(int clockPin, int dataPin) {
  if (captureTask_ != nullptr) return true;
  captureFailed_.store(false);
  if (!capture_.begin(clockPin, dataPin)) {
    captureFailed_.store(true);
    setState(ConnectionState::kError);
    return false;
  }

  captureRequested_.store(true);
  if (xTaskCreatePinnedToCore(captureTaskEntry, "pdm-capture", 4096, this,
                              3, &captureTask_, 0) != pdPASS) {
    captureRequested_.store(false);
    capture_.end();
    setState(ConnectionState::kError);
    return false;
  }
  return true;
}

bool StreamingController::tryTakeFrame(audio::AudioFrame& frame,
                                       TickType_t waitTicks) {
  return queue_.tryPop(frame, waitTicks);
}

void StreamingController::stopCapture() {
  captureRequested_.store(false);
  // PDM reads are short and return regularly; let the task release I2S itself.
  const uint32_t deadline = millis() + 250;
  while (captureTask_ != nullptr &&
         static_cast<int32_t>(deadline - millis()) > 0) {
    delay(1);
  }
  if (captureTask_ != nullptr) {
    vTaskDelete(captureTask_);
    captureTask_ = nullptr;
    capture_.end();
  }
}

void StreamingController::setState(ConnectionState state) {
  if (state_.exchange(state) == state) return;
  Serial.printf("State: %s\n", connectionStateName(state));
}

void StreamingController::captureTaskEntry(void* context) {
  static_cast<StreamingController*>(context)->captureLoop();
}

void StreamingController::captureLoop() {
  while (captureRequested_.load()) {
    audio::AudioFrame frame;
    if (!capture_.readFrame(frame)) {
      captureFailed_.store(true);
      setState(ConnectionState::kError);
      break;
    }
    capturedFrames_.fetch_add(1);
    queue_.tryPush(frame);
  }
  capture_.end();
  captureTask_ = nullptr;
  vTaskDelete(nullptr);
}

}  // namespace huh::runtime
