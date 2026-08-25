#include "DeviceIdentity.h"

#include <esp_random.h>

namespace huh::device {

protocol::HelloInfo DeviceIdentity::helloInfo() {
  const uint64_t chipId = ESP.getEfuseMac();
  char id[32];
  snprintf(id, sizeof(id), "huh-xiao-%04x%08x",
           static_cast<uint16_t>(chipId >> 32),
           static_cast<uint32_t>(chipId));
  return {
      id,
      "Huh? XIAO",
      "Seeed Studio",
      "XIAO ESP32S3 Sense",
      HUH_FIRMWARE_VERSION,
  };
}

protocol::StreamUuid DeviceIdentity::createStreamUuid() {
  protocol::StreamUuid uuid{};
  esp_fill_random(uuid.data(), uuid.size());
  // RFC 4122 variant and random (v4) version bits.
  uuid[6] = static_cast<uint8_t>((uuid[6] & 0x0F) | 0x40);
  uuid[8] = static_cast<uint8_t>((uuid[8] & 0x3F) | 0x80);
  return uuid;
}

}  // namespace huh::device

