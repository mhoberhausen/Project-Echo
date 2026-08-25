#include "AudioFrameQueue.h"

namespace huh::runtime {

AudioFrameQueue::~AudioFrameQueue() {
  if (queue_ != nullptr) vQueueDelete(queue_);
}

bool AudioFrameQueue::begin(size_t capacityFrames) {
  if (queue_ != nullptr || capacityFrames == 0) return false;
  queue_ = xQueueCreate(capacityFrames, sizeof(audio::AudioFrame));
  if (queue_ == nullptr) return false;
  capacity_ = capacityFrames;
  return true;
}

bool AudioFrameQueue::tryPush(const audio::AudioFrame& frame) {
  if (queue_ == nullptr) return false;
  if (xQueueSend(queue_, &frame, 0) == pdTRUE) {
    const size_t waitingFrames = uxQueueMessagesWaiting(queue_);
    size_t previous = highWaterMark_.load();
    while (waitingFrames > previous &&
           !highWaterMark_.compare_exchange_weak(previous, waitingFrames)) {}
    return true;
  }
  overruns_.fetch_add(1);
  return false;
}

bool AudioFrameQueue::tryPop(audio::AudioFrame& frame, TickType_t waitTicks) {
  return queue_ != nullptr && xQueueReceive(queue_, &frame, waitTicks) == pdTRUE;
}

void AudioFrameQueue::clear() {
  if (queue_ != nullptr) xQueueReset(queue_);
}

size_t AudioFrameQueue::waiting() const {
  return queue_ == nullptr ? 0 : uxQueueMessagesWaiting(queue_);
}

}  // namespace huh::runtime
