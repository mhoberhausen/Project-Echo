#include "WifiCredentialStore.h"

#include <Preferences.h>

namespace huh::device {
namespace {
constexpr char kNamespace[] = "huh-wifi";
constexpr char kSsidKey[] = "ssid";
constexpr char kPasswordKey[] = "password";
}

void WifiCredentials::clearSensitive() {
  for (size_t index = 0; index < password.length(); ++index) password.setCharAt(index, '\0');
  password = "";
}

bool WifiCredentialStore::load(WifiCredentials& credentials) const {
  Preferences preferences;
  if (!preferences.begin(kNamespace, true)) return false;
  credentials.ssid = preferences.getString(kSsidKey, "");
  credentials.password = preferences.getString(kPasswordKey, "");
  preferences.end();
  if (!credentials.isValid()) {
    credentials.clearSensitive();
    credentials.ssid = "";
    return false;
  }
  return true;
}

bool WifiCredentialStore::save(const char* ssid, const char* password) const {
  if (ssid == nullptr || password == nullptr) return false;
  const size_t ssidLength = strlen(ssid);
  const size_t passwordLength = strlen(password);
  if (ssidLength == 0 || ssidLength > 32 || passwordLength > 63) return false;

  Preferences preferences;
  if (!preferences.begin(kNamespace, false)) return false;
  const size_t ssidWritten = preferences.putString(kSsidKey, ssid);
  const size_t passwordWritten = preferences.putString(kPasswordKey, password);
  if (ssidWritten == 0 || passwordWritten == 0) {
    preferences.remove(kSsidKey);
    preferences.remove(kPasswordKey);
    preferences.end();
    return false;
  }
  preferences.end();
  return true;
}

bool WifiCredentialStore::clear() const {
  Preferences preferences;
  if (!preferences.begin(kNamespace, false)) return false;
  const bool result = preferences.clear();
  preferences.end();
  return result;
}

}  // namespace huh::device

