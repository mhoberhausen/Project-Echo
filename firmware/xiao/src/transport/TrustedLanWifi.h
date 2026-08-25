#pragma once

#include <Arduino.h>
#include <WiFi.h>

#include "../device/WifiCredentialStore.h"

namespace huh::transport {

enum class WifiConnectionState { kUnconfigured, kConnecting, kConnected, kWaitingToRetry };

class TrustedLanWifi {
 public:
  void configure(device::WifiCredentials& credentials);
  void clearConfiguration();
  void poll();
  bool isConnected() const { return WiFi.status() == WL_CONNECTED; }
  WifiConnectionState state() const { return state_; }
  const char* stateName() const;
  const String& configuredSsid() const { return ssid_; }

 private:
  void startConnection();
  void registerEventHandler();
  void scanForConfiguredNetwork();
  String ssid_;
  String password_;
  WifiConnectionState state_ = WifiConnectionState::kUnconfigured;
  uint32_t stateStartedAt_ = 0;
  uint32_t retryAt_ = 0;
  wifi_event_id_t disconnectEventId_ = 0;
  bool eventHandlerRegistered_ = false;
  volatile uint8_t pendingDisconnectReason_ = 0;
  volatile bool hasPendingDisconnectReason_ = false;
  uint8_t selectedBssid_[6]{};
  int32_t selectedChannel_ = 0;
  bool hasSelectedAccessPoint_ = false;
};

}  // namespace huh::transport
