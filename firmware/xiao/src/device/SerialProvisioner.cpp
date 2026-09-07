#include "SerialProvisioner.h"

#include <cstring>

namespace huh::device {
namespace {
void secureClear(char* bytes, size_t length) {
  volatile char* cursor = bytes;
  while (length-- > 0) *cursor++ = 0;
}
}

void SerialProvisioner::begin(bool hasCredentials) {
  Serial.println("Commands: wifi status | wifi setup | wifi reset | captures | signal test");
  if (!hasCredentials) startSetup();
}

ProvisioningEvent SerialProvisioner::poll() {
  while (Serial.available() > 0) {
    const char value = static_cast<char>(Serial.read());
    if (value == '\r') continue;
    if (value == '\n') return finishLine();
    if (value == '\b' || value == 0x7F) {
      if (lineLength_ > 0) line_[--lineLength_] = 0;
      continue;
    }
    if (lineLength_ >= sizeof(line_) - 1) {
      lineOverflow_ = true;
      continue;
    }
    line_[lineLength_++] = value;
    line_[lineLength_] = 0;
  }
  return ProvisioningEvent::kNone;
}

void SerialProvisioner::startSetup() {
  clearSensitiveBuffers();
  state_ = State::kSsid;
  Serial.println("Huh? Puck Wi-Fi setup");
  Serial.print("SSID: ");
}

ProvisioningEvent SerialProvisioner::finishLine() {
  if (lineOverflow_) {
    Serial.println("Input rejected: line is too long.");
    resetLine();
    if (state_ == State::kPassword) Serial.print("Password (input hidden): ");
    if (state_ == State::kSsid) Serial.print("SSID: ");
    return ProvisioningEvent::kNone;
  }

  ProvisioningEvent event = ProvisioningEvent::kNone;
  if (state_ == State::kCommands) {
    if (strcmp(line_, "wifi status") == 0) {
      event = ProvisioningEvent::kStatusRequested;
    } else if (strcmp(line_, "captures") == 0) {
      event = ProvisioningEvent::kCapturesRequested;
    } else if (strcmp(line_, "signal test") == 0) {
      event = ProvisioningEvent::kConnectionTestRequested;
    } else if (strcmp(line_, "wifi setup") == 0) {
      startSetup();
    } else if (strcmp(line_, "wifi reset") == 0) {
      if (store_.clear()) {
        Serial.println("Stored Wi-Fi credentials erased.");
        event = ProvisioningEvent::kCredentialsReset;
        startSetup();
      } else {
        Serial.println("ERROR: NVS credentials could not be erased.");
      }
    } else if (lineLength_ > 0) {
      Serial.println("Unknown command. Use: wifi status | wifi setup | wifi reset | captures | signal test");
    }
  } else if (state_ == State::kSsid) {
    if (lineLength_ == 0 || lineLength_ > 32) {
      Serial.println("SSID must contain 1 to 32 bytes.");
      Serial.print("SSID: ");
    } else {
      memcpy(ssid_, line_, lineLength_ + 1);
      state_ = State::kPassword;
      Serial.print("Password (input hidden): ");
    }
  } else {
    if (lineLength_ > 63) {
      Serial.println("Password must contain at most 63 bytes.");
      Serial.print("Password (input hidden): ");
    } else if (store_.save(ssid_, line_)) {
      Serial.println("Saving credentials... done.");
      clearSensitiveBuffers();
      state_ = State::kCommands;
      event = ProvisioningEvent::kCredentialsSaved;
    } else {
      Serial.println("ERROR: Credentials could not be persisted to NVS.");
      clearSensitiveBuffers();
      state_ = State::kCommands;
    }
  }
  resetLine();
  return event;
}

void SerialProvisioner::resetLine() {
  secureClear(line_, sizeof(line_));
  lineLength_ = 0;
  lineOverflow_ = false;
}

void SerialProvisioner::clearSensitiveBuffers() {
  secureClear(ssid_, sizeof(ssid_));
  resetLine();
}

}  // namespace huh::device
