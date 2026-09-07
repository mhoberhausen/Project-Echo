#include "OtaService.h"

#include <ArduinoOTA.h>
#include <WiFi.h>

#include "../../include/DeviceConfig.h"

namespace huh::device {

void OtaService::begin() {
  if (ready_ || !networkAvailable_) return;

  ArduinoOTA.setPort(config::kOtaPort);
  ArduinoOTA.setPassword(config::kOtaPassword);
  ArduinoOTA.setMdnsEnabled(false);
  ArduinoOTA.onStart([this]() {
    updating_ = true;
    Serial.println("OTA update started; capture services are suspended.");
  });
  ArduinoOTA.onEnd([this]() {
    updating_ = false;
    Serial.println("OTA update complete; rebooting.");
  });
  ArduinoOTA.onError([this](ota_error_t error) {
    updating_ = false;
    Serial.printf("OTA update failed: error=%u\n", static_cast<unsigned>(error));
  });
  ArduinoOTA.begin();
  ready_ = true;
  Serial.printf("Direct-IP OTA ready on %s:%u (mDNS disabled).\n",
                WiFi.localIP().toString().c_str(), config::kOtaPort);
}

void OtaService::end() {
  if (ready_) ArduinoOTA.end();
  ready_ = false;
  updating_ = false;
}

void OtaService::setNetworkAvailable(bool available) {
  if (networkAvailable_ == available) return;
  networkAvailable_ = available;
  if (available) begin();
  else end();
}

void OtaService::poll(bool captureIdle) {
  if (!ready_) return;
  // Once an update starts it must be serviced through completion. New updates
  // are only accepted while no capture or capture-controller connection exists.
  if (updating_ || captureIdle) ArduinoOTA.handle();
}

}  // namespace huh::device
