#pragma once

#include <Arduino.h>

namespace huh::device {

class TestTonePlayer {
 public:
  TestTonePlayer(int tonePin, int ledPin, bool ledActiveLow)
      : tonePin_(tonePin), ledPin_(ledPin), ledActiveLow_(ledActiveLow) {}
  bool begin(int reservedPin = -1);
  bool play();
  void poll();
  bool isAvailable() const { return available_; }
  bool hasLed() const { return ledAvailable_; }
  bool isPlaying() const { return playing_; }

 private:
  void setStep(uint8_t step);

  void setLed(bool on);

  int tonePin_;
  int ledPin_;
  bool ledActiveLow_;
  bool toneAvailable_ = false;
  bool ledAvailable_ = false;
  bool available_ = false;
  bool playing_ = false;
  uint8_t step_ = 0;
  uint32_t stepStartedAt_ = 0;
};

}  // namespace huh::device
