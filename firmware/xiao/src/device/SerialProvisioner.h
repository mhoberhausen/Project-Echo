#pragma once

#include <Arduino.h>

#include "WifiCredentialStore.h"

namespace huh::device {

enum class ProvisioningEvent {
  kNone,
  kCredentialsSaved,
  kCredentialsReset,
  kStatusRequested,
  kCapturesRequested,
  kConnectionTestRequested,
};

class SerialProvisioner {
 public:
  explicit SerialProvisioner(const WifiCredentialStore& store) : store_(store) {}

  void begin(bool hasCredentials);
  ProvisioningEvent poll();
  bool isProvisioning() const { return state_ != State::kCommands; }

 private:
  enum class State { kCommands, kSsid, kPassword };
  void startSetup();
  ProvisioningEvent finishLine();
  void resetLine();
  void clearSensitiveBuffers();

  const WifiCredentialStore& store_;
  State state_ = State::kCommands;
  char line_[97]{};
  size_t lineLength_ = 0;
  bool lineOverflow_ = false;
  char ssid_[33]{};
};

}  // namespace huh::device
