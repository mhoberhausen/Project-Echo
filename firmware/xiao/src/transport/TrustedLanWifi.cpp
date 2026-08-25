#include "TrustedLanWifi.h"

#include "DeviceConfig.h"

#include <cstring>

namespace huh::transport {
namespace {

const char* authModeName(wifi_auth_mode_t mode) {
  switch (mode) {
    case WIFI_AUTH_OPEN: return "open";
    case WIFI_AUTH_WEP: return "WEP";
    case WIFI_AUTH_WPA_PSK: return "WPA-PSK";
    case WIFI_AUTH_WPA2_PSK: return "WPA2-PSK";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2-PSK";
    case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-Enterprise";
    case WIFI_AUTH_WPA3_PSK: return "WPA3-PSK";
    case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3-PSK";
    default: return "other";
  }
}

}  // namespace

void TrustedLanWifi::configure(device::WifiCredentials& credentials) {
  clearConfiguration();
  ssid_ = credentials.ssid;
  password_ = credentials.password;
  credentials.clearSensitive();
  registerEventHandler();
  scanForConfiguredNetwork();
  startConnection();
}

void TrustedLanWifi::registerEventHandler() {
  if (eventHandlerRegistered_) return;
  disconnectEventId_ = WiFi.onEvent(
      [this](WiFiEvent_t, WiFiEventInfo_t info) {
        pendingDisconnectReason_ = info.wifi_sta_disconnected.reason;
        hasPendingDisconnectReason_ = true;
      },
      ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
  eventHandlerRegistered_ = true;
}

void TrustedLanWifi::scanForConfiguredNetwork() {
  WiFi.mode(WIFI_STA);
  Serial.println("Scanning for configured SSID...");
  const int count = WiFi.scanNetworks(false, true);
  bool found = false;
  int32_t strongestSignal = -1000;
  for (int index = 0; index < count; ++index) {
    if (WiFi.SSID(index) != ssid_) continue;
    found = true;
    Serial.printf("SSID visible: channel=%ld, signal=%ld dBm, security=%s\n",
                  static_cast<long>(WiFi.channel(index)),
                  static_cast<long>(WiFi.RSSI(index)),
                  authModeName(WiFi.encryptionType(index)));
    if (WiFi.RSSI(index) > strongestSignal) {
      strongestSignal = WiFi.RSSI(index);
      selectedChannel_ = WiFi.channel(index);
      memcpy(selectedBssid_, WiFi.BSSID(index), sizeof(selectedBssid_));
      hasSelectedAccessPoint_ = true;
    }
  }
  if (!found) {
    Serial.println("SSID not visible in a 2.4 GHz scan.");
    hasSelectedAccessPoint_ = false;
  } else {
    Serial.printf("Selected strongest access point: channel=%ld, signal=%ld dBm\n",
                  static_cast<long>(selectedChannel_),
                  static_cast<long>(strongestSignal));
  }
  WiFi.scanDelete();
}

void TrustedLanWifi::clearConfiguration() {
  WiFi.disconnect(true, false);
  for (size_t index = 0; index < password_.length(); ++index) password_.setCharAt(index, '\0');
  password_ = "";
  ssid_ = "";
  state_ = WifiConnectionState::kUnconfigured;
}

void TrustedLanWifi::startConnection() {
  if (ssid_.isEmpty()) {
    state_ = WifiConnectionState::kUnconfigured;
    return;
  }
  WiFi.mode(WIFI_STA);
  // Continuous 20 ms audio frames require predictable radio service. Modem sleep can
  // defer TCP acknowledgements/transmission long enough to exhaust the small send buffer.
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(false);
  Serial.println("Connecting to configured trusted LAN...");
  if (hasSelectedAccessPoint_) {
    WiFi.begin(ssid_.c_str(), password_.c_str(), selectedChannel_,
               selectedBssid_, true);
  } else {
    WiFi.begin(ssid_.c_str(), password_.c_str());
  }
  state_ = WifiConnectionState::kConnecting;
  stateStartedAt_ = millis();
}

void TrustedLanWifi::poll() {
  if (state_ == WifiConnectionState::kUnconfigured) return;
  if (hasPendingDisconnectReason_) {
    const uint8_t reason = pendingDisconnectReason_;
    hasPendingDisconnectReason_ = false;
    if (reason != WIFI_REASON_ASSOC_LEAVE && reason != WIFI_REASON_AUTH_LEAVE &&
        reason != WIFI_REASON_STA_LEAVING) {
      Serial.printf("Wi-Fi disconnect reason: %u (%s)\n", reason,
                    WiFi.STA.disconnectReasonName(
                        static_cast<wifi_err_reason_t>(reason)));
    }
  }
  if (WiFi.status() == WL_CONNECTED) {
    if (state_ != WifiConnectionState::kConnected) {
      state_ = WifiConnectionState::kConnected;
      Serial.println("Connected to trusted LAN.");
      Serial.printf("IP: %s\n", WiFi.localIP().toString().c_str());
      Serial.printf("HUH1 TCP server: %u\n", config::kTcpAudioPort);
    }
    return;
  }
  if (state_ == WifiConnectionState::kConnected) {
    Serial.println("Wi-Fi connection lost; waiting before retry.");
    state_ = WifiConnectionState::kWaitingToRetry;
    retryAt_ = millis() + config::kWifiRetryIntervalMs;
  } else if (state_ == WifiConnectionState::kConnecting &&
             millis() - stateStartedAt_ >= config::kWifiConnectTimeoutMs) {
    WiFi.disconnect(false, false);
    Serial.println("Wi-Fi connection failed; use 'wifi setup' to replace credentials or wait for retry.");
    state_ = WifiConnectionState::kWaitingToRetry;
    retryAt_ = millis() + config::kWifiRetryIntervalMs;
  } else if (state_ == WifiConnectionState::kWaitingToRetry &&
             static_cast<int32_t>(millis() - retryAt_) >= 0) {
    startConnection();
  }
}

const char* TrustedLanWifi::stateName() const {
  switch (state_) {
    case WifiConnectionState::kUnconfigured: return "unconfigured";
    case WifiConnectionState::kConnecting: return "connecting";
    case WifiConnectionState::kConnected: return "connected";
    case WifiConnectionState::kWaitingToRetry: return "waiting to retry";
  }
  return "unknown";
}

}  // namespace huh::transport
