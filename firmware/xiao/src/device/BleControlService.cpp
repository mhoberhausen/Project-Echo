#include "BleControlService.h"

#include <BLEAdvertising.h>
#include <BLECharacteristic.h>
#include <BLEDevice.h>
#include <BLESecurity.h>
#include <BLEServer.h>

namespace huh::device {
namespace {

constexpr char kServiceUuid[] = "7d2e0001-6f9b-4af7-ae8c-5e4f48554831";
constexpr char kIdentityUuid[] = "7d2e0002-6f9b-4af7-ae8c-5e4f48554831";
constexpr char kStatusUuid[] = "7d2e0003-6f9b-4af7-ae8c-5e4f48554831";
constexpr char kCommandUuid[] = "7d2e0004-6f9b-4af7-ae8c-5e4f48554831";
constexpr char kResponseUuid[] = "7d2e0005-6f9b-4af7-ae8c-5e4f48554831";

class ServerCallbacks final : public BLEServerCallbacks {
 public:
  void onConnect(BLEServer*) override { Serial.println("BLE controller connected; authentication required for control."); }
  void onDisconnect(BLEServer* server) override {
    Serial.println("BLE controller disconnected.");
    server->startAdvertising();
  }
};

class SecurityCallbacks final : public BLESecurityCallbacks {
 public:
  bool onSecurityRequest() override { return true; }
#if defined(CONFIG_BLUEDROID_ENABLED)
  void onAuthenticationComplete(esp_ble_auth_cmpl_t result) override {
    Serial.printf("BLE authentication %s.\n", result.success ? "succeeded" : "failed");
  }
#endif
#if defined(CONFIG_NIMBLE_ENABLED)
  void onAuthenticationComplete(ble_gap_conn_desc* result) override {
    const bool success = result != nullptr && result->sec_state.encrypted &&
                         result->sec_state.authenticated && result->sec_state.bonded;
    Serial.printf("BLE authentication %s.\n", success ? "succeeded" : "failed");
  }
#endif
};

}  // namespace

class BleCommandCallbacks final : public BLECharacteristicCallbacks {
 public:
  explicit BleCommandCallbacks(BleControlService& owner) : owner_(owner) {}
  void onWrite(BLECharacteristic* characteristic) override {
    owner_.receive(characteristic->getData(), characteristic->getLength());
  }
 private:
  BleControlService& owner_;
};

bool BleControlService::begin(const protocol::HelloInfo& identity) {
  BLEDevice::init(identity.displayName.c_str());
  BLESecurity security;
  const uint32_t passkey = security.setPassKey(false);
  security.setCapability(ESP_IO_CAP_OUT);
  security.setAuthenticationMode(true, true, true);
  security.setKeySize(16);
  BLEDevice::setSecurityCallbacks(new SecurityCallbacks());

  BLEServer* server = BLEDevice::createServer();
  if (server == nullptr) return false;
  server->setCallbacks(new ServerCallbacks());
  server->advertiseOnDisconnect(true);
  BLEService* service = server->createService(kServiceUuid);
  if (service == nullptr) return false;

  BLECharacteristic* identityCharacteristic = service->createCharacteristic(
      kIdentityUuid, BLECharacteristic::PROPERTY_READ);
  status_ = service->createCharacteristic(
      kStatusUuid, BLECharacteristic::PROPERTY_READ |
          BLECharacteristic::PROPERTY_READ_AUTHEN | BLECharacteristic::PROPERTY_NOTIFY);
  BLECharacteristic* command = service->createCharacteristic(
      kCommandUuid, BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_WRITE_AUTHEN);
  response_ = service->createCharacteristic(
      kResponseUuid, BLECharacteristic::PROPERTY_READ |
          BLECharacteristic::PROPERTY_READ_AUTHEN | BLECharacteristic::PROPERTY_NOTIFY);

  identityCharacteristic->setAccessPermissions(ESP_GATT_PERM_READ);
  const uint16_t secureRead = ESP_GATT_PERM_READ_ENC_MITM;
  status_->setAccessPermissions(secureRead);
  command->setAccessPermissions(ESP_GATT_PERM_WRITE_ENC_MITM);
  response_->setAccessPermissions(secureRead);
  command->setCallbacks(new BleCommandCallbacks(*this));

  const String identityValue = String("v1|") + identity.deviceId.c_str() + "|" +
      identity.model.c_str() + "|" + identity.firmwareVersion.c_str();
  identityCharacteristic->setValue(identityValue);
  status_->setValue("v1|BOOTING");
  response_->setValue("v1|0|READY");
  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(kServiceUuid);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);
  advertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.printf("BLE control advertising; pairing passkey=%06lu (USB serial only).\n",
                static_cast<unsigned long>(passkey));
  return true;
}

void BleControlService::receive(const uint8_t* bytes, size_t length) {
  HardwareControlRequest request;
  if (!parseHardwareControlRequest(bytes, length, request)) {
    publishResponse(0, "INVALID_REQUEST");
    return;
  }
  if (pendingCommand_.load() != static_cast<uint8_t>(HardwareControlCommand::kUnknown)) {
    publishResponse(request.requestId, "BUSY");
    return;
  }
  pendingRequestId_.store(request.requestId);
  pendingCommand_.store(static_cast<uint8_t>(request.command));
}

bool BleControlService::takeRequest(HardwareControlRequest& request) {
  const uint8_t command = pendingCommand_.exchange(
      static_cast<uint8_t>(HardwareControlCommand::kUnknown));
  if (command == static_cast<uint8_t>(HardwareControlCommand::kUnknown)) return false;
  request.version = 1;
  request.requestId = pendingRequestId_.load();
  request.command = static_cast<HardwareControlCommand>(command);
  return true;
}

void BleControlService::publishStatus(const String& status) {
  if (status_ == nullptr || status == lastStatus_) return;
  lastStatus_ = status;
  status_->setValue(String("v1|") + status);
  status_->notify();
}

void BleControlService::publishResponse(uint32_t requestId, const char* result) {
  if (response_ == nullptr) return;
  response_->setValue(String("v1|") + requestId + "|" + result);
  response_->notify();
}

}  // namespace huh::device
