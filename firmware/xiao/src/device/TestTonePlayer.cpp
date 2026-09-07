#include "TestTonePlayer.h"

namespace huh::device {
namespace {
constexpr uint16_t kFrequencies[] = {880, 0, 1175, 0, 1568};
// Keep the test acknowledgement deliberately brief so it is unmistakable but
// does not distract from normal capture use: 60 + 30 + 60 + 30 + 90 = 270 ms.
constexpr uint16_t kDurationsMs[] = {60, 30, 60, 30, 90};
}

bool TestTonePlayer::begin(int reservedPin) {
  if (tonePin_ >= 0 && tonePin_ != reservedPin && !toneAvailable_) {
    toneAvailable_ = ledcAttach(static_cast<uint8_t>(tonePin_), 880, 8);
    if (toneAvailable_) ledcWriteTone(static_cast<uint8_t>(tonePin_), 0);
  }
  ledAvailable_ = false;
  if (ledPin_ >= 0 && ledPin_ != reservedPin && ledPin_ != tonePin_) {
    pinMode(ledPin_, OUTPUT);
    ledAvailable_ = true;
    setLed(false);
  }
  available_ = toneAvailable_ || ledAvailable_;
  return available_;
}

bool TestTonePlayer::play() {
  if (!available_ || playing_) return false;
  playing_ = true;
  setStep(0);
  return true;
}

void TestTonePlayer::poll() {
  if (!playing_ || millis() - stepStartedAt_ < kDurationsMs[step_]) return;
  ++step_;
  if (step_ >= sizeof(kFrequencies) / sizeof(kFrequencies[0])) {
    if (toneAvailable_) ledcWriteTone(static_cast<uint8_t>(tonePin_), 0);
    setLed(false);
    playing_ = false;
    return;
  }
  setStep(step_);
}

void TestTonePlayer::setStep(uint8_t step) {
  step_ = step;
  stepStartedAt_ = millis();
  if (toneAvailable_) ledcWriteTone(static_cast<uint8_t>(tonePin_), kFrequencies[step_]);
  setLed(kFrequencies[step_] != 0);
}

void TestTonePlayer::setLed(bool on) {
  if (!ledAvailable_) return;
  digitalWrite(ledPin_, (on != ledActiveLow_) ? HIGH : LOW);
}

}  // namespace huh::device
