#pragma once

#include <Arduino.h>

namespace huh::device {

struct WifiCredentials {
  String ssid;
  String password;

  bool isValid() const { return !ssid.isEmpty() && ssid.length() <= 32 && password.length() <= 63; }
  void clearSensitive();
};

class WifiCredentialStore {
 public:
  bool load(WifiCredentials& credentials) const;
  bool save(const char* ssid, const char* password) const;
  bool clear() const;
};

}  // namespace huh::device

