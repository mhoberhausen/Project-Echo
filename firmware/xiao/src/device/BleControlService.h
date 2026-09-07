#pragma once

#include <Arduino.h>
#include <atomic>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include "HardwareControlProtocol.h"
#include "../protocol/HuhAudioProtocol.h"

class BLECharacteristic;

namespace huh::device {

class BleControlService {
 public:
  bool begin(const protocol::HelloInfo& identity);
  bool takeRequest(HardwareControlRequest& request);
  bool takeDiagnostic(String& diagnostic);
  // Callbacks execute on the BLE stack. This method only enqueues a bounded,
  // non-blocking diagnostic event for the main loop to publish later.
  void recordDiagnostic(const char* event);
  void publishStatus(const String& status);
  void publishResponse(uint32_t requestId, const char* result);

 private:
  friend class BleCommandCallbacks;
  void receive(const uint8_t* bytes, size_t length);

  BLECharacteristic* status_ = nullptr;
  BLECharacteristic* response_ = nullptr;
  std::atomic<uint8_t> pendingCommand_{static_cast<uint8_t>(HardwareControlCommand::kUnknown)};
  std::atomic<uint32_t> pendingRequestId_{0};
  String lastStatus_;
  QueueHandle_t diagnostics_ = nullptr;
};

}  // namespace huh::device
